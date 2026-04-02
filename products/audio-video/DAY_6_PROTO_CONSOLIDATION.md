# Day 6: Audio-Video Protobuf Consolidation & JWT Hardening

**Objective**: Consolidate proto definitions and ensure JWT security  
**Date**: 2026-04-01  
**Status**: PARTIAL (JWT done in prior session, proto consolidation in progress)

---

## Current State

### Completed ✅
- **JWT Hardening**: `JwtServerInterceptor` now fails startup when `AV_JWT_SECRET` absent and `AV_JWT_PERMISSIVE_MODE` ≠ "true"
- **JWT Tests**: 3 tests pass in `JwtServerInterceptorTest.java` (startup fail, health bypass, strict rejection)
- **Javadoc**: Updated with new startup behavior

### Remaining ⬜

#### Proto Consolidation Issue
**Problem**: Java and Rust protos diverge in package naming:
- **Java canonical**: `package com.ghatana.audio.video.tts.grpc;`
- **Rust desktop**: `package tts;`

**Files**:
- Java sources: `modules/speech/*/src/main/proto/*_service.proto`
- Rust sources: `apps/desktop/src-tauri/proto/*.proto`

**Duplication Detected**:
```
Java Protos              Rust Protos
─────────────────────  ──────────────────
tts_service.proto      tts.proto
stt_service.proto      stt.proto
vision_service.proto   vision.proto
multimodal_service.proto multimodal.proto
                       ai_voice.proto (Rust-only)
```

---

## Analysis: Should Protos Be Unified?

### Option A: Single Canonical Proto Location ❌
**Why not**: Rust and Java codegen have different conventions 
- Java uses hierarchical packages: `com.ghatana.audio.video.tts.grpc`
- Rust uses flat packages: `tts`
- Generated code structure incompatible between languages
- Adds no value to force artificial unification

### Option B: Language-Specific Protos with CI Drift Detection ✅ **CHOSEN**
**Why**: Respects language conventions while preventing accidental divergence
- Java protos follow Java package conventions
- Rust protos follow Rust module conventions
- CI test: Compare semantic content (messages, RPCs) across both
- Allow package/option differences, flag semantic divergence
- Each language regenerates independently from its source

---

## Implementation Plan

### Phase 1: Semantic Alignment (Quick)
- [ ] Compare message definitions between Java and Rust protos
- [ ] Align field names and types (should match despite package differences)
- [ ] Verify RPC methods are consistent
- [ ] Document allowed differences (package, java_options, etc.)

### Phase 2: Regeneration Verification (Quick)
- [ ] Verify Java proto codegen is current
- [ ] Verify Rust proto codegen is current
- [ ] Run `cargo build` in desktop to confirm proto builds
- [ ] Run `gradle :audio-video:build` in Java to confirm proto builds

### Phase 3: CI Drift Detection Test (Medium)
- [ ] Create proto comparison utility (Java vs Rust)
- [ ] Compare message structures semantically
- [ ] Compare RPC definitions semantically
- [ ] Add test to CI: `ProtoCompatibilityTest.java`
- [ ] Fail build if semantic drift detected (allow package differences)

### Phase 4: Documentation (Easy)
- [ ] Update README: explain proto strategy (language-specific, drift-checked)
- [ ] Add regeneration instructions for each language
- [ ] Note allowed differences (package, java_options, etc.)

---

## Files to Modify/Create

| File | Change | Status |
|------|--------|--------|
| `modules/speech/tts-service/src/main/proto/tts_service.proto` | Verify consistency | ⬜ |
| `modules/speech/stt-service/src/main/proto/stt_service.proto` | Verify consistency | ⬜ |
| `modules/vision/vision-service/src/main/proto/vision_service.proto` | Verify consistency | ⬜ |
| `modules/intelligence/multimodal-service/src/main/proto/multimodal_service.proto` | Verify consistency | ⬜ |
| `apps/desktop/src-tauri/proto/tts.proto` | Verify consistency | ⬜ |
| `apps/desktop/src-tauri/proto/stt.proto` | Verify consistency | ⬜ |
| `apps/desktop/src-tauri/proto/vision.proto` | Verify consistency | ⬜ |
| `apps/desktop/src-tauri/proto/multimodal.proto` | Verify consistency | ⬜ |
| NEW: `libs/common/src/test/java/com/ghatana/audio/video/proto/ProtoCompatibilityTest.java` | Create drift detector | ⬜ |
| `README.md` | Document proto strategy | ⬜ |

---

## Done: JWT Hardening ✅

```java
// JwtServerInterceptor startup now fails if:
// - AV_JWT_SECRET env var absent AND
// - AV_JWT_PERMISSIVE_MODE != "true"
// Code in: products/audio-video/libs/common/src/main/java/.../JwtServerInterceptor.java

@Override
public void onServerStartup() throws IllegalStateException {
  String secret = getenv("AV_JWT_SECRET");
  String permissiveMode = getenv("AV_JWT_PERMISSIVE_MODE");
  
  if (secret == null || secret.isBlank()) {
    if (!"true".equals(permissiveMode)) {
      throw new IllegalStateException(
          "AV_JWT_SECRET not configured and AV_JWT_PERMISSIVE_MODE not explicitly enabled"
      );
    }
  }
}
```

**Tests**: 3/3 passing
- `constructorThrowsWhenSecretAbsentAndNoPermissiveFlag()`
- `healthCheckBypassesValidation()`
- `strictModeRejectsMissingAuthorizationHeader()`

---

## Success Criteria

✅ JWT startup hardening enabled and tested  
✅ Proto semantic definitions aligned across Java/Rust  
✅ Package naming allowed to differ (language convention)  
✅ CI detects semantic drift automatically  
✅ All service protos and desktop app rebuild successfully  
✅ Documentation clear on proto strategy  

---

## Notes

- Proto drift detection will be JSON-based semantic comparison
- Package/option differences are intentional and OK
- Each language can regenerate independently
- Consider shared proto registry/artifact if complexity grows

