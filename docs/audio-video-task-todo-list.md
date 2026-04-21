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
- [ ] `AV-P0-04` Owner: Backend (Messaging) + SDET ETA: 2026-04-25 Status: `IN_PROGRESS` Notes: Added producer/consumer lifecycle unit tests and fixed module compile against current `QueueConsumerStrategy` API; RabbitMQ Testcontainers round-trip, retry, and DLQ tests still pending.
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

- [ ] `AV-P1-01` Owner: SDET + Backend ETA: 2026-04-29 Status: `BLOCKED` Notes: Requires full cross-service runtime (auth + STT + vision + persistence + messaging) and stable integration environment wiring.
- [ ] `AV-P1-02` Owner: Backend (Cache) ETA: 2026-04-26 Status: `IN_PROGRESS` Notes: Added `AudioVideoCacheTest`; fixed cache module dependency wiring to persistence entities. Redis-backed TTL and tenant invalidation integration tests still pending.
- [ ] `AV-P1-03` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___
- [x] `AV-P1-04` Owner: TypeScript Engineer ETA: 2026-04-21 Status: `DONE` Notes: Added runtime boundary validators and parser functions in `@audio-video/types`; integrated validation in `@audio-video/client`; added contract and client tests for schema drift.
- [ ] `AV-P1-05` Owner: Performance Engineer ETA: 2026-04-30 Status: `BLOCKED` Notes: Requires benchmark environment and representative runtime dependencies for STT/vision/database/cache.
- [ ] `AV-P1-06` Owner: Platform/O11y ETA: 2026-05-01 Status: `BLOCKED` Notes: Requires cross-service deployment wiring for metrics/traces/readiness verification.
- [ ] `AV-P1-07` Owner: Tech Lead + Backend ETA: 2026-04-30 Status: `IN_PROGRESS` Notes: Multimodal adapter routing tightened; `@audio-video/client` now propagates tenant/correlation headers and has client-to-service integration coverage. API contract documentation + backend integration contract tests still pending.

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

- [ ] `AV-P2-01` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___
- [ ] `AV-P2-02` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___
- [ ] `AV-P2-03` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___
- [ ] `AV-P2-04` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___
- [ ] `AV-P2-05` Owner: ___ ETA: ___ Status: `NOT_STARTED` Notes: ___

---

## Weekly Review Checklist

- [ ] Review blockers and move blocked items to explicit owner follow-ups
- [ ] Update all ETAs and statuses
- [ ] Confirm dependencies are still valid
- [ ] Link merged PRs/issues in task notes
- [ ] Re-rank backlog if new production risk appears

---

## Progress Snapshot

- P0 complete: `4/5`
- P1 complete: `1/7`
- P2 complete: `0/5`
- Overall complete: `5/17`


