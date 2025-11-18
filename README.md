# Real Time Face Recognition App using TfLite

A minimalistic Face Recognition module which can be easily incorporated in any Android project.

## [Playstore Link](https://play.google.com/store/apps/details?id=com.atharvakale.facerecognition)

## Key Features 
- Fast and very accurate.
- No re-training required to add new Faces.
- Save Recognitions for further use.
- Real-Time and offline.
- Simple UI.

## Tools and Frameworks used:
- Android Studio (Kotlin)
- **Jetpack Compose** - Modern declarative UI framework
- **Material Design 3** - Latest Material Design system
- CameraX - Camera integration
- ML Kit - Face detection
- TensorFlow Lite - Face recognition model
- Lottie - Animation library

## Debug Logging
- All critical data objects now emit structured logs through Android `Log` (tag: `FaceRecognition`).
- Logged values cover camera frames, detected faces, embedding vector size/sample values, recognition distances, thresholds, and persistence actions (save/load/clear).
- Use Android Studio Logcat and filter by `FaceRecognition` to inspect the runtime data flow and confirm which data types are used at every stage.
- Toggle `Developer Mode` from the in-app `Actions` menu to additionally stream the full embedding arrays for the active face and every stored recognition; the output is chunked so the complete vectors appear sequentially in Logcat.

## Model 
- MobileFaceNet : [Research Paper](https://arxiv.org/ftp/arxiv/papers/1804/1804.07573.pdf)
- [Implementation](https://github.com/sirius-ai/MobileFaceNet_TF)

## Architecture

This project has been migrated to **Jetpack Compose**, providing a modern, declarative UI framework. Key architectural improvements:

- **Compose-based UI**: All activities use Jetpack Compose for UI rendering
- **Material Design 3**: Modern Material 3 theming with dark mode support
- **State Management**: Reactive state management using Compose state
- **Camera Integration**: CameraX PreviewView integrated via AndroidView wrapper
- **Compose Dialogs**: All dialogs converted to Compose AlertDialog components
- **Activity Result Launchers**: Modern permission and image selection handling

### Migration Details

The app has been fully migrated from XML layouts to Jetpack Compose:
- `SplashScreen`: Converted to Compose with Lottie animation
- `MainActivity`: Complete Compose migration with all UI components and dialogs
- All business logic preserved (face detection, recognition, TensorFlow Lite integration)
- Camera functionality maintained with Compose integration

## Kotlin Multiplatform Setup

The project now follows a Kotlin Multiplatform (KMP) structure to keep business logic shared while UI stays per-platform.

### Module layout
- `shared/`: Multiplatform library
  - `commonMain/` – platform-agnostic contracts (`FaceRecognitionController`, `FaceRepository`, `RecognitionSettingsRepository`, `PermissionsManager`, `FaceAnalyzer`, `RecognitionSettings`, etc.) implemented with coroutines + StateFlow for SSOT.
  - `androidMain/` – Android actuals (`SharedPreferencesFaceRepository`, `SharedPreferencesRecognitionSettingsRepository`, `AndroidPermissionsManager`, `AndroidSharedModule`, stub `provideFaceAnalyzer`) that reuse existing ML Kit + CameraX stacks.
  - `iosMain/` – placeholder actuals so iOS builds can be enabled later without breaking compilation.
- `app/`: Android UI module (Jetpack Compose) now depends on `:shared` and delegates persistence/settings/permission orchestration to the shared controller exposed by `AndroidSharedModule.controller(activity)`.

### Shared state orchestration
- `FaceRecognitionController` exposes `state: StateFlow<FaceRegistryState>` and `events: SharedFlow<FaceRegistryEvent)` for a unidirectional data flow across Compose screens.
- Domain use cases encapsulate storage + settings mutations (`RegisterFaceUseCase`, `LoadFacesUseCase`, `ReplaceFacesUseCase`, `UpdateSettingsUseCase`, etc.).
- Android UI interacts with the controller via helper functions (`persistRecognitions`, `loadRecognitions`, `updateDistanceThreshold`, `setDeveloperMode`) which keeps Compose state and the shared repository in sync.

### Building / verifying
```bash
./gradlew shared:assemble    # build shared module (android + stubs for iOS)
./gradlew app:assembleDebug  # build Android app with shared logic
```
Set `kotlin.native.ignoreDisabledTargets=true` (already in `gradle.properties`) on non-macOS hosts to silence iOS warnings.

### Future iOS work
To finish iOS support, implement the pending actuals inside `shared/src/iosMain/`:
- `provideFaceAnalyzer` backed by Vision or TensorFlowLiteSwift and ensure ML models are embedded as xcassets.
- A `FaceRepository` implementation powered by `NSUserDefaults`/CoreData for parity with Android `SharedPreferences`.
- A `RecognitionSettingsRepository` that surfaces camera/performance flags across different screen sizes (consider SwiftUI or Compose Multiplatform UI layers).
- Camera permission management via `AVAuthorizationStatus` and gracefully handling multi-orientation previews.

Compose screens are already responsive, but when building iOS/large-screen variants ensure layout modifiers use `Modifier.fillMaxSize()` plus adaptive paddings so both phone/tablet breakpoints remain consistent.

## Installation

Use Import from Version Control in Android Studio or Clone repo and open the project in Android Studio.

**Requirements:**
- Android Studio Hedgehog (2023.1.1) or later
- Kotlin 2.2.0 or later
- Gradle 8.10.1 or later
- Minimum SDK: 21 (Android 5.0)
- Target SDK: 36

```bash
git clone https://github.com/atharvakale31/Face_Recognition_Android.git
```

### Application file : [Face_Recognition.apk](https://drive.google.com/file/d/1ggOo4acHOodrdCP2MkfUv4DJlL_VDZH4/view?usp=sharing)

## Usage
<table>
  <tr>
    <td><b>1.Add Face</b></td>
     <td><b>2.Import Face</b></td>
     <td><b>3.Recognize Face</b></td>
     
  </tr>
  <tr>
    <td><img src="demo/add_face.gif" width=270 height=480></td>
  <td><img src="demo/import photo.gif" width=270 height=480></td>
    <td><img src="demo/recognize_face.gif" width=270 height=480></td>
  
  </tr>
 </table>
 

 
 <table>
  <tr>
    <td><b>Actions</b></td>
     <td><b>View Recognitions</b></td>
     <td><b>Update Recognitions</b></td>
  </tr>
  <tr>
    <td><img src="demo/actions.jpeg" width=270 height=480></td>
    <td><img src="demo/view_reco.jpeg" width=270 height=480></td>
    <td><img src="demo/update_reco.jpeg" width=270 height=480></td>
  </tr>
 </table>
 
## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.



## Development Notes

### Compose Migration
The project has been successfully migrated to Jetpack Compose. Key changes:
- All XML layouts replaced with Compose composables
- Material Design 3 theming implemented
- State management using Compose state
- Modern permission handling with Activity Result Launchers
- CameraX integration maintained through AndroidView wrapper

### Building the Project
1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on a device or emulator with API 21+

### Dependencies
Key Compose dependencies:
- `androidx.compose:compose-bom:2024.02.00`
- `androidx.compose.material3:material3`
- `androidx.activity:activity-compose:1.8.2`
- `androidx.camera:camera-viewfinder:1.3.1`
- `com.airbnb.android:lottie-compose:6.1.0`

# Action Items
- [x] Migrate to Jetpack Compose
- [ ] Improve Performance(Code Optimization)
- [ ] Auto face orientation for Import Photo Action.
- [ ] iOS application

