# ADR-014: Platform Module Consumer Audit — Premature Generalisations

**Status:** Accepted  
**Date:** 2026-03-21  
**Authors:** Platform Team  
**Phase:** BDY-8 (Boundary Implementation Plan)  
**Related:** `docs/BOUNDARY_IMPLEMENTATION_PLAN.md` Phase BDY-8

---

## Context

Phase BDY-8 required auditing `platform/java` modules with ≤ 1 real product consumer to identify premature generalisations — code built for hypothetical future reuse that has not materialised.

The boundary audit flagged: `ai-experimental`, `plugin`, `ingestion`, and `yaml-template`.

---

## Findings

### 1. `platform/java/ai-experimental` — 6 files

**Verified consumers:** 0 direct product consumers  
**Indirect consumer:** Used only as an `api(project(":platform:java:ai-experimental"))` dependency within `platform/java/ai-integration/build.gradle.kts`

**Files:** `OpenAICompletionService.java`, `ToolAwareOpenAICompletionService.java`, `OpenAIEmbeddingService.java`, `AIHttpAdapter.java`, `OpenAIService.java`, + 1 more

**Assessment:** This module is effectively a sub-module of `ai-integration`, not a standalone platform library. It was split prematurely to separate "experimental" API implementations from stable ones. The experimental flag is misleading since all 6 files are real implementations.

**Decision: MERGE into `platform/java/ai-integration`**

Action items:
- [ ] Move all 6 files from `ai-experimental/src/main` into `ai-integration/src/main` (appropriate sub-package)
- [ ] Remove `ai(project(":platform:java:ai-experimental"))` from `ai-integration/build.gradle.kts`
- [ ] Remove `include(":platform:java:ai-experimental")` from `settings.gradle.kts`
- [ ] Keep `@Experimental` annotation on moved classes if they are still unstable
- Target completion: 2026-04-30

---

### 2. `platform/java/plugin` — 37 files

**Verified consumers:** 7+ products (Finance×3, AEP, App-Platform×2, Data-Cloud, YAPPC×4)

**Assessment:** Genuinely cross-product. The plugin registry (`platform.plugin.*`) is used as the extensibility backbone across most of the codebase.

**Decision: KEEP — Not a premature generalisation**

No action required. 7+ product consumers confirms genuine platform status.

---

### 3. `platform/java/ingestion` — 22 files

**Verified consumers:** 0 external Gradle dependencies found  
**Gradle registration:** `include(":platform:java:ingestion")` exists in `settings.gradle.kts`  
**Settings comment:** None — included without documentation

**Assessment:** No product lists `platform:java:ingestion` as a dependency. The module exists but is not consumed. May be related to Data-Cloud ingestion pipeline work, but is detached from any product build.

**Decision: INVESTIGATE → likely DELETE or MOVE to `products/data-cloud`**

Action items:
- [ ] Ask Data-Cloud team: does `platform/java/ingestion` map to their ingestion pipeline work?
- [ ] If yes: move to `products/data-cloud/core/ingestion/` (product-local code)
- [ ] If no: delete the module and its settings entry
- [ ] Either way: add a comment to `settings.gradle.kts` explaining the decision
- Decision deadline: 2026-04-15

---

### 4. `platform/java/yaml-template` — (size not confirmed but expected ~10 files)

**Verified consumers:** 3 entries:
1. `platform/java/agent-core/build.gradle.kts` — `api(project(":platform:java:yaml-template"))`
2. `products/yappc/services/lifecycle/build.gradle.kts` — `implementation(project(":platform:java:yaml-template"))`
3. `products/yappc/backend/api/build.gradle.kts` — `implementation(project(":platform:java:yaml-template"))`

**Assessment:** `yaml-template` is used by `agent-core` (platform) and YAPPC (2 modules). Since `agent-core` is itself a widely shared platform module that exposes `yaml-template` as an API, all agent consumers transitively depend on it.

**Decision: KEEP — Meets 2+ consumer threshold; consider eventual merge into agent-core**

No immediate action. Long-term, if `yaml-template` grows to be agent-core-specific, merge it into `agent-core` to reduce module count.

---

## Summary Table

| Module | Files | Direct Consumers | Decision | Action |
|--------|-------|-----------------|----------|--------|
| `ai-experimental` | 6 | 0 (internal to ai-integration) | ✅ MERGE into `ai-integration` | By 2026-04-30 |
| `plugin` | 37 | 7+ products | ✅ KEEP | None |
| `ingestion` | 22 | 0 | ⚠️ INVESTIGATE → DELETE or MOVE | By 2026-04-15 |
| `yaml-template` | ~10 | 3 (agent-core + yappc×2) | ✅ KEEP | Consider future merge |

---

## Consequences

### Module Count Impact
- Merging `ai-experimental` into `ai-integration`: -1 module
- Deleting `ingestion` (if decision = delete): -1 module
- Total reduction: -2 modules (at most)

### Eliminated Complexity
- `ai-experimental` removal simplifies the `ai-integration` build graph
- `ingestion` decision eliminates an unused registered-but-disconnected module

---

## Rejected Alternatives

### Keep `ai-experimental` as separate module with `@Experimental` documentation
**Rejected because:**  
- The separation adds build complexity without module isolation value  
- Products don't import it directly — it's always transitive through ai-integration  
- Making it truly experimental is achievable with annotations within ai-integration

### Merge `plugin` into a consumer product
**Rejected because:**  
- 7+ products consume it — no single product ownership model applies  
- Platform team dependency on plugin for extensibility is genuine  
- The module is appropriately sized (37 files)
