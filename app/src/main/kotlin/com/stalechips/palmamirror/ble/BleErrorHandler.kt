package com.stalechips.palmamirror.ble

import android.util.Log

/**
 * Handles BLE error codes and provides user-friendly error messages.
 * Common GATT error codes from Android BLE stack.
 */
object BleErrorHandler {

    private const val TAG = "BleErrorHandler"

    /** Common GATT status codes */
    const val GATT_SUCCESS = 0
    const val GATT_READ_NOT_PERMITTED = 2
    const val GATT_WRITE_NOT_PERMITTED = 3
    const val GATT_INSUFFICIENT_AUTHENTICATION = 5
    const val GATT_REQUEST_NOT_SUPPORTED = 6
    const val GATT_INSUFFICIENT_ENCRYPTION = 15
    const val GATT_INVALID_OFFSET = 7
    const val GATT_CONNECTION_CONGESTED = 0x8F
    const val GATT_FAILURE = 0x101
    const val GATT_ERROR = 133  // The infamous "133 error"
    const val GATT_CONN_TIMEOUT = 8
    const val GATT_CONN_TERMINATE_PEER = 19
    const val GATT_CONN_TERMINATE_LOCAL = 22

    /**
     * Get a human-readable description of a GATT status code.
     */
    fun getStatusDescription(status: Int): String {
        return when (status) {
            GATT_SUCCESS -> "Success"
            GATT_READ_NOT_PERMITTED -> "Read not permitted"
            GATT_WRITE_NOT_PERMITTED -> "Write not permitted"
            GATT_INSUFFICIENT_AUTHENTICATION -> "Insufficient authentication — try re-pairing"
            GATT_REQUEST_NOT_SUPPORTED -> "Request not supported"
            GATT_INSUFFICIENT_ENCRYPTION -> "Insufficient encryption — try re-pairing"
            GATT_INVALID_OFFSET -> "Invalid offset"
            GATT_CONNECTION_CONGESTED -> "Connection congested"
            GATT_FAILURE -> "GATT failure"
            GATT_ERROR -> "GATT error (133) — connection issue, will retry"
            GATT_CONN_TIMEOUT -> "Connection timeout — iPhone may be out of range"
            GATT_CONN_TERMINATE_PEER -> "iPhone terminated connection"
            GATT_CONN_TERMINATE_LOCAL -> "Connection terminated locally"
            else -> "Unknown GATT error ($status)"
        }
    }

    /**
     * Determine the appropriate recovery action for a GATT error.
     */
    fun getRecoveryAction(status: Int): RecoveryAction {
        return when (status) {
            GATT_SUCCESS -> RecoveryAction.NONE
            GATT_ERROR -> RecoveryAction.CLOSE_AND_RECONNECT
            GATT_CONN_TIMEOUT -> RecoveryAction.RECONNECT
            GATT_CONN_TERMINATE_PEER -> RecoveryAction.RECONNECT
            GATT_CONN_TERMINATE_LOCAL -> RecoveryAction.RECONNECT
            GATT_INSUFFICIENT_AUTHENTICATION -> RecoveryAction.RE_PAIR
            GATT_INSUFFICIENT_ENCRYPTION -> RecoveryAction.RE_PAIR
            GATT_FAILURE -> RecoveryAction.CLOSE_AND_RECONNECT
            else -> RecoveryAction.RECONNECT
        }
    }

    /**
     * Log a GATT error with context.
     */
    fun logError(context: String, status: Int) {
        val desc = getStatusDescription(status)
        val action = getRecoveryAction(status)
        Log.e(TAG, "$context: $desc (status=$status, recovery=$action)")
    }

    enum class RecoveryAction {
        /** No action needed */
        NONE,
        /** Simply reconnect */
        RECONNECT,
        /** Close GATT object first, then reconnect with new object */
        CLOSE_AND_RECONNECT,
        /** Bond is lost or invalid — user must re-pair */
        RE_PAIR,
        /** Fatal error — BLE is not available */
        FATAL
    }
}
