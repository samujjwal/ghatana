# Audio-Video Library Integration Guide

This guide explains how to integrate the new `platform/java/audio-video` library with the existing `products/audio-video` modules.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Existing gRPC Services                              │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐                │
│  │ SttGrpcService  │ │ TtsGrpcService  │ │ VisionGrpcSvc   │                │
│  │ (thin wrapper)  │ │ (thin wrapper)  │ │ (thin wrapper)  │                │
│  └────────┬────────┘ └────────┬────────┘ └────────┬────────┘                │
└───────────┼───────────────────┼───────────────────┼────────────────────────┘
            │                   │                   │
            ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Audio-Video Library (platform/java/audio-video)          │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                    AudioVideoLibrary (Unified Facade)                 ││
│  │                    - Lifecycle management                               ││
│  │                    - Engine caching                                     ││
│  │                    - Cross-engine coordination                          ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│  ┌───────────────────────┐ ┌───────────────────────┐ ┌──────────────────────┐│
│  │    SttEngine API    │ │    TtsEngine API    │ │   VisionEngine API   ││
│  │  ┌─────────────────┐  │ │  ┌─────────────────┐  │ │  ┌────────────────┐  ││
│  │  │ WhisperOnnxEng  │  │ │  │ PiperOnnxEng  │  │ │  │ YoloOnnxEngine │  ││
│  │  │ (ONNX Runtime)  │  │ │  │ (ONNX Runtime)│  │ │  │ (ONNX Runtime) │  ││
│  │  └─────────────────┘  │ │  └─────────────────┘  │ │  └────────────────┘  ││
│  │  ┌─────────────────┐  │ │  ┌─────────────────┐  │ │                      ││
│  │  │ Stub (fallback) │  │ │  │ Stub (fallback)│  │ │                      ││
│  │  └─────────────────┘  │ │  └─────────────────┘  │ │                      ││
│  └───────────────────────┘ └───────────────────────┘ └──────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

## Migration Steps

### Step 1: Add Library Dependency

In each service's `build.gradle`:

```gradle
dependencies {
    // Add the audio-video library
    implementation project(':platform:java:audio-video')

    // Remove or keep these as needed:
    // implementation 'com.microsoft.onnxruntime:onnxruntime:1.17.0'
}
```

### Step 2: Refactor gRPC Services

#### STT Service Migration

**Before:**
```java
// In products/audio-video/modules/speech/stt-service/src/main/java/...
public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
    private final AdaptiveSTTEngine engine;

    public SttGrpcService() {
        EngineConfig config = EngineConfig.builder()
            .modelPath("/models/whisper-base")
            .build();
        this.engine = AdaptiveSTTEngineFactory.create(config);
    }

    @Override
    public void transcribe(TranscribeRequest req, StreamObserver<TranscribeResponse> resp) {
        // Complex conversion logic inline
        AudioData audio = convert(req.getAudio());
        TranscriptionResult result = engine.transcribe(audio, options);
        // Complex response building inline
        resp.onNext(convert(result));
        resp.onCompleted();
    }
}
```

**After:**
```java
// Import the library adapter
import com.ghatana.media.service.grpc.SttGrpcAdapter;

public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
    private final SttGrpcAdapter adapter;

    public SttGrpcService() {
        // Create library
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .modelId("whisper-base")
                .useGpu(true)
                .build())
            .build();

        // Create adapter
        this.adapter = new SttGrpcAdapter(library.getSttEngine());
    }

    @Override
    public void transcribe(TranscribeRequest req, StreamObserver<TranscribeResponse> resp) {
        // Delegate to adapter
        adapter.transcribe(req, resp);
    }

    @Override
    public StreamObserver<StreamingTranscribeRequest> streamingTranscribe(
            StreamObserver<StreamingTranscribeResponse> responseObserver) {
        return adapter.streamingTranscribe(responseObserver);
    }
}
```

### Step 3: Update Package Structure

The existing services have inconsistent package structures:
- `com.ghatana.stt.core.grpc` (good)
- `com.ghatana.media.vision.vision.core` (double-nested - needs fix)

**Recommended structure after migration:**
```
com.ghatana.media.service.grpc  (new library adapters)
com.ghatana.media.stt.service   (existing STT service)
com.ghatana.media.tts.service   (existing TTS service)
com.ghatana.media.vision.service (existing vision service)
```

### Step 4: Model Path Updates

Update model paths to use ONNX format:

```java
// Before
.modelPath("/models/whisper-base")

// After
.modelPath(Paths.get("/models/whisper-base.onnx"))
```

### Step 5: Configuration Consolidation

Consolidate the duplicate config packages:

**Before:**
- `com.ghatana.stt.core.config.EngineConfig`
- `com.ghatana.stt.core.api.EngineConfig` (record)

**After:**
- `com.ghatana.media.config.SttConfig` (from library)

## Key Benefits of Migration

1. **Consistent API**: Same `AudioVideoLibrary` facade across all products
2. **Better Testability**: Can use stub implementations for testing
3. **Resource Management**: Built-in lifecycle management with `AutoCloseable`
4. **Async Support**: Promise-based API for non-blocking operations
5. **Type Safety**: Unified type system across STT, TTS, and Vision

## Testing Strategy

### Unit Tests

```java
@Test
void testSttGrpcService() {
    // Create library with stub engine
    AudioVideoLibrary library = AudioVideoLibrary.builder()
        .withSttConfig(SttConfig.builder()
            .modelId("stub-model")
            .build())
        .build();

    SttGrpcService service = new SttGrpcService(library.getSttEngine());

    // Test service methods
    // ...
}
```

### Integration Tests

```java
@Test
void testWithRealOnnxModel() {
    // This test requires actual ONNX model file
    AudioVideoLibrary library = AudioVideoLibrary.builder()
        .withSttConfig(SttConfig.builder()
            .modelPath(Paths.get("/models/whisper-tiny.onnx"))
            .modelId("whisper-tiny")
            .build())
        .build();

    SttEngine engine = library.getSttEngine();
    // Real inference test
}
```

## Troubleshooting

### Issue: ONNX Model Not Found

**Symptom:** ModelLoadingError thrown during initialization

**Solution:**
```java
// Check if model exists before creating engine
if (!config.modelPath().toFile().exists()) {
    LOG.warning("ONNX model not found at " + config.modelPath());
    LOG.info("Falling back to stub implementation");
}
```

### Issue: GPU Not Available

**Symptom:** CUDA not found, falling back to CPU

**Solution:**
```java
// GPU is optional - engine will work on CPU
// To force CPU:
.useGpu(false)
```

### Issue: Type Conflicts

**Symptom:** Duplicate type definitions in api/ and model/ packages

**Solution:**
1. Remove `model/` package (legacy builder pattern)
2. Keep `api/` package (records with proper types)
3. Update all imports

## Rollout Plan

### Phase 1: Library Development (Completed)
- ✅ Create library structure
- ✅ Implement ONNX engines
- ✅ Create gRPC adapters
- ✅ Add comprehensive tests

### Phase 2: STT Service Migration (Next)
1. Add library dependency to stt-service
2. Refactor SttGrpcService to use SttGrpcAdapter
3. Test with stub implementation
4. Deploy with real ONNX model

### Phase 3: TTS Service Migration
1. Add library dependency to tts-service
2. Refactor TtsGrpcService to use TtsGrpcAdapter
3. Test and deploy

### Phase 4: Vision Service Migration
1. Add library dependency to vision-service
2. Flatten vision/vision/ package structure
3. Refactor VisionGrpcService to use VisionGrpcAdapter
4. Test and deploy

### Phase 5: Cleanup
1. Remove duplicate code in products/audio-video
2. Consolidate model/api packages
3. Update documentation
4. Performance benchmarking

## Files Created/Modified

### Library Files (platform/java/audio-video)
```
src/main/java/com/ghatana/audio/video/
├── AudioVideoLibrary.java              (Main facade)
├── package-info.java                     (Documentation)
├── common/
│   └── Types.java                        (Shared types)
├── config/
│   └── EngineConfigs.java                (Configuration builders)
├── stt/
│   ├── api/
│   │   ├── SttEngine.java                (STT interface)
│   │   └── SttEngineFactory.java         (Factory with stub fallback)
│   └── engine/
│       └── onnx/
│           └── WhisperOnnxEngine.java    (ONNX implementation)
├── tts/
│   ├── api/
│   │   ├── TtsEngine.java                (TTS interface)
│   │   └── TtsEngineFactory.java         (Factory with stub fallback)
│   └── engine/
│       └── onnx/
│           └── PiperOnnxEngine.java      (ONNX implementation)
├── vision/
│   ├── api/
│   │   ├── VisionEngine.java             (Vision interface)
│   │   └── VisionEngineFactory.java      (Factory with stub fallback)
│   └── engine/
│       └── onnx/
│           └── YoloOnnxEngine.java       (ONNX implementation)
└── service/grpc/
    ├── SttGrpcAdapter.java               (gRPC adapter)
    ├── TtsGrpcAdapter.java               (gRPC adapter)
    └── VisionGrpcAdapter.java            (gRPC adapter)

src/test/java/com/ghatana/audio/video/
└── AudioVideoLibraryTest.java            (Comprehensive tests)

build.gradle                              (Build configuration)
README.md                                 (Library documentation)
```

### Migration Tasks for Existing Services

**products/audio-video/modules/speech/stt-service:**
- Update build.gradle to depend on library
- Refactor SttGrpcService to use SttGrpcAdapter
- Remove duplicate types in model/ package

**products/audio-video/modules/speech/tts-service:**
- Update build.gradle to depend on library
- Refactor TtsGrpcService to use TtsGrpcAdapter
- Remove duplicate types

**products/audio-video/modules/vision/vision-service:**
- Update build.gradle to depend on library
- Flatten vision/vision/ package to vision/
- Refactor VisionGrpcService to use VisionGrpcAdapter
- Remove duplicate BoundingBox classes

## Summary

The new audio-video library provides:
- **Stable, embeddable Java API** for STT, TTS, and Vision
- **ONNX Runtime integration** for efficient ML inference
- **Unified type system** across all engines
- **gRPC adapters** for easy service integration
- **Stub fallbacks** for testing without models
- **Comprehensive test coverage**

Next steps: Migrate existing services to use the library, starting with STT service.
