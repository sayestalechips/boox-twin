package com.stalechips.palmamirror.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps ANCS-specific GATT operations: requesting notification attributes
 * and performing notification actions via the Control Point characteristic.
 */
class AncsService(
    private val connectionManager: BleConnectionManager
) {

    companion object {
        private const val TAG = "AncsService"
    }

    /**
     * Request notification attributes for a given notification UID.
     * Sends a GetNotificationAttributes command via the Control Point.
     *
     * Command format:
     *   Byte 0: CommandID (0 = GetNotificationAttributes)
     *   Bytes 1-4: NotificationUID (uint32 LE)
     *   Remaining: List of AttributeIDs (some followed by uint16 LE max length)
     */
    fun requestNotificationAttributes(notificationUID: Int): Boolean {
        val buffer = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)

        // CommandID
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)

        // NotificationUID
        buffer.putInt(notificationUID)

        // Request AppIdentifier (no max length — null-terminated string)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)

        // Request Title with max length
        buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
        buffer.putShort(AncsConstants.MAX_TITLE_LENGTH.toShort())

        // Request Subtitle with max length
        buffer.put(AncsConstants.NOTIFICATION_ATTR_SUBTITLE)
        buffer.putShort(AncsConstants.MAX_SUBTITLE_LENGTH.toShort())

        // Request Message with max length
        buffer.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE)
        buffer.putShort(AncsConstants.MAX_MESSAGE_LENGTH.toShort())

        // Request Date (no max length — fixed 8-byte format YYYYMMDDTHHMMSS)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_DATE)

        // Request action labels
        buffer.put(AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL)

        val command = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(command)

        Log.d(TAG, "Requesting attributes for notification UID=$notificationUID (${command.size} bytes)")
        return connectionManager.writeControlPoint(command)
    }

    /**
     * Request app attributes (display name) for a given app identifier.
     *
     * Command format:
     *   Byte 0: CommandID (1 = GetAppAttributes)
     *   Remaining: AppIdentifier (null-terminated string) + AttributeIDs
     */
    fun requestAppAttributes(appIdentifier: String): Boolean {
        val appIdBytes = appIdentifier.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + appIdBytes.size + 1 + 1).order(ByteOrder.LITTLE_ENDIAN)

        // CommandID
        buffer.put(AncsConstants.COMMAND_ID_GET_APP_ATTRIBUTES)

        // AppIdentifier (null-terminated)
        buffer.put(appIdBytes)
        buffer.put(0.toByte())

        // Request DisplayName
        buffer.put(AncsConstants.APP_ATTR_DISPLAY_NAME)

        val command = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(command)

        Log.d(TAG, "Requesting app attributes for '$appIdentifier'")
        return connectionManager.writeControlPoint(command)
    }

    /**
     * Perform an action on a notification (positive = accept/reply, negative = reject/dismiss).
     *
     * Command format:
     *   Byte 0: CommandID (2 = PerformNotificationAction)
     *   Bytes 1-4: NotificationUID (uint32 LE)
     *   Byte 5: ActionID (0 = positive, 1 = negative)
     */
    fun performAction(notificationUID: Int, positive: Boolean): Boolean {
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(AncsConstants.COMMAND_ID_PERFORM_NOTIFICATION_ACTION)
        buffer.putInt(notificationUID)
        buffer.put(
            if (positive) AncsConstants.ACTION_ID_POSITIVE
            else AncsConstants.ACTION_ID_NEGATIVE
        )

        val command = buffer.array()
        val actionName = if (positive) "positive" else "negative"
        Log.d(TAG, "Performing $actionName action on notification UID=$notificationUID")
        return connectionManager.writeControlPoint(command)
    }

    /**
     * Accept an incoming call.
     */
    fun acceptCall(notificationUID: Int): Boolean = performAction(notificationUID, positive = true)

    /**
     * Reject an incoming call.
     */
    fun rejectCall(notificationUID: Int): Boolean = performAction(notificationUID, positive = false)
}
