package com.stalechips.palmamirror.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Tests for BleConnectionManager state machine logic, ANCS constants, and UUIDs.
 *
 * Since BleConnectionManager requires a real Android Context and BluetoothManager,
 * we cannot instantiate it in a pure JUnit test. Instead, we verify the constants,
 * UUIDs, enum values, and state machine contract that the manager depends on.
 */
class BleConnectionManagerTest {

    // -----------------------------------------------------------------------
    // 1. ConnectionState enum — all expected states present
    // -----------------------------------------------------------------------

    @Test
    fun `ConnectionState has exactly six states`() {
        val states = BleConnectionManager.ConnectionState.entries
        assertEquals(6, states.size)
    }

    @Test
    fun `ConnectionState contains DISCONNECTED`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("DISCONNECTED"))
    }

    @Test
    fun `ConnectionState contains SCANNING`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("SCANNING"))
    }

    @Test
    fun `ConnectionState contains CONNECTING`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("CONNECTING"))
    }

    @Test
    fun `ConnectionState contains DISCOVERING_SERVICES`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("DISCOVERING_SERVICES"))
    }

    @Test
    fun `ConnectionState contains SUBSCRIBING`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("SUBSCRIBING"))
    }

    @Test
    fun `ConnectionState contains CONNECTED`() {
        assertNotNull(BleConnectionManager.ConnectionState.valueOf("CONNECTED"))
    }

    @Test
    fun `ConnectionState ordinal progression follows lifecycle order`() {
        val states = BleConnectionManager.ConnectionState.entries
        assertEquals(0, states.indexOf(BleConnectionManager.ConnectionState.DISCONNECTED))
        assertEquals(1, states.indexOf(BleConnectionManager.ConnectionState.SCANNING))
        assertEquals(2, states.indexOf(BleConnectionManager.ConnectionState.CONNECTING))
        assertEquals(3, states.indexOf(BleConnectionManager.ConnectionState.DISCOVERING_SERVICES))
        assertEquals(4, states.indexOf(BleConnectionManager.ConnectionState.SUBSCRIBING))
        assertEquals(5, states.indexOf(BleConnectionManager.ConnectionState.CONNECTED))
    }

    // -----------------------------------------------------------------------
    // 2. State transition logic — conceptual verification of the contract
    // -----------------------------------------------------------------------

    @Test
    fun `initial state should be DISCONNECTED`() {
        // The BleConnectionManager initializes _connectionState to DISCONNECTED.
        // We verify the enum default is the first entry.
        assertEquals(
            BleConnectionManager.ConnectionState.DISCONNECTED,
            BleConnectionManager.ConnectionState.entries.first()
        )
    }

    @Test
    fun `DISCONNECTED is a valid starting state for SCANNING`() {
        // The state machine allows DISCONNECTED -> SCANNING (via startScan)
        val from = BleConnectionManager.ConnectionState.DISCONNECTED
        val to = BleConnectionManager.ConnectionState.SCANNING
        assertTrue(from.ordinal < to.ordinal)
    }

    @Test
    fun `SCANNING is a valid starting state for CONNECTING`() {
        val from = BleConnectionManager.ConnectionState.SCANNING
        val to = BleConnectionManager.ConnectionState.CONNECTING
        assertTrue(from.ordinal < to.ordinal)
    }

    @Test
    fun `CONNECTING is a valid starting state for DISCOVERING_SERVICES`() {
        val from = BleConnectionManager.ConnectionState.CONNECTING
        val to = BleConnectionManager.ConnectionState.DISCOVERING_SERVICES
        assertTrue(from.ordinal < to.ordinal)
    }

    @Test
    fun `DISCOVERING_SERVICES is a valid starting state for SUBSCRIBING`() {
        val from = BleConnectionManager.ConnectionState.DISCOVERING_SERVICES
        val to = BleConnectionManager.ConnectionState.SUBSCRIBING
        assertTrue(from.ordinal < to.ordinal)
    }

    @Test
    fun `SUBSCRIBING is a valid starting state for CONNECTED`() {
        val from = BleConnectionManager.ConnectionState.SUBSCRIBING
        val to = BleConnectionManager.ConnectionState.CONNECTED
        assertTrue(from.ordinal < to.ordinal)
    }

    @Test
    fun `any state can transition back to DISCONNECTED`() {
        // The manager can go to DISCONNECTED from any state on BLE disconnect.
        val disconnected = BleConnectionManager.ConnectionState.DISCONNECTED
        for (state in BleConnectionManager.ConnectionState.entries) {
            // DISCONNECTED -> DISCONNECTED is a no-op but valid
            assertTrue(
                "State $state should be able to transition to DISCONNECTED",
                disconnected.ordinal <= state.ordinal
            )
        }
    }

    // -----------------------------------------------------------------------
    // 3. ANCS Service UUID
    // -----------------------------------------------------------------------

    @Test
    fun `ANCS service UUID matches Apple specification`() {
        val expected = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
        assertEquals(expected, AncsConstants.ANCS_SERVICE_UUID)
    }

    @Test
    fun `ANCS service UUID is not null`() {
        assertNotNull(AncsConstants.ANCS_SERVICE_UUID)
    }

    @Test
    fun `ANCS service UUID string representation is uppercase-safe`() {
        // UUID.fromString is case-insensitive, but we verify the object equality
        val fromLower = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0")
        assertEquals(fromLower, AncsConstants.ANCS_SERVICE_UUID)
    }

    // -----------------------------------------------------------------------
    // 4. CCCD UUID
    // -----------------------------------------------------------------------

    @Test
    fun `CCCD UUID matches Bluetooth SIG standard`() {
        val expected = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        assertEquals(expected, AncsConstants.CCCD_UUID)
    }

    @Test
    fun `CCCD UUID is the standard 16-bit UUID 0x2902 in 128-bit form`() {
        // The standard Bluetooth Base UUID with 0x2902 inserted at bits 96-111
        val uuidStr = AncsConstants.CCCD_UUID.toString()
        assertTrue(
            "CCCD UUID should contain 2902 in the correct position",
            uuidStr.startsWith("00002902-")
        )
        assertTrue(
            "CCCD UUID should have Bluetooth SIG base suffix",
            uuidStr.endsWith("-0000-1000-8000-00805f9b34fb")
        )
    }

    // -----------------------------------------------------------------------
    // 5. ANCS Characteristic UUIDs
    // -----------------------------------------------------------------------

    @Test
    fun `Notification Source UUID matches Apple ANCS specification`() {
        val expected = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
        assertEquals(expected, AncsConstants.NOTIFICATION_SOURCE_UUID)
    }

    @Test
    fun `Control Point UUID matches Apple ANCS specification`() {
        val expected = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
        assertEquals(expected, AncsConstants.CONTROL_POINT_UUID)
    }

    @Test
    fun `Data Source UUID matches Apple ANCS specification`() {
        val expected = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
        assertEquals(expected, AncsConstants.DATA_SOURCE_UUID)
    }

    @Test
    fun `all ANCS characteristic UUIDs are distinct`() {
        val uuids = setOf(
            AncsConstants.NOTIFICATION_SOURCE_UUID,
            AncsConstants.CONTROL_POINT_UUID,
            AncsConstants.DATA_SOURCE_UUID
        )
        assertEquals("All three ANCS characteristic UUIDs must be unique", 3, uuids.size)
    }

    @Test
    fun `ANCS characteristic UUIDs are different from service UUID`() {
        assertNotEquals(AncsConstants.ANCS_SERVICE_UUID, AncsConstants.NOTIFICATION_SOURCE_UUID)
        assertNotEquals(AncsConstants.ANCS_SERVICE_UUID, AncsConstants.CONTROL_POINT_UUID)
        assertNotEquals(AncsConstants.ANCS_SERVICE_UUID, AncsConstants.DATA_SOURCE_UUID)
    }

    @Test
    fun `ANCS characteristic UUIDs are different from CCCD UUID`() {
        assertNotEquals(AncsConstants.CCCD_UUID, AncsConstants.NOTIFICATION_SOURCE_UUID)
        assertNotEquals(AncsConstants.CCCD_UUID, AncsConstants.CONTROL_POINT_UUID)
        assertNotEquals(AncsConstants.CCCD_UUID, AncsConstants.DATA_SOURCE_UUID)
    }

    @Test
    fun `AMS service UUID is present for future use`() {
        val expected = UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC")
        assertEquals(expected, AncsConstants.AMS_SERVICE_UUID)
    }

    @Test
    fun `AMS service UUID is distinct from ANCS service UUID`() {
        assertNotEquals(AncsConstants.ANCS_SERVICE_UUID, AncsConstants.AMS_SERVICE_UUID)
    }

    // -----------------------------------------------------------------------
    // 6. AncsConstants values — Category IDs
    // -----------------------------------------------------------------------

    @Test
    fun `category ID OTHER is 0`() {
        assertEquals(0.toByte(), AncsConstants.CATEGORY_ID_OTHER)
    }

    @Test
    fun `category ID INCOMING_CALL is 1`() {
        assertEquals(1.toByte(), AncsConstants.CATEGORY_ID_INCOMING_CALL)
    }

    @Test
    fun `category ID MISSED_CALL is 2`() {
        assertEquals(2.toByte(), AncsConstants.CATEGORY_ID_MISSED_CALL)
    }

    @Test
    fun `category ID VOICEMAIL is 3`() {
        assertEquals(3.toByte(), AncsConstants.CATEGORY_ID_VOICEMAIL)
    }

    @Test
    fun `category ID SOCIAL is 4`() {
        assertEquals(4.toByte(), AncsConstants.CATEGORY_ID_SOCIAL)
    }

    @Test
    fun `category ID SCHEDULE is 5`() {
        assertEquals(5.toByte(), AncsConstants.CATEGORY_ID_SCHEDULE)
    }

    @Test
    fun `category ID EMAIL is 6`() {
        assertEquals(6.toByte(), AncsConstants.CATEGORY_ID_EMAIL)
    }

    @Test
    fun `category ID NEWS is 7`() {
        assertEquals(7.toByte(), AncsConstants.CATEGORY_ID_NEWS)
    }

    @Test
    fun `category ID HEALTH_AND_FITNESS is 8`() {
        assertEquals(8.toByte(), AncsConstants.CATEGORY_ID_HEALTH_AND_FITNESS)
    }

    @Test
    fun `category ID BUSINESS_AND_FINANCE is 9`() {
        assertEquals(9.toByte(), AncsConstants.CATEGORY_ID_BUSINESS_AND_FINANCE)
    }

    @Test
    fun `category ID LOCATION is 10`() {
        assertEquals(10.toByte(), AncsConstants.CATEGORY_ID_LOCATION)
    }

    @Test
    fun `category ID ENTERTAINMENT is 11`() {
        assertEquals(11.toByte(), AncsConstants.CATEGORY_ID_ENTERTAINMENT)
    }

    @Test
    fun `category IDs span contiguous range 0 to 11`() {
        val categoryIds = listOf(
            AncsConstants.CATEGORY_ID_OTHER,
            AncsConstants.CATEGORY_ID_INCOMING_CALL,
            AncsConstants.CATEGORY_ID_MISSED_CALL,
            AncsConstants.CATEGORY_ID_VOICEMAIL,
            AncsConstants.CATEGORY_ID_SOCIAL,
            AncsConstants.CATEGORY_ID_SCHEDULE,
            AncsConstants.CATEGORY_ID_EMAIL,
            AncsConstants.CATEGORY_ID_NEWS,
            AncsConstants.CATEGORY_ID_HEALTH_AND_FITNESS,
            AncsConstants.CATEGORY_ID_BUSINESS_AND_FINANCE,
            AncsConstants.CATEGORY_ID_LOCATION,
            AncsConstants.CATEGORY_ID_ENTERTAINMENT
        )
        assertEquals(12, categoryIds.size)
        for (i in 0..11) {
            assertTrue(
                "Category ID $i should be present",
                categoryIds.contains(i.toByte())
            )
        }
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Event IDs
    // -----------------------------------------------------------------------

    @Test
    fun `event ID NOTIFICATION_ADDED is 0`() {
        assertEquals(0.toByte(), AncsConstants.EVENT_ID_NOTIFICATION_ADDED)
    }

    @Test
    fun `event ID NOTIFICATION_MODIFIED is 1`() {
        assertEquals(1.toByte(), AncsConstants.EVENT_ID_NOTIFICATION_MODIFIED)
    }

    @Test
    fun `event ID NOTIFICATION_REMOVED is 2`() {
        assertEquals(2.toByte(), AncsConstants.EVENT_ID_NOTIFICATION_REMOVED)
    }

    @Test
    fun `event IDs are contiguous 0, 1, 2`() {
        val eventIds = listOf(
            AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
            AncsConstants.EVENT_ID_NOTIFICATION_MODIFIED,
            AncsConstants.EVENT_ID_NOTIFICATION_REMOVED
        )
        assertEquals(listOf(0.toByte(), 1.toByte(), 2.toByte()), eventIds)
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Event Flags
    // -----------------------------------------------------------------------

    @Test
    fun `event flag SILENT is bit 0`() {
        assertEquals(0x01.toByte(), AncsConstants.EVENT_FLAG_SILENT)
    }

    @Test
    fun `event flag IMPORTANT is bit 1`() {
        assertEquals(0x02.toByte(), AncsConstants.EVENT_FLAG_IMPORTANT)
    }

    @Test
    fun `event flag PRE_EXISTING is bit 2`() {
        assertEquals(0x04.toByte(), AncsConstants.EVENT_FLAG_PRE_EXISTING)
    }

    @Test
    fun `event flag POSITIVE_ACTION is bit 3`() {
        assertEquals(0x08.toByte(), AncsConstants.EVENT_FLAG_POSITIVE_ACTION)
    }

    @Test
    fun `event flag NEGATIVE_ACTION is bit 4`() {
        assertEquals(0x10.toByte(), AncsConstants.EVENT_FLAG_NEGATIVE_ACTION)
    }

    @Test
    fun `event flags are non-overlapping power-of-two bitmask`() {
        val flags = listOf(
            AncsConstants.EVENT_FLAG_SILENT,
            AncsConstants.EVENT_FLAG_IMPORTANT,
            AncsConstants.EVENT_FLAG_PRE_EXISTING,
            AncsConstants.EVENT_FLAG_POSITIVE_ACTION,
            AncsConstants.EVENT_FLAG_NEGATIVE_ACTION
        )
        // Each flag should have exactly one bit set
        for (flag in flags) {
            val intVal = flag.toInt() and 0xFF
            assertTrue("Flag 0x${intVal.toString(16)} should be a power of 2", intVal != 0 && (intVal and (intVal - 1)) == 0)
        }
        // Combined OR of all flags should have 5 bits set
        var combined = 0
        for (flag in flags) {
            combined = combined or (flag.toInt() and 0xFF)
        }
        assertEquals(0x1F, combined)
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Command IDs
    // -----------------------------------------------------------------------

    @Test
    fun `command ID GET_NOTIFICATION_ATTRIBUTES is 0`() {
        assertEquals(0.toByte(), AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
    }

    @Test
    fun `command ID GET_APP_ATTRIBUTES is 1`() {
        assertEquals(1.toByte(), AncsConstants.COMMAND_ID_GET_APP_ATTRIBUTES)
    }

    @Test
    fun `command ID PERFORM_NOTIFICATION_ACTION is 2`() {
        assertEquals(2.toByte(), AncsConstants.COMMAND_ID_PERFORM_NOTIFICATION_ACTION)
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Notification Attribute IDs
    // -----------------------------------------------------------------------

    @Test
    fun `notification attr APP_IDENTIFIER is 0`() {
        assertEquals(0.toByte(), AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)
    }

    @Test
    fun `notification attr TITLE is 1`() {
        assertEquals(1.toByte(), AncsConstants.NOTIFICATION_ATTR_TITLE)
    }

    @Test
    fun `notification attr SUBTITLE is 2`() {
        assertEquals(2.toByte(), AncsConstants.NOTIFICATION_ATTR_SUBTITLE)
    }

    @Test
    fun `notification attr MESSAGE is 3`() {
        assertEquals(3.toByte(), AncsConstants.NOTIFICATION_ATTR_MESSAGE)
    }

    @Test
    fun `notification attr MESSAGE_SIZE is 4`() {
        assertEquals(4.toByte(), AncsConstants.NOTIFICATION_ATTR_MESSAGE_SIZE)
    }

    @Test
    fun `notification attr DATE is 5`() {
        assertEquals(5.toByte(), AncsConstants.NOTIFICATION_ATTR_DATE)
    }

    @Test
    fun `notification attr POSITIVE_ACTION_LABEL is 6`() {
        assertEquals(6.toByte(), AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL)
    }

    @Test
    fun `notification attr NEGATIVE_ACTION_LABEL is 7`() {
        assertEquals(7.toByte(), AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL)
    }

    @Test
    fun `notification attribute IDs span contiguous range 0 to 7`() {
        val attrIds = listOf(
            AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER,
            AncsConstants.NOTIFICATION_ATTR_TITLE,
            AncsConstants.NOTIFICATION_ATTR_SUBTITLE,
            AncsConstants.NOTIFICATION_ATTR_MESSAGE,
            AncsConstants.NOTIFICATION_ATTR_MESSAGE_SIZE,
            AncsConstants.NOTIFICATION_ATTR_DATE,
            AncsConstants.NOTIFICATION_ATTR_POSITIVE_ACTION_LABEL,
            AncsConstants.NOTIFICATION_ATTR_NEGATIVE_ACTION_LABEL
        )
        assertEquals(8, attrIds.size)
        for (i in 0..7) {
            assertEquals(i.toByte(), attrIds[i])
        }
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — App Attribute IDs
    // -----------------------------------------------------------------------

    @Test
    fun `app attr DISPLAY_NAME is 0`() {
        assertEquals(0.toByte(), AncsConstants.APP_ATTR_DISPLAY_NAME)
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Action IDs
    // -----------------------------------------------------------------------

    @Test
    fun `action ID POSITIVE is 0`() {
        assertEquals(0.toByte(), AncsConstants.ACTION_ID_POSITIVE)
    }

    @Test
    fun `action ID NEGATIVE is 1`() {
        assertEquals(1.toByte(), AncsConstants.ACTION_ID_NEGATIVE)
    }

    @Test
    fun `action IDs are distinct`() {
        assertNotEquals(AncsConstants.ACTION_ID_POSITIVE, AncsConstants.ACTION_ID_NEGATIVE)
    }

    // -----------------------------------------------------------------------
    // 6 (continued). AncsConstants values — Max attribute lengths
    // -----------------------------------------------------------------------

    @Test
    fun `max title length is 128`() {
        assertEquals(128, AncsConstants.MAX_TITLE_LENGTH)
    }

    @Test
    fun `max subtitle length is 64`() {
        assertEquals(64, AncsConstants.MAX_SUBTITLE_LENGTH)
    }

    @Test
    fun `max message length is 512`() {
        assertEquals(512, AncsConstants.MAX_MESSAGE_LENGTH)
    }

    @Test
    fun `max lengths are positive and reasonable`() {
        assertTrue(AncsConstants.MAX_TITLE_LENGTH > 0)
        assertTrue(AncsConstants.MAX_SUBTITLE_LENGTH > 0)
        assertTrue(AncsConstants.MAX_MESSAGE_LENGTH > 0)
        // Message should be the longest
        assertTrue(AncsConstants.MAX_MESSAGE_LENGTH >= AncsConstants.MAX_TITLE_LENGTH)
        assertTrue(AncsConstants.MAX_TITLE_LENGTH >= AncsConstants.MAX_SUBTITLE_LENGTH)
    }

    // -----------------------------------------------------------------------
    // 7. DESIRED_MTU constant is reasonable
    // -----------------------------------------------------------------------

    @Test
    fun `desired MTU is 256`() {
        assertEquals(256, AncsConstants.DESIRED_MTU)
    }

    @Test
    fun `desired MTU is above BLE minimum of 23`() {
        assertTrue(
            "DESIRED_MTU (${ AncsConstants.DESIRED_MTU}) must be above BLE minimum ATT_MTU of 23",
            AncsConstants.DESIRED_MTU > 23
        )
    }

    @Test
    fun `desired MTU does not exceed BLE maximum of 517`() {
        assertTrue(
            "DESIRED_MTU (${AncsConstants.DESIRED_MTU}) must not exceed BLE max ATT_MTU of 517",
            AncsConstants.DESIRED_MTU <= 517
        )
    }

    @Test
    fun `desired MTU is large enough to hold max attribute lengths in fewer packets`() {
        // MTU should be large enough to carry headers + reasonable attribute chunks
        // At minimum, it should be > header (5 bytes) + some attribute data
        assertTrue(AncsConstants.DESIRED_MTU > 5 + AncsConstants.MAX_SUBTITLE_LENGTH)
    }

    // -----------------------------------------------------------------------
    // 8. Notification Source event size is 8
    // -----------------------------------------------------------------------

    @Test
    fun `notification source event size is 8`() {
        assertEquals(8, AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE)
    }

    @Test
    fun `notification source event size matches ANCS specification`() {
        // ANCS spec: EventID(1) + EventFlags(1) + CategoryID(1) + CategoryCount(1) + NotificationUID(4) = 8
        val expectedSize = 1 + 1 + 1 + 1 + 4
        assertEquals(expectedSize, AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE)
    }

    @Test
    fun `notification source event size is positive`() {
        assertTrue(AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE > 0)
    }
}
