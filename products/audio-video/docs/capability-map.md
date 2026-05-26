# Audio-Video Capability Mapping

**REG-P2-005**: Capability-to-module mapping for Audio-Video

This document maps STT/TTS/Vision/Multimodal capabilities to modules, routes/protos, tests, and gates.

## STT (Speech-to-Text) Capability

### Modules
- `products/audio-video/apps/stt-service` - Main STT service
- `products/audio-video/infrastructure/stt` - STT infrastructure adapters
- `products/audio-video/core/stt` - STT core logic

### Routes/Protos
- gRPC: `stt.v1.TranscribeAudio`
- gRPC: `stt.v1.GetTranscriptionStatus`
- gRPC: `stt.v1.ListTranscriptions`
- REST: `POST /api/v1/stt/transcribe`
- REST: `GET /api/v1/stt/transcriptions/{id}`
- REST: `GET /api/v1/stt/transcriptions`

### Tests
- Unit: `products/audio-video/core/stt/src/test/**/*Test.java`
- Integration: `products/audio-video/apps/stt-service/src/test/**/*IT.java`
- E2E: `products/audio-video/integration-tests/stt-e2e.spec.ts`

### Gates
- `media-privacy` - Validates audio privacy compliance
- `content-safety` - Validates content safety checks
- `artifact-retention` - Validates retention policy compliance
- `stt-model-validation` - Validates STT model availability

### Evidence
- `products/audio-video/docs/AUDIO_FORMAT_SPEC.md`
- `products/audio-video/docs/MEDIA_PRIVACY_AND_RETENTION_POLICY.md`

## TTS (Text-to-Speech) Capability

### Modules
- `products/audio-video/apps/tts-service` - Main TTS service
- `products/audio-video/infrastructure/tts` - TTS infrastructure adapters
- `products/audio-video/core/tts` - TTS core logic

### Routes/Protos
- gRPC: `tts.v1.SynthesizeSpeech`
- gRPC: `tts.v1.GetSynthesisStatus`
- gRPC: `tts.v1.ListSyntheses`
- REST: `POST /api/v1/tts/synthesize`
- REST: `GET /api/v1/tts/syntheses/{id}`
- REST: `GET /api/v1/tts/syntheses`

### Tests
- Unit: `products/audio-video/core/tts/src/test/**/*Test.java`
- Integration: `products/audio-video/apps/tts-service/src/test/**/*IT.java`
- E2E: `products/audio-video/integration-tests/tts-e2e.spec.ts`

### Gates
- `media-privacy` - Validates audio privacy compliance
- `content-safety` - Validates content safety checks
- `artifact-retention` - Validates retention policy compliance
- `tts-model-validation` - Validates TTS model availability

### Evidence
- `products/audio-video/docs/AUDIO_FORMAT_SPEC.md`
- `products/audio-video/docs/MEDIA_PRIVACY_AND_RETENTION_POLICY.md`

## Vision Capability

### Modules
- `products/audio-video/apps/vision-service` - Main Vision service
- `products/audio-video/infrastructure/vision` - Vision infrastructure adapters
- `products/audio-video/core/vision` - Vision core logic

### Routes/Protos
- gRPC: `vision.v1.AnalyzeImage`
- gRPC: `vision.v1.GetAnalysisStatus`
- gRPC: `vision.v1.ListAnalyses`
- REST: `POST /api/v1/vision/analyze`
- REST: `GET /api/v1/vision/analyses/{id}`
- REST: `GET /api/v1/vision/analyses`

### Tests
- Unit: `products/audio-video/core/vision/src/test/**/*Test.java`
- Integration: `products/audio-video/apps/vision-service/src/test/**/*IT.java`
- E2E: `products/audio-video/integration-tests/vision-e2e.spec.ts`

### Gates
- `media-privacy` - Validates image privacy compliance
- `content-safety` - Validates content safety checks
- `artifact-retention` - Validates retention policy compliance
- `vision-model-validation` - Validates Vision model availability

### Evidence
- `products/audio-video/docs/MEDIA_PRIVACY_AND_RETENTION_POLICY.md`

## Multimodal Capability

### Modules
- `products/audio-video/apps/multimodal-service` - Main Multimodal service
- `products/audio-video/infrastructure/multimodal` - Multimodal infrastructure adapters
- `products/audio-video/core/multimodal` - Multimodal core logic

### Routes/Protos
- gRPC: `multimodal.v1.ProcessMultimodal`
- gRPC: `multimodal.v1.GetProcessStatus`
- gRPC: `multimodal.v1.ListProcesses`
- REST: `POST /api/v1/multimodal/process`
- REST: `GET /api/v1/multimodal/processes/{id}`
- REST: `GET /api/v1/multimodal/processes`

### Tests
- Unit: `products/audio-video/core/multimodal/src/test/**/*Test.java`
- Integration: `products/audio-video/apps/multimodal-service/src/test/**/*IT.java`
- E2E: `products/audio-video/integration-tests/multimodal-e2e.spec.ts`

### Gates
- `media-privacy` - Validates multimodal privacy compliance
- `content-safety` - Validates content safety checks
- `artifact-retention` - Validates retention policy compliance
- `multimodal-model-validation` - Validates multimodal model availability

### Evidence
- `products/audio-video/docs/MEDIA_PRIVACY_AND_RETENTION_POLICY.md`

## Shared Infrastructure

### Modules
- `products/audio-video/infrastructure/auth` - Authentication/authorization
- `products/audio-video/infrastructure/observability` - Observability (metrics, traces, logs)
- `products/audio-video/infrastructure/storage` - Media storage adapters
- `products/audio-video/infrastructure/queue` - Event queue adapters

### Routes/Protos
- gRPC: `health.v1.HealthCheck`
- REST: `GET /health`
- REST: `GET /metrics`

### Tests
- Unit: `products/audio-video/infrastructure/*/src/test/**/*Test.java`
- Integration: `products/audio-video/infrastructure/*/src/test/**/*IT.java`

### Gates
- `media-privacy` - Shared privacy gate
- `content-safety` - Shared safety gate
- `artifact-retention` - Shared retention gate

### Evidence
- `products/audio-video/docs/API_DOCUMENTATION.md`
- `products/audio-video/docs/CIRCUIT_BREAKER_DIFFERENCES.md`

## Feature Completeness Matrix

| Capability | Modules | Routes | Tests | Gates | Evidence | Status |
|------------|---------|--------|-------|-------|----------|--------|
| STT | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| TTS | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Vision | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Multimodal | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Auth | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Observability | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Storage | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Queue | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |

## Notes

- All capabilities are implemented with full module, route, test, and gate coverage
- Evidence documentation is available for all capabilities
- Shared infrastructure modules support all capabilities
- All gates are enforced in CI/CD pipelines
