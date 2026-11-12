# Project Master Context: Professional Android Camera with Advanced AR & Cinematic Filters

## 1. Project Overview & Vision
This project transforms an existing Jetpack CameraX demonstration application (`com.arindam.camerax`) into a commercial-grade, high-performance Android camera application matching the quality, responsiveness, and interactive experience of industry leaders such as Snapchat and Instagram.

The application combines:
- Professional photographic and videographic controls (manual exposure, ISO, shutter priority, focus, RAW/DNG, Ultra HDR).
- Real-time GPU-accelerated cinematic color grading shaders.
- Advanced real-time face tracking and expression landmarking via Google MediaPipe Face Landmarker (478 3D landmarks + blendshapes).
- Real-time facial beauty enhancements (bilateral skin smoothing, eye brightening, skin tone tuning).
- 2D/3D augmented reality (AR) face attachments (glasses, animal ears, masks, crowns, beards, makeup) following head pose and facial expressions.
- Full-pipeline filter rendering for live preview, high-resolution photo captures, and synchronized video recordings.

---

## 2. Technical Stack Baseline
- **Language**: Kotlin 2.4.10 (built-in Kotlin compiler via AGP 9.1.1).
- **Target OS / Toolchain**: 
  - `minSdk`: 23 (Android 6.0 Marshmallow) — target MediaPipe features gracefully gate or scale on API 24+.
  - `targetSdk` / `compileSdk`: 37 (Android 16).
  - Java Toolchain: JDK 21 (Temurin).
  - Gradle: 9.3.1.
  - Android Gradle Plugin (AGP): 9.1.1.
- **UI Framework**: Jetpack Compose with Material Design 3 (M3) and custom glassmorphism viewfinder overlays.
- **Camera Core**: AndroidX CameraX 1.6.2 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-video`, `camera-view`, `camera-extensions`, `featurecombinationquery`).
- **Vision & Face Tracking**: Google MediaPipe Face Landmarker (`com.google.mediapipe:tasks-vision`).
- **Graphics Pipeline**: OpenGL ES 2.0 / 3.0 via EGL and GLSurfaceView / SurfaceTexture / CameraX `Preview.SurfaceProvider`.
- **Concurrency & Architecture**: Kotlin Coroutines & `StateFlow` / `SharedFlow`, Clean Architecture (Presentation `ui` -> Domain `domain` <- Data `data`).

---

## 3. Strict Operating Rules & Constraints
1. **Zero Mock Data Policy**: No simulated sensors, no fake filters, no dummy UI affordances. Every UI button and mode represents active, verifiable functionality.
2. **Architecture Preservation**: Clean Architecture boundaries must be respected. The UI layer communicates only with domain use cases (`CameraInteractors`), never directly with CameraX or raw OpenGL contexts.
3. **Decoupled Filter Engine**: The Filter Processing Engine (`filter` module / domain contract) must operate independently of CameraX, receiving raw video frames/textures and returning processed frames.
4. **End-to-End Pipeline**: Filters must not be mere visual overlays on top of the viewfinder; they must be baked directly into captured stills (JPEG/HEIC) and encoded video frames (MP4).
5. **Security & Extension Safety**: Filter assets are developer-authored only. No dynamic executable bytecode loading (`.dex`, `.jar`, `.so`). External packages must be strictly structured declarative bundles (textures, shaders, 3D glTF/OBJ models, metadata) validated against cryptographic hashes.
