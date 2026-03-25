# Audio-Video Processing Library for Java

> **Stable, embeddable speech-to-text, text-to-speech, and computer vision for Java applications.**

This library provides a unified API for audio-video processing that can be embedded directly in Java applications or used as the foundation for gRPC/microservice deployments.

## Features

- **Embeddable** - No network required, runs in-process
- **Multi-Engine** - STT, TTS, and Vision in one library
- **Async Support** - Promise-based API for non-blocking operations
- **Pluggable Backends** - Native ONNX, cloud inference, or hybrid
- **Unified Configuration** - Consistent config across all engines
- **Resource Management** - Built-in limits and graceful degradation

## Quick Start

```java
// Create library with STT and TTS
AudioVideoLibrary library = AudioVideoLibrary.builder()
    .withSttConfig(SttConfig.builder()
        .modelPath(Paths.get("/models/whisper-base.onnx"))
        .build())
    .withTtsConfig(TtsConfig.builder()
        .voiceModelPath(Paths.get("/models/piper-en.onnx"))
        .build())
    .build();

// Use STT
try (SttEngine stt = library.getSttEngine()) {
    TranscriptionResult result = stt.transcribe(audioData);
    System.out.println(result.getText());
}

// Use TTS
try (TtsEngine tts = library.getTtsEngine()) {
    AudioData audio = tts.synthesize("Hello, world!");
    playAudio(audio);
}
```

## Installation

### Gradle

```groovy
dependencies {
    implementation 'com.ghatana:audio-video:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.ghatana</groupId>
    <artifactId>audio-video</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Speech-to-Text (STT)

```java
try (SttEngine stt = library.getSttEngine()) {
    // Simple transcription
    TranscriptionResult result = stt.transcribe(audioData);

    // With options
    TranscriptionResult result2 = stt.transcribe(audioData,
        TranscriptionOptions.builder()
            .language(Locale.ENGLISH)
            .enableTimestamps(true)
            .build());

    // Async
    Promise<TranscriptionResult> promise = stt.transcribeAsync(audioData);
    promise.whenResult(r -> System.out.println(r.getText()));
}
```

### Streaming STT

```java
StreamingSession session = stt.createStreamingSession(profile);

session.onTranscription(transcription -> {
    if (transcription.isFinal()) {
        System.out.println("Final: " + transcription.text());
    }
});

// Feed audio chunks from microphone
session.feedAudio(chunk);
session.endStream();
```

### Text-to-Speech (TTS)

```java
try (TtsEngine tts = library.getTtsEngine()) {
    // Simple synthesis
    AudioData audio = tts.synthesize("Hello!");

    // With prosody control
    AudioData audio2 = tts.synthesize(text, SynthesisOptions.builder()
        .speed(0.9)
        .pitch(1.1)
        .emotion(Emotion.PROFESSIONAL)
        .build());

    // Streaming (lower latency)
    tts.synthesizeStreaming(text, options, chunk -> {
        playAudioChunk(chunk);
    });
}
```

### Computer Vision

```java
try (VisionEngine vision = library.getVisionEngine()) {
    // Object detection
    DetectionResult result = vision.detect(imageData);
    for (DetectedObject obj : result.objects()) {
        System.out.println(obj.className() + " at " + obj.bbox());
    }

    // Classification
    List<Classification> classes = vision.classify(imageData, 5);
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  AudioVideoLibrary                       │
│  (Unified facade with lifecycle management)              │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │
│  │  SttEngine  │  │  TtsEngine  │  │  VisionEngine   │   │
│  │  (STT API)  │  │  (TTS API)  │  │  (Vision API)   │   │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘   │
└─────────┼────────────────┼────────────────────┼──────────┘
          │                │                    │
┌─────────▼────────────────▼────────────────────▼──────────┐
│                    Engine Implementations                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │WhisperOnnx  │ │PiperOnnx     │ │YOLOv8Onnx        │  │
│  │WhisperCpp   │ │CoquiOnnx     │ │OpenCV            │  │
│  │CloudFallback│ │CloudFallback │ │CloudFallback     │  │
│  └──────────────┘ └──────────────┘ └──────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

## Configuration

### STT Configuration

```java
SttConfig config = SttConfig.builder()
    .modelPath(Paths.get("/models/whisper-base.onnx"))
    .modelId("whisper-base")
    .useGpu(true)
    .maxConcurrentRequests(10)
    .timeout(Duration.ofSeconds(30))
    .enablePunctuation(true)
    .enableTimestamps(false)
    .maxAudioLengthSeconds(300)
    .cloudFallback(CloudFallbackConfig.builder()
        .endpoint("https://api.ghatana.ai/v1/stt")
        .apiKey(System.getenv("API_KEY"))
        .build())
    .build();
```

### TTS Configuration

```java
TtsConfig config = TtsConfig.builder()
    .voiceModelPath(Paths.get("/models/piper-en.onnx"))
    .defaultVoiceId("piper-en")
    .sampleRate(22050)
    .enableProsody(true)
    .enableVoiceCloning(false)
    .enableStreaming(true)
    .maxTextLength(5000)
    .build();
```

### Vision Configuration

```java
VisionConfig config = VisionConfig.builder()
    .modelPath(Paths.get("/models/yolov8n.onnx"))
    .modelId("yolov8n")
    .useGpu(true)
    .defaultConfidenceThreshold(0.5)
    .defaultMaxDetections(100)
    .inputSize(640)
    .enableTracking(true)
    .build();
```

## Error Handling

The library uses a hierarchy of checked exceptions:

- `ProcessingError` - Base exception
  - `ValidationError` - Invalid input (not retryable)
  - `ModelLoadingError` - Model loading failed (not retryable)
  - `InferenceError` - Processing failed (may be retryable)
  - `ResourceExhaustedError` - Rate limits exceeded (retryable)

```java
try {
    TranscriptionResult result = stt.transcribe(audio);
} catch (ValidationError e) {
    // Invalid input
} catch (InferenceError e) {
    if (e.isRetryable()) {
        // Retry with backoff
    }
}
```

## Thread Safety

- **AudioVideoLibrary**: Thread-safe, can be shared across threads
- **SttEngine/TtsEngine/VisionEngine**: Thread-safe, supports concurrent requests
- **StreamingSession**: Not thread-safe, use from single thread

## Resource Management

All engines implement `AutoCloseable`:

```java
// Try-with-resources (recommended)
try (SttEngine stt = library.getSttEngine()) {
    // use stt
}

// Or close library (closes all engines)
library.close();
```

## Performance Tuning

### Concurrency Limits

```java
SttConfig.builder()
    .maxConcurrentRequests(20)  // Limit concurrent transcriptions
    .build();
```

### GPU Acceleration

```java
SttConfig.builder()
    .useGpu(true)  // Enable CUDA/Metal acceleration
    .build();
```

### Model Warmup

```java
// Pre-load models to reduce first-request latency
stt.warmup();
tts.warmup();
vision.warmup();
```

## Comparison: Library vs Service

| Feature | Library (Embedded) | gRPC Service |
|---------|-------------------|--------------|
| Network | Not required | Required |
| Latency | Lowest | Higher (network hop) |
| Scalability | Single JVM | Multi-node |
| Resource sharing | Per-process | Shared pool |
| Use case | Edge/embedded | Cloud/microservices |

## Migration from Services

The gRPC services in `products/audio-video/modules/` will be refactored to use this library as their core:

```java
// In gRPC service (thin wrapper)
public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
    private final SttEngine engine;

    public SttGrpcService(AudioVideoLibrary library) {
        this.engine = library.getSttEngine();
    }

    @Override
    public void transcribe(TranscribeRequest req, StreamObserver<TranscribeResponse> resp) {
        // Convert protobuf to library types
        AudioData audio = convert(req.getAudio());
        TranscriptionResult result = engine.transcribe(audio);
        // Convert result back to protobuf
        resp.onNext(convert(result));
        resp.onCompleted();
    }
}
```

## Roadmap

- [ ] Real implementations of WhisperOnnx, PiperOnnx, YOLOv8Onnx
- [ ] Voice cloning implementation
- [ ] Batch processing optimization
- [ ] Quantized model support (INT8, FP16)
- [ ] Model caching and hot-swapping

## License

Apache License 2.0
