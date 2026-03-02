package com.stalechips.palmamirror.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore instance scoped to the application context. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "palmamirror_prefs")

/**
 * Manages user preferences via Jetpack DataStore.
 * Provides reactive Flow getters and suspend setters for each preference.
 */
class PreferencesManager(private val context: Context) {

    companion object Keys {
        val FONT_SIZE = stringPreferencesKey("font_size")
        val PAIRED_DEVICE_ADDRESS = stringPreferencesKey("paired_device_address")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")

        const val DEFAULT_FONT_SIZE = "medium"
        const val DEFAULT_AUTO_RECONNECT = true
        const val DEFAULT_SETUP_COMPLETE = false
    }

    // --- Font Size ---

    val fontSize: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: DEFAULT_FONT_SIZE
    }

    suspend fun setFontSize(size: String) {
        context.dataStore.edit { prefs ->
            prefs[FONT_SIZE] = size
        }
    }

    // --- Paired Device Address ---

    val pairedDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PAIRED_DEVICE_ADDRESS]
    }

    suspend fun setPairedDeviceAddress(address: String?) {
        context.dataStore.edit { prefs ->
            if (address != null) {
                prefs[PAIRED_DEVICE_ADDRESS] = address
            } else {
                prefs.remove(PAIRED_DEVICE_ADDRESS)
            }
        }
    }

    // --- Auto Reconnect ---

    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_RECONNECT] ?: DEFAULT_AUTO_RECONNECT
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_RECONNECT] = enabled
        }
    }

    // --- Setup Complete ---

    val setupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETE] ?: DEFAULT_SETUP_COMPLETE
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SETUP_COMPLETE] = complete
        }
    }
}
