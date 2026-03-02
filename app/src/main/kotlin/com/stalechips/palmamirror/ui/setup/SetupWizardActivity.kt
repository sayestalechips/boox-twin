package com.stalechips.palmamirror.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stalechips.palmamirror.MainActivity
import com.stalechips.palmamirror.R
import com.stalechips.palmamirror.ble.BlePermissionHelper

/**
 * First-run setup wizard: permissions → Bluetooth → pair → done.
 * E-ink optimized: one step per screen, large text, clear actions.
 */
class SetupWizardActivity : AppCompatActivity() {

    private var currentStep = 0
    private val totalSteps = 4

    private lateinit var stepIndicator: TextView
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var actionButton: MaterialButton
    private lateinit var skipButton: MaterialButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            nextStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        stepIndicator = findViewById(R.id.stepIndicator)
        titleView = findViewById(R.id.setupTitle)
        descriptionView = findViewById(R.id.setupDescription)
        actionButton = findViewById(R.id.btnSetupAction)
        skipButton = findViewById(R.id.btnSetupSkip)

        showStep(0)
    }

    private fun showStep(step: Int) {
        currentStep = step
        stepIndicator.text = "Step ${step + 1} of $totalSteps"

        when (step) {
            0 -> showWelcome()
            1 -> showPermissions()
            2 -> showPairing()
            3 -> showDone()
        }
    }

    private fun showWelcome() {
        titleView.text = getString(R.string.setup_welcome)
        descriptionView.text = getString(R.string.setup_welcome_desc)
        actionButton.text = getString(R.string.setup_next)
        skipButton.visibility = View.GONE
        actionButton.setOnClickListener { nextStep() }
    }

    private fun showPermissions() {
        titleView.text = getString(R.string.setup_permissions)
        descriptionView.text = getString(R.string.setup_permissions_desc)
        actionButton.text = getString(R.string.setup_grant_permissions)
        skipButton.visibility = View.GONE

        actionButton.setOnClickListener {
            if (BlePermissionHelper.hasAllPermissions(this)) {
                nextStep()
            } else {
                permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
            }
        }
    }

    private fun showPairing() {
        titleView.text = getString(R.string.setup_pair)
        descriptionView.text = getString(R.string.setup_pair_desc)
        actionButton.text = getString(R.string.setup_start_pairing)
        skipButton.visibility = View.VISIBLE
        skipButton.setOnClickListener { nextStep() }

        actionButton.setOnClickListener {
            // Open Bluetooth settings for manual pairing
            try {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e: Exception) {
                // Fallback — just proceed
            }
            nextStep()
        }
    }

    private fun showDone() {
        titleView.text = getString(R.string.setup_done)
        descriptionView.text = getString(R.string.setup_done_desc)
        actionButton.text = getString(R.string.setup_finish)
        skipButton.visibility = View.GONE

        actionButton.setOnClickListener {
            // Mark setup as complete and go to main
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun nextStep() {
        if (currentStep < totalSteps - 1) {
            showStep(currentStep + 1)
        }
    }
}
