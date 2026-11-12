# Features Specification: Professional Camera & AR Filter Engine

## 1. High-Resolution Photographic Pipeline
- **Formats Supported**:
  - Standard JPEG with EXIF orientation normalization.
  - Ultra HDR (Gain-map HDR stills in JPEG and HEIC format).
  - RAW / DNG (paired Adobe DNG + JPEG, or unbinned full-sensor RAW).
- **Filter Integration**:
  - All selected color grading, beauty smoothing, and AR 2D/3D elements must be rendered onto the full-resolution capture buffer before disk write and MediaStore publishing.
  - Front camera selfies must respect horizontal mirror settings without corrupting orientation EXIF tags.

---

## 2. Professional Videography Pipeline
- **Quality & Formats**:
  - SD (480p), HD (720p), FHD (1080p), UHD 4K (2160p).
  - 60 FPS recording when supported by device hardware (`GroupableFeature.FPS_60`).
  - High dynamic range (SDR, HLG 10-bit, HDR10, HDR10+).
  - Persistent recording allowing lens flipping during an active clip without stopping.
- **Filter Baking into Video**:
  - The live filtered output must be piped into the `VideoCapture` / `MediaCodec` input surface.
  - Video and audio synchronization must remain pristine (<15ms deviation) without frame dropping.

---

## 3. Manual Camera Controls
- **Exposure**:
  - Exposure compensation index adjustment with live EV readout (-4.0 EV to +4.0 EV).
  - Hybrid AE: Manual ISO priority or Shutter priority via Camera2 interop (`CONTROL_AE_PRIORITY_MODE`).
- **Focus**:
  - Tap-to-focus on viewfinder with visual focus ring and metering lock (`FocusMeteringAction`).
- **Zoom**:
  - Smooth multi-camera physical lens selection (0.5x Ultra-Wide, 1.0x Wide, 2.0x Telephoto).
  - Continuous pinch-to-zoom and vertical drag zoom gestures.

---

## 4. Cinematic Color Filters (GPU Shaders)
Real-time GLSL fragment shaders processing 30-60 FPS with adjustable intensity (0.0 to 1.0):
1. **Cinematic**: Teal-and-orange split toning, high contrast shadows, softened skin highlights.
2. **Vintage**: Faded blacks, gentle warm vignette, nostalgic color shift.
3. **Film**: Classic Kodak/Fuji film stock emulation with authentic grain simulation.
4. **Black & White**: High-contrast monochrome with customized red/green/blue luminance weighting.
5. **Warm**: Amber golden tint with elevated color temperature.
6. **Cold**: Soft blue cast with subdued warmth for clean modern aesthetics.
7. **Golden Hour**: Sunset radiance with warm lens bloom and highlighted rim lighting.
8. **Portrait**: Skin-optimized color mapping emphasizing natural warmth and clarity.
9. **Natural**: Balanced vibrance and contrast maintaining true-to-life color fidelity.
10. **Night**: Enhanced shadow detail with noise-suppressed dark tones and clean highlight rolloff.
11. **Retro**: 90s aesthetic with saturated primaries and subtle chromatic aberration.

---

## 5. Real-Time Face Beauty Pipeline
- **Bilateral Skin Smoothing**: High-speed, edge-preserving spatial filter smoothing skin texture while keeping eyelashes, eyes, lips, and hair sharp.
- **Skin Tone Normalization**: Subtly warms and evens out discolored facial zones.
- **Lighting Enhancement**: Lifts ambient shadows across facial features to eliminate harsh unflattering shadows.
- **Adjustability**: Each feature has an independent toggle and variable intensity slider (0% to 100%).

---

## 6. 3D Face Filters & AR Effects (MediaPipe Face Landmarker)
- **Tracking Core**:
  - Google MediaPipe Face Landmarker with 478 3D landmarks + 52 facial blendshapes.
  - Real-time 6DoF head pose estimation (Pitch, Yaw, Roll, Translation X/Y/Z).
- **Supported AR Categories**:
  - **Glasses**: 3D sunglasses and wireframe spectacles anchored to nose bridge and temples with perspective scaling.
  - **Masks**: 3D mesh contouring over face surface adhering to cheeks, nose, and chin.
  - **Animal Ears**: Animated cat/dog/rabbit ears positioned on top of the skull with physics bounce.
  - **Crowns & Headwear**: Floating crowns and hats tracking head tilt.
  - **Beards & Facial Hair**: Anchored to jawline and lip landmarks.
  - **Makeup**: Lip tint, eye shadow, and blush blended seamlessly with facial contours.
  - **Interactive Expressions**: AR elements that react to facial gestures (e.g., mouth opening triggers sparkles, eye blink triggers sound/animation).

---

## 7. Extensible Filter Package System
- **Security Principles**:
  - Strictly declarative packages: JSON descriptor + GLSL shaders + PNG/WEBP textures + 3D meshes (glTF / OBJ).
  - No executable dynamic code loading (`.dex`, `.jar`, `.so`).
  - Cryptographic checksum verification (SHA-256) for any remote or bundled assets.
- **Package Anatomy**:
  ```json
  {
    "id": "filter_neon_glasses",
    "name": "Neon Glasses",
    "version": 1,
    "category": "AR_3D",
    "shader": "shaders/color_neon.frag",
    "mesh": "models/glasses.obj",
    "texture": "textures/glasses_diffuse.png",
    "anchor": "NOSE_BRIDGE",
    "scale": [1.0, 1.0, 1.0],
    "expressionTriggers": [
      { "blendshape": "mouthOpen", "action": "activateGlow" }
    ]
  }
  ```
- **Favorites & Caching**:
  - Quick-access favorites bar saved locally via DataStore.
