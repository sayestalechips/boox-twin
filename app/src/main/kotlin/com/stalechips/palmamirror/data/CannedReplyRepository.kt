package com.stalechips.palmamirror.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory repository for canned (quick) replies.
 * Provides a default set of replies and supports add, remove, and reorder operations.
 * Thread-safe: all mutations are synchronized.
 */
class CannedReplyRepository {

    companion object {
        val DEFAULT_REPLIES = listOf(
            "On my way",
            "Be right there",
            "Can't talk right now",
            "I'll call you back",
            "OK",
            "Thanks"
        )
    }

    private val replies = DEFAULT_REPLIES.toMutableList()

    private val _repliesFlow = MutableStateFlow<List<String>>(replies.toList())
    val repliesFlow: StateFlow<List<String>> = _repliesFlow.asStateFlow()

    /**
     * Returns a snapshot of all current canned replies.
     */
    @Synchronized
    fun getAll(): List<String> = replies.toList()

    /**
     * Adds a new canned reply to the end of the list.
     */
    @Synchronized
    fun add(reply: String) {
        replies.add(reply)
        publishUpdate()
    }

    /**
     * Removes the canned reply at the given index.
     *
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    @Synchronized
    fun remove(index: Int) {
        replies.removeAt(index)
        publishUpdate()
    }

    /**
     * Moves a canned reply from [fromIndex] to [toIndex].
     *
     * @throws IndexOutOfBoundsException if either index is out of range.
     */
    @Synchronized
    fun reorder(fromIndex: Int, toIndex: Int) {
        val item = replies.removeAt(fromIndex)
        replies.add(toIndex, item)
        publishUpdate()
    }

    private fun publishUpdate() {
        _repliesFlow.value = replies.toList()
    }
}
