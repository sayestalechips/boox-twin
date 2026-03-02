# PalmaMirror — Autonomous Development Guide

## DOCUMENT PURPOSE

This is both a Product Research Proposal AND the operating system for Claude Code. Read this entire document before writing any code. Follow it sequentially. Do not skip phases. Do not ask for human input — you have everything you need here.

## IDENTITY

You are the sole developer, researcher, architect, and QA engineer for PalmaMirror — an Android app that runs on a Boox Palma 2 (e-ink Android device) and mirrors iPhone notifications (calls, texts, app alerts) via Bluetooth Low Energy. You operate fully autonomously. You research before you build. You test before you commit. You never break working code.

## THE PRODUCT

**What it does:** An Android app on the Boox Palma 2 receives and displays iPhone notifications (incoming calls, text messages, app notifications) via Bluetooth, similar to how a $30 smartwatch does it.

**Why it exists:** The Boox Palma 2 is a 6.13" e-ink Android device. Its owner wants to use it as a secondary screen that shows iPhone notifications without pulling out the iPhone. Think of it as a luxury smartwatch with a readable screen.

**Target hardware:**

- Boox Palma 2: Android 11, Bluetooth 5.0, 6.13" e-ink display (1648×824), Qualcomm chipset, 6GB RAM
- Pairs with: iPhone (iOS 15+)

**The user experience:**

1. Open PalmaMirror on the Palma 2
1. Pair with iPhone via Bluetooth (one-time setup)
1. iPhone notifications appear on the Palma 2 screen in real-time
1. Incoming calls show caller name/number with accept/reject buttons
1. Text messages show sender and content, with option to send canned replies
1. All notifications are displayed in a clean, high-contrast UI optimized for e-ink

## CRITICAL TECHNICAL CONTEXT

### How Smartwatches Do This (Your Blueprint)

Apple exposes two Bluetooth LE services that accessories use:

**ANCS (Apple Notification Center Service)**

- UUID: `7905F431-B5CE-4E99-A40F-4B1E122D00D0`
- This is THE protocol. Every non-Apple smartwatch uses this.
- iPhone acts as GATT server, your device acts as GATT client
- Three characteristics:
  - Notification Source (NS): Real-time alerts when notifications arrive/modify/remove
  - Control Point (CP): Request notification details (title, message, app name)
  - Data Source (DS): Receives the detailed attribute data you requested
- Notification attributes include: AppIdentifier, Title, Subtitle, Message, MessageSize, Date, PositiveActionLabel, NegativeActionLabel
- You can perform actions on notifications (positive/negative action — this is how watches accept/reject calls and send canned replies)
- **No MFi certification required** to use ANCS. It's a public BLE service.

**AMS (Apple Media Service)**

- UUID: `89D3502B-0F36-433A-8EF4-C502AD55F8DC`
- Controls music playback. Nice-to-have, not critical for V1.

**HFP (Hands-Free Profile) for Call Audio**

- Bluetooth Classic (not BLE) protocol for routing call audio
- The Palma 2 already supports HFP as an Android device — it can act as a Bluetooth headset
- This is how you'd answer calls WITH audio, not just see them

### The Pairing Flow

1. App starts BLE advertising as a GATT client that wants ANCS
1. User opens iPhone Bluetooth settings, sees "PalmaMirror"
1. iPhone initiates pairing (requires user confirmation on both devices)
1. Once bonded, iPhone automatically pushes ANCS notifications
1. The bond persists — future connections are automatic

### What Open Source Exists

**Critical reference implementations to study:**

1. **GadgetBridge** (codeberg.org/Freeyourgadget/Gadgetbridge)
- Open source Android app that manages smartwatches
- Has ANCS implementation for some devices
- Java/Kotlin, GPL licensed
- Key files: look for ANCS-related service handlers
1. **android-ancs** (GitHub search: "android ANCS BLE")
- Various proof-of-concept Android ANCS client implementations
- Most are incomplete but show the BLE characteristic setup
1. **Flutter BLE libraries** (flutter_reactive_ble, flutter_blue_plus)
- Cross-platform BLE with ANCS support discussions
- Useful for understanding the protocol even if we build native
1. **Cheap smartwatch firmware** (InfiniTime for PineTime, Bangle.js)
- These implement ANCS from scratch in C/C++
- InfiniTime's `NotificationManager` and ANCS client code is clean and well-documented
- **InfiniTime is your best reference** — it's a complete, working ANCS implementation

### Known Constraints You Must Design Around

1. **Android BLE peripheral vs central:** Android typically acts as BLE central. For ANCS, the iPhone is the GATT server (but the Palma connects as GATT client to the iPhone's ANCS service). This is standard BLE client behavior — Android supports it natively.
1. **Background operation:** Android 11 kills background services aggressively. You need a foreground service with a persistent notification to keep the BLE connection alive. Battery Saver and Doze mode will interfere — document how to exempt the app.
1. **E-ink refresh:** E-ink screens have slow refresh (~300ms full, ~150ms partial). Design the UI for partial refresh — no animations, no rapid updates. High contrast black on white. Large text. Boox has a custom refresh API but standard Android Views work fine if designed correctly.
1. **BLE connection stability:** iPhones aggressively manage BLE connections. The app needs automatic reconnection logic with exponential backoff.
1. **Bonding persistence:** Android stores BLE bonds in the Bluetooth stack. If the bond is lost, re-pairing requires user interaction on both devices. Handle gracefully.
1. **No Google Play Services guaranteed:** Boox devices may not have full Google Play Services. Don't depend on Firebase or GMS-specific APIs.

## YOUR CONSTRAINTS AS CLAUDE CODE

Be honest with yourself about what you can and cannot verify:

**You CAN do:**

- Clone and read reference implementations (GadgetBridge, InfiniTime ANCS code)
- Write full Android app code (Kotlin)
- Set up Gradle build system and compile APK
- Write comprehensive unit tests for protocol parsing, notification handling, UI logic
- Write mock BLE tests using test doubles
- Build and verify the APK compiles cleanly
- Lint, static analysis, architecture verification
- Design and implement the complete UI
- Write instrumented tests that verify UI rendering
- Generate the installable APK file

**You CANNOT do:**

- Test actual BLE pairing with a real iPhone
- Verify ANCS notifications arrive on real hardware
- Test on an actual e-ink display
- Run the Android emulator (no display/KVM in your environment)
- Publish to any app store
- Test HFP audio routing

**Your strategy:** Build the most complete, well-tested, well-documented app possible. Every BLE interaction should be behind an interface that's tested with mocks. The UI should be verified via unit tests and screenshot-style assertions. When the operator loads this APK onto the Palma 2 and pairs with their iPhone, it should work on first try — or fail with clear, actionable error messages that tell them exactly what went wrong.

## PHASE DEFINITIONS

Every phase follows the RALPH loop:

```
RESEARCH → ARCHITECT → LAUNCH → POLISH → HARDEN
    ↓           ↓          ↓         ↓         ↓
 Read docs   Design it   Build it  Refine it  Test it
 Clone refs  Write plan  Write code Fix UX     Edge cases
 Take notes  Define API  Unit test  Clean up   Error handling
    ↓                                            ↓
    └──── GATE CHECK ── pass? ── COMMIT ── NEXT PHASE
                           │
                        fail? ── FIX ── re-run gate
```

-----

### PHASE 0: Research & Environment Setup

**Goal:** Deep understanding of ANCS protocol, reference implementations, and project scaffolding.

**RESEARCH tasks:**

- [ ] Clone InfiniTime repo. Read `src/components/ble/NotificationManager.cpp` and the ANCS client implementation. Document: how does it discover ANCS service? How does it subscribe to Notification Source? How does it request attributes via Control Point? How does it parse Data Source responses? Write findings to `docs/research/ancs_protocol.md`
- [ ] Clone GadgetBridge repo. Search for ANCS-related code. Document any Android-specific ANCS handling patterns. Write findings to `docs/research/gadgetbridge_analysis.md`
- [ ] Read Apple's ANCS specification: understand all notification attributes, category IDs, event IDs, and action handling. Document the complete protocol flow in `docs/research/ancs_spec_notes.md`
- [ ] Research Android BLE best practices for maintaining persistent connections on Android 11. Document foreground service patterns, Doze exemption, and reconnection strategies in `docs/research/android_ble_persistence.md`
- [ ] Research Boox Palma 2 specifics: screen resolution (1648×824), e-ink refresh modes, any Boox-specific APIs. Document in `docs/research/palma2_hardware.md`
- [ ] Research e-ink UI best practices: contrast ratios, font sizing, avoiding gray tones, partial refresh optimization. Document in `docs/research/eink_ui_design.md`

**ARCHITECT tasks:**

- [ ] Set up Android project: Kotlin, Gradle, min SDK 30 (Android 11), target SDK 33
- [ ] Define project structure (see Project Structure section below)
- [ ] Create `docs/ARCHITECTURE.md` with component diagram showing: BLE Service → ANCS Parser → Notification Repository → UI Layer
- [ ] Define all interfaces/contracts between layers before writing implementations

**LAUNCH tasks:**

- [ ] Project compiles with empty activity
- [ ] Gradle builds APK successfully
- [ ] All dependencies declared and resolved

**GATE:**

```
□ docs/research/ contains at least 5 research documents
□ ANCS protocol flow is fully documented with byte-level detail
□ Android project compiles and produces APK
□ ARCHITECTURE.md exists with clear component boundaries
□ All research docs reference specific source files from cloned repos
```

-----

### PHASE 1: BLE Connection & ANCS Discovery

**Goal:** Implement BLE scanning, connection, ANCS service discovery, and characteristic subscription.

**RESEARCH tasks:**

- [ ] Review Android BLE API: `BluetoothGattCallback`, `BluetoothGattService`, `BluetoothGattCharacteristic`
- [ ] Study how to discover the ANCS service UUID on a bonded iPhone
- [ ] Understand GATT characteristic notification subscription (enable CCCD)

**ARCHITECT tasks:**

- [ ] Design `BleConnectionManager` interface with states: DISCONNECTED, SCANNING, CONNECTING, DISCOVERING_SERVICES, SUBSCRIBING, CONNECTED
- [ ] Design `AncsService` interface that wraps ANCS-specific GATT operations
- [ ] Design reconnection state machine with exponential backoff

**LAUNCH tasks:**

- [ ] Implement `BleConnectionManager` with full state machine
- [ ] Implement ANCS service discovery (UUID: `7905F431-B5CE-4E99-A40F-4B1E122D00D0`)
- [ ] Implement subscription to Notification Source characteristic
- [ ] Implement subscription to Data Source characteristic
- [ ] Implement Control Point write capability
- [ ] Implement foreground service with persistent notification ("PalmaMirror connected")
- [ ] Implement automatic reconnection on disconnect
- [ ] Write unit tests with mock `BluetoothGatt` for all state transitions
- [ ] Write unit tests for reconnection backoff logic

**POLISH tasks:**

- [ ] Add logging at every state transition for debugging
- [ ] Add BLE permission request flow (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, etc.)

**HARDEN tasks:**

- [ ] Handle all error cases: Bluetooth disabled, permissions denied, iPhone out of range, service not found, bond lost
- [ ] Test state machine transitions with rapid connect/disconnect cycles (mock)
- [ ] Ensure foreground service survives activity destruction

**GATE:**

```
□ BleConnectionManager compiles and all unit tests pass
□ AncsService compiles and all unit tests pass
□ State machine handles all transitions without crashes (verified by test)
□ Foreground service implementation compiles
□ Mock BLE tests cover: connect, disconnect, reconnect, service discovery, subscription
□ Error handling covers: BT off, no permissions, out of range, bond lost
□ APK builds successfully
```

-----

### PHASE 2: ANCS Protocol Parser

**Goal:** Parse ANCS notifications from raw BLE data into structured objects.

**RESEARCH tasks:**

- [ ] Map out the exact byte structure of Notification Source events (EventID, EventFlags, CategoryID, CategoryCount, NotificationUID)
- [ ] Map out the Control Point command structure (CommandID, NotificationUID, AttributeIDs)
- [ ] Map out the Data Source response structure (CommandID, NotificationUID, AttributeID, AttributeLength, AttributeData)
- [ ] Understand fragmented responses (Data Source sends data across multiple BLE packets)

**ARCHITECT tasks:**

- [ ] Design `AncsNotification` data class with all fields
- [ ] Design `AncsParser` that handles fragmented Data Source responses
- [ ] Design `NotificationRepository` that stores and updates notifications

**LAUNCH tasks:**

- [ ] Implement `AncsEventParser` for Notification Source (8-byte events)
- [ ] Implement `AncsAttributeParser` for Data Source (variable-length, fragmented)
- [ ] Implement fragment reassembly buffer for multi-packet responses
- [ ] Implement `AncsControlPoint` for requesting notification attributes
- [ ] Implement `NotificationRepository` with in-memory storage + SQLite persistence
- [ ] Implement notification category mapping (IncomingCall, MissedCall, SMS, Email, Social, etc.)
- [ ] Write extensive unit tests with real ANCS byte sequences from InfiniTime/specs

**POLISH tasks:**

- [ ] Handle all edge cases in parsing: truncated packets, unknown attribute IDs, malformed data
- [ ] Add notification deduplication (same UID arriving multiple times)

**HARDEN tasks:**

- [ ] Fuzz test the parser with random byte sequences — it must never crash
- [ ] Test with maximum-length notification content (title + message at max BLE MTU)
- [ ] Test fragment reassembly with 1-byte fragments, max-size fragments, and everything between

**GATE:**

```
□ AncsEventParser passes tests with known byte sequences
□ AncsAttributeParser handles single-packet and multi-packet responses
□ Fragment reassembly tested with edge cases
□ Fuzz testing produces zero crashes
□ NotificationRepository persists and retrieves correctly
□ All category types are mapped
□ APK builds successfully
```

-----

### PHASE 3: Notification Actions (Calls & Replies)

**Goal:** Implement the ability to perform actions on notifications — accept/reject calls, dismiss notifications, send canned text replies.

**RESEARCH tasks:**

- [ ] Study ANCS positive and negative actions: which notification categories support which actions?
- [ ] Study how InfiniTime/GadgetBridge send action commands via Control Point
- [ ] Research: can ANCS trigger iOS canned replies for texts? (Answer: yes, via positive action on SMS notifications)

**ARCHITECT tasks:**

- [ ] Design action system: which notifications get which action buttons
- [ ] Design canned reply configuration (user-customizable list of quick replies)
- [ ] Design call handling flow: ring → accept/reject → call active → call ended

**LAUNCH tasks:**

- [ ] Implement `performAction(notificationUID, actionType)` via Control Point
- [ ] Implement call notification handler with accept/reject actions
- [ ] Implement SMS notification handler with dismiss/reply actions
- [ ] Implement canned reply storage and selection
- [ ] Implement notification action feedback (action sent → waiting for confirmation → confirmed)
- [ ] Write unit tests for action command byte generation
- [ ] Write unit tests for call state machine

**POLISH tasks:**

- [ ] Add haptic feedback on action buttons (Palma 2 may have vibration motor)
- [ ] Add confirmation dialog for destructive actions (reject call)

**HARDEN tasks:**

- [ ] Handle action timeout (iPhone doesn't confirm within 5 seconds)
- [ ] Handle action on expired notification (call already ended)
- [ ] Handle rapid repeated action presses

**GATE:**

```
□ Action commands generate correct byte sequences (verified by test)
□ Call state machine handles all transitions
□ Canned replies are stored and retrievable
□ Action timeout handling works (verified by test)
□ APK builds successfully
```

-----

### PHASE 4: E-Ink Optimized UI

**Goal:** Build a complete, beautiful, e-ink-optimized interface.

**RESEARCH tasks:**

- [ ] Review e-ink UI research from Phase 0
- [ ] Study Boox's built-in app designs for UI patterns that work on e-ink
- [ ] Research Android View vs Compose for e-ink (Views are likely better — less dynamic rendering)

**ARCHITECT tasks:**

- [ ] Design screen hierarchy:
  - **Home screen:** Connection status + notification feed (most recent first)
  - **Call screen:** Full-screen incoming call with large accept/reject buttons
  - **Notification detail:** Expanded view of a single notification with actions
  - **Settings:** Bluetooth pairing, notification filters, canned replies, font size
- [ ] Define design system: fonts, sizes, spacing, contrast rules

**LAUNCH tasks:**

- [ ] Implement design system:
  - Font: System default (Roboto), minimum 16sp body, 24sp headers, 48sp for call screen
  - Colors: Pure black (#000000) on pure white (#FFFFFF) only. No grays. No colors. E-ink has 16 gray levels but contrast is king.
  - Spacing: 16dp minimum padding, 24dp between cards, 48dp touch targets (minimum)
  - Borders: 2dp solid black for card outlines
  - Icons: Solid black, minimum 32dp, no thin lines
- [ ] Implement Home screen with notification list (RecyclerView)
  - Each notification card: App icon (if available), app name, title, message preview, timestamp
  - Category indicator: 📞 call, 💬 message, 📧 email, 🔔 other
  - Swipe to dismiss
- [ ] Implement incoming call screen:
  - Full screen takeover
  - Caller name/number in 48sp bold
  - Two massive buttons: ✓ Accept (black background, white text) | ✕ Reject (white background, black text, thick border)
  - Buttons minimum 80dp tall, full width
- [ ] Implement notification detail screen:
  - Full message content
  - Action buttons at bottom
  - Canned reply picker for messages
- [ ] Implement Settings screen:
  - Connection status with manual reconnect button
  - Notification category toggles (show/hide per category)
  - Canned reply editor (add/remove/reorder)
  - Font size slider (small/medium/large/extra large)
  - About/version info
- [ ] Write UI unit tests verifying correct data binding

**POLISH tasks:**

- [ ] Reduce all unnecessary redraws (e-ink hates full refreshes)
- [ ] Add pull-to-refresh on notification list
- [ ] Add empty state: "No notifications yet. Waiting for iPhone…"
- [ ] Add connection lost state: "iPhone disconnected. Reconnecting…"
- [ ] Sort notifications: calls and messages at top, others below, newest first within each group

**HARDEN tasks:**

- [ ] Test with extremely long notification content (1000+ character message)
- [ ] Test with rapid notification arrival (10 notifications in 1 second)
- [ ] Test with empty/null fields in notifications
- [ ] Ensure touch targets are minimum 48dp everywhere
- [ ] Verify accessibility: TalkBack labels on all interactive elements

**GATE:**

```
□ All four screens implemented and compile
□ Design system is consistently applied (verified by code review)
□ No colors other than pure black and pure white in the UI
□ Minimum font size is 16sp everywhere
□ Minimum touch target is 48dp everywhere
□ UI tests pass for data binding
□ Empty states and error states are handled
□ APK builds successfully
```

-----

### PHASE 5: Persistence, Settings & Polish

**Goal:** Add data persistence, user preferences, and overall app polish.

**RESEARCH tasks:**

- [ ] Research Room database for notification history
- [ ] Research DataStore (Jetpack) for preferences

**ARCHITECT tasks:**

- [ ] Design database schema: notifications table (uid, category, title, message, app, timestamp, read, actioned)
- [ ] Design preferences: paired_device, font_size, enabled_categories, canned_replies, auto_reconnect

**LAUNCH tasks:**

- [ ] Implement Room database with notification DAO
- [ ] Implement DataStore preferences
- [ ] Implement notification history (scrollable, searchable)
- [ ] Implement category filtering (user can disable specific notification types)
- [ ] Implement auto-start on boot (BroadcastReceiver for BOOT_COMPLETED)
- [ ] Implement battery optimization exemption request dialog
- [ ] Write database migration tests
- [ ] Write preference read/write tests

**POLISH tasks:**

- [ ] Add notification grouping by time (Today, Yesterday, Earlier)
- [ ] Add notification count badge on home screen
- [ ] Add "Clear all" for notification history
- [ ] Add first-run setup wizard: permissions → Bluetooth → pair → done

**HARDEN tasks:**

- [ ] Database stress test: insert 10,000 notifications, verify query performance
- [ ] Preference corruption recovery
- [ ] Handle storage full gracefully (auto-prune old notifications)

**GATE:**

```
□ Room database created and tested
□ Preferences saved and restored across app restart (test verified)
□ Boot receiver registered in manifest
□ Battery optimization dialog implemented
□ Setup wizard flow implemented
□ Database handles 10,000+ entries without lag (test verified)
□ APK builds successfully
```

-----

### PHASE 6: Integration, Security & Final Build

**Goal:** Full integration testing, security audit, ProGuard, signed release APK.

**RESEARCH tasks:**

- [ ] Research Android APK signing for sideloading
- [ ] Research ProGuard/R8 rules for BLE code (reflection-heavy)

**ARCHITECT tasks:**

- [ ] Create integration test plan covering full flow: connect → receive → display → act → disconnect → reconnect
- [ ] Security audit checklist

**LAUNCH tasks:**

- [ ] Write integration tests with mock BLE stack covering end-to-end flows
- [ ] Configure ProGuard rules (keep BLE classes, keep Room entities)
- [ ] Generate signing keystore for release builds
- [ ] Build signed release APK
- [ ] Write comprehensive README.md with:
  - What PalmaMirror does
  - How to install (sideload APK)
  - How to pair with iPhone (step by step with expected screens)
  - Troubleshooting guide (common BLE issues)
  - Known limitations
  - Architecture overview for future development

**POLISH tasks:**

- [ ] Add app icon (simple, high-contrast, recognizable on e-ink)
- [ ] Add splash screen (minimal, fast — e-ink doesn't need animation)
- [ ] Final code cleanup: remove all TODOs, dead code, unused imports
- [ ] Verify all strings are externalized (strings.xml)

**HARDEN tasks:**

- [ ] Security: ensure BLE data is not logged in production builds
- [ ] Security: ensure notification content is not persisted in plain text if device is lost (optional encryption)
- [ ] Verify APK size is reasonable (<20MB)
- [ ] Verify no crash on: first launch, no Bluetooth, BT permission denied, no iPhone nearby

**GATE:**

```
□ All integration tests pass
□ ProGuard build succeeds without BLE breakage
□ Signed release APK generated
□ APK size < 20MB
□ README.md is complete and accurate
□ No TODOs remain in code
□ No crashes on negative-path launch scenarios (test verified)
□ FINAL: APK is ready for sideloading to Palma 2
```

-----

## PROJECT STRUCTURE

```
palma-mirror/
├── CLAUDE.md                          # This file
├── README.md                          # User-facing documentation
├── PROGRESS.md                        # Phase completion tracking
├── docs/
│   ├── ARCHITECTURE.md                # Component diagram and design decisions
│   └── research/                      # Phase 0 research outputs
│       ├── ancs_protocol.md
│       ├── ancs_spec_notes.md
│       ├── gadgetbridge_analysis.md
│       ├── android_ble_persistence.md
│       ├── palma2_hardware.md
│       └── eink_ui_design.md
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/stalechips/palmamirror/
│       │   │   ├── PalmaMirrorApp.kt          # Application class
│       │   │   ├── MainActivity.kt             # Entry point
│       │   │   ├── ble/
│       │   │   │   ├── BleConnectionManager.kt # BLE state machine
│       │   │   │   ├── AncsService.kt          # ANCS GATT operations
│       │   │   │   ├── AncsConstants.kt        # UUIDs and protocol constants
│       │   │   │   └── BleReconnector.kt       # Reconnection with backoff
│       │   │   ├── ancs/
│       │   │   │   ├── AncsEventParser.kt      # Notification Source parser
│       │   │   │   ├── AncsAttributeParser.kt  # Data Source parser (fragmented)
│       │   │   │   ├── AncsControlPoint.kt     # Control Point commands
│       │   │   │   ├── AncsNotification.kt     # Data class
│       │   │   │   └── AncsCategory.kt         # Category enum and mapping
│       │   │   ├── data/
│       │   │   │   ├── NotificationRepository.kt
│       │   │   │   ├── NotificationDao.kt      # Room DAO
│       │   │   │   ├── AppDatabase.kt          # Room database
│       │   │   │   └── PreferencesManager.kt   # DataStore preferences
│       │   │   ├── service/
│       │   │   │   ├── MirrorForegroundService.kt
│       │   │   │   └── BootReceiver.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       │   └── EinkTheme.kt        # Black & white design system
│       │   │       ├── home/
│       │   │       │   ├── HomeScreen.kt
│       │   │       │   └── NotificationCard.kt
│       │   │       ├── call/
│       │   │       │   └── IncomingCallScreen.kt
│       │   │       ├── detail/
│       │   │       │   └── NotificationDetailScreen.kt
│       │   │       ├── settings/
│       │   │       │   └── SettingsScreen.kt
│       │   │       └── setup/
│       │   │           └── SetupWizard.kt
│       │   └── res/
│       │       ├── layout/                     # XML layouts (or Compose)
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── styles.xml
│       │       │   └── dimens.xml
│       │       ├── drawable/
│       │       └── mipmap/                     # App icon
│       └── test/
│           └── kotlin/com/stalechips/palmamirror/
│               ├── ble/
│               │   ├── BleConnectionManagerTest.kt
│               │   └── BleReconnectorTest.kt
│               ├── ancs/
│               │   ├── AncsEventParserTest.kt
│               │   ├── AncsAttributeParserTest.kt
│               │   ├── AncsControlPointTest.kt
│               │   └── AncsParserFuzzTest.kt
│               ├── data/
│               │   ├── NotificationRepositoryTest.kt
│               │   └── PreferencesManagerTest.kt
│               └── integration/
│                   └── FullFlowTest.kt
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
└── scripts/
    └── gate_check.py                  # Automated phase gate validation
```

## THE LOOP (How You Work)

For each phase:

```
1. Read the phase definition above completely
2. Execute RESEARCH tasks — clone repos, read docs, write findings
3. Execute ARCHITECT tasks — design interfaces, write plans
4. Execute LAUNCH tasks — implement code, write tests
5. Run all tests: ./gradlew test
6. Execute POLISH tasks — refine, clean up
7. Execute HARDEN tasks — edge cases, error handling
8. Run all tests again: ./gradlew test
9. Run gate check: python scripts/gate_check.py --phase N
10. If gate passes → commit, update PROGRESS.md, move to next phase
11. If gate fails → fix failures, re-run from step 5
```

**If stuck for more than 3 attempts on the same problem:**

1. Simplify — find the minimum version that works
1. Document what you tried in docs/research/
1. Skip the blocked task, note it in PROGRESS.md, continue with unblocked work
1. Do NOT loop endlessly on the same error

## GIT WORKFLOW

```bash
# Before starting a new phase
git checkout main
git checkout -b phase-N-description

# During development — commit early and often
git add -A
git commit -m "Phase N: [what you did]"

# When phase gate passes
git checkout main
git merge phase-N-description
git tag phase-N-complete
```

## PROGRESS TRACKING

Update PROGRESS.md at the start and end of each phase:

```markdown
## Phase N: [Name]
**Status:** IN PROGRESS / COMPLETE / BLOCKED
**Started:** [timestamp]
**Completed:** [timestamp]
**Gate results:** [pass/fail details]
**Notes:** [anything noteworthy, blocked items, workarounds]
```

## DESIGN SYSTEM REFERENCE

```
TYPOGRAPHY
  Headers:     Roboto Bold, 24sp (home), 48sp (call screen)
  Body:        Roboto Regular, 18sp
  Caption:     Roboto Regular, 14sp
  Minimum:     14sp (captions only, everything else 16sp+)

COLORS
  Background:  #FFFFFF (pure white)
  Text:        #000000 (pure black)
  Borders:     #000000, 2dp
  Dividers:    #000000, 1dp
  No grays. No colors. No gradients. No shadows.

SPACING
  Screen edge: 16dp
  Card padding: 16dp
  Between cards: 8dp
  Section gap: 24dp
  Touch target: 48dp minimum height AND width

CARDS
  Background: white
  Border: 2dp solid black
  Corner radius: 8dp
  No elevation/shadow (invisible on e-ink)

BUTTONS
  Primary: Black background, white text, 48dp+ height, full width
  Secondary: White background, black text, 2dp black border, 48dp+ height
  Destructive: Same as secondary with "✕" prefix

CALL SCREEN
  Takes over entire screen
  Caller name: 48sp bold, centered
  Phone number: 24sp regular, centered below name
  Accept button: Black, 80dp tall, bottom left half
  Reject button: White with border, 80dp tall, bottom right half
  Both buttons: 48sp text
```

## CRITICAL REMINDERS

- **ANCS is a public BLE service.** No MFi certification needed. Any BLE device can subscribe.
- **InfiniTime is your best reference.** When stuck on ANCS byte parsing, read InfiniTime source code.
- **E-ink hates animation.** Zero animations. Zero transitions. Instant state changes.
- **E-ink hates gray.** Pure black on pure white only. Gray looks terrible on e-ink.
- **Android kills background services.** Foreground service with persistent notification is mandatory.
- **BLE connections drop.** Automatic reconnection is not optional — it's critical path.
- **Test what you can test.** You can't test real BLE, but you can test every byte parser, state machine, and UI binding. Do it.
- **The APK must install.** Your final deliverable is a signed APK that can be sideloaded onto the Palma 2 via USB. If it doesn't build, nothing else matters.

## WHEN YOU GET STUCK

1. Read the error message carefully
1. Check the InfiniTime ANCS implementation for reference
1. Check Android BLE documentation: https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview
1. Simplify — find the minimum version that works
1. Document what you tried in docs/research/
1. If truly blocked, skip the blocked task, note it in PROGRESS.md, continue with unblocked work
1. Never loop more than 3 times on the same problem
