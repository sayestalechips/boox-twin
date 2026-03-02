# PalmaMirror Architecture

## Overview

PalmaMirror is a four-layer Android application that mirrors iPhone notifications via Bluetooth Low Energy (ANCS protocol) onto a Boox Palma 2 e-ink device.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI LAYER                                  │
│  ┌──────────┐  ┌──────────────┐  ┌────────┐  ┌──────────────┐  │
│  │HomeScreen│  │IncomingCall  │  │Detail  │  │Settings      │  │
│  │          │  │Screen        │  │Screen  │  │Screen        │  │
│  └────┬─────┘  └──────┬───────┘  └───┬────┘  └──────┬───────┘  │
│       │               │              │               │          │
│  ┌────┴───────────────┴──────────────┴───────────────┴───────┐  │
│  │                    EinkTheme (Design System)               │  │
│  │     Pure B&W · No animations · 48dp min touch · 16sp+     │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ StateFlow<List<Notification>>
                           │ StateFlow<ActiveCall?>
┌──────────────────────────┴──────────────────────────────────────┐
│                      DATA LAYER                                  │
│  ┌─────────────────────┐  ┌────────────────────────────────────┐│
│  │NotificationRepository│  │PreferencesManager (DataStore)     ││
│  │  - In-memory map     │  │  - Font size, filters, replies    ││
│  │  - Sorted by priority│  │  - Paired device                  ││
│  │  - Deduplication     │  │  - Auto-reconnect                 ││
│  └─────────┬───────────┘  └────────────────────────────────────┘│
│            │                                                     │
│  ┌─────────┴───────────┐                                        │
│  │AppDatabase (Room)   │ (Phase 5 — persistence)                │
│  │  - NotificationDao  │                                        │
│  └─────────────────────┘                                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │ AncsNotification events
┌──────────────────────────┴──────────────────────────────────────┐
│                     ANCS PROTOCOL LAYER                          │
│  ┌───────────────┐  ┌──────────────────┐  ┌─────────────────┐  │
│  │AncsEventParser│  │AncsAttributeParser│  │AncsControlPoint │  │
│  │ (8-byte NS)   │  │ (fragmented DS)  │  │ (CP commands)   │  │
│  └───────┬───────┘  └────────┬─────────┘  └────────┬────────┘  │
│          │                   │                      │           │
│  ┌───────┴───────────────────┴──────────────────────┴────────┐  │
│  │                  AncsConstants (UUIDs, IDs)                │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ Raw BLE byte arrays
┌──────────────────────────┴──────────────────────────────────────┐
│                      BLE LAYER                                   │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │BleConnectionMgr  │  │AncsService   │  │BleReconnector    │  │
│  │ - State machine  │  │ - GATT ops   │  │ - Exp. backoff   │  │
│  │ - GATT callback  │  │ - Actions    │  │ - 1s → 60s max   │  │
│  │ - Discovery      │  │              │  │                   │  │
│  └────────┬─────────┘  └──────┬───────┘  └────────┬──────────┘  │
│           │                   │                    │             │
│  ┌────────┴───────────────────┴────────────────────┴──────────┐  │
│  │         MirrorForegroundService (Service Layer)             │  │
│  │  - Foreground notification · Boot receiver · START_STICKY   │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ BluetoothGatt / Android BLE API
                    ┌──────┴──────┐
                    │   iPhone    │
                    │  (ANCS/BLE) │
                    └─────────────┘
```

## Data Flow

### Notification Arrival

```
iPhone ANCS Service
  → Notification Source characteristic (8 bytes)
  → BleConnectionManager.gattCallback.onCharacteristicChanged()
  → MirrorForegroundService.onNotificationSourceData()
  → AncsEventParser.parse() → AncsNotification (basic: uid, category, flags)
  → NotificationRepository.addOrUpdate()
  → AncsService.requestNotificationAttributes(uid)
  → Control Point write → iPhone processes request
  → Data Source characteristic (variable, possibly fragmented)
  → BleConnectionManager.gattCallback.onCharacteristicChanged()
  → MirrorForegroundService.onDataSourceData()
  → AncsAttributeParser.feedData() → AttributeResult (title, message, app, etc.)
  → NotificationRepository.updateAttributes()
  → UI observes StateFlow → renders updated notification
```

### Notification Action (Accept Call)

```
User taps "Accept" on IncomingCallScreen
  → AncsControlPoint.buildPerformAction(uid, positive=true)
  → AncsService.acceptCall(uid) → BleConnectionManager.writeControlPoint()
  → Control Point write → iPhone accepts call
  → NotificationRepository.markActioned(uid)
  → activeCall cleared → UI returns to HomeScreen
```

## Key Design Decisions

1. **Android Views over Compose**: Traditional View system is more predictable on e-ink displays. Compose's recomposition model can trigger unnecessary redraws.

2. **Foreground Service**: Mandatory for persistent BLE connection on Android 11+. Uses `connectedDevice` foreground service type.

3. **In-memory + Room**: Phase 0-4 uses in-memory `LinkedHashMap` for fast notification storage. Phase 5 adds Room for persistence across app restarts.

4. **Interface-driven BLE**: All BLE operations go through interfaces/abstractions that can be mocked for testing. We can't test real BLE, but we can test everything around it.

5. **StateFlow for reactivity**: UI observes `StateFlow<List<AncsNotification>>` from the repository. No LiveData — StateFlow integrates better with coroutines.

6. **Fragment reassembly**: ANCS Data Source responses can span multiple BLE packets. `AncsAttributeParser` maintains a reassembly buffer per notification UID.

7. **ANCS subscription order**: Data Source must be subscribed BEFORE Notification Source (per ANCS spec). The `BleConnectionManager` enforces this order.

## Threading Model

- **BLE callbacks**: Arrive on Binder threads. Byte arrays are copied immediately.
- **Parsing**: Happens on the calling thread (synchronous, fast).
- **Repository**: Thread-safe via `@Synchronized` methods.
- **UI updates**: StateFlow emissions are collected on Main dispatcher.
- **Reconnection**: Handler-based scheduling on Main looper.

## Error Recovery

```
BLE Disconnect
  → BleReconnector starts (exponential backoff: 1s, 2s, 4s, 8s... 60s max)
  → Each attempt: close old GATT, create new connectGatt(autoConnect=true)
  → On success: stop reconnector, re-discover services, re-subscribe
  → On bond loss: notify user to re-pair in Settings
```
