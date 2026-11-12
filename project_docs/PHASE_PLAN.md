# Phase Plan: Step-by-Step Implementation Roadmap

## Overview & Progression Policy
To ensure zero regressions, clean architecture, and commercial-grade stability, implementation is divided into discrete sequential phases. Each phase concludes with compilation verification and user approval before proceeding to the next.

---

## Phase 0: Inspection, Architectural Baseline & Documentation [CURRENT]
- [x] Deep inspection of all codebase files, Gradle setup, CameraX use cases, and permissions.
- [x] Fix initial build issues (Gradle wrapper 9.3.1, AGP 9.1.1, Java 21, debug signing).
- [x] Create comprehensive architecture and project documentation in `project_docs/`.
- [x] Generate detailed inspection report for user review and approval.

---

## Phase 1: Decoupled Filter Engine Core (OpenGL ES & CameraX Bridge)
- **Objective**: Establish the high-performance GPU rendering foundation.
- **Tasks**:
  1. Create `OpenGlFilterEngine` managing EGLDisplay, EGLContext, and background render thread (`GLThread`).
  2. Implement CameraX `CameraEffect` or custom `SurfaceProvider` to pipe camera frames (`GL_TEXTURE_EXTERNAL_OES`) into the OpenGL pipeline.
  3. Implement ping-pong Framebuffer Objects (FBO) for multi-pass shader chaining.
  4. Build the domain interface `FilterRepository` and interactor use cases in `domain/`.
  5. Verify that raw camera feed renders smoothly at 60 FPS without memory allocations.

---

## Phase 2: Cinematic Real-Time Color Filter Shaders
- **Objective**: Implement 11 real-time cinematic color grading shaders on GPU.
- **Tasks**:
  1. Write GLSL fragment shaders:
     - `cinematic.frag`, `vintage.frag`, `film.frag`, `bw.frag`, `warm.frag`, `cold.frag`, `golden_hour.frag`, `portrait.frag`, `natural.frag`, `night.frag`, `retro.frag`.
  2. Add uniform bindings for filter intensity (`0.0f` to `1.0f`).
  3. Add domain filter models in `FilterModels.kt`.
  4. Create filter selection logic in `CameraViewModel` with zero frame-drop switching.
  5. Test real-time preview color grading.

---

## Phase 3: MediaPipe Face Landmarker Integration
- **Objective**: Real-time facial mesh and head pose tracking.
- **Tasks**:
  1. Add Google MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) to `libs.versions.toml` and `app/build.gradle.kts`.
  2. Download official MediaPipe Face Landmarker bundle (`face_landmarker.task`) to `assets/`.
  3. Implement `MediaPipeFaceTracker` running on `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`.
  4. Extract 478 3D landmarks, 52 facial blendshapes, and compute 6DoF head pose transformation matrix (Pitch, Yaw, Roll, Translation).
  5. Transfer tracking matrices to the OpenGL render thread with thread-safe lock-free buffers.

---

## Phase 4: Face Beauty & 2D/3D AR Filter Modules
- **Objective**: Implement skin smoothing and facial AR attachments.
- **Tasks**:
  1. **Beauty Shader**: Bilateral filtering shader for skin smoothing, tone adjustment, and lighting enhancement with configurable intensity.
  2. **2D Facial Contours**: High-precision makeup blending (lips, eyes, blush) mapped to landmark UVs.
  3. **3D Mesh Rendering**: OpenGL ES 3D rendering pipeline for glasses, animal ears, crowns, masks, and beards anchored to landmark indices (nose bridge, forehead, temples, jaw).
  4. **Expression Triggers**: Blendshape monitoring (e.g. `mouthOpen`, `eyeBlink`) activating dynamic particle animations.

---

## Phase 5: Filtered Capture & Synchronized Video Recording
- **Objective**: Bake all active filters directly into saved photos and videos.
- **Tasks**:
  1. **Video Recording**:
     - Connect the GPU filter output to `VideoCapture` / `MediaCodec` input Surface.
     - Maintain pristine audio/video synchronization and presentation timestamps.
  2. **Photo Capture**:
     - Render full-resolution still frame through the exact active shader stack.
     - Save output as JPEG/HEIC with correct EXIF orientation and front mirror tags.
  3. Validate MediaStore publishing to `DCIM/CameraX`.

---

## Phase 6: Snapchat-Style UI/UX & Interactive Carousel
- **Objective**: Premium interactive user experience.
- **Tasks**:
  1. Implement horizontal animated Filter Carousel above the shutter button.
  2. Add filter preview badges and smooth swipe/snap gestures.
  3. Add vertical/horizontal glassmorphic intensity sliders for beauty and color grading.
  4. Add Favorites system allowing users to bookmark preferred filters.

---

## Phase 7: Declarative Filter Packaging, Low-Power Mode & Play Store Polish
- **Objective**: Commercial production readiness.
- **Tasks**:
  1. Implement declarative JSON package parser with SHA-256 integrity validation.
  2. Implement Low-Power Mode for entry-level devices (reduces FBO resolution, skips secondary beauty passes).
  3. Memory leak audits, strict ProGuard/R8 rules, and end-to-end Robolectric tests.
  4. Production APK verification.
