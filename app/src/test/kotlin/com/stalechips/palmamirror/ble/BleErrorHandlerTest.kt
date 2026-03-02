package com.stalechips.palmamirror.ble

import org.junit.Assert.*
import org.junit.Test

class BleErrorHandlerTest {

    @Test
    fun `GATT_SUCCESS returns no recovery action`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.NONE,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_SUCCESS)
        )
    }

    @Test
    fun `GATT_ERROR 133 returns close and reconnect`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.CLOSE_AND_RECONNECT,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_ERROR)
        )
    }

    @Test
    fun `connection timeout returns reconnect`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.RECONNECT,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_CONN_TIMEOUT)
        )
    }

    @Test
    fun `peer termination returns reconnect`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.RECONNECT,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_CONN_TERMINATE_PEER)
        )
    }

    @Test
    fun `insufficient authentication returns re-pair`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.RE_PAIR,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_INSUFFICIENT_AUTHENTICATION)
        )
    }

    @Test
    fun `insufficient encryption returns re-pair`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.RE_PAIR,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_INSUFFICIENT_ENCRYPTION)
        )
    }

    @Test
    fun `unknown status returns reconnect`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.RECONNECT,
            BleErrorHandler.getRecoveryAction(999)
        )
    }

    @Test
    fun `all known statuses have descriptions`() {
        val statuses = listOf(
            BleErrorHandler.GATT_SUCCESS,
            BleErrorHandler.GATT_READ_NOT_PERMITTED,
            BleErrorHandler.GATT_WRITE_NOT_PERMITTED,
            BleErrorHandler.GATT_INSUFFICIENT_AUTHENTICATION,
            BleErrorHandler.GATT_REQUEST_NOT_SUPPORTED,
            BleErrorHandler.GATT_INSUFFICIENT_ENCRYPTION,
            BleErrorHandler.GATT_INVALID_OFFSET,
            BleErrorHandler.GATT_CONNECTION_CONGESTED,
            BleErrorHandler.GATT_FAILURE,
            BleErrorHandler.GATT_ERROR,
            BleErrorHandler.GATT_CONN_TIMEOUT,
            BleErrorHandler.GATT_CONN_TERMINATE_PEER,
            BleErrorHandler.GATT_CONN_TERMINATE_LOCAL
        )

        for (status in statuses) {
            val desc = BleErrorHandler.getStatusDescription(status)
            assertFalse("Status $status should not start with 'Unknown'",
                desc.startsWith("Unknown"))
        }
    }

    @Test
    fun `unknown status description includes the code`() {
        val desc = BleErrorHandler.getStatusDescription(12345)
        assertTrue(desc.contains("12345"))
    }

    @Test
    fun `GATT_FAILURE returns close and reconnect`() {
        assertEquals(
            BleErrorHandler.RecoveryAction.CLOSE_AND_RECONNECT,
            BleErrorHandler.getRecoveryAction(BleErrorHandler.GATT_FAILURE)
        )
    }

    @Test
    fun `logError does not throw`() {
        // Should not throw even with unusual values
        BleErrorHandler.logError("test context", 0)
        BleErrorHandler.logError("test context", 133)
        BleErrorHandler.logError("test context", -1)
        BleErrorHandler.logError("test context", Int.MAX_VALUE)
    }
}
