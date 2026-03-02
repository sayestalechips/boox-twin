package com.stalechips.palmamirror.ble

import java.util.UUID

/**
 * Apple Notification Center Service (ANCS) protocol constants.
 * Reference: Apple ANCS Specification + InfiniTime implementation.
 */
object AncsConstants {

    // --- Service UUIDs ---

    /** ANCS Service UUID */
    val ANCS_SERVICE_UUID: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")

    /** Apple Media Service UUID (future use) */
    val AMS_SERVICE_UUID: UUID = UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC")

    // --- ANCS Characteristic UUIDs ---

    /** Notification Source: real-time notification events (8 bytes each) */
    val NOTIFICATION_SOURCE_UUID: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")

    /** Control Point: send commands (request attributes, perform actions) */
    val CONTROL_POINT_UUID: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")

    /** Data Source: receive attribute data responses */
    val DATA_SOURCE_UUID: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")

    /** Client Characteristic Configuration Descriptor (for enabling notifications) */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --- Event IDs (Notification Source byte 0) ---

    const val EVENT_ID_NOTIFICATION_ADDED: Byte = 0
    const val EVENT_ID_NOTIFICATION_MODIFIED: Byte = 1
    const val EVENT_ID_NOTIFICATION_REMOVED: Byte = 2

    // --- Event Flags (Notification Source byte 1, bitmask) ---

    const val EVENT_FLAG_SILENT: Byte = (1 shl 0).toByte()
    const val EVENT_FLAG_IMPORTANT: Byte = (1 shl 1).toByte()
    const val EVENT_FLAG_PRE_EXISTING: Byte = (1 shl 2).toByte()
    const val EVENT_FLAG_POSITIVE_ACTION: Byte = (1 shl 3).toByte()
    const val EVENT_FLAG_NEGATIVE_ACTION: Byte = (1 shl 4).toByte()

    // --- Category IDs (Notification Source byte 2) ---

    const val CATEGORY_ID_OTHER: Byte = 0
    const val CATEGORY_ID_INCOMING_CALL: Byte = 1
    const val CATEGORY_ID_MISSED_CALL: Byte = 2
    const val CATEGORY_ID_VOICEMAIL: Byte = 3
    const val CATEGORY_ID_SOCIAL: Byte = 4
    const val CATEGORY_ID_SCHEDULE: Byte = 5
    const val CATEGORY_ID_EMAIL: Byte = 6
    const val CATEGORY_ID_NEWS: Byte = 7
    const val CATEGORY_ID_HEALTH_AND_FITNESS: Byte = 8
    const val CATEGORY_ID_BUSINESS_AND_FINANCE: Byte = 9
    const val CATEGORY_ID_LOCATION: Byte = 10
    const val CATEGORY_ID_ENTERTAINMENT: Byte = 11

    // --- Command IDs (Control Point byte 0) ---

    const val COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES: Byte = 0
    const val COMMAND_ID_GET_APP_ATTRIBUTES: Byte = 1
    const val COMMAND_ID_PERFORM_NOTIFICATION_ACTION: Byte = 2

    // --- Notification Attribute IDs (for GetNotificationAttributes command) ---

    const val NOTIFICATION_ATTR_APP_IDENTIFIER: Byte = 0
    const val NOTIFICATION_ATTR_TITLE: Byte = 1
    const val NOTIFICATION_ATTR_SUBTITLE: Byte = 2
    const val NOTIFICATION_ATTR_MESSAGE: Byte = 3
    const val NOTIFICATION_ATTR_MESSAGE_SIZE: Byte = 4
    const val NOTIFICATION_ATTR_DATE: Byte = 5
    const val NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL: Byte = 6
    const val NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL: Byte = 7

    // --- App Attribute IDs (for GetAppAttributes command) ---

    const val APP_ATTR_DISPLAY_NAME: Byte = 0

    // --- Action IDs (for PerformNotificationAction command) ---

    const val ACTION_ID_POSITIVE: Byte = 0
    const val ACTION_ID_NEGATIVE: Byte = 1

    // --- Max attribute lengths we request ---

    /** Max title length to request */
    const val MAX_TITLE_LENGTH: Int = 128

    /** Max subtitle length to request */
    const val MAX_SUBTITLE_LENGTH: Int = 64

    /** Max message length to request */
    const val MAX_MESSAGE_LENGTH: Int = 512

    // --- BLE Parameters ---

    /** Desired MTU for BLE connection */
    const val DESIRED_MTU: Int = 256

    /** Notification Source event size (always 8 bytes) */
    const val NOTIFICATION_SOURCE_EVENT_SIZE: Int = 8
}
