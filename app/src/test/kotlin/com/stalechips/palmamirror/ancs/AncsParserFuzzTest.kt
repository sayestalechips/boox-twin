package com.stalechips.palmamirror.ancs

import com.stalechips.palmamirror.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/**
 * Fuzz tests for AncsEventParser and AncsAttributeParser.
 *
 * Uses a fixed seed for reproducibility. These tests ensure that arbitrary
 * (potentially malformed) input never causes an unhandled exception.
 */
class AncsParserFuzzTest {

    private lateinit var eventParser: AncsEventParser
    private lateinit var attributeParser: AncsAttributeParser

    companion object {
        /** Fixed seed for reproducible randomness */
        private const val SEED = 0xDEADBEEFL

        /** Number of random iterations for fuzz tests */
        private const val FUZZ_ITERATIONS = 1000

        /** Max random length for event parser fuzz */
        private const val MAX_EVENT_FUZZ_LENGTH = 100

        /** Max random length for attribute parser fuzz */
        private const val MAX_ATTR_FUZZ_LENGTH = 200
    }

    @Before
    fun setUp() {
        eventParser = AncsEventParser()
        attributeParser = AncsAttributeParser()
    }

    // -----------------------------------------------------------------------
    // 1. Fuzz AncsEventParser.parse() with 1000 random byte arrays (0-100 bytes)
    // -----------------------------------------------------------------------

    @Test
    fun `AncsEventParser parse never crashes on random input`() {
        val rng = Random(SEED)

        for (i in 0 until FUZZ_ITERATIONS) {
            val length = rng.nextInt(MAX_EVENT_FUZZ_LENGTH + 1) // 0 to 100 inclusive
            val data = ByteArray(length)
            rng.nextBytes(data)

            try {
                val result = eventParser.parse(data)
                // Result can be null or non-null — both are acceptable.
                // If data < 8 bytes, expect null. If >= 8, expect non-null.
                if (length < AncsConstants.NOTIFICATION_SOURCE_EVENT_SIZE) {
                    assertNull(
                        "Iteration $i: data of length $length should return null",
                        result
                    )
                }
                // Non-null is fine for >= 8 bytes — parser should handle any byte values
            } catch (e: Exception) {
                fail("Iteration $i: AncsEventParser.parse() threw ${e.javaClass.simpleName} " +
                        "on input of length $length: ${e.message}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // 2. Fuzz AncsAttributeParser.feedData() with 1000 random byte arrays (0-200 bytes)
    // -----------------------------------------------------------------------

    @Test
    fun `AncsAttributeParser feedData never crashes on random input`() {
        val rng = Random(SEED)

        for (i in 0 until FUZZ_ITERATIONS) {
            // Reset parser state between iterations to avoid cross-contamination
            attributeParser.reset()

            val length = rng.nextInt(MAX_ATTR_FUZZ_LENGTH + 1) // 0 to 200 inclusive
            val data = ByteArray(length)
            rng.nextBytes(data)

            try {
                val result = attributeParser.feedData(data)
                // Result can be null or non-null — both are acceptable.
                // We only care that it does not throw.
            } catch (e: Exception) {
                fail("Iteration $i: AncsAttributeParser.feedData() threw ${e.javaClass.simpleName} " +
                        "on input of length $length: ${e.message}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // 3. Test with special byte patterns — all zeros, all 0xFF, alternating
    // -----------------------------------------------------------------------

    @Test
    fun `AncsEventParser handles all-zero input without crashing`() {
        for (length in 0..20) {
            val data = ByteArray(length) // all zeros by default
            try {
                eventParser.parse(data)
            } catch (e: Exception) {
                fail("AncsEventParser.parse() threw on all-zero input of length $length: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsEventParser handles all-0xFF input without crashing`() {
        for (length in 0..20) {
            val data = ByteArray(length) { 0xFF.toByte() }
            try {
                eventParser.parse(data)
            } catch (e: Exception) {
                fail("AncsEventParser.parse() threw on all-0xFF input of length $length: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsEventParser handles alternating 0xAA 0x55 pattern without crashing`() {
        for (length in 0..20) {
            val data = ByteArray(length) { i -> if (i % 2 == 0) 0xAA.toByte() else 0x55.toByte() }
            try {
                eventParser.parse(data)
            } catch (e: Exception) {
                fail("AncsEventParser.parse() threw on alternating pattern of length $length: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsAttributeParser handles all-zero input without crashing`() {
        for (length in 0..20) {
            attributeParser.reset()
            val data = ByteArray(length) // all zeros
            try {
                attributeParser.feedData(data)
            } catch (e: Exception) {
                fail("AncsAttributeParser.feedData() threw on all-zero input of length $length: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsAttributeParser handles all-0xFF input without crashing`() {
        for (length in 0..20) {
            attributeParser.reset()
            val data = ByteArray(length) { 0xFF.toByte() }
            try {
                attributeParser.feedData(data)
            } catch (e: Exception) {
                fail("AncsAttributeParser.feedData() threw on all-0xFF input of length $length: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsAttributeParser handles alternating 0xAA 0x55 pattern without crashing`() {
        for (length in 0..20) {
            attributeParser.reset()
            val data = ByteArray(length) { i -> if (i % 2 == 0) 0xAA.toByte() else 0x55.toByte() }
            try {
                attributeParser.feedData(data)
            } catch (e: Exception) {
                fail("AncsAttributeParser.feedData() threw on alternating pattern of length $length: ${e.message}")
            }
        }
    }

    // -----------------------------------------------------------------------
    // 4. Test AncsEventParser with exactly 8 random bytes — should always return non-null
    // -----------------------------------------------------------------------

    @Test
    fun `AncsEventParser always returns non-null for exactly 8 random bytes`() {
        val rng = Random(SEED)

        for (i in 0 until FUZZ_ITERATIONS) {
            val data = ByteArray(8)
            rng.nextBytes(data)

            try {
                val result = eventParser.parse(data)
                assertNotNull(
                    "Iteration $i: 8-byte input should always produce a non-null result. " +
                            "Data: ${data.joinToString(",") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }}",
                    result
                )
            } catch (e: Exception) {
                fail("Iteration $i: AncsEventParser.parse() threw ${e.javaClass.simpleName} " +
                        "on 8-byte input: ${e.message}")
            }
        }
    }

    @Test
    fun `AncsEventParser non-null result has valid fields for 8 random bytes`() {
        val rng = Random(SEED + 1) // different seed for variety

        for (i in 0 until FUZZ_ITERATIONS) {
            val data = ByteArray(8)
            rng.nextBytes(data)

            val result = eventParser.parse(data)
            assertNotNull("Unexpected null for 8-byte input at iteration $i", result)
            result!!

            // eventId should always be one of the enum values (fromByte defaults to ADDED)
            assertTrue(
                "eventId should be a valid enum value",
                AncsNotification.EventId.values().contains(result.eventId)
            )

            // category should always be a valid enum value (fromId defaults to OTHER)
            assertTrue(
                "category should be a valid enum value",
                AncsCategory.values().contains(result.category)
            )

            // eventFlags and categoryCount are just ints — no constraints
            assertTrue("eventFlags should be non-negative (unsigned byte)", result.eventFlags in 0..255)
            assertTrue("categoryCount should be non-negative (unsigned byte)", result.categoryCount in 0..255)
        }
    }

    // -----------------------------------------------------------------------
    // 5. Test AncsAttributeParser with command ID 0 but truncated content
    // -----------------------------------------------------------------------

    @Test
    fun `AncsAttributeParser handles command ID 0 with only 1 byte`() {
        attributeParser.reset()
        val data = byteArrayOf(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        try {
            val result = attributeParser.feedData(data)
            // Should return null — partial header buffered for reassembly
            assertNull("Single command byte should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on 1-byte command-0 input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with 2 bytes`() {
        attributeParser.reset()
        val data = byteArrayOf(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES, 0x01)
        try {
            val result = attributeParser.feedData(data)
            assertNull("Two bytes should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on 2-byte command-0 input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with 3 bytes`() {
        attributeParser.reset()
        val data = byteArrayOf(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES, 0x01, 0x02)
        try {
            val result = attributeParser.feedData(data)
            assertNull("Three bytes should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on 3-byte command-0 input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with 4 bytes (partial UID)`() {
        attributeParser.reset()
        val data = byteArrayOf(
            AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES,
            0x01, 0x02, 0x03
        )
        try {
            val result = attributeParser.feedData(data)
            assertNull("Four bytes (partial UID) should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on 4-byte command-0 input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with exactly 5 bytes (header only, no attributes)`() {
        attributeParser.reset()
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(42)

        try {
            val result = attributeParser.feedData(buffer.array())
            // 5 bytes = command + UID, no attribute data.
            // Parser should succeed with an empty attribute map.
            assertNotNull("Header-only packet should parse with empty attributes", result)
            assertEquals(42, result!!.notificationUID)
            assertTrue("Attributes should be empty", result.attributes.isEmpty())
        } catch (e: Exception) {
            fail("feedData threw on 5-byte header-only input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with header plus truncated attribute ID`() {
        attributeParser.reset()
        // 5 bytes header + 1 byte attribute ID with no length/data after it
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(77)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE) // attribute ID, but no length follows

        try {
            val result = attributeParser.feedData(buffer.array())
            // Parser should treat this as incomplete and return null (waiting for more data)
            assertNull("Truncated attribute should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on truncated attribute input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with header plus attribute ID and partial length`() {
        attributeParser.reset()
        // 5 bytes header + 1 byte attr ID + 1 byte partial length (need 2 for uint16)
        val buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(88)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_MESSAGE) // attr ID
        buffer.put(0x0A) // only 1 byte of the 2-byte length

        try {
            val result = attributeParser.feedData(buffer.array())
            assertNull("Partial length field should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on partial-length input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with header plus attribute with length but no data`() {
        attributeParser.reset()
        // 5 bytes header + 1 byte attr ID + 2 byte length (10) but no data follows
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(99)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_TITLE)
        buffer.putShort(10) // claims 10 bytes of data follow, but none do

        try {
            val result = attributeParser.feedData(buffer.array())
            assertNull("Missing attribute data should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on missing-data input: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles command ID 0 with truncated AppIdentifier`() {
        attributeParser.reset()
        // AppIdentifier uses standard tuple format: attr ID + uint16 length + data.
        // Send header + attr ID 0 + length (4) but only 2 bytes of data.
        val buffer = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        buffer.putInt(55)
        buffer.put(AncsConstants.NOTIFICATION_ATTR_APP_IDENTIFIER)
        buffer.putShort(4) // claims 4 bytes of data
        buffer.put('c'.code.toByte())
        buffer.put('o'.code.toByte())
        // Only 2 of 4 promised bytes — incomplete

        try {
            val result = attributeParser.feedData(buffer.array())
            // Parser should return null — data is incomplete (IncompleteParsing).
            // The key assertion is that it does not crash.
            assertNull("Truncated attribute data should not produce a result", result)
        } catch (e: Exception) {
            fail("feedData threw on truncated AppIdentifier: ${e.message}")
        }
    }

    @Test
    fun `AncsAttributeParser handles multiple truncated packets in reassembly`() {
        attributeParser.reset()

        // First packet: just the command byte
        val packet1 = byteArrayOf(AncsConstants.COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES)
        try {
            assertNull(attributeParser.feedData(packet1))
        } catch (e: Exception) {
            fail("feedData threw on first truncated packet: ${e.message}")
        }

        // Second packet: partial UID (2 more bytes, still need 2 more for complete UID)
        val packet2 = byteArrayOf(0x01, 0x00)
        try {
            assertNull(attributeParser.feedData(packet2))
        } catch (e: Exception) {
            fail("feedData threw on second truncated packet: ${e.message}")
        }

        // Third packet: remaining UID bytes to complete header but no attributes
        val packet3 = byteArrayOf(0x00, 0x00)
        try {
            val result = attributeParser.feedData(packet3)
            // Now we have 5 bytes total: cmd(1) + UID(4) = valid header with empty attributes
            if (result != null) {
                assertEquals(1, result.notificationUID) // UID = 0x00000001 LE
                assertTrue(result.attributes.isEmpty())
            }
        } catch (e: Exception) {
            fail("feedData threw on third packet completing header: ${e.message}")
        }
    }
}
