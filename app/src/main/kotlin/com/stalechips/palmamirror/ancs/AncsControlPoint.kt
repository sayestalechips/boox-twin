package com.stalechips.palmamirror.ancs

import com.stalechips.palmamirror.ble.AncsConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds Control Point command byte arrays for ANCS operations.
 * Pure functions — no BLE I/O, fully testable.
 */
object AncsControlPoint {

    /**
     * Build a GetNotificationAttributes command.
     *
     * Format:
     *   [0]: CommandID = 0
     *   [1-4]: NotificationUID (uint32 LE)
     *   Then for each requested attribute:
     *     [n]: AttributeID
     *     [n+1..n+2]: MaxLength (uint16 LE) — only for attributes that need it
     */
    fun buildGetNotificationAttributes(
        notificationUID: Int,
        requestTitle: Boolean = true,
        maxTitleLength: Int = AncsConstants.MAX_TITLE_LENGTH,
        requestSubtitle: Boolean = true,
        maxSubtitleLength: Int = AncsConstants.MAX_SUBTITLE_LENGTH,
        requestMessage: Boolean = true,
        maxMessageLength: Int = AncsConstants.MAX_MESSAGE_LENGTH,
        requestDate: Boolean = true,
        requestAppIdentifier: Boolean = true,
        requestActionLabels: Boolean = true
    ): ByteArray {
        // Calculate size
        var size = 5 // CommandID + UID
        if (requestAppIdentifier) size += 1
        if (requestTitle) size += 3 // AttrID + uint16 maxLen
        if (requestSubtitle) size += 3
        if (requestMessage) size += 3
        if (requestDate) size += 1
        if (requestActionLabels) size += 2 // positive + negative

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(notificationUID)

        if (requestAppIdentifier) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)
        }
        if (requestTitle) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
            buffer.putShort(maxTitleLength.toShort())
        }
        if (requestSubtitle) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_SUBTITLE)
            buffer.putShort(maxSubtitleLength.toShort())
        }
        if (requestMessage) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE)
            buffer.putShort(maxMessageLength.toShort())
        }
        if (requestDate) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_DATE)
        }
        if (requestActionLabels) {
            buffer.put(AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL)
            buffer.put(AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL)
        }

        return buffer.array()
    }

    /**
     * Build a GetAppAttributes command.
     *
     * Format:
     *   [0]: CommandID = 1
     *   [1..n]: AppIdentifier (null-terminated UTF-8 string)
     *   [n+1]: AttributeID = 0 (DisplayName)
     */
    fun buildGetAppAttributes(appIdentifier: String): ByteArray {
        val appIdBytes = appIdentifier.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + appIdBytes.size + 1 + 1).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(AncsConstants.COMMAND_ID_GET_APP_ATTRIBUTES)
        buffer.put(appIdBytes)
        buffer.put(0.toByte()) // null terminator
        buffer.put(AncsConstants.APP_ATTR_DISPLAY_NAME)

        return buffer.array()
    }

    /**
     * Build a PerformNotificationAction command.
     *
     * Format:
     *   [0]: CommandID = 2
     *   [1-4]: NotificationUID (uint32 LE)
     *   [5]: ActionID (0 = positive, 1 = negative)
     */
    fun buildPerformAction(notificationUID: Int, positive: Boolean): ByteArray {
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(AncsConstants.COMMAND_ID_PERFORM_NOTIFICATION_ACTION)
        buffer.putInt(notificationUID)
        buffer.put(
            if (positive) AncsConstants.ACTION_ID_POSITIVE
            else AncsConstants.ACTION_ID_NEGATIVE
        )

        return buffer.array()
    }
}
