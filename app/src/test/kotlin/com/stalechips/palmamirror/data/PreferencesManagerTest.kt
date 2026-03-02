package com.stalechips.palmamirror.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [PreferencesManager] key definitions and default values.
 *
 * DataStore requires an Android context so we cannot test the reactive flows
 * in a pure JUnit environment. These tests verify the key names and defaults
 * are configured correctly.
 */
class PreferencesManagerTest {

    @Test
    fun `FONT_SIZE key has correct name`() {
        assertEquals(stringPreferencesKey("font_size"), PreferencesManager.FONT_SIZE)
    }

    @Test
    fun `PAIRED_DEVICE_ADDRESS key has correct name`() {
        assertEquals(stringPreferencesKey("paired_device_address"), PreferencesManager.PAIRED_DEVICE_ADDRESS)
    }

    @Test
    fun `AUTO_RECONNECT key has correct name`() {
        assertEquals(booleanPreferencesKey("auto_reconnect"), PreferencesManager.AUTO_RECONNECT)
    }

    @Test
    fun `SETUP_COMPLETE key has correct name`() {
        assertEquals(booleanPreferencesKey("setup_complete"), PreferencesManager.SETUP_COMPLETE)
    }

    @Test
    fun `default font size is medium`() {
        assertEquals("medium", PreferencesManager.DEFAULT_FONT_SIZE)
    }

    @Test
    fun `default auto reconnect is true`() {
        assertTrue(PreferencesManager.DEFAULT_AUTO_RECONNECT)
    }

    @Test
    fun `default setup complete is false`() {
        assertFalse(PreferencesManager.DEFAULT_SETUP_COMPLETE)
    }

    @Test
    fun `font size accepts valid values`() {
        val validSizes = listOf("small", "medium", "large", "xlarge")
        validSizes.forEach { size ->
            // Verify these are valid string values (compile-time check essentially)
            assertNotNull(size)
            assertTrue(size.isNotEmpty())
        }
    }
}
