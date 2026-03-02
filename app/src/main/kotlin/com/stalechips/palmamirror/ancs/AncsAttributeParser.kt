package com.stalechips.palmamirror.ancs

import android.util.Log
import com.stalechips.palmamirror.ble.AncsConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Data Source responses for GetNotificationAttributes commands.
 * Handles fragmented multi-packet BLE responses by reassembling them.
 *
 * Response format:
 *   Byte 0: CommandID (0 = GetNotificationAttributes)
 *   Bytes 1-4: NotificationUID (uint32 LE)
 *   Then repeating: AttributeID (1 byte) + AttributeLength (uint16 LE) + AttributeData (variable)
 */
class AncsAttributeParser {

    /**
     * Result of parsing attribute data for a notification.
     */
    data class AttributeResult(
        val notificationUID: Int,
        val attributes: Map<Byte, String>
    )

    // Fragment reassembly buffer
    private val reassemblyBuffer = mutableMapOf<Int, ByteArray>()
    private var expectedUID: Int? = null
    private var isReassembling = false

    /**
     * Feed incoming Data Source data. Returns a complete AttributeResult
     * when all fragments for a response have been reassembled, or null
     * if more fragments are expected.
     */
    fun feedData(data: ByteArray): AttributeResult? {
        if (data.isEmpty()) return null

        return if (!isReassembling) {
            // First packet — check if it's a GetNotificationAttributes response
            if (data[0] != AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES) {
                Log.d(TAG, "Ignoring non-GetNotificationAttributes response (commandId=${data[0]})")
                return null
            }

            if (data.size < 5) {
                // Header is incomplete — buffer it and wait for more data
                Log.d(TAG, "Partial header received (${data.size} bytes), buffering...")
                isReassembling = true
                expectedUID = PARTIAL_HEADER_UID
                reassemblyBuffer[PARTIAL_HEADER_UID] = data
                return null
            }

            val uid = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            expectedUID = uid
            isReassembling = true
            reassemblyBuffer[uid] = data

            tryParse(uid)
        } else {
            // Continuation fragment
            val uid = expectedUID ?: return null
            val existing = reassemblyBuffer[uid] ?: return null
            val combined = existing + data

            // If we were buffering a partial header, check if we now have enough for the UID
            if (uid == PARTIAL_HEADER_UID && combined.size >= 5) {
                val realUid = ByteBuffer.wrap(combined, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                reassemblyBuffer.remove(PARTIAL_HEADER_UID)
                expectedUID = realUid
                reassemblyBuffer[realUid] = combined
                return tryParse(realUid)
            }

            reassemblyBuffer[uid] = combined
            tryParse(uid)
        }
    }

    companion object {
        private const val TAG = "AncsAttributeParser"
        /** Sentinel UID used while header is still being assembled */
        private const val PARTIAL_HEADER_UID = -1
    }

    /**
     * Try to parse the reassembled buffer. Returns result if complete,
     * null if more data is needed.
     */
    private fun tryParse(uid: Int): AttributeResult? {
        val data = reassemblyBuffer[uid] ?: return null

        return try {
            val result = parseAttributes(data)
            // Parsing succeeded — clean up
            isReassembling = false
            expectedUID = null
            reassemblyBuffer.remove(uid)
            result
        } catch (e: IncompleteParsing) {
            // Need more fragments
            Log.d(TAG, "Incomplete data for UID=$uid, waiting for more fragments...")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse attributes for UID=$uid", e)
            isReassembling = false
            expectedUID = null
            reassemblyBuffer.remove(uid)
            null
        }
    }

    /**
     * Parse the complete attribute data buffer.
     * Throws IncompleteParsing if the buffer doesn't contain enough data.
     */
    internal fun parseAttributes(data: ByteArray): AttributeResult {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Skip CommandID
        buffer.get()

        // Read NotificationUID
        val uid = buffer.getInt()

        val attributes = mutableMapOf<Byte, String>()

        while (buffer.hasRemaining()) {
            val attrId = buffer.get()

            // All attributes in GetNotificationAttributes response use the
            // standard tuple format: AttributeID + uint16 Length + Value.
            // (Null-terminated AppIdentifier only appears in GetAppAttributes responses.)
            if (buffer.remaining() < 2) throw IncompleteParsing()
            val length = buffer.getShort().toInt() and 0xFFFF

            if (buffer.remaining() < length) throw IncompleteParsing()

            val valueBytes = ByteArray(length)
            buffer.get(valueBytes)
            attributes[attrId] = String(valueBytes, Charsets.UTF_8)
        }

        Log.d(TAG, "Parsed ${attributes.size} attributes for UID=$uid")
        return AttributeResult(uid, attributes)
    }

    /**
     * Apply parsed attributes to an existing notification, returning a new copy.
     */
    fun applyAttributes(notification: AncsNotification, result: AttributeResult): AncsNotification {
        return notification.copy(
            appIdentifier = result.attributes[AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER]
                ?: notification.appIdentifier,
            title = result.attributes[AncsConstants.NOTIFICATION_ATTR_TITLE]
                ?: notification.title,
            subtitle = result.attributes[AncsConstants.NOTIFICATION_ATTR_SUBTITLE]
                ?: notification.subtitle,
            message = result.attributes[AncsConstants.NOTIFICATION_ATTR_MESSAGE]
                ?: notification.message,
            date = result.attributes[AncsConstants.NOTIFICATION_ATTR_DATE]
                ?: notification.date,
            positiveActionLabel = result.attributes[AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL]
                ?: notification.positiveActionLabel,
            negativeActionLabel = result.attributes[AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL]
                ?: notification.negativeActionLabel
        )
    }

    /**
     * Reset the parser state. Call on disconnect or error.
     */
    fun reset() {
        isReassembling = false
        expectedUID = null
        reassemblyBuffer.clear()
    }

    private class IncompleteParsing : Exception("Need more data fragments")
}
