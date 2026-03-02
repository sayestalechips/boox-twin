package com.stalechips.palmamirror

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PalmaMirrorApp : Application() {

    companion object {
        const val CHANNEL_ID = "palmamirror_connection"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
