package com.stalechips.palmamirror.ancs

import com.stalechips.palmamirror.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AncsControlPointTest {

    @Test
    fun `buildGetNotificationAttributes generates correct command`() {
        val command = AncsControlPoint.buildGetNotificationAttributes(
            notificationUID = 42,
            requestTitle = true,
            maxTitleLength = 128,
            requestSubtitle = true,
            maxSubtitleLength = 64,
            requestMessage = true,
            maxMessageLength = 512,
            requestDate = true,
            requestAppIdentifier = true,
            requestActionLabels = true
        )

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)

        // CommandID
        assertEquals(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES, buffer.get())

        // NotificationUID
        assertEquals(42, buffer.getInt())

        // AppIdentifier (no max length)
        assertEquals(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER, buffer.get())

        // Title + max length
        assertEquals(AncsConstants.NOTIFICATION_ATTR_TITLE, buffer.get())
        assertEquals(128.toShort(), buffer.getShort())

        // Subtitle + max length
        assertEquals(AncsConstants.NOTIFICATION_ATTR_SUBTITLE, buffer.get())
        assertEquals(64.toShort(), buffer.getShort())

        // Message + max length
        assertEquals(AncsConstants.NOTIFICATION_ATTR_MESSAGE, buffer.get())
        assertEquals(512.toShort(), buffer.getShort())

        // Date (no max length)
        assertEquals(AncsConstants.NOTIFICATION_ATTR_DATE, buffer.get())

        // Action labels
        assertEquals(AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL, buffer.get())
        assertEquals(AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL, buffer.get())

        // Should have consumed all bytes
        assertFalse(buffer.hasRemaining())
    }

    @Test
    fun `buildGetNotificationAttributes with minimal attributes`() {
        val command = AncsControlPoint.buildGetNotificationAttributes(
            notificationUID = 1,
            requestTitle = true,
            requestSubtitle = false,
            requestMessage = false,
            requestDate = false,
            requestAppIdentifier = false,
            requestActionLabels = false
        )

        // CommandID(1) + UID(4) + TitleAttr(1) + TitleMaxLen(2) = 8
        assertEquals(8, command.size)

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES, buffer.get())
        assertEquals(1, buffer.getInt())
        assertEquals(AncsConstants.NOTIFICATION_ATTR_TITLE, buffer.get())
        assertEquals(128.toShort(), buffer.getShort())
    }

    @Test
    fun `buildGetAppAttributes generates correct command`() {
        val command = AncsControlPoint.buildGetAppAttributes("com.apple.MobileSMS")

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)

        // CommandID
        assertEquals(AncsConstants.COMMAND_ID_GET_APP_ATTRIBUTES, buffer.get())

        // AppIdentifier (null-terminated)
        val appIdBytes = ByteArray(19) // "com.apple.MobileSMS".length
        buffer.get(appIdBytes)
        assertEquals("com.apple.MobileSMS", String(appIdBytes, Charsets.UTF_8))

        // Null terminator
        assertEquals(0.toByte(), buffer.get())

        // DisplayName attribute
        assertEquals(AncsConstants.APP_ATTR_DISPLAY_NAME, buffer.get())

        assertFalse(buffer.hasRemaining())
    }

    @Test
    fun `buildPerformAction positive generates correct command`() {
        val command = AncsControlPoint.buildPerformAction(notificationUID = 42, positive = true)

        assertEquals(6, command.size)

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AncsConstants.COMMAND_ID_PERFORM_NOTIFICATION_ACTION, buffer.get())
        assertEquals(42, buffer.getInt())
        assertEquals(AncsConstants.ACTION_ID_POSITIVE, buffer.get())
    }

    @Test
    fun `buildPerformAction negative generates correct command`() {
        val command = AncsControlPoint.buildPerformAction(notificationUID = 99, positive = false)

        assertEquals(6, command.size)

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AncsConstants.COMMAND_ID_PERFORM_NOTIFICATION_ACTION, buffer.get())
        assertEquals(99, buffer.getInt())
        assertEquals(AncsConstants.ACTION_ID_NEGATIVE, buffer.get())
    }

    @Test
    fun `buildPerformAction with zero UID`() {
        val command = AncsControlPoint.buildPerformAction(notificationUID = 0, positive = true)

        val buffer = ByteBuffer.wrap(command).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // skip command ID
        assertEquals(0, buffer.getInt())
    }

    @Test
    fun `buildGetAppAttributes with empty app identifier`() {
        val command = AncsControlPoint.buildGetAppAttributes("")

        // CommandID(1) + null terminator(1) + AttrID(1) = 3
        assertEquals(3, command.size)
    }
}
