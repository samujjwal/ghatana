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

## Data Cloud Media Artifact Operations

### Modules
- `products/data-cloud/delivery/api` - MediaArtifactController
- `products/data-cloud/planes/data/entity` - MediaArtifactRecord, MediaProcessingJob
- `products/data-cloud/delivery/api` - MediaArtifactRepository, MediaArtifactEventEmitter

### Routes/Protos
- REST: `POST /api/v1/media/artifacts` - Register media artifact
- REST: `GET /api/v1/media/artifacts` - List media artifacts
- REST: `GET /api/v1/media/artifacts/{artifactId}` - Get media artifact
- REST: `DELETE /api/v1/media/artifacts/{artifactId}` - Delete media artifact
- REST: `POST /api/v1/media/artifacts/{artifactId}/transcribe` - Request transcription
- REST: `POST /api/v1/media/artifacts/{artifactId}/analyze` - Request vision analysis

### Tests
- Unit: `products/data-cloud/delivery/api/src/test/**/MediaArtifactControllerTest.java`
- Unit: `products/data-cloud/delivery/api/src/test/**/MediaArtifactControllerTenantSecurityTest.java`
- Unit: `products/data-cloud/delivery/launcher/src/test/**/MediaArtifactProcessingJobTest.java`
- Unit: `products/data-cloud/delivery/launcher/src/test/**/StorageProfileHandlerTest.java`

### Gates
- `media-privacy` - Validates media privacy compliance
- `tenant-isolation` - Validates tenant-scoped access
- `datacloud-access` - Validates Data Cloud access permissions
- `artifact-retention` - Validates retention policy compliance

### Data Cloud Integration Status
- **metadata registration**: complete - MediaArtifactController with tenant extraction, MediaArtifactRecord with status/processingState/retentionUntil fields
- **processing request**: complete - Transcription/analysis routes implemented with nested routing and proper permissions
- **durable job lifecycle**: complete - MediaProcessingJob entity with comprehensive lifecycle management and tenant isolation
- **result retrieval**: complete - MediaProcessingResultRecord entity with validation, quality metrics, and data redaction
- **event emission**: complete - MediaArtifactEventEmitter with typed lifecycle events for media processing
- **privacy/retention enforcement**: complete - Consent validation, retention policy enforcement, and PII redaction implemented

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
| Data Cloud Media Artifacts | ✅ | ✅ | ✅ | ✅ | ✅ | Complete |
| Auth | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Observability | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Storage | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |
| Queue | ✅ | ✅ | ✅ | ✅ | ✅ | Implemented |

## Notes

- STT, TTS, Vision, and Multimodal capabilities are implemented with full module, route, test, and gate coverage
- Data Cloud Media Artifacts integration is complete - controller, entities (MediaArtifactRecord, MediaProcessingJob, MediaProcessingResultRecord), event emitter, routes, and privacy/retention enforcement are fully implemented
- Privacy/retention enforcement is complete with consent validation, retention policy enforcement, and PII redaction
- Evidence documentation is available for all capabilities
- Shared infrastructure modules support all capabilities
- All gates are enforced in CI/CD pipelines
- Data Cloud media routes are registered in audio-video-capabilities.yaml with proper tenant scope and policies
- STT, TTS, Vision, and Multimodal services have Data Cloud integration with event bridge tests and security/observability conventions
