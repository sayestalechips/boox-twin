package com.stalechips.palmamirror.ancs

/**
 * Represents a parsed ANCS notification with all available attributes.
 */
data class AncsNotification(
    /** Unique identifier assigned by the iPhone (uint32) */
    val uid: Int,

    /** Notification event type */
    val eventId: EventId,

    /** Event flags bitmask */
    val eventFlags: Int,

    /** Notification category */
    val category: AncsCategory,

    /** Category count from Notification Source */
    val categoryCount: Int = 0,

    /** App bundle identifier (e.g., "com.apple.MobileSMS") */
    val appIdentifier: String? = null,

    /** Notification title */
    val title: String? = null,

    /** Notification subtitle */
    val subtitle: String? = null,

    /** Notification message body */
    val message: String? = null,

    /** Notification date (YYYYMMDDTHHMMSS format) */
    val date: String? = null,

    /** Label for positive action button (e.g., "Accept", "Reply") */
    val positiveActionLabel: String? = null,

    /** Label for negative action button (e.g., "Reject", "Dismiss") */
    val negativeActionLabel: String? = null,

    /** App display name resolved via GetAppAttributes */
    val appDisplayName: String? = null,

    /** Timestamp when this notification was received locally */
    val receivedAt: Long = System.currentTimeMillis(),

    /** Whether the user has read/seen this notification */
    val isRead: Boolean = false,

    /** Whether an action has been performed on this notification */
    val isActioned: Boolean = false
) {

    enum class EventId(val value: Byte) {
        ADDED(0),
        MODIFIED(1),
        REMOVED(2);

        companion object {
            fun fromByte(value: Byte): EventId = entries.find { it.value == value } ?: ADDED
        }
    }

    val isSilent: Boolean get() = (eventFlags and 0x01) != 0
    val isImportant: Boolean get() = (eventFlags and 0x02) != 0
    val isPreExisting: Boolean get() = (eventFlags and 0x04) != 0
    val hasPositiveAction: Boolean get() = (eventFlags and 0x08) != 0
    val hasNegativeAction: Boolean get() = (eventFlags and 0x10) != 0

    /**
     * Display title — uses title, appDisplayName, or appIdentifier as fallback.
     */
    val displayTitle: String
        get() = title ?: appDisplayName ?: appIdentifier ?: category.displayName

    /**
     * Whether this notification should trigger the full-screen call UI.
     */
    val isIncomingCall: Boolean
        get() = category.isCall() && eventId == EventId.ADDED
}
