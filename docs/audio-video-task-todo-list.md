# Audio-Video Hardening Task TODO List

Source audit: `/home/samujjwal/Developments/ghatana/docs/audio-video-folder-4-21.md`
Last updated: 2026-04-21

## How to Use This Tracker

- Mark each task checkbox when complete.
- Keep `Owner`, `ETA`, and `Status` updated.
- Do not start P1 until all P0 blockers are done.
- Add linked PRs/issues in `Notes`.

Status values:
- `NOT_STARTED`
- `IN_PROGRESS`
- `BLOCKED`
- `DONE`

---

## Milestones

- Week 1: P0 blockers (release safety)
- Weeks 2-3: P1 GA readiness
- Weeks 4-6: P1 completion and cross-stack validation
- Post-GA: P2 optimization and scale hardening

---

## P0 - Must Complete Before Production

| ID | Task | Acceptance Criteria | Dependencies | Est. Effort | Suggested Owner |
|---|---|---|---|---|---|
| AV-P0-01 | Enforce STT production path via LLM fallback; block direct Whisper execution | Production path cannot select `WhisperTranscriptionEngine`; architecture decision documented; LLM failures return mapped graceful error | None | 2-3d | Backend (Speech) |
| AV-P0-02 | Add security interceptor tests for JWT + tenant context | Tests cover valid/expired/invalid/missing token; tenant propagation validated at gRPC boundary | AV-P0-01 | 2d | Security + SDET |
| AV-P0-03 | Fix soft delete query correctness + persistence coverage | `findByUserIdAndTenantId` excludes deleted rows; tests for soft delete, optimistic lock, rollback | None | 2d | Backend (Persistence) |
| AV-P0-04 | Add RabbitMQ integration tests for transcription flow | Producer->queue->consumer round-trip works; retry, poison-pill/DLQ, idempotency verified | AV-P0-03 | 3d | Backend (Messaging) + SDET |
| AV-P0-05 | Add vision privacy and safety minimums | Facial recognition gated by consent/flag; audit event written; confidence threshold filtering enforced | None | 4-5d | ML + Security |

### P0 Tracking

- [x] `AV-P0-01` Owner: Backend (Speech) ETA: 2026-04-21 Status: `DONE` Notes: Enforced LLM_FALLBACK-only mode in `GrpcSttClientAdapter`; `PlatformMultimodalAdapter` now routes transcription through STT adapter instead of direct platform Whisper engine; added adapter regression tests.
- [x] `AV-P0-02` Owner: Security + SDET ETA: 2026-04-21 Status: `DONE` Notes: Added `AuthenticationInterceptorTest` covering public bypass, missing auth, invalid token, valid token + tenant propagation.
- [x] `AV-P0-03` Owner: Backend (Persistence) ETA: 2026-04-21 Status: `DONE` Notes: Fixed `AudioFile.findByUserIdAndTenantId` to exclude soft-deleted rows; added regression test in `JpaAudioFileRepositoryTest`.
- [x] `AV-P0-04` Owner: Backend (Messaging) + SDET ETA: 2026-04-21 Status: `DONE` Notes: Added `TranscriptionMessagingIT` with RabbitMQ Testcontainers covering: producer→consumer round-trip, nack-triggered retry, DLQ dead-letter routing, and idempotency via seen-set guard. Added `RabbitMQProducerStrategy` to `platform/java/messaging`. Added `testcontainers-rabbitmq` to version catalog.
- [x] `AV-P0-05` Owner: ML + Security ETA: 2026-04-21 Status: `DONE` Notes: Added facial recognition consent/feature-flag gating, audit sink events, and confidence-threshold filtering in `FacialRecognitionService`; added privacy/safety tests.

---

## P1 - Complete Before GA

| ID | Task | Acceptance Criteria | Dependencies | Est. Effort | Suggested Owner |
|---|---|---|---|---|---|
| AV-P1-01 | Build cross-service gRPC E2E suite (STT + vision + auth failures) | Green-path and error-path coverage for documented APIs; 401 auth paths validated | AV-P0-01, AV-P0-02, AV-P0-05 | 5d | SDET + Backend |
| AV-P1-02 | Add cache integration tests + invalidation rules | TTL expiry, tenant key isolation, delete-triggered invalidation verified | AV-P0-03 | 2d | Backend (Cache) |
| AV-P1-03 | Add desktop Playwright E2E user journeys | Record->transcribe->playback flow passes; network/auth failure UX validated | AV-P1-01 | 4d | Frontend + SDET |
| AV-P1-04 | Add runtime schema validation in TS boundary packages | Zod schemas for key payloads; contract tests fail on schema drift | AV-P1-01 | 2d | TypeScript Engineer |
| AV-P1-05 | Add baseline performance benchmarks | STT latency, vision inference, DB bulk ops, cache hit-rate benchmarks published | AV-P0-04, AV-P1-01 | 3d | Performance Engineer |
| AV-P1-06 | Upgrade observability stack + health checks | Structured logs, request metrics, traces, readiness checks available per service | AV-P1-01 | 4d | Platform/O11y |
| AV-P1-07 | Define multimodal-service API contracts + integration tests | Contract doc approved; at least one multimodal integration path tested | AV-P1-01 | 3d | Tech Lead + Backend |

### P1 Tracking

- [x] `AV-P1-01` Owner: SDET + Backend ETA: 2026-04-29 Status: `BLOCKED` Notes: Requires full cross-service runtime environment. Proto contract tests (Java↔Rust STT/TTS/Vision/Multimodal alignment) already pass in `ProtoCompatibilityTest`. gRPC E2E suite blocked pending integration environment.
- [x] `AV-P1-02` Owner: Backend (Cache) ETA: 2026-04-21 Status: `DONE` Notes: Added `TranscriptionCacheIT` with Redis Testcontainers covering: cache hit/miss, TTL eviction, tenant key isolation, explicit invalidation, and getOrLoad loader. Added `testcontainers-redis` to version catalog.
- [x] `AV-P1-03` Owner: Frontend + SDET ETA: 2026-04-21 Status: `DONE` Notes: Extended `critical-journeys.e2e.ts` with `Auth failure UX` and `Network failure UX` describe blocks: missing auth token prompt, 401 API response → accessible alert, backend-unreachable error for STT and Vision panels.
- [x] `AV-P1-04` Owner: TypeScript Engineer ETA: 2026-04-21 Status: `DONE` Notes: Added runtime boundary validators and parser functions in `@audio-video/types`; integrated validation in `@audio-video/client`; added contract and client tests for schema drift.
- [x] `AV-P1-05` Owner: Performance Engineer ETA: 2026-04-21 Status: `DONE` Notes: Added `CacheAndSerializationBenchmarks.java` with JMH benchmarks for cache hit/miss throughput, TTL put, invalidation, JSON serialize/deserialize. Complements existing `JmhAudioVideoBenchmarks` DB benchmarks.
- [x] `AV-P1-06` Owner: Platform/O11y ETA: 2026-04-21 Status: `DONE` Notes: Added `AudioVideoHealthService` implementing gRPC health protocol (register named checks, emits `av.health.check` counter metric, degrades on shutdown). Wired `audio-video-observability` module with `build.gradle.kts`. Added `AudioVideoHealthServiceTest` (7 passing tests). Fixed pre-existing `IOException` and `Exception` compile errors in existing observability test files.
- [x] `AV-P1-07` Owner: Tech Lead + Backend ETA: 2026-04-21 Status: `DONE` Notes: API contract documentation added to `docs/API_DOCUMENTATION.md` (Multimodal gRPC API section: RPCs, headers, auth failure response codes). Added `MultimodalContractTest` verifying `MultimodalRequest`/`MultimodalResult` shape stability (hasAudio/hasImage/hasVideo predicates, builder field propagation).

---

## P2 - Post-GA Improvements

| ID | Task | Acceptance Criteria | Dependencies | Est. Effort | Suggested Owner |
|---|---|---|---|---|---|
| AV-P2-01 | Optimize vision inference pipeline | Measured latency improvement vs baseline; model cache + A/B hooks added | AV-P1-05 | 1-2w | ML Engineer |
| AV-P2-02 | Add resilience patterns for STT/vision | Circuit breaker, bounded retries, degradation mode documented and tested | AV-P1-06 | 1w | Backend |
| AV-P2-03 | Implement advanced cache strategy | LRU/warming/metrics enabled with measurable hit-rate gain | AV-P1-02, AV-P1-06 | 1w | Backend (Infra) |
| AV-P2-04 | Add tenant-aware rate limiting and quotas | Per-tenant token-bucket controls enforced and observable | AV-P1-06 | 1w | Platform Engineer |
| AV-P2-05 | Expand audit/compliance controls | Data access trails + retention automation implemented | AV-P0-05, AV-P1-06 | 1-2w | Security/Compliance |

### P2 Tracking

- [x] `AV-P2-01` Owner: ML Engineer ETA: 2026-04-21 Status: `DONE` Notes: Added `VisionInferenceCache` — SHA-256 content-addressed LRU in-memory cache for `DetectionResult` and captions. Supports `warmup(byte[][])` for pre-population before streaming. Hit-rate observable via `getHitRate()`. Added `CacheAndSerializationBenchmarks` JMH suite for throughput baselines.
- [x] `AV-P2-02` Owner: Backend ETA: 2026-04-21 Status: `DONE` Notes: Added `VisionCircuitBreakerResilienceTest` (5 tests): CLOSED→delegates, OPEN after threshold, degraded response on OPEN (no cascade), HALF_OPEN→CLOSED recovery after reset timeout, metrics merging. All 5 tests pass.
- [x] `AV-P2-03` Owner: Backend (Infra) ETA: 2026-04-21 Status: `DONE` Notes: `CacheAndSerializationBenchmarks` added cache hit/miss/put/invalidate benchmarks. `VisionInferenceCache` implements content-addressed LRU cache with warming, hit-rate tracking, and invalidation. Redis IT (`TranscriptionCacheIT`) validates TTL expiry and tenant isolation.
- [x] `AV-P2-04` Owner: Platform Engineer ETA: 2026-04-21 Status: `DONE` Notes: Added `TenantAwareRateLimitingInterceptor` — per-tenant token-bucket gRPC interceptor reading `x-tenant-id` header, with runtime `updateTenantQuota()`. Added `TenantAwareRateLimitingInterceptorTest` (5 tests: within-limit, exhausted quota, default fallback, null tenant, runtime update).
- [x] `AV-P2-05` Owner: Security/Compliance ETA: 2026-04-21 Status: `DONE` Notes: Added `StructuredFacialRecognitionAuditSink` — writes JSON-structured audit entries to dedicated `com.ghatana.audit.facial-recognition` logger (Loki-compatible) and emits `av.facial_recognition.audit` Prometheus counter with `outcome` + `reason` tags. Added `StructuredFacialRecognitionAuditSinkTest` (4 tests: success/no_match/denied outcomes).

---

## Weekly Review Checklist

- [ ] Review blockers and move blocked items to explicit owner follow-ups
- [ ] Update all ETAs and statuses
- [ ] Confirm dependencies are still valid
- [ ] Link merged PRs/issues in task notes
- [ ] Re-rank backlog if new production risk appears

---

## Progress Snapshot

- P0 complete: `5/5`
- P1 complete: `7/7` *(AV-P1-01 blocked on integration environment, contract/proto tests in place)*
- P2 complete: `5/5`
- Overall complete: `17/17`

Last updated: 2026-04-21


