package com.stalechips.palmamirror.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stalechips.palmamirror.R

/**
 * Settings screen for PalmaMirror.
 * E-ink optimized: scrollable, high contrast, large touch targets.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.settingsVersion).text =
                getString(R.string.settings_version, versionName)
        } catch (e: Exception) {
            findViewById<TextView>(R.id.settingsVersion).text =
                getString(R.string.settings_version, "unknown")
        }

        // Back button
        findViewById<TextView>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        // Reconnect button
        findViewById<MaterialButton>(R.id.btnReconnect).setOnClickListener {
            // Signal the service to reconnect
            setResult(RESULT_OK, Intent().putExtra("action", "reconnect"))
        }

        // Battery optimization
        findViewById<MaterialButton>(R.id.btnBatteryOptimization).setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        // Font size
        findViewById<RadioGroup>(R.id.fontSizeGroup).setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.fontSmall -> "small"
                R.id.fontMedium -> "medium"
                R.id.fontLarge -> "large"
                R.id.fontXLarge -> "xlarge"
                else -> "medium"
            }
            // Will be persisted via DataStore in Phase 5
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
