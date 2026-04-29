# ADR-STT-001: Whisper STT Engine as External gRPC Dependency

**Status:** Accepted  
**Date:** 2026-04-29  
**Deciders:** Audio-Video Platform Team  
**Tracking:** GH-90000

---

## Context

The audio-video product requires acoustic speech-to-text (STT) transcription with high accuracy, real word timings, and speaker diarization. Two implementation strategies were evaluated:

1. **Local ONNX/JNI embedding** — bundle a Whisper ONNX model inside the JVM process, calling it via JNI or ONNX Runtime Java bindings.
2. **External gRPC microservice** — deploy a standalone Whisper server that exposes the `STTService` gRPC contract; the JVM adapter calls it over the network.

The `WhisperTranscriptionEngine` class was a planned implementation of option 1. It currently throws `UnsupportedOperationException` for `transcribe()` and is marked `@Deprecated(since = "1.0", forRemoval = true)`.

---

## Decision

**We adopt option 2: external gRPC microservice**, governed by the `stt_service.proto` contract.

The canonical client-side entry point is `GrpcSttClientAdapter` with `SttMode.GRPC` (production) or `SttMode.LLM_FALLBACK` (development / no Whisper service available).

`WhisperTranscriptionEngine` is **permanently deprecated** and will be removed when GH-90000 is closed.

---

## Rationale

| Concern | Local ONNX/JNI | External gRPC (chosen) |
|---|---|---|
| JVM stability | JNI segfault risk in shared heap | isolated process; JVM never crashes |
| Model hot-swap | requires app restart | `LoadModel`/`UnloadModel` RPCs |
| GPU utilisation | CUDA context per JVM instance | single GPU context on Whisper host |
| Scale-out | must scale JVM heap + GPU together | STT service scales independently |
| Implementation cost | XL (40+ hours), needs ONNX Runtime bindings | S (deploy existing server) |
| Streaming | complex JNI streaming | bidirectional gRPC stream (already in proto) |

---

## External Dependency Contract

### gRPC Service: `STTService`

Proto package: `com.ghatana.audio.video.stt.grpc`  
Proto source: `stt_service.proto` (tracked in `platform/contracts/proto/`)

#### Mandatory RPCs (must be implemented by any conforming Whisper service)

| RPC | Request | Response | SLA |
|---|---|---|---|
| `Transcribe` | `TranscribeRequest` | `TranscribeResponse` | < 5 s per 60 s audio |
| `StreamTranscribe` | `stream AudioChunk` | `stream Transcription` | < 300 ms first partial |
| `HealthCheck` | `HealthCheckRequest` | `HealthCheckResponse` | < 100 ms |
| `GetStatus` | `StatusRequest` | `StatusResponse` | < 100 ms |

#### Optional RPCs (required for model management features)

- `LoadModel` / `UnloadModel` / `ListModels`
- `AdaptModel` / `SubmitCorrection`
- `CreateProfile` / `GetProfile` / `UpdateProfile`

#### Required Response Fields

`TranscribeResponse` must populate:
- `text` — UTF-8 transcript
- `confidence` — acoustic confidence in [0.0, 1.0]
- `word_timings` — at minimum start/end offsets per word
- `processing_time_ms` — wall-clock duration

`Transcription` (streaming) must populate:
- `text` — partial or final transcript
- `is_final` — true when segment is complete
- `confidence` — per-segment score

#### Error semantics

| gRPC Status | Meaning |
|---|---|
| `OK` | Transcription completed; `text` may be empty for silent audio |
| `INVALID_ARGUMENT` | Malformed audio, unsupported sample rate, or empty `audio_data` |
| `RESOURCE_EXHAUSTED` | Engine overloaded; caller should retry with backoff |
| `UNAVAILABLE` | Service starting up or shutting down |
| `INTERNAL` | Unexpected model error; escalate to on-call |

---

## Integration Guide

### Production (`SttMode.GRPC`)

```java
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    System.getenv("WHISPER_GRPC_HOST"),          // e.g. "whisper.internal.ghatana.dev"
    Integer.parseInt(System.getenv("WHISPER_GRPC_PORT")), // e.g. 50051
    GrpcSttClientAdapter.SttMode.GRPC
);
```

Required environment variables:

| Variable | Default | Description |
|---|---|---|
| `WHISPER_GRPC_HOST` | — | Hostname of deployed Whisper gRPC service |
| `WHISPER_GRPC_PORT` | `50051` | gRPC port |
| `ENABLE_STT_GRPC` | `false` | Feature flag; must be `true` to enable GRPC mode |
| `WHISPER_GRPC_DEADLINE_MS` | `10000` | Per-call deadline in milliseconds |

### Development / CI (`SttMode.LLM_FALLBACK`)

No Whisper service is required. Transcription is routed to the Ghatana AI Inference Service (base64 audio prompt). Accuracy is lower than the acoustic model; confidence scores are LLM-generated.

```java
GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
    "ignored-host", 50051,
    GrpcSttClientAdapter.SttMode.LLM_FALLBACK
);
```

---

## Acceptance Criteria for External Whisper Service

A Whisper gRPC service is conformant when:

1. `HealthCheck` returns `HEALTH_STATUS_HEALTHY` within 100 ms of startup.
2. `Transcribe` with a 10-second PCM clip at 16 kHz 16-bit mono returns:
   - `confidence ≥ 0.80` for English speech
   - `processing_time_ms ≤ 5000`
   - non-empty `word_timings`
3. `StreamTranscribe` emits at least one partial `Transcription` within 300 ms of the first `AudioChunk`.
4. Concurrent load of 10 simultaneous `Transcribe` calls completes without `RESOURCE_EXHAUSTED`.
5. `GetStatus` reports `ENGINE_STATE_READY` after model is loaded.

These criteria must be covered by integration tests under `products/audio-video/integration-tests/` before promoting to production.

---

## Consequences

- `WhisperTranscriptionEngine` is deprecated and scheduled for deletion with GH-90000.
- All enabled STT tests exercise `GrpcSttClientAdapter` (LLM_FALLBACK) or the gRPC integration path — not `WhisperTranscriptionEngine`.
- 15 `@Disabled` tests in `stt-service` remain disabled until a conformant Whisper gRPC service is deployed in the integration test environment (GH-90000).
- The `stt_service.proto` contract is the single authoritative interface between the JVM adapter and the Whisper engine. Changes to the proto require a versioned migration following the platform contracts policy.
