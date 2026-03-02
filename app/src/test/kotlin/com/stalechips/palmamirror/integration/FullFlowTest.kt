package com.stalechips.palmamirror.integration

import com.stalechips.palmamirror.ancs.AncsAttributeParser
import com.stalechips.palmamirror.ancs.AncsCategory
import com.stalechips.palmamirror.ancs.AncsEventParser
import com.stalechips.palmamirror.ancs.AncsNotification
import com.stalechips.palmamirror.ancs.CallStateManager
import com.stalechips.palmamirror.ble.AncsConstants
import com.stalechips.palmamirror.data.NotificationRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Integration tests exercising the full notification flow:
 * Event parsing -> Repository storage -> Attribute parsing -> Repository update.
 *
 * Uses in-memory components only (no Room, no Context).
 * JUnit 4 with isReturnDefaultValues = true for Android stubs.
 */
class FullFlowTest {

    private lateinit var repository: NotificationRepository
    private lateinit var eventParser: AncsEventParser
    private lateinit var attributeParser: AncsAttributeParser
    private lateinit var callStateManager: CallStateManager

    @Before
    fun setUp() {
        repository = NotificationRepository()
        eventParser = AncsEventParser()
        attributeParser = AncsAttributeParser()
        callStateManager = CallStateManager()
    }

    // --- Helper methods ---

    /**
     * Build an 8-byte Notification Source event.
     */
    private fun buildNsEvent(
        eventId: Byte = AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
        flags: Byte = 0,
        categoryId: Byte = AncsConstants.CATEGORY_ID_OTHER,
        categoryCount: Byte = 1,
        uid: Int = 1
    ): ByteArray {
        val buffer = ByteBuffer.allocate(AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(eventId)
        buffer.put(flags)
        buffer.put(categoryId)
        buffer.put(categoryCount)
        buffer.putInt(uid)
        return buffer.array()
    }

    /**
     * Build a Data Source response with notification attributes.
     * Format: CommandID(1) + UID(4) + [AttrID(1) + Length(2) + Data(variable)]*
     * All attributes use the standard tuple format (AttrID + uint16 Length + Data).
     */
    private fun buildDsResponse(
        uid: Int,
        appIdentifier: String? = null,
        title: String? = null,
        subtitle: String? = null,
        message: String? = null
    ): ByteArray {
        val parts = mutableListOf<ByteArray>()

        // Header: CommandID + UID
        val header = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        header.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        header.putInt(uid)
        parts.add(header.array())

        // AppIdentifier (standard tuple: AttrID + uint16 Length + Data)
        if (appIdentifier != null) {
            val appIdBytes = appIdentifier.toByteArray(Charsets.UTF_8)
            val appData = ByteBuffer.allocate(1 + 2 + appIdBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            appData.put(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)
            appData.putShort(appIdBytes.size.toShort())
            appData.put(appIdBytes)
            parts.add(appData.array())
        }

        // Title (length-prefixed)
        if (title != null) {
            val titleBytes = title.toByteArray(Charsets.UTF_8)
            val titleData = ByteBuffer.allocate(1 + 2 + titleBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            titleData.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
            titleData.putShort(titleBytes.size.toShort())
            titleData.put(titleBytes)
            parts.add(titleData.array())
        }

        // Subtitle (length-prefixed)
        if (subtitle != null) {
            val subtitleBytes = subtitle.toByteArray(Charsets.UTF_8)
            val subtitleData = ByteBuffer.allocate(1 + 2 + subtitleBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            subtitleData.put(AncsConstants.NOTIFICATION_ATTR_SUBTITLE)
            subtitleData.putShort(subtitleBytes.size.toShort())
            subtitleData.put(subtitleBytes)
            parts.add(subtitleData.array())
        }

        // Message (length-prefixed)
        if (message != null) {
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val messageData = ByteBuffer.allocate(1 + 2 + messageBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            messageData.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE)
            messageData.putShort(messageBytes.size.toShort())
            messageData.put(messageBytes)
            parts.add(messageData.array())
        }

        // Concatenate all parts
        val totalSize = parts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            System.arraycopy(part, 0, result, offset, part.size)
            offset += part.size
        }
        return result
    }

    // --- Full notification flow ---

    @Test
    fun `full flow - parse NS event, add to repository, parse DS attributes, update repository`() {
        // Step 1: Receive a Notification Source event
        val nsData = buildNsEvent(
            eventId = AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
            categoryId = AncsConstants.CATEGORY_ID_SOCIAL,
            uid = 42
        )

        val notification = eventParser.parse(nsData)
        assertNotNull("NS event should parse successfully", notification)

        // Step 2: Add to repository
        repository.addOrUpdate(notification!!)
        assertEquals(1, repository.count())
        assertNull("Title should be null before DS response", repository.get(42)?.title)

        // Step 3: Receive Data Source response with attributes
        val dsData = buildDsResponse(
            uid = 42,
            appIdentifier = "com.apple.MobileSMS",
            title = "Jane Smith",
            message = "Hey, are you coming to dinner?"
        )

        val attrResult = attributeParser.feedData(dsData)
        assertNotNull("DS response should parse completely", attrResult)

        // Step 4: Apply attributes to the notification in the repository
        repository.updateAttributes(42) { existing ->
            attributeParser.applyAttributes(existing, attrResult!!)
        }

        // Step 5: Verify the notification has all data
        val updated = repository.get(42)
        assertNotNull(updated)
        assertEquals("Jane Smith", updated!!.title)
        assertEquals("Hey, are you coming to dinner?", updated.message)
        assertEquals("com.apple.MobileSMS", updated.appIdentifier)
        assertEquals(AncsCategory.SOCIAL, updated.category)
    }

    // --- Call flow ---

    @Test
    fun `call flow - incoming call, accept, verify state`() {
        // Step 1: Incoming call notification
        val nsData = buildNsEvent(
            eventId = AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
            flags = (AncsConstants.EVENT_FLAG_POSITIVE_ACTION.toInt() or
                    AncsConstants.EVENT_FLAG_NEGATIVE_ACTION.toInt()).toByte(),
            categoryId = AncsConstants.CATEGORY_ID_INCOMING_CALL,
            uid = 100
        )

        val notification = eventParser.parse(nsData)
        assertNotNull(notification)
        assertTrue("Should be an incoming call", notification!!.isIncomingCall)

        // Step 2: Add to repository (sets active call)
        repository.addOrUpdate(notification)
        assertNotNull("Active call should be set", repository.activeCall.value)
        assertEquals(100, repository.activeCall.value!!.uid)

        // Step 3: Parse caller attributes
        val dsData = buildDsResponse(
            uid = 100,
            title = "John Doe"
        )
        val attrResult = attributeParser.feedData(dsData)
        assertNotNull(attrResult)

        repository.updateAttributes(100) { existing ->
            attributeParser.applyAttributes(existing, attrResult!!)
        }

        // Step 4: Trigger call state manager
        val callerName = repository.get(100)?.title
        callStateManager.onIncomingCall(100, callerName, null)
        assertEquals(CallStateManager.State.RINGING, callStateManager.state.value)

        // Step 5: Accept the call
        callStateManager.acceptCall()
        assertEquals(CallStateManager.State.ACCEPTED, callStateManager.state.value)

        // Step 6: Mark actioned in repository (clears active call)
        repository.markActioned(100)
        assertTrue(repository.get(100)!!.isActioned)
        assertNull("Active call should be cleared after action", repository.activeCall.value)
    }

    // --- Rapid notification burst ---

    @Test
    fun `rapid notifications - 100 notifications added and counted correctly`() {
        for (i in 1..100) {
            val nsData = buildNsEvent(
                eventId = AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
                categoryId = AncsConstants.CATEGORY_ID_EMAIL,
                uid = i
            )

            val notification = eventParser.parse(nsData)
            assertNotNull("Notification $i should parse", notification)
            repository.addOrUpdate(notification!!)
        }

        assertEquals("All 100 notifications should be stored", 100, repository.count())
        assertEquals("All 100 should be unread", 100, repository.unreadCount())

        // Verify the notifications list is populated
        assertEquals(100, repository.notifications.value.size)

        // Mark some as read
        for (i in 1..25) {
            repository.markRead(i)
        }
        assertEquals("75 should remain unread", 75, repository.unreadCount())
    }

    // --- Notification removal ---

    @Test
    fun `notification removal - removed event clears from repository`() {
        // Add three notifications
        for (i in 1..3) {
            val nsData = buildNsEvent(uid = i)
            val notification = eventParser.parse(nsData)!!
            repository.addOrUpdate(notification)
        }
        assertEquals(3, repository.count())

        // Remove notification UID=2 via a REMOVED event
        val removeEvent = buildNsEvent(
            eventId = AncsConstants.EVENT_ID_NOTIFICATION_REMOVED,
            uid = 2
        )
        val removeNotification = eventParser.parse(removeEvent)!!
        assertEquals(AncsNotification.EventId.REMOVED, removeNotification.eventId)

        repository.addOrUpdate(removeNotification)

        assertEquals("Count should be 2 after removal", 2, repository.count())
        assertNull("UID 2 should be gone", repository.get(2))
        assertNotNull("UID 1 should still exist", repository.get(1))
        assertNotNull("UID 3 should still exist", repository.get(3))
    }

    @Test
    fun `notification removal - removing active call clears it`() {
        // Add an incoming call
        val nsData = buildNsEvent(
            eventId = AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
            categoryId = AncsConstants.CATEGORY_ID_INCOMING_CALL,
            uid = 50
        )
        repository.addOrUpdate(eventParser.parse(nsData)!!)
        assertNotNull(repository.activeCall.value)

        // Remove it
        val removeData = buildNsEvent(
            eventId = AncsConstants.EVENT_ID_NOTIFICATION_REMOVED,
            categoryId = AncsConstants.CATEGORY_ID_INCOMING_CALL,
            uid = 50
        )
        repository.addOrUpdate(eventParser.parse(removeData)!!)

        assertNull("Active call should be cleared", repository.activeCall.value)
        assertEquals(0, repository.count())
    }

    // --- Entity conversion ---

    @Test
    fun `entity round-trip preserves all fields`() {
        val original = AncsNotification(
            uid = 7,
            eventId = AncsNotification.EventId.ADDED,
            eventFlags = 0x1A,
            category = AncsCategory.EMAIL,
            categoryCount = 3,
            appIdentifier = "com.apple.mobilemail",
            title = "Meeting Update",
            subtitle = "RE: Sprint Planning",
            message = "The meeting has been moved to 3pm.",
            date = "20260301T150000",
            positiveActionLabel = "Reply",
            negativeActionLabel = "Delete",
            appDisplayName = "Mail",
            receivedAt = 1709312400000L,
            isRead = false,
            isActioned = false
        )

        val entity = com.stalechips.palmamirror.data.NotificationEntity.fromAncsNotification(original)
        val restored = entity.toAncsNotification()

        assertEquals(original.uid, restored.uid)
        assertEquals(original.eventId, restored.eventId)
        assertEquals(original.eventFlags, restored.eventFlags)
        assertEquals(original.category, restored.category)
        assertEquals(original.categoryCount, restored.categoryCount)
        assertEquals(original.appIdentifier, restored.appIdentifier)
        assertEquals(original.title, restored.title)
        assertEquals(original.subtitle, restored.subtitle)
        assertEquals(original.message, restored.message)
        assertEquals(original.date, restored.date)
        assertEquals(original.positiveActionLabel, restored.positiveActionLabel)
        assertEquals(original.negativeActionLabel, restored.negativeActionLabel)
        assertEquals(original.appDisplayName, restored.appDisplayName)
        assertEquals(original.receivedAt, restored.receivedAt)
        assertEquals(original.isRead, restored.isRead)
        assertEquals(original.isActioned, restored.isActioned)
    }

    @Test
    fun `entity round-trip handles null optional fields`() {
        val original = AncsNotification(
            uid = 99,
            eventId = AncsNotification.EventId.MODIFIED,
            eventFlags = 0,
            category = AncsCategory.OTHER,
            categoryCount = 0,
            receivedAt = 1709312400000L
        )

        val entity = com.stalechips.palmamirror.data.NotificationEntity.fromAncsNotification(original)
        val restored = entity.toAncsNotification()

        assertEquals(original.uid, restored.uid)
        assertNull(restored.appIdentifier)
        assertNull(restored.title)
        assertNull(restored.subtitle)
        assertNull(restored.message)
        assertNull(restored.date)
        assertNull(restored.positiveActionLabel)
        assertNull(restored.negativeActionLabel)
        assertNull(restored.appDisplayName)
    }
}
