# Status: AI Inference Service

**Status:** ARCHIVED — Pending stabilization  
**Last reviewed:** 2026-03-26 (SHM audit remediation)  
**Decision:** Do not include in root build until stabilized

## What this is

The AI Inference Service was intended to be a **deployable, shared inference endpoint**
for all products — a managed wrapper around `platform:java:ai-integration` that provides:
- Centralised model routing (AIInferenceHttpAdapter)
- Shared deployment with observability and rate limiting (AIInferenceServiceLauncher)

## Why it is disabled

The build was never stabilized after the `platform:java:ai-integration` module underwent
significant refactoring (Session 2, merge of ai-registry + ai-observability + ai-feature-store).
The adapter imports became stale.

**Root cause:** `AIInferenceHttpAdapter.java` needs updating to the current `platform:java:ai-integration` API surface.

## Changes made during SHM audit (2026-03-26)

While this module is archived, the following improvements were applied to prepare it for re-activation:

- **SHM-001 FIXED**: Removed 320-line duplicate class body that existed from lines 408–727. Retained the superior version (lines 1–407) with JWT auth, input validation, proper error handling.
- **SHM-003 FIXED**: Added `sanitizeText()` helper removing null bytes and ASCII control chars. Applied to all text/prompt inputs.
- **SHM-005 FIXED**: Created `AiRateLimiter.java` (per-tenant Guava Cache, configurable via `AI_RATE_LIMIT_RPM`). Integrated into all three inference handlers. Returns 429 with platform `ErrorResponse`.
- **SHM-006 FIXED**: Imported `com.ghatana.platform.http.server.response.ErrorResponse`. Rate-limit errors use `ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "...")`.
- **SHM-008 FIXED**: Added structured audit logging: `requestId`, `operation`, `tenant`, input sizes, `tokensUsed`, `model`, `durationMs` on each inference request/response.
- **Added** package-private constructor for injectable `AiRateLimiter` (testability).
- **Added** `AiRateLimiterTest.java` (13 tests) and extended `AIInferenceHttpAdapterTest.java` (+8 tests including rate limit 429, missing field validation, batch size limits).

## To re-enable

1. Update `AIInferenceHttpAdapter.java` imports to current `platform:java:ai-integration` APIs
2. Update `AIInferenceServiceLauncher.java` to current APIs
3. Run `./gradlew :shared-services:ai-inference-service:build` and fix remaining compilation errors
4. Un-comment the include in `settings.gradle.kts`
5. Update this file status to `ACTIVE`

## Current state in settings.gradle.kts

```kotlin
// include(":shared-services:ai-inference-service")   // ARCHIVED: build not stabilised
// See shared-services/ai-inference-service/STATUS.md for re-enable instructions
```

## Decision record

Decision to formally archive (not delete) taken on 2026-03-22 boundary audit.
Source code preserved for future stabilization. Product teams currently use
`platform:java:ai-integration` directly rather than routing through this service.


## What this is

The AI Inference Service was intended to be a **deployable, shared inference endpoint** for all products — a managed wrapper around `platform:java:ai-integration` that provides:
- Centralised model routing (AIInferenceHttpAdapter)
- Shared deployment with observability and rate limiting (AIInferenceServiceLauncher)

## Why it is disabled

The build was never stabilized after the `platform:java:ai-integration` module underwent significant refactoring (Session 2, merge of ai-registry + ai-observability + ai-feature-store). The adapter imports became stale.

**Root cause:** `AIInferenceHttpAdapter.java` needs updating to the current `platform:java:ai-integration` API surface.

## To re-enable

1. Update `AIInferenceHttpAdapter.java` and `AIInferenceServiceLauncher.java` to current `platform:java:ai-integration` APIs
2. Add integration tests extending `EventloopTestBase`
3. Verify build passes: `./gradlew :shared-services:ai-inference-service:build`
4. Un-comment the include in `settings.gradle.kts`
5. Remove this STATUS.md or update it to `Status: ACTIVE`

## Current state in settings.gradle.kts

```kotlin
// include(":shared-services:ai-inference-service")   // ARCHIVED: build not stabilised
// See shared-services/ai-inference-service/STATUS.md for re-enable instructions
```

## Decision record

Decision to formally archive (not delete) taken on 2026-03-22 boundary audit.  
Source code preserved for future stabilization. Product teams currently use  
`platform:java:ai-integration` directly rather than routing through this service.
