package com.stalechips.palmamirror.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.stalechips.palmamirror.MainActivity
import com.stalechips.palmamirror.PalmaMirrorApp
import com.stalechips.palmamirror.R
import com.stalechips.palmamirror.ancs.AncsAttributeParser
import com.stalechips.palmamirror.ancs.AncsEventParser
import com.stalechips.palmamirror.ancs.AncsNotification
import com.stalechips.palmamirror.ble.AncsService
import com.stalechips.palmamirror.ble.BleConnectionManager
import com.stalechips.palmamirror.ble.BleReconnector
import com.stalechips.palmamirror.data.NotificationRepository

/**
 * Foreground service that maintains the BLE connection to the iPhone
 * and processes ANCS notifications.
 */
class MirrorForegroundService : Service(), BleConnectionManager.ConnectionListener {

    companion object {
        private const val TAG = "MirrorForegroundService"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var connectionManager: BleConnectionManager
    private lateinit var ancsService: AncsService
    private lateinit var reconnector: BleReconnector
    private val eventParser = AncsEventParser()
    private val attributeParser = AncsAttributeParser()
    val repository = NotificationRepository()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        connectionManager = BleConnectionManager(this, this)
        ancsService = AncsService(connectionManager)
        reconnector = BleReconnector(
            onReconnect = { connectionManager.reconnect() }
        )

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_connecting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Try to connect to a bonded iPhone
        val bondedDevices = connectionManager.getBondedIPhones()
        if (bondedDevices.isNotEmpty()) {
            connectionManager.connectToDevice(bondedDevices.first())
        } else {
            Log.w(TAG, "No bonded BLE devices found")
            updateNotification(getString(R.string.notification_disconnected))
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        reconnector.stop()
        connectionManager.disconnect()
        super.onDestroy()
    }

    // --- BleConnectionManager.ConnectionListener ---

    override fun onStateChanged(state: BleConnectionManager.ConnectionState) {
        Log.d(TAG, "Connection state: $state")
        when (state) {
            BleConnectionManager.ConnectionState.CONNECTED -> {
                reconnector.stop()
                updateNotification(getString(R.string.notification_connected))
            }
            BleConnectionManager.ConnectionState.DISCONNECTED -> {
                updateNotification(getString(R.string.notification_disconnected))
                reconnector.startReconnecting()
            }
            BleConnectionManager.ConnectionState.CONNECTING,
            BleConnectionManager.ConnectionState.SCANNING,
            BleConnectionManager.ConnectionState.DISCOVERING_SERVICES,
            BleConnectionManager.ConnectionState.SUBSCRIBING -> {
                updateNotification(getString(R.string.notification_connecting))
            }
        }
    }

    override fun onAncsReady(gatt: BluetoothGatt) {
        Log.d(TAG, "ANCS is ready! Listening for notifications...")
    }

    override fun onNotificationSourceData(data: ByteArray) {
        val notification = eventParser.parse(data) ?: return

        repository.addOrUpdate(notification)

        // Request full attributes for added/modified notifications
        if (notification.eventId != AncsNotification.EventId.REMOVED) {
            ancsService.requestNotificationAttributes(notification.uid)
        }
    }

    override fun onDataSourceData(data: ByteArray) {
        val result = attributeParser.feedData(data) ?: return

        // Update the notification with parsed attributes
        repository.updateAttributes(result.notificationUID) { existing ->
            attributeParser.applyAttributes(existing, result)
        }
    }

    override fun onError(message: String) {
        Log.e(TAG, "BLE Error: $message")
    }

    // --- Notification management ---

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PalmaMirrorApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
