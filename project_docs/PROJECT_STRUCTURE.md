# Project Structure & File Map

## 1. Directory Tree Overview

```
/
├── .agents/skills/              # Specialized developer skills & documentation
├── app/
│   ├── build.gradle.kts         # Main application build configuration
│   ├── proguard-rules.pro       # R8 / ProGuard obfuscation rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions, activities, FileProvider
│       │   ├── assets/             # Bundled animations, shaders, models, 3D assets
│       │   ├── java/com/arindam/camerax/
│       │   │   ├── CameraX.kt             # Application class
│       │   │   ├── data/                  # Data layer implementations
│       │   │   │   ├── camera/            # CameraX session & camera features
│       │   │   │   │   ├── CameraDeviceFeaturesRepository.kt
│       │   │   │   │   ├── CameraSession.kt
│       │   │   │   │   ├── CameraXMappers.kt
│       │   │   │   │   ├── ColorEffectAnalyzer.kt
│       │   │   │   │   ├── ColorEffects.kt
│       │   │   │   │   ├── DualComposition.kt
│       │   │   │   │   ├── MotionPhotoMuxer.kt
│       │   │   │   │   ├── PanoramaStitcher.kt
│       │   │   │   │   ├── StillImageExif.kt
│       │   │   │   │   ├── VideoQualitySupport.kt
│       │   │   │   │   └── VideoRecordingAudioPolicy.kt
│       │   │   │   ├── filter/            # [Target] GPU shader & filter pipeline
│       │   │   │   │   ├── OpenGlFilterEngine.kt
│       │   │   │   │   ├── GlProgram.kt
│       │   │   │   │   ├── GlTexture.kt
│       │   │   │   │   └── GlFrameBuffer.kt
│       │   │   │   ├── vision/            # [Target] MediaPipe tracking pipeline
│       │   │   │   │   ├── MediaPipeFaceTracker.kt
│       │   │   │   │   └── FaceMeshTransformer.kt
│       │   │   │   ├── media/             # MediaStore & file persistence
│       │   │   │   │   ├── FileMediaRepository.kt
│       │   │   │   │   ├── MediaFileInfo.kt
│       │   │   │   │   ├── MediaFiles.kt
│       │   │   │   │   ├── MediaPublishException.kt
│       │   │   │   │   └── MediaStorePublisher.kt
│       │   │   │   └── settings/          # SharedPreferences persistence
│       │   │   │       └── PreferenceSettingsRepository.kt
│       │   │   ├── di/                    # Dependency Injection
│       │   │   │   ├── AppContainer.kt
│       │   │   │   └── AppDispatchers.kt
│       │   │   ├── domain/                # Domain layer (business logic & contracts)
│       │   │   │   ├── model/             # Domain entities
│       │   │   │   │   ├── CameraModeCatalog.kt
│       │   │   │   │   ├── CameraModels.kt
│       │   │   │   │   └── FilterModels.kt # [Target] Filter definitions & intensities
│       │   │   │   ├── repository/        # Repository contracts
│       │   │   │   │   ├── CameraRepository.kt
│       │   │   │   │   ├── DeviceFeaturesRepository.kt
│       │   │   │   │   ├── FilterRepository.kt # [Target] Filter engine interface
│       │   │   │   │   ├── MediaRepository.kt
│       │   │   │   │   └── SettingsRepository.kt
│       │   │   │   └── usecase/           # Domain interactors
│       │   │   │       ├── CameraUseCases.kt
│       │   │   │       └── FilterUseCases.kt # [Target] Filter application use cases
│       │   │   ├── ui/                    # Presentation layer (Compose & Activities)
│       │   │   │   ├── base/              # Base classes & Navigation helpers
│       │   │   │   ├── compose/           # Shared glassmorphic UI components
│       │   │   │   ├── home/              # Viewfinder & primary capture flows
│       │   │   │   │   ├── HomeActivity.kt
│       │   │   │   │   ├── camera/        # Camera viewfinder, chrome, & pager
│       │   │   │   │   │   ├── CameraChrome.kt
│       │   │   │   │   │   ├── CameraFragment.kt
│       │   │   │   │   │   ├── CameraModels.kt
│       │   │   │   │   │   ├── CameraPager.kt
│       │   │   │   │   │   ├── CameraScreen.kt
│       │   │   │   │   │   ├── CameraViewModel.kt
│       │   │   │   │   │   ├── CameraViewModelFactory.kt
│       │   │   │   │   │   ├── CaptureConfirm.kt
│       │   │   │   │   │   └── OthersOverlay.kt
│       │   │   │   │   ├── filter/        # [Target] Snapchat-style filter carousel
│       │   │   │   │   │   ├── FilterCarousel.kt
│       │   │   │   │   │   └── FilterIntensitySlider.kt
│       │   │   │   │   ├── gallery/       # Media viewer & video player
│       │   │   │   │   └── permission/    # Runtime permission flows
│       │   │   │   ├── settings/          # Camera & app settings screen
│       │   │   │   └── theme/             # Material 3 colors, typography, theme
│       │   │   └── util/                  # Utilities (loggers, display, debug)
│       │   └── res/                       # Android resources (XML, drawables, strings)
│       └── test/                          # Unit & Robolectric JVM tests
├── gradle/
│   ├── libs.versions.toml                 # Version catalog
│   └── wrapper/gradle-wrapper.properties  # Gradle wrapper configuration
├── metadata.json                          # AI Studio platform configuration
└── project_docs/                          # Comprehensive technical documentation
```

---

## 2. Component Responsibility Matrix

| Package / File | Architectural Layer | Responsibility |
|---|---|---|
| `AppContainer.kt` | DI Root | Instantiates singletons and wires domain interactors to ViewModels. |
| `CameraSession.kt` | Data | Manages CameraX use-case binding, lens selection, zoom, exposure, and physical recording. |
| `CameraRepository.kt` | Domain | Abstraction decoupling presentation from CameraX details. |
| `CameraViewModel.kt` | Presentation | Holds UI state flow, manages gestures, triggers captures, and updates HUD indicators. |
| `CameraScreen.kt` | Presentation | Top-level Compose host displaying the live viewfinder and glassmorphic overlays. |
| `CameraChrome.kt` | Presentation | Live viewfinder controls (header actions, shutter button, zoom chips, mode chips). |
| `OpenGlFilterEngine.kt` (Planned) | Data | OpenGL ES multi-pass rendering pipeline for color, beauty, and AR shaders. |
| `MediaPipeFaceTracker.kt` (Planned)| Data | Runs real-time 478-point 3D facial landmark detection via Google MediaPipe. |
| `FilterCarousel.kt` (Planned) | Presentation | Horizontal interactive lens selector with animated thumbnails. |
