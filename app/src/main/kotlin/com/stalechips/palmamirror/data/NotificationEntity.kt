package com.stalechips.palmamirror.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stalechips.palmamirror.ancs.AncsCategory
import com.stalechips.palmamirror.ancs.AncsNotification

/**
 * Room entity representing a persisted ANCS notification.
 * Maps 1:1 with [AncsNotification] but stores enum values as primitives
 * for SQLite compatibility.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val uid: Int,
    val eventId: Int,
    val eventFlags: Int,
    val categoryId: Int,
    val categoryCount: Int,
    val appIdentifier: String?,
    val title: String?,
    val subtitle: String?,
    val message: String?,
    val date: String?,
    val positiveActionLabel: String?,
    val negativeActionLabel: String?,
    val appDisplayName: String?,
    val receivedAt: Long,
    val isRead: Boolean,
    val isActioned: Boolean
) {

    /**
     * Convert this entity to an [AncsNotification] domain model.
     */
    fun toAncsNotification(): AncsNotification = AncsNotification(
        uid = uid,
        eventId = AncsNotification.EventId.fromByte(eventId.toByte()),
        eventFlags = eventFlags,
        category = AncsCategory.fromId(categoryId.toByte()),
        categoryCount = categoryCount,
        appIdentifier = appIdentifier,
        title = title,
        subtitle = subtitle,
        message = message,
        date = date,
        positiveActionLabel = positiveActionLabel,
        negativeActionLabel = negativeActionLabel,
        appDisplayName = appDisplayName,
        receivedAt = receivedAt,
        isRead = isRead,
        isActioned = isActioned
    )

    companion object {

        /**
         * Create a [NotificationEntity] from an [AncsNotification] domain model.
         */
        fun fromAncsNotification(notification: AncsNotification): NotificationEntity =
            NotificationEntity(
                uid = notification.uid,
                eventId = notification.eventId.value.toInt(),
                eventFlags = notification.eventFlags,
                categoryId = notification.category.id.toInt(),
                categoryCount = notification.categoryCount,
                appIdentifier = notification.appIdentifier,
                title = notification.title,
                subtitle = notification.subtitle,
                message = notification.message,
                date = notification.date,
                positiveActionLabel = notification.positiveActionLabel,
                negativeActionLabel = notification.negativeActionLabel,
                appDisplayName = notification.appDisplayName,
                receivedAt = notification.receivedAt,
                isRead = notification.isRead,
                isActioned = notification.isActioned
            )
    }
}
