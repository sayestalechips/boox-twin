package com.stalechips.palmamirror.ui.call

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stalechips.palmamirror.R

/**
 * Full-screen incoming call activity.
 * E-ink optimized: pure B&W, massive touch targets, no animations.
 */
class IncomingCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_NUMBER = "caller_number"
    }

    private var notificationUID: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        notificationUID = intent.getIntExtra(EXTRA_NOTIFICATION_UID, -1)
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: getString(R.string.call_unknown)
        val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: ""

        findViewById<TextView>(R.id.callerName).text = callerName
        findViewById<TextView>(R.id.callerNumber).text = callerNumber

        findViewById<MaterialButton>(R.id.btnAccept).setOnClickListener {
            setResult(RESULT_OK, intent.putExtra("action", "accept"))
            finish()
        }

        findViewById<MaterialButton>(R.id.btnReject).setOnClickListener {
            setResult(RESULT_OK, intent.putExtra("action", "reject"))
            finish()
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // Don't allow back during incoming call — must accept or reject
    }
}
