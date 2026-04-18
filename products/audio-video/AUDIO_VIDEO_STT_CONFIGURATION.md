# Audio-Video Speech-to-Text (STT) Configuration

## Overview

The Audio-Video product supports multiple STT modes via the `GrpcSttClientAdapter`. This document describes the available modes, configuration options, and how to integrate with the Whisper gRPC service.

## STT Modes

### 1. GRPC Mode (Recommended for Production)

Uses real Whisper gRPC endpoint for high-quality acoustic transcription with proper confidence scoring.

**Configuration:**
```java
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    "whisper-service.example.com",
    50051,
    GrpcSttClientAdapter.SttMode.GRPC
);
```

**Benefits:**
- Real acoustic model with high accuracy
- Low latency (typically < 1 second)
- Proper confidence scoring from Whisper
- Supports streaming and batch transcription
- Production-ready reliability

**Requirements:**
- Whisper gRPC service must be deployed
- gRPC proto stubs must be compiled from `stt_service.proto`
- Network connectivity to Whisper service

### 2. LLM_FALLBACK Mode (Current Default)

Uses AI Inference HTTP service with LLM-based transcription as a fallback.

**Configuration:**
```java
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    "whisper-service.example.com",
    50051,
    GrpcSttClientAdapter.SttMode.LLM_FALLBACK
);
```

**Benefits:**
- No Whisper gRPC service required
- Works with existing AI Inference Service
- Easy to set up for development

**Limitations:**
- Lower accuracy than real acoustic model
- Higher latency (LLM generation time)
- Confidence scores are LLM-generated, not acoustic
- Audio is base64-encoded and sent as text prompt (limited to 4KB sample)

**Use Cases:**
- Development and testing
- Environments without Whisper service
- Prototyping and validation

### 3. NOP Mode (Disabled)

Disables STT transcription entirely.

**Configuration:**
```java
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    "whisper-service.example.com",
    50051,
    GrpcSttClientAdapter.SttMode.NOP
);
```

**Use Cases:**
- Testing without transcription
- Environments where STT is not needed
- Cost control (no transcription API calls)

## Whisper gRPC Service Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `WHISPER_GRPC_HOST` | Whisper gRPC service host | `localhost` | No (uses default) |
| `WHISPER_GRPC_PORT` | Whisper gRPC service port | `50051` | No (uses default) |
| `STT_MODE` | STT mode (`grpc`, `llm-fallback`, `nop`) | `llm-fallback` | No |

### Example Configuration

**Development (LLM Fallback):**
```bash
export STT_MODE=llm-fallback
export AI_INFERENCE_SERVICE_URL=http://localhost:8083
```

**Production (Whisper gRPC):**
```bash
export WHISPER_GRPC_HOST=whisper.internal.example.com
export WHISPER_GRPC_PORT=50051
export STT_MODE=grpc
```

**Disabled:**
```bash
export STT_MODE=nop
```

## gRPC Service Integration

### Proto Definition

The Whisper gRPC service is defined in `stt_service.proto`:

```protobuf
syntax = "proto3";

package whisper;

service SttService {
  rpc Transcribe(TranscribeRequest) returns (TranscribeResponse);
}

message TranscribeRequest {
  bytes audio_data = 1;
  int32 sample_rate = 2;
  string language = 3;  // optional, auto-detect if omitted
}

message TranscribeResponse {
  string transcription = 1;
  double confidence = 2;
  repeated WordSegment segments = 3;  // optional word-level timestamps
}

message WordSegment {
  string word = 1;
  double start_time = 2;
  double end_time = 3;
  double confidence = 4;
}
```

### Generating Proto Stubs

Once `stt_service.proto` is available, generate Java gRPC stubs:

```bash
# From the audio-video multimodal-service directory
protoc --java_out=src/main/java \
       --grpc-java_out=src/main/java \
       src/main/proto/stt_service.proto
```

### Implementing gRPC Call

Replace the placeholder in `GrpcSttClientAdapter.transcribeViaGrpc()`:

```java
private AudioResult transcribeViaGrpc(byte[] audioData) {
    SttServiceGrpc.SttServiceBlockingStub stub =
        SttServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(30, TimeUnit.SECONDS);
    
    TranscribeRequest req = TranscribeRequest.newBuilder()
        .setAudioData(ByteString.copyFrom(audioData))
        .setSampleRate(16000)
        .build();
    
    TranscribeResponse resp = stub.transcribe(req);
    
    return AudioResult.builder()
        .transcription(resp.getTranscription())
        .confidence(resp.getConfidence())
        .build();
}
```

## Fallback Behavior

The STT adapter implements automatic fallback:

1. **GRPC mode failure:** Falls back to LLM_FALLBACK if gRPC call fails
2. **LLM_FALLBACK mode failure:** Returns empty result with 0.0 confidence
3. **NOP mode:** Always returns empty result with 0.0 confidence

### Fallback Example

```java
// Configure GRPC mode
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    "whisper.example.com",
    50051,
    GrpcSttClientAdapter.SttMode.GRPC
);

// If Whisper service is down, automatically falls back to LLM
AudioResult result = adapter.transcribe(audioData);

if (adapter.getCurrentMode() == GrpcSttClientAdapter.SttMode.LLM_FALLBACK) {
    // Whisper gRPC failed, LLM fallback was used
    LOG.warn("Whisper unavailable, used LLM fallback");
}
```

## Metrics

The STT adapter should emit the following metrics (to be implemented):

| Metric | Type | Description |
|--------|------|-------------|
| `stt.transcribe.latency` | Histogram | Transcription latency by mode |
| `stt.transcribe.confidence` | Histogram | Confidence score distribution |
| `stt.fallback.rate` | Gauge | Rate of fallback to LLM |
| `stt.error.rate` | Counter | Transcription error count by mode |

## Testing

### Integration Tests

Test GRPC mode with real Whisper service:

```java
@Test
void grpcModeReturnsRealTranscriptionWithConfidence() {
    GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
        "localhost",
        50051,
        GrpcSttClientAdapter.SttMode.GRPC
    );
    
    byte[] audioData = loadTestAudio();
    AudioResult result = adapter.transcribe(audioData);
    
    assertThat(result.getTranscription()).isNotEmpty();
    assertThat(result.getConfidence()).isGreaterThan(0.5);
    assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.GRPC);
}
```

Test fallback behavior:

```java
@Test
void fallbackToLlmWhenGrpcUnavailable() {
    GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
        "unavailable-host",
        50051,
        GrpcSttClientAdapter.SttMode.GRPC
    );
    
    byte[] audioData = loadTestAudio();
    AudioResult result = adapter.transcribe(audioData);
    
    // Should fall back to LLM
    assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
}
```

## Migration Notes

This refactoring is part of task P2-003. The current implementation uses LLM fallback as the primary method. To complete the migration:

1. **Generate gRPC proto stubs** from `stt_service.proto`
2. **Implement `transcribeViaGrpc()`** with real gRPC calls
3. **Add metrics collection** for latency, confidence, and fallback rate
4. **Add integration tests** for GRPC mode
5. **Update deployment configs** to use GRPC mode in production
6. **Monitor fallback rate** to ensure Whisper service availability

## Current Status

- ✅ STT mode configuration support added
- ✅ Mode-aware transcription logic implemented
- ✅ Fallback behavior defined
- ⏳ gRPC proto stubs not yet generated
- ⏳ Real gRPC call not yet implemented (placeholder exists)
- ⏳ Metrics not yet implemented
- ⏳ Integration tests not yet added

## References

- Task: P2-003 (Audio-Video: Replace LLM STT with real acoustic model)
- File: `GrpcSttClientAdapter.java`
- Proto: `stt_service.proto` (to be created)
