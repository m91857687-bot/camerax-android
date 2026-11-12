# Architectural Decision Records (ADRs)

## ADR 001: Decoupling the Filter Engine from CameraX Core

### Context:
The existing `ColorEffects.kt` system was tightly coupled inside `CameraSession.kt` using CPU-based `ImageAnalysis`. This made extending the filter system impossible without bloating camera session code, broke video recording filter integration, and created massive memory pressure.

### Decision:
Separate the Filter Engine into an autonomous subsystem (`data/filter` and `domain/repository/FilterRepository`). CameraX serves exclusively as the frame and sensor provider, feeding camera surfaces into the Filter Engine. The Filter Engine processes frames on the GPU and outputs to Preview, Video Encoder, or Image Capture.

### Consequences:
- **Pros**: Independent testability, modular architecture, zero risk of regressing CameraX use-case binding logic.
- **Cons**: Requires surface management and EGL context synchronization between CameraX and the custom renderer.

---

## ADR 002: GPU Multi-Pass OpenGL ES 2.0/3.0 Pipeline over CPU ImageAnalysis

### Context:
`ColorEffectAnalyzer` was decoding frames to software Bitmaps on the CPU at 30 FPS, allocating new Bitmaps on every frame. This resulted in >100 MB/s GC churn and severe jank. Furthermore, bilateral skin smoothing and 3D AR meshes are mathematically impossible at 60 FPS on CPU.

### Decision:
Build an OpenGL ES rendering pipeline utilizing Framebuffer Objects (FBO) and GLSL fragment shaders. The camera feed is ingested as `GL_TEXTURE_EXTERNAL_OES` and processed in GPU memory without CPU readback until final photo capture.

### Consequences:
- **Pros**: High framerates (60 FPS on standard devices), zero GC allocations, support for advanced shaders (LUTs, bilateral filter, vignette, split-toning, 3D meshes).
- **Cons**: Requires explicit EGL lifecycle handling and shader program compilation management.

---

## ADR 003: Choosing Google MediaPipe Tasks Vision over ML Kit

### Context:
For Snapchat-grade AR face filters (glasses, masks, crowns, animal ears, makeup), accurate 3D facial coordinates and facial expressions are mandatory.

### Comparison:
- **ML Kit Face Detection**: Returns ~37 2D landmarks and rough Euler angles (yaw, roll). Cannot fit 3D meshes or accurately track lips/eyes/eyebrows for blendshape-driven animations.
- **Google MediaPipe Face Landmarker**: Returns 478 3D landmarks (with depth coordinate Z) and 52 facial blendshapes (mouth open, eyebrow raise, eye blink, cheek puff) at sub-10ms inference time on mobile GPUs/NNAPIs.

### Decision:
Adopt `com.google.mediapipe:tasks-vision` with the official Face Landmarker bundle (`face_landmarker.task`).

### Consequences:
- **Pros**: Industry-standard 3D mesh tracking, realistic AR geometry attachment, blendshape reactive animations.
- **Cons**: Adds ~10MB asset bundle to the APK and requires background image inference threading.

---

## ADR 004: Video Filter Integration via Shared Surface Multiplexing

### Context:
The user specifically requires that video recording records the filtered result, not the raw camera feed.

### Decision:
Route the final FBO texture pass to both the display surface (for viewfinder) and a secondary EGL window surface backed by the `MediaCodec` / `VideoCapture` input surface. Both surfaces render from the same GPU pass with exact presentation timestamps (`eglPresentationTimeANDROID`).

### Consequences:
- **Pros**: Zero latency difference between preview and recorded video, true 1:1 visual match.
- **Cons**: Must handle surface lifecycle changes when recording starts and stops without disrupting the preview.

---

## ADR 005: Declarative Filter Bundles without Executable Code Loading

### Context:
The application must allow downloading and updating filter packs without violating Google Play Developer Program policies regarding Dynamic Code Loading (DCL).

### Decision:
Filter packs are strictly declarative archives containing:
- `descriptor.json` (metadata, anchor points, blendshape triggers).
- `.frag` / `.vert` GLSL shaders.
- `.png` / `.webp` textures.
- `.obj` / `.gltf` 3D meshes.
All packages are verified against SHA-256 checksums before loading. No `.dex`, `.jar`, or dynamic native code will ever be accepted.
