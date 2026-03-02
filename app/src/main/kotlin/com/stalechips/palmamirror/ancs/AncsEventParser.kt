package com.stalechips.palmamirror.ancs

import android.util.Log
import com.stalechips.palmamirror.ble.AncsConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Notification Source events (8 bytes each).
 *
 * Byte structure:
 *   Byte 0: EventID (0=Added, 1=Modified, 2=Removed)
 *   Byte 1: EventFlags (bitmask: Silent, Important, Pre-existing, PositiveAction, NegativeAction)
 *   Byte 2: CategoryID (0-11)
 *   Byte 3: CategoryCount
 *   Bytes 4-7: NotificationUID (uint32 little-endian)
 */
class AncsEventParser {

    companion object {
        private const val TAG = "AncsEventParser"
    }

    /**
     * Parse an 8-byte Notification Source event into an AncsNotification.
     * Returns null if the data is malformed.
     */
    fun parse(data: ByteArray): AncsNotification? {
        if (data.size < AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE) {
            Log.w(TAG, "Notification Source data too short: ${data.size} bytes (expected ${AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE})")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val eventId = AncsNotification.EventId.fromByte(buffer.get())
            val eventFlags = buffer.get().toInt() and 0xFF
            val categoryId = buffer.get()
            val categoryCount = buffer.get().toInt() and 0xFF
            val notificationUID = buffer.getInt()

            val category = AncsCategory.fromId(categoryId)

            Log.d(TAG, "Parsed event: $eventId, category=${category.displayName}, " +
                    "uid=$notificationUID, flags=0x${eventFlags.toString(16)}")

            AncsNotification(
                uid = notificationUID,
                eventId = eventId,
                eventFlags = eventFlags,
                category = category,
                categoryCount = categoryCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Notification Source event", e)
            null
        }
    }
}
