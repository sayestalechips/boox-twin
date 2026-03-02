# Android BLE Persistent Connection Best Practices

## Context: PalmaMirror on Boox Palma 2

**Target device**: Boox Palma 2 (Android 13, Qualcomm SoC, BT 5.1, E-Ink display)
**Use case**: Maintain a constant BLE connection from the Boox Palma 2 (Android, GATT Client / Central) to an iPhone (GATT Server / Peripheral)
**Key challenge**: E-Ink devices aggressively conserve power; the app must survive Doze, screen-off, and device reboots without dropping the BLE link

> **Note on Boox Palma 2 vs Palma 1**: The original Palma runs Android 11. The Palma 2 runs Android 13 with BT 5.1. This document covers Android 11+ patterns but calls out Android 12/13/14 differences where relevant. If targeting the original Palma (Android 11), ignore the Android 12+ permission changes and foreground service type requirements.

---

## 1. Foreground Service Patterns for BLE

A foreground service is the **single most important mechanism** for keeping a BLE connection alive on modern Android. Without one, the system will kill your process within minutes of the app leaving the foreground.

### 1.1 Why a Foreground Service

- Android aggressively kills background processes to reclaim memory
- BLE connections are tied to the process; when the process dies, the GATT connection drops
- A foreground service with an ongoing notification tells the OS "this process is doing important work -- keep it alive"
- Starting from Android 8 (API 26), background services are killed within ~1 minute; foreground services are the only reliable long-running option

### 1.2 Service Declaration (AndroidManifest.xml)

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Android 14+ requires specific foreground service type permission -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<application ...>
    <service
        android:name=".ble.BleConnectionService"
        android:foregroundServiceType="connectedDevice"
        android:exported="false" />
</application>
```

**Important**: The `foregroundServiceType="connectedDevice"` attribute is **required** for apps targeting Android 14 (API 34)+. For Android 11-13 targets, it is still recommended to declare it for forward compatibility. The `connectedDevice` type covers Bluetooth, NFC, USB, and network-connected external devices.

### 1.3 Notification Channel Setup

```kotlin
// Create notification channel (required Android 8+)
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,                          // e.g., "ble_connection_channel"
        "BLE Connection",                    // User-visible name
        NotificationManager.IMPORTANCE_LOW   // LOW = no sound, shows in shade
    ).apply {
        description = "Maintains connection to your iPhone"
        setShowBadge(false)
    }
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)
}

// Build the foreground notification
private fun buildNotification(status: String): Notification {
    val pendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("PalmaMirror")
        .setContentText(status)  // e.g., "Connected to iPhone"
        .setSmallIcon(R.drawable.ic_bluetooth_connected)
        .setOngoing(true)          // Cannot be swiped away
        .setCategory(Notification.CATEGORY_SERVICE)
        .setContentIntent(pendingIntent)
        .build()
}
```

### 1.4 Service Lifecycle

```kotlin
class BleConnectionService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately (must happen within 5 seconds)
        val notification = buildNotification("Connecting...")
        startForeground(NOTIFICATION_ID, notification)

        // Initiate BLE connection
        connectToDevice()

        // START_STICKY: System restarts service with null intent if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Return a binder if you need Activity <-> Service communication
        return LocalBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleConnectionService = this@BleConnectionService
    }
}
```

### 1.5 START_STICKY vs START_REDELIVER_INTENT

| Aspect | START_STICKY | START_REDELIVER_INTENT |
|--------|-------------|----------------------|
| **Behavior after kill** | System restarts service, calls onStartCommand with **null** intent | System restarts service, calls onStartCommand with the **last delivered intent** |
| **Use case** | Service that self-manages its state (e.g., BLE reconnection based on saved device address) | Service that needs the original intent data to resume (e.g., specific command parameters) |
| **Recommendation for BLE** | **Preferred**. The service should store the target device address persistently (SharedPreferences) and reconnect on restart regardless of intent contents | Alternative if you pass critical params via intent |

**Recommendation**: Use `START_STICKY`. Store the target device MAC address in SharedPreferences so the service can reconnect even when restarted with a null intent.

### 1.6 Starting the Service

```kotlin
// From Activity or BroadcastReceiver
val serviceIntent = Intent(context, BleConnectionService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(serviceIntent)
} else {
    context.startService(serviceIntent)
}
```

---

## 2. Doze Mode Exemption

### 2.1 How Doze Affects BLE

Doze mode activates when the device is:
- Unplugged
- Stationary (on Android 6-7 only; Android 7+ has "light Doze" even while moving)
- Screen off for an extended period

**What Doze restricts**:
- Network access (including WiFi)
- WakeLocks (deferred)
- AlarmManager alarms (deferred to maintenance windows)
- JobScheduler jobs

**What Doze does NOT restrict**:
- **Active BLE GATT connections are NOT killed by Doze** -- the Bluetooth radio operates independently of the application-layer Doze restrictions
- BLE notifications/indications continue to be delivered to the BluetoothGattCallback
- However, if your process is killed (OOM or aggressive battery saver), the connection dies

**The real risk**: Not Doze killing the BLE link directly, but Doze + aggressive OEMs killing the process hosting the BLE connection. The foreground service is the primary defense.

### 2.2 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

Request the user to whitelist the app from battery optimization:

```kotlin
// Check if already whitelisted
val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)

if (!isIgnoring) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    startActivity(intent)
}
```

**Manifest permission**:
```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

**Important caveats**:
- Google Play Store may reject apps that use this unless the core function requires it (BLE companion apps are an acceptable use case)
- Since PalmaMirror likely won't be on Play Store (sideloaded on Boox), this restriction is moot
- This exemption helps prevent the process from being killed, but does NOT guarantee it

### 2.3 AlarmManager for Periodic Health Checks

Even with a foreground service, use alarms as a safety net to verify the BLE connection is alive:

```kotlin
val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
val intent = Intent(this, BleHealthCheckReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(
    this, 0, intent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

// setExactAndAllowWhileIdle fires even in Doze (at most once per 9 minutes in Doze)
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL_MS,  // e.g., 15 minutes
    pendingIntent
)
```

```kotlin
class BleHealthCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Check if BLE service is running and connection is alive
        // If not, restart the service and reconnect
        val serviceIntent = Intent(context, BleConnectionService::class.java)
        serviceIntent.action = ACTION_HEALTH_CHECK
        context.startForegroundService(serviceIntent)
    }
}
```

**Note**: `setExactAndAllowWhileIdle` has a minimum interval of ~9 minutes during Doze. Do not attempt to fire alarms more frequently.

### 2.4 PowerManager WakeLock

Use a partial wake lock sparingly -- primarily during reconnection attempts where CPU must stay active:

```kotlin
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "PalmaMirror::BleReconnect"
)

// Acquire with timeout to prevent stuck wake locks
wakeLock.acquire(60_000L)  // 60 seconds max
try {
    // Perform reconnection
    reconnectToDevice()
} finally {
    if (wakeLock.isHeld) {
        wakeLock.release()
    }
}
```

**Manifest permission**:
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 3. Reconnection Strategies

### 3.1 Exponential Backoff Pattern

```kotlin
class BleReconnectionManager {
    companion object {
        private const val BASE_DELAY_MS = 1_000L        // 1 second
        private const val MAX_DELAY_MS = 300_000L        // 5 minutes
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val JITTER_FACTOR = 0.2            // 20% jitter
    }

    private var attemptCount = 0
    private val handler = Handler(Looper.getMainLooper())

    fun scheduleReconnect(onReconnect: () -> Unit) {
        val delay = calculateDelay()
        attemptCount++

        Log.d(TAG, "Scheduling reconnect attempt $attemptCount in ${delay}ms")
        handler.postDelayed({ onReconnect() }, delay)
    }

    private fun calculateDelay(): Long {
        val exponentialDelay = (BASE_DELAY_MS * BACKOFF_MULTIPLIER.pow(attemptCount)).toLong()
        val clampedDelay = min(exponentialDelay, MAX_DELAY_MS)

        // Add jitter to prevent thundering herd
        val jitter = (clampedDelay * JITTER_FACTOR * Random.nextDouble()).toLong()
        return clampedDelay + jitter
    }

    fun resetBackoff() {
        attemptCount = 0
    }
}
```

**Recommended backoff schedule**: 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s (cap)

### 3.2 BluetoothGatt.connect() vs connectGatt()

These are two **different methods** with different semantics:

#### `BluetoothDevice.connectGatt(context, autoConnect, callback)`
- Creates a **new** BluetoothGatt object
- Used for the **initial connection** or after calling `close()`
- The `autoConnect` parameter controls connection behavior (see 3.3)
- Returns a BluetoothGatt instance

#### `BluetoothGatt.connect()`
- **Reuses an existing** BluetoothGatt object
- Used to **reconnect** after `disconnect()` was called (without `close()`)
- Always behaves like `autoConnect=true` -- it waits for the device to appear
- Does not time out
- Faster than creating a new BluetoothGatt because it reuses cached services

**When to use which**:
```
Initial connection     -> device.connectGatt(ctx, false, callback)  // direct connect
Reconnect same session -> gatt.connect()                            // reuse GATT
After close() / error  -> device.connectGatt(ctx, true, callback)   // new GATT, auto
```

### 3.3 autoConnect=true vs autoConnect=false

| Aspect | autoConnect=false | autoConnect=true |
|--------|-------------------|------------------|
| **Connection speed** | Fast (~1-2 seconds if device nearby) | Slow (uses low-power background scan) |
| **Timeout** | ~30 seconds (OEM-dependent; Samsung sometimes 10s) | Never times out |
| **Concurrent attempts** | Only ONE at a time across all apps | Multiple allowed |
| **Use case** | Initial connection when device is known to be nearby | Reconnection to a previously known device |
| **Power consumption** | Higher during attempt (direct connect) | Lower (passive scan) |

**Strategy for PalmaMirror**:
1. **First connect**: Use `autoConnect=false` with manual timeout handling
2. **Reconnection after disconnect**: Use `autoConnect=true` to let the stack handle it
3. **Reconnection after error 133**: Close GATT, wait 200ms, create new GATT with `autoConnect=false`

### 3.4 When to close() vs reconnect()

```
Disconnected normally (status 0, 19):
  -> gatt.connect()                    // Reuse GATT, auto-reconnect

GATT_ERROR (133) or unknown error:
  -> gatt.close()                      // Must destroy the GATT object
  -> wait 200-1000ms                   // Let the stack settle
  -> device.connectGatt(autoConnect)   // Create fresh GATT

Multiple consecutive failures (3+):
  -> gatt.close()
  -> Toggle Bluetooth adapter off/on   // Nuclear option, use sparingly
  -> Rescan for device
  -> device.connectGatt(false, ...)

Connection timeout:
  -> gatt.disconnect()
  -> gatt.close()
  -> Back off and retry with connectGatt()
```

**Critical rule**: ALWAYS call `close()` after `disconnect()` if you don't intend to reuse the GATT object. Failure to call `close()` causes **resource leaks** and eventually prevents new connections (Android has a ~30 GATT client limit).

### 3.5 Handling Common GATT Errors

| Status Code | Name | Cause | Recovery |
|-------------|------|-------|----------|
| **0** | GATT_SUCCESS | Normal disconnect (local initiated) | `gatt.connect()` to reconnect |
| **8** | GATT_CONN_TIMEOUT | Supervision timeout -- device went out of range or stopped responding | Close GATT, backoff, reconnect |
| **19** | GATT_CONN_TERMINATE_PEER_USER | Remote device (iPhone) intentionally disconnected | `gatt.connect()` -- device may reconnect soon |
| **20** | GATT_CONN_TERMINATE_LOCAL_HOST | Local Android stack disconnected | Close GATT, reconnect |
| **22** | GATT_CONN_FAIL_ESTABLISH | Failed to establish connection | Close GATT, backoff, retry |
| **133** | GATT_ERROR | Generic catch-all error | **Always close() GATT**, wait 200ms+, create new GATT object |
| **257** | GATT_INTERNAL_ERROR | Internal Bluetooth stack error | Close GATT, may need BT adapter restart |

**Error 133 deep dive**: This is the most common and frustrating error. Common causes:
- Too many GATT objects not properly closed (resource exhaustion)
- Connecting too rapidly after a previous failure
- Bluetooth stack corruption (requires adapter toggle)
- Device bonding issues
- Trying to connect while another connection attempt is pending

**Recovery pattern for error 133**:
```kotlin
private fun handleGattError133(gatt: BluetoothGatt) {
    gatt.close()  // Always close first

    // Delay before retry -- critical for 133 recovery
    handler.postDelayed({
        val device = bluetoothAdapter.getRemoteDevice(targetMacAddress)
        bluetoothGatt = device.connectGatt(
            this@BleConnectionService,
            false,   // Use direct connect for retry
            gattCallback,
            BluetoothDevice.TRANSPORT_LE  // Force LE transport
        )
    }, 1000L)  // 1 second delay minimum
}
```

### 3.6 Complete Reconnection State Machine

```
                    +----------------+
                    |   IDLE         |
                    +-------+--------+
                            |
                     startConnection()
                            |
                            v
                    +----------------+
                    |  CONNECTING    |<-----------+
                    +-------+--------+            |
                            |                     |
                   onConnectionStateChange        |
                      /            \              |
                     /              \             |
                    v                v            |
           +----------+      +----------+        |
           | CONNECTED|      |  FAILED  |--------+
           +----+-----+      +----------+   (backoff + retry)
                |
         discoverServices()
                |
                v
           +----------+
           |DISCOVERED|  <-- Normal operating state
           +----+-----+
                |
         onConnectionStateChange (disconnected)
                |
                v
           +----------+
           |RECONNECT |----> gatt.connect() or close+connectGatt
           +----------+
```

---

## 4. Android 11+ BLE Permissions

### 4.1 Permission Matrix

| Permission | Android 11 (API 30) | Android 12+ (API 31+) | Purpose |
|------------|--------------------|-----------------------|---------|
| BLUETOOTH | Required | Not needed (maxSdkVersion=30) | Legacy BT access |
| BLUETOOTH_ADMIN | Required | Not needed (maxSdkVersion=30) | Legacy BT admin |
| BLUETOOTH_CONNECT | N/A | **Runtime permission** | Connect to paired devices |
| BLUETOOTH_SCAN | N/A | **Runtime permission** | Discover BLE devices |
| BLUETOOTH_ADVERTISE | N/A | **Runtime permission** | Advertise as BLE peripheral |
| ACCESS_FINE_LOCATION | **Required for BLE scan** | Not required if `neverForLocation` declared | Location-based BLE scan |
| ACCESS_COARSE_LOCATION | Alternative to FINE | Not needed | Legacy location |

### 4.2 Manifest Declarations

```xml
<!-- Bluetooth feature declaration -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

<!-- Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<!-- Only include neverForLocation if you truly don't derive location from BLE scans -->

<!-- Location - required for BLE scanning on Android 11 and below -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Other -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### 4.3 Runtime Permission Flow

```kotlin
class MainActivity : AppCompatActivity() {

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // Android 11
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBleService()
        } else {
            // Show rationale or direct to settings
            showPermissionDeniedDialog()
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startBleService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
```

---

## 5. BLE Connection Best Practices

### 5.1 Connection Parameter Negotiation

After establishing a connection, negotiate optimal parameters:

```kotlin
// Request connection priority after connection is established
// and service discovery is complete
fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        // For persistent monitoring: use BALANCED or LOW_POWER
        // For data transfer bursts: temporarily use HIGH
        gatt.requestConnectionPriority(
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        )
        // CONNECTION_PRIORITY_HIGH:        interval 11.25-15ms, latency 0, timeout 2s
        // CONNECTION_PRIORITY_BALANCED:     interval 30-50ms,    latency 0, timeout 5s
        // CONNECTION_PRIORITY_LOW_POWER:    interval 100-125ms,  latency 2, timeout 6s
    }
}
```

**Strategy for PalmaMirror**:
- Use `CONNECTION_PRIORITY_BALANCED` for normal operation (good latency, reasonable power)
- Switch to `CONNECTION_PRIORITY_HIGH` briefly during data transfer bursts
- Switch to `CONNECTION_PRIORITY_LOW_POWER` when app is in background and only monitoring for notifications
- On E-Ink devices, prefer LOW_POWER to maximize battery life

### 5.2 MTU Negotiation

```kotlin
// Request MTU after connection, before service discovery
fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    if (newState == BluetoothProfile.STATE_CONNECTED) {
        // Request max MTU (Android 14+ auto-negotiates to 517)
        gatt.requestMtu(517)
    }
}

fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.d(TAG, "MTU negotiated: $mtu")
        // Effective payload = MTU - 3 (ATT header)
        maxPayloadSize = mtu - 3

        // Now proceed with service discovery
        gatt.discoverServices()
    }
}
```

**Key points**:
- Default MTU is 23 bytes (20 bytes usable payload)
- Maximum MTU is 517 bytes (514 bytes usable payload)
- Android 14+ automatically negotiates to 517; earlier versions need explicit `requestMtu()`
- Always request MTU BEFORE service discovery
- The `onMtuChanged` callback returns the **agreed** MTU (may be less than requested)
- **Boox Palma 2 (Android 13)**: Will need explicit MTU negotiation

### 5.3 GATT Operation Queuing

**Android BLE is strictly single-operation**. If you issue a second GATT operation before the callback from the first returns, the second operation will silently fail or cause undefined behavior.

```kotlin
class GattOperationQueue {
    private val operationQueue: ConcurrentLinkedQueue<GattOperation> = ConcurrentLinkedQueue()
    private val operationLock = ReentrantLock()
    private var pendingOperation: GattOperation? = null

    sealed class GattOperation {
        data class Read(val characteristic: BluetoothGattCharacteristic) : GattOperation()
        data class Write(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : GattOperation()
        data class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOperation()
        data class RequestMtu(val mtu: Int) : GattOperation()
        data class ReadRssi(val unit: Unit = Unit) : GattOperation()
    }

    fun enqueue(operation: GattOperation) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            executeNext()
        }
    }

    @Synchronized
    private fun executeNext() {
        if (pendingOperation != null) return

        val operation = operationQueue.poll() ?: return
        pendingOperation = operation

        when (operation) {
            is GattOperation.Read -> {
                gatt?.readCharacteristic(operation.characteristic)
            }
            is GattOperation.Write -> {
                operation.characteristic.value = operation.value
                gatt?.writeCharacteristic(operation.characteristic)
            }
            is GattOperation.WriteDescriptor -> {
                operation.descriptor.value = operation.value
                gatt?.writeDescriptor(operation.descriptor)
            }
            is GattOperation.RequestMtu -> {
                gatt?.requestMtu(operation.mtu)
            }
            is GattOperation.ReadRssi -> {
                gatt?.readRemoteRssi()
            }
        }

        // Safety timeout: if callback never fires, unblock queue
        handler.postDelayed({
            if (pendingOperation == operation) {
                Log.w(TAG, "GATT operation timed out: $operation")
                signalEndOfOperation()
            }
        }, OPERATION_TIMEOUT_MS)  // 10 seconds
    }

    fun signalEndOfOperation() {
        pendingOperation = null
        executeNext()
    }
}
```

**Rules**:
1. Every GATT operation must go through the queue
2. Every callback (success or failure) must call `signalEndOfOperation()`
3. Include a timeout handler to prevent the queue from getting permanently stuck
4. Service discovery (`discoverServices()`) is also a GATT operation -- queue it

### 5.4 Thread Safety with BluetoothGattCallback

**All BluetoothGattCallback methods are called on a Binder thread**, not the main thread. This has critical implications:

```kotlin
private val gattCallback = object : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        // Called on Binder thread -- NOT main thread
        // Do NOT touch UI directly
        // Do NOT assume this is the same thread as previous callbacks

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(TAG, "Connected to GATT server")
                // Post to main thread if needed for UI updates
                handler.post { updateConnectionState(true) }
                // Request MTU (this is a GATT operation -- goes through queue)
                operationQueue.enqueue(GattOperation.RequestMtu(517))
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "Disconnected from GATT server, status: $status")
                handler.post { updateConnectionState(false) }
                handleDisconnection(gatt, status)
            }
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        // Called on Binder thread for each notification/indication
        // Copy the value immediately -- it may be overwritten
        val value = characteristic.value.copyOf()
        handler.post { processNotification(characteristic.uuid, value) }
    }
}
```

**Thread safety rules**:
- Never access BluetoothGatt from multiple threads simultaneously
- Copy characteristic values immediately in callbacks (the byte array is reused)
- Use a Handler or coroutine dispatcher to relay events to the main thread
- Protect shared state with synchronization primitives
- **Do not call `gatt.disconnect()` or `gatt.close()` from within a callback** -- post it to the main thread handler

### 5.5 Enabling Notifications

```kotlin
fun enableNotifications(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic
): Boolean {
    // Step 1: Enable local notifications
    val success = gatt.setCharacteristicNotification(characteristic, true)
    if (!success) return false

    // Step 2: Write to the CCC descriptor on the remote device
    val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
        ?: return false

    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    // This MUST go through the operation queue
    operationQueue.enqueue(GattOperation.WriteDescriptor(descriptor,
        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))

    return true
}

companion object {
    val CCC_DESCRIPTOR_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
```

---

## 6. Boot Receiver for Auto-Starting BLE Service

### 6.1 Manifest Declaration

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
    <!-- Boot receiver -->
    <receiver
        android:name=".boot.BootCompletedReceiver"
        android:enabled="true"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        </intent-filter>
    </receiver>
</application>
```

**Note on `LOCKED_BOOT_COMPLETED`**: Fires before the user unlocks the device. Requires `android:directBootAware="true"` on the receiver. Useful for starting the BLE service as early as possible after reboot, but you can only access device-encrypted storage (not credential-encrypted storage like SharedPreferences).

### 6.2 BroadcastReceiver Implementation

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            Log.d(TAG, "Boot/update completed, starting BLE service")

            // Check if user has configured a target device
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val targetDevice = prefs.getString(KEY_TARGET_DEVICE, null)

            if (targetDevice != null) {
                val serviceIntent = Intent(context, BleConnectionService::class.java).apply {
                    action = ACTION_BOOT_CONNECT
                    putExtra(EXTRA_TARGET_DEVICE, targetDevice)
                }

                // BOOT_COMPLETED is an exempt condition for starting
                // foreground services from background on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
```

### 6.3 Android 12+ Background Start Exemptions

Starting with Android 12 (API 31), apps are generally forbidden from starting foreground services from the background. However, these exemptions apply:
- `ACTION_BOOT_COMPLETED` receiver -- **allowed**
- `ACTION_LOCKED_BOOT_COMPLETED` receiver -- **allowed**
- `ACTION_MY_PACKAGE_REPLACED` receiver -- **allowed**
- High-priority FCM push -- allowed (but not relevant for offline use)

**Android 14 additional restriction**: Apps targeting API 34 cannot start `connectedDevice` foreground services from `BOOT_COMPLETED` unless the app holds the `BLUETOOTH_CONNECT` permission at that point. Since runtime permissions persist across reboots (once granted), this is not an issue if the user previously granted permission.

---

## 7. Boox-Specific Considerations

### 7.1 Device Specifications (Palma 2)

| Spec | Value |
|------|-------|
| **Android version** | Android 13 (API 33) |
| **Bluetooth** | BT 5.1 (includes BLE) |
| **SoC** | Qualcomm (specific model unconfirmed) |
| **Display** | 6.13" E-Ink Carta Plus 1200 |
| **Battery** | 3,950 mAh |
| **RAM** | 4GB (6GB for Pro variant) |

> **Note**: The original Palma runs Android 11. Verify which hardware you have before finalizing permission and service type configurations.

### 7.2 Google Play Services Availability

- Boox devices ship with Google Play Store pre-installed (via GSF/GMS)
- However, GMS certification may be incomplete or sideloaded
- **Do NOT depend on Google Play Services** for core functionality:
  - Avoid Firebase Cloud Messaging (FCM) for wake-ups
  - Avoid Google's Companion Device Manager if it relies on Play Services
  - Use raw Android BLE APIs (`android.bluetooth.*`) directly
  - Use standard Android `AlarmManager` and `JobScheduler` instead of Play Services APIs

### 7.3 E-Ink Display Power Management

E-Ink devices have unique power characteristics:
- **Display draws zero power when static** -- the screen does not need refreshing
- **CPU is often aggressively suspended** by Boox's custom firmware to extend battery life
- **Boox's own power management layer** sits on top of Android's standard Doze -- it may be more aggressive than stock Android

**Mitigations**:
1. **Battery optimization exemption** is critical on Boox (see Section 2.2)
2. Boox devices have a setting: **Settings > Apps > App Optimization** -- the app must be set to "Don't Optimize"
3. Some Boox firmware versions have an **"App Freeze"** feature that force-stops apps -- ensure PalmaMirror is excluded
4. The foreground service notification prevents the system from treating the app as "idle"

### 7.4 Boox Custom ROM Behavior

Boox runs a modified Android with their own launcher and system apps:
- The Bluetooth stack is **standard Android** (Fluoride/GaBeldorsche) -- no custom BLE implementation
- Boox may add custom battery saving that kills background apps beyond what stock Android does
- The `adb shell dumpsys deviceidle` commands work normally for debugging Doze behavior
- Boox supports standard BroadcastReceivers for `BOOT_COMPLETED`
- No reports of Boox blocking foreground services

### 7.5 Recommendations for Boox

1. **Always request battery optimization exemption** on first launch
2. **Guide the user** through Boox-specific settings to whitelist the app
3. **Test with `adb shell dumpsys battery unplug`** to simulate Doze on the device
4. **Do not rely on GMS/FCM** -- use AlarmManager for periodic health checks
5. **Use WakeLock during reconnection** to prevent CPU suspension during critical BLE operations
6. **Consider the e-ink refresh rate** -- don't update the notification text too frequently (each update triggers a partial e-ink refresh which is slow and drains battery)

---

## 8. CompanionDeviceManager (Alternative Approach)

Android's CompanionDeviceManager provides an alternative pattern for persistent BLE connections with system-level support:

### 8.1 Overview

```kotlin
// Associate with a BLE device (replaces manual scanning + pairing)
val deviceFilter = BluetoothLeDeviceFilter.Builder()
    .setNamePattern(Pattern.compile("iPhone.*"))
    .build()

val request = AssociationRequest.Builder()
    .addDeviceFilter(deviceFilter)
    .setSingleDevice(false)
    .build()

val companionDeviceManager = getSystemService(CompanionDeviceManager::class.java)
companionDeviceManager.associate(request, callback, handler)
```

### 8.2 CompanionDeviceService (Android 12+)

```kotlin
class PalmaMirrorCompanionService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        // Device came into BLE range -- connect
        Log.d(TAG, "Companion device appeared")
        startBleConnection(associationInfo)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        // Device left BLE range
        Log.d(TAG, "Companion device disappeared")
    }
}
```

**Manifest**:
```xml
<service
    android:name=".companion.PalmaMirrorCompanionService"
    android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.companion.CompanionDeviceService" />
    </intent-filter>
</service>
```

### 8.3 Benefits of CompanionDeviceManager

- **Automatic service binding**: `startObservingDevicePresence()` automatically binds/unbinds the CompanionDeviceService based on device presence
- **No location permission needed**: Companion device pairing bypasses location permission requirements
- **System-managed reconnection**: The system handles detecting when the device appears/disappears
- **Survives process death better**: The system itself monitors for the companion device

### 8.4 Limitations

- `CompanionDeviceService` is only available on **Android 12+** (API 31)
- Relies on system-level scanning, which may be slower than a dedicated foreground service
- Less control over connection parameters and timing
- **May depend on GMS** on some OEM implementations -- needs testing on Boox
- The original Palma (Android 11) cannot use CompanionDeviceService

**Recommendation**: For the Boox Palma 2 (Android 13), CompanionDeviceManager is a viable primary or supplementary strategy. For the original Palma (Android 11), stick with the foreground service approach.

---

## 9. Recommended Architecture for PalmaMirror

### 9.1 Layered Architecture

```
+--------------------------------------------------+
|               MainActivity                        |
|  (Permission requests, Settings UI, Service bind) |
+--------------------------------------------------+
                     |
                     | bind / startForegroundService
                     v
+--------------------------------------------------+
|          BleConnectionService                     |
|  (Foreground service, lifecycle management)       |
|  - Holds foreground notification                  |
|  - Manages WakeLocks during reconnection          |
|  - Receives boot/alarm intents                    |
+--------------------------------------------------+
                     |
                     | owns
                     v
+--------------------------------------------------+
|          BleConnectionManager                     |
|  (BLE connection state machine)                   |
|  - connectGatt / connect / disconnect / close     |
|  - Reconnection with exponential backoff          |
|  - Error handling and classification              |
+--------------------------------------------------+
                     |
                     | owns
                     v
+--------------------------------------------------+
|          GattOperationQueue                       |
|  (Thread-safe serial operation queue)             |
|  - Read / Write / Notify / MTU / RSSI            |
|  - Timeout handling per operation                 |
|  - Callback routing                               |
+--------------------------------------------------+
                     |
                     | delegates to
                     v
+--------------------------------------------------+
|          BluetoothGattCallback                    |
|  (Binder thread callbacks)                        |
|  - Connection state changes                       |
|  - Characteristic notifications                   |
|  - Operation completions -> queue.signalEnd()     |
+--------------------------------------------------+
```

### 9.2 Startup Sequence

```
1. Device boots
   -> BootCompletedReceiver fires
   -> Starts BleConnectionService as foreground service

2. BleConnectionService.onStartCommand()
   -> Creates notification channel + notification
   -> Calls startForeground()
   -> Reads target device from SharedPreferences
   -> Creates BleConnectionManager
   -> Initiates connection with autoConnect=true

3. Connection established
   -> onConnectionStateChange(CONNECTED)
   -> Request MTU (517)
   -> Discover services
   -> Enable notifications on target characteristics
   -> Update notification: "Connected to iPhone"

4. Connection lost
   -> onConnectionStateChange(DISCONNECTED, status)
   -> Classify error (see error table)
   -> If clean disconnect: gatt.connect() (auto-reconnect)
   -> If error 133: close + backoff + new connectGatt
   -> Update notification: "Reconnecting..."

5. Health check alarm fires (every 15 minutes)
   -> Verify service is running
   -> Verify GATT connection state
   -> If disconnected, trigger reconnection
   -> Reschedule next alarm
```

---

## 10. Testing and Debugging

### 10.1 ADB Commands

```bash
# Check if app is in battery optimization whitelist
adb shell dumpsys deviceidle whitelist

# Simulate Doze mode
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle

# Exit simulated Doze
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset

# Check Bluetooth state
adb shell dumpsys bluetooth_manager

# Monitor BLE logs
adb logcat -s BluetoothGatt:V BluetoothAdapter:V bt_btif:V

# Enable Bluetooth HCI snoop log (detailed packet-level debugging)
# Developer Options -> Enable Bluetooth HCI snoop log
# Then retrieve: adb pull /sdcard/btsnoop_hci.log

# Check foreground service state
adb shell dumpsys activity services | grep PalmaMirror
```

### 10.2 Common Pitfalls

1. **Not calling close()**: Leaks GATT clients; eventually prevents all connections
2. **Operating on wrong thread**: BluetoothGattCallback runs on Binder thread
3. **Not queuing operations**: Concurrent GATT ops silently fail
4. **Ignoring error 133**: Must close() and recreate GATT; cannot just retry
5. **Using autoConnect=false for reconnection**: Times out after 30s; use autoConnect=true
6. **Not requesting battery optimization exemption**: Process killed within minutes on Boox
7. **Notification updates too frequent**: Causes excessive e-ink refreshes on Boox
8. **Not handling Bluetooth adapter state changes**: Adapter can be toggled by user or system
9. **Hardcoding MAC addresses**: iPhone randomizes its BLE MAC address; use bonding to maintain identity

---

## 11. Key Libraries

### 11.1 BLESSED (Recommended)

[BLESSED](https://github.com/weliem/blessed-android) by Martijn van Welie -- wraps all the patterns described above:
- Automatic GATT operation queuing
- Automatic reconnection with autoConnect
- Proper error handling and resource cleanup
- Bonding support
- Coroutine-friendly API (blessed-android-coroutines)

```gradle
implementation 'com.github.weliem:blessed-android:2.4.0'
// or for coroutines
implementation 'com.github.weliem:blessed-android-coroutines:0.5.0'
```

### 11.2 Nordic Android BLE Library

[Nordic BLE Library](https://github.com/NordicSemiconductor/Android-BLE-Library) -- battle-tested, used in Nordic's nRF Connect app:
- Automatic GATT operation queuing
- Connection and bonding management
- Extensive logging
- Flow-based API

### 11.3 RxAndroidBle

[RxAndroidBle](https://github.com/dariuszseweryn/RxAndroidBle) -- RxJava-based BLE wrapper:
- Reactive API for BLE operations
- Built-in connection sharing
- Automatic resource management

**Recommendation for PalmaMirror**: Use **BLESSED** if building from scratch (simplest API, Kotlin-first). Use **Nordic BLE Library** if you need maximum control and debugging capability. Both handle the operation queue, reconnection, and error recovery patterns automatically.

---

## References

- [Android Developer: Communicate in Background (BLE)](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Android Developer: Bluetooth Permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android Developer: Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android Developer: Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Developer: Companion Device Pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Android Developer: Background FGS Start Restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Punch Through: Android BLE Ultimate Guide](https://punchthrough.com/android-ble-guide/)
- [Punch Through: Android BLE Operation Queue](https://punchthrough.com/android-ble-operation-queue/)
- [Punch Through: Mastering Android BLE Permissions](https://punchthrough.com/mastering-permissions-for-bluetooth-low-energy-android/)
- [Martijn van Welie: Making Android BLE Work (Part 1)](https://medium.com/@martijn.van.welie/making-android-ble-work-part-1-a736dcd53b02)
- [Martijn van Welie: Making Android BLE Work (Part 2)](https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07)
- [Martijn van Welie: Making Android BLE Work (Part 3)](https://medium.com/@martijn.van.welie/making-android-ble-work-part-3-117d3a8aee23)
- [Martijn van Welie: Making Android BLE Work (Part 4)](https://medium.com/@martijn.van.welie/making-android-ble-work-part-4-72a0b85cb442)
- [Don't Kill My App](https://dontkillmyapp.com/general)
- [Nordic Semiconductor: Android BLE Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- [BLESSED Android](https://github.com/weliem/blessed-android)
- [BLE Status Codes Reference](https://github.com/arstagaev/BLE-Status-Codes)
- [Boox Palma 2 Specifications](https://onyxboox.com/boox_palma2)
- [Boox Help: Google Play Store](https://help.boox.com/hc/en-us/articles/8569260963732-Google-Play-Store)
