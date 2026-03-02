package com.stalechips.palmamirror.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stalechips.palmamirror.R
import com.stalechips.palmamirror.ancs.AncsCategory
import com.stalechips.palmamirror.ancs.AncsNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for notification cards on the home screen.
 * E-ink optimized: no animations, instant updates.
 */
class NotificationAdapter(
    private val onItemClick: (AncsNotification) -> Unit
) : ListAdapter<AncsNotification, NotificationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryIndicator: TextView = itemView.findViewById(R.id.categoryIndicator)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val title: TextView = itemView.findViewById(R.id.notificationTitle)
        private val messagePreview: TextView = itemView.findViewById(R.id.messagePreview)

        fun bind(notification: AncsNotification) {
            categoryIndicator.text = notification.category.indicator
            appName.text = notification.appDisplayName ?: notification.appIdentifier ?: notification.category.displayName
            timestamp.text = formatTimestamp(notification.receivedAt)
            title.text = notification.displayTitle

            val message = notification.message
            if (message.isNullOrBlank()) {
                messagePreview.visibility = View.GONE
            } else {
                messagePreview.visibility = View.VISIBLE
                messagePreview.text = message
            }

            // Bold unread notifications
            val typeface = if (notification.isRead) {
                android.graphics.Typeface.DEFAULT
            } else {
                android.graphics.Typeface.DEFAULT_BOLD
            }
            title.typeface = typeface

            itemView.setOnClickListener { onItemClick(notification) }
        }

        private fun formatTimestamp(timeMs: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timeMs

            return when {
                diff < 60_000 -> "now"
                diff < 3_600_000 -> "${diff / 60_000}m"
                diff < 86_400_000 -> "${diff / 3_600_000}h"
                else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timeMs))
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AncsNotification>() {
        override fun areItemsTheSame(old: AncsNotification, new: AncsNotification): Boolean {
            return old.uid == new.uid
        }

        override fun areContentsTheSame(old: AncsNotification, new: AncsNotification): Boolean {
            return old == new
        }
    }
}
