package com.stalechips.palmamirror

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.stalechips.palmamirror.ble.BleConnectionManager
import com.stalechips.palmamirror.service.MirrorForegroundService
import com.stalechips.palmamirror.ui.home.NotificationAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, getString(R.string.error_permissions_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(this, getString(R.string.error_bluetooth_off), Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var connectionStatus: TextView
    private lateinit var emptyState: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var notificationList: RecyclerView
    private lateinit var adapter: NotificationAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var service: MirrorForegroundService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatus = findViewById(R.id.connectionStatus)
        emptyState = findViewById(R.id.emptyState)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        notificationList = findViewById(R.id.notificationList)

        // Setup RecyclerView
        adapter = NotificationAdapter { notification ->
            // TODO: open detail activity
        }
        notificationList.layoutManager = LinearLayoutManager(this)
        notificationList.adapter = adapter

        // Fix stuck spinner — stop after 2 seconds max
        swipeRefresh.setOnRefreshListener {
            handler.postDelayed({ swipeRefresh.isRefreshing = false }, 2000)
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, getString(R.string.error_no_ble), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        checkPermissionsAndStart()
        showBondedDeviceInfo()
    }

    @SuppressLint("MissingPermission")
    private fun showBondedDeviceInfo() {
        val adapter = bluetoothAdapter ?: return
        val bonded = adapter.bondedDevices ?: return
        val names = bonded.map { "${it.name ?: "?"} (type=${it.type})" }
        val info = if (names.isEmpty()) {
            "No paired devices found.\nPair your iPhone in Settings → Bluetooth first."
        } else {
            "Found ${names.size} paired device(s):\n${names.joinToString("\n")}\n\nConnecting..."
        }
        emptyState.text = info
    }

    private fun checkPermissionsAndStart() {
        if (bluetoothAdapter?.isEnabled != true) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            onPermissionsGranted()
        }
    }

    private fun onPermissionsGranted() {
        startMirrorService()
        observeService()
    }

    private fun startMirrorService() {
        val serviceIntent = Intent(this, MirrorForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun observeService() {
        // Poll service state every 2 seconds to update UI
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateConnectionUI()
                handler.postDelayed(this, 2000)
            }
        }, 1000)
    }

    private fun updateConnectionUI() {
        // Update connection status text based on service state
        val serviceRunning = MirrorForegroundService.instance != null
        val svc = MirrorForegroundService.instance

        if (svc != null) {
            val state = svc.getConnectionState()
            connectionStatus.text = when (state) {
                BleConnectionManager.ConnectionState.CONNECTED -> "Connected"
                BleConnectionManager.ConnectionState.CONNECTING -> "Connecting..."
                BleConnectionManager.ConnectionState.DISCOVERING_SERVICES -> "Discovering services..."
                BleConnectionManager.ConnectionState.SUBSCRIBING -> "Subscribing to notifications..."
                BleConnectionManager.ConnectionState.SCANNING -> "Scanning..."
                BleConnectionManager.ConnectionState.DISCONNECTED -> "Disconnected"
            }

            // Update notification list
            val notifications = svc.repository.notifications.value
            adapter.submitList(notifications)

            if (notifications.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                notificationList.visibility = View.GONE
                if (state == BleConnectionManager.ConnectionState.CONNECTED) {
                    emptyState.text = getString(R.string.home_empty)
                } else if (state == BleConnectionManager.ConnectionState.DISCONNECTED) {
                    showBondedDeviceInfo()
                }
            } else {
                emptyState.visibility = View.GONE
                notificationList.visibility = View.VISIBLE
            }
        }
    }
}
