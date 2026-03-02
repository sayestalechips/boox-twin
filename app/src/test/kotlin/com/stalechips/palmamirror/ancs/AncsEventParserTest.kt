package com.stalechips.palmamirror.ancs

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AncsEventParserTest {

    private lateinit var parser: AncsEventParser

    @Before
    fun setUp() {
        parser = AncsEventParser()
    }

    @Test
    fun `parse valid notification added event`() {
        // EventID=0 (Added), Flags=0x18 (PositiveAction+NegativeAction),
        // CategoryID=1 (IncomingCall), CategoryCount=1, UID=42
        val data = byteArrayOf(0, 0x18, 1, 1, 42, 0, 0, 0)

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertEquals(AncsNotification.EventId.ADDED, notification!!.eventId)
        assertEquals(0x18, notification.eventFlags)
        assertEquals(AncsCategory.INCOMING_CALL, notification.category)
        assertEquals(1, notification.categoryCount)
        assertEquals(42, notification.uid)
        assertTrue(notification.hasPositiveAction)
        assertTrue(notification.hasNegativeAction)
        assertFalse(notification.isSilent)
        assertFalse(notification.isImportant)
    }

    @Test
    fun `parse notification removed event`() {
        // EventID=2 (Removed), Flags=0, CategoryID=4 (Social), Count=0, UID=100
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(2) // Removed
        buffer.put(0) // No flags
        buffer.put(4) // Social
        buffer.put(0) // Count 0
        buffer.putInt(100) // UID

        val notification = parser.parse(buffer.array())

        assertNotNull(notification)
        assertEquals(AncsNotification.EventId.REMOVED, notification!!.eventId)
        assertEquals(AncsCategory.SOCIAL, notification.category)
        assertEquals(100, notification.uid)
    }

    @Test
    fun `parse notification modified event`() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(1) // Modified
        buffer.put(0x02) // Important
        buffer.put(6) // Email
        buffer.put(3) // Count
        buffer.putInt(999)

        val notification = parser.parse(buffer.array())

        assertNotNull(notification)
        assertEquals(AncsNotification.EventId.MODIFIED, notification!!.eventId)
        assertTrue(notification.isImportant)
        assertEquals(AncsCategory.EMAIL, notification.category)
        assertEquals(3, notification.categoryCount)
        assertEquals(999, notification.uid)
    }

    @Test
    fun `parse with all flags set`() {
        val data = byteArrayOf(0, 0x1F.toByte(), 0, 0, 1, 0, 0, 0)

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertTrue(notification!!.isSilent)
        assertTrue(notification.isImportant)
        assertTrue(notification.isPreExisting)
        assertTrue(notification.hasPositiveAction)
        assertTrue(notification.hasNegativeAction)
    }

    @Test
    fun `parse incoming call is detected`() {
        val data = byteArrayOf(0, 0x18, 1, 1, 5, 0, 0, 0)

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertTrue(notification!!.isIncomingCall)
    }

    @Test
    fun `removed incoming call is not active call`() {
        val data = byteArrayOf(2, 0x18, 1, 1, 5, 0, 0, 0)

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertFalse(notification!!.isIncomingCall) // Removed, not Added
    }

    @Test
    fun `parse with large notification UID`() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0) // Added
        buffer.put(0) // No flags
        buffer.put(0) // Other
        buffer.put(0) // Count
        buffer.putInt(0x7FFFFFFF) // Max positive int32

        val notification = parser.parse(buffer.array())

        assertNotNull(notification)
        assertEquals(0x7FFFFFFF, notification!!.uid)
    }

    @Test
    fun `parse returns null for too-short data`() {
        val data = byteArrayOf(0, 1, 2) // Only 3 bytes, need 8

        val notification = parser.parse(data)

        assertNull(notification)
    }

    @Test
    fun `parse returns null for empty data`() {
        val notification = parser.parse(byteArrayOf())
        assertNull(notification)
    }

    @Test
    fun `parse handles unknown category ID gracefully`() {
        val data = byteArrayOf(0, 0, 99.toByte(), 0, 1, 0, 0, 0) // Category 99 doesn't exist

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertEquals(AncsCategory.OTHER, notification!!.category) // Falls back to OTHER
    }

    @Test
    fun `parse all category types`() {
        for (i in 0..11) {
            val data = byteArrayOf(0, 0, i.toByte(), 0, 1, 0, 0, 0)
            val notification = parser.parse(data)
            assertNotNull("Category $i should parse", notification)
            assertEquals(AncsCategory.fromId(i.toByte()), notification!!.category)
        }
    }

    @Test
    fun `parse handles extra bytes gracefully`() {
        // 10 bytes instead of 8 — should still parse first 8
        val data = byteArrayOf(0, 0, 0, 0, 1, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte())

        val notification = parser.parse(data)

        assertNotNull(notification)
        assertEquals(1, notification!!.uid)
    }
}
