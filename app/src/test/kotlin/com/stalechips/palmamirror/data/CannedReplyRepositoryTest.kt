package com.stalechips.palmamirror.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CannedReplyRepository].
 */
class CannedReplyRepositoryTest {

    private lateinit var repository: CannedReplyRepository

    @Before
    fun setUp() {
        repository = CannedReplyRepository()
    }

    // --- Default replies ---

    @Test
    fun `defaults are loaded on creation`() {
        val replies = repository.getAll()

        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size, replies.size)
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES, replies)
    }

    @Test
    fun `defaults include expected entries`() {
        val replies = repository.getAll()

        assertTrue(replies.contains("On my way"))
        assertTrue(replies.contains("Be right there"))
        assertTrue(replies.contains("Can't talk right now"))
        assertTrue(replies.contains("I'll call you back"))
        assertTrue(replies.contains("OK"))
        assertTrue(replies.contains("Thanks"))
    }

    @Test
    fun `stateFlow reflects defaults on creation`() {
        val flowValue = repository.repliesFlow.value

        assertEquals(CannedReplyRepository.DEFAULT_REPLIES, flowValue)
    }

    // --- Add ---

    @Test
    fun `add reply appends to end of list`() {
        repository.add("Running late")

        val replies = repository.getAll()
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 1, replies.size)
        assertEquals("Running late", replies.last())
    }

    @Test
    fun `add multiple replies`() {
        repository.add("First")
        repository.add("Second")

        val replies = repository.getAll()
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 2, replies.size)
        assertEquals("First", replies[replies.size - 2])
        assertEquals("Second", replies.last())
    }

    @Test
    fun `add reply updates stateFlow`() {
        repository.add("New reply")

        val flowValue = repository.repliesFlow.value
        assertTrue(flowValue.contains("New reply"))
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 1, flowValue.size)
    }

    // --- Remove ---

    @Test
    fun `remove reply by index`() {
        val originalSize = repository.getAll().size
        val firstReply = repository.getAll()[0]

        repository.remove(0)

        val replies = repository.getAll()
        assertEquals(originalSize - 1, replies.size)
        assertFalse(replies.contains(firstReply))
    }

    @Test
    fun `remove last reply`() {
        val originalSize = repository.getAll().size
        val lastReply = repository.getAll().last()

        repository.remove(originalSize - 1)

        val replies = repository.getAll()
        assertEquals(originalSize - 1, replies.size)
        assertFalse(replies.contains(lastReply))
    }

    @Test
    fun `remove reply updates stateFlow`() {
        val removedReply = repository.getAll()[0]

        repository.remove(0)

        val flowValue = repository.repliesFlow.value
        assertFalse(flowValue.contains(removedReply))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `remove with invalid index throws`() {
        repository.remove(100)
    }

    // --- Reorder ---

    @Test
    fun `reorder moves item forward`() {
        // Move first item to index 2
        val firstReply = repository.getAll()[0]

        repository.reorder(0, 2)

        val replies = repository.getAll()
        assertEquals(firstReply, replies[2])
    }

    @Test
    fun `reorder moves item backward`() {
        val lastIndex = repository.getAll().size - 1
        val lastReply = repository.getAll()[lastIndex]

        repository.reorder(lastIndex, 0)

        val replies = repository.getAll()
        assertEquals(lastReply, replies[0])
    }

    @Test
    fun `reorder preserves list size`() {
        val originalSize = repository.getAll().size

        repository.reorder(0, 3)

        assertEquals(originalSize, repository.getAll().size)
    }

    @Test
    fun `reorder updates stateFlow`() {
        val firstReply = repository.getAll()[0]

        repository.reorder(0, 2)

        val flowValue = repository.repliesFlow.value
        assertEquals(firstReply, flowValue[2])
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun `reorder with invalid fromIndex throws`() {
        repository.reorder(100, 0)
    }

    // --- StateFlow reflects all changes ---

    @Test
    fun `stateFlow reflects sequential add and remove`() {
        repository.add("Custom 1")
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 1, repository.repliesFlow.value.size)

        repository.add("Custom 2")
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 2, repository.repliesFlow.value.size)

        // Remove the first default
        repository.remove(0)
        assertEquals(CannedReplyRepository.DEFAULT_REPLIES.size + 1, repository.repliesFlow.value.size)
        assertFalse(repository.repliesFlow.value.contains(CannedReplyRepository.DEFAULT_REPLIES[0]))
        assertTrue(repository.repliesFlow.value.contains("Custom 1"))
        assertTrue(repository.repliesFlow.value.contains("Custom 2"))
    }

    @Test
    fun `getAll returns defensive copy`() {
        val replies = repository.getAll()
        val originalSize = replies.size

        // The returned list should not be the internal list
        repository.add("Extra")

        // Original snapshot should be unchanged
        assertEquals(originalSize, replies.size)
        // Repository should have the new item
        assertEquals(originalSize + 1, repository.getAll().size)
    }
}
