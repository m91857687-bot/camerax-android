# Changelog

All notable changes to the Professional Camera & AR Filter project will be documented in this file.

## [Phase 2: 11 Cinematic Color Filters & GPU Shader Engine] - 2026-11-12

### Added
- Extended `EffectMode` with the 11 required cinematic filters:
  1. `NATURAL`: Enhanced natural clarity, subtle saturation (+15%), preserved skin tones.
  2. `CINEMATIC`: Hollywood Teal & Orange split toning, rich blacks, filmic contrast.
  3. `VINTAGE`: Lifted matte black toe, warm cream highlights, classic analog fade.
  4. `WARM`: Cozy golden color temperature boost (+15% R, -14% B).
  5. `COLD`: Crisp Nordic blue/cyan temperature shift (+18% B, -12% R).
  6. `GOLDEN`: Radiant golden hour warmth and amber illumination.
  7. `NOIR`: High-contrast dramatic black & white (deep shadows, bright speculars).
  8. `DRAMA`: Heavy contrast, punchy saturation (+22%), deep moody blacks.
  9. `PORTRAIT`: Flattering warm peach glow, smooth skin midtones.
  10. `SUNSET`: Dusky violet shadows, fiery orange/golden highlights.
  11. `SOFT`: Dreamy pastel palette, low contrast, lifted shadows.
- Upgraded `GlPassthroughRenderer.kt` with a multi-mode GLSL fragment shader supporting all 11 cinematic filter algorithms, integer uniform `uFilterType`, and float uniform `uIntensity` (0.0 to 1.0).
- Implemented zero-cost, zero-rebind shader switching in `OpenGlSurfaceProcessor.kt` on `GLThread`.
- Added `FilterIntensityControl` UI component in `CameraChrome.kt`:
  - Glassmorphic rounded surface container (`testTag("filter_intensity_control")`).
  - Real-time intensity percentage text (`testTag("filter_intensity_value")`).
  - Continuous slider from 0% to 100% (`testTag("filter_intensity_slider")`).
  - Instant Reset button returning intensity to 100% (`testTag("filter_intensity_reset_button")`).
- Enhanced `EffectsFilmstrip` with distinctive gradient thumbnails for each filter and `EffectMode.cinematicFilters` catalog.
- Added matching ColorMatrix transforms and intensity blending in `ColorEffects.kt` and `CameraSession.kt` for high-resolution still JPEG captures.
- Added comprehensive unit tests in `ColorEffectsTest.kt` and `CameraViewModelTest.kt` verifying filter switching, intensity clamping, and catalog integrity.

### Verified
- Full project compilation via `compile_applet` succeeded.
- All unit and Robolectric tests (`gradle :app:testDebugUnitTest`) executed and passed (34 tasks completed cleanly).
- Zero regression on existing camera controls (zoom, focus, exposure, flip, audio, slo-mo, dual).

## [Phase 1: OpenGL ES Core & CameraX SurfaceProcessor] - 2026-11-12

### Added
- Created `EglCore.kt` managing EGL14 display, context, and window/pbuffer surfaces.
- Created `GlProgram.kt` utility for GLSL shader compilation, linking, and attribute/uniform mapping.
- Created `GlPassthroughRenderer.kt` executing full-screen quad vertex and fragment shaders for `GL_TEXTURE_EXTERNAL_OES` textures.
- Created `OpenGlSurfaceProcessor.kt` implementing CameraX `SurfaceProcessor` on a dedicated `HandlerThread` (`GLThread`).
- Wired `CameraEffect(PREVIEW or VIDEO_CAPTURE)` into `CameraSession.kt` using `OpenGlSurfaceProcessor`.
- Updated app launcher and display name to **"كاميرا برو"** (`Camera Pro`) across `metadata.json` and `build.gradle.kts`.

### Verified
- Zero-allocation GPU rendering pipeline with no per-frame Bitmap allocation.
- Camera preview and video capture surfaces both receive frames concurrently from `OpenGlSurfaceProcessor`.
- Seamless fallback policy when binding sessions with or without CameraEffect.
- Full application compilation succeeded with zero errors.

## [Phase 0: Baseline & Inspection] - 2026-11-12

### Added
- Created complete documentation suite in `project_docs/`:
  - `MASTER_CONTEXT.md`
  - `ARCHITECTURE.md`
  - `PROJECT_STRUCTURE.md`
  - `FEATURES_SPEC.md`
  - `PHASE_PLAN.md`
  - `PROJECT_STATUS.md`
  - `DECISIONS.md`
  - `CHANGELOG.md`
  - `KNOWN_ISSUES.md`
  - `NEXT_TASK.md`
- Created `metadata.json` for AI Studio platform management and emulator permission declarations (`CAMERA`, `RECORD_AUDIO`).
- Configured `debugConfig` signing block in `app/build.gradle.kts` targeting `${rootDir}/debug.keystore`.

### Changed
- Aligned Gradle wrapper distribution from `9.5.0-bin.zip` to `9.3.1-bin.zip` to match the container's pre-installed Gradle runtime.
- Aligned Android Gradle Plugin (AGP) version in `gradle/libs.versions.toml` from `9.3.1` to `9.1.1` to resolve the Gradle compatibility constraint.
- Updated `compileOptions` and Kotlin `jvmToolchain` in `app/build.gradle.kts` from Java 17 to Java 21 (Temurin 21) matching the host JDK.

### Verified
- Built `app/build/outputs/apk/debug/app-debug.apk` successfully (31 MB) with zero compilation or packaging errors.
- Completed comprehensive inspection of existing codebase, CameraX configurations, legacy Effects mode, and permissions.
