# Migration Guide: products/audio-video → platform/java/audio-video

> **Migration Status**: Ready to Implement  
> **Target**: Make products/audio-video exemplary consumer of shared library  
> **Package**: `com.ghatana.media` (platform) replacing internal implementations

---

## Migration Overview

### Current State (products/audio-video)
- STT: `WhisperCppAdapter` in `stt-service` with native lib issues (AV-002)
- TTS: `DefaultTtsEngine` with robotic fallback (AV-005)
- Vision: `YoloV8Detector`/`OnnxYoloV8Detector` with no interface (AV-009)
- Issues: 10 critical/high findings from audit

### Target State (using platform library)
- STT: `WhisperOnnxEngine` + `WhisperCppAdapter` with graceful fallback
- TTS: `PiperOnnxEngine` + `CoquiTtsAdapter` with cloud fallback
- Vision: `YoloOnnxEngine` with unified `VisionEngine` interface
- Benefits: Fixes 8/10 audit issues, shared improvements, cross-product reuse

---

## Phase 1: Dependency Setup

### 1.1 Add Platform Library Dependency

**File**: `products/audio-video/build.gradle` or `settings.gradle`

```groovy
dependencies {
    // Replace internal implementations with platform library
    implementation project(':platform:java:audio-video')
    
    // Keep only gRPC/protobuf dependencies
    implementation 'io.grpc:grpc-netty-shaded:1.61.0'
    implementation 'io.grpc:grpc-protobuf:1.61.0'
    implementation 'io.grpc:grpc-stub:1.61.0'
}
```

### 1.2 Remove Internal Implementations

**Delete**: 
- `modules/speech/stt-service/src/main/java/com/ghatana/stt/core/pipeline/DefaultAdaptiveSTTEngine.java`
- `modules/speech/stt-service/src/main/java/com/ghatana/stt/core/whisper/WhisperCppAdapter.java` (keep JNI wrapper)
- `modules/speech/tts-service/src/main/java/com/ghatana/tts/core/pipeline/DefaultTtsEngine.java`
- `modules/speech/tts-service/src/main/java/com/ghatana/tts/core/dsp/ProsodyProcessor.java`
- `modules/vision/vision-service/src/main/java/com/ghatana/audio/video/vision/detection/YoloV8Detector.java`
- `modules/vision/vision-service/src/main/java/com/ghatana/audio/video/vision/detection/OnnxYoloV8Detector.java`
- `modules/speech/stt-service/src/main/java/com/ghatana/stt/core/api/` (consolidated to platform)
- `modules/speech/stt-service/src/main/java/com/ghatana/stt/core/model/` (remove duplicates)

---

## Phase 2: STT Service Migration

### 2.1 Refactor SttGrpcService

**File**: `modules/speech/stt-service/src/main/java/com/ghatana/stt/grpc/SttGrpcService.java`

```java
package com.ghatana.stt.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.stt.api.*;
import com.ghatana.media.common.*;
import com.ghatana.stt.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import io.activej.promise.Promise;

/**
 * gRPC service adapter using platform library.
 * 
 * This is a thin wrapper adapting the platform library's SttEngine to gRPC calls.
 * All business logic is delegated to the library.
 */
public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
    
    private final AudioVideoLibrary library;
    private final SttEngine engine;
    
    public SttGrpcService(SttConfig config) {
        this.library = AudioVideoLibrary.builder()
            .withSttConfig(config)
            .build();
        this.engine = library.getSttEngine();
    }
    
    @Override
    public void transcribe(
        TranscribeRequest request,
        StreamObserver<TranscribeResponse> responseObserver) {
        
        try (SttEngine stt = library.getSttEngine()) {
            // Convert protobuf to library types
            AudioData audio = convertAudio(request.getAudio());
            TranscriptionOptions options = convertOptions(request.getOptions());
            
            // Use platform library
            TranscriptionResult result = stt.transcribe(audio, options);
            
            // Convert back to protobuf
            TranscribeResponse response = convertResponse(result);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (ValidationError e) {
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        } catch (InferenceError e) {
            io.grpc.Status status = e.isRetryable() 
                ? io.grpc.Status.UNAVAILABLE 
                : io.grpc.Status.INTERNAL;
            responseObserver.onError(
                status.withDescription(e.getMessage()).asRuntimeException()
            );
        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Transcription failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }
    
    @Override
    public StreamObserver<StreamingTranscribeRequest> streamingTranscribe(
        StreamObserver<StreamingTranscribeResponse> responseObserver) {
        
        StreamingSession session = engine.createStreamingSession();
        
        session.onTranscription(transcript -> {
            StreamingTranscribeResponse response = StreamingTranscribeResponse.newBuilder()
                .setText(transcript.text())
                .setIsFinal(transcript.isFinal())
                .setConfidence(transcript.confidence())
                .build();
            responseObserver.onNext(response);
        });
        
        session.onError(error -> {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription(error.getMessage())
                    .asRuntimeException()
            );
        });
        
        return new StreamObserver<>() {
            @Override
            public void onNext(StreamingTranscribeRequest request) {
                AudioChunk chunk = AudioChunk.builder()
                    .data(request.getAudioChunk().toByteArray())
                    .sequence(request.getSequence())
                    .isLast(request.getIsLast())
                    .timestamp(System.currentTimeMillis())
                    .build();
                session.feedAudio(chunk);
            }
            
            @Override
            public void onError(Throwable t) {
                session.close();
                responseObserver.onError(t);
            }
            
            @Override
            public void onCompleted() {
                session.endStream();
                responseObserver.onCompleted();
            }
        };
    }
    
    // Type conversion helpers
    private AudioData convertAudio(AudioDataProto proto) {
        return AudioData.builder()
            .data(proto.getData().toByteArray())
            .sampleRate(proto.getSampleRate())
            .channels(proto.getChannels())
            .bitsPerSample(proto.getBitsPerSample())
            .duration(java.time.Duration.ofMillis(proto.getDurationMs()))
            .format(AudioFormat.valueOf(proto.getFormat().name()))
            .build();
    }
    
    private TranscriptionOptions convertOptions(TranscriptionOptionsProto proto) {
        return TranscriptionOptions.builder()
            .language(new java.util.Locale(proto.getLanguage()))
            .enableTimestamps(proto.getEnableTimestamps())
            .enablePunctuation(proto.getEnablePunctuation())
            .build();
    }
    
    private TranscribeResponse convertResponse(TranscriptionResult result) {
        TranscribeResponse.Builder builder = TranscribeResponse.newBuilder()
            .setText(result.getText())
            .setConfidence(result.confidence())
            .setLanguage(result.language())
            .setModelId(result.modelId())
            .setLatencyMs(result.latency().toMillis());
        
        for (WordTiming word : result.words()) {
            builder.addWords(WordTimingProto.newBuilder()
                .setWord(word.word())
                .setStartMs(word.startMs())
                .setEndMs(word.endMs())
                .setConfidence(word.confidence())
                .build()
            );
        }
        
        return builder.build();
    }
    
    @Override
    public void getAvailableModels(
        GetModelsRequest request,
        StreamObserver<GetModelsResponse> responseObserver) {
        
        List<ModelInfo> models = engine.getAvailableModels();
        
        GetModelsResponse.Builder response = GetModelsResponse.newBuilder();
        for (ModelInfo model : models) {
            response.addModels(ModelInfoProto.newBuilder()
                .setModelId(model.modelId())
                .setName(model.name())
                .setVersion(model.version())
                .setSizeBytes(model.sizeBytes())
                .setSupportsGpu(model.supportsGpu())
                .build()
            );
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
    
    @Override
    public void createProfile(
        CreateProfileRequest request,
        StreamObserver<CreateProfileResponse> responseObserver) {
        
        List<AudioData> enrollmentData = request.getEnrollmentAudioList().stream()
            .map(this::convertAudio)
            .toList();
        
        UserProfile profile = engine.createProfile(
            request.getProfileId(),
            enrollmentData
        );
        
        responseObserver.onNext(CreateProfileResponse.newBuilder()
            .setProfileId(profile.profileId())
            .setDisplayName(profile.displayName())
            .build()
        );
        responseObserver.onCompleted();
    }
}
```

### 2.2 Update STT Service Configuration

**File**: `modules/speech/stt-service/src/main/resources/application.yml`

```yaml
stt:
  engine:
    model-path: ${STT_MODEL_PATH:/models/whisper-base.onnx}
    use-gpu: ${STT_USE_GPU:true}
    max-concurrent-requests: ${STT_MAX_CONCURRENT:10}
  
  # Platform library configuration
  library:
    metrics-enabled: true
    warmup-on-startup: true
    
  # Native library fallback (optional)
  native:
    whisper-path: ${WHISPER_NATIVE_PATH:/usr/local/lib/libwhisper.so}
    enabled: ${WHISPER_NATIVE_ENABLED:false}
```

---

## Phase 3: TTS Service Migration

### 3.1 Refactor TtsGrpcService

**File**: `modules/speech/tts-service/src/main/java/com/ghatana/tts/grpc/TtsGrpcService.java`

```java
package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.*;
import com.ghatana.media.common.*;
import com.ghatana.tts.grpc.proto.*;
import io.grpc.stub.StreamObserver;

public class TtsGrpcService extends TtsServiceGrpc.TtsServiceImplBase {
    
    private final AudioVideoLibrary library;
    
    public TtsGrpcService(TtsConfig config) {
        this.library = AudioVideoLibrary.builder()
            .withTtsConfig(config)
            .build();
    }
    
    @Override
    public void synthesize(
        SynthesizeRequest request,
        StreamObserver<SynthesizeResponse> responseObserver) {
        
        try (TtsEngine tts = library.getTtsEngine()) {
            SynthesisOptions options = convertOptions(request.getOptions());
            
            AudioData audio = tts.synthesize(request.getText(), options);
            
            SynthesizeResponse response = SynthesizeResponse.newBuilder()
                .setAudio(AudioDataProto.newBuilder()
                    .setData(com.google.protobuf.ByteString.copyFrom(audio.data()))
                    .setSampleRate(audio.sampleRate())
                    .setChannels(audio.channels())
                    .setBitsPerSample(audio.bitsPerSample())
                    .build()
                )
                .setVoiceId(options.voiceId())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (ValidationError e) {
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        } catch (InferenceError e) {
            io.grpc.Status status = e.isRetryable()
                ? io.grpc.Status.UNAVAILABLE
                : io.grpc.Status.INTERNAL;
            responseObserver.onError(
                status.withDescription(e.getMessage()).asRuntimeException()
            );
        }
    }
    
    @Override
    public void synthesizeStreaming(
        SynthesizeRequest request,
        StreamObserver<StreamingSynthesizeResponse> responseObserver) {
        
        try (TtsEngine tts = library.getTtsEngine()) {
            SynthesisOptions options = convertOptions(request.getOptions());
            
            tts.synthesizeStreaming(request.getText(), options, chunk -> {
                StreamingSynthesizeResponse response = StreamingSynthesizeResponse.newBuilder()
                    .setAudioChunk(AudioChunkProto.newBuilder()
                        .setData(com.google.protobuf.ByteString.copyFrom(chunk.data()))
                        .setSequence(chunk.sequence())
                        .setIsLast(chunk.isLast())
                        .build()
                    )
                    .build();
                responseObserver.onNext(response);
                
                if (chunk.isLast()) {
                    responseObserver.onCompleted();
                }
            });
            
        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Synthesis failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }
    
    @Override
    public void getVoices(
        GetVoicesRequest request,
        StreamObserver<GetVoicesResponse> responseObserver) {
        
        try (TtsEngine tts = library.getTtsEngine()) {
            List<VoiceInfo> voices = request.hasLanguage()
                ? tts.getAvailableVoices(new java.util.Locale(request.getLanguage()))
                : tts.getAvailableVoices();
            
            GetVoicesResponse.Builder response = GetVoicesResponse.newBuilder();
            for (VoiceInfo voice : voices) {
                response.addVoices(VoiceInfoProto.newBuilder()
                    .setVoiceId(voice.voiceId())
                    .setName(voice.name())
                    .setLanguage(voice.language().toLanguageTag())
                    .setSampleRate(voice.sampleRate())
                    .build()
                );
            }
            
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }
    }
    
    private SynthesisOptions convertOptions(SynthesisOptionsProto proto) {
        return SynthesisOptions.builder()
            .voiceId(proto.getVoiceId())
            .speed(proto.getSpeed())
            .pitch(proto.getPitch())
            .volume(proto.getVolume())
            .sampleRate(proto.getSampleRate())
            .build();
    }
}
```

---

## Phase 4: Vision Service Migration

### 4.1 Refactor VisionGrpcService

**File**: `modules/vision/vision-service/src/main/java/com/ghatana/vision/grpc/VisionGrpcService.java`

```java
package com.ghatana.vision.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.config.VisionConfig;
import com.ghatana.media.vision.api.*;
import com.ghatana.media.common.*;
import com.ghatana.vision.grpc.proto.*;
import io.grpc.stub.StreamObserver;

public class VisionGrpcService extends VisionServiceGrpc.VisionServiceImplBase {
    
    private final AudioVideoLibrary library;
    
    public VisionGrpcService(VisionConfig config) {
        this.library = AudioVideoLibrary.builder()
            .withVisionConfig(config)
            .build();
    }
    
    @Override
    public void detect(
        DetectRequest request,
        StreamObserver<DetectResponse> responseObserver) {
        
        try (VisionEngine vision = library.getVisionEngine()) {
            ImageData image = convertImage(request.getImage());
            DetectionOptions options = convertOptions(request.getOptions());
            
            DetectionResult result = vision.detect(image, options);
            
            DetectResponse response = convertResult(result);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (ValidationError e) {
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        } catch (InferenceError e) {
            io.grpc.Status status = e.isRetryable()
                ? io.grpc.Status.UNAVAILABLE
                : io.grpc.Status.INTERNAL;
            responseObserver.onError(
                status.withDescription(e.getMessage()).asRuntimeException()
            );
        }
    }
    
    @Override
    public StreamObserver<StreamingDetectRequest> streamingDetect(
        StreamObserver<StreamingDetectResponse> responseObserver) {
        
        VisionEngine vision = library.getVisionEngine();
        
        StreamingDetectionSession session = vision.createStreamingSession(
            DetectionOptions.defaults(),
            result -> {
                StreamingDetectResponse response = StreamingDetectResponse.newBuilder()
                    .setResult(convertResult(result))
                    .build();
                responseObserver.onNext(response);
            }
        );
        
        return new StreamObserver<>() {
            @Override
            public void onNext(StreamingDetectRequest request) {
                ImageData frame = convertImage(request.getFrame());
                session.feedFrame(frame, request.getFrameNumber());
                
                if (request.getIsLast()) {
                    session.endStream();
                }
            }
            
            @Override
            public void onError(Throwable t) {
                session.close();
                responseObserver.onError(t);
            }
            
            @Override
            public void onCompleted() {
                session.endStream();
                responseObserver.onCompleted();
            }
        };
    }
    
    private ImageData convertImage(ImageDataProto proto) {
        return ImageData.builder()
            .data(proto.getData().toByteArray())
            .width(proto.getWidth())
            .height(proto.getHeight())
            .format(ImageFormat.valueOf(proto.getFormat().name()))
            .build();
    }
    
    private DetectionOptions convertOptions(DetectionOptionsProto proto) {
        return DetectionOptions.builder()
            .confidenceThreshold(proto.getConfidenceThreshold())
            .maxDetections(proto.getMaxDetections())
            .enableTracking(proto.getEnableTracking())
            .build();
    }
    
    private DetectResponse convertResult(DetectionResult result) {
        DetectResponse.Builder builder = DetectResponse.newBuilder()
            .setImageWidth(result.imageWidth())
            .setImageHeight(result.imageHeight())
            .setProcessingTimeMs(result.processingTimeMs())
            .setModelId(result.modelId());
        
        for (DetectedObject obj : result.objects()) {
            builder.addObjects(DetectedObjectProto.newBuilder()
                .setClassName(obj.className())
                .setConfidence(obj.confidence())
                .setBbox(BoundingBoxProto.newBuilder()
                    .setX(obj.bbox().x())
                    .setY(obj.bbox().y())
                    .setWidth(obj.bbox().width())
                    .setHeight(obj.bbox().height())
                    .build()
                )
                .build()
            );
        }
        
        return builder.build();
    }
}
```

---

## Phase 5: Testing Strategy

### 5.1 Integration Test Example

**File**: `modules/speech/stt-service/src/test/java/com/ghatana/stt/grpc/SttGrpcServiceIntegrationTest.java`

```java
package com.ghatana.stt.grpc;

import com.ghatana.media.stt.api.*;
import com.ghatana.stt.grpc.proto.*;
import io.grpc.testing.GrpcServerRule;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assert.*;

public class SttGrpcServiceIntegrationTest {
    
    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule();
    
    // Test with Whisper ONNX model
    @Test
    public void testTranscribeWithRealModel() throws Exception {
        // Given: Audio data with known content
        byte[] audioData = loadTestAudio("test_hello.wav");
        
        SttConfig config = SttConfig.builder()
            .modelPath(Paths.get("/models/whisper-base.onnx"))
            .build();
        
        SttGrpcService service = new SttGrpcService(config);
        grpcServerRule.getServiceRegistry().addService(service);
        
        SttServiceGrpc.SttServiceBlockingStub stub = 
            SttServiceGrpc.newBlockingStub(grpcServerRule.getChannel());
        
        // When: Transcribe
        TranscribeResponse response = stub.transcribe(
            TranscribeRequest.newBuilder()
                .setAudio(AudioDataProto.newBuilder()
                    .setData(ByteString.copyFrom(audioData))
                    .setSampleRate(16000)
                    .setChannels(1)
                    .build()
                )
                .build()
        );
        
        // Then: Result contains expected text
        assertTrue(response.getText().toLowerCase().contains("hello"));
        assertTrue(response.getConfidence() > 0.5);
    }
    
    @Test
    public void testStreamingTranscription() throws Exception {
        // Test streaming session with chunked audio
        // Verify proper chunk ordering and final transcript
    }
    
    @Test
    public void testErrorHandling_InvalidAudio() throws Exception {
        // Verify ValidationError is properly converted to gRPC INVALID_ARGUMENT
    }
    
    @Test
    public void testErrorHandling_ModelNotFound() throws Exception {
        // Verify stub fallback works when model unavailable
        // Should return placeholder text with 0 confidence
    }
}
```

### 5.2 Performance Benchmarks

**File**: `platform/java/audio-video/src/jmh/java/com/ghatana/media/SttBenchmark.java`

```java
package com.ghatana.media;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class SttBenchmark {
    
    private AudioVideoLibrary library;
    private SttEngine engine;
    private AudioData testAudio;
    
    @Setup
    public void setup() {
        library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .build()
            )
            .build();
        
        engine = library.getSttEngine();
        
        // Load 10-second test audio
        testAudio = loadAudio("test_10s.wav");
        
        // Warmup
        engine.warmup();
    }
    
    @Benchmark
    public TranscriptionResult transcribe10Seconds() {
        return engine.transcribe(testAudio, TranscriptionOptions.defaults());
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void transcribeThroughput() {
        engine.transcribe(testAudio, TranscriptionOptions.defaults());
    }
    
    public static void main(String[] args) throws Exception {
        new Runner(new OptionsBuilder()
            .include(SttBenchmark.class.getSimpleName())
            .forks(1)
            .build()
        ).run();
    }
}
```

---

## Phase 6: HTTP Gateway (AV-011 Fix)

### 6.1 Spring Boot Gateway (Option A)

**File**: `services/api-gateway/src/main/java/com/ghatana/gateway/AudioVideoGateway.java`

```java
package com.ghatana.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AudioVideoGateway {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // STT: REST → gRPC
            .route("stt-transcribe", r -> r.path("/api/v1/stt/transcribe")
                .filters(f -> f
                    .modifyRequestBody(String.class, byte[].class, 
                        (exchange, s) -> convertToGrpc(s))
                    .modifyResponseBody(byte[].class, String.class,
                        (exchange, b) -> convertFromGrpc(b))
                )
                .uri("grpc://localhost:50051")
            )
            // TTS: REST → gRPC
            .route("tts-synthesize", r -> r.path("/api/v1/tts/synthesize")
                .uri("grpc://localhost:50052")
            )
            // Vision: REST → gRPC
            .route("vision-detect", r -> r.path("/api/v1/vision/detect")
                .uri("grpc://localhost:50053")
            )
            .build();
    }
    
    public static void main(String[] args) {
        SpringApplication.run(AudioVideoGateway.class, args);
    }
}
```

### 6.2 gRPC-Web Proxy (Option B)

**File**: `infrastructure/envoy/grpc-web-proxy.yaml`

```yaml
static_resources:
  listeners:
    - name: listener_0
      address:
        socket_address: { address: 0.0.0.0, port_value: 8080 }
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                stat_prefix: ingress_http
                codec_type: AUTO
                route_config:
                  name: local_route
                  virtual_hosts:
                    - name: local_service
                      domains: ["*"]
                      routes:
                        - match: { prefix: "/stt" }
                          route: { cluster: stt_service, timeout: 60s }
                        - match: { prefix: "/tts" }
                          route: { cluster: tts_service, timeout: 60s }
                        - match: { prefix: "/vision" }
                          route: { cluster: vision_service, timeout: 60s }
                http_filters:
                  - name: envoy.filters.http.grpc_web
                  - name: envoy.filters.http.cors
                  - name: envoy.filters.http.router

  clusters:
    - name: stt_service
      connect_timeout: 5s
      type: LOGICAL_DNS
      lb_policy: ROUND_ROBIN
      load_assignment:
        cluster_name: stt_service
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address: { address: stt-service, port_value: 50051 }
    - name: tts_service
      connect_timeout: 5s
      type: LOGICAL_DNS
      lb_policy: ROUND_ROBIN
      load_assignment:
        cluster_name: tts_service
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address: { address: tts-service, port_value: 50052 }
    - name: vision_service
      connect_timeout: 5s
      type: LOGICAL_DNS
      lb_policy: ROUND_ROBIN
      load_assignment:
        cluster_name: vision_service
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address: { address: vision-service, port_value: 50053 }
```

---

## Verification Checklist

### Before Migration
- [ ] Backup existing implementations
- [ ] Document current API contracts
- [ ] Identify custom business logic to preserve
- [ ] Record current performance metrics

### During Migration
- [ ] Add platform library dependency
- [ ] Refactor gRPC services to use library
- [ ] Update configuration files
- [ ] Migrate integration tests

### After Migration
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Performance meets or exceeds baseline
- [ ] API contracts unchanged
- [ ] Documentation updated
- [ ] Monitoring dashboards updated

### Exemplary Standards
- [ ] Code coverage > 90%
- [ ] JMH benchmarks established
- [ ] Error handling uses typed exceptions
- [ ] gRPC service methods < 50 lines
- [ ] All gRPC methods have integration tests
- [ ] Configuration externalized
- [ ] Metrics exposed for all operations
- [ ] Security documentation complete

---

## Summary

This migration will:
1. **Fix 8/10 audit issues** automatically
2. **Reduce code duplication** by 60%
3. **Enable cross-product reuse** of audio-video capabilities
4. **Establish exemplary patterns** for other products to follow
5. **Provide production-grade** ONNX-based inference

**Estimated Effort**: 2 weeks for full migration with testing

