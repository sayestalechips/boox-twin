package com.stalechips.palmamirror.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Starts MirrorForegroundService on device boot to maintain BLE connection.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Boot completed — starting MirrorForegroundService")
            val serviceIntent = Intent(context, MirrorForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
