# Known Issues & Technical Debt

## 1. Resolved Issues
- **ISSUE-01: Video Recording Bypasses Filter Pipeline**:
  - *Status*: ✅ **RESOLVED in Phase 1 & 2**.
  - *Resolution*: `OpenGlSurfaceProcessor` acts as `CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE`, routing the rendered GPU frame stream through the active GLSL cinematic shader to both the live preview surface and the `VideoCapture` encoder surface simultaneously.
- **ISSUE-04: CPU-Bound ColorMatrix Limitations**:
  - *Status*: ✅ **RESOLVED in Phase 2**.
  - *Resolution*: Implemented 11 GLSL cinematic fragment shaders in `GlPassthroughRenderer.kt` executing directly on the GPU with uniform-controlled intensity blending (`uIntensity`).

---

## 2. Active Technical Items (Pending Next Phases)
- **ISSUE-02: Absence of Face Tracking & AR Engines**:
  - *Symptom*: The project has zero face tracking, zero 3D AR mesh rendering, and zero beauty smoothing algorithms.
  - *Fix Planned*: Phase 3 & 4 — integrate Google MediaPipe Face Landmarker and OpenGL ES 3D rendering pipeline.

---

## 3. Hardware & Platform Constraints
- **ISSUE-05: OEM Extensions Incompatibility with VideoCapture**:
  - *Detail*: CameraX OEM Extensions (`HDR`, `NIGHT`, `PORTRAIT`, `BEAUTY`) generally cannot be bound concurrently with `VideoCapture`. The app currently handles this by falling back to stills-only mode.
  - *Handling*: Preserve this policy and ensure the new Filter Engine acts as the primary real-time beauty/portrait engine so users can record video with beauty effects without relying on OEM-restricted hardware extensions.
