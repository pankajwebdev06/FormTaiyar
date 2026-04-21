# FormTaiyar — Sarkari Form Photo Maker

**100% Offline Android App** for cropping and compressing government form photos (PAN, Aadhaar, Passport, SSC).

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34 (API Level 34)
- Gradle 8.5
- JDK 17+

### Steps to Build APK

1. **Open in Android Studio**
   ```
   File → Open → Select the FormTaiyar/ folder
   ```

2. **Sync Gradle**
   - Android Studio will auto-sync. If not: `File → Sync Project with Gradle Files`

3. **Build Release APK** (under 8MB with ProGuard):
   ```
   Build → Generate Signed Bundle/APK → APK → Release
   ```
   Or via terminal:
   ```bash
   ./gradlew assembleRelease
   ```

4. **Build Debug APK** (for testing):
   ```bash
   ./gradlew assembleDebug
   ```

APK output: `app/build/outputs/apk/release/app-release.apk`

---

## Architecture

```
app/src/main/
├── java/com/formtaiyar/app/
│   ├── MainActivity.kt       — Home dashboard with 4 template cards
│   ├── CropActivity.kt       — Crop → Quality Slider → Preview → Save/Share
│   ├── PhotoTemplate.kt      — Template data (exact 2026 gov specs)
│   └── ImageProcessor.kt     — Offline JPEG compression using Bitmap.compress()
├── res/
│   ├── layout/
│   │   ├── activity_main.xml — 2×2 card grid dashboard
│   │   └── activity_crop.xml — Crop + preview + save flow
│   ├── values/
│   │   ├── strings.xml       — Hinglish UI labels
│   │   ├── colors.xml        — High-contrast White/Blue palette
│   │   └── themes.xml        — Material3 theme
│   └── drawable/             — All vector icons (no heavy PNGs)
└── AndroidManifest.xml       — Minimal permissions
```

---

## Template Specs (2026)

| Template | Dimensions | Size (px) | Max Size | Aspect |
|---|---|---|---|---|
| PAN Card | 35×25mm | 213×213px | 300KB | 1:1 |
| Aadhaar / Passport | 35×45mm | 630×810px | 100KB | 7:9 |
| SSC / State Exam | 45×35mm | 472×378px | 50KB | ~4:3 |
| Custom | User-defined | User-defined | 500KB | Free |

---

## Key Libraries

| Library | Purpose | Size Impact |
|---|---|---|
| `simplecropview:1.1.8` | Lightweight crop view | ~180KB |
| `androidx.exifinterface` | EXIF rotation correction | ~95KB |
| `material:1.11.0` | Material3 UI components | ~1.8MB |
| `constraintlayout:2.1.4` | Layouts | ~380KB |

**No cloud, no database, no analytics, no ads.**

---

## Features

- **4 Template Cards**: PAN, Aadhaar/Passport, SSC/State Exam, Custom
- **Gallery + Camera** picker with permission handling
- **Auto-crop overlay** with correct aspect ratio per template
- **Quality Slider**: Binary-search compression to hit exact KB target
- **EXIF rotation fix**: Portrait photos from camera auto-corrected
- **Watermark**: "Made with FormTaiyar - Bina internet ke photo sahi karein"
- **Save to Gallery**: Saved in `Pictures/FormTaiyar/` folder
- **Share**: Native Android share sheet with watermark text

---

## Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" /> <!-- API < 33 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /> <!-- API < 29 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />     <!-- API 33+ -->
```

---

## Localization (Hinglish Buttons)

| Button | Hinglish Label |
|---|---|
| Pick from gallery | Gallery Se Chunein |
| Take from camera | Camera Se Lein |
| Crop the photo | Photo Sahi Karein |
| Crop again | Dobara Crop Karein |
| Save to phone | Phone Me Save Karein |
| Share | Share Karein |

---

## APK Size Optimization

ProGuard rules enabled in `release` build:
- `minifyEnabled true` — removes unused code
- `shrinkResources true` — removes unused resources
- Result: estimated **4–6MB** final APK (well under 8MB target)

---

*Made with FormTaiyar — Bina internet ke photo sahi karein*
