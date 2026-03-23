# Ghatana Monorepo — Boundary Remediation Plan 2026

**Source Audit:** `GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md` (2026-03-21)  
**Plan Created:** 2026-03-21  
**Owner:** Architecture Team  
**Status:** ACTIVE — Phase 1 in progress

---

## Current Baseline (as of 2026-03-21)

The codebase entered this plan with the following verified state (from previous
implementation sessions):

| Metric | Baseline | Current | Target |
|--------|----------|---------|--------|
| Gradle modules | ~210 | ~183 | ~155 |
| Platform Java modules | 32 | 30 | 20 |
| Circular dependency loops | 463 claimed | **0 actual** | 0 |
| Architecture score | — | 80/100 | 95/100 |
| CI coverage (module tests) | partial | ✅ path-gated | ✅ maintained |
| Agent registry design | fragmented | ✅ centralized (AEP) | ✅ maintained |
| Cross-product dep violations | unknown | 0 unapproved | 0 |

**Already completed (not repeated in this plan):**
- Agent Registry centralization with SPI (Phases 0–7 of consolidated plan)
- `agent-runtime` merged: agent-memory + agent-learning + agent-dispatch + agent-resilience → 1
- `ai-integration` merged: registry + observability + feature-store → 1
- `kernel-capabilities` merged: 7 kernel sub-modules → 1
- ArchUnit boundary guards (`PlatformBoundaryRulesTest`)
- CI architecture gates (`check-java-architecture.sh`, `scripts/check-cross-product-deps.sh`)
- Governance freeze rules (`docs/GOVERNANCE_FREEZE_RULES.md`)
- AEP `platform-agent`, `platform-bundle`, `platform-core`, `platform-analytics` modules created
- All 56 consolidated plan tasks (100%)

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ⬜ | Not Started |
| 🔵 | In Progress |
| ✅ | Completed |
| ❌ | Blocked |
| ⏭️ | Deferred |
| 🔴 | P0 — Release blocker |
| 🟠 | P1 — Must do this phase |
| 🟡 | P2 — Should do this phase |
| 🟢 | P3 — Nice to have |

---

## Phase 1 — Immediate Action: Fix Critical Boundary Violations (Weeks 1–4)

**Goal:** Eliminate the remaining 5 fragmented-module clusters that the audit
identified. Each is a 1–3 day drop-in consolidation with no API changes — only build
graph simplification.

### OBSV-1 🟠 — Consolidate Observability Modules (3 → 1)

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (1–2 days)  
**Ticket ref:** #boundary/obsv-1

**Problem:**  
`platform/java/observability`, `platform/java/observability-http`, and
`platform/java/observability-clickhouse` are a single logical concern split
across three modules. `observability-http` already depends on both others —
making it the "assembly" module that almost everyone actually consumes.

| Module | Java files | Role |
|--------|-----------|------|
| `platform:java:observability` | 26 | Core metrics + tracing types |
| `platform:java:observability-http` | 33 | HTTP ingest/query layer + bridges both above |
| `platform:java:observability-clickhouse` | 18 | ClickHouse `TraceStorage` impl |

**Steps:**

1. Copy `observability-http/src` and `observability-clickhouse/src` source trees
   into `observability/src` under their existing sub-packages
   (`com.ghatana.platform.observability.http.*`,
   `com.ghatana.platform.observability.clickhouse.*`). No package renames needed.
2. Merge the ClickHouse JDBC driver dependency into `observability/build.gradle.kts`:
   ```kotlin
   implementation("com.clickhouse:clickhouse-jdbc:0.6.0:all")
   ```
3. Absorb the HTTP module's transitive deps (`platform:java:http`) into the unified build.
4. Search for all `project(":platform:java:observability-http")` and
   `project(":platform:java:observability-clickhouse")` references across build files
   and replace with `project(":platform:java:observability")`.
   - Known consumers: `products/app-platform/kernel/observability-sdk/build.gradle.kts`,
     `products/app-platform/kernel/platform-sdk/build.gradle.kts`,
     `platform/java/observability-http/build.gradle.kts` (self — remove).
5. Remove `include(":platform:java:observability-http")` and
   `include(":platform:java:observability-clickhouse")` from `settings.gradle.kts`.
6. Delete the two now-empty directories.
7. Run `./gradlew :platform:java:observability:test` — verify all 10 tests pass.

**Verification:**
```bash
./gradlew :platform:java:observability:build
grep -r "observability-http\|observability-clickhouse" . --include="*.kts" | grep -v "build/"
# → 0 results
```

---

### WF-1 🟠 — Consolidate Workflow Modules (3 → 1)

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (1–2 days)  
**Ticket ref:** #boundary/wf-1  
**ADR:** ADR-007 (extend with workflow consolidation rationale)

**Problem:**  
`platform/java/workflow`, `platform/java/workflow-runtime`, and
`platform/java/workflow-jdbc` contain a coherent workflow engine split across three
modules. All three share the `com.ghatana.platform.workflow.*` package root. The
`workflow-runtime` module already depends on `workflow`, and `workflow-jdbc` depends
on both.

| Module | Java files | Role |
|--------|-----------|------|
| `platform:java:workflow` | 25 | Operators, state stores, lifecycle events |
| `platform:java:workflow-runtime` | 27 | Durable execution, CEL eval, step registry |
| `platform:java:workflow-jdbc` | 10 | JDBC-backed state store + wait coordinator |

**Steps:**

1. Merge `workflow-runtime/src` into `workflow/src`. Sub-package:
   `com.ghatana.platform.workflow.runtime.*` (already the existing namespace).
2. Merge `workflow-jdbc/src` into `workflow/src`. Sub-package:
   `com.ghatana.platform.workflow.jdbc.*`.
3. Merge all unique dependencies from `workflow-runtime/build.gradle.kts` and
   `workflow-jdbc/build.gradle.kts` into `workflow/build.gradle.kts`:
   - CEL (Common Expression Language) library
   - Flyway / JDBC API dependencies
4. Find and replace all `project(":platform:java:workflow-runtime")` and
   `project(":platform:java:workflow-jdbc")` references with
   `project(":platform:java:workflow")`.
   - Primary consumers: YAPPC (agents/workflow, lifecycle, services),
     virtual-org, finance (rules-engine, reconciliation, oms, post-trade,
     corporate-actions, regulatory-reporting), software-org.
5. Remove `include(...)` entries for `workflow-runtime` and `workflow-jdbc` from
   `settings.gradle.kts`.
6. Delete the two now-empty directories.
7. Run `./gradlew :platform:java:workflow:test`.

**Verification:**
```bash
./gradlew :platform:java:workflow:build
grep -r "workflow-runtime\|workflow-jdbc" . --include="*.kts" | grep -v "build/"
# → 0 results
```

---

### AI-1 🟠 — Merge `ai-experimental` into `ai-integration`

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** XS (0.5 days)  
**Deadline:** 2026-04-30 (from ADR-014)  
**ADR:** ADR-014 §1

**Problem:**  
`platform/java/ai-experimental` has 6 implementation files and zero direct product
consumers. It is only consumed as `api(...)` inside `platform/java/ai-integration`.
It exists purely as an artificial split.

| File | Target sub-package |
|------|-------------------|
| `OpenAICompletionService.java` | `com.ghatana.ai.providers.openai` |
| `ToolAwareOpenAICompletionService.java` | `com.ghatana.ai.providers.openai` |
| `OpenAIEmbeddingService.java` | `com.ghatana.ai.providers.openai` |
| `AIHttpAdapter.java` | `com.ghatana.ai.http` |
| `OpenAIService.java` | `com.ghatana.ai.providers.openai` |

**Steps:**

1. Move all 6 files from `ai-experimental/src/main/java/` into
   `ai-integration/src/main/java/` under appropriate sub-packages.
2. Retain any `@Experimental` annotations on the moved classes.
3. Remove `api(project(":platform:java:ai-experimental"))` from
   `ai-integration/build.gradle.kts`.
4. Remove `include(":platform:java:ai-experimental")` from `settings.gradle.kts`.
5. Delete `platform/java/ai-experimental/` directory.
6. Run `./gradlew :platform:java:ai-integration:test`.

**Verification:**
```bash
./gradlew :platform:java:ai-integration:build
find . -path "*/ai-experimental*" | grep -v "build/" | grep -v ".git"
# → 0 results
```

---

### ING-1 🟠 — Resolve `ingestion` Module (Move to Data-Cloud or Delete)

**Status:** ⬜  
**Owner:** Data-Cloud Team  
**Effort:** XS (0.5 days investigation + 1 day action)  
**Deadline:** 2026-04-15 (from ADR-014)  
**ADR:** ADR-014 §3

**Problem:**  
`platform/java/ingestion` (22 Java files) has zero Gradle consumers across all
product build files. It is registered in `settings.gradle.kts` but never declared
as a dependency by any product. This is dead platform code.

**Decision Protocol:**

Run the following to confirm no product imports `com.ghatana.platform.ingestion`:
```bash
find products/ -name "*.java" -not -path "*/build/*" \
  | xargs grep "com.ghatana.platform.ingestion" 2>/dev/null
```

- **If 0 results** → DELETE the module (recommended path).
- **If results found** → examine imports and decide whether to MOVE to
  `products/data-cloud/src/main/java/com/ghatana/datacloud/ingestion/`.

**Steps (delete path):**

1. Confirm 0 Java import references (command above).
2. Remove `include(":platform:java:ingestion")` from `settings.gradle.kts`.
3. Delete `platform/java/ingestion/` directory.
4. Run `./gradlew build --dry-run` to confirm no missing deps.

**Steps (move path):**

1. Move `platform/java/ingestion/src/main/java/` into
   `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/ingestion/`.
2. Reclassify as `products/data-cloud/platform` internal code.
3. Update package declarations from `com.ghatana.platform.ingestion` →
   `com.ghatana.datacloud.ingestion`.
4. Remove `include(":platform:java:ingestion")` from `settings.gradle.kts`.
5. Add ArchUnit rule to prevent re-introduction of ingestion in platform.

---

### DOM-1 🟡 — Fix `domain → core` Exception Coupling

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** XS (< 1 day)  
**Source:** `docs/audits/circular-deps-report.md` §Check-2

**Problem:**  
`platform/java/domain/src/main/java/com/ghatana/platform/domain/GEventType.java`
imports two exception types from `platform/java/core`:
- `com.ghatana.platform.core.exception.EventCreationException`
- `com.ghatana.platform.core.exception.SchemaValidationException`

This creates a `domain → core` dependency. While not circular, it violates the
platform layer purity goal: `domain` should be dependency-free (or depend only
on contracts).

**Options (pick one):**

**Option A (preferred) — Move exceptions into `domain`:**
1. Move `EventCreationException.java` and `SchemaValidationException.java` from
   `platform/java/core/src/main/java/com/ghatana/platform/core/exception/` into
   `platform/java/domain/src/main/java/com/ghatana/platform/domain/exception/`.
2. Update all imports across the codebase:
   ```bash
   find . -name "*.java" | xargs grep -l "platform.core.exception.EventCreationException\|platform.core.exception.SchemaValidationException"
   ```
3. Update `PlatformBoundaryRulesTest.java` to remove the known-violation exception.

**Option B — Introduce `platform:java:exceptions` thin module:**
1. Create `platform/java/exceptions/` with just the exception types.
2. Both `core` and `domain` depend on `exceptions`.
3. Update `settings.gradle.kts` and `PlatformBoundaryRulesTest`.

**Verification:**
```bash
./gradlew :platform:java:domain:test
# PlatformBoundaryRulesTest must pass with 0 known violations
```

---

### DCDUP-1 🟡 — Consolidate Data-Cloud Duplicate Abstractions

**Status:** ⬜  
**Owner:** Data-Cloud Team  
**Effort:** M (3–5 days)  
**Source:** `docs/audits/V2_PRODUCTS_DEEP_AUDIT_2026-03-20.md` §46.6

**Problem:**  
8 class names exist in 2 locations each within `products/data-cloud/platform/`:

| Class | Duplicate locations |
|-------|---------------------|
| `StorageConnector` | `spi/`, `entity/storage/` |
| `StoragePlugin` | `spi/`, `event/spi/` |
| `BackpressureManager` | `backpressure/`, `infrastructure/backpressure/` |
| `QueryPlan` | `analytics/`, `application/nlq/` |
| `QueryResult` | `analytics/`, `application/nlq/` |
| `QuerySpec` | `application/`, `entity/storage/` |
| `AuditLog` | `application/audit/legacy/`, `entity/audit/` |
| `DataCloudClient` | `client/`, root package |

**Steps:**

For each pair, apply the "canonical wins" rule:

1. **Interfaces/contracts** → `spi/` package is canonical; delete the duplicate.
2. **Infrastructure impls** → `infrastructure/` sub-package is canonical; delete duplicate.
3. **NLQ types** (`QueryPlan`, `QueryResult`) → `application/nlq/` is canonical (feature-scoped); mark `analytics/` copies `@Deprecated`.
4. **AuditLog** → `entity/audit/` is canonical (domain model); mark `application/audit/legacy/` version `@Deprecated(forRemoval=true)`.
5. **DataCloudClient** → `client/` package is canonical; remove the root-package copy.
6. After each consolidation, run:
   ```bash
   ./gradlew :products:data-cloud:platform:compileJava
   ```
7. Update `DataCloudHttpServer.java` imports where it imports the deprecated copies.

**Verification:**
```bash
./gradlew :products:data-cloud:platform:test
# grep for duplicated FQNs returns single location each
```

---

### Phase 1 Checklist

| ID | Task | Owner | Status | Effort | Target |
|----|------|-------|--------|--------|--------|
| OBSV-1 | Consolidate observability 3→1 | Platform | ⬜ | S | Week 1 |
| WF-1 | Consolidate workflow 3→1 | Platform | ⬜ | S | Week 1 |
| AI-1 | Merge ai-experimental into ai-integration | Platform | ⬜ | XS | Week 2 |
| ING-1 | Resolve ingestion module fate | Data-Cloud | ⬜ | XS-S | Week 2 |
| DOM-1 | Fix domain→core exception coupling | Platform | ⬜ | XS | Week 3 |
| DCDUP-1 | Consolidate Data-Cloud duplicates | Data-Cloud | ⬜ | M | Week 3–4 |

**Phase 1 exit criteria:**
- [ ] `settings.gradle.kts` module count ≤ 145 (from 149)
- [ ] Zero `observability-http` / `observability-clickhouse` / `workflow-runtime` /
      `workflow-jdbc` / `ai-experimental` / `ingestion` references in any `*.kts` outside `build/`
- [ ] `./gradlew :platform:java:observability:test :platform:java:workflow:test :platform:java:ai-integration:test` all green
- [ ] `PlatformBoundaryRulesTest` passes with 0 known violations (after DOM-1)
- [ ] Data-Cloud has no duplicate class FQNs (after DCDUP-1)

---

## Phase 2 — Short-Term: Consolidate and Restructure Modules (Weeks 1–8)

**Goal:** Address structural issues inside products and remaining platform smell. These
are higher-effort changes that require product team coordination.

### YAPPC-1 🟠 — Restructure YAPPC Core into Focused Domains

**Status:** ⬜  
**Owner:** YAPPC Team  
**Effort:** L (2–3 weeks)  
**ADR:** ADR-011 (YAPPC modular refactoring — extend with Phase 2 decisions)

**Current state:**  
`products/yappc/core/` contains 10 sub-directories with overlapping responsibilities:
```
agents/          (+ agents/runtime + agents/specialists + agents/workflow = 4 modules)
ai/
cli-tools/
domain/
framework/       (+ framework/integration-test = 2 modules)
knowledge-graph/
lifecycle/
refactorer/      (+ refactorer/api + refactorer/engine = 2 modules)
scaffold/        (+ scaffold/api + scaffold/core + scaffold/packs = 3 modules)
spi/
```
Total: ~18 Gradle modules in `yappc/core` alone.

**Target architecture:**
```
yappc/core/
├── domain/          Pure domain model (KEEP, already reasonably scoped)
├── application/     Application services (merge: lifecycle + spi + framework core)
├── infrastructure/  External integrations (merge: agents, ai, knowledge-graph)
└── interfaces/      Public APIs and extension points (merge: refactorer/api, scaffold/api, spi)
```

**Steps (execute in order to maintain build stability):**

**Week 1 — Audit and plan:**
1. For each of the 18 YAPPC core Gradle modules, classify every Java package into
   one of: `domain`, `application`, `infrastructure`, `interfaces`.
2. Document the classification in `products/yappc/docs/CORE_ARCHITECTURE.md`
   (if not already present — add the Phase 2 classification table).
3. Identify cross-cutting imports that would become circular after merge.
4. Create a dependency graph: `./gradlew :products:yappc:core:dependencies`.

**Week 2 — Execute scaffold consolidation (3 → 1):**
1. Merge `core/scaffold/api`, `core/scaffold/core`, `core/scaffold/packs` → `core/scaffold`.
2. Already tracked in previous session; verify current state in
   `products/yappc/core/scaffold/build.gradle.kts`.
3. Update `settings.gradle.kts`: remove the three split entries, add single `core:scaffold`.

**Week 3 — Execute agent/ai/knowledge-graph consolidation:**
1. Merge `core/agents/specialists` and `core/agents/workflow` into `core/agents`.
2. Validate that `core/agents/runtime` can likewise be folded in.
3. Create `core/infrastructure/` as the merge target for `core/agents`, `core/ai`,
   `core/knowledge-graph`.
4. Move `core/cli-tools` → `products/yappc/tools/cli` (CLI is not a core domain concept).

**Verification at each step:**
```bash
./gradlew :products:yappc:build --continue
./gradlew :products:yappc:launcher:test
```

---

### YAPPC-2 🟡 — Consolidate YAPPC Services Layer

**Status:** ⬜  
**Owner:** YAPPC Team  
**Effort:** M (1 week)

**Current state:**
```
products/yappc/services/          (parent)
products/yappc/services/ai/
products/yappc/services/lifecycle/
products/yappc/services/platform/
products/yappc/services/scaffold/
```

**Target:** 3 modules max — collapse thin service wrappers that duplicate core logic.

**Steps:**

1. Audit each services/* module to determine if it adds value beyond delegating to core.
2. Merge `services/scaffold` into `core/scaffold` if the service layer is a thin wrapper.
3. Merge `services/ai` into `core/ai` following the same logic.
4. Keep `services/lifecycle` and `services/platform` if they contain non-trivial
   orchestration logic or outbound service calls.
5. Update `settings.gradle.kts` accordingly.

---

### SCHM-1 🟡 — Address `schema-registry` Low Adoption

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (1–2 days)  
**ADR:** ADR-014 (add schema-registry section)

**Problem:**  
`platform/java/schema-registry` has limited product adoption (identified in audit
as candidate for deletion/movement). Needs the same detective work as `ingestion`.

**Steps:**

1. Run consumer audit:
   ```bash
   grep -r "schema-registry" . --include="*.kts" | grep -v "^Binary\|settings.gradle\|build/"
   find products/ -name "*.java" | xargs grep "com.ghatana.platform.schema" 2>/dev/null | grep -v "build/"
   ```
2. If consumers < 2 separate product families: move logic into the single consumer
   or delete + document in ADR.
3. If consumers ≥ 2: keep and add governance documentation to `docs/SHARED_LIBRARY_REGISTRY.md`.

---

### YAML-1 🟢 — Merge `yaml-template` into `agent-core`

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** XS (0.5 days)  
**ADR:** ADR-014 §4 (long-term recommendation)

**Problem:**  
`platform/java/yaml-template` is consumed by `agent-core` (as `api(...)`) and YAPPC
transitively via `agent-core`. It is effectively an `agent-core` sub-module.

**Steps:**

1. Move `yaml-template/src/main/java/` into `agent-core/src/main/java/` under
   `com.ghatana.agent.template.*`.
2. Remove `api(project(":platform:java:yaml-template"))` from `agent-core/build.gradle.kts`.
3. Remove the explicit YAPPC deps on `yaml-template` (they will inherit via `agent-core`).
4. Remove `include(":platform:java:yaml-template")` from `settings.gradle.kts`.
5. Delete `platform/java/yaml-template/`.

---

### DCHTTP-1 🟡 — Decompose `DataCloudHttpServer` (1993 LOC → < 800 LOC)

**Status:** 🔵 (started in P7-2b — HealthHandler extracted; 2033→1993 LOC)  
**Owner:** Data-Cloud Team  
**Effort:** M (1 week)

**Problem:**  
`products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`
still contains 100+ HTTP handler methods across entity CRUD, events, agents, pipelines,
checkpoints, memory, brain, learning, analytics, reporting, models/features, SSE, WebSocket.
The 1993-line class is a maintenance liability.

**Steps (extract one handler family per day):**

| Day | Handler family | Target class | Est. methods |
|-----|---------------|--------------|-------------|
| 1 | Entity CRUD + Batch | `EntityHandler` | 15 |
| 2 | Events + SSE | `EventHandler` | 12 |
| 3 | Agents + Pipelines | `AgentPipelineHandler` | 18 |
| 4 | Brain + Memory + Learning | `BrainMemoryHandler` | 20 |
| 5 | Analytics + Reporting + Models | `AnalyticsReportingHandler` | 22 |

**Pattern:** Each extracted handler follows the existing `HealthHandler` pattern:
```java
public class EntityHandler {
    private final EntityService entityService;
    
    void register(HttpServerRouting routing) {
        routing.get("/api/entities", this::handleList);
        // ...
    }
}
```
`DataCloudHttpServer` becomes a thin router delegating to handlers.

**Verification:**
```bash
./gradlew :products:data-cloud:launcher:test
wc -l products/data-cloud/launcher/src/main/java/.../DataCloudHttpServer.java
# target: < 800 lines
```

---

### TSUI-1 🟡 — Consolidate TypeScript UI into Design System

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** XS (0.5 days)

**Problem:**  
`platform/typescript/ui` is a thin re-export package over `platform/typescript/capabilities/design-system`.
Both export the same component surface.

**Steps:**

1. Audit what `platform/typescript/ui/src/` actually exports vs. `design-system`.
2. If `ui` is a re-export barrel: deprecate `@ghatana/ui` in `platform/typescript/ui/package.json`,
   add a note directing consumers to `@ghatana/design-system`.
3. Find all `@ghatana/ui` import sites across `products/` and migrate them to
   `@ghatana/design-system`.
4. Once consumers are 0, remove the package from `pnpm-workspace.yaml`.

**Verification:**
```bash
grep -r "@ghatana/ui" products/ apps/ --include="*.ts" --include="*.tsx" | grep -v "build/" | wc -l
# → 0
```

---

### SEC-1 🟡 — Split `platform:java:security` Generic vs Product-Specific

**Status:** ⬜  
**Owner:** Platform Team + Product Teams  
**Effort:** M (1 week)

**Problem:**  
`platform/java/security` contains both generic authentication/authorization
infrastructure and product-specific security rules. These need separation to prevent
platform drift.

**Steps:**

1. Audit all classes in `platform/java/security/src/main/java/`:
   ```bash
   find platform/java/security/src/main/java -name "*.java" | xargs grep -l "class\|interface" | sed 's|.*security/src/main/java/||'
   ```
2. Classify each class:
   - **Generic** (keep in `platform:java:security`): auth filters, JWT validation,
     session management, RBAC interfaces
   - **Product-specific** (move to product): AEP-specific security policies,
     YAPPC authorization rules, tenant isolation rules tied to specific product models
3. For each product-specific class, create `products/<product>/platform-security/src/`.
4. Move the identified files; update imports.
5. Add ArchUnit rule: `platform.security` must not import from any `com.ghatana.aep`,
   `.yappc`, `.datacloud`, etc. namespace.

**Verification:**
```bash
./gradlew :platform:java:security:test
# ArchUnit boundary test for security module must pass
```

---

### CODEOWNERS-1 🟡 — Add CODEOWNERS to All Products

**Status:** ⬜  
**Owner:** All Product Teams  
**Effort:** S (1 day total, distributed)

**Problem:**  
Ownership of modules is documented in ADR-013 and OWNER.md files, but the repository
lacks machine-readable CODEOWNERS entries that auto-assign reviewers on pull requests.

**Steps:**

1. Create/update `.gitea/CODEOWNERS` (or `.github/CODEOWNERS`) at the repository root.
2. Map each top-level product path to its owning team:
   ```
   /platform/java/          @ghatana/platform-team
   /products/aep/           @ghatana/aep-team
   /products/data-cloud/    @ghatana/data-cloud-team
   /products/yappc/         @ghatana/yappc-team
   /products/finance/       @ghatana/finance-team
   /products/dcmaar/        @ghatana/guardian-team
   /products/audio-video/   @ghatana/av-team
   /products/security-gateway/ @ghatana/platform-team
   /shared-services/        @ghatana/platform-team
   ```
3. Verify that each team slug exists in Gitea/GitHub.
4. Test by creating a draft PR touching a product file.

---

### Phase 2 Checklist

| ID | Task | Owner | Status | Effort | Target |
|----|------|-------|--------|--------|--------|
| YAPPC-1 | YAPPC core restructuring | YAPPC | ⬜ | L | Weeks 3–5 |
| YAPPC-2 | YAPPC services consolidation | YAPPC | ⬜ | M | Weeks 5–6 |
| SCHM-1 | schema-registry adoption audit | Platform | ⬜ | S | Week 2 |
| YAML-1 | yaml-template merge into agent-core | Platform | ⬜ | XS | Week 3 |
| DCHTTP-1 | DataCloudHttpServer decomposition | Data-Cloud | ⬜ | M | Weeks 4–5 |
| TSUI-1 | ui → design-system consolidation | Platform | ⬜ | XS | Week 2 |
| SEC-1 | security module split | Platform+Products | ⬜ | M | Weeks 5–6 |
| CODEOWNERS-1 | CODEOWNERS file | All Teams | ⬜ | S | Week 2 |

**Phase 2 exit criteria:**
- [ ] YAPPC core module count ≤ 10 (from ~18)
- [ ] `DataCloudHttpServer.java` ≤ 800 lines
- [ ] `@ghatana/ui` has 0 consumers or is deleted
- [ ] `schema-registry` fate decided and actioned
- [ ] `platform:java:security` audit complete; product-specific rules extracted
- [ ] CODEOWNERS file in place and validated
- [ ] Total Gradle module count ≤ 140 (from ~149)

---

## Phase 3 — Medium-Term: Establish Governance and Guardrails (Weeks 1–12)

**Goal:** Make the improved structural state permanent through automation, process,
and social contracts that make drift visible and expensive.

### GOV-1 🟠 — Complete and Close All Open ADR Action Items

**Status:** ⬜  
**Owner:** Architecture Team  
**Effort:** S (2–3 days total, spread across Weeks 1–4)

**Open ADR items (as of 2026-03-21):**

| ADR | Section | Action | Deadline |
|-----|---------|--------|----------|
| ADR-014 | ai-experimental | Merge into ai-integration | 2026-04-30 → done in Phase 1 |
| ADR-014 | ingestion | Investigate DELETE/MOVE | 2026-04-15 → done in Phase 1 |
| circular-deps-report.md | BDY-1-5 | Move exceptions from core to domain | Phase 1 DOM-1 |
| circular-deps-report.md | BDY-1-6 | ArchUnit guard strengthened | Phase 1 DOM-1 |

**Steps:**

1. After Phase 1 completes, update each open ADR with resolution status.
2. Add a new ADR covering the observability and workflow consolidations (OBSV-1, WF-1).
3. Archive superseded recommendations from `GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md`
   §70 items 1–5 (document as resolved).

---

### GOV-2 🟠 — Platform Module Admission Process

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (2 days)

**Problem:**  
`platform/java/` grew from a reasonable set to 32 modules partly because there
was no gate preventing new modules from entering. The governance freeze covered
existing patterns but did not formalize the admission criteria for new platform
modules.

**Steps:**

1. Update `docs/MODULE_ADMISSION_CHECKLIST.md` to add an explicit
   "Platform Module Admission Gate" section:
   ```markdown
   ## Platform Module Admission Gate (platform/java/*)

   A new platform/java module requires ALL of the following:
   - [ ] Written ADR proposing the module
   - [ ] ≥ 3 confirmed product consumers identified by name
   - [ ] No equivalent functionality exists in an existing module
   - [ ] Architecture Team sign-off (2 approvals)
   - [ ] Added to SHARED_LIBRARY_REGISTRY.md before merge
   ```
2. Add a CI check: any PR adding a new `include(":platform:java:*")` entry to
   `settings.gradle.kts` must include an ADR file reference in the PR description
   (enforced via PR template + `scripts/check-new-platform-module.sh`).

---

### GOV-3 🟠 — Architecture Score Target: 80 → 90/100

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** M (1 week)

**Current score:** 80/100 from `scripts/architecture-score-gate.sh`.

**Score breakdown and improvement plan:**

| Check | Current Points | Target | Action |
|-------|---------------|--------|--------|
| Deprecated refs count | 20/25 | 25/25 | Remove deprecated wrappers after migration grace period |
| Reflective instantiation | 20/25 | 25/25 | Audit remaining reflection usages; replace with ServiceLoader |
| Cross-product deps | 20/25 | 25/25 | After SEC-1 and YAPPC-1, re-run cross-product dep check |
| Module size limits | 20/25 | 15/25 | DataCloudHttpServer decomp (DCHTTP-1) is the primary fix |

**Steps:**

1. After Phase 1 completes: re-run `scripts/architecture-score-gate.sh` to get updated baseline.
2. Address each failing check systematically:
   - For deprecated refs: set a sunset date for deprecated adapter classes created during
     agent registry migration; remove after 60 days (target: 2026-06-01).
   - For reflective instantiation: audit `Class.forName()`, `getDeclaredConstructor().newInstance()`;
     replace with ServiceLoader or injection.
   - For module size: DCHTTP-1 (DataCloudHttpServer) plus AEP YAPPC agent consolidation.
3. Update score threshold in CI from 80 to 90:
   ```bash
   # scripts/architecture-score-gate.sh line: REQUIRED_SCORE=80 → 90
   ```
4. Commit the threshold increase as a separate PR after scores are verified.

---

### GOV-4 🟡 — Dependency Visualization Dashboard

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (2 days)

**Problem:**  
The dependency graph is invisible to most engineers, making it hard to spot new
violations early.

**Steps:**

1. Add a Gradle task to `build.gradle.kts` that generates a module dependency
   report in DOT format:

   ```kotlin
   tasks.register("generateDependencyGraph") {
       doLast {
           // Use Gradle's built-in dependency insight to produce DOT output
           // or integrate the `gradle-dependency-graph-generator` plugin
       }
   }
   ```

2. Alternatively, integrate the `com.vanniktech.dependency.graph.generator` Gradle
   plugin (add to `buildSrc/build.gradle.kts`):
   ```kotlin
   // buildSrc/build.gradle.kts
   dependencies {
       implementation("com.vanniktech:gradle-dependency-graph-generator-plugin:0.8.0")
   }
   ```

3. Generate a graph on every CI run and store as a build artifact:
   ```yaml
   # .github/workflows/platform-module-tests.yml
   - name: Generate dependency graph
     run: ./gradlew generateDependencyGraph
   - uses: actions/upload-artifact@v4
     with:
       name: dependency-graph
       path: build/reports/dependency-graph/
   ```

4. Add a weekly scheduled job that posts the diff of boundary violations to
   the `#architecture` Slack/Teams channel.

---

### GOV-5 🟡 — Module Count Tracking and Enforcement

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (1 day)

**Steps:**

1. Add a module count check to `scripts/architecture-score-gate.sh`:
   ```bash
   MODULE_COUNT=$(grep -c "^include" settings.gradle.kts)
   MAX_MODULES=155  # decrease quarterly
   if [ "$MODULE_COUNT" -gt "$MAX_MODULES" ]; then
     echo "FAIL: Module count $MODULE_COUNT exceeds limit $MAX_MODULES"
     exit 1
   fi
   ```

2. Current count: 149. Phase 1 target: ≤ 145. Phase 2 target: ≤ 140.
3. Set CI to fail if count increases without a matching ADR.
4. Track trend in `docs/VERSION_CONVERGENCE_REPORT.md` quarterly.

---

### GOV-6 🟡 — Formal Deprecation Lifecycle Policy

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (1 day)

**Problem:**  
Multiple classes are marked `@Deprecated(since="2.4.0", forRemoval=true)` from
the agent registry migration, but there is no documented process for HOW and WHEN
to actually remove them.

**Steps:**

1. Add a `docs/DEPRECATION_POLICY.md` document defining:
   - **Deprecation notice period:** minimum 60 days from first deprecated release
   - **Removal approval:** requires PR with migration guide reference + 2 Architecture Team approvals
   - **Tracking:** all deprecated items listed in `docs/SHARED_LIBRARY_REGISTRY.md`
     under a "Sunset Schedule" section
   - **CI enforcement:** deprecated class count monitored; alert if count grows

2. Populate `docs/SHARED_LIBRARY_REGISTRY.md` "Sunset Schedule" with all current
   `@Deprecated(forRemoval=true)` items and their earliest removal date.

3. Schedule "Deprecation Day" recurring event (quarterly) for removal of graduated items.

---

### GOV-7 🟡 — ArchUnit Rule Expansion

**Status:** ⬜  
**Owner:** Platform Team  
**Effort:** S (2 days)

**Current coverage:**  
`PlatformBoundaryRulesTest` in `platform/java/testing` covers:
- `platform.domain` not importing `platform.core`
- `platform.*` not importing product namespaces

**Gaps to close:**

| New rule | Description | Test class |
|----------|-------------|-----------|
| Security isolation | `platform.security` must not import product namespaces | `PlatformBoundaryRulesTest` |
| Workflow isolation | `platform.workflow` must not import `platform.agent` | `PlatformBoundaryRulesTest` |
| Product cross-deps | Product A must not import Product B unless approved | `CrossProductBoundaryTest` (new) |
| Size guard | No single source file > 800 lines in platform modules | `PlatformSizeRulesTest` (new) |

**Steps:**

1. Add rules to `PlatformBoundaryRulesTest`:
   ```java
   @Test
   void securityMustNotImportProducts() {
       noClasses().that().resideInAPackage("com.ghatana.platform.security..")
           .should().dependOnClassesThat().resideInAnyPackage(
               "com.ghatana.yappc..", "com.ghatana.aep..", "com.ghatana.datacloud..")
           .check(PLATFORM_SECURITY_CLASSES);
   }
   ```

2. Create `CrossProductBoundaryTest` listing approved cross-product imports from
   `scripts/check-cross-product-deps.sh` (27 approved deps already documented).

3. Create `PlatformSizeRulesTest` using ArchUnit's `source` section to count lines.

---

### GOV-8 🟡 — Quarterly Boundary Audit Automation

**Status:** ⬜  
**Owner:** Architecture Team  
**Effort:** S (2 days)

**Problem:**  
The existing `docs/QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md` is manual. Automating
key metrics collection makes audits faster and more consistent.

**Steps:**

1. Create `scripts/run-quarterly-audit.sh` that collects:
   ```bash
   #!/usr/bin/env bash
   # Module count
   echo "=== Module Count ===" && grep -c "^include" settings.gradle.kts
   # Cross-product deps
   echo "=== Cross-Product Deps ===" && bash scripts/check-cross-product-deps.sh
   # Architecture score
   echo "=== Architecture Score ===" && bash scripts/architecture-score-gate.sh
   # Deprecated class count
   echo "=== Deprecated Classes ===" && find . -name "*.java" -not -path "*/build/*" \
     | xargs grep -l "@Deprecated" | wc -l
   # Large files
   echo "=== Files > 800 lines ===" && find . -name "*.java" -not -path "*/build/*" \
     | xargs wc -l 2>/dev/null | awk '$1 > 800 {print}' | sort -rn | head -20
   ```

2. Add the script to a scheduled CI job (quarterly trigger: 2026-06-21, 2026-09-21, 2026-12-21).

3. Output report appended to `docs/QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md` with
   date prefix.

---

### Phase 3 Checklist

| ID | Task | Owner | Status | Effort | Target |
|----|------|-------|--------|--------|--------|
| GOV-1 | Close open ADR action items | Architecture | ⬜ | S | Weeks 1–4 |
| GOV-2 | Platform module admission process | Platform | ⬜ | S | Week 2 |
| GOV-3 | Architecture score 80 → 90 | Platform | ⬜ | M | Weeks 4–8 |
| GOV-4 | Dependency visualization dashboard | Platform | ⬜ | S | Week 4 |
| GOV-5 | Module count enforcement in CI | Platform | ⬜ | S | Week 3 |
| GOV-6 | Formal deprecation lifecycle policy | Platform | ⬜ | S | Week 3 |
| GOV-7 | ArchUnit rule expansion | Platform | ⬜ | S | Week 5 |
| GOV-8 | Quarterly audit automation script | Architecture | ⬜ | S | Week 6 |

**Phase 3 exit criteria:**
- [ ] Architecture score ≥ 90/100 in CI
- [ ] All ADR action items resolved and closed
- [ ] Platform module admission gate in `MODULE_ADMISSION_CHECKLIST.md` + CI enforcement
- [ ] Deprecation lifecycle policy documented; all current deprecations in sunset schedule
- [ ] `CrossProductBoundaryTest` added and passing
- [ ] Module count CI gate active at ≤ 140
- [ ] Quarterly audit script operational; first automated run completed

---

## Phase 4 — Long-Term: Continuous Improvement and Evolution (Months 4+)

**Goal:** Establish sustainable systems that maintain and continuously improve
architectural health without large periodic clean-up efforts.

### EVOL-1 — Platform Evolution Roadmap (Quarterly)

**Owner:** Architecture Team  
**Cadence:** Quarterly (next: 2026-06-21)

Following the `docs/QUARTERLY_BOUNDARY_AUDIT_CHECKLIST.md` process, each quarterly
review should produce an evolution decision for each platform module:

| Decision type | Action |
|--------------|--------|
| **Grow** | Module gaining new consumers → document growth plan |
| **Stable** | Module mature, no changes needed → verify test coverage |
| **Shrink** | Module losing consumers → deprecation notice, merge plan |
| **Remove** | Module at 0 consumers → ADR + removal sprint |

Quarterly cadence:
- **Q2 2026 (June):** First post-remediation audit; validate Phase 1+2 outcomes
- **Q3 2026 (September):** Focus on build performance impact of consolidation
- **Q4 2026 (December):** Year-end score target: 95/100; publish annual boundary report

---

### EVOL-2 — Build Performance Improvement

**Owner:** Platform Team  
**Target:** 2026-09-21

The module consolidations in Phases 1 and 2 should yield measurable improvements
in incremental build times by reducing the dependency chain depth.

**Baseline measurement (run after Phase 1):**
```bash
./gradlew :products:aep:server:build --profile
# Record: total time, configuration time, task execution time
```

**Target improvements:**
- Configuration time: reduce by 15% (fewer modules to configure)
- Full build time: reduce by 10% (fewer inter-module compilation boundaries)
- Incremental build (change 1 file): reduce by 25% (simpler dependency graph)

**Implementation:**
1. Enable Gradle configuration cache:
   ```properties
   # gradle.properties
   org.gradle.configuration-cache=true
   org.gradle.configuration-cache.problems=warn
   ```
2. Enable Gradle build cache:
   ```properties
   org.gradle.caching=true
   ```
3. After each consolidation sprint, re-measure and publish results to
   `docs/IMPLEMENTATION_PROGRESS_TRACKER.md`.

---

### EVOL-3 — ArchUnit Coverage to 100% Critical Paths

**Owner:** Platform Team  
**Target:** 2026-09-21

Expand ArchUnit coverage to include:
1. **All product internal layers**: each product's domain → application → infrastructure
   layering enforced by tests in `products/<product>/testing/`.
2. **Finance domain isolation**: `finance.domains.X` must not import `finance.domains.Y`
   directly (should use published interfaces).
3. **Data-Cloud duplicate prevention**: ArchUnit rule preventing classes with the same
   name appearing in more than one package within `com.ghatana.datacloud.*`.
4. **Agent API boundary**: no direct dependency on `agent-core` internals from products
   (should use `agent-api` + `agent-spi` only).

---

### EVOL-4 — GAA-Based Boundary Drift Detection

**Owner:** Architecture Team + AEP Team  
**Target:** 2026-12-21

Leverage the Generic Adaptive Agent (GAA) framework to build an automated boundary
drift detector that:
1. **PERCEIVE:** Monitors PR diffs for new `import` statements and dependency
   declarations in `build.gradle.kts`.
2. **REASON:** Classifies each new import against known-good patterns (approved
   cross-product deps, platform→product direction).
3. **ACT:** Posts a PR comment flagging potential boundary violations with specific
   remediation suggestions.
4. **CAPTURE:** Records flagged violations as episodic memory events.
5. **REFLECT:** Weekly batch analysis to detect patterns (e.g., "YAPPC keeps
   importing Data-Cloud internals in the same package area").

**Technical implementation:**
- Run as an `aep` pipeline operator using `platform-analytics` for pattern detection.
- Input: git diff output from PR webhook.
- Output: GitHub/Gitea PR comment via `platform-connectors`.
- Memory: violations stored in `data-cloud/event` event log with tenant = `architecture`.

---

### EVOL-5 — Product Boundary Scorecard Tracking

**Owner:** Architecture Team  
**Cadence:** Quarterly

Track the scores from `GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md` §43–52
programmatically each quarter:

```bash
# scripts/compute-boundary-scores.sh
# Computes Boundary Clarity, Ownership, Cohesion, Coupling per product
# Outputs updated scorecard table in Markdown
```

**2026 targets:**

| Product | Current | Q2 2026 | Q3 2026 | Q4 2026 |
|---------|---------|---------|---------|---------|
| AEP | 8.0/10 | 8.5/10 | 9.0/10 | 9.0/10 |
| Data-Cloud | 7.3/10 | 7.8/10 | 8.0/10 | 8.5/10 |
| YAPPC | 3.5/10 | 5.0/10 | 7.0/10 | 8.0/10 |
| Finance | 8.8/10 | 9.0/10 | 9.0/10 | 9.5/10 |
| Flashit | 6.5/10 | 7.0/10 | 7.5/10 | 8.0/10 |
| DCMAAR | 6.8/10 | 7.0/10 | 7.5/10 | 8.0/10 |
| Audio-Video | 7.5/10 | 8.0/10 | 8.5/10 | 9.0/10 |
| Security-Gateway | 8.5/10 | 9.0/10 | 9.0/10 | 9.5/10 |
| **Platform (avg)** | 6.5/10 | 7.5/10 | 8.5/10 | 9.0/10 |

---

### EVOL-6 — Shared-Services Build Integration

**Owner:** Platform Team  
**Target:** 2026-06-21

**Problem:**  
`shared-services/` (auth-gateway, auth-service, feature-store-ingest, ai-registry,
user-profile-service) is partially included in `settings.gradle.kts` but lacks a
clear deployment strategy documentation.

**Steps:**

1. Document in `shared-services/README.md`:
   - Which services are included in the root Gradle build
   - Which require independent build/deploy (Docker Compose vs. K8s)
   - Dependency relationships with platform Java modules

2. Ensure all shared-services Gradle modules compile as part of the standard
   `./gradlew build` invocation.

3. If any service requires Spring Boot or a framework incompatible with the
   current build conventions, create an exception entry in `docs/GOVERNANCE_FREEZE_RULES.md`.

---

## Master Timeline

```
Week 1–2   [Phase 1] OBSV-1, WF-1, AI-1, ING-1 (platform consolidations)
Week 3–4   [Phase 1] DOM-1, DCDUP-1 (coupling + duplicates)
            [Phase 2] SCHM-1, YAML-1, TSUI-1, CODEOWNERS-1

Week 5–6   [Phase 2] DCHTTP-1 (DataCloudHttpServer decomp)
Week 7–8   [Phase 2] YAPPC-1 scaffold + service consolidation
            [Phase 2] SEC-1 security module split

Week 9     [Phase 3] GOV-1, GOV-2, GOV-5 (ADRs + admission gate + module count)
Week 10    [Phase 3] GOV-3 architecture score push (80→90)
Week 11    [Phase 3] GOV-4, GOV-6 (viz dashboard + deprecation policy)
Week 12    [Phase 3] GOV-7, GOV-8, YAPPC-1 final validation

Month 4–6  [Phase 4] EVOL-1/2/3 build performance + ArchUnit expansion
Month 7–9  [Phase 4] EVOL-4 GAA drift detector
Month 10+  [Phase 4] EVOL-5/6 scorecard tracking + shared-services
```

---

## Module Count Trajectory

| Milestone | Target Count | Notes |
|-----------|-------------|-------|
| Baseline (today) | 149 | Current `settings.gradle.kts` |
| After Phase 1 | ≤ 145 | -4 (obsv×2, workflow×2, ai-experimental, ingestion) |
| After Phase 2 | ≤ 130 | -15 (YAPPC core consolidation + services) |
| After Phase 4 | ≤ 120 | -10 (schema-registry, yaml-template + long-term removals) |

---

## Dependencies Between Tasks

```
OBSV-1 ─────────────────────────────────────┐
WF-1 ────────────────────────────────────────┤
AI-1 ────────────────────────────────────────┤─→ GOV-3 (score target)
ING-1 ───────────────────────────────────────┤         ↓
DOM-1 ───────────────────────────────────────┘   GOV-7 (ArchUnit)
DCDUP-1 ──────────────────────────────────────→ DCHTTP-1 (same team, sequence)
YAPPC-1 ──────────────────────────────────────→ YAPPC-2 (must come first)
SEC-1 ────────────────────────────────────────→ GOV-7 (new security rule)
GOV-2 + GOV-5 ────────────────────────────────→ EVOL-1 (quarterly process)
GOV-3 ────────────────────────────────────────→ EVOL-3 (ArchUnit expansion)
EVOL-4 depends on: Phase 1 complete + AEP GAA framework operational
```

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|----------|--------|-----------|
| OBSV-1/WF-1 break transitive consumers | Medium | High | Add `api()` re-exports before removing modules; validate with `./gradlew build --continue` |
| YAPPC-1 scope creep | High | Medium | Time-box each sub-step to 1 day; defer anything that requires Java code changes beyond imports |
| DCDUP-1 introduces regressions in Data-Cloud HTTP server | Medium | High | Feature-flag each handler migration; run full integration test suite before each merge |
| SEC-1 reveals product code that bypasses platform auth | Medium | Critical | Security review required on all moved classes before deletion from platform |
| Architecture score regression between phases | Low | Low | GOV-5 CI gate prevents silent regression |
| New platform modules added before GOV-2 is in place | High | Medium | Manual review required on all PRs touching `settings.gradle.kts` until GOV-2 CI gate is active |

---

## Change Log

| Date | Version | Change |
|------|---------|--------|
| 2026-03-21 | 1.0 | Initial plan created from `GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md` |
