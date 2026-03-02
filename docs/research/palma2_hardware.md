# Boox Palma 2 Hardware Research

> Research compiled for the PalmaMirror project.
> Last updated: 2026-03-01

---

## 1. Display Specifications

| Property | Value |
|---|---|
| Screen Size | 6.13 inches (diagonal) |
| Technology | E Ink Carta Plus (Carta 1200) |
| Resolution | 1648 x 824 pixels |
| Aspect Ratio | 2:1 |
| Pixel Density | 300 PPI (301 PPI per PhoneArena) |
| Touch | Capacitive touch control |
| Front Light | MOON Light 2 (warm + cold LED, independent control) |
| Glass | ONYX Glass (flush with body, anti-glare coating) |

### Display Technology Notes

- **Carta 1200** provides 15% more contrast and 20% faster screen refreshing compared to the older Carta 1000 generation.
- The display is **grayscale only** (B&W model). The Palma 2 Pro variant adds Kaleido 3 color, but the standard Palma 2 is monochrome.
- Contrast ratio for E Ink Carta is approximately **15:1** (up from 10:1 on Pearl and 7:1 on Vizplex). For comparison, LCD panels achieve ~1000:1.
- Grayscale depth: 16 levels of gray is the standard for Carta displays. Some newer E Ink controllers support 256 levels in theory, but practical rendering typically uses 16.
- Front light uses **DC power supply (Flicker-Free)** to avoid LED shimmering and reduce eye fatigue.

### Refresh Rates

E-ink does not have a traditional "Hz" refresh rate like LCDs. Instead, refresh is measured by mode:

| Mode | Speed | Quality | Use Case |
|---|---|---|---|
| Normal | Slowest (~450ms) | Best clarity, minimal ghosting | Reading books, static content |
| Speed (Regal) | Faster (~250ms) | Good clarity, some ghosting | News, magazines, mixed text/images |
| A2 | Fast (~120ms) | Degraded quality, noticeable ghosting | Scrolling, browsing |
| X Mode | Fastest | Significant detail loss | Video playback, rapid interaction |

BOOX Super Refresh (BSR) technology is a proprietary optimization layer that improves refresh speed across all modes.

---

## 2. Processor

| Property | Value |
|---|---|
| Brand | Qualcomm |
| Architecture | Octa-core (64-bit ARM) |
| Likely Model | Snapdragon 662 or 690 (unconfirmed by Boox) |
| Performance Tier | Mid-range mobile |
| Process Node | 11nm (SD662) or 8nm (SD690) |
| GPU | Adreno 610 (SD662) or Adreno 619 (SD690) |

### Notes

- Boox has not officially disclosed the exact chipset model. Teardowns and benchmarks suggest it is a **Snapdragon 662 or 690**, upgraded from the original Palma's Snapdragon 460.
- The Palma 2 Pro uses a higher-tier **Snapdragon 750G**.
- For an e-ink device, this processor is significantly overpowered relative to display demands, meaning BLE operations, background services, and app logic will have minimal CPU overhead.
- All Boox devices use Qualcomm chipsets, which makes rooting predictable (standard Qualcomm bootloader unlock flow).

---

## 3. Bluetooth

| Property | Value |
|---|---|
| Version | Bluetooth 5.0 (some sources report 5.1) |
| BLE Support | Yes (Bluetooth Low Energy included in BT 5.0+) |
| Confirmed Profiles | A2DP (Advanced Audio Distribution) |
| Expected BLE Profiles | GATT (Generic Attribute), GAP (Generic Access) |

### BLE Capabilities (Bluetooth 5.0)

- **Data rate**: Up to 2 Mbps (LE 2M PHY) -- more than sufficient for text data transfer.
- **Range**: Up to ~240m outdoors (LE Coded PHY), ~40m typical indoor.
- **Advertising extensions**: Larger advertising packets (up to 255 bytes).
- **Multiple advertisements**: Can advertise on multiple channels simultaneously.
- **Connection events**: Supports multiple simultaneous BLE connections.

### BLE Power Consumption Expectations

- **Active BLE connection**: 5-30 mA during data transfer bursts.
- **Idle connected state**: 100-300 uA average (depending on connection interval).
- **With 75ms connection interval and peripheral latency of 5**: ~140 uA average.
- BLE is fundamentally designed for intermittent, low-duty-cycle communication -- ideal for periodic data sync between paired devices.
- On the 3950 mAh battery, a BLE-heavy app should have negligible battery impact if connection intervals are managed properly (e.g., 500ms+ intervals during idle).

### Android BLE API

- BLE is fully supported on Android 13 via `android.bluetooth.le` package.
- Key classes: `BluetoothLeScanner`, `BluetoothGatt`, `BluetoothGattServer`, `BluetoothGattCharacteristic`.
- Android's BLE stack supports both Central (client) and Peripheral (server) roles.

---

## 4. RAM / Storage

| Property | Value |
|---|---|
| RAM | 6 GB LPDDR4X |
| Internal Storage | 128 GB |
| Expandable Storage | microSDXC card slot |

### Notes

- 6 GB RAM is generous for an e-ink device. Most e-readers ship with 2-3 GB.
- The microSD slot allows significant storage expansion, useful for offline content.
- No cloud storage dependency required -- aligns well with offline-first architecture.

---

## 5. Android Version

| Property | Value |
|---|---|
| Android Version | Android 13 |
| Boox UI Layer | BOOX OS (custom launcher and system apps) |
| Google Play Store | Available (requires activation in settings) |

### Boox Customizations

- **Custom Launcher**: BOOX ships its own home screen launcher optimized for e-ink. Users can replace it with third-party launchers (Nova, Niagara, Smart Launcher 6), though switching back can sometimes be finicky.
- **E-Ink Center (EinkWise)**: System-level app that controls per-app refresh mode, contrast, and DPI settings.
- **App Optimization Settings**: Per-app controls for refresh mode, color enhancement, contrast boost, and active/freeze states.
- **Navigation Ball**: Floating navigation widget optimized for e-ink interaction.
- **Google Play Services**: Available and functional. The Palma 2 is Google-certified, meaning Play Services, Play Store, and Google account sync all work. This is a significant difference from many e-ink devices that lack GMS certification.
- **Developer Options**: Accessible via standard Android method (tap Build Number 7 times in Settings). May require "Android Hidden Settings" app to access some system settings that Boox hides.

---

## 6. Boox-Specific APIs and SDK

### Official SDK: OnyxAndroidDemo

- **Repository**: https://github.com/onyx-intl/OnyxAndroidDemo
- **Status**: 206 stars, 45 forks, 240 commits (actively maintained)

### SDK Components

| SDK Package | Version | Purpose |
|---|---|---|
| `onyxsdk-device` | 1.1.11 | Device control, display management, refresh modes |
| `onyxsdk-pen` | 1.2.1 | Stylus/pen input, inking |
| `onyxsdk-scribble` | varies | Advanced drawing (depends on DBFlow) |

### Key API Classes

- **`EpdController`**: Core class for controlling the e-ink display. Handles refresh operations, update modes, and scribble integration.
- **`EpdDeviceManager`**: Device-level EPD management.
- **`FrontLightController`**: Programmatic control of screen brightness (warm/cold LEDs).
- **`DeviceUtils`**: Utility methods including `setFullScreenOnResume()` for full-screen mode.
- **`DeviceEnvironment`**: Access to internal and SD card storage paths.
- **`DictionaryUtil`**: Dictionary query integration.
- **`TouchHelper`**: Simplified pen/stylus input API with multi-view support.

### Integration (build.gradle)

```gradle
repositories {
    maven { url "https://jitpack.io" }
    maven { url "http://repo.boox.com/repository/maven-public/" }
}

dependencies {
    implementation 'com.onyx.android.sdk:onyxsdk-device:1.1.11'
    implementation 'com.onyx.android.sdk:onyxsdk-pen:1.2.1'
}
```

### E-Ink Refresh Mode Control

The SDK exposes refresh mode control through `EpdController` with documented update modes:

- **GC16 (Normal)**: Full 16-level grayscale update. Highest quality, slowest.
- **DU (Direct Update)**: Binary (black/white only) update. Fast, no grayscale.
- **A2**: Fast 2-bit update. Speed priority over quality.
- **GC4**: 4-level grayscale, balanced speed/quality.
- **REGAL**: Proprietary mode reducing ghosting artifacts during partial refreshes.

Developers can set refresh mode per-view or per-activity. The `RefreshModeDemoActivity` in the SDK repository demonstrates the API usage.

### Neo Reader SDK

- NeoReader is Boox's built-in reading app supporting PDF, EPUB, DJVU, CBZ, and more.
- **Third-party integration is limited**: NeoReader supports dictionary app integration (since V3.0 firmware) via Android Intent system.
- NeoReader does NOT expose a public SDK for embedding or controlling reading functionality.
- For reading features, developers should use their own rendering or integrate open-source alternatives (e.g., KOReader, MuPDF).

---

## 7. Battery

| Property | Value |
|---|---|
| Capacity | 3,950 mAh |
| Type | Li-ion Polymer |
| Charging | USB Type-C 2.0 |

### Power Budget Analysis for BLE App

- **E-ink display**: Near-zero power when static (bi-stable, holds image without power).
- **E-ink refresh**: ~40-100 mW per refresh event (varies by mode and area).
- **BLE active**: 5-30 mA bursts during data transfer (~15-100 mW at 3.3V).
- **BLE idle connected**: ~0.5-1 mW average.
- **CPU (idle with BLE service)**: ~50-100 mW.
- **Estimated standby with BLE connection**: 3950 mAh / ~5 mA average = ~790 hours (~33 days).
- **Estimated active use with frequent BLE sync**: Several days of typical use before recharge.

E-ink's bi-stable nature means the display contributes almost nothing to power consumption between refreshes, making BLE communication the dominant power consumer in a mirror-display app.

---

## 8. Physical Dimensions

| Property | Value |
|---|---|
| Height | 159 mm (6.26 in) |
| Width | 80 mm (3.15 in) |
| Thickness | 8.0 mm (0.31 in) |
| Weight | 170 g (6.0 oz) |
| Screen-to-Body Ratio | ~75% (estimated) |

### Form Factor Notes

- The device is **phone-sized** -- comparable to a tall, narrow smartphone (similar to an iPhone 15 Pro in height, slightly narrower).
- One-handed operation is feasible due to the narrow 80mm width.
- The 2:1 aspect ratio screen fills most of the front face. ONYX Glass is flush with the body, minimizing visible bezel on the sides.
- The bottom bezel houses the fingerprint sensor (embedded in power button, side-mounted).
- There is no front-facing camera or speaker grille consuming front-face space.

---

## 9. Known Developer Considerations

### Platform Constraints

1. **Google Play Services**: Available and functional (Google-certified device). This is NOT a limitation on the Palma 2, unlike many other e-ink devices.
2. **Custom Launcher**: Boox ships its own launcher. While replaceable, some settings routes assume the Boox launcher is active.
3. **E-Ink Center overrides**: Boox's E-Ink Center can override per-app refresh settings at the system level. Your app's programmatic refresh mode requests may be overridden by user's system-level settings.
4. **No cellular modem**: The standard Palma 2 is WiFi + Bluetooth only. The Palma 2 Pro adds 5G cellular.
5. **Camera**: Has a 16MP rear camera (unusual for an e-reader). No front camera.
6. **Firmware updates on rooted devices**: OTA incremental updates will NOT install on rooted devices. Full firmware flashing is required.
7. **Android 13 target SDK**: Apps should target API level 33 (Android 13) for full compatibility.

### Development Tips

- **Always test refresh modes on-device**. Emulators cannot replicate e-ink rendering behavior.
- Use the `onyxsdk-device` library to programmatically control refresh mode for optimal UX.
- Prefer **partial refresh** for small UI updates (e.g., updating a single text field) and **full refresh** periodically to clear ghosting.
- The device supports **standard Android BLE APIs** -- no Boox-specific BLE wrappers needed.
- Developer Options + USB debugging work normally once enabled.
- ADB over WiFi is supported for wireless debugging.
- The 6 GB RAM means aggressive app killing by Android's memory manager is unlikely, but the app should still handle lifecycle events properly.

### Rooting (Optional)

- Qualcomm bootloader unlock is straightforward on Boox devices.
- Rooting enables deeper system control but breaks OTA updates.
- For PalmaMirror, rooting should NOT be required -- standard Android APIs + Boox SDK should suffice.

---

## Sources

- [BOOX Official Product Page](https://shop.boox.com/products/palma2)
- [ONYX BOOX Palma 2 Specs](https://onyxboox.com/boox_palma2)
- [PhoneArena Full Specifications](https://www.phonearena.com/phones/BOOX-Palma-2_id12700)
- [NotebookCheck Launch Coverage](https://www.notebookcheck.net/BOOX-Palma-2-launches-as-a-moderate-refresh-with-a-few-new-features-including-a-fingerprint-sensor.906048.0.html)
- [9to5Google Announcement](https://9to5google.com/2024/10/22/boox-palma-2/)
- [GSMArena Palma 2 Coverage](https://www.gsmarena.com/boox_palma_2_adds_faster_chipset_android_13_and_a_fingerprint_scanner_-news-65034.php)
- [OnyxAndroidDemo SDK](https://github.com/onyx-intl/OnyxAndroidDemo)
- [BOOX Help Center - Refresh Modes](https://help.boox.com/hc/en-us/articles/8569262708372-Refresh-Modes)
- [B&H Photo Specs](https://www.bhphotovideo.com/c/product/1858969-REG/boox_opc1230r_6_13_palma_2_tablet.html)
- [Good e-Reader - Grayscale Levels](https://goodereader.com/blog/electronic-readers/e-reader-companies-are-super-charging-their-products-with-256-levels-of-grayscale)
- [MobileRead Wiki - E Ink Display](https://wiki.mobileread.com/wiki/E_Ink_display)
