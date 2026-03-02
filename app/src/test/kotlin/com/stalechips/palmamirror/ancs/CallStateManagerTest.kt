package com.stalechips.palmamirror.ancs

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CallStateManager].
 *
 * Relies on testOptions { unitTests { isReturnDefaultValues = true } } so that
 * android.os.Handler and android.util.Log return defaults (no-op) without Robolectric.
 */
class CallStateManagerTest {

    private lateinit var manager: CallStateManager

    @Before
    fun setUp() {
        manager = CallStateManager()
    }

    // --- Initial state ---

    @Test
    fun `initial state is IDLE`() {
        assertEquals(CallStateManager.State.IDLE, manager.state.value)
    }

    @Test
    fun `initial call info is null`() {
        assertNull(manager.callInfo.value)
    }

    // --- IDLE -> RINGING ---

    @Test
    fun `onIncomingCall transitions from IDLE to RINGING`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = "+15551234567")

        assertEquals(CallStateManager.State.RINGING, manager.state.value)
    }

    @Test
    fun `onIncomingCall sets call info`() {
        manager.onIncomingCall(notificationUid = 42, callerName = "Bob", phoneNumber = "+15559876543")

        val info = manager.callInfo.value
        assertNotNull(info)
        assertEquals(42, info!!.notificationUid)
        assertEquals("Bob", info.callerName)
        assertEquals("+15559876543", info.phoneNumber)
    }

    // --- RINGING -> ACCEPTED ---

    @Test
    fun `acceptCall transitions from RINGING to ACCEPTED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.acceptCall()

        assertEquals(CallStateManager.State.ACCEPTED, manager.state.value)
    }

    // --- RINGING -> REJECTED ---

    @Test
    fun `rejectCall transitions from RINGING to REJECTED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.rejectCall()

        assertEquals(CallStateManager.State.REJECTED, manager.state.value)
    }

    // --- RINGING -> ENDED ---

    @Test
    fun `endCall transitions from RINGING to ENDED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.endCall()

        assertEquals(CallStateManager.State.ENDED, manager.state.value)
    }

    // --- ACCEPTED -> ENDED ---

    @Test
    fun `endCall transitions from ACCEPTED to ENDED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.acceptCall()

        manager.endCall()

        assertEquals(CallStateManager.State.ENDED, manager.state.value)
    }

    // --- Invalid transitions are ignored ---

    @Test
    fun `acceptCall from IDLE is ignored`() {
        manager.acceptCall()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
    }

    @Test
    fun `rejectCall from IDLE is ignored`() {
        manager.rejectCall()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
    }

    @Test
    fun `endCall from IDLE is ignored`() {
        manager.endCall()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
    }

    @Test
    fun `onIncomingCall from RINGING is ignored`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.onIncomingCall(notificationUid = 2, callerName = "Bob", phoneNumber = null)

        // State should still be RINGING with the original call info
        assertEquals(CallStateManager.State.RINGING, manager.state.value)
        assertEquals(1, manager.callInfo.value!!.notificationUid)
    }

    @Test
    fun `onIncomingCall from ACCEPTED is ignored`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.acceptCall()

        manager.onIncomingCall(notificationUid = 2, callerName = "Bob", phoneNumber = null)

        assertEquals(CallStateManager.State.ACCEPTED, manager.state.value)
    }

    @Test
    fun `acceptCall from ENDED is ignored`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.endCall()

        manager.acceptCall()

        assertEquals(CallStateManager.State.ENDED, manager.state.value)
    }

    @Test
    fun `endCall from REJECTED is ignored`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.rejectCall()

        manager.endCall()

        assertEquals(CallStateManager.State.REJECTED, manager.state.value)
    }

    // --- Reset ---

    @Test
    fun `reset from RINGING returns to IDLE`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.reset()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
        assertNull(manager.callInfo.value)
    }

    @Test
    fun `reset from ACCEPTED returns to IDLE`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.acceptCall()

        manager.reset()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
        assertNull(manager.callInfo.value)
    }

    @Test
    fun `reset from ENDED returns to IDLE`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.endCall()

        manager.reset()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
        assertNull(manager.callInfo.value)
    }

    @Test
    fun `reset from IDLE stays IDLE`() {
        manager.reset()

        assertEquals(CallStateManager.State.IDLE, manager.state.value)
        assertNull(manager.callInfo.value)
    }

    // --- Call info tracking ---

    @Test
    fun `call info tracks UID correctly`() {
        manager.onIncomingCall(notificationUid = 12345, callerName = null, phoneNumber = null)

        assertEquals(12345, manager.callInfo.value!!.notificationUid)
    }

    @Test
    fun `call info allows null caller name and phone number`() {
        manager.onIncomingCall(notificationUid = 1, callerName = null, phoneNumber = null)

        val info = manager.callInfo.value
        assertNotNull(info)
        assertNull(info!!.callerName)
        assertNull(info.phoneNumber)
    }

    @Test
    fun `call info is preserved during ACCEPTED state`() {
        manager.onIncomingCall(notificationUid = 7, callerName = "Charlie", phoneNumber = "+15550000000")
        manager.acceptCall()

        val info = manager.callInfo.value
        assertNotNull(info)
        assertEquals(7, info!!.notificationUid)
        assertEquals("Charlie", info.callerName)
        assertEquals("+15550000000", info.phoneNumber)
    }

    // --- Call info cleared on terminal states ---

    @Test
    fun `call info is cleared on ENDED from RINGING`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.endCall()

        assertNull(manager.callInfo.value)
    }

    @Test
    fun `call info is cleared on ENDED from ACCEPTED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)
        manager.acceptCall()

        manager.endCall()

        assertNull(manager.callInfo.value)
    }

    @Test
    fun `call info is cleared on REJECTED`() {
        manager.onIncomingCall(notificationUid = 1, callerName = "Alice", phoneNumber = null)

        manager.rejectCall()

        assertNull(manager.callInfo.value)
    }

    // --- Full lifecycle ---

    @Test
    fun `full lifecycle IDLE to RINGING to ACCEPTED to ENDED to reset`() {
        assertEquals(CallStateManager.State.IDLE, manager.state.value)

        manager.onIncomingCall(notificationUid = 50, callerName = "Dave", phoneNumber = "+15551112222")
        assertEquals(CallStateManager.State.RINGING, manager.state.value)
        assertNotNull(manager.callInfo.value)

        manager.acceptCall()
        assertEquals(CallStateManager.State.ACCEPTED, manager.state.value)

        manager.endCall()
        assertEquals(CallStateManager.State.ENDED, manager.state.value)
        assertNull(manager.callInfo.value)

        manager.reset()
        assertEquals(CallStateManager.State.IDLE, manager.state.value)

        // Can receive another call after reset
        manager.onIncomingCall(notificationUid = 51, callerName = "Eve", phoneNumber = null)
        assertEquals(CallStateManager.State.RINGING, manager.state.value)
        assertEquals(51, manager.callInfo.value!!.notificationUid)
    }
}
