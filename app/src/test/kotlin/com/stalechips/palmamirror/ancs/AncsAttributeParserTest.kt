package com.stalechips.palmamirror.ancs

import com.stalechips.palmamirror.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AncsAttributeParserTest {

    private lateinit var parser: AncsAttributeParser

    @Before
    fun setUp() {
        parser = AncsAttributeParser()
    }

    private fun buildAttributeResponse(
        uid: Int,
        appId: String = "com.test.app",
        title: String = "Test Title",
        subtitle: String = "Sub",
        message: String = "Hello World"
    ): ByteArray {
        val appIdBytes = appId.toByteArray(Charsets.UTF_8)
        val titleBytes = title.toByteArray(Charsets.UTF_8)
        val subtitleBytes = subtitle.toByteArray(Charsets.UTF_8)
        val messageBytes = message.toByteArray(Charsets.UTF_8)

        val size = 5 + // CommandID + UID
                1 + 2 + appIdBytes.size + // AppIdentifier (attr + len + data)
                1 + 2 + titleBytes.size + // Title (attr + len + data)
                1 + 2 + subtitleBytes.size + // Subtitle
                1 + 2 + messageBytes.size // Message

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(uid)

        // AppIdentifier (standard tuple: attr + uint16 length + data)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)
        buffer.putShort(appIdBytes.size.toShort())
        buffer.put(appIdBytes)

        // Title
        buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
        buffer.putShort(titleBytes.size.toShort())
        buffer.put(titleBytes)

        // Subtitle
        buffer.put(AncsConstants.NOTIFICATION_ATTR_SUBTITLE)
        buffer.putShort(subtitleBytes.size.toShort())
        buffer.put(subtitleBytes)

        // Message
        buffer.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE)
        buffer.putShort(messageBytes.size.toShort())
        buffer.put(messageBytes)

        return buffer.array()
    }

    @Test
    fun `parse single-packet response`() {
        val data = buildAttributeResponse(uid = 42, title = "Hello", message = "World")

        val result = parser.feedData(data)

        assertNotNull(result)
        assertEquals(42, result!!.notificationUID)
        assertEquals("com.test.app", result.attributes[AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER])
        assertEquals("Hello", result.attributes[AncsConstants.NOTIFICATION_ATTR_TITLE])
        assertEquals("World", result.attributes[AncsConstants.NOTIFICATION_ATTR_MESSAGE])
    }

    @Test
    fun `parse fragmented response (two packets)`() {
        val fullData = buildAttributeResponse(uid = 10, title = "Fragmented", message = "Data")

        // Split into two fragments
        val splitPoint = fullData.size / 2
        val fragment1 = fullData.copyOfRange(0, splitPoint)
        val fragment2 = fullData.copyOfRange(splitPoint, fullData.size)

        // First fragment — should return null (incomplete)
        val result1 = parser.feedData(fragment1)
        assertNull(result1)

        // Second fragment — should complete the parse
        val result2 = parser.feedData(fragment2)
        assertNotNull(result2)
        assertEquals(10, result2!!.notificationUID)
        assertEquals("Fragmented", result2.attributes[AncsConstants.NOTIFICATION_ATTR_TITLE])
        assertEquals("Data", result2.attributes[AncsConstants.NOTIFICATION_ATTR_MESSAGE])
    }

    @Test
    fun `parse fragmented response where first fragment has partial header`() {
        val fullData = buildAttributeResponse(uid = 20, title = "Fragmented", message = "Content")

        // First fragment: only 3 bytes (partial header — needs 5 for CommandID + UID)
        val frag1 = fullData.copyOfRange(0, 3)
        // Second fragment: rest of the data
        val frag2 = fullData.copyOfRange(3, fullData.size)

        assertNull(parser.feedData(frag1))

        val result = parser.feedData(frag2)
        assertNotNull(result)
        assertEquals(20, result!!.notificationUID)
        assertEquals("Fragmented", result.attributes[AncsConstants.NOTIFICATION_ATTR_TITLE])
    }

    @Test
    fun `parse response with empty attributes`() {
        val buffer = ByteBuffer.allocate(5 + 3 + 3).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(50)

        // Title with length 0
        buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
        buffer.putShort(0)

        // Message with length 0
        buffer.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE)
        buffer.putShort(0)

        val result = parser.feedData(buffer.array())

        assertNotNull(result)
        assertEquals("", result!!.attributes[AncsConstants.NOTIFICATION_ATTR_TITLE])
        assertEquals("", result.attributes[AncsConstants.NOTIFICATION_ATTR_MESSAGE])
    }

    @Test
    fun `applyAttributes updates notification correctly`() {
        val notification = AncsNotification(
            uid = 42,
            eventId = AncsNotification.EventId.ADDED,
            eventFlags = 0x18,
            category = AncsCategory.INCOMING_CALL
        )

        val result = AncsAttributeParser.AttributeResult(
            notificationUID = 42,
            attributes = mapOf(
                AncsConstants.NOTIFICATION_ATTR_TITLE to "John Doe",
                AncsConstants.NOTIFICATION_ATTR_MESSAGE to "+1 555 123 4567",
                AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER to "com.apple.mobilephone",
                AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL to "Accept",
                AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL to "Decline"
            )
        )

        val updated = parser.applyAttributes(notification, result)

        assertEquals("John Doe", updated.title)
        assertEquals("+1 555 123 4567", updated.message)
        assertEquals("com.apple.mobilephone", updated.appIdentifier)
        assertEquals("Accept", updated.positiveActionLabel)
        assertEquals("Decline", updated.negativeActionLabel)
        assertEquals(42, updated.uid)
        assertEquals(AncsCategory.INCOMING_CALL, updated.category)
    }

    @Test
    fun `reset clears reassembly state`() {
        val fullData = buildAttributeResponse(uid = 99)
        val fragment = fullData.copyOfRange(0, 10)

        parser.feedData(fragment) // Start reassembly
        parser.reset()

        // After reset, this should be treated as a new packet
        val freshData = buildAttributeResponse(uid = 100)
        val result = parser.feedData(freshData)
        assertNotNull(result)
        assertEquals(100, result!!.notificationUID)
    }

    @Test
    fun `ignores non-GetNotificationAttributes responses`() {
        val data = byteArrayOf(1, 0, 0, 0, 0) // CommandID = 1 (GetAppAttributes)
        val result = parser.feedData(data)
        assertNull(result)
    }

    @Test
    fun `returns null for empty data`() {
        val result = parser.feedData(byteArrayOf())
        assertNull(result)
    }

    @Test
    fun `returns null for data too short for UID`() {
        val data = byteArrayOf(0, 1, 2) // CommandID + 2 bytes (need 4 for UID)
        val result = parser.feedData(data)
        assertNull(result)
    }
}
