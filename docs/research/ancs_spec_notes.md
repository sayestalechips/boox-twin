# Apple Notification Center Service (ANCS) Specification Notes

> Research compiled for PalmaMirror -- an Android app on Boox Palma 2 that mirrors
> iPhone notifications via BLE. This document covers the complete ANCS protocol at
> byte level, drawn from Apple's official specification and reference implementations.

**Spec version:** 1.1 (current as of iOS 7+, with action support added in iOS 8)
**Apple reference:** https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html

---

## Table of Contents

1. [Terminology and Roles](#1-terminology-and-roles)
2. [Service and Characteristic UUIDs](#2-service-and-characteristic-uuids)
3. [Complete Protocol Flow](#3-complete-protocol-flow)
4. [Notification Source Characteristic (NS)](#4-notification-source-characteristic-ns)
5. [Control Point Characteristic (CP)](#5-control-point-characteristic-cp)
6. [Data Source Characteristic (DS)](#6-data-source-characteristic-ds)
7. [Notification Attributes](#7-notification-attributes)
8. [App Attributes](#8-app-attributes)
9. [Action Handling](#9-action-handling)
10. [Fragmentation and Reassembly](#10-fragmentation-and-reassembly)
11. [Error Handling](#11-error-handling)
12. [Session Management](#12-session-management)
13. [PalmaMirror Implementation Notes](#13-palmamirror-implementation-notes)

---

## 1. Terminology and Roles

| Term | Meaning |
|------|---------|
| **NP** (Notification Provider) | The iPhone. Acts as the **GATT server**. Hosts the ANCS service. |
| **NC** (Notification Consumer) | The accessory (Boox Palma 2). Acts as the **BLE peripheral** and **GATT client**. |

Key architectural point: The NC advertises and the iPhone (central) connects TO it, but
the iPhone is the GATT server hosting ANCS. The NC is the GATT client that discovers
and reads/writes ANCS characteristics. This is a dual-role scenario:

- **GAP role:** NC = Peripheral (advertiser), NP = Central (scanner/connector)
- **GATT role:** NC = Client (discovers services, writes CP, subscribes to NS/DS), NP = Server (hosts ANCS)

---

## 2. Service and Characteristic UUIDs

### Primary Service

| Component | UUID |
|-----------|------|
| **ANCS Service** | `7905F431-B5CE-4E99-A40F-4B1E122D00D0` |

### Characteristics

| Characteristic | UUID | Properties | Required |
|----------------|------|------------|----------|
| **Notification Source (NS)** | `9FBF120D-6301-42D9-8C58-25E699A21DBD` | Notify | **Mandatory** |
| **Control Point (CP)** | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` | Write With Response | Optional |
| **Data Source (DS)** | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` | Notify | Optional |

All three characteristics require **authorization** (i.e., pairing/bonding must be
completed before they can be accessed).

> Note: CP and DS are listed as "optional" in the spec, but in practice they are
> essential for retrieving notification content (title, message body, app name, etc.).
> Without them, the NC only receives the 8-byte NS summary.

---

## 3. Complete Protocol Flow

### Phase 1: Advertising and Connection

```
NC (Boox Palma 2)                         NP (iPhone)
      |                                        |
      |--- BLE Advertisement ----------------->|
      |    (includes Service Solicitation       |
      |     for ANCS UUID)                      |
      |                                        |
      |<-- Connection Request (Central) -------|
      |                                        |
      |--- Connection Accepted --------------->|
      |                                        |
```

The NC must advertise with a **Service Solicitation** data type containing the
128-bit ANCS UUID (`7905F431-B5CE-4E99-A40F-4B1E122D00D0`). This causes the
device to appear in iOS Settings > Bluetooth, enabling user-initiated pairing.

**Advertisement data structure:**
```
Byte  Value    Meaning
0     0x02     Length of flags field
1     0x01     AD Type: Flags
2     0x06     LE General Discoverable | BR/EDR Not Supported
3     0x11     Length of solicitation field (17 bytes)
4     0x15     AD Type: List of 128-bit Service Solicitation UUIDs
5-20  (UUID)   7905F431-B5CE-4E99-A40F-4B1E122D00D0 (little-endian in advert)
```

> AD Type 0x15 = "List of 128-bit Service Solicitation UUIDs" per Bluetooth CSS.

### Phase 2: Pairing and Bonding

```
NC                                         NP (iPhone)
      |                                        |
      |--- MTU Exchange Request --------------->|
      |<-- MTU Exchange Response ---------------|
      |                                        |
      |--- Discover Primary Services --------->|
      |    (by UUID: ANCS service UUID)        |
      |<-- Service Handle Range ---------------|
      |                                        |
      |--- Discover Characteristics ----------->|
      |    (within ANCS handle range)          |
      |<-- Characteristic declarations ---------|
      |                                        |
      |--- Subscribe to NS (write CCCD) ------>|
      |<-- ATT Error: Insufficient Auth -------|
      |                                        |
      |--- Initiate Pairing/Bonding ---------->|
      |<-- Pairing Request (from iOS) ---------|
      |--- Pairing Response ------------------>|
      |    (user confirms on both devices)     |
      |<-- Encryption established --------------|
      |<-- Bonding complete --------------------|
      |                                        |
```

Key: When the NC first tries to subscribe to NS, iOS rejects with
`ATT_ERR_INSUFFICIENT_AUTHEN` (0x05). This triggers the pairing flow.
After bonding completes, the NC must **retry** the subscription.

### Phase 3: Subscription and Notification Flow

```
NC                                         NP (iPhone)
      |                                        |
      |--- Subscribe to NS (write CCCD=0x0001) |
      |<-- Write confirmed --------------------|
      |                                        |
      |--- Subscribe to DS (write CCCD=0x0001) |
      |<-- Write confirmed --------------------|
      |                                        |
      |  (Session is now active)               |
      |                                        |
      |<-- NS Notification (8 bytes) ----------|  (new notification arrives on iPhone)
      |                                        |
      |--- Write CP: GetNotificationAttributes |
      |<-- Write confirmed --------------------|
      |                                        |
      |<-- DS Notification (fragment 1) -------|
      |<-- DS Notification (fragment 2) -------|
      |<-- DS Notification (fragment N) -------|
      |    (NC reassembles complete response)  |
      |                                        |
```

**Important:** The NC must subscribe to DS **before** writing to CP. If the NC
writes a GetNotificationAttributes command to CP without having subscribed to DS,
the response data will be lost.

### Phase 4: Full Notification Retrieval Sequence

```
1. iPhone generates a notification (e.g., iMessage arrives)
2. NP sends 8-byte GATT notification on NS characteristic
3. NC parses NS: gets EventID, EventFlags, CategoryID, CategoryCount, NotificationUID
4. NC decides whether to fetch details (e.g., skip if EventID == Removed)
5. NC writes GetNotificationAttributes to CP with desired AttributeIDs
6. NP responds via GATT notifications on DS (possibly fragmented)
7. NC reassembles DS response, extracts Title, Message, AppIdentifier, etc.
8. (Optional) NC writes GetAppAttributes to CP for app display name
9. (Optional) NC writes PerformNotificationAction to CP for accept/dismiss
```

---

## 4. Notification Source Characteristic (NS)

The NS characteristic delivers an **8-byte** notification for every iOS notification
event. This is a lightweight summary -- it does NOT contain the notification text.

### Byte Structure (8 bytes total)

```
Offset  Size     Field            Type
------  ----     -----            ----
0       1 byte   EventID          uint8
1       1 byte   EventFlags       uint8 (bitmask)
2       1 byte   CategoryID       uint8
3       1 byte   CategoryCount    uint8
4-7     4 bytes  NotificationUID  uint32 (little-endian)
```

### EventID (byte 0)

| Value | Name | Description |
|-------|------|-------------|
| 0 | `EventIDNotificationAdded` | A new notification has arrived on the NP |
| 1 | `EventIDNotificationModified` | An existing notification has been modified |
| 2 | `EventIDNotificationRemoved` | A notification has been dismissed/cleared |
| 3-255 | Reserved | |

### EventFlags (byte 1) -- Bitmask

| Bit | Mask | Name | Description |
|-----|------|------|-------------|
| 0 | `0x01` | `EventFlagSilent` | Notification should be displayed silently (no sound/vibration) |
| 1 | `0x02` | `EventFlagImportant` | Notification is important (e.g., time-sensitive) |
| 2 | `0x04` | `EventFlagPreExisting` | Notification existed before session started (sent on subscribe) |
| 3 | `0x08` | `EventFlagPositiveAction` | A positive action is available (e.g., Accept, Reply) |
| 4 | `0x10` | `EventFlagNegativeAction` | A negative action is available (e.g., Decline, Dismiss) |
| 5-7 | | Reserved | |

Multiple flags can be set simultaneously. For example, `0x19` = Silent + PositiveAction + NegativeAction.

**Pre-existing flag:** When an NC first subscribes to NS, the NP sends
`EventIDNotificationAdded` for all currently active notifications. These have
`EventFlagPreExisting` set so the NC can distinguish them from genuinely new events.

### CategoryID (byte 2)

| Value | Name | Typical iOS Sources |
|-------|------|---------------------|
| 0 | `CategoryIDOther` | Catch-all for uncategorized |
| 1 | `CategoryIDIncomingCall` | Phone, FaceTime, VoIP apps |
| 2 | `CategoryIDMissedCall` | Phone, FaceTime |
| 3 | `CategoryIDVoicemail` | Phone voicemail |
| 4 | `CategoryIDSocial` | Messages, WhatsApp, Telegram, etc. |
| 5 | `CategoryIDSchedule` | Calendar, Reminders |
| 6 | `CategoryIDEmail` | Mail, Gmail, Outlook |
| 7 | `CategoryIDNews` | News apps |
| 8 | `CategoryIDHealthAndFitness` | Health, workout apps |
| 9 | `CategoryIDBusinessAndFinance` | Finance, banking apps |
| 10 | `CategoryIDLocation` | Maps, location-based alerts |
| 11 | `CategoryIDEntertainment` | Music, media apps |
| 12-255 | Reserved | |

> The NP makes a "best effort" to provide accurate CategoryID. It may not always
> be correct -- apps can override notification categories.

### CategoryCount (byte 3)

The number of **active** notifications in the given category on the NP. For example,
if there are 3 unread emails, an email notification would have `CategoryCount = 3`.

### NotificationUID (bytes 4-7)

A **32-bit unsigned integer** in **little-endian** byte order. This UID is unique
within the current ANCS session and is used to:
- Request notification attributes via CP
- Perform actions on the notification via CP

**The UID is only valid for the duration of the current session.** Once the session
ends (disconnect or unsubscribe), all UIDs are invalidated.

### Example NS Packet

```
Raw bytes: 00 18 04 03 A7 03 00 00

Byte 0: 0x00 -> EventID = NotificationAdded
Byte 1: 0x18 -> EventFlags = 0x08 | 0x10 = PositiveAction + NegativeAction
Byte 2: 0x04 -> CategoryID = Social
Byte 3: 0x03 -> CategoryCount = 3 active social notifications
Bytes 4-7: 0xA7 0x03 0x00 0x00 -> NotificationUID = 935 (little-endian)
```

---

## 5. Control Point Characteristic (CP)

The CP characteristic accepts **write with response** operations from the NC.
Three commands are defined:

### CommandID Values

| Value | Name |
|-------|------|
| 0 | `CommandIDGetNotificationAttributes` |
| 1 | `CommandIDGetAppAttributes` |
| 2 | `CommandIDPerformNotificationAction` |
| 3-255 | Reserved |

---

### Command 0: GetNotificationAttributes

Requests detailed attributes for a specific notification.

**Request format (written to CP):**

```
Offset  Size      Field
------  ----      -----
0       1 byte    CommandID = 0x00
1-4     4 bytes   NotificationUID (uint32, little-endian)
5+      variable  AttributeID list (see below)
```

**AttributeID list encoding:**

Some attributes require a 2-byte max-length parameter; others do not:

| AttributeID | Needs MaxLen? | Request Encoding |
|-------------|---------------|------------------|
| 0 (AppIdentifier) | No | `[0x00]` (1 byte) |
| 1 (Title) | **Yes** | `[0x01, LenLo, LenHi]` (3 bytes) |
| 2 (Subtitle) | **Yes** | `[0x02, LenLo, LenHi]` (3 bytes) |
| 3 (Message) | **Yes** | `[0x03, LenLo, LenHi]` (3 bytes) |
| 4 (MessageSize) | No | `[0x04]` (1 byte) |
| 5 (Date) | No | `[0x05]` (1 byte) |
| 6 (PositiveActionLabel) | No | `[0x06]` (1 byte) |
| 7 (NegativeActionLabel) | No | `[0x07]` (1 byte) |

The max-length is a **uint16 little-endian** value specifying the maximum number
of bytes the NC wants for that attribute. The NP will truncate if the actual value
exceeds this length.

**Example: Request Title (max 128 bytes), Message (max 512 bytes), and AppIdentifier:**

```
Byte  Value   Meaning
0     0x00    CommandID = GetNotificationAttributes
1     0xA7    NotificationUID byte 0 (UID = 935)
2     0x03    NotificationUID byte 1
3     0x00    NotificationUID byte 2
4     0x00    NotificationUID byte 3
5     0x00    AttributeID = AppIdentifier (no length param)
6     0x01    AttributeID = Title
7     0x80    MaxLen low byte = 128
8     0x00    MaxLen high byte = 0  -> max 128 bytes
9     0x03    AttributeID = Message
10    0x00    MaxLen low byte = 0
11    0x02    MaxLen high byte = 2  -> max 512 bytes
```

Total write: 12 bytes.

---

### Command 1: GetAppAttributes

Requests attributes for a specific application by its bundle identifier.

**Request format (written to CP):**

```
Offset  Size      Field
------  ----      -----
0       1 byte    CommandID = 0x01
1+      variable  AppIdentifier (NULL-terminated UTF-8 string)
N+1+    variable  AttributeID list
```

**Example: Request DisplayName for "com.apple.MobileSMS":**

```
Byte  Value   Meaning
0     0x01    CommandID = GetAppAttributes
1-20  "com.apple.MobileSMS\0"  (20 bytes including NULL terminator)
21    0x00    AttributeID = DisplayName
```

> The AppIdentifier is the iOS bundle identifier string, which is the same string
> returned as NotificationAttributeIDAppIdentifier (attribute 0) from
> GetNotificationAttributes.

---

### Command 2: PerformNotificationAction

Performs a positive or negative action on a notification.

**Request format (written to CP):**

```
Offset  Size     Field
------  ----     -----
0       1 byte   CommandID = 0x02
1-4     4 bytes  NotificationUID (uint32, little-endian)
5       1 byte   ActionID
```

**ActionID values:**

| Value | Name | Typical Meaning |
|-------|------|-----------------|
| 0 | `ActionIDPositive` | Accept call, Reply, Mark as Read, etc. |
| 1 | `ActionIDNegative` | Decline call, Dismiss, Delete, etc. |
| 2-255 | Reserved | |

**No response is generated on the Data Source** for this command, whether it
succeeds or fails. The NC can infer success if the notification is subsequently
modified or removed (via NS notifications).

**Example: Accept an incoming call (UID = 42):**

```
Byte  Value   Meaning
0     0x02    CommandID = PerformNotificationAction
1     0x2A    NotificationUID byte 0 (UID = 42)
2     0x00    NotificationUID byte 1
3     0x00    NotificationUID byte 2
4     0x00    NotificationUID byte 3
5     0x00    ActionID = Positive (accept)
```

---

## 6. Data Source Characteristic (DS)

The DS characteristic delivers responses to CP commands via **GATT notifications**.
Only `GetNotificationAttributes` (CommandID 0) and `GetAppAttributes` (CommandID 1)
generate DS responses. `PerformNotificationAction` does NOT.

### Response to GetNotificationAttributes (CommandID 0)

```
Offset  Size      Field
------  ----      -----
0       1 byte    CommandID = 0x00
1-4     4 bytes   NotificationUID (uint32, little-endian)
5+      variable  Attribute tuples (repeated)
```

### Response to GetAppAttributes (CommandID 1)

```
Offset  Size      Field
------  ----      -----
0       1 byte    CommandID = 0x01
1+      variable  AppIdentifier (NULL-terminated UTF-8 string)
N+1+    variable  Attribute tuples (repeated)
```

### Attribute Tuple Format

Each attribute in the response is encoded as a **3+ byte tuple**:

```
Offset  Size      Field
------  ----      -----
0       1 byte    AttributeID
1-2     2 bytes   AttributeLength (uint16, little-endian)
3+      N bytes   AttributeValue (UTF-8 string, NOT null-terminated)
```

- Attributes are returned in the **same order** as requested in the CP command.
- If an attribute is empty or not available, its length is `0x0000` and no value
  bytes follow.
- The value is always a UTF-8 string (even MessageSize -- it is a decimal string
  like "1234").
- The value is **NOT null-terminated**.
- If a max-length was specified in the request and the actual value is longer,
  the NP truncates to that max-length.

### Example DS Response

For a request that asked for AppIdentifier, Title (max 128), and Message (max 512):

```
Byte(s)    Value                          Meaning
0          0x00                           CommandID = GetNotificationAttributes
1-4        0xA7 0x03 0x00 0x00           NotificationUID = 935

-- Tuple 1: AppIdentifier --
5          0x00                           AttributeID = AppIdentifier
6-7        0x14 0x00                      Length = 20
8-27       "com.apple.MobileSMS"          Value (20 bytes, no null terminator)

-- Tuple 2: Title --
28         0x01                           AttributeID = Title
29-30      0x04 0x00                      Length = 4
31-34      "John"                         Value (4 bytes)

-- Tuple 3: Message --
35         0x03                           AttributeID = Message
36-37      0x0D 0x00                      Length = 13
38-50      "Hello, world!"               Value (13 bytes)
```

Total response: 51 bytes. If MTU is, say, 23 bytes (default), this is split across
3 GATT notifications (fragments).

---

## 7. Notification Attributes

### Complete NotificationAttributeID Table

| ID | Name | Has MaxLen Param | Value Format | Notes |
|----|------|------------------|--------------|-------|
| 0 | `AppIdentifier` | No | UTF-8 string | iOS bundle ID (e.g., "com.apple.MobileSMS") |
| 1 | `Title` | **Yes** (uint16) | UTF-8 string | Notification title (sender name, app name, etc.) |
| 2 | `Subtitle` | **Yes** (uint16) | UTF-8 string | Notification subtitle (if present) |
| 3 | `Message` | **Yes** (uint16) | UTF-8 string | Notification body text |
| 4 | `MessageSize` | No | UTF-8 decimal string | Total size of message in bytes as a string (e.g., "1234") |
| 5 | `Date` | No | UTF-8 timestamp string | Format: `yyyyMMdd'T'HHmmSS` (UTS #35) |
| 6 | `PositiveActionLabel` | No | UTF-8 string | Label for positive action (e.g., "Accept", "Reply") |
| 7 | `NegativeActionLabel` | No | UTF-8 string | Label for negative action (e.g., "Decline", "Clear") |
| 8-255 | Reserved | | | |

### Date Format Details

The date attribute uses Unicode Technical Standard #35 format:

```
yyyyMMdd'T'HHmmSS

yyyy = 4-digit year
MM   = 2-digit month (01-12)
dd   = 2-digit day (01-31)
T    = literal 'T' separator
HH   = 2-digit hour (00-23)
mm   = 2-digit minute (00-59)
SS   = 2-digit second (00-59)

Example: "20260301T143022" = March 1, 2026 at 14:30:22
```

The date is always exactly **15 characters** (15 bytes UTF-8).

### MessageSize vs. Message

`MessageSize` returns the **total** size of the notification message as a decimal
string. This lets the NC know how much data the full message contains, even if
the NC only requested a truncated version via the max-length parameter. Useful
for showing "... (2048 bytes)" or deciding whether to request more.

---

## 8. App Attributes

### AppAttributeID Table

| ID | Name | Value Format |
|----|------|--------------|
| 0 | `DisplayName` | UTF-8 string |
| 1-255 | Reserved | |

The `DisplayName` is the user-facing app name as displayed on the iOS home screen
(e.g., "Messages", "Mail", "WhatsApp").

### Requesting App Attributes

App attributes are requested per bundle identifier. Since many notifications may
come from the same app, the NC should **cache** app display names to avoid
repeated GetAppAttributes requests.

**Example flow:**
1. NS notification arrives with UID 42
2. NC sends GetNotificationAttributes for UID 42, requesting AppIdentifier
3. DS response includes AppIdentifier = "com.apple.MobileSMS"
4. NC checks cache: no entry for "com.apple.MobileSMS"
5. NC sends GetAppAttributes for "com.apple.MobileSMS", requesting DisplayName
6. DS response includes DisplayName = "Messages"
7. NC caches: "com.apple.MobileSMS" -> "Messages"
8. Future notifications from same app skip step 5-7

---

## 9. Action Handling

### Overview

Actions allow the NC to interact with iOS notifications -- accepting/declining
calls, replying to messages, dismissing alerts, etc.

### Availability

An NC knows actions are available by checking EventFlags in the NS notification:

- Bit 3 (`0x08`): `EventFlagPositiveAction` -- a positive action exists
- Bit 4 (`0x10`): `EventFlagNegativeAction` -- a negative action exists

### Action Labels

Before presenting action buttons to the user, the NC should retrieve the action
labels via `GetNotificationAttributes` with attribute IDs 6 and 7:

- `NotificationAttributeIDPositiveActionLabel` (ID 6) -- e.g., "Accept", "Reply"
- `NotificationAttributeIDNegativeActionLabel` (ID 7) -- e.g., "Decline", "Clear"

### Performing Actions

Write a `PerformNotificationAction` command to CP:

```
CommandID (0x02) + NotificationUID (4 bytes) + ActionID (1 byte)
```

Where:
- `ActionIDPositive = 0` -- performs the positive action
- `ActionIDNegative = 1` -- performs the negative action

### Action Semantics by Category

| Category | Positive Action | Negative Action |
|----------|-----------------|-----------------|
| IncomingCall | Accept/Answer | Decline/Reject |
| MissedCall | Call Back | Clear |
| Social (Messages) | Reply | Clear |
| Email | Mark Read / Open | Delete / Archive |
| Schedule | Accept | Decline |
| Other | Varies | Dismiss |

> The NP guarantees that actions produce results that "do not surprise the user."
> The NC should represent positive actions with affirmative visuals (green, check)
> and negative actions with dismissive visuals (red, X).

### Important Limitations

- **No response on DS:** The PerformNotificationAction command does not generate
  any data on the Data Source, whether it succeeds or fails.
- **Inference only:** The NC can infer action success by watching for a subsequent
  `EventIDNotificationModified` or `EventIDNotificationRemoved` on NS.
- **No custom replies:** ANCS does not support sending custom text replies. The
  "Reply" action typically opens a canned reply picker on the iPhone or sends a
  predefined response.

---

## 10. Fragmentation and Reassembly

### Why Fragmentation Occurs

GATT notifications are limited by the negotiated **ATT MTU** (Maximum Transmission
Unit). The default BLE ATT MTU is **23 bytes** (20 bytes payload after ATT header).
DS responses frequently exceed this.

### How the NP Fragments

When a DS response exceeds the MTU payload size, the NP splits it into multiple
consecutive GATT notifications. Each fragment is sent as a separate GATT notification
on the DS characteristic. There is **no fragment header or sequence number** -- the
NC must simply concatenate all received DS notification payloads in order.

### How the NC Reassembles

The NC must maintain a **reassembly buffer** and follow this algorithm:

```
1. Receive first DS notification after a CP write
2. Parse header: CommandID + NotificationUID (or AppIdentifier)
3. Begin accumulating attribute tuples
4. For each subsequent DS notification:
   a. Append raw bytes to the reassembly buffer
   b. Attempt to parse complete attribute tuples
5. Response is COMPLETE when all requested attribute tuples have been received
   (each tuple has: 1 byte ID + 2 bytes length + N bytes value)
```

### Determining Response Completeness

Since the NC knows which attributes it requested, it knows how many tuples to
expect. The response is complete when the buffer contains exactly that many
complete tuples (ID + Length + Value for each).

### Interleaving

The Apple spec does **not** guarantee responses arrive in command order if multiple
CP commands are issued. In practice, the NP processes commands sequentially, but
the NC should match responses by CommandID + NotificationUID (or AppIdentifier)
to be safe.

### MTU Negotiation

For better performance, the NC should request a larger MTU during connection setup.
Modern BLE supports MTU up to 517 bytes. A larger MTU means fewer fragments and
faster notification retrieval.

```
Recommended: Request MTU of 185 or 247 bytes
- 185 bytes: Common practical maximum for many BLE stacks
- 247 bytes: Maximum for BLE 4.2+ data length extension
```

---

## 11. Error Handling

### ANCS-Specific Error Codes

When a CP write fails, the NP responds with an ATT error on the write response:

| Error Code | Name | Meaning |
|------------|------|---------|
| `0xA0` | Unknown Command | The CommandID is not recognized (not 0, 1, or 2) |
| `0xA1` | Invalid Command | The command is malformed (wrong length, bad structure) |
| `0xA2` | Invalid Parameter | A referenced object does not exist (e.g., invalid NotificationUID, unknown AppIdentifier) |
| `0xA3` | Action Failed | The requested action could not be performed |

### Error Behavior

- Errors are returned as **ATT error responses** to the CP write operation.
- **No DS notification is generated** when an error occurs. The NC must not wait
  for a DS response after receiving an error on the CP write.
- The NC should handle errors gracefully:
  - `0xA2` (Invalid Parameter): The notification may have been dismissed on the
    iPhone between the time the NS event was received and the CP command was sent.
    This is a normal race condition.
  - `0xA3` (Action Failed): The action may no longer be valid (e.g., call already
    ended). Log and move on.

### Common Error Scenarios

| Scenario | Expected Error |
|----------|---------------|
| Write GetNotificationAttributes with an expired/invalid UID | `0xA2` Invalid Parameter |
| Write PerformNotificationAction on a removed notification | `0xA2` Invalid Parameter |
| Write GetAppAttributes with a nonexistent bundle ID | `0xA2` Invalid Parameter |
| Write a command with a truncated or malformed payload | `0xA1` Invalid Command |
| Write a byte that is not a valid CommandID | `0xA0` Unknown Command |
| Attempt action on notification that no longer supports it | `0xA3` Action Failed |

### Standard ATT Errors

In addition to ANCS-specific errors, the NC may encounter standard ATT errors:

| Error Code | Name | When |
|------------|------|------|
| `0x05` | Insufficient Authentication | Not yet paired/bonded |
| `0x06` | Request Not Supported | Characteristic does not support this operation |
| `0x0F` | Insufficient Encryption | Encryption required but not established |

---

## 12. Session Management

### Session Lifecycle

```
Session Start  -> NC subscribes to Notification Source (writes CCCD = 0x0001)
Session Active -> NP sends NS notifications, NC sends CP commands
Session End    -> NC unsubscribes from NS (writes CCCD = 0x0000)
                  OR NC disconnects from NP
```

### Rules

1. **All data is session-scoped.** NotificationUIDs, AppIdentifiers, and all
   attribute data are valid ONLY within the current session. When a session ends,
   the NC must discard everything.

2. **Pre-existing notifications.** When a session starts, the NP sends
   `EventIDNotificationAdded` (with `EventFlagPreExisting` set) for all currently
   active notifications on the iPhone. This lets the NC build an initial snapshot.

3. **No state persistence.** The NP does not track state across sessions. If the
   NC disconnects and reconnects, it starts fresh -- receiving all pre-existing
   notifications again.

4. **Service may disappear.** The ANCS service may be unpublished by the NP at
   any time (e.g., Bluetooth settings changed, iOS update). The NC should monitor
   the GATT **Service Changed** characteristic to detect this.

5. **Reconnection after bonding.** Since the devices are bonded, iOS will often
   automatically reconnect when the NC comes back into range. The NC should be
   prepared to re-subscribe to NS and DS upon reconnection.

---

## 13. PalmaMirror Implementation Notes

### Android-Specific Considerations

Since PalmaMirror runs on Android (Boox Palma 2), these are key implementation
considerations:

#### BLE Peripheral + GATT Client Dual Role

Android must act as:
- **BLE Peripheral** (using `BluetoothLeAdvertiser`) to advertise with service
  solicitation so the iPhone can discover and connect
- **GATT Client** (using `BluetoothGatt`) to discover ANCS service and interact
  with its characteristics

This dual role is supported on Android 5.0+ with BLE peripheral support.

#### Advertising with Service Solicitation

Use `AdvertiseData.Builder` with the service solicitation UUID:

```
Service Solicitation UUID: 7905F431-B5CE-4E99-A40F-4B1E122D00D0
```

Note: Android's `AdvertiseData.Builder.addServiceSolicitationUuid()` was added
in API level 31 (Android 12). For older versions, raw advertising data may need
to be constructed manually.

#### Pairing Flow on Android

1. Start advertising with ANCS solicitation UUID
2. iPhone user goes to Settings > Bluetooth and taps the Palma device
3. iOS initiates connection as Central
4. Android receives connection callback
5. Android discovers ANCS service via `BluetoothGatt.discoverServices()`
6. Android subscribes to NS characteristic (enable notifications via CCCD)
7. iOS may reject with insufficient authentication -- triggers pairing dialog
8. User confirms pairing on both devices
9. Bond is established -- retry subscription
10. Notifications begin flowing

#### MTU Negotiation

Call `BluetoothGatt.requestMtu(185)` (or higher) immediately after connection
to reduce fragmentation.

#### Reassembly Buffer

Maintain a per-command reassembly buffer. On each DS notification callback:
1. Append bytes to buffer
2. Parse to check if all expected tuples are complete
3. If complete, deliver the assembled response and clear the buffer

#### Caching Strategy

- Cache app display names by bundle identifier (persistent across sessions)
- Cache notification attributes only for the current session
- Clear notification cache on disconnect

#### Notification Display Mapping

| ANCS Field | PalmaMirror Display |
|------------|---------------------|
| CategoryID | Notification icon/category grouping |
| Title | Notification title line |
| Subtitle | Secondary text line (if present) |
| Message | Body text |
| AppIdentifier -> DisplayName | App name label |
| Date | Timestamp |
| PositiveActionLabel | Green action button text |
| NegativeActionLabel | Red action button text |

#### Rate Limiting

When many notifications arrive simultaneously (e.g., on first connection with
pre-existing notifications), avoid flooding the CP with GetNotificationAttributes
requests. Queue them and process sequentially, as the NP handles commands one
at a time.

---

## Appendix A: Quick Reference -- All Enumerations

### EventID
```
0 = NotificationAdded
1 = NotificationModified
2 = NotificationRemoved
```

### EventFlags (bitmask)
```
Bit 0 (0x01) = Silent
Bit 1 (0x02) = Important
Bit 2 (0x04) = PreExisting
Bit 3 (0x08) = PositiveAction
Bit 4 (0x10) = NegativeAction
```

### CategoryID
```
 0 = Other
 1 = IncomingCall
 2 = MissedCall
 3 = Voicemail
 4 = Social
 5 = Schedule
 6 = Email
 7 = News
 8 = HealthAndFitness
 9 = BusinessAndFinance
10 = Location
11 = Entertainment
```

### CommandID
```
0 = GetNotificationAttributes
1 = GetAppAttributes
2 = PerformNotificationAction
```

### NotificationAttributeID
```
0 = AppIdentifier
1 = Title            (requires uint16 max-length)
2 = Subtitle         (requires uint16 max-length)
3 = Message          (requires uint16 max-length)
4 = MessageSize
5 = Date
6 = PositiveActionLabel
7 = NegativeActionLabel
```

### AppAttributeID
```
0 = DisplayName
```

### ActionID
```
0 = Positive
1 = Negative
```

### ANCS Error Codes
```
0xA0 = Unknown Command
0xA1 = Invalid Command
0xA2 = Invalid Parameter
0xA3 = Action Failed
```

---

## Appendix B: All UUIDs

```
ANCS Service:         7905F431-B5CE-4E99-A40F-4B1E122D00D0
Notification Source:  9FBF120D-6301-42D9-8C58-25E699A21DBD
Control Point:        69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9
Data Source:          22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB
```

---

## Sources

- Apple ANCS Specification: https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html
- Apple ANCS Appendix: https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Appendix/Appendix.html
- Silicon Labs ANCS Implementation Guide: https://docs.silabs.com/bluetooth/2.13/bluetooth-code-examples-applications/apple-notification-center-service
- Texas Instruments ANCS Reference: https://github.com/TexasInstruments/ble-sdk-210-extra/blob/master/Projects/ble/ancs/README.md
- Nordic Semiconductor nRF Connect SDK ANCS Client: https://developer.nordicsemi.com/nRF_Connect_SDK/doc/latest/nrf/libraries/bluetooth_services/services/ancs_client.html
- ARM Mbed ANCS Client Header: https://github.com/ARMmbed/ble-ancs-client/blob/master/ble-ancs-client/ANCSClient.h
- Rust ANCS Library: https://docs.rs/ancs/latest/ancs/attributes/notification/enum.NotificationAttributeID.html
