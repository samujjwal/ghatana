# Production Remediation Plan — Path to 10/10 in Every Dimension

**Status:** In Progress  
**Last Updated:** 2026-03-16  
**Audit Base Score:** 75.4 / 100 (Grade B+)  
**Target Score:** 100 / 100 (Grade A+)  
**Estimated Effort:** 4 focused engineering weeks

---

## Score Gap Summary

| Dimension | Current | Target | Gap | Priority |
|---|---|---|---|---|
| Repository Structure | 9/10 | 10/10 | -1 | P2 |
| Product Architecture | 6/10 | 10/10 | -4 | P1 |
| Shared Library Governance | 8/10 | 10/10 | -2 | P1 |
| Dependency Architecture | 7/10 | 10/10 | -3 | P1 |
| Naming Quality | 9/10 | 10/10 | -1 | P2 |
| Domain Boundaries | 7/10 | 10/10 | -3 | P1 |
| Frontend Architecture | 7/10 | 10/10 | -3 | P1 |
| Backend Architecture | 8/10 | 10/10 | -2 | **P0** |
| Data Architecture | 8/10 | 10/10 | -2 | P2 |
| Tooling & Build | 9/10 | 10/10 | -1 | **P0** |
| Polyglot Governance | 8/10 | 10/10 | -2 | P2 |
| Reuse vs Duplication | 5/10 | 10/10 | -5 | P1 |
| Code Health | 7/10 | 10/10 | -3 | P1 |
| Testing Strategy | 7/10 | 10/10 | -3 | **P0** |
| Security & Compliance | 7/10 | 10/10 | -3 | **P0** |
| Observability | 9/10 | 10/10 | -1 | P2 |
| Deployment Strategy | 9/10 | 10/10 | -1 | **P0** |
| Documentation | 8/10 | 10/10 | -2 | P1 |
| Governance Model | 9/10 | 10/10 | -1 | **P0** |
| Future Evolution | 8/10 | 10/10 | -2 | P2 |

---

## Priority 0 — Critical Blockers (Fix This Week)

These defects constitute active production risks. Zero tolerance. Implementation starts immediately.

---

### P0-1 · Promise.getResult() Blocking Anti-Patterns

**Dimensions Affected:** Backend Architecture (-2), Code Health  
**Risk:** Event-loop deadlock, NPE, service hang under load  
**Files:** 4 files, 8 call sites

#### Root Cause

`Promise.getResult()` in ActiveJ returns the result only if the Promise is already complete — otherwise returns `null`. Calling it from a non-eventloop thread (even from inside `Promise.ofBlocking`) on a Promise that is scheduled to run on the eventloop will return `null` and trigger a NullPointerException or silently corrupt state. Within the eventloop thread itself, it causes a deadlock.

#### Violation Sites

| File | Method | Violation | Count |
|---|---|---|---|
| `platform/java/workflow-runtime/.../DurableWorkflowRuntime.java` | `executeRun` | `stateStore.save(current).getResult()` | 5 |
| `platform/java/workflow-runtime/.../DurableWorkflowRuntime.java` | `executeAction` | `operator.execute(...).getResult()` | 1 |
| `platform/java/workflow-runtime/.../DurableWorkflowRuntime.java` | `executeParallel` | `Promises.toList(promises).getResult()` | 1 |
| `platform/java/workflow-runtime/.../DurableWorkflowRuntime.java` | `executeSubWorkflow` | `start(...).getResult()` | 1 |
| `platform/java/agent-learning/.../DefaultLLMFactExtractor.java` | `doExtract` | `llmGateway.complete(request).getResult()` | 1 |
| `shared-services/auth-gateway/.../AuthGatewayLauncher.java` | `/auth/login` handler | `request.loadBody().getResult()` | 1 |
| `shared-services/auth-service/.../AuthService.java` | `/auth/token/introspect` | `request.loadBody().getResult()` | 1 |
| `shared-services/auth-service/.../AuthService.java` | `/auth/logout` | `request.loadBody().getResult()` | 1 |

#### Fix Strategy

**For `DurableWorkflowRuntime` (inside `Promise.ofBlocking` lambda):**  
Add a private `awaitBlocking(Promise<T>)` helper that uses a `CountDownLatch` to safely block the virtual thread while the eventloop pumps the Promise to completion. Replace all `.getResult()` calls with `awaitBlocking(...)`.

```java
// ❌ WRONG — may NPE if promise not yet complete
stateStore.save(current).getResult();

// ✅ CORRECT — blocks virtual thread using latch until eventloop completes promise
awaitBlocking(stateStore.save(current));
```

**For `DefaultLLMFactExtractor` (calls Promise-returning method inside `Promise.ofBlocking`):**  
Remove the `doExtract` private method. Restructure `extractFacts()` to properly chain the LLM call as a Promise, then parse the response inside a new `Promise.ofBlocking`.

```java
// ❌ WRONG — doExtract is called inside Promise.ofBlocking but calls Promise.getResult()
return Promise.ofBlocking(EXECUTOR, () -> doExtract(episode));

// ✅ CORRECT — LLM call is a proper Promise chain; parsing runs in blocking executor
return llmGateway.complete(request)
    .then(response -> Promise.ofBlocking(EXECUTOR, () -> parseFacts(response.getText(), episode)))
    .then(Promise::of, e -> {
        log.warn("[LLMFactExtractor] extraction failed: {}", e.getMessage());
        return Promise.of(List.of());
    });
```

**For `AuthGatewayLauncher` and `AuthService` (HTTP handler blocking):**  
Replace `request.loadBody().getResult().getString(UTF_8)` with a proper `request.loadBody().then(byteBuf -> { ... })` chain. The entire handler body moves inside the `.then()` lambda.

```java
// ❌ WRONG — blocks eventloop thread
String body = request.loadBody().getResult().getString(UTF_8);

// ✅ CORRECT — async body loading
return request.loadBody()
    .then(byteBuf -> {
        String body = byteBuf.getString(StandardCharsets.UTF_8);
        // ... rest of handler logic ...
    })
    .then(Promise::of, ex -> {
        LOGGER.error("Handler failed", ex);
        return Promise.of(HttpResponse.ofCode(500).withJson("{\"error\":\"Internal error\"}").build());
    });
```

**Status:** ✅ Implemented

---

### P0-2 · Hardcoded Credentials in Docker Compose Files

**Dimensions Affected:** Security & Compliance (-3), Deployment Strategy (-1)  
**Risk:** Credential exposure if compose files are shared, committed to forks, or leaked  
**Files:** 3 files, 6 credential instances

#### Violation Sites

| File | Environment Variable | Hardcoded Value |
|---|---|---|
| `shared-services/infrastructure/monitoring/docker-compose.yml` | `GF_SECURITY_ADMIN_PASSWORD` | `admin` |
| `products/virtual-org/docker-compose.yml` | `GF_SECURITY_ADMIN_PASSWORD` | `admin` |
| `products/virtual-org/docker-compose.yml` | `DATABASE_PASSWORD` | `ghatana123` |
| `products/virtual-org/docker-compose.yml` | `REDIS_PASSWORD` | `redis123` |
| `products/flashit/docker-compose.local.yml` | `MINIO_ROOT_PASSWORD` | `minioadmin123` |
| `products/flashit/docker-compose.local.yml` | `GF_SECURITY_ADMIN_PASSWORD` | `admin` |

#### Fix

Replace all hardcoded values with the `${VAR:?VAR is required}` pattern. This:
- Forces explicit environment variable setting
- Fails fast with clear error if variable is missing
- Prevents silent use of default insecure values

```yaml
# ❌ WRONG
GF_SECURITY_ADMIN_PASSWORD: admin

# ✅ CORRECT
GF_SECURITY_ADMIN_PASSWORD: ${GF_SECURITY_ADMIN_PASSWORD:?GF_SECURITY_ADMIN_PASSWORD must be set}
```

Add corresponding `.env.example` entries to each product's template file.

**Status:** ✅ Implemented

---

### P0-3 · Duplicate JWT and Logging Libraries in Version Catalog

**Dimensions Affected:** Dependency Architecture (-3), Security & Compliance  
**Risk:** Runtime classpath conflicts, unexpected behavior when both libraries initialize JWT processing  
**Files:** `gradle/libs.versions.toml`

#### Issues

1. **JWT:** Both `nimbus-jose-jwt = "9.37.3"` (canonical) and `jjwt = "0.12.3"` declared. Comments confirm jjwt has zero callers. Version mismatch with BOM (`0.12.5`).
2. **Logging:** Both `log4j2` (canonical via SLF4J bridge) and `logback = "1.4.14"` declared. Logback must not be on runtime classpath alongside Log4j2 or it creates a double-binding.

#### Fix

Remove the `jjwt` version entry and the `logback` version entry from `[versions]`. Keep the `logback-classic` library alias only if needed by a specific test (clearly document with `# TEST ONLY` comment and the exact module using it).

**Status:** ✅ Implemented

---

### P0-4 · OWASP Dependency Scan Soft-Fails in CI

**Dimensions Affected:** Security & Compliance (-3), Governance Model (-1)  
**Risk:** Known CVEs in dependencies pass CI unchallenged  
**Files:** `.github/workflows/security-scan.yml`

#### Issue

```yaml
# ❌ WRONG — vulnerabilities are reported but never block the build
run: ./gradlew dependencyCheckAnalyze --no-daemon || true
```

#### Fix

Remove `|| true`. Set CVSS failure threshold. Configure proper suppression via `config/owasp-suppressions.xml` for accepted risks.

```yaml
# ✅ CORRECT — builds fail on CRITICAL/HIGH CVEs
run: ./gradlew dependencyCheckAnalyze --no-daemon
  # Failures suppressed only via config/owasp-suppressions.xml with documented justification
```

Also fix `pnpm audit` soft-fail in npm-audit job.

**Status:** ✅ Implemented

---

### P0-5 · No Code Ownership Declaration

**Dimensions Affected:** Governance Model (-1), Documentation  
**Risk:** PRs merge without subject-matter-expert review; no escalation path for incidents  
**Files:** `.github/CODEOWNERS` (missing)

#### Fix

Create `.github/CODEOWNERS` mapping all platform modules, products, and shared infrastructure to owning teams. This enforces automatic reviewer assignment on PRs touching those paths.

**Status:** ✅ Implemented

---

## Priority 1 — High Priority (This Sprint)

---

### P1-1 · Cross-Product Dependency Violations

**Dimensions Affected:** Product Architecture (-4), Dependency Architecture (-3), Domain Boundaries (-3)  
**Files:** 5 products + platform build scripts

#### Violations

| Violating Product | Depends On | Type |
|---|---|---|
| `products/yappc` (3 modules) | `products/aep:platform` | DIRECT |
| `products/virtual-org` (2 modules) | `products/aep:platform` | DIRECT |
| `products/software-org` | `products/virtual-org` + `products/aep` | TRANSITIVE CHAIN |
| `products/aep:launcher` | `products/data-cloud:platform` | DIRECT |

#### Root Cause

AEP's `platform` module has leaked abstraction types (operator contracts, pipeline interfaces) that other products depend on directly. This inverts the product isolation principle.

#### Remediation Plan

**Phase A — Extract Shared Abstractions (Sprint 1)**
1. Create `platform:java:pipeline-api` module containing:
   - `Operator` interface (currently in `aep:platform`)
   - `OperatorResult` value object
   - `PipelineDefinition` interface
   - `OperatorCatalog` interface (SPI)
2. Migrate `products/yappc`, `products/virtual-org`, `products/software-org` to depend on `platform:java:pipeline-api` instead of `aep:platform`
3. Migrate `products/aep:launcher` to depend on `data-cloud:spi` instead of `data-cloud:platform`

**Phase B — Remove Allowlist Entries (Sprint 2)**
After migration, remove the cross-product allowlist entries from `gradle/product-isolation.gradle`.

**Phase C — Validate (Sprint 2)**
Run `./gradlew checkPlatformBoundaries` and verify clean output.

---

### P1-2 · AgentRegistry Duplication (5 Implementations)

**Dimensions Affected:** Reuse vs Duplication (-5), Shared Library Governance (-2)

| Implementation | Location | Status |
|---|---|---|
| `JdbcAgentRegistry` | `platform:java:agent-registry` | **CANONICAL — Keep** |
| `YAPPCAgentRegistry` | `yappc:core:agents` | DELETE — delegate to platform |
| `AgentRegistry` (in-memory) | `yappc:core:domain` | DELETE — delegate to platform |
| `AgentRegistryService` wrapper | `yappc:backend:api` | REFACTOR — use REST proxy to platform |
| `AgentRegistry` | `virtual-org:modules:framework` | DELETE — use platform SPI |
| `DataCloudAgentRegistry` | `data-cloud:agent-registry` | ADAPT — implement `platform:java:agent-registry` SPI |

**Remediation:**
1. Define `AgentRegistry` as a proper SPI in `platform:java:agent-registry`
2. Annotate `DataCloudAgentRegistry` to implement the SPI via `@AutoService`
3. Delete YAPPC and Virtual-Org local registries
4. Inject `platform:java:agent-registry` SPI in all former consumers

---

### P1-3 · JaCoCo Coverage Minimum Not Enforced

**Dimensions Affected:** Testing Strategy (-3), Governance Model  
**Files:** `buildSrc/src/main/groovy/com/ghatana/gradle/JacocoConventionsPlugin.groovy`

#### Fix

Add a `jacocoTestCoverageVerification` task to the `JacocoConventionsPlugin`. Set minimum line coverage at 70% (initial), escalate to 80% within 60 days.

**Status:** ✅ Implemented

---

### P1-4 · Automated Dependency Updates Missing

**Dimensions Affected:** Security & Compliance (-3), Dependency Architecture  
**Files:** `.github/dependabot.yml` (missing)

**Status:** ✅ Implemented

---

### P1-5 · Parallel UI Component Libraries (Frontend Sprawl)

**Dimensions Affected:** Frontend Architecture (-3), Reuse vs Duplication (-5)

| Library | Components | Action |
|---|---|---|
| `@ghatana/design-system` | Platform canonical | KEEP |
| `@ghatana/yappc-ui` | 50+ exports, full parallel library | AUDIT: migrate unique components to design-system; delete duplicates |
| `@ghatana/dcmaar-shared-ui-*` (3 stubs) | No content | DELETE immediately |
| `@ghatana/tutorputor-ui-shared` | Utility wrappers | MERGE: move to design-system or inline |

**State Management Inconsistencies:**
- `audio-video/apps/desktop`: using **zustand** → migrate to **Jotai**
- `dcmaar/apps/agent-react-native`: using **zustand v5** → migrate to **Jotai**

---

### P1-6 · Missing Product READMEs (8 of 13)

**Dimensions Affected:** Documentation (-2)

Products without root README:
`aep/`, `data-cloud/`, `dcmaar/`, `phr/`, `security-gateway/`, `software-org/`, `tutorputor/`, `virtual-org/`, `aura/`

Each README must include: purpose, architecture overview, setup & prerequisites, local development commands, key design decisions.

**Status:** ✅ Partially Implemented (see below)

---

## Priority 2 — Medium Priority (Next Sprint)

---

### P2-1 · Create Formal Developer Onboarding Guide

**Dimensions Affected:** Documentation (-2)  
**File:** `docs/ONBOARDING.md` (missing)

Must cover:
- Prerequisites: Java 21, Gradle, Node.js 18+, pnpm 10+, Docker
- First build: `./gradlew :platform:java:core:build`
- Running a product locally: YAPPC + AEP examples
- Key concepts: agents, operators, events, tenants, GAA lifecycle
- Running tests: `./gradlew test`
- Code standards: `@doc.*` tags, Promise discipline, ActiveJ rules
- How to add a new operator / new agent type

---

### P2-2 · Promote doc-tag-check to Build Error

**Dimensions Affected:** Governance Model (-1)  
**File:** `gradle/doc-tag-check.gradle`

Currently runs at `WARNING` level. Once all public classes have `@doc.*` tags (audit shows ~95% coverage), escalate to `ERROR` to enforce permanently.

**Prerequisite:** Run `./gradlew checkDocTags` and fix remaining ~5% of non-compliant classes first.

---

### P2-3 · WorkflowEngine Duplication (3 Implementations)

**Dimensions Affected:** Reuse vs Duplication (-5)

| Implementation | Location | Action |
|---|---|---|
| `DurableWorkflowRuntime` | `platform:java:workflow-runtime` | **CANONICAL — Keep** |
| Local workflow executor | `virtual-org:modules:workflow` | DELETE — migrate to platform |
| Inline step execution | `yappc:core:domain` | DELETE — use `DurableWorkflowRuntime` |

---

### P2-4 · Repository Structure — Archive Index

**Dimensions Affected:** Repository Structure (-1), Documentation  

`docs/archive/` contains 90+ historical documents. Add `docs/archive/INDEX.md` with a table linking name, date, and reason for archival.

---

### P2-5 · Contract Tests for gRPC/REST API Boundaries

**Dimensions Affected:** Testing Strategy (-3)  

Zero contract tests exist. Add Pact consumer/provider tests for:
- `platform:java:agent-registry` → `products:*` (AgentRegistry REST/gRPC API)
- `platform:java:event-cloud` → `products:aep` (Event streaming contract)
- `platform:java:ai-api` → `products:*` (LLMGateway contract)

---

### P2-6 · Untested Products

**Dimensions Affected:** Testing Strategy (-3), Code Health (-3)

| Product | Current Status | Action |
|---|---|---|
| `products/phr/` | Placeholder only (no code) | Confirm placeholder, add README |
| `products/aura/` | Docs folder only (no code) | Confirm placeholder, add README |
| `products/dcmaar/` | Extensive code, minimal Java tests | Add `EventloopTestBase` tests for all service classes |

---

### P2-7 · javax/jakarta Dual Dependency Cleanup

**Dimensions Affected:** Dependency Architecture (-3)  

Both `javax-annotation = "1.3.2"` and `jakarta-annotation = "2.1.1"` are in `libs.versions.toml`. Create a migration plan:
1. Identify all usages of `javax.*` imports
2. Replace with `jakarta.*` equivalents module by module
3. Remove `javax-annotation` and `javax-inject` from version catalog

---

### P2-8 · ActiveJ RC Version — Watch for GA

**Dimensions Affected:** Dependency Architecture  

`activej = "6.0-rc2"` is a Release Candidate. Monitor the [ActiveJ releases](https://github.com/activej/activej/releases) page and upgrade to GA as soon as available. Add a Dependabot entry for it.

---

## Implementation Tracking Checklist

### P0 Fixes

- [x] Fix `DurableWorkflowRuntime.java` — 8 × `.getResult()` → `awaitBlocking()`
- [x] Fix `DefaultLLMFactExtractor.java` — restructure to proper Promise chain
- [x] Fix `AuthGatewayLauncher.java` — fix login handler body loading
- [x] Fix `AuthService.java` — fix introspect and logout body loading
- [x] Fix `products/virtual-org/docker-compose.yml` — replace hardcoded `DATABASE_PASSWORD`, `REDIS_PASSWORD`, `GF_SECURITY_ADMIN_PASSWORD`
- [x] Fix `shared-services/infrastructure/monitoring/docker-compose.yml` — replace `GF_SECURITY_ADMIN_PASSWORD`
- [x] Fix `products/flashit/docker-compose.local.yml` — replace `MINIO_ROOT_PASSWORD`, `GF_SECURITY_ADMIN_PASSWORD`
- [x] Remove `jjwt` version from `gradle/libs.versions.toml`
- [x] Remove `logback` from `[versions]` (keep alias with `# TEST ONLY` guard)
- [x] Remove `|| true` OWASP soft-fail from `.github/workflows/security-scan.yml`
- [x] Remove `|| true` pnpm audit soft-fail
- [x] Create `.github/CODEOWNERS`

### P1 Fixes

- [x] Add JaCoCo coverage verification (70% minimum) to `JacocoConventionsPlugin.groovy`
- [x] Create `.github/dependabot.yml`
- [ ] Extract `platform:java:pipeline-api` module from `products:aep:platform`
- [ ] Migrate cross-product consumers to `platform:java:pipeline-api`
- [ ] Remove cross-product allowlist entries from `product-isolation.gradle`
- [x] Fix AgentRegistry @doc.layer in `virtual-org` (product → correct layer, added @apiNote)
- [x] Implement `@ghatana/dcmaar-shared-ui-*` packages (real adapter implementations, not deleted — they are actively used)
- [ ] Audit `@ghatana/yappc-ui` for components to promote to `@ghatana/design-system`
- [x] Migrate `audio-video/desktop` from zustand to Jotai
- [x] Migrate `dcmaar/agent-react-native` from zustand v5 to Jotai
- [x] Add READMEs to: `aep/`, `data-cloud/`, `dcmaar/`, `phr/`, `security-gateway/`, `tutorputor/`, `virtual-org/`, `aura/`

### P2 Fixes

- [x] Create `docs/ONBOARDING.md`
- [x] Promote `doc-tag-check` to ERROR for NEW files (baseline of 463 existing violations grandfathered in `gradle/doc-tag-baseline.txt`; any new file without tags fails build immediately)
- [ ] Delete `virtual-org:modules:workflow` WorkflowEngine (use platform)
- [ ] Delete `yappc:core:domain` inline step execution (use `DurableWorkflowRuntime`)
- [ ] Create `docs/archive/INDEX.md`
- [ ] Add Pact contract tests for top 3 API boundaries
- [ ] Add `EventloopTestBase` tests for DCMAAR service classes
- [ ] Create migration plan for `javax` → `jakarta`
- [ ] Monitor ActiveJ 6.0 GA release and upgrade

---

## Estimated Score After All P0 + P1 Fixes

| Dimension | Current | After P0 | After P0+P1 | After All |
|---|---|---|---|---|
| Backend Architecture | 8 | **10** | 10 | 10 |
| Security & Compliance | 7 | **9** | 9 | 10 |
| Deployment Strategy | 9 | **10** | 10 | 10 |
| Governance Model | 9 | **10** | 10 | 10 |
| Product Architecture | 6 | 6 | **9** | 10 |
| Reuse vs Duplication | 5 | 5 | **8** | 10 |
| Shared Library Governance | 8 | 8 | **9** | 10 |
| Dependency Architecture | 7 | **8** | **9** | 10 |
| Frontend Architecture | 7 | 7 | **9** | 10 |
| Domain Boundaries | 7 | 7 | **9** | 10 |
| Testing Strategy | 7 | 7 | **8** | 10 |
| Documentation | 8 | **9** | **9** | 10 |
| **Weighted Total** | **75.4** | **~82** | **~91** | **100** |

---

*This document is the single source of truth for the post-audit remediation effort.  
Update checkboxes as items are completed. Link PRs to each item.*
