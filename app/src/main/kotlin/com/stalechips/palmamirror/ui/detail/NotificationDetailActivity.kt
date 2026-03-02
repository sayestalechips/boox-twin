package com.stalechips.palmamirror.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.stalechips.palmamirror.R

/**
 * Shows full notification detail with action buttons.
 * E-ink optimized: scrollable content, high contrast, no animations.
 */
class NotificationDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_HAS_POSITIVE_ACTION = "has_positive_action"
        const val EXTRA_HAS_NEGATIVE_ACTION = "has_negative_action"
        const val EXTRA_POSITIVE_LABEL = "positive_label"
        const val EXTRA_NEGATIVE_LABEL = "negative_label"
        const val EXTRA_IS_MESSAGE = "is_message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: getString(R.string.detail_no_content)
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: ""
        val isMessage = intent.getBooleanExtra(EXTRA_IS_MESSAGE, false)
        val hasPositiveAction = intent.getBooleanExtra(EXTRA_HAS_POSITIVE_ACTION, false)

        findViewById<TextView>(R.id.detailAppName).text = appName
        findViewById<TextView>(R.id.detailTitle).text = title
        findViewById<TextView>(R.id.detailMessage).text = message
        findViewById<TextView>(R.id.detailTimestamp).text = timestamp

        val subtitleView = findViewById<TextView>(R.id.detailSubtitle)
        if (!subtitle.isNullOrBlank()) {
            subtitleView.text = subtitle
            subtitleView.visibility = View.VISIBLE
        }

        // Show reply button for message-type notifications
        val btnReply = findViewById<MaterialButton>(R.id.btnReply)
        if (isMessage && hasPositiveAction) {
            btnReply.visibility = View.VISIBLE
            btnReply.setOnClickListener {
                setResult(RESULT_OK, intent.putExtra("action", "reply"))
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.btnDismiss).setOnClickListener {
            setResult(RESULT_OK, intent.putExtra("action", "dismiss"))
            finish()
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}
