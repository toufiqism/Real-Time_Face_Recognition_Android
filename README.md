# Real Time Face Recognition App using TfLite

A minimalistic Face Recognition module which can be easily incorporated in any Android project.

## [Playstore Link](https://play.google.com/store/apps/details?id=com.atharvakale.facerecognition)

## Key Features 
- Fast and very accurate.
- No re-training required to add new Faces.
- Save Recognitions for further use.
- Real-Time and offline.
- Simple UI.

## Onboarding Experience
- Brand-new login screen modeled after the DMP Duty Distribution mockups keeps the crest (`dmp_logo.jpg`), typography, and spacing faithful to the design while using Material text fields for accessibility.
- A dedicated **Go to Face Recognition** button sits right beneath the Login button so power users can jump straight into the camera workflow without entering credentials.
- Login inputs now have comfortable horizontal padding and a softer hint color so labels remain legible without clashing with borders or background.

## Modern UI Theme
- The entire app now uses a consistent **DMP teal** (`#00766C`) brand color across all screens.
- **Material Toolbar** added to the main activity for a modern look with the app title.
- Camera preview and face preview are now wrapped in **CardView** containers with rounded corners and elevation for a polished appearance.
- All buttons use **MaterialButton** with consistent styling (filled primary, outlined secondary).
- Status bar color matches the brand theme for a cohesive experience.

## In-app Help
- The main face-recognition screen includes a floating **instructions** action button in the bottom-right corner.
- Tapping this FAB opens a dialog that shows the same step-by-step guidance text that is bound to `textView2`, so you can keep inline hints while also providing a focused popup for users who need help.

## Tools and Frameworks used:
- Android Studio (Kotlin)
- CameraX
- ML Kit
- TensorFlow Lite

## Debug Logging
- All critical data objects now emit structured logs through Android `Log` (tag: `FaceRecognition`).
- Logged values cover camera frames, detected faces, embedding vector size/sample values, recognition distances, thresholds, and persistence actions (save/load/clear).
- Use Android Studio Logcat and filter by `FaceRecognition` to inspect the runtime data flow and confirm which data types are used at every stage.
- Toggle `Developer Mode` from the in-app `Actions` menu to additionally stream the full embedding arrays for the active face and every stored recognition; the output is chunked so the complete vectors appear sequentially in Logcat.

## Model 
- MobileFaceNet : [Research Paper](https://arxiv.org/ftp/arxiv/papers/1804/1804.07573.pdf)
- [Implementation](https://github.com/sirius-ai/MobileFaceNet_TF)

## Installation

Use Import from Version Control in Android Studio or Clone repo and open the project in Android Studio.

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



# Action Items
- [ ] Improve Performance(Code Optimization)
- [ ] Auto face orientation for Import Photo Action.
- [ ] iOS application

