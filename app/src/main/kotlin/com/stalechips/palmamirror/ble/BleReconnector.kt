package com.stalechips.palmamirror.ble

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.min
import kotlin.math.pow

/**
 * Handles automatic reconnection with exponential backoff.
 * Backs off from 1s to 60s max, resets on successful connection.
 */
class BleReconnector(
    private val onReconnect: () -> Unit,
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 60000L,
    private val backoffMultiplier: Double = 2.0
) {

    companion object {
        private const val TAG = "BleReconnector"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var isActive = false

    private val reconnectRunnable = Runnable {
        if (isActive) {
            attempt++
            Log.d(TAG, "Reconnection attempt #$attempt")
            onReconnect()
        }
    }

    /**
     * Start reconnection attempts with exponential backoff.
     */
    fun startReconnecting() {
        if (isActive) return
        isActive = true
        attempt = 0
        scheduleNext()
    }

    /**
     * Schedule the next reconnection attempt.
     * Call this after a failed reconnection to continue the backoff.
     */
    fun scheduleNext() {
        if (!isActive) return
        val delay = calculateDelay()
        Log.d(TAG, "Next reconnection in ${delay}ms (attempt #${attempt + 1})")
        handler.postDelayed(reconnectRunnable, delay)
    }

    /**
     * Stop all reconnection attempts. Call on successful connection.
     */
    fun stop() {
        isActive = false
        attempt = 0
        handler.removeCallbacks(reconnectRunnable)
        Log.d(TAG, "Reconnection stopped")
    }

    /**
     * Cancel pending reconnection but keep state.
     */
    fun cancel() {
        handler.removeCallbacks(reconnectRunnable)
    }

    /**
     * Calculate delay with exponential backoff, capped at maxDelayMs.
     */
    internal fun calculateDelay(): Long {
        val delay = (initialDelayMs * backoffMultiplier.pow(attempt.toDouble())).toLong()
        return min(delay, maxDelayMs)
    }

    fun isReconnecting(): Boolean = isActive

    fun getCurrentAttempt(): Int = attempt
}
