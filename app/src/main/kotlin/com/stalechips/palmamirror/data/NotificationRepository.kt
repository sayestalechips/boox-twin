package com.stalechips.palmamirror.data

import com.stalechips.palmamirror.ancs.AncsNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory repository for ANCS notifications.
 * Stores, updates, and removes notifications by UID.
 * Will be backed by Room database in Phase 5.
 */
class NotificationRepository {

    private val _notifications = MutableStateFlow<List<AncsNotification>>(emptyList())
    val notifications: StateFlow<List<AncsNotification>> = _notifications.asStateFlow()

    private val _activeCall = MutableStateFlow<AncsNotification?>(null)
    val activeCall: StateFlow<AncsNotification?> = _activeCall.asStateFlow()

    private val notificationMap = LinkedHashMap<Int, AncsNotification>()

    /**
     * Add or update a notification.
     */
    @Synchronized
    fun addOrUpdate(notification: AncsNotification) {
        when (notification.eventId) {
            AncsNotification.EventId.ADDED -> {
                notificationMap[notification.uid] = notification
                if (notification.isIncomingCall) {
                    _activeCall.value = notification
                }
            }
            AncsNotification.EventId.MODIFIED -> {
                val existing = notificationMap[notification.uid]
                if (existing != null) {
                    // Preserve attributes from previous version
                    notificationMap[notification.uid] = notification.copy(
                        appIdentifier = notification.appIdentifier ?: existing.appIdentifier,
                        title = notification.title ?: existing.title,
                        subtitle = notification.subtitle ?: existing.subtitle,
                        message = notification.message ?: existing.message,
                        date = notification.date ?: existing.date,
                        appDisplayName = notification.appDisplayName ?: existing.appDisplayName,
                        positiveActionLabel = notification.positiveActionLabel ?: existing.positiveActionLabel,
                        negativeActionLabel = notification.negativeActionLabel ?: existing.negativeActionLabel
                    )
                } else {
                    notificationMap[notification.uid] = notification
                }
            }
            AncsNotification.EventId.REMOVED -> {
                notificationMap.remove(notification.uid)
                if (_activeCall.value?.uid == notification.uid) {
                    _activeCall.value = null
                }
            }
        }
        publishUpdate()
    }

    /**
     * Update a notification with parsed attribute data.
     */
    @Synchronized
    fun updateAttributes(uid: Int, updater: (AncsNotification) -> AncsNotification) {
        val existing = notificationMap[uid] ?: return
        val updated = updater(existing)
        notificationMap[uid] = updated

        if (updated.isIncomingCall) {
            _activeCall.value = updated
        }
        publishUpdate()
    }

    /**
     * Get a notification by UID.
     */
    @Synchronized
    fun get(uid: Int): AncsNotification? = notificationMap[uid]

    /**
     * Remove a notification by UID.
     */
    @Synchronized
    fun remove(uid: Int) {
        notificationMap.remove(uid)
        if (_activeCall.value?.uid == uid) {
            _activeCall.value = null
        }
        publishUpdate()
    }

    /**
     * Mark a notification as read.
     */
    @Synchronized
    fun markRead(uid: Int) {
        val existing = notificationMap[uid] ?: return
        notificationMap[uid] = existing.copy(isRead = true)
        publishUpdate()
    }

    /**
     * Mark a notification as actioned.
     */
    @Synchronized
    fun markActioned(uid: Int) {
        val existing = notificationMap[uid] ?: return
        notificationMap[uid] = existing.copy(isActioned = true)
        if (_activeCall.value?.uid == uid) {
            _activeCall.value = null
        }
        publishUpdate()
    }

    /**
     * Clear the active call.
     */
    fun clearActiveCall() {
        _activeCall.value = null
    }

    /**
     * Clear all notifications.
     */
    @Synchronized
    fun clearAll() {
        notificationMap.clear()
        _activeCall.value = null
        publishUpdate()
    }

    /**
     * Get count of unread notifications.
     */
    @Synchronized
    fun unreadCount(): Int = notificationMap.values.count { !it.isRead }

    /**
     * Get total notification count.
     */
    @Synchronized
    fun count(): Int = notificationMap.size

    private fun publishUpdate() {
        // Sort: calls first, then messages, then others. Newest first within each group.
        _notifications.value = notificationMap.values
            .sortedWith(
                compareBy<AncsNotification> { !it.category.isCall() }
                    .thenBy { !it.category.isMessage() }
                    .thenByDescending { it.receivedAt }
            )
            .toList()
    }
}
