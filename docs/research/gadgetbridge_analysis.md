# GadgetBridge ANCS & BLE Analysis for PalmaMirror

**Date**: 2026-03-01
**Source**: `https://codeberg.org/Freeyourgadget/Gadgetbridge.git` cloned to `/tmp/gadgetbridge`
**Purpose**: Extract Android-specific BLE patterns, ANCS implementation details, reconnection logic, and foreground service patterns for the PalmaMirror project (Android app mirroring iPhone notifications via BLE).

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [ANCS Implementation (Withings Steel HR)](#ancs-implementation-withings-steel-hr)
3. [BLE GATT Connection Architecture](#ble-gatt-connection-architecture)
4. [GATT Server Pattern (Critical for PalmaMirror)](#gatt-server-pattern-critical-for-palmamirror)
5. [Foreground Service Pattern](#foreground-service-pattern)
6. [Reconnection Logic](#reconnection-logic)
7. [Notification Parsing & Display](#notification-parsing--display)
8. [Key Lessons for PalmaMirror](#key-lessons-for-palmamirror)

---

## Executive Summary

GadgetBridge contains a **complete ANCS-like notification protocol implementation** in the Withings Steel HR device support module. This is the most relevant code for PalmaMirror because GadgetBridge acts as an **ANCS server** (the Android phone pretends to be an iPhone sending notifications to the watch), which is the **reverse** of what PalmaMirror needs but reveals the full protocol structure.

Key findings:
- GadgetBridge implements ANCS as a **GATT Server** hosted on the Android device, using `BluetoothGattServer` + `BluetoothGattServerCallback`
- The ANCS protocol consists of 3 characteristics: Notification Source (notify), Control Point (write), and Data Source (notify)
- Reconnection uses a multi-strategy approach: immediate BLE reconnect, timed exponential backoff, BLE scan-based reconnect, and Android CompanionDeviceService
- Foreground service uses `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on Android Q+
- All BLE operations go through a serialized transaction queue (`BtLEQueue`) to prevent concurrent GATT operations

---

## ANCS Implementation (Withings Steel HR)

### Overview

The Withings Steel HR module is the **only** place in GadgetBridge that implements an ANCS-like protocol. The watch itself expects to receive notifications via an ANCS-compatible service, so GadgetBridge creates a GATT server that mimics Apple's ANCS service.

### File Locations

| File | Purpose |
|------|---------|
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/WithingsSteelHRDeviceSupport.java` | Main device support - sets up ANCS GATT server service |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/WithingsUUID.java` | UUID constants for Withings ANCS-like service |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/AncsConstants.java` | ANCS event IDs, flags, and category IDs |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationSource.java` | ANCS Notification Source packet serialization |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationProvider.java` | Maps Android notifications to ANCS protocol |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/GetNotificationAttributes.java` | Parses ANCS GetNotificationAttributes requests |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/GetNotificationAttributesResponse.java` | Serializes ANCS attribute responses |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationAttribute.java` | Individual attribute serialization with UTF-8 |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/RequestedNotificationAttribute.java` | Deserializes attribute requests |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/datastructures/AncsStatus.java` | ANCS enable/disable toggle structure |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/datastructures/WithingsStructureType.java` | Structure type constants (ANCS_STATUS = 2346) |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/message/WithingsMessageType.java` | Message type constants (GET_ANCS_STATUS = 2353, SET_ANCS_STATUS = 2345) |

### ANCS Service UUIDs (Withings Variant)

From `WithingsUUID.java` (lines 28-31):

```java
// NOTE: These are Withings-proprietary UUIDs, NOT Apple's standard ANCS UUIDs.
// Apple's standard ANCS Service UUID is: 7905F431-B5CE-4E99-A40F-4B1E122D00D0
public static final UUID WITHINGS_ANCS_SERVICE_UUID = UUID.fromString("10000057-5749-5448-0037-000000000000");
public static final UUID NOTIFICATION_SOURCE_CHARACTERISTIC_UUID = UUID.fromString("10000059-5749-5448-0037-000000000000");
public static final UUID CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("10000058-5749-5448-0037-000000000000");
public static final UUID DATA_SOURCE_CHARACTERISTIC_UUID = UUID.fromString("1000005a-5749-5448-0037-000000000000");
public static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
```

**Important for PalmaMirror**: The Withings watch uses proprietary UUIDs that mirror ANCS's 3-characteristic structure. For PalmaMirror connecting to a real iPhone, use Apple's standard ANCS UUIDs:
- Service: `7905F431-B5CE-4E99-A40F-4B1E122D00D0`
- Notification Source: `9FBF120D-6301-42D9-8C58-25E699A21DBD`
- Control Point: `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9`
- Data Source: `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB`

### ANCS Protocol Constants

From `AncsConstants.java` (lines 21-42):

```java
// Event IDs
public static final byte EVENT_ID_NOTIFICATION_ADDED = 0;
public static final byte EVENT_ID_NOTIFICATION_MODIFIED = 1;
public static final byte EVENT_ID_NOTIFICATION_REMOVED = 2;

// Event Flags
public static final byte EVENT_FLAGS_SILENT = (1 << 0);
public static final byte EVENT_FLAGS_IMPORTANT = (1 << 1);
public static final byte EVENT_FLAGS_PREEXISTING = (1 << 2);
public static final byte EVENT_FLAGS_POSITIVE_ACTION = (1 << 3);
public static final byte EVENT_FLAGS_NEGATIVE_ACTION = (1 << 4);

// Category IDs
public static final byte CATEGORY_ID_OTHER = 0;
public static final byte CATEGORY_ID_INCOMING_CALL = 1;
public static final byte CATEGORY_ID_MISSED_CALL = 2;
public static final byte CATEGORY_ID_VOICEMAIL = 3;
public static final byte CATEGORY_ID_SOCIAL = 4;
public static final byte CATEGORY_ID_SCHEDULE = 5;
public static final byte CATEGORY_ID_EMAIL = 6;
public static final byte CATEGORY_ID_NEWS = 7;
public static final byte CATEGORY_ID_HEALTHANDFITNESS = 8;
public static final byte CATEGORY_ID_BUSINESSANDFINANCE = 9;
public static final byte CATEGORY_ID_LOCATION = 10;
public static final byte CATEGORY_ID_ENTERTAINMENT = 11;
```

### Notification Source Packet Format

From `NotificationSource.java` (lines 46-54):

```java
public byte[] serialize() {
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.put(eventID);        // 1 byte: Event ID (added/modified/removed)
    buffer.put(eventFlags);     // 1 byte: Event flags bitmask
    buffer.put(categoryId);     // 1 byte: Category ID
    buffer.put(categoryCount);  // 1 byte: Count of active notifications in category
    buffer.putInt(notificationUID); // 4 bytes: Unique notification ID
    return buffer.array();
}
```

**Total: 8 bytes per Notification Source packet.**

### ANCS Attribute Request/Response Flow

From `GetNotificationAttributes.java` (lines 55-71), the Control Point write request format:

```java
public void deserialize(byte[] rawData) {
    ByteBuffer buffer = ByteBuffer.wrap(rawData);
    commandID = buffer.get();           // 1 byte: Command ID (0 = GetNotificationAttributes)
    notificationUID = buffer.getInt();  // 4 bytes: Notification UID
    while (buffer.hasRemaining()) {
        // Each requested attribute: 1 byte ID + optional 2 byte max length
        RequestedNotificationAttribute attr = new RequestedNotificationAttribute();
        int length = 1;
        if (buffer.remaining() >= 3) {
            length = 3;
        }
        byte[] rawAttributeData = new byte[length];
        buffer.get(rawAttributeData);
        attr.deserialize(rawAttributeData);
        attributes.add(attr);
    }
}
```

From `GetNotificationAttributesResponse.java` (lines 37-47), the Data Source response format:

```java
public byte[] serialize() {
    ByteBuffer buffer = ByteBuffer.allocate(getLength());
    buffer.put(commandID);                    // 1 byte: Command ID (0)
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(notificationUID);           // 4 bytes: Notification UID (LITTLE ENDIAN!)
    buffer.order(ByteOrder.BIG_ENDIAN);
    for (NotificationAttribute attribute : attributes) {
        buffer.put(attribute.serialize());    // Each: 1 byte ID + 2 bytes length + N bytes value
    }
    return buffer.array();
}
```

**Critical byte order note**: The notification UID in responses uses **LITTLE_ENDIAN** byte order, while the attribute values use **BIG_ENDIAN**. This mixed endianness is part of the ANCS spec.

### Attribute ID Mapping

From `NotificationProvider.java` (lines 86-100):

```java
// Attribute ID 0 = App Identifier (bundle ID)
if (requestedAttribute.getAttributeID() == 0) {
    value = spec.sourceAppId;
}
// Attribute ID 1 = Title (sender name / phone number / source name)
if (requestedAttribute.getAttributeID() == 1) {
    value = spec.sender != null ? spec.sender :
            (spec.phoneNumber != null ? spec.phoneNumber :
            (spec.sourceName != null ? spec.sourceName : "Unknown"));
}
// Attribute ID 2 = Subtitle (title / subject)
if (requestedAttribute.getAttributeID() == 2) {
    value = spec.title != null ? spec.title : (spec.subject != null ? spec.subject : " ");
}
// Attribute ID 3 = Message (body)
if (requestedAttribute.getAttributeID() == 3) {
    value = (spec.body != null ? spec.body : " ");
}
```

### Notification Type to ANCS Category Mapping

From `NotificationProvider.java` (lines 134-181), comprehensive mapping of Android notification types to ANCS categories. Key mappings:

- Phone calls -> `CATEGORY_ID_INCOMING_CALL`
- SMS/messaging apps (WhatsApp, Telegram, Signal, etc.) -> `CATEGORY_ID_SOCIAL`
- Email apps -> `CATEGORY_ID_EMAIL`
- Calendar -> `CATEGORY_ID_SCHEDULE`
- Navigation -> `CATEGORY_ID_LOCATION`

---

## BLE GATT Connection Architecture

### Core Classes

| File | Lines | Purpose |
|------|-------|---------|
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/BtLEQueue.java` | 1157 | Central BLE queue - one per device. Manages GATT client + GATT server connections, transaction serialization |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/AbstractBTLESingleDeviceSupport.java` | 514 | Base class for single BLE device support - manages service discovery, characteristic caching |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/AbstractBTLEDeviceSupport.java` | 101 | Abstract base - defines `getServiceDiscoveryDelay()`, MTU calculation |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/GattCallback.java` | 101 | Interface mirroring `BluetoothGattCallback` but as an interface with boolean return values for event consumption |
| `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/NoThrowBluetoothGattCallback.java` | 243 | Wrapper that catches all exceptions from GATT callbacks to prevent thread death |

### Connection Flow

From `BtLEQueue.java` (lines 292-363):

```
1. connect() called
   -> Validates state (not already connecting, not disposed, threads alive)
   -> Calls connectImp()

2. connectImp()
   -> Posts 5-second GATT connect timeout handler
   -> Cancels any active Bluetooth discovery
   -> If device has server services: opens BluetoothGattServer, adds services
   -> Calls remoteDevice.connectGatt() with:
      - autoConnect = false (explicitly noted: "true doesn't really work")
      - transport = TRANSPORT_LE
      - On Oreo+: uses PHY_LE_CODED_MASK and dedicated receiver handler
      - Pre-Oreo: legacy 4-arg connectGatt

3. InternalGattCallback.onConnectionStateChange()
   -> STATE_CONNECTED:
      - Removes connect timeout handler
      - Sets device state to CONNECTED
      - Posts discoverServices() with configurable delay on MAIN THREAD
      - Delay varies by API level and bond state (300ms-1600ms)
   -> STATE_DISCONNECTED:
      - Calls handleDisconnected() with status code

4. onServicesDiscovered()
   -> Caches discovered characteristics filtered by supported service UUIDs
   -> Counts down mConnectionLatch to unblock dispatch thread
   -> Calls initializeDevice() on the device support class
```

### Key Pattern: connectGatt Parameters

From `BtLEQueue.java` (lines 353-361):

```java
// connectGatt with true doesn't really work ;( too often connection problems
if (GBApplication.isRunningOreoOrLater() && !connectionForceLegacyGatt) {
    mBluetoothGatt = remoteDevice.connectGatt(mContext, false,
            internalGattCallback, BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_CODED_MASK, mReceiverHandler);
} else {
    mBluetoothGatt = remoteDevice.connectGatt(mContext, false,
            internalGattCallback, BluetoothDevice.TRANSPORT_LE);
}
```

**Lesson**: Always use `autoConnect = false`. The GadgetBridge team found that `autoConnect = true` causes "too often connection problems." They handle reconnection themselves.

### Service Discovery Delay

From `AbstractBTLEDeviceSupport.java` (lines 73-81):

```java
public long getServiceDiscoveryDelay(boolean bonded) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return bonded ? 1600L : 300L;
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return 300L;
    } else {
        return bonded ? 1000L : 300L;
    }
}
```

**Lesson**: Service discovery needs a delay after connection, especially for bonded devices. Bonded devices need longer delays (1000-1600ms) because Android processes Service Changed Indications before services are stable. The Withings device support adds an additional **2-second** delay before even starting initialization (line 199 of `WithingsSteelHRDeviceSupport.java`).

### Serialized Transaction Queue

From `BtLEQueue.java` (lines 103-200), the dispatch thread processes one transaction at a time:

```
DispatchRunnable.run():
  while not disposed:
    1. Take transaction from blocking deque (blocks if empty)
    2. If not connected, wait on mConnectionLatch
    3. For each action in transaction:
       a. Set wait characteristic
       b. Create result latch
       c. Execute action (write/read/notify)
       d. If action expects result, await latch
       e. If aborted (disconnect), break
```

**Lesson**: Android BLE is inherently single-threaded for GATT operations. GadgetBridge enforces this with a `BlockingDeque<AbstractTransaction>` and `CountDownLatch` synchronization. Never issue concurrent GATT operations.

---

## GATT Server Pattern (Critical for PalmaMirror)

### How GadgetBridge Creates a GATT Server

The Withings module demonstrates how to set up a GATT server on Android. From `WithingsSteelHRDeviceSupport.java` (lines 559-569):

```java
private void addANCSService() {
    BluetoothGattService withingsGATTService = new BluetoothGattService(
        WithingsUUID.WITHINGS_ANCS_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY);

    // Notification Source: NOTIFY property, READ permission
    notificationSourceCharacteristic = new BluetoothGattCharacteristic(
        WithingsUUID.NOTIFICATION_SOURCE_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ);
    notificationSourceCharacteristic.addDescriptor(
        new BluetoothGattDescriptor(WithingsUUID.CCC_DESCRIPTOR_UUID,
            BluetoothGattCharacteristic.PERMISSION_WRITE));
    withingsGATTService.addCharacteristic(notificationSourceCharacteristic);

    // Control Point: WRITE property, WRITE permission
    withingsGATTService.addCharacteristic(new BluetoothGattCharacteristic(
        WithingsUUID.CONTROL_POINT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE,
        BluetoothGattCharacteristic.PERMISSION_WRITE));

    // Data Source: NOTIFY property, READ permission
    dataSourceCharacteristic = new BluetoothGattCharacteristic(
        WithingsUUID.DATA_SOURCE_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ);
    dataSourceCharacteristic.addDescriptor(
        new BluetoothGattDescriptor(WithingsUUID.CCC_DESCRIPTOR_UUID,
            BluetoothGattCharacteristic.PERMISSION_WRITE));
    withingsGATTService.addCharacteristic(dataSourceCharacteristic);

    addSupportedServerService(withingsGATTService);
}
```

### GATT Server Lifecycle

From `BtLEQueue.java` (lines 336-350), the GATT server is opened during connection:

```java
if (!mSupportedServerServices.isEmpty()) {
    BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothGattServer = bluetoothManager.openGattServer(mContext, internalGattServerCallback);
    if (mBluetoothGattServer == null) {
        LOG.error("Error opening Gatt Server");
        return false;
    }
    for (BluetoothGattService service : mSupportedServerServices) {
        mBluetoothGattServer.addService(service);
    }
}
```

And cleaned up on disconnect (lines 384-390):

```java
BluetoothGattServer gattServer = mBluetoothGattServer;
if (gattServer != null) {
    mBluetoothGattServer = null;
    LOG.info("disconnecting BluetoothGattServer");
    gattServer.clearServices();
    gattServer.close();
}
```

### GATT Server Notification Flow

From `WithingsSteelHRDeviceSupport.java` (lines 459-481):

```java
// Sending a Notification Source notification (step 1: alert the watch)
public void sendAncsNotificationSourceNotification(NotificationSource notificationSource) {
    ServerTransactionBuilder builder = performServer("notificationSourceNotification");
    byte[] data = notificationSource.serialize();
    builder.notifyCharacteristicChanged(device, notificationSourceCharacteristic, data);
    builder.queue(getQueue());
}

// Sending a Data Source response (step 2: provide notification details)
public void sendAncsDataSourceNotification(GetNotificationAttributesResponse response) {
    ServerTransactionBuilder builder = performServer("dataSourceNotification");
    byte[] data = response.serialize();
    builder.notifyCharacteristicChanged(device, dataSourceCharacteristic, data);
    builder.queue(getQueue());
}
```

### ANCS-like Notification Flow Summary

```
1. Android notification arrives
   -> NotificationProvider.notifyClient(NotificationSpec)
   -> Creates NotificationSource packet (8 bytes)
   -> Stores NotificationSpec in pendingNotifications map (keyed by UID)
   -> Sends Notification Source characteristic notification to device

2. Device requests details by writing to Control Point
   -> onCharacteristicWriteRequest() fires
   -> Deserializes GetNotificationAttributes (command ID + notification UID + requested attributes)
   -> Calls NotificationProvider.handleNotificationAttributeRequest()

3. NotificationProvider builds response
   -> Looks up pending notification by UID
   -> For each requested attribute, fills value from NotificationSpec
   -> Strips newlines/carriage returns (watch can't display them)
   -> Truncates to max length specified in request
   -> Serializes GetNotificationAttributesResponse
   -> Sends Data Source characteristic notification

4. Cleanup
   -> After title/subtitle/message attributes are sent, removes from pending map
```

**Important for PalmaMirror**: PalmaMirror is the **reverse** - it will be a GATT **client** subscribing to an iPhone's ANCS service. But this protocol structure shows exactly what data to expect from the iPhone's Notification Source and Data Source characteristics.

---

## Foreground Service Pattern

### Manifest Declaration

From `app/src/main/AndroidManifest.xml`:

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" tools:remove="android:maxSdkVersion"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Service declaration -->
<service
    android:name=".service.DeviceCommunicationService"
    android:foregroundServiceType="connectedDevice" />
```

### Foreground Service Startup

From `DeviceCommunicationService.java` (lines 553-575, 1322-1333):

```java
@Override
public void onCreate() {
    super.onCreate();
    mFactory = getDeviceSupportFactory();
    registerInternalReceivers();
    registerExternalReceivers();
    // ... preference setup ...
    startForeground();  // Called immediately in onCreate
}

private void startForeground() {
    GB.createNotificationChannels(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
               == PackageManager.PERMISSION_DENIED)
            return;

        ServiceCompat.startForeground(this, GB.NOTIFICATION_ID,
            GB.createNotification(getString(R.string.gadgetbridge_running), this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    } else {
        ServiceCompat.startForeground(this, GB.NOTIFICATION_ID,
            GB.createNotification(getString(R.string.gadgetbridge_running), this), 0);
    }
}
```

### Notification Channels

From `GB.java` (lines 76-98):

```java
public static final String NOTIFICATION_CHANNEL_ID = "gadgetbridge";
public static final String NOTIFICATION_CHANNEL_ID_CONNECTION_STATUS = "gadgetbridge connection status";
public static final int NOTIFICATION_ID = 1;
```

**Lesson for PalmaMirror**:
- Use `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` for BLE connections
- Call `startForeground()` immediately in `onCreate()` before any BLE operations
- On Android 14+ (UPSIDE_DOWN_CAKE), check `BLUETOOTH_SCAN` permission before starting foreground
- Use `ServiceCompat.startForeground()` for backward compatibility
- Create notification channels before starting foreground

---

## Reconnection Logic

GadgetBridge implements a **multi-layered** reconnection strategy.

### Layer 1: Immediate BLE Reconnect

From `BtLEQueue.java` (lines 443-457):

```java
// After disconnect, if not a fatal error:
if (mDeviceSupport.getAutoReconnect()) {
    if (mDeviceSupport.getScanReconnect()) {
        forceDisconnect = true;  // Will use scan-based reconnect instead
    } else {
        LOG.info("enabling automatic immediate BLE reconnection");
        mPauseTransaction = false;
        if (mBluetoothGatt.connect()) {  // Re-uses existing GATT object
            setDeviceConnectionState(State.CONNECTING);
        } else {
            forceDisconnect = true;
        }
    }
}
```

### Layer 2: Timed Exponential Backoff

From `AutoConnectIntervalReceiver.java` (lines 40-131):

```java
// Uses AlarmManager for timed reconnection attempts
private void scheduleReconnect() {
    scheduleReconnect(mDelay);
    // Exponential backoff with a limit of 64 seconds
    mBackingOff = true;
    mDelay = Math.min(mDelay * 2, 64000);
}

private void scheduleReconnect(int delay) {
    AlarmManager am = (AlarmManager) GBApplication.getContext().getSystemService(Context.ALARM_SERVICE);
    Intent intent = new Intent("GB_RECONNECT");
    intent.setPackage(BuildConfig.APPLICATION_ID);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(
        GBApplication.getContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
    am.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        Calendar.getInstance().getTimeInMillis() + delay,
        pendingIntent);
}
```

- Default initial delay: 2000ms (from `AbstractDeviceCoordinator.getReconnectionDelay()`)
- Exponential backoff: 2s -> 4s -> 8s -> 16s -> 32s -> 64s (capped)
- Uses `setAndAllowWhileIdle()` to fire during Doze mode
- Resets delay when all devices are initialized

### Layer 3: BLE Scan-Based Reconnect

From `BLEScanService.java` (lines 62-140):

- Runs as a separate foreground service
- Uses `BluetoothLeScanner` with `ScanCallback`
- Restarts scans every 5 minutes (`DELAY_SCAN_RESTART = 5 * 60 * 1000`)
- When device found, broadcasts `EVENT_DEVICE_FOUND` which triggers reconnect

### Layer 4: ACL Connect Receiver

From `BluetoothConnectReceiver.java` (lines 38-103):

```java
// Listens for BluetoothDevice.ACTION_ACL_CONNECTED system broadcasts
// When a known device is seen in WAITING_FOR_RECONNECT or WAITING_FOR_SCAN state:
if (state == WAITING_FOR_RECONNECT || state == WAITING_FOR_SCAN) {
    GBApplication.deviceService(gbDevice).connect();
}
```

### Layer 5: CompanionDeviceService (Android 12+)

From `GBCompanionDeviceService.java` (lines 47-129):

```java
@RequiresApi(Build.VERSION_CODES.S)
public class GBCompanionDeviceService extends CompanionDeviceService {
    // System binds this service, elevating process priority
    // Android documentation:
    // "The system binding CompanionDeviceService elevates the priority of the process
    //  that the service is running in, and thus may prevent the Low-memory killer
    //  from killing the process"

    @Override
    public void onDeviceAppeared(@NonNull String address) {
        BluetoothConnectReceiver.observedDevice(address);
    }
}
```

### Device States for Reconnection

```
NOT_CONNECTED -> CONNECTING -> CONNECTED -> INITIALIZING -> INITIALIZED
                                    |
                                    v (on disconnect)
                        WAITING_FOR_RECONNECT (timed backoff)
                        WAITING_FOR_SCAN (scan-based reconnect)
```

### Error Handling

From `BtLEQueue.java` (lines 415-431), certain GATT errors force full disconnect (no reconnect attempt):

```java
switch(status) {
    case 0x81: // GATT_INTERNAL_ERROR (129)
    case 0x85: // GATT_ERROR (133)
        // Bluetooth stack has a fundamental problem
    case 0x8:  // GATT_INSUFFICIENT_AUTHORIZATION (API 35)
    case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
    case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
        // Bluetooth bonding/pairing issue
    case 0x93: // GATT_CONNECTION_TIMEOUT (API 35)
        forceDisconnect = true;
        break;
    default:
        forceDisconnect = false;
}
```

---

## Notification Parsing & Display

### NotificationSpec Model

GadgetBridge uses a `NotificationSpec` model that carries:
- `sourceAppId` - Android package name / app identifier
- `sender` - Sender name
- `phoneNumber` - Phone number (for calls/SMS)
- `sourceName` - App display name
- `title` - Notification title
- `subject` - Email subject
- `body` - Notification body text
- `type` - `NotificationType` enum (GENERIC_SMS, WHATSAPP, GMAIL, etc.)

### Text Sanitization

From `NotificationProvider.java` (lines 103-105):

```java
// Remove linefeed and carriage returns as the watch cannot display this
value = value.replace("\n", " ");
value = value.replace("\r", " ");
```

### Length Truncation

From `NotificationProvider.java` (lines 106-109):

```java
if (requestedAttribute.getAttributeMaxLength() == 0
    || requestedAttribute.getAttributeMaxLength() >= value.length()) {
    attribute.setValue(value);
} else {
    attribute.setValue(value.substring(0, requestedAttribute.getAttributeMaxLength()));
}
```

---

## Key Lessons for PalmaMirror

### 1. PalmaMirror is the ANCS Client (Reverse of GadgetBridge)

GadgetBridge acts as an ANCS **server** (pretending to be an iPhone). PalmaMirror needs to be an ANCS **client** (subscribing to an iPhone's ANCS service). The protocol is the same, but the roles are reversed:

| GadgetBridge (Server) | PalmaMirror (Client) |
|----------------------|---------------------|
| Creates GATT server with ANCS service | Discovers ANCS service on iPhone |
| Sends Notification Source notifications | Subscribes to Notification Source characteristic |
| Receives Control Point writes | Writes to Control Point characteristic |
| Sends Data Source notifications | Subscribes to Data Source characteristic |

### 2. GATT Client Connection Pattern

```java
// From BtLEQueue - proven patterns:
// 1. NEVER use autoConnect = true
remoteDevice.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);

// 2. Cancel discovery before connecting
mBluetoothAdapter.cancelDiscovery();

// 3. Add service discovery delay (especially for bonded devices)
// 300ms-1600ms depending on API level and bond state

// 4. Set connect timeout (5 seconds)
mGattConnectTimeoutHandler.postDelayed(() -> {
    handleDisconnected(GATT_CONNECTION_TIMEOUT);
}, 5000L);

// 5. Service discovery on main thread (fixes Samsung issues)
new Handler(Looper.getMainLooper()).postDelayed(() -> {
    gatt.discoverServices();
}, delayMillis);
```

### 3. Exception Safety in GATT Callbacks

From `NoThrowBluetoothGattCallback.java` - wrap ALL callback methods:

```java
@Override
public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
    try {
        Delegate.onConnectionStateChange(gatt, status, newState);
    } catch (Exception ex) {
        LOG.error("onConnectionStateChange", ex);
    } catch (Throwable t) {
        LOG.error("onConnectionStateChange", t);
        throw t;  // Re-throw Errors (OutOfMemoryError etc.)
    }
}
```

**Lesson**: An unhandled exception in a GATT callback will kill the BLE thread. Always catch `Exception` (but let `Throwable` like `Error` propagate after logging).

### 4. Serialized BLE Operations

Never perform concurrent GATT operations. Use a queue:

```
Transaction Queue (BlockingDeque)
  -> Transaction 1: [Action1, Action2, Action3]
  -> Transaction 2: [Action1, Action2]

Each action waits for its result via CountDownLatch before the next runs.
```

### 5. Foreground Service Requirements

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<service
    android:name=".service.BleConnectionService"
    android:foregroundServiceType="connectedDevice" />
```

### 6. Reconnection Strategy for PalmaMirror

Recommended multi-layer approach (adapted from GadgetBridge):

1. **Immediate**: On disconnect, try `gatt.connect()` if GATT object still valid
2. **Timed backoff**: AlarmManager with `setAndAllowWhileIdle()`, exponential backoff 2s -> 64s
3. **ACL listener**: Register for `BluetoothDevice.ACTION_ACL_CONNECTED` broadcasts
4. **CompanionDeviceService** (Android 12+): Register iPhone as companion device for system-level presence monitoring and elevated process priority
5. **BLE scan**: Periodic low-power BLE scans as last resort

### 7. MTU Negotiation

From `WithingsSteelHRDeviceSupport.java` (line 208):

```java
builder.requestMtu(512);  // Request maximum MTU
```

From `AbstractBTLEDeviceSupport.java` (lines 92-100):

```java
// Maximum payload per write = MTU - 3 (ATT overhead)
// Minimum MTU = 23 -> minimum payload = 20
// Maximum ATT payload = 512
public static int calcMaxWriteChunk(int mtu) {
    int safeMtu = Math.max(23, mtu);
    return Math.min(512, safeMtu - 3);
}
```

### 8. ANCS-Specific Gotchas for PalmaMirror

1. **Pairing required**: iPhone only exposes ANCS to bonded (paired) devices. Must pair at the BLE level first.
2. **Mixed endianness**: Notification UID is LITTLE_ENDIAN in Data Source responses, rest is BIG_ENDIAN.
3. **Notification Source is 8 bytes**: Fixed format - EventID(1) + Flags(1) + CategoryID(1) + CategoryCount(1) + NotificationUID(4).
4. **GetNotificationAttributes**: Write to Control Point with CommandID(1) + NotificationUID(4) + AttributeIDs with max lengths.
5. **Attribute IDs**: 0=AppIdentifier, 1=Title, 2=Subtitle, 3=Message, 4=MessageSize, 5=Date, 6=PositiveActionLabel, 7=NegativeActionLabel.
6. **Text cleanup**: Strip `\n` and `\r` before displaying on e-ink/limited displays.
7. **Pending notification map**: Keep a map of UID -> notification data to handle the async request/response flow.

### 9. Android Permission Model

From the manifest, the full permission set needed:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />  <!-- For reconnection alarms -->
```

---

## Architecture Diagram

```
PalmaMirror (Android)                              iPhone
+---------------------------+                 +------------------+
| ForegroundService         |                 | ANCS Service     |
|   (connectedDevice type)  |                 |   7905F431...    |
|                           |                 |                  |
| +-----BtLEQueue--------+ |  BLE GATT       | +Notification    |
| | Transaction Queue     | | <=============> | | Source (notify) |
| | (BlockingDeque)       | |  Client/Server  | +Control Point   |
| |                       | |                 | | (write)         |
| | InternalGattCallback  | |  Subscribe to   | +Data Source     |
| |   onCharacteristicChg | | <-- NS + DS     | | (notify)       |
| |   onConnectionStatChg | |                 | +                |
| |                       | |  Write to       |                  |
| | NoThrowCallback wrap  | | --> CP          +------------------+
| +-----------------------+ |
|                           |
| Reconnection Layers:      |
|  1. Immediate gatt.connect |
|  2. AlarmManager backoff   |
|  3. ACL_CONNECTED receiver |
|  4. CompanionDeviceService |
|  5. BLE scan service       |
+---------------------------+
```

---

## File Index

All paths relative to `/tmp/gadgetbridge/`:

### ANCS/Notification Protocol
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/AncsConstants.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationSource.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationProvider.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/GetNotificationAttributes.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/GetNotificationAttributesResponse.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/NotificationAttribute.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/notification/RequestedNotificationAttribute.java`

### Device Support
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/WithingsSteelHRDeviceSupport.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/WithingsUUID.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/datastructures/AncsStatus.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/datastructures/WithingsStructureType.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/withingssteelhr/communication/message/WithingsMessageType.java`

### BLE Infrastructure
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/BtLEQueue.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/AbstractBTLESingleDeviceSupport.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/AbstractBTLEDeviceSupport.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/GattCallback.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/NoThrowBluetoothGattCallback.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/ServerTransactionBuilder.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/btle/BLEScanService.java`

### Reconnection
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/receivers/AutoConnectIntervalReceiver.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/externalevents/BluetoothConnectReceiver.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/GBCompanionDeviceService.java`

### Foreground Service
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/DeviceCommunicationService.java`
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/util/GB.java`
- `app/src/main/AndroidManifest.xml`

### Pebble BLE (Alternative GATT Client Pattern)
- `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/pebble/ble/PebbleGATTClient.java`
