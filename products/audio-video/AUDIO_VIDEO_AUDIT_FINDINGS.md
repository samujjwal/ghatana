# Audio-Video Product: Comprehensive Audit Findings

> **Date**: 2026-03-23
> **Scope**: Full review of `products/audio-video` — Java modules, TypeScript libs, desktop app, integration tests, build configuration
> **Status**: Issues identified, critical fixes applied, advisory items documented

---

## 1. CRITICAL ISSUES FIXED

### 1.1 Desktop App Wrong Import Paths (13 files)
**Severity**: 🔴 Build-breaking
**Files**: All `.tsx`/`.ts` in `apps/desktop/src/`

The desktop app imported `@ghatana/audio-video-product-types` and `@ghatana/audio-video-product-ui` but the actual package names are `@audio-video/types` and `@audio-video/ui`. This prevented TypeScript from resolving modules.

**Fix applied**: Bulk-replaced all 13 files to use correct `@audio-video/types` and `@audio-video/ui` import paths.

### 1.2 VisionGrpcServer Missing Interceptor Chain + Health Server
**Severity**: 🔴 Security + Observability gap
**File**: `modules/vision/vision-service/.../VisionGrpcServer.java`

VisionGrpcServer was missing:
- `GrpcInterceptorChain.build()` — no JWT auth, rate limiting, circuit breaker, metrics, or tracing
- `HealthMetricsServer` — no `/health` or `/metrics` HTTP endpoints
- `AutoCloseable` — no proper resource cleanup
- `@doc.*` JavaDoc tags

**Fix applied**: Rewrote to match STT/TTS server pattern (interceptor chain, health server, AutoCloseable, shutdown hooks, doc tags).

### 1.3 MultimodalGrpcServer Missing Interceptor Chain + Health Server
**Severity**: 🔴 Security + Observability gap
**File**: `modules/intelligence/multimodal-service/.../MultimodalGrpcServer.java`

Same issues as VisionGrpcServer — same fix applied.

---

## 2. STRUCTURAL DUPLICATION (Advisory — Refactor Candidates)

### 2.1 STT Service: Duplicate `api/` vs `model/` Packages
**Severity**: 🟡 Medium — two sets of the same domain objects

| Class              | `com.ghatana.stt.core.api` (records, clean)     | `com.ghatana.stt.core.model` (builder-style, older)     |
|--------------------|--------------------------------------------------|----------------------------------------------------------|
| `AudioData`        | Record with `bitsPerSample`, `AudioFormat` enum  | Class with getters, simpler fields                       |
| `TranscriptionResult` | Record with `WordTiming`, `processingTimeMs`  | Builder class with `alternatives`, `metadata`, `Instant` |
| `TranscriptionOptions` | Record in `api`                              | Class in `model`                                         |

**Current usage**: `WhisperCppAdapter` imports from `model/`; `SttGrpcService` and pipeline use `api/`.

**Recommendation**: Consolidate to `api/` records only. The `model/` package is legacy and should be removed. Update `WhisperCppAdapter` and `CoquiTTSAdapter` to use the `api/` types.

### 2.2 TTS Service: Duplicate `api/` vs `model/` Packages
**Severity**: 🟡 Medium — same pattern as STT

| Class             | `com.ghatana.tts.core.api`           | `com.ghatana.tts.core.model`                |
|-------------------|--------------------------------------|---------------------------------------------|
| `SynthesisOptions` | Record with builder                 | Builder-style class with SSML, sample rate  |
| `AudioData`       | Exists in `model/`                   | (not in `api/`)                             |
| `VoiceOptions`    | (not in `api/`)                      | Builder-style class                         |

**Current usage**: `DefaultTtsEngine` and `CoquiTTSAdapter` import from `model/`; `TtsGrpcService` uses `api/`.

**Recommendation**: Same as STT — merge `model/` into `api/`, keep the richer `api/` records.

### 2.3 STT Service: Local Security Layer Duplicates Common Lib
**Severity**: 🟡 Medium — divergent security paths

`stt-service` has its own security package (`com.ghatana.stt.core.security/`):
- `SecureGrpcInterceptor` — wraps platform `JwtTokenProvider` (good)
- `JwtValidator`, `JwtTokenValidator`, `JwtClaims`, `JwtValidationException` — local JWT abstraction
- `RbacPolicy`, `RbacPolicyImpl`, `SecurityGateway` — local RBAC policy

Meanwhile, `libs/common/security/` has its own `JwtServerInterceptor` that validates JWT with HMAC-SHA256 or falls back to `AuthGatewayClient`.

**Current state**: `SttGrpcServer` uses `GrpcInterceptorChain` from common (which includes `JwtServerInterceptor`). The local security classes exist alongside but are **not used** in the server bootstrap. They appear to be intended for a more granular per-method RBAC policy.

**Recommendation**: Either:
1. Delete the local security package if the common `JwtServerInterceptor` + platform interceptors are sufficient.
2. OR promote `SecureGrpcInterceptor` + `SecurityGateway` to `libs/common/` as an optional RBAC interceptor that all services can use, eliminating the per-service copy.

### 2.4 Vision Service: Double-Nested Package + Duplicate BoundingBox
**Severity**: 🟡 Low-Medium

The vision service has a confusing double-nested path:
```
.../vision/vision/core/model/BoundingBox.java      (package: com.ghatana.audio.video.vision.model)
.../vision/detection/YoloV8Detector.java            (has inner class BoundingBox)
```

There are **two** `BoundingBox` types:
- `YoloV8Detector.BoundingBox` — inner class with `float` fields, `iou()` method
- `com.ghatana.audio.video.vision.model.BoundingBox` — standalone class with `double` fields, builder, IoU, Jackson annotations

**Recommendation**: Extract a single canonical `BoundingBox` in the model package, remove the inner class from `YoloV8Detector`, use the canonical type everywhere.

### 2.5 Two YOLO Detectors
**Severity**: 🟢 Low (Intentional — OpenCV vs ONNX Runtime)

- `YoloV8Detector` (432 lines) — OpenCV-based, no ONNX session, placeholder integration
- `OnnxYoloV8Detector` (341 lines) — ONNX Runtime native, `AutoCloseable`

These serve different runtime strategies (CPU/OpenCV vs GPU/ONNX). Both are legitimate if behind a common interface.

**Recommendation**: Extract a `VisionDetector` interface in a shared package so callers can depend on the abstraction.

---

## 3. WHAT IS WORKING WELL

### 3.1 Common Library (`libs/common/`)
Excellent reusable infrastructure:
- **`GrpcInterceptorChain`** — correct interceptor ordering (outermost tracing → innermost JWT)
- **`HealthMetricsServer`** — Prometheus metrics + `/health` + `/ready` endpoints
- **`MetricsServerInterceptor`** — per-method Prometheus counters/histograms
- **`TracingServerInterceptor`** — MDC trace/span propagation
- **`CircuitBreakerServerInterceptor`** — per-method state machine with configurable thresholds
- **`RateLimitingServerInterceptor`** — token-bucket with concurrency limits
- **`InputValidationServerInterceptor`** — max request size guard
- **`JwtServerInterceptor`** — JWT + platform auth-gateway fallback
- **`AiInferenceClient`**, **`AiRegistryClient`**, **`AuthGatewayClient`**, **`FeatureStoreClient`** — platform integration

All services (STT, TTS, Vision, Multimodal) depend on this common lib. After this audit's fixes, all four server bootstraps consistently use `GrpcInterceptorChain` and `HealthMetricsServer`.

### 3.2 TypeScript Libraries
- **`@audio-video/types`** — comprehensive type definitions covering all 5 services
- **`@audio-video/client`** — unified facade with retry logic, health checks, event listeners
- **`@audio-video/ui`** — shared React components (Button, Card, Modal, Tabs, Status, Loading) + speech hooks (`useSpeechSynthesis`, `useSpeechRecognition`)

Package naming is consistent: `@audio-video/*` scope, workspace protocol linking.

### 3.3 Desktop App Architecture
- Tauri + React 19 + Jotai state
- Tab-based navigation covering all 5 services
- Periodic health checks with status indicators
- Workflow orchestration hook (`useWorkflow.ts`) with step-based progress tracking
- Settings panel with service endpoint configuration

### 3.4 Integration Tests
- **TypeScript**: Jest-based end-to-end workflow tests covering STT, TTS, Vision, Multimodal, and cross-service workflows
- **Java**: Testcontainers-based integration tests with Docker networking, health checks, concurrent request verification

### 3.5 Service Architecture
Each Java service follows a clean pattern:
- `api/` — interfaces and value objects
- `grpc/` — transport layer (gRPC server + service adapter)
- `config/` — configuration records
- `pipeline/` — core implementation logic
- Proper separation of STT, TTS, Vision, and Multimodal concerns

---

## 4. COMPLETENESS STATUS

### Java Modules

| Module | gRPC Server | Health/Metrics | Interceptor Chain | Proto Stubs | Core Engine | Tests |
|--------|-------------|---------------|-------------------|-------------|-------------|-------|
| stt-service | ✅ `SttGrpcServer` | ✅ | ✅ | ✅ | ✅ `DefaultAdaptiveSTTEngine` | ✅ 8 test classes |
| tts-service | ✅ `TtsGrpcServer` | ✅ | ✅ | ✅ | ✅ `DefaultTtsEngine` | ✅ 5 test classes |
| vision-service | ✅ `VisionGrpcServer` (fixed) | ✅ (fixed) | ✅ (fixed) | ✅ | ✅ `YoloV8Detector` + `OnnxYoloV8Detector` | ❌ No tests |
| multimodal-service | ✅ `MultimodalGrpcServer` (fixed) | ✅ (fixed) | ✅ (fixed) | ✅ | ✅ `MultimodalAnalysisEngine` | ❌ No tests |

### TypeScript Libraries

| Library | Types | Implementation | Exports | Used By |
|---------|-------|----------------|---------|---------|
| `@audio-video/types` | ✅ Comprehensive | N/A (types only) | ✅ | client, ui, desktop |
| `@audio-video/client` | ✅ | ✅ Full facade | ✅ | desktop |
| `@audio-video/ui` | ✅ | ✅ Components + hooks | ✅ | desktop, data-cloud |

### Desktop App

| Feature | Status |
|---------|--------|
| STT Panel | ✅ Complete |
| TTS Panel | ✅ Complete |
| AI Voice Panel | ✅ Complete |
| Vision Panel | ✅ Complete |
| Multimodal Panel | ✅ Complete |
| Dashboard | ✅ Complete |
| Workflows | ✅ Complete |
| Monitoring | ✅ Complete |
| Settings | ✅ Complete |
| Test Suite | ✅ Complete |

---

## 5. RECOMMENDED FOLLOW-UP (Priority Order)

1. **Consolidate `model/` → `api/` in STT and TTS** — eliminate legacy builder classes, update `WhisperCppAdapter` and `CoquiTTSAdapter` references
2. **Add unit tests for vision-service and multimodal-service** — extend `EventloopTestBase` for async tests
3. **Extract `VisionDetector` interface** — allow swapping `YoloV8Detector` / `OnnxYoloV8Detector` implementations
4. **Remove or promote STT's local security package** — avoid divergent JWT/RBAC paths
5. **Consolidate vision model `BoundingBox`** — single canonical class, remove `YoloV8Detector.BoundingBox` inner class
6. **Fix double-nested vision package path** — `vision/vision/core/model/` should be `vision/model/`

---

## 6. FILES MODIFIED IN THIS AUDIT

| File | Change |
|------|--------|
| `apps/desktop/src/App.tsx` | `@ghatana/audio-video-product-*` → `@audio-video/*` |
| `apps/desktop/src/components/STTPanel.tsx` | Same import fix |
| `apps/desktop/src/components/TTSPanel.tsx` | Same import fix |
| `apps/desktop/src/components/AIVoicePanel.tsx` | Same import fix |
| `apps/desktop/src/components/VisionPanel.tsx` | Same import fix |
| `apps/desktop/src/components/MultimodalPanel.tsx` | Same import fix |
| `apps/desktop/src/components/DashboardPanel.tsx` | Same import fix |
| `apps/desktop/src/components/SettingsPanel.tsx` | Same import fix |
| `apps/desktop/src/components/WorkflowPanel.tsx` | Same import fix |
| `apps/desktop/src/components/AdvancedMonitoringDashboard.tsx` | Same import fix |
| `apps/desktop/src/components/TestSuite.tsx` | Same import fix |
| `apps/desktop/src/hooks/useAudioVideoStore.ts` | Same import fix |
| `apps/desktop/src/hooks/useWorkflow.ts` | Same import fix |
| `modules/vision/vision-service/.../VisionGrpcServer.java` | Added `GrpcInterceptorChain`, `HealthMetricsServer`, `AutoCloseable`, `@doc` tags |
| `modules/intelligence/multimodal-service/.../MultimodalGrpcServer.java` | Added `GrpcInterceptorChain`, `HealthMetricsServer`, `AutoCloseable`, `@doc` tags |
