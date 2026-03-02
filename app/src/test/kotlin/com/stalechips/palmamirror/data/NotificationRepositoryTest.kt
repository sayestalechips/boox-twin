package com.stalechips.palmamirror.data

import com.stalechips.palmamirror.ancs.AncsCategory
import com.stalechips.palmamirror.ancs.AncsNotification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationRepositoryTest {

    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        repository = NotificationRepository()
    }

    private fun makeNotification(
        uid: Int,
        eventId: AncsNotification.EventId = AncsNotification.EventId.ADDED,
        category: AncsCategory = AncsCategory.OTHER,
        title: String? = null,
        message: String? = null,
        flags: Int = 0
    ) = AncsNotification(
        uid = uid,
        eventId = eventId,
        eventFlags = flags,
        category = category,
        title = title,
        message = message,
        receivedAt = System.currentTimeMillis()
    )

    @Test
    fun `add notification increases count`() {
        repository.addOrUpdate(makeNotification(1))

        assertEquals(1, repository.count())
        assertEquals(1, repository.notifications.value.size)
    }

    @Test
    fun `add multiple notifications`() {
        repository.addOrUpdate(makeNotification(1))
        repository.addOrUpdate(makeNotification(2))
        repository.addOrUpdate(makeNotification(3))

        assertEquals(3, repository.count())
    }

    @Test
    fun `remove notification decreases count`() {
        repository.addOrUpdate(makeNotification(1))
        repository.addOrUpdate(makeNotification(2))

        repository.addOrUpdate(makeNotification(1, eventId = AncsNotification.EventId.REMOVED))

        assertEquals(1, repository.count())
    }

    @Test
    fun `get notification by uid`() {
        repository.addOrUpdate(makeNotification(42, title = "Test"))

        val notification = repository.get(42)

        assertNotNull(notification)
        assertEquals("Test", notification!!.title)
    }

    @Test
    fun `get nonexistent notification returns null`() {
        assertNull(repository.get(999))
    }

    @Test
    fun `modified event preserves existing attributes`() {
        repository.addOrUpdate(makeNotification(1, title = "Original"))
        repository.updateAttributes(1) { it.copy(message = "Hello") }

        // Modified event without title should preserve existing title
        repository.addOrUpdate(makeNotification(1, eventId = AncsNotification.EventId.MODIFIED))

        val notification = repository.get(1)
        assertNotNull(notification)
        assertEquals("Original", notification!!.title)
        assertEquals("Hello", notification.message)
    }

    @Test
    fun `incoming call sets active call`() {
        val callNotification = makeNotification(
            uid = 10,
            category = AncsCategory.INCOMING_CALL,
            title = "John Doe"
        )

        repository.addOrUpdate(callNotification)

        assertNotNull(repository.activeCall.value)
        assertEquals(10, repository.activeCall.value!!.uid)
    }

    @Test
    fun `removed call clears active call`() {
        repository.addOrUpdate(makeNotification(10, category = AncsCategory.INCOMING_CALL))
        assertNotNull(repository.activeCall.value)

        repository.addOrUpdate(makeNotification(10, eventId = AncsNotification.EventId.REMOVED, category = AncsCategory.INCOMING_CALL))
        assertNull(repository.activeCall.value)
    }

    @Test
    fun `mark read updates notification`() {
        repository.addOrUpdate(makeNotification(1))
        assertFalse(repository.get(1)!!.isRead)

        repository.markRead(1)
        assertTrue(repository.get(1)!!.isRead)
    }

    @Test
    fun `mark actioned updates notification and clears active call`() {
        repository.addOrUpdate(makeNotification(10, category = AncsCategory.INCOMING_CALL))
        assertNotNull(repository.activeCall.value)

        repository.markActioned(10)
        assertTrue(repository.get(10)!!.isActioned)
        assertNull(repository.activeCall.value)
    }

    @Test
    fun `unread count`() {
        repository.addOrUpdate(makeNotification(1))
        repository.addOrUpdate(makeNotification(2))
        repository.addOrUpdate(makeNotification(3))

        assertEquals(3, repository.unreadCount())

        repository.markRead(1)
        assertEquals(2, repository.unreadCount())
    }

    @Test
    fun `clear all removes everything`() {
        repository.addOrUpdate(makeNotification(1))
        repository.addOrUpdate(makeNotification(2))
        repository.addOrUpdate(makeNotification(10, category = AncsCategory.INCOMING_CALL))

        repository.clearAll()

        assertEquals(0, repository.count())
        assertNull(repository.activeCall.value)
        assertTrue(repository.notifications.value.isEmpty())
    }

    @Test
    fun `notifications are sorted calls first then messages then others`() {
        repository.addOrUpdate(makeNotification(1, category = AncsCategory.EMAIL))
        repository.addOrUpdate(makeNotification(2, category = AncsCategory.INCOMING_CALL))
        repository.addOrUpdate(makeNotification(3, category = AncsCategory.SOCIAL))

        val sorted = repository.notifications.value
        assertEquals(AncsCategory.INCOMING_CALL, sorted[0].category)
        assertEquals(AncsCategory.SOCIAL, sorted[1].category) // Message category
        assertEquals(AncsCategory.EMAIL, sorted[2].category) // Other
    }

    @Test
    fun `updateAttributes applies changes`() {
        repository.addOrUpdate(makeNotification(1))

        repository.updateAttributes(1) { it.copy(title = "Updated", message = "New message") }

        val updated = repository.get(1)
        assertEquals("Updated", updated!!.title)
        assertEquals("New message", updated.message)
    }

    @Test
    fun `updateAttributes on nonexistent uid does nothing`() {
        repository.updateAttributes(999) { it.copy(title = "Nothing") }
        assertEquals(0, repository.count())
    }

    @Test
    fun `remove nonexistent uid does nothing`() {
        repository.addOrUpdate(makeNotification(1))
        repository.remove(999)
        assertEquals(1, repository.count())
    }

    @Test
    fun `duplicate add overwrites`() {
        repository.addOrUpdate(makeNotification(1, title = "First"))
        repository.addOrUpdate(makeNotification(1, title = "Second"))

        assertEquals(1, repository.count())
        assertEquals("Second", repository.get(1)!!.title)
    }
}
