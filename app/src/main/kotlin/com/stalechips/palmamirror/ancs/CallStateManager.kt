package com.stalechips.palmamirror.ancs

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the lifecycle of an incoming phone call via ANCS notifications.
 *
 * State machine transitions:
 *   IDLE -> RINGING        (incoming call notification arrives)
 *   RINGING -> ACCEPTED    (user accepts the call)
 *   RINGING -> REJECTED    (user rejects the call)
 *   RINGING -> ENDED       (caller hangs up or 60-second timeout)
 *   ACCEPTED -> ENDED      (call ends)
 *
 * All other transitions are ignored.
 *
 * Thread-safe: all state mutations are synchronized.
 */
class CallStateManager {

    enum class State {
        IDLE,
        RINGING,
        ACCEPTED,
        REJECTED,
        ENDED
    }

    /**
     * Information about the current (or most recent) call.
     */
    data class CallInfo(
        val notificationUid: Int,
        val callerName: String?,
        val phoneNumber: String?
    )

    companion object {
        private const val TAG = "CallStateManager"
        private const val RING_TIMEOUT_MS = 60_000L
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _callInfo = MutableStateFlow<CallInfo?>(null)
    val callInfo: StateFlow<CallInfo?> = _callInfo.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    private val ringTimeoutRunnable = Runnable {
        synchronized(this) {
            if (_state.value == State.RINGING) {
                Log.w(TAG, "Ring timeout reached (${RING_TIMEOUT_MS}ms), transitioning to ENDED")
                transitionTo(State.ENDED)
            }
        }
    }

    /**
     * Called when an incoming call ANCS notification arrives.
     * Transitions from IDLE to RINGING and starts the ring timeout.
     */
    @Synchronized
    fun onIncomingCall(notificationUid: Int, callerName: String?, phoneNumber: String?) {
        if (_state.value != State.IDLE) {
            Log.w(TAG, "onIncomingCall ignored: current state is ${_state.value}")
            return
        }
        _callInfo.value = CallInfo(notificationUid, callerName, phoneNumber)
        transitionTo(State.RINGING)
        handler.postDelayed(ringTimeoutRunnable, RING_TIMEOUT_MS)
        Log.i(TAG, "Incoming call from ${callerName ?: "Unknown"} (UID=$notificationUid)")
    }

    /**
     * User accepts the ringing call.
     * Transitions from RINGING to ACCEPTED.
     */
    @Synchronized
    fun acceptCall() {
        if (_state.value != State.RINGING) {
            Log.w(TAG, "acceptCall ignored: current state is ${_state.value}")
            return
        }
        cancelRingTimeout()
        transitionTo(State.ACCEPTED)
        Log.i(TAG, "Call accepted (UID=${_callInfo.value?.notificationUid})")
    }

    /**
     * User rejects the ringing call.
     * Transitions from RINGING to REJECTED.
     */
    @Synchronized
    fun rejectCall() {
        if (_state.value != State.RINGING) {
            Log.w(TAG, "rejectCall ignored: current state is ${_state.value}")
            return
        }
        cancelRingTimeout()
        transitionTo(State.REJECTED)
        Log.i(TAG, "Call rejected (UID=${_callInfo.value?.notificationUid})")
    }

    /**
     * The call has ended (caller hung up, or the active call finished).
     * Valid from RINGING (caller hung up) or ACCEPTED (call finished).
     */
    @Synchronized
    fun endCall() {
        val current = _state.value
        if (current != State.RINGING && current != State.ACCEPTED) {
            Log.w(TAG, "endCall ignored: current state is $current")
            return
        }
        if (current == State.RINGING) {
            cancelRingTimeout()
        }
        transitionTo(State.ENDED)
        Log.i(TAG, "Call ended (UID=${_callInfo.value?.notificationUid})")
    }

    /**
     * Reset the state machine back to IDLE, clearing call info.
     * Can be called from any state.
     */
    @Synchronized
    fun reset() {
        cancelRingTimeout()
        _callInfo.value = null
        val previous = _state.value
        _state.value = State.IDLE
        Log.i(TAG, "Reset from $previous to IDLE")
    }

    private fun transitionTo(newState: State) {
        val previous = _state.value
        _state.value = newState
        Log.d(TAG, "State transition: $previous -> $newState")

        if (newState == State.ENDED || newState == State.REJECTED) {
            _callInfo.value = null
        }
    }

    private fun cancelRingTimeout() {
        handler.removeCallbacks(ringTimeoutRunnable)
    }
}
