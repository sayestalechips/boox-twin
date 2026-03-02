# E-Ink UI Design Best Practices

> Research compiled for the PalmaMirror project (Boox Palma 2 target device).
> Last updated: 2026-03-01

---

## 1. Contrast: Why Pure Black on White is Essential

### The Problem

E-ink displays have fundamentally different contrast characteristics than LCD or OLED:

| Display Type | Contrast Ratio | Notes |
|---|---|---|
| E Ink Vizplex | ~7:1 | First-generation commercial e-ink |
| E Ink Pearl | ~10:1 | Improved but still limited |
| E Ink Carta 1200 | ~15:1 | Current gen (Palma 2 uses this) |
| LCD | ~1000:1 | 67x more contrast than Carta |
| OLED | ~1,000,000:1 | Effectively infinite (true black) |

### Design Implications

- **Use #000000 black text on #FFFFFF white background.** Any deviation (e.g., dark gray #333333 on light gray #F0F0F0) reduces the already-thin contrast margin.
- "Close to black" and "close to white" look visibly different from pure black and pure white on e-ink. On LCD, the difference is imperceptible.
- **Never use light gray text.** What looks like "subtle" placeholder text on LCD becomes nearly invisible on e-ink.
- Progress bars, divider lines, and secondary UI elements that rely on subtle shading become invisible or muddy.
- Icons must use **solid black fills and clear outlines**, not shaded or semi-transparent styles.

### Rule of Thumb

> If a UI element would be hard to see on a printed newspaper, it will be hard to see on e-ink. Design as if you are printing on cheap newsprint.

---

## 2. Ghosting: What Causes It and How to Minimize It

### What Is Ghosting

Ghosting is the residual shadow of previously displayed content that persists after a screen update. It appears as faint "burned in" text or shapes from the prior screen state.

### Root Cause

E-ink works by moving charged pigment particles (black and white microcapsules) using electric fields. When a partial refresh is performed:

1. Only the changed pixels receive voltage pulses.
2. Particles in unchanged areas stay put, but **neighboring particles can drift slightly**.
3. Over multiple partial refreshes, accumulated drift creates visible artifacts.
4. Dark-to-light transitions are particularly prone to ghosting because black particles don't fully return to their resting position.

### Waveform Tables

Each refresh uses a **waveform lookup table** -- a calibrated sequence of voltage pulses that controls particle movement. Different content types (text, UI, images) require different waveforms. When the waveform doesn't match the content, particles shift unevenly, leaving visual noise.

### UI Design Strategies to Minimize Ghosting

1. **Avoid persistent dark regions next to frequently changing content.** A black navigation bar that never changes while the content area updates constantly will cause ghosting at the boundary.

2. **Minimize large filled rectangles.** Borders-only buttons ghost less than filled buttons because there is less pigment to drift.

3. **Use borders over fills.** An outlined card (`border: 2px solid black`) ghosts far less than a filled card (`background: #000`).

4. **Avoid fixed overlays.** Navigation bars, floating action buttons, and persistent toolbars that stay while content scrolls underneath them build up ghosting in that fixed region.

5. **Trigger periodic full refreshes.** Every 5-10 page turns or UI interactions, perform a full GC16 refresh (the screen will briefly flash black-white-black) to clear accumulated ghosting. This is standard practice on all e-readers.

6. **Prefer page transitions over incremental updates.** Replacing the entire screen content at once is cleaner than updating small regions repeatedly.

7. **Keep layouts simple.** Complex overlapping elements and layered designs create more ghosting vectors.

---

## 3. Refresh Modes: When to Use Each

### Available Modes (Boox Devices)

| Mode | Technical Name | Speed | Quality | Grayscale | Use Case |
|---|---|---|---|---|---|
| **Normal** | GC16 | ~450ms | Excellent | 16-level | Static content, reading, final display states |
| **Speed** | GLR16/Regal | ~250ms | Good | 16-level | Scrollable content with mixed text/images |
| **A2** | A2 | ~120ms | Poor | 2-level (B&W only) | Fast scrolling, typing, interactive UI |
| **X Mode** | DU | ~80ms | Very poor | Binary | Video, rapid animation, games |

### Decision Framework for PalmaMirror

```
Is the content static (displaying received data)?
  YES -> Use Normal (GC16) mode
    - Best text clarity
    - Minimal ghosting
    - User is reading, not interacting

Is the user actively scrolling or navigating?
  YES -> Use Speed mode
    - Balanced clarity and responsiveness
    - Acceptable ghosting level

Is the user typing or performing rapid interactions?
  YES -> Use A2 mode
    - Fast enough for keyboard input
    - Quality is acceptable for transient states

Does the UI need real-time responsiveness?
  YES -> Use X Mode (sparingly)
    - Only for brief interactive moments
    - Return to Normal mode when interaction ends
```

### Full Refresh Scheduling

- Insert a **full GC16 refresh every 5-10 partial refreshes** to clear ghosting.
- On the Boox Palma 2, this can be configured via `EpdController` in the Onyx SDK or through the system E-Ink Center per-app settings.
- The full refresh causes a visible black-to-white flash (~0.5s). Users expect this on e-ink devices -- it is not a bug.

---

## 4. Typography: Best Fonts and Sizes

### Font Selection Criteria for E-Ink

E-ink at 300 PPI renders text well, but the low contrast ratio and lack of sub-pixel rendering (no RGB sub-pixels -- e-ink pixels are monochrome) mean font choice matters more than on LCD.

### Recommended Font Families

| Font | Type | Why It Works on E-Ink |
|---|---|---|
| **Roboto** | Sans-serif | Android system font. Clean, open letterforms. Excellent at all sizes. |
| **Noto Sans** | Sans-serif | Wide language support. Clear at small sizes. |
| **Literata** | Serif | Designed specifically for e-reading. High x-height, open counters. |
| **Source Sans Pro** | Sans-serif | Adobe's open-source. Crisp strokes, good weight range. |
| **Open Sans** | Sans-serif | Highly readable, friendly. Performs well on low-contrast displays. |
| **Merriweather** | Serif | Designed for screens. Thick strokes render clearly on e-ink. |

### Font Characteristics to Prioritize

- **High x-height**: Larger lowercase letters relative to capitals improve readability at small sizes.
- **Open counters**: Letters like 'a', 'e', 'g' with larger enclosed spaces are more legible.
- **Even stroke width**: Avoid fonts with extreme thick-thin contrast (like Didot or Bodoni). The thin strokes can disappear at the 15:1 contrast ratio.
- **Medium to bold weight**: Regular weight is fine, but **avoid Thin or Light weights**. They become invisible on e-ink.

### Font Sizes

| Element | Minimum Size | Recommended Size |
|---|---|---|
| Body text | 14sp | 16-18sp |
| Secondary text | 12sp | 14sp |
| Headings | 20sp | 24-28sp |
| Touch button labels | 14sp | 16sp |
| Small captions | 10sp (absolute minimum) | 12sp |

### Anti-Aliasing Note

E-ink displays render anti-aliased text using their 16 gray levels. At 300 PPI, this is generally sufficient for smooth text rendering. However, at very small sizes (<10sp), the limited grayscale makes anti-aliasing visibly blocky. Stick to 12sp+ for all user-facing text.

---

## 5. Color Avoidance: Why Grays Look Muddy

### The Grayscale Reality

The Boox Palma 2 (standard model) has a **B&W e-ink display with 16 levels of gray**. This means:

- **Pure black** (#000000) = gray level 0
- **Pure white** (#FFFFFF) = gray level 15
- **Mid-gray** (#808080) = gray level ~7-8
- Everything in between maps to one of 16 discrete steps.

### Why Grays Are Problematic

1. **Dithering artifacts**: When the system maps a continuous gradient to 16 levels, visible banding appears.
2. **Inconsistent rendering**: Gray level 7 on one device may appear slightly different on another due to waveform calibration, temperature, and panel age.
3. **Ghosting amplification**: Gray regions ghost more than pure black or pure white because the pigment particles are in a partially-shifted state that's harder to reset.
4. **Contrast collapse**: A UI that uses gray (#888888) text on a light gray (#DDDDDD) background might have only 2-3 gray levels of difference, making it nearly unreadable.

### Design Rules

- **Use only black and white for UI elements.** Black text, white backgrounds, black borders, white fill.
- **If you must use gray**, restrict to a maximum of 3-4 distinct gray values with clear visual separation (e.g., #000000, #555555, #AAAAAA, #FFFFFF).
- **Never use color values.** They will be converted to their grayscale luminance equivalent, and the results are often unexpected. A vibrant red (#FF0000) becomes a medium gray that's nearly indistinguishable from a muted green (#669966).
- **Icons should be solid black with transparent/white backgrounds.** No colored icons, no gradient fills.
- **Shadows, glows, and elevation effects are useless.** Material Design's shadow-based elevation system does not translate to e-ink. Use borders instead.

### Android Color Conversion

When the e-ink panel renders standard Android UI colors:
```
Red (#FF0000)      -> Gray level ~5  (dark gray)
Green (#00FF00)    -> Gray level ~11 (light gray)
Blue (#0000FF)     -> Gray level ~2  (very dark gray)
Yellow (#FFFF00)   -> Gray level ~14 (near white)
Orange (#FF8800)   -> Gray level ~9  (medium gray)
```

These mappings make most color-coded information indistinguishable. Never rely on color to convey meaning.

---

## 6. Animation: Why Animations Are Harmful on E-Ink

### The Core Problem

E-ink refresh is fundamentally slow:

- **Normal mode**: ~450ms per frame = ~2 FPS
- **A2 mode**: ~120ms per frame = ~8 FPS
- **X mode**: ~80ms per frame = ~12 FPS

For comparison, smooth animation on LCD requires 60 FPS (16ms per frame). E-ink is **5-30x slower**.

### What Happens When Animations Play on E-Ink

1. **Ghosting accumulates rapidly.** Each intermediate frame of an animation leaves a ghost that compounds with the next frame.
2. **Frames are skipped.** The display cannot keep up, so intermediate states are either dropped or overlaid on each other, creating a smeared mess.
3. **Full refreshes interrupt.** If the system triggers a ghosting-clearing refresh during an animation, the screen flashes black, destroying the visual continuity.
4. **Battery drain.** Each refresh consumes power. An animation running at even 8 FPS means 480 refreshes per minute, dramatically increasing power consumption compared to a static display.

### What to Eliminate

- **Transition animations**: No slide-in, fade-in, or scale animations between screens.
- **Loading spinners**: Replace with static "Loading..." text or a progress bar that updates in discrete steps.
- **Scroll momentum**: Disable kinetic/fling scrolling. Use paginated navigation or discrete scroll steps.
- **Ripple effects**: Material Design's touch ripple is useless on e-ink. Use a simple border highlight or invert (black background, white text) on press.
- **Animated progress bars**: Replace with segmented/stepped indicators (e.g., [===---] rather than a smooth sliding bar).
- **Floating action button animations**: No morphing, no expanding. Static button with pressed state only.
- **Slide-out menus/drawers**: Replace with full-screen menu pages.

### Acceptable "Animations"

- **Page flip**: Replace the entire screen content at once. This is effectively a single-frame "animation" and works well on e-ink.
- **State toggle**: Button switches from outlined to filled (or vice versa) on tap. Two states, no intermediate frames.
- **Cursor blink**: In text fields, a blinking cursor is acceptable in A2 mode because it's a tiny region updating.

---

## 7. Touch Targets: Minimum Sizes for E-Ink

### Why E-Ink Needs Larger Touch Targets

1. **Display latency**: 120-450ms between tap and visual feedback means users cannot quickly confirm their tap landed correctly. Larger targets reduce mis-taps.
2. **No hover state**: There is no hover feedback to guide the finger before tapping.
3. **Capacitive touch on glass**: ONYX Glass adds a layer between finger and display, which can slightly reduce touch precision.
4. **Older user demographic**: E-reader users skew older, with potentially reduced fine motor control.

### Size Guidelines

| Element | Material Design Minimum | E-Ink Recommended | PalmaMirror Minimum |
|---|---|---|---|
| Primary buttons | 48dp x 48dp | 56dp x 48dp | 56dp x 48dp |
| List items | 48dp height | 56dp height | 56dp height |
| Icon buttons | 48dp x 48dp | 52dp x 52dp | 52dp x 52dp |
| Text links | 48dp touch area | 48dp touch area | 48dp touch area |
| Spacing between targets | 8dp | 12dp | 12dp |

### Touch Feedback

Since animation-based feedback (ripple, scale) does not work on e-ink:

- **Inversion feedback**: On tap, invert the button colors (black background, white text). On release, revert. This is a single-frame update that e-ink handles well.
- **Border thickening**: Increase border width from 2dp to 4dp on press.
- **Checkmark/state indicator**: For toggles, show a clear state change (empty box -> box with X mark).

### The 1cm Rule

Research by Parhi, Karlson, and Bederson found that a minimum touch target of **1cm x 1cm** (approximately 40dp on a 300 PPI screen) enables quick and accurate tapping. For e-ink, err on the side of larger.

---

## 8. Layout Patterns: What Works and What Does Not

### What Works Well on E-Ink

#### Simple Vertical Lists
```
+------------------------------------------+
| > Item Title                          [>] |
+------------------------------------------+
| > Item Title                          [>] |
+------------------------------------------+
| > Item Title                          [>] |
+------------------------------------------+
```
- Clear separation with 2px black divider lines.
- Large touch targets (full row is tappable).
- Minimal grayscale usage.
- Easy to scan visually.

#### Card Layouts (Border-Only)
```
+------------------------------------------+
|  Title Text                               |
|  Description line here                    |
|  [Action]                                 |
+------------------------------------------+
```
- Use **black borders** to define cards, not shadow elevation.
- White background inside cards.
- Adequate padding (16dp minimum).
- Stack cards vertically with 12dp spacing.

#### Paginated Content
```
+------------------------------------------+
|                                           |
|    Content fills the page                 |
|    No scrolling needed                    |
|                                           |
|                                           |
+------------------------------------------+
|  [< Prev]              Page 3/10  [Next >]|
+------------------------------------------+
```
- **Pagination over scrolling** is the single most important layout decision for e-ink.
- Scrolling causes rapid partial refreshes and ghosting.
- Pagination causes a single full refresh per page turn -- clean and predictable.

#### Large Text with Ample Whitespace
- Headlines at 24-28sp.
- Body text at 16-18sp.
- Line spacing at 1.4-1.6x.
- Generous margins (24dp+).

#### Status Displays
```
+------------------------------------------+
|  CONNECTION         [ACTIVE]              |
|  Last sync:         12:34 PM             |
|  Battery:           ████░░░░ 52%         |
+------------------------------------------+
```
- Key-value pairs with clear labels.
- Segmented battery indicators (discrete blocks, not smooth gradients).
- Timestamp-based updates rather than real-time counters.

### What Does NOT Work on E-Ink

| Pattern | Problem | Alternative |
|---|---|---|
| **Smooth scrolling** | Ghosting, smearing, refresh flicker | Paginated navigation |
| **Tab bars with indicators** | Animated slide indicator ghosts | Static tab labels, full page swap |
| **Drop shadows / elevation** | Invisible or muddy at 16 gray levels | Black borders |
| **Gradient backgrounds** | Visible banding, 16-level dithering | Solid white backgrounds |
| **Small icons (< 24dp)** | Blurry, indistinct at low contrast | Larger icons (32dp+) or text labels |
| **Complex grid layouts** | Too much visual noise for low contrast | Simple vertical lists |
| **Floating dialogs** | Ghost when dismissed, overlay artifacts | Full-screen dialogs / new pages |
| **Bottom sheets** | Slide animation, partial overlay | Full-screen settings page |
| **Image-heavy layouts** | Gray muddiness, slow rendering | Text-primary layouts |
| **Carousel / swipe galleries** | Continuous swipe motion ghosts | Grid with pagination |
| **Live-updating content** | Constant partial refreshes burn battery, ghost | Timed updates (pull-to-refresh or interval) |

---

## 9. Android View vs Jetpack Compose for E-Ink

### Jetpack Compose

**Pros for E-Ink:**
- Declarative state management reduces unnecessary redraws. Only composables whose state changes are recomposed.
- Easier to implement custom drawing (Canvas API) for e-ink-optimized widgets.
- Simpler to build static, pagination-based UIs.
- Modern lifecycle integration with ViewModel and StateFlow.
- Compose's `LaunchedEffect` and `snapshotFlow` allow precise control over when UI updates trigger.

**Cons for E-Ink:**
- Compose's animation APIs (`animate*AsState`, `AnimatedVisibility`) are easy to accidentally use, and they degrade badly on e-ink.
- Default Material3 theme uses elevation shadows, ripple effects, and color theming -- all of which must be overridden for e-ink.
- Compose recomposition can trigger more frequent view updates than necessary if state management is not careful.
- The Boox `onyxsdk-device` SDK was built for the View system. Integration with Compose may require `AndroidView` interop wrappers for `EpdController` calls.

### Android Views (XML)

**Pros for E-Ink:**
- Direct, well-understood rendering model. Each view draws once and holds.
- The Boox SDK (`onyxsdk-device`, `EpdController`) was designed for Views. No interop layer needed.
- Easier to avoid accidental animations (XML layouts are inherently static).
- RecyclerView with pagination is well-proven for e-ink list content.
- `invalidate()` gives explicit control over when a view redraws.

**Cons for E-Ink:**
- More verbose code for state management.
- Layout inflations can trigger unnecessary redraws.
- Harder to build truly custom rendering logic compared to Compose Canvas.
- Boilerplate-heavy for complex UI state.

### Recommendation for PalmaMirror

**Use Jetpack Compose with e-ink discipline:**

1. Create a custom **e-ink Material theme** with:
   - No ripple effects (override `LocalRippleTheme`).
   - No elevation/shadows (all `elevation = 0.dp`).
   - Pure black/white color scheme only.
   - No animated transitions.

2. Use `AndroidView` interop for Boox SDK integration:
   ```kotlin
   AndroidView(
       factory = { context ->
           // Create a standard View that calls EpdController
       }
   )
   ```

3. Disable all implicit animations:
   ```kotlin
   // Instead of AnimatedVisibility
   if (showContent) {
       ContentComposable()
   }

   // Instead of animate*AsState
   val color = if (isSelected) Color.Black else Color.White
   ```

4. Control refresh modes programmatically:
   - Switch to A2 mode during active user interaction.
   - Switch to Normal mode when displaying final content.
   - Trigger full refresh after page transitions.

Compose's modern architecture and state management outweigh the minor SDK interop cost, especially since PalmaMirror's UI is relatively simple (status display + BLE pairing + content mirror).

---

## 10. Real-World Examples: E-Ink Apps That Do It Well

### Kindle Reading App (Amazon)

**What it does right:**
- Pure black text on white background, no exceptions.
- Pagination, not scrolling, for book content.
- Minimal UI chrome -- the content fills the screen.
- Progress indicator uses simple text ("Page 42 of 300" or "Location 1234") rather than animated bars.
- Font selection and size adjustment built-in, respecting e-ink readability needs.
- Full refresh triggered every N page turns (user-configurable on some devices).

**What it does wrong:**
- Store/library screens use image covers that render muddy on B&W e-ink.
- Some UI transitions have subtle fade animations that ghost.

### Boox Built-In Apps (NeoReader, Library, Notes)

**What they do right:**
- Deep integration with the E-Ink Center for per-screen refresh mode optimization.
- NeoReader uses separate refresh modes for reading (Normal) vs. menu interaction (Speed).
- Notes app uses A2 mode during active drawing for responsiveness, then re-renders in Normal mode for clarity.
- Library uses a simple grid/list toggle with clear black borders.
- Settings screens use full-height list items with clear dividers.

**What they do wrong:**
- Some transitions between apps cause visible ghosting due to different refresh mode states.
- The notification shade uses semi-transparent overlay that ghosts.

### KOReader (Open Source E-Reader)

**What it does right:**
- Designed specifically for e-ink from the ground up.
- Custom UI framework built for pagination and static rendering.
- Contrast and font settings are first-class features.
- No animations anywhere in the interface.
- Keyboard interaction uses A2 mode automatically.
- Highly configurable refresh settings (full refresh every N pages).

### Light Phone II

**What it does right:**
- Extremely minimal interface -- large text, maximum whitespace.
- Binary design decisions: something is visible or it's not. No gray areas (literally).
- Touch targets are enormous (nearly full-screen width for list items).
- No images, no gradients, no decoration.

### TRMNL (E-Ink Dashboard Device)

**What it does right:**
- Designed for "set it and forget it" information display.
- Updates on a schedule (not real-time), triggering one clean full refresh per update cycle.
- Content is pre-rendered as optimized 1-bit or 4-bit images.
- Web-based content pipeline allows server-side optimization before sending to the e-ink device.

### eink-ui CSS Component Library

**What it does right:**
- 46+ UI components explicitly designed for e-ink constraints.
- Zero JavaScript -- purely CSS, which means no accidental animations or transitions.
- Prioritizes **borders over fills, whitespace over color, clarity over decoration**.
- Three themes optimized for different e-ink display types.
- Demonstrates that complex, functional UIs can be built within e-ink constraints.

---

## Summary: PalmaMirror Design Principles

Based on this research, PalmaMirror should follow these core principles:

1. **Black and white only.** No grays unless absolutely necessary, no colors ever.
2. **Pagination over scrolling.** Navigate between discrete screens, not continuous scroll.
3. **Zero animations.** State changes are instantaneous frame swaps.
4. **Large touch targets.** Minimum 48dp, prefer 56dp for primary actions.
5. **Borders, not fills.** Define visual hierarchy with outlines and whitespace, not shadows or fills.
6. **Full refresh periodically.** Clear ghosting every 5-10 interactions.
7. **Content-first layout.** Minimal chrome, maximum content area.
8. **Normal mode for display, A2 for interaction.** Switch refresh modes based on user activity state.
9. **Test on real hardware.** E-ink rendering cannot be simulated on LCD emulators.
10. **Respect the medium.** E-ink is not a slow LCD. It's a different display technology that excels at static, high-contrast, text-primary content.

---

## Sources

- [WithIntent: How to Design for E-Ink Devices](https://www.withintent.com/blog/e-ink-design/)
- [Kevin Lynagh: Designing Apps for the E Ink Kindle](https://kevinlynagh.com/kindle-games/)
- [eink-ui Component Library](https://eink-components.dev/)
- [Viwoods: E Ink Ghosting Decoded](https://viwoods.com/blogs/paper-tablet/e-ink-ghosting-explained)
- [BOOX Medium: Optimize Ghosting on Color E Ink Screen](https://medium.com/boox-content-hub/how-to-optimize-ghosting-on-color-e-ink-screen-fa0b9b77a171)
- [Visionect: Why Does ePaper Blink?](https://www.visionect.com/blog/why-epaper-blinks/)
- [BOOX Help Center: Refresh Modes](https://help.boox.com/hc/en-us/articles/8569262708372-Refresh-Modes)
- [Good e-Reader: 256 Levels of Grayscale](https://goodereader.com/blog/electronic-readers/e-reader-companies-are-super-charging-their-products-with-256-levels-of-grayscale)
- [Good e-Reader: Challenges for E-Reader Apps](https://goodereader.com/blog/electronic-readers/these-are-the-challenges-for-developing-apps-for-an-android-e-reader)
- [MobileRead Wiki: E Ink Display](https://wiki.mobileread.com/wiki/E_Ink_display)
- [MobileRead Forums: Fonts for E-Ink Readability](https://www.mobileread.com/forums/showthread.php?t=366520)
- [NNGroup: Touch Target Size](https://www.nngroup.com/articles/touch-target-size/)
- [Smashing Magazine: Accessible Tap Target Sizes](https://www.smashingmagazine.com/2023/04/accessible-tap-target-sizes-rage-taps-clicks/)
- [Android Developers: BLE Overview](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Android Developers: Compose vs View Metrics](https://developer.android.com/develop/ui/compose/migrate/compare-metrics)
- [Hacker News: E-Ink Contrast Ratio Discussion](https://news.ycombinator.com/item?id=22832452)
- [Android Police: E-Ink Friendly Apps](https://www.androidpolice.com/top-e-ink-friendly-apps-for-android-e-reader/)
