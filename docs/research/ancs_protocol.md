# ANCS Protocol Research: InfiniTime Implementation Analysis

## Purpose

This document provides a detailed analysis of how InfiniTime handles BLE notifications, specifically examining its Alert Notification Service (ANS) implementation. This research informs the PalmaMirror project -- an Android app that mirrors iPhone notifications on a Boox Palma 2 e-ink device via BLE.

**Critical Finding**: InfiniTime does NOT implement Apple's ANCS (Apple Notification Center Service). It implements the standard Bluetooth **Alert Notification Service (ANS)** -- UUID `0x1811` -- which is a completely different protocol. The Nordic SDK's ANCS client (`BLE_ANCS_C_ENABLED`) is explicitly disabled in `sdk_config.h` (line 1298: `#define BLE_ANCS_C_ENABLED 0`). This has major implications for PalmaMirror's design.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [ANS vs ANCS: Critical Distinction](#2-ans-vs-ancs-critical-distinction)
3. [Service Discovery Flow](#3-service-discovery-flow)
4. [Notification Subscription](#4-notification-subscription)
5. [Notification Data Format](#5-notification-data-format)
6. [Notification Storage and Management](#6-notification-storage-and-management)
7. [Call Notification Actions](#7-call-notification-actions)
8. [Full Connection Lifecycle](#8-full-connection-lifecycle)
9. [Key Code References](#9-key-code-references)
10. [Implications for PalmaMirror](#10-implications-for-palmamirror)
11. [Apple ANCS Protocol Specification](#11-apple-ancs-protocol-specification)

---

## 1. Architecture Overview

InfiniTime's notification system is built on NimBLE (Apache's BLE stack for nRF52) and follows a layered architecture:

```
+---------------------------------------------------+
|  UI Layer: Notifications Screen                    |
|  src/displayapp/screens/Notifications.cpp          |
+---------------------------------------------------+
|  Storage: NotificationManager (circular buffer)    |
|  src/components/ble/NotificationManager.cpp        |
+---------------------------------------------------+
|  BLE Services:                                     |
|  - AlertNotificationService (GATT server, write)   |
|  - AlertNotificationClient (GATT client, discover) |
|  - ImmediateAlertService   (GATT server, write)    |
+---------------------------------------------------+
|  Controller: NimbleController                      |
|  src/components/ble/NimbleController.cpp           |
+---------------------------------------------------+
|  Discovery: ServiceDiscovery                       |
|  src/components/ble/ServiceDiscovery.cpp           |
+---------------------------------------------------+
|  BLE Stack: Apache NimBLE                          |
+---------------------------------------------------+
```

### Key Classes

| Class | Role | File |
|-------|------|------|
| `NimbleController` | Central BLE orchestrator; handles GAP events, starts discovery | `src/components/ble/NimbleController.cpp` |
| `ServiceDiscovery` | Iterates through BleClient instances to discover remote services | `src/components/ble/ServiceDiscovery.cpp` |
| `AlertNotificationClient` | GATT client that discovers ANS on the companion and subscribes to notifications | `src/components/ble/AlertNotificationClient.cpp` |
| `AlertNotificationService` | GATT server that exposes the ANS write characteristic for companion apps to push notifications | `src/components/ble/AlertNotificationService.cpp` |
| `NotificationManager` | Circular buffer (5 slots) storing notifications for the UI | `src/components/ble/NotificationManager.cpp` |
| `ImmediateAlertService` | GATT server for find-my-phone alerts | `src/components/ble/ImmediateAlertService.cpp` |

---

## 2. ANS vs ANCS: Critical Distinction

### What InfiniTime Implements: ANS (Alert Notification Service)

- **UUID**: `0x1811` (standard 16-bit Bluetooth SIG UUID)
- **Protocol**: Standard BLE Alert Notification Service
- **Characteristics**:
  - `0x2A47` - Supported New Alert Category
  - `0x2A48` - Supported Unread Alert Category
  - `0x2A46` - New Alert (notifications are sent here)
  - `0x2A45` - Unread Alert Status
  - `0x2A44` - Alert Notification Control Point

### What InfiniTime Does NOT Implement: ANCS (Apple Notification Center Service)

- **UUID**: `7905F431-B5CE-4E99-A40F-4B1E122D00D0` (128-bit Apple proprietary)
- **Protocol**: Apple's proprietary notification mirroring protocol
- **Characteristics**:
  - Notification Source (NS): `9FBF120D-6301-42D9-8C58-25E699A21DBD`
  - Control Point (CP): `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9`
  - Data Source (DS): `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB`

### Why This Matters for PalmaMirror

InfiniTime relies on a **companion app** (Gadgetbridge, ITD, etc.) running on the phone to:
1. Intercept phone notifications via the phone OS's notification API
2. Translate them into ANS format
3. Push them to the watch via BLE GATT write

This is fundamentally different from ANCS, where:
1. The iOS device itself exposes ANCS as a GATT service
2. The peripheral (watch) discovers and subscribes to ANCS
3. iOS pushes raw notification events directly via BLE
4. The peripheral requests notification details via the Control Point

**For PalmaMirror talking to an iPhone, we MUST implement true ANCS client behavior, not ANS.**

---

## 3. Service Discovery Flow

### Discovery Trigger

Discovery is deferred after BLE connection to avoid timing conflicts.

**File**: `src/systemtask/SystemTask.cpp`, lines 238-242

```cpp
case Messages::BleConnected:
    displayApp.PushMessage(Pinetime::Applications::Display::Messages::NotifyDeviceActivity);
    isBleDiscoveryTimerRunning = true;
    bleDiscoveryTimer = 5;  // ~5 second delay
    break;
```

The timer ticks down in the main loop (line 383-391):

```cpp
if (isBleDiscoveryTimerRunning) {
    if (bleDiscoveryTimer == 0) {
        isBleDiscoveryTimerRunning = false;
        nimbleController.StartDiscovery();
    } else {
        bleDiscoveryTimer--;
    }
}
```

### Discovery Chain

**File**: `src/components/ble/NimbleController.cpp`, line 52

The `ServiceDiscovery` object is initialized with two clients in order:

```cpp
serviceDiscovery({&currentTimeClient, &alertNotificationClient})
```

**File**: `src/components/ble/ServiceDiscovery.cpp`, lines 10-31

Discovery runs sequentially through each BleClient via a callback chain:

```cpp
void ServiceDiscovery::StartDiscovery(uint16_t connectionHandle) {
    clientIterator = clients.begin();
    DiscoverNextService(connectionHandle);
}

void ServiceDiscovery::DiscoverNextService(uint16_t connectionHandle) {
    auto discoverNextService = [this](uint16_t connectionHandle) {
        this->OnServiceDiscovered(connectionHandle);
    };
    (*clientIterator)->Discover(connectionHandle, discoverNextService);
}

void ServiceDiscovery::OnServiceDiscovered(uint16_t connectionHandle) {
    clientIterator++;
    if (clientIterator != clients.end()) {
        DiscoverNextService(connectionHandle);
    }
}
```

Each client's `Discover()` method receives a lambda callback. When discovery completes (success or failure), it calls the lambda, which triggers discovery of the next service.

### ANS Service Discovery

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 185-189

```cpp
void AlertNotificationClient::Discover(uint16_t connectionHandle,
                                        std::function<void(uint16_t)> onServiceDiscovered) {
    this->onServiceDiscovered = onServiceDiscovered;
    ble_gattc_disc_svc_by_uuid(connectionHandle, &ansServiceUuid.u, OnDiscoveryEventCallback, this);
}
```

This uses NimBLE's `ble_gattc_disc_svc_by_uuid()` to find service UUID `0x1811` on the remote device.

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 49-68

```cpp
bool AlertNotificationClient::OnDiscoveryEvent(uint16_t connectionHandle,
                                                const ble_gatt_error* error,
                                                const ble_gatt_svc* service) {
    if (service == nullptr && error->status == BLE_HS_EDONE) {
        if (isDiscovered) {
            // Service found, proceed to characteristic discovery
            ble_gattc_disc_all_chrs(connectionHandle, ansStartHandle, ansEndHandle,
                                    OnAlertNotificationCharacteristicDiscoveredCallback, this);
        } else {
            // Service not found, move to next service
            onServiceDiscovered(connectionHandle);
        }
        return true;
    }

    if (service != nullptr && ble_uuid_cmp(&ansServiceUuid.u, &service->uuid.u) == 0) {
        ansStartHandle = service->start_handle;
        ansEndHandle = service->end_handle;
        isDiscovered = true;
    }
    return false;
}
```

Key pattern: NimBLE calls the discovery callback once per found service, then once more with `service == nullptr` and `status == BLE_HS_EDONE` to signal completion.

---

## 4. Notification Subscription

### Characteristic Discovery

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 71-108

After the service is found, all characteristics within the handle range are discovered:

```cpp
int AlertNotificationClient::OnCharacteristicsDiscoveryEvent(
    uint16_t connectionHandle, const ble_gatt_error* error,
    const ble_gatt_chr* characteristic)
```

The following characteristics are identified by UUID comparison:

| UUID | Handle Variable | Purpose |
|------|-----------------|---------|
| `0x2A47` | `supportedNewAlertCategoryHandle` | Supported New Alert Category |
| `0x2A48` | `supportedUnreadAlertCategoryHandle` | Supported Unread Alert Category |
| `0x2A46` | `newAlertHandle` | **New Alert** (notification data arrives here) |
| `0x2A45` | `unreadAlertStatusHandle` | Unread Alert Status |
| `0x2A44` | `controlPointHandle` | Alert Notification Control Point |

The critical characteristic is `newAlertHandle` (`0x2A46`). When discovered, the code also stores `newAlertDefHandle` (the definition handle) and sets `isCharacteristicDiscovered = true`.

### Descriptor Discovery and Subscription

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 80-83

After characteristics are found, descriptors are discovered for the New Alert characteristic:

```cpp
if (isCharacteristicDiscovered) {
    ble_gattc_disc_all_dscs(connectionHandle, newAlertHandle, ansEndHandle,
                            OnAlertNotificationDescriptorDiscoveryEventCallback, this);
}
```

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 121-142

The CCCD (Client Characteristic Configuration Descriptor) is found and written to enable notifications:

```cpp
int AlertNotificationClient::OnDescriptorDiscoveryEventCallback(
    uint16_t connectionHandle, const ble_gatt_error* error,
    uint16_t characteristicValueHandle, const ble_gatt_dsc* descriptor)
{
    if (error->status == 0) {
        if (characteristicValueHandle == newAlertHandle &&
            ble_uuid_cmp(&newAlertUuid.u, &descriptor->uuid.u)) {
            if (newAlertDescriptorHandle == 0) {
                newAlertDescriptorHandle = descriptor->handle;
                isDescriptorFound = true;
                uint8_t value[2];
                value[0] = 1;   // Enable notifications
                value[1] = 0;
                ble_gattc_write_flat(connectionHandle, newAlertDescriptorHandle,
                                     value, sizeof(value), NewAlertSubcribeCallback, this);
            }
        }
    }
}
```

The subscription writes `[0x01, 0x00]` to the CCCD, which is the standard BLE way to enable notifications (bit 0 = notifications, bit 1 = indications).

---

## 5. Notification Data Format

### ANS New Alert Format (What InfiniTime Actually Receives)

**File**: `src/components/ble/AlertNotificationClient.cpp`, lines 144-168

```cpp
void AlertNotificationClient::OnNotification(ble_gap_event* event) {
    if (event->notify_rx.attr_handle == newAlertHandle) {
        constexpr size_t stringTerminatorSize = 1;
        constexpr size_t headerSize = 3;
        const auto maxMessageSize {NotificationManager::MaximumMessageSize()};  // 100 bytes
        const auto maxBufferSize {maxMessageSize + headerSize};

        const auto packetLen = OS_MBUF_PKTLEN(event->notify_rx.om);
        if (packetLen <= headerSize)
            return;  // Ignore empty notifications

        size_t bufferSize = std::min(packetLen + stringTerminatorSize, maxBufferSize);
        auto messageSize = std::min(maxMessageSize, (bufferSize - headerSize));

        NotificationManager::Notification notif;
        os_mbuf_copydata(event->notify_rx.om, headerSize, messageSize - 1, notif.message.data());
        notif.message[messageSize - 1] = '\0';
        notif.size = messageSize;
        notif.category = NotificationManager::Categories::SimpleAlert;
        notificationManager.Push(std::move(notif));

        systemTask.PushMessage(Pinetime::System::Messages::OnNewNotification);
    }
}
```

### ANS Byte Structure

```
Offset  Size    Field
------  ----    -----
0       1       Category ID (uint8)
1       1       Number of new alerts (uint8)
2       1       Null separator (0x00)
3+      var     Text info (UTF-8, null-separated title\0body)
```

Categories (from `AlertNotificationService.h`, lines 37-49):

```
0x00 = SimpleAlert
0x01 = Email
0x02 = News
0x03 = Call (IncomingCall)
0x04 = MissedCall
0x05 = MMS/SMS
0x06 = VoiceMail
0x07 = Schedule
0x08 = HighPrioritizedAlert
0x09 = InstantMessage
0xFF = All Alerts
```

### ANS Write Format (AlertNotificationService -- GATT Server Side)

**File**: `src/components/ble/AlertNotificationService.cpp`, lines 47-85

The companion app writes to the `0x2A46` characteristic hosted by InfiniTime. The parsing is the same 3-byte header:

```cpp
int AlertNotificationService::OnAlert(struct ble_gatt_access_ctxt* ctxt) {
    if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
        const auto packetLen = OS_MBUF_PKTLEN(ctxt->om);
        if (packetLen <= headerSize) return 0;

        // ... size calculations ...

        NotificationManager::Notification notif;
        os_mbuf_copydata(ctxt->om, headerSize, messageSize - 1, notif.message.data());
        os_mbuf_copydata(ctxt->om, 0, 1, &category);  // Read category byte
        notif.message[messageSize - 1] = '\0';
        notif.size = messageSize;

        switch (category) {
            case Categories::Call:
                notif.category = NotificationManager::Categories::IncomingCall;
                break;
            default:
                notif.category = NotificationManager::Categories::SimpleAlert;
                break;
        }

        notificationManager.Push(std::move(notif));
        systemTask.PushMessage(Pinetime::System::Messages::OnNewNotification);
    }
    return 0;
}
```

### Title/Body Parsing

**File**: `src/components/ble/NotificationManager.cpp`, lines 135-150

The message buffer uses a null byte (`\0`) as a separator between title and body:

```cpp
const char* NotificationManager::Notification::Message() const {
    const char* itField = std::find(message.begin(), message.begin() + size - 1, '\0');
    if (itField != message.begin() + size - 1) {
        const char* ptr = (itField) + 1;
        return ptr;  // Return everything after the first null = body
    }
    return const_cast<char*>(message.data());  // No null found = entire string is body
}

const char* NotificationManager::Notification::Title() const {
    const char* itField = std::find(message.begin(), message.begin() + size - 1, '\0');
    if (itField != message.begin() + size - 1) {
        return message.data();  // Return up to the first null = title
    }
    return {};  // No title
}
```

So a notification like `"Test Title\0Test Body"` yields:
- `Title()` returns `"Test Title"`
- `Message()` returns `"Test Body"`

---

## 6. Notification Storage and Management

**File**: `src/components/ble/NotificationManager.h`, lines 10-77

### Notification Structure

```cpp
struct Notification {
    using Id = uint8_t;
    using Idx = uint8_t;

    std::array<char, MessageSize + 1> message{};  // MessageSize = 100
    uint8_t size;
    Categories category = Categories::Unknown;
    Id id = 0;
    bool valid = false;
};
```

### Circular Buffer

- **Capacity**: 5 notifications (`TotalNbNotifications = 5`)
- **Max message size**: 100 bytes (`MessageSize = 100`)
- **Implementation**: Ring buffer indexed by `beginIdx` (newest) with `size` tracking valid entries

**File**: `src/components/ble/NotificationManager.cpp`, lines 10-23

```cpp
void NotificationManager::Push(NotificationManager::Notification&& notif) {
    notif.id = GetNextId();   // Sequential uint8_t
    notif.valid = true;
    newNotification = true;   // Atomic flag
    if (beginIdx > 0) {
        --beginIdx;
    } else {
        beginIdx = notifications.size() - 1;
    }
    notifications[beginIdx] = std::move(notif);
    if (size < notifications.size()) {
        size++;
    }
}
```

---

## 7. Call Notification Actions

InfiniTime supports three actions for incoming call notifications: Accept, Reject, and Mute.

### Custom Notification Event Characteristic

**File**: `src/components/ble/AlertNotificationService.h`, lines 11-12

```cpp
#define NOTIFICATION_EVENT_SERVICE_UUID_BASE \
  { 0xd0, 0x42, 0x19, 0x3a, 0x3b, 0x43, 0x23, 0x8e, 0xfe, 0x48, 0xfc, 0x78, 0x01, 0x00, 0x02, 0x00 }
```

This translates to UUID: `00020001-78fc-48fe-8e23-433b3a1942d0`

This is a custom characteristic added to the ANS service (`0x1811`) that supports NOTIFY only. The companion app subscribes to this characteristic to receive call action responses.

### Action Response Values

**File**: `src/components/ble/AlertNotificationService.h`, line 34

```cpp
enum class IncomingCallResponses : uint8_t {
    Reject = 0x00,
    Answer = 0x01,
    Mute = 0x02
};
```

### Action Sending

**File**: `src/components/ble/AlertNotificationService.cpp`, lines 87-124

```cpp
void AlertNotificationService::AcceptIncomingCall() {
    auto response = IncomingCallResponses::Answer;
    auto* om = ble_hs_mbuf_from_flat(&response, 1);  // Single byte
    uint16_t connectionHandle = systemTask.nimble().connHandle();
    if (connectionHandle == 0 || connectionHandle == BLE_HS_CONN_HANDLE_NONE) return;
    ble_gattc_notify_custom(connectionHandle, eventHandle, om);
}
```

The pattern is the same for Reject and Mute -- a single byte sent as a GATT notification.

### UI Buttons

**File**: `src/displayapp/screens/Notifications.cpp`, lines 354-369

```cpp
void Notifications::NotificationItem::OnCallButtonEvent(lv_obj_t* obj, lv_event_t event) {
    if (event != LV_EVENT_CLICKED) return;
    motorController.StopRinging();
    if (obj == bt_accept)      alertNotificationService.AcceptIncomingCall();
    else if (obj == bt_reject) alertNotificationService.RejectIncomingCall();
    else if (obj == bt_mute)   alertNotificationService.MuteIncomingCall();
    running = false;
}
```

---

## 8. Full Connection Lifecycle

```
1. InfiniTime advertises (BLE_GAP_CONN_MODE_UND, BLE_GAP_DISC_MODE_GEN)
   - Advertises HR service UUID and DFU service UUID
   - Fast advertising (32-47 intervals) for 30 seconds, then slow (1636-1651)

2. Companion app connects
   - BLE_GAP_EVENT_CONNECT fires
   - connectionHandle stored
   - BleConnected message pushed to SystemTask

3. SystemTask defers discovery for ~5 seconds
   - Avoids BLE timing conflicts

4. NimbleController.StartDiscovery() called
   - ServiceDiscovery iterates through clients:
     a. CurrentTimeClient.Discover() -- discovers CTS (0x1805)
        - Reads current time
        - Calls completion lambda
     b. AlertNotificationClient.Discover() -- discovers ANS (0x1811)
        - Discovers characteristics (0x2A46, 0x2A47, etc.)
        - Discovers CCCD descriptor for New Alert
        - Writes [0x01, 0x00] to CCCD to enable notifications
        - Calls completion lambda

5. Notifications flow:
   Path A (ANS Client): Companion's ANS server notifies on 0x2A46
     -> BLE_GAP_EVENT_NOTIFY_RX in NimbleController
     -> alertNotificationClient.OnNotification(event)
     -> notificationManager.Push()
     -> systemTask.PushMessage(OnNewNotification)

   Path B (ANS Server): Companion writes to InfiniTime's 0x2A46
     -> AlertNotificationService.OnAlert()
     -> notificationManager.Push()
     -> systemTask.PushMessage(OnNewNotification)

6. UI displays notification:
   -> DisplayApp receives NewNotification message
   -> Notifications screen created
   -> Reads from NotificationManager
```

---

## 9. Key Code References

| File | Lines | Description |
|------|-------|-------------|
| `src/components/ble/NimbleController.cpp` | 25-53 | Constructor, service initialization order |
| `src/components/ble/NimbleController.cpp` | 184-388 | GAP event handler (connect, disconnect, notify_rx) |
| `src/components/ble/NimbleController.cpp` | 359-373 | NOTIFY_RX routes to alertNotificationClient |
| `src/components/ble/NimbleController.cpp` | 390-394 | StartDiscovery() |
| `src/components/ble/ServiceDiscovery.cpp` | 1-32 | Full discovery chain logic |
| `src/components/ble/AlertNotificationClient.h` | 38-51 | ANS UUID constants (0x1811, 0x2A46, etc.) |
| `src/components/ble/AlertNotificationClient.cpp` | 49-68 | Service discovery callback |
| `src/components/ble/AlertNotificationClient.cpp` | 71-108 | Characteristic discovery |
| `src/components/ble/AlertNotificationClient.cpp` | 121-142 | Descriptor discovery and CCCD subscription |
| `src/components/ble/AlertNotificationClient.cpp` | 144-168 | Notification parsing (3-byte header + message) |
| `src/components/ble/AlertNotificationClient.cpp` | 170-188 | Reset and Discover methods |
| `src/components/ble/AlertNotificationService.h` | 11-12 | Custom notification event UUID |
| `src/components/ble/AlertNotificationService.h` | 34 | Call response enum (Reject/Answer/Mute) |
| `src/components/ble/AlertNotificationService.h` | 37-49 | ANS category enum |
| `src/components/ble/AlertNotificationService.cpp` | 19-45 | GATT server init, characteristic definitions |
| `src/components/ble/AlertNotificationService.cpp` | 47-85 | OnAlert write handler (parses notifications) |
| `src/components/ble/AlertNotificationService.cpp` | 87-124 | Call action response methods |
| `src/components/ble/NotificationManager.h` | 25-27 | Notification struct (100-byte message buffer) |
| `src/components/ble/NotificationManager.cpp` | 10-23 | Push() -- circular buffer insertion |
| `src/components/ble/NotificationManager.cpp` | 135-150 | Title/Message parsing (null-separated) |
| `src/components/ble/BleClient.h` | 1-12 | BleClient interface (Discover virtual method) |
| `src/systemtask/SystemTask.cpp` | 238-242 | BleConnected handler, discovery timer start |
| `src/systemtask/SystemTask.cpp` | 383-391 | Discovery timer countdown and trigger |
| `src/sdk_config.h` | 1297-1298 | `BLE_ANCS_C_ENABLED 0` (ANCS disabled) |
| `src/displayapp/screens/Notifications.cpp` | 354-369 | Call button UI handlers |
| `doc/ble.md` | 140-198 | Official notification protocol documentation |

---

## 10. Implications for PalmaMirror

### What We Can Learn from InfiniTime

1. **Service Discovery Pattern**: The sequential discovery chain with completion callbacks is a clean pattern. For Android/Kotlin, this maps well to coroutines with `suspendCancellableCoroutine` wrapping BLE callbacks.

2. **Deferred Discovery**: InfiniTime waits ~5 seconds after connection before starting discovery. This avoids race conditions with BLE connection parameter negotiation and security procedures. PalmaMirror should implement similar debouncing.

3. **CCCD Subscription**: The pattern of discovering descriptors after characteristics, finding the CCCD, and writing `[0x01, 0x00]` to enable notifications is universal BLE and applies directly to ANCS.

4. **Notification Buffering**: A small fixed-size circular buffer is sufficient. On an e-ink device with slow refresh, we likely need even fewer buffered notifications.

### What We CANNOT Use from InfiniTime

1. **ANS protocol is NOT ANCS**: InfiniTime's entire notification system depends on a companion app translating phone notifications into ANS format. PalmaMirror talks directly to iOS, which exposes ANCS, not ANS.

2. **No fragmented packet handling**: InfiniTime receives complete notifications in single GATT writes. ANCS Data Source responses are often fragmented across multiple packets and require reassembly.

3. **No attribute request/response flow**: ANCS requires a two-phase approach: (a) receive event IDs from Notification Source, then (b) request details via Control Point and parse responses from Data Source. InfiniTime has nothing like this.

4. **No notification UID tracking**: ANCS uses 32-bit notification UIDs. InfiniTime uses simple sequential 8-bit IDs.

### What PalmaMirror Must Implement (True ANCS)

Since PalmaMirror acts as a BLE peripheral connecting to an iPhone, it must implement the full ANCS client protocol:

#### ANCS Service Discovery
- Service UUID: `7905F431-B5CE-4E99-A40F-4B1E122D00D0`
- Must be discovered on the iOS device (GATT server) after bonding/encryption

#### Three ANCS Characteristics

| Characteristic | UUID | Direction | Purpose |
|---------------|------|-----------|---------|
| Notification Source (NS) | `9FBF120D-6301-42D9-8C58-25E699A21DBD` | Notify | Event stream (added/modified/removed) |
| Control Point (CP) | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` | Write | Request notification attributes |
| Data Source (DS) | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` | Notify | Attribute data responses |

#### Notification Source Event Format (8 bytes)

```
Byte 0:    EventID (0=Added, 1=Modified, 2=Removed)
Byte 1:    EventFlags (bitmask: Silent=bit0, Important=bit1, PreExisting=bit2, PositiveAction=bit3, NegativeAction=bit4)
Byte 2:    CategoryID (0=Other, 1=IncomingCall, 2=MissedCall, 3=Voicemail, 4=Social, 5=Schedule, 6=Email, 7=News, 8=HealthFitness, 9=BusinessFinance, 10=Location, 11=Entertainment)
Byte 3:    CategoryCount (number of active notifications in this category)
Bytes 4-7: NotificationUID (uint32, little-endian)
```

#### Control Point: Get Notification Attributes Command

```
Byte 0:    CommandID = 0 (GetNotificationAttributes)
Bytes 1-4: NotificationUID (uint32, little-endian)
Byte 5+:   AttributeID list, each optionally followed by MaxLen (uint16 LE)
```

Attribute IDs:
```
0 = AppIdentifier
1 = Title (+ uint16 max length)
2 = Subtitle (+ uint16 max length)
3 = Message (+ uint16 max length)
4 = MessageSize
5 = Date (format: yyyyMMdd'T'HHmmSS)
6 = PositiveActionLabel
7 = NegativeActionLabel
```

Example: Request title (max 32 chars) and message (max 256 chars) for notification UID 0x00000042:
```
[0x00, 0x42, 0x00, 0x00, 0x00, 0x01, 0x20, 0x00, 0x03, 0x00, 0x01]
  cmd   UID (LE)                 Title  MaxLen    Message MaxLen
```

#### Data Source Response Format

```
Byte 0:    CommandID = 0 (GetNotificationAttributes response)
Bytes 1-4: NotificationUID (uint32, little-endian)
Then for each requested attribute:
  Byte:    AttributeID
  2 Bytes: Length (uint16, little-endian)
  N Bytes: Value (UTF-8 string, NOT null-terminated)
```

**CRITICAL**: Data Source responses can be fragmented across multiple BLE notifications. The response must be reassembled using a state machine that tracks:
- Expected CommandID
- Expected NotificationUID
- Current attribute being parsed
- Bytes remaining in current attribute value

#### Perform Notification Action Command

```
Byte 0:    CommandID = 2 (PerformNotificationAction)
Bytes 1-4: NotificationUID (uint32, little-endian)
Byte 5:    ActionID (0=Positive, 1=Negative)
```

#### Get App Attributes Command

```
Byte 0:    CommandID = 1 (GetAppAttributes)
Bytes 1+:  AppIdentifier (null-terminated UTF-8 string)
Then:      AttributeID list (0 = DisplayName)
```

---

## 11. Apple ANCS Protocol Specification

### Prerequisites for ANCS Access

1. **BLE Peripheral Role**: The Android device must advertise as a BLE peripheral (not central). iOS only exposes ANCS to connected peripherals.

2. **Bonding Required**: ANCS is only available after the devices are bonded. iOS requires encryption (pairing) before exposing ANCS.

3. **Service Solicitation**: The peripheral should include ANCS service UUID in its advertising data as a "Service Solicitation" to hint to iOS that it wants ANCS access.

### ANCS Discovery Sequence

```
1. Android advertises as BLE peripheral
   - Include ANCS UUID in Service Solicitation (AD type 0x15 for 128-bit)

2. iOS connects as central

3. After bonding/encryption:
   - Android discovers GATT services on iOS
   - Find ANCS service (7905F431-B5CE-4E99-A40F-4B1E122D00D0)
   - Discover NS, CP, DS characteristics
   - Subscribe to NS (write [0x01, 0x00] to CCCD)
   - Subscribe to DS (write [0x01, 0x00] to CCCD)

4. NS notifications begin flowing for new/modified/removed events

5. For each interesting event:
   - Write GetNotificationAttributes to CP
   - Reassemble response fragments from DS
   - Parse attribute data
   - Display notification
```

### Fragmented Response Reassembly (State Machine)

This is the most complex part of ANCS implementation. The Data Source sends responses that may span multiple BLE notification packets (typically 20 bytes each with default MTU, up to ~512 with negotiated MTU).

```
State: IDLE
  -> Receive first fragment
  -> Parse CommandID (byte 0)
  -> Parse NotificationUID (bytes 1-4)
  -> State: PARSING_ATTRIBUTES

State: PARSING_ATTRIBUTES
  -> Parse AttributeID (1 byte)
  -> Parse Length (2 bytes, little-endian)
  -> If Length == 0: record empty attribute, stay in PARSING_ATTRIBUTES
  -> If Length > 0: State: READING_VALUE, bytesRemaining = Length

State: READING_VALUE
  -> Append incoming bytes to current attribute value
  -> bytesRemaining -= bytesConsumed
  -> If bytesRemaining == 0: State: PARSING_ATTRIBUTES
  -> If no more attributes expected: State: IDLE, emit complete notification

Note: Fragment boundaries are arbitrary -- a single BLE packet may contain
the end of one attribute and the beginning of the next, or even span
multiple attributes. The parser must handle all cases.
```

### Key Differences from InfiniTime's Approach

| Aspect | InfiniTime (ANS) | PalmaMirror (ANCS) |
|--------|------------------|---------------------|
| Protocol | Standard BLE ANS (0x1811) | Apple ANCS (128-bit UUID) |
| Role | Peripheral receives writes | Peripheral discovers service on central |
| Data flow | Push (companion writes data) | Pull (request attributes after event) |
| Fragmentation | None (single packet) | Multi-packet reassembly required |
| Notification ID | uint8 sequential | uint32 from iOS |
| Categories | ANS standard (10) | ANCS categories (12) |
| Actions | Custom characteristic notify | ANCS PerformNotificationAction command |
| Content | Pre-formatted title\0body | Structured attributes (title, subtitle, message, app, date) |
| Companion app | Required (Gadgetbridge) | Not needed (direct iOS communication) |

---

## Summary

InfiniTime's codebase provides an excellent reference for **BLE GATT client patterns** using NimBLE -- service discovery, characteristic enumeration, descriptor-based subscription, and notification handling. These patterns translate directly to Android's BLE API.

However, InfiniTime does **not** implement Apple ANCS. Its notification system relies entirely on the standard Alert Notification Service with a companion app intermediary. For PalmaMirror, which must communicate directly with iOS, a full ANCS client implementation is required with:

1. BLE peripheral advertising with ANCS service solicitation
2. GATT service discovery for the ANCS 128-bit UUID
3. Two-phase notification retrieval (NS event + CP/DS attribute request)
4. Fragmented Data Source response reassembly via state machine
5. Notification action support via Control Point writes

The InfiniTime discovery chain pattern, deferred discovery timing, and circular notification buffer are reusable architectural concepts for PalmaMirror's design.
