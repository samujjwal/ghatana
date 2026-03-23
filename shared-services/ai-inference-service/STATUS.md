# Status: AI Inference Service

**Status:** ARCHIVED — Pending stabilization  
**Last reviewed:** 2026-03-22 (boundary audit)  
**Decision:** Do not include in root build until stabilized

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
