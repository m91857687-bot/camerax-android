# Next Task: Phase 3 — MediaPipe Face Landmarker & Real-Time Face Mesh Tracking

## Current Milestone
**Phase 2 Complete**: 11 cinematic GPU filters implemented in GLSL, integrated with `OpenGlSurfaceProcessor` for live preview and video recording, high-res still capture support, and dynamic real-time intensity slider UI (0% - 100%) with reset. All tests passing cleanly.

---

## Action Plan for Phase 3 Execution (Awaiting User Approval)

### Goal
Integrate Google MediaPipe Face Landmarker (478 3D landmarks + blendshapes) without causing frame drops or blocking the CameraX OpenGL rendering pipeline.

### Step 1: Add MediaPipe Dependencies & Face Model Asset
- Add `com.google.mediapipe:tasks-vision` dependency.
- Bundle `face_landmarker.task` model asset in `app/src/main/assets/`.

### Step 2: Implement Background Vision Pipeline
- Create `FaceTrackerEngine.kt` running on a dedicated background thread.
- Feed camera frames using `ImageAnalysis` (or EGL pixel buffer) in `LIVE_STREAM` mode.
- Output normalized 3D face landmarks and facial blendshapes asynchronously via StateFlow.

### Step 3: Implement Frame Timestamp Synchronization & Stale Result Handling
- Map face landmark coordinates to the active preview viewport and camera lens orientation (front camera mirroring).
- Apply exponential smoothing / Kalman filter on landmarks to prevent visual jitter.
- Implement stale result expiration (drop landmarks if older than ~100ms when face leaves the frame).

### Step 4: Verification & Tests
- Ensure preview frame rate remains stable (≥30 FPS).
- Write unit tests for landmark transformation, smoothing, and lifecycle management.
