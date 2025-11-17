# Real-Time Face Recognition (Android) — Technical Documentation

## 1. System Overview
- **Purpose**: Provide on-device, real-time facial recognition that works fully offline by combining ML Kit face detection with a TensorFlow Lite embedding model.
- **Supported Platforms**: Android (minSdk 21, targetSdk 36). No iOS implementation included—see §11 for guidance.
- **Design Principles**: Single Source of Truth (SSOT) for face embeddings via in-memory registry + SharedPreferences persistence, unidirectional data flow from camera → detection → embedding → matching, SOLID-aligned separation of concerns.

## 2. High-Level Architecture
- **UI Layer (`SplashScreen`, `MainActivity`)**
  - `SplashScreen` animates the Lottie intro and routes to `MainActivity`.
  - `MainActivity` hosts the full recognition workflow: camera preview (`PreviewView`), action controls, and recognition status.
- **Domain Layer**
  - `SimilarityClassifier.Recognition`: Immutable carrier for model outputs + metadata.
  - Recognition registry: `HashMap<String, Recognition>` storing embeddings keyed by display names.
- **ML/Camera Pipeline**
  - CameraX `ImageAnalysis` streams `ImageProxy` frames to ML Kit face detector (`FaceDetection` API).
  - Cropped face bitmaps are normalized and passed to TensorFlow Lite `Interpreter` (MobileFaceNet) producing 192-d embeddings.
  - `findNearest` computes Euclidean distance to stored embeddings, enforcing SSOT through shared registry + distance threshold.
- **Persistence & Configuration**
  - Embeddings serialized via Gson into SharedPreferences (`HashMap` namespace) for save/load.
  - Hyperparameters (distance threshold) persisted separately (`Distance` namespace).

## 3. Data Flow
1. **Camera Feed**: CameraX `Preview` + `ImageAnalysis` produce rotation-aware frames.
2. **Face Detection**: ML Kit identifies bounding boxes (high-accuracy mode).
3. **Pre-processing**: Frames converted (`Image` → NV21 → `Bitmap`), rotated, cropped, scaled (112×112).
4. **Embedding Generation**: TensorFlow Lite interpreter generates normalized vector.
5. **Similarity Search**: `findNearest` compares embedding against SSOT registry; applies Euclidean distance threshold.
6. **UI Update**: Recognition label, diagnostics, and preview updated based on mode (`Recognize` vs `Add Face`).
7. **Persistence** (optional): Actions dialog allows saving/loading/clearing registry, maintaining SSOT symmetry between runtime and storage.

## 4. Key Components
- `MainActivity.kt`
  - CameraX binding (`cameraBind`, `bindPreview`), analyzer loop, permission handling.
  - Embedding lifecycle: `recognizeImage`, `findNearest`, `addFace`, `logEmbeddingArray`.
  - User actions: recognition list dialogs, hyperparameter editor, developer mode toggles.
  - External interactions: gallery import (`loadphoto`) with same preprocessing path as live camera.
- `SimilarityClassifier.kt`
  - Defines recognition DTO maintaining immutability for SSOT and future adapter patterns.
- `SplashScreen.kt`
  - Simple launch delay handler to display the Lottie animation.
- Assets & Resources
  - `assets/mobile_face_net.tflite`: MobileFaceNet model (192-d output).
  - Layouts (`activity_main.xml`, `activity_splash_screen.xml`) provide camera surface, controls, diagnostics.

## 5. Dependencies
- **CameraX** (`androidx.camera:camera-* 1.2.0-alpha04`): Camera preview + analysis. If updating, align versions to stable release for API parity.
- **ML Kit Face Detection** (`com.google.mlkit:face-detection:16.1.5`): On-device face bounding boxes.
- **TensorFlow Lite** (`tensorflow-lite`, support, task-vision 0.3.0`): Embedding inference.
- **Gson** (`2.8.9`): Persistence serialization.
- **Lottie** (`4.2.2`): Splash animation.
- **AndroidX UI stack**: AppCompat, Material, ConstraintLayout, Navigation (currently unused in UI).
- **Note on iOS**: None of these Android libraries are cross-platform; for iOS parity use `AVFoundation` + `Vision` (face detection) and a converted TFLite/Metal model—see §11.

## 6. Permissions & Privacy
- **Camera**: Declared in manifest; runtime request handled in `MainActivity`.
- **Storage (Import Photo)**: Uses SAF (`ACTION_GET_CONTENT`) so no legacy storage permission required.
- **Privacy**: Embeddings persist locally via SharedPreferences; no network IO. Document storage scope when integrating into larger apps.

## 7. Error Handling & Null Safety
- All external interactions guard against null/empty states:
  - SharedPreferences lookups default to empty maps.
  - Embedding conversions handle null buffers and type coercion.
  - Gallery import gracefully toasts on failure and resets flow.
- **Best Practices**: When extending, uphold:
  - Null-check imported bitmaps (`getBitmapFromUri` throws if decoding fails).
  - Validate recognition names to prevent collisions.
  - Wrap asynchronous ML tasks with explicit failure listeners.

## 8. Configuration & Hyperparameters
- **Distance Threshold**: Controls recognition acceptance. Editable via Actions → Hyperparameters; stored under `Distance/distance`.
- **Developer Mode**: Actions → Developer Mode toggles verbose logging of embedding vectors and nearest-neighbour diagnostics.
- **Camera Facing**: Default back camera; `camera_switch` toggles with horizontal flip to maintain orientation integrity.

## 9. Logging & Diagnostics
- Log tag `FaceRecognition` centralizes structured logging (setup, camera binding, embedding sample dumps, persistence lifecycle).
- Developer mode splits large embeddings into manageable chunks (<=3k chars) to avoid Logcat truncation.
- Recommendation: Integrate Crashlytics or structured logging when embedding in production apps.

## 10. Build, Run & Testing
- **Prerequisites**: Android Studio (Giraffe+), Android SDK 36, Gradle wrapper (`./gradlew`).
- **Build Steps**:
  1. `./gradlew clean assembleDebug`
  2. Install generated APK (`app/build/outputs/apk/debug/`).
  3. Grant camera permission on first run.
- **Testing Strategy**:
  - Unit tests minimal (`app/src/test` placeholder). Add instrumentation tests for permission flows, persistence, and camera analyzer (with Robolectric or CameraX testing library).
  - Manual QA: verify recognition accuracy across lighting conditions, import flow, threshold adjustments.
- **Performance**: Model executes on CPU; to enable GPU delegates (if required), configure `Interpreter.Options` with delegate support (ensure delegate availability on device).

## 11. Extensibility & Cross-Platform Notes
- **Adding New Storage Backends**: Extract persistence logic into repository class (implements interface) to maintain SOLID `Single Responsibility`. Ensure registry remains SSOT by funnelling all mutations through a single data source.
- **Multi-Face Support**: Currently processes first detected face. To support multi-face, iterate over `faces` list and track recognition results in immutable list before rendering.
- **UI Scaling**: Layout uses fixed `dp` dimensions. For responsive design across screen sizes, migrate to constraints with `0dp` width/height and `ConstraintSet` guidelines; consider Compose for adaptive UI.
- **iOS Considerations**: Reuse MobileFaceNet by exporting to Core ML / TFLite for iOS, pair with `VNDetectFaceRectanglesRequest` (Vision) and Metal delegate; maintain consistent embedding distance threshold to keep SSOT parity across platforms.

## 12. Known Limitations & Future Work
- CameraX version is alpha; upgrade to stable release to reduce runtime quirks.
- No biometric liveness detection; susceptible to spoofing via photos.
- SharedPreferences persistence is not encrypted. For production, integrate `EncryptedSharedPreferences` or secure storage.
- TODOs (see README Action Items): performance optimization, face orientation correction for imports, iOS parity.

## 13. Repository Structure (Key Paths)
```
app/
  ├── src/main/java/com/atharvakale/facerecognition/
  │     ├── MainActivity.kt
  │     ├── SimilarityClassifier.kt
  │     └── SplashScreen.kt
  ├── src/main/assets/mobile_face_net.tflite
  ├── src/main/res/layout/activity_main.xml
  └── src/main/res/layout/activity_splash_screen.xml
demo/  # animated GIFs for README usage section
```

## 14. Maintenance Checklist
- Keep ML Kit & CameraX dependencies aligned; mixed versions can break analyzer pipeline.
- Validate TFLite model compatibility (input 112×112 RGB, output 192 floats) prior to swapping models.
- Before releases, verify threshold tuning and reconstructions on representative dataset.
- Update documentation (README + this file) whenever feature surfaces or configurations change to preserve SSOT for knowledge.


