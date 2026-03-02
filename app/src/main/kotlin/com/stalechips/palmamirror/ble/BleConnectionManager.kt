package com.stalechips.palmamirror.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages BLE connection lifecycle to an iPhone for ANCS.
 * States: DISCONNECTED → SCANNING → CONNECTING → DISCOVERING_SERVICES → SUBSCRIBING → CONNECTED
 */
class BleConnectionManager(
    private val context: Context,
    private val listener: ConnectionListener
) {

    companion object {
        private const val TAG = "BleConnectionManager"
    }

    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        DISCOVERING_SERVICES,
        SUBSCRIBING,
        CONNECTED
    }

    interface ConnectionListener {
        fun onStateChanged(state: ConnectionState)
        fun onAncsReady(gatt: BluetoothGatt)
        fun onNotificationSourceData(data: ByteArray)
        fun onDataSourceData(data: ByteArray)
        fun onError(message: String)
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null

    // GATT write queue — Android BLE only allows one pending write at a time
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var writeInProgress = false

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateState(ConnectionState.DISCOVERING_SERVICES)
                    gatt.requestMtu(AncsConstants.DESIRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    bluetoothGatt = null
                    updateState(ConnectionState.DISCONNECTED)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, status=$status")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("Service discovery failed with status $status")
                return
            }

            val ancsService = gatt.getService(AncsConstants.ANCS_SERVICE_UUID)
            if (ancsService == null) {
                listener.onError("ANCS service not found on iPhone. Make sure the phone is unlocked.")
                return
            }

            Log.d(TAG, "ANCS service found! Subscribing to characteristics...")
            updateState(ConnectionState.SUBSCRIBING)
            subscribeToNotifications(gatt, ancsService)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                AncsConstants.NOTIFICATION_SOURCE_UUID -> {
                    Log.d(TAG, "Notification Source data received: ${value.size} bytes")
                    listener.onNotificationSourceData(value)
                }
                AncsConstants.DATA_SOURCE_UUID -> {
                    Log.d(TAG, "Data Source data received: ${value.size} bytes")
                    listener.onDataSourceData(value)
                }
            }
        }

        @Deprecated("Deprecated in API 33, kept for backward compat")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            onCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite: ${characteristic.uuid}, status=$status")
            writeInProgress = false
            processNextWrite()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "Descriptor write for ${descriptor.characteristic.uuid}: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleSubscriptionProgress(gatt, descriptor)
            } else {
                listener.onError("Failed to subscribe to notifications (status $status)")
            }
        }
    }

    private var subscribedDataSource = false
    private var subscribedNotificationSource = false

    @SuppressLint("MissingPermission")
    private fun subscribeToNotifications(gatt: BluetoothGatt, ancsService: BluetoothGattService) {
        subscribedDataSource = false
        subscribedNotificationSource = false

        // Subscribe to Data Source FIRST (must be done before Notification Source per ANCS spec)
        val dataSource = ancsService.getCharacteristic(AncsConstants.DATA_SOURCE_UUID)
        if (dataSource != null) {
            gatt.setCharacteristicNotification(dataSource, true)
            val descriptor = dataSource.getDescriptor(AncsConstants.CCCD_UUID)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        } else {
            listener.onError("Data Source characteristic not found")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleSubscriptionProgress(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        when (descriptor.characteristic.uuid) {
            AncsConstants.DATA_SOURCE_UUID -> {
                subscribedDataSource = true
                Log.d(TAG, "Subscribed to Data Source. Now subscribing to Notification Source...")

                // Now subscribe to Notification Source
                val ancsService = gatt.getService(AncsConstants.ANCS_SERVICE_UUID) ?: return
                val notifSource = ancsService.getCharacteristic(AncsConstants.NOTIFICATION_SOURCE_UUID)
                if (notifSource != null) {
                    gatt.setCharacteristicNotification(notifSource, true)
                    val cccd = notifSource.getDescriptor(AncsConstants.CCCD_UUID)
                    cccd?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                    }
                }
            }
            AncsConstants.NOTIFICATION_SOURCE_UUID -> {
                subscribedNotificationSource = true
                Log.d(TAG, "Subscribed to Notification Source. ANCS ready!")
                updateState(ConnectionState.CONNECTED)
                listener.onAncsReady(gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to device: ${device.address}")
        updateState(ConnectionState.CONNECTING)
        targetDevice = device
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun reconnect() {
        val device = targetDevice ?: return
        Log.d(TAG, "Reconnecting to device: ${device.address}")
        updateState(ConnectionState.CONNECTING)
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnecting")
        writeQueue.clear()
        writeInProgress = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        updateState(ConnectionState.DISCONNECTED)
    }

    /**
     * Queue a write to the ANCS Control Point. Writes are serialized
     * so only one GATT write is in flight at a time.
     */
    fun writeControlPoint(data: ByteArray): Boolean {
        if (bluetoothGatt == null) return false
        writeQueue.add(data)
        processNextWrite()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun processNextWrite() {
        if (writeInProgress) return
        val data = writeQueue.poll() ?: return
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(AncsConstants.ANCS_SERVICE_UUID) ?: return
        val controlPoint = service.getCharacteristic(AncsConstants.CONTROL_POINT_UUID) ?: return

        writeInProgress = true
        controlPoint.value = data
        controlPoint.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt.writeCharacteristic(controlPoint)
        if (!success) {
            Log.w(TAG, "writeCharacteristic failed, retrying next in queue")
            writeInProgress = false
            processNextWrite()
        }
    }

    fun getConnectedGatt(): BluetoothGatt? = bluetoothGatt

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    @SuppressLint("MissingPermission")
    fun getBondedIPhones(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        val allBonded = adapter.bondedDevices?.toList() ?: emptyList()
        Log.d(TAG, "Bonded devices: ${allBonded.size}")
        for (device in allBonded) {
            Log.d(TAG, "  Device: ${device.name ?: "unnamed"} [${device.address}] type=${device.type}")
        }
        // Return ALL bonded devices — iPhones can show as any type depending on pairing method
        return allBonded
    }

    private fun updateState(newState: ConnectionState) {
        Log.d(TAG, "State: ${_connectionState.value} → $newState")
        _connectionState.value = newState
        listener.onStateChanged(newState)
    }
}
