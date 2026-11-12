# Architecture Specification: Professional Android Camera & Filter Engine

## 1. Clean Architecture Overview

The application follows strict Clean Architecture principles with unidirectional data flow:

```
┌────────────────────────────────────────────────────────┐
│                   Presentation (UI)                    │
│   - Jetpack Compose (CameraScreen, CameraChrome)       │
│   - Filter Carousel (Snapchat-style bottom selector)   │
│   - Settings & Gallery Screens                         │
│   - CameraViewModel & FilterViewModel                  │
└──────────────────────────┬─────────────────────────────┘
                           │ Calls
                           ▼
┌────────────────────────────────────────────────────────┐
│                      Domain                            │
│   - Models (CameraMode, FilterProfile, FaceLandmarks)  │
│   - Repository Interfaces (CameraRepository, FilterEngine)
│   - Interactors & UseCases (ApplyFilter, BindCamera)   │
└──────────────────────────▲─────────────────────────────┘
                           │ Implements
┌──────────────────────────┴─────────────────────────────┐
│                       Data                             │
│   - CameraSession (CameraX 1.6.2 lifecycle & use cases)│
│   - OpenGlFilterEngine (EGL, FBOs, GLSL Shaders)       │
│   - MediaPipeFaceTracker (Tasks Vision Face Landmarker)│
│   - MediaStorePublisher & FileMediaRepository          │
└────────────────────────────────────────────────────────┘
```

---

## 2. Analysis of the Existing Legacy Effects System

### Current Implementation:
- **Location**: `ColorEffectAnalyzer.kt`, `ColorEffects.kt`, `CameraSession.kt`.
- **Pipeline**:
  1. `ImageAnalysis` receives `ImageProxy` (~1280x720 YUV_420_888).
  2. Converts `ImageProxy` to Android software `Bitmap` on CPU.
  3. Allocates a new `Bitmap` on *every single frame* (`Bitmap.createBitmap(upright.width, upright.height, ...)`).
  4. Executes `Canvas.drawBitmap` with `ColorMatrixColorFilter` on CPU.
  5. Emits the `Bitmap` to a `StateFlow<Bitmap?>`.
  6. Jetpack Compose renders this Bitmap using `Image(bitmap = effectFrame)`.

### Critical Architecture Flaws:
1. **Zero Video Filtering**: Video recording in `Effects` mode completely bypasses `ColorEffectAnalyzer`. `VideoCapture` records raw camera feed directly from the sensor surface without any filter applied.
2. **Severe CPU & Memory Churn**: Creating 30 Bitmaps per second at 720p consumes >100 MB/s of allocation and triggers frequent Stop-The-World Garbage Collection (GC) pauses, causing thermal throttling and dropped frames.
3. **Absence of GPU Shading**: Modern color grading (LUTs, curve mapping, vignette, split-toning) requires GPU fragment shaders, impossible in software `ColorMatrix`.
4. **No AR or Face Tracking Foundation**: Lacks any facial mesh detection, blendshape computation, or 3D coordinate space transformation.

---

## 3. Target Filter Engine Architecture (Decoupled & GPU-Accelerated)

To achieve Snapchat-like real-time performance (60 FPS on mid-range devices), the Filter Engine is completely decoupled from CameraX into a standalone subsystem:

```
                  ┌────────────────────────┐
                  │    CameraX Engine      │
                  │ (Preview, Video, Pic)  │
                  └───────────┬────────────┘
                              │
               Camera Surface │ Frame Stream (YUV / OES)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  GPU Filter Processing Engine                   │
│                                                                 │
│   ┌───────────────────────┐         ┌────────────────────────┐  │
│   │   MediaPipe Vision    │         │  OpenGL ES 2.0 / 3.0   │  │
│   │ Face Landmarker (CPU) │         │  Multi-Pass FBO Pipeline│ │
│   │  478 3D Landmarks     │         │                        │  │
│   │  Blendshapes / Pose   │         │  Pass 1: OES to RGB    │  │
│   └───────────┬───────────┘         │  Pass 2: Color Shaders │  │
│               │                     │  Pass 3: Beauty Bilat  │  │
│               │ Face Mesh Matrices  │  Pass 4: 2D/3D AR Mesh │  │
│               └────────────────────►│                        │  │
│                                     └───────────┬────────────┘  │
└─────────────────────────────────────────────────┼───────────────┘
                                                  │
                         Multiplexed Target Output│
                ┌─────────────────────────────────┼──────────────────────────────┐
                ▼                                 ▼                              ▼
      ┌──────────────────┐              ┌──────────────────┐           ┌──────────────────┐
      │ Viewfinder (EGL) │              │ Video Encoder    │           │ Still Image      │
      │ SurfaceView / GL │              │ MediaCodec / MP4 │           │ High-Res JPEG    │
      └──────────────────┘              └──────────────────┘           └──────────────────┘
```

### Core Architecture Components:

1. **`FilterEngine` (Domain Interface)**:
   - Contract defining filter selection, parameter tweaking (intensity, skin smooth factor), face landmark input, and frame dispatching.
   - Independent of Android UI and CameraX classes.

2. **`OpenGlFilterRenderer` (Data Layer)**:
   - Manages dedicated EGL display, context, and surfaces on a background render thread.
   - Operates a ping-pong Framebuffer Object (FBO) pipeline:
     - **Pass 1 (OES Conversion)**: Samples `GL_TEXTURE_EXTERNAL_OES` from camera `SurfaceTexture`.
     - **Pass 2 (Color LUT / Grading)**: Applies GLSL shaders for Cinematic, Vintage, Golden Hour, B&W, Film, Warm, Cool, etc.
     - **Pass 3 (Beauty / Bilateral Filter)**: Performs edge-preserving bilateral filtering to smooth skin textures while retaining sharp eyes and hair edges.
     - **Pass 4 (AR Elements & 3D Projection)**: Renders 2D sprites and 3D textured meshes (glasses, animal ears, crowns) transformed by 3D head rotation and translation matrices derived from MediaPipe landmarks.

3. **`FaceTrackingPipeline` (Data Layer)**:
   - Integrates Google MediaPipe Face Landmarker.
   - Consumes frames from CameraX `ImageAnalysis` (using `STRATEGY_KEEP_ONLY_LATEST` to avoid lag).
   - Extracts 478 3D face landmarks and 52 facial blendshapes in real time.
   - Dispatches tracking coordinates to `FilterEngine` with sub-millisecond thread handoff.

4. **Synchronized Output Dispatcher**:
   - For **Preview**: Renders directly onto a `SurfaceView` or `TextureView`.
   - For **Video**: Renders identical filtered frames into an input `Surface` configured on `MediaCodec` / `VideoCapture` with exact timestamps (`EGL14.eglPresentationTimeANDROID`).
   - For **Photo Capture**: Renders the high-resolution still capture buffer through the identical shader stack with full floating-point precision.
