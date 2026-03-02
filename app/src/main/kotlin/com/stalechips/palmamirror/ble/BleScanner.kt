package com.stalechips.palmamirror.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log

/**
 * Scans for BLE devices advertising the ANCS service.
 * Used during initial setup to find nearby iPhones.
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"
        private const val SCAN_TIMEOUT_MS = 30_000L
    }

    interface ScanListener {
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onScanFailed(errorCode: Int)
        fun onScanTimeout()
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var listener: ScanListener? = null
    private val handler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            Log.d(TAG, "Device found: ${device.address}, RSSI: $rssi")
            listener?.onDeviceFound(device, rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
            listener?.onScanFailed(errorCode)
        }
    }

    private val timeoutRunnable = Runnable {
        if (isScanning) {
            stopScan()
            listener?.onScanTimeout()
        }
    }

    /**
     * Start scanning for BLE devices.
     * Filters for devices that may support ANCS (iPhones).
     */
    @SuppressLint("MissingPermission")
    fun startScan(scanListener: ScanListener) {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return
        }

        listener = scanListener
        scanner = adapter.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan for all BLE devices — we'll filter bonded iPhones after
        // ANCS service is not advertised, so we can't filter by UUID during scan
        scanner?.startScan(null, settings, scanCallback)
        isScanning = true
        handler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS)
        Log.d(TAG, "Scan started")
    }

    /**
     * Stop scanning.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        handler.removeCallbacks(timeoutRunnable)
        scanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Scan stopped")
    }

    fun isScanning(): Boolean = isScanning
}
