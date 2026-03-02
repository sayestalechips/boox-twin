# PalmaMirror

**Mirror your iPhone notifications on your Boox Palma 2.**

PalmaMirror is an Android app that receives and displays iPhone notifications (incoming calls, text messages, app alerts) via Bluetooth Low Energy using Apple's ANCS protocol. It's designed specifically for the Boox Palma 2's e-ink display — pure black and white, no animations, large touch targets.

## How It Works

PalmaMirror connects to your iPhone the same way a $30 smartwatch does — using Apple's Notification Center Service (ANCS). Once paired:

- Incoming calls show a full-screen alert with Accept/Reject buttons
- Text messages display sender and content with quick reply options
- App notifications appear in a clean, scrollable feed
- Everything is optimized for e-ink: high contrast, fast refresh, readable

## Installation

### Requirements
- Boox Palma 2 (Android 11+, Bluetooth 5.0)
- iPhone (iOS 15+)
- USB cable for sideloading

### Steps

1. **Transfer the APK** to your Palma 2 via USB
2. **Open a file manager** on the Palma 2 and tap the APK to install
3. **Allow installation** from unknown sources when prompted
4. **Launch PalmaMirror** — the setup wizard will guide you through:
   - Granting Bluetooth permissions
   - Pairing with your iPhone
   - Configuring notification preferences

### Pairing with iPhone

1. Open PalmaMirror on the Palma 2
2. On your iPhone, go to **Settings → Bluetooth**
3. Look for **"PalmaMirror"** in the list of available devices
4. Tap to pair — confirm on both devices
5. Once paired, notifications will flow automatically

## Features

- **Incoming call screen** — Full-screen with large Accept/Reject buttons
- **Notification feed** — Scrollable list sorted by priority (calls > messages > other)
- **Notification detail** — Tap any notification to see full content
- **Quick replies** — Send canned replies to text messages
- **Auto-reconnect** — Automatically reconnects when iPhone comes back in range
- **Boot start** — Starts automatically when the Palma 2 boots
- **Battery efficient** — Foreground service with minimal power draw
- **Settings** — Font size, notification filters, battery optimization

## Troubleshooting

### iPhone doesn't see PalmaMirror
- Make sure Bluetooth is enabled on both devices
- Restart Bluetooth on the iPhone (toggle off/on in Settings)
- Restart PalmaMirror on the Palma 2

### Notifications stop arriving
- Check that PalmaMirror is running (look for the persistent notification)
- Make sure the iPhone is within Bluetooth range (~30 feet)
- Open PalmaMirror → Settings → Reconnect
- Check that battery optimization is disabled for PalmaMirror

### "Bond lost" error
- Go to iPhone Settings → Bluetooth → forget "PalmaMirror"
- Go to Palma 2 Settings → Bluetooth → unpair
- Re-pair following the steps above

### Notifications are delayed
- Ensure the iPhone is unlocked (ANCS requires an unlocked phone for initial connection)
- Check that Do Not Disturb is off on the iPhone
- Move the devices closer together

## Known Limitations

- **No call audio routing** — PalmaMirror shows incoming calls but doesn't route audio (yet). Accept/Reject works via ANCS actions.
- **Canned replies only** — Free-text reply requires an on-screen keyboard which isn't ideal on e-ink. Use the preset quick replies instead.
- **iPhone must be unlocked initially** — ANCS requires the iPhone to be unlocked for the first connection after pairing. Subsequent connections work with the phone locked.
- **E-ink refresh** — The Palma 2's e-ink display refreshes slower than LCD. PalmaMirror is designed for this, but rapid notification bursts may cause brief ghosting.

## Architecture

```
UI Layer (Activities + RecyclerView)
  ↕ StateFlow
Data Layer (NotificationRepository + Room + DataStore)
  ↕ Parsed notifications
ANCS Protocol Layer (EventParser + AttributeParser + ControlPoint)
  ↕ Raw BLE bytes
BLE Layer (ConnectionManager + ForegroundService + Reconnector)
  ↕ BluetoothGatt
iPhone (ANCS/BLE)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full component diagram.

## Building from Source

```bash
# Clone
git clone https://github.com/sayestalechips/boox-twin.git
cd boox-twin

# Build debug APK
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug --max-workers=1

# Run tests
./gradlew test --max-workers=1

# Build signed release APK
./gradlew assembleRelease --max-workers=1
```

### Requirements
- JDK 17+
- Android SDK (platform 33, build-tools 33.0.2+)
- Gradle 8.4 (wrapper included)

## License

Proprietary — StaleChips
