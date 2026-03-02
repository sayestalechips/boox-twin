package com.stalechips.palmamirror.ancs

import com.stalechips.palmamirror.ble.AncsConstants

/**
 * ANCS notification category mapping.
 */
enum class AncsCategory(val id: Byte, val displayName: String, val indicator: String) {
    OTHER(AncsConstants.CATEGORY_ID_OTHER, "Other", "N"),
    INCOMING_CALL(AncsConstants.CATEGORY_ID_INCOMING_CALL, "Incoming Call", "C"),
    MISSED_CALL(AncsConstants.CATEGORY_ID_MISSED_CALL, "Missed Call", "M"),
    VOICEMAIL(AncsConstants.CATEGORY_ID_VOICEMAIL, "Voicemail", "V"),
    SOCIAL(AncsConstants.CATEGORY_ID_SOCIAL, "Social", "S"),
    SCHEDULE(AncsConstants.CATEGORY_ID_SCHEDULE, "Schedule", "A"),
    EMAIL(AncsConstants.CATEGORY_ID_EMAIL, "Email", "E"),
    NEWS(AncsConstants.CATEGORY_ID_NEWS, "News", "W"),
    HEALTH_AND_FITNESS(AncsConstants.CATEGORY_ID_HEALTH_AND_FITNESS, "Health & Fitness", "H"),
    BUSINESS_AND_FINANCE(AncsConstants.CATEGORY_ID_BUSINESS_AND_FINANCE, "Business & Finance", "B"),
    LOCATION(AncsConstants.CATEGORY_ID_LOCATION, "Location", "L"),
    ENTERTAINMENT(AncsConstants.CATEGORY_ID_ENTERTAINMENT, "Entertainment", "T");

    companion object {
        fun fromId(id: Byte): AncsCategory = entries.find { it.id == id } ?: OTHER
    }

    /**
     * Whether this category supports positive action (accept call, reply, etc.)
     */
    fun supportsPositiveAction(): Boolean = when (this) {
        INCOMING_CALL, SOCIAL, EMAIL -> true
        else -> false
    }

    /**
     * Whether this category supports negative action (reject call, dismiss, etc.)
     */
    fun supportsNegativeAction(): Boolean = when (this) {
        INCOMING_CALL, SOCIAL, EMAIL -> true
        else -> false
    }

    /**
     * Whether this is a call-related category requiring full-screen UI.
     */
    fun isCall(): Boolean = this == INCOMING_CALL

    /**
     * Whether this is a message-type category supporting replies.
     */
    fun isMessage(): Boolean = this == SOCIAL
}
