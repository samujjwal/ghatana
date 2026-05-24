## Review scope

I reviewed `samujjwal/ghatana` at commit `30a4e4427298f3f42290188e0947de7a7d2c0299`, treating the **snapshot at this commit** as source of truth, not the diff. The commit itself is a merge commit, and the fetched commit metadata shows only a YAPPC changelog diff, so the meaningful review is the repository state at that ref, not that merge diff.

I ignored archived/legacy/temporal planning docs and focused on active code/docs under `products/data-cloud`, `products/aep`, generated product registry/build includes, CI/release gates, and current implementation anchors.

---

# Executive assessment

The direction is now much more coherent than before. The active docs already encode the right boundary:

> **Data-Cloud is the governed data/storage substrate. AEP is the adaptive event intelligence layer. EventCloud and adaptive event semantics belong to AEP. For now, AEP implementation may live under `products/data-cloud/planes/action/*`, but that is a temporary code-location reality, not product ownership.**

This is explicitly stated in the Data-Cloud architecture and plane architecture docs. Data-Cloud’s current `ARCHITECTURE.md` says Data-Cloud owns entity storage, metadata, schemas, audit, retention, encryption support, queryable historical metadata, and pluggable persistence, while complex event processing, PatternSpec/EPL, EventCloud subscriptions/tailing/windowing, pattern learning/adaptation, agent orchestration, and predictive/recommended lifecycle belong to AEP.

The plane architecture also clearly says that during migration, AEP modules may remain under `products/data-cloud/planes/action/*`, but Data-Cloud planes must not import AEP semantics, and AEP integration must go through public contracts or stable SPI.

However, **Data-Cloud is not production-ready yet**. The readiness evidence marks `status: blocked`, disables ordinary lifecycle execution, and requires platform-provider mode, bootstrap separation, runtime-truth providers, product neutrality, and Action Plane governance.

So the current state is:

```text
Architecture direction: strong
Boundary clarity: improving and mostly coherent
AEP-in-Data-Cloud migration structure: present
Event processing as agent capability foundation: present
PatternSpec foundation: present
Uncertainty foundation: present
Production readiness: blocked / incomplete
Runtime execution completeness: partial
Release gates: strong but split between advisory and strict workflows
```

---

# 1. Current organization analysis

## 1.1 Root build organization

The root Gradle settings require Java 21, use a build-logic included build, central dependency resolution, build cache configuration, and generated product includes from `config/generated/settings-gradle-includes.kts`.

That is good because it gives the monorepo a central build model instead of each product inventing local build patterns. But it also means Data-Cloud production readiness depends on generated registry consistency, not only local module health.

## 1.2 Data-Cloud module organization

The generated settings includes show Data-Cloud as a `platform-provider` with these active modules:

```text
products:data-cloud:planes:shared-spi
products:data-cloud:planes:data:entity
products:data-cloud:planes:event:core
products:data-cloud:planes:operations:config
products:data-cloud:planes:intelligence:analytics
products:data-cloud:planes:governance:core
products:data-cloud:delivery:runtime-composition
products:data-cloud:extensions:plugins
products:data-cloud:delivery:api
products:data-cloud:delivery:launcher
products:data-cloud:delivery:sdk
products:data-cloud:contracts
products:data-cloud:extensions:agent-registry
products:data-cloud:extensions:agent-catalog
products:data-cloud:delivery:api-contract-tests
products:data-cloud:planes:intelligence:feature-ingest
products:data-cloud:planes:event:store
products:data-cloud:integration-tests
products:data-cloud:planes:action:*
products:data-cloud:extensions:kernel-bridge
```

The important observation is that **AEP is effectively co-located under Data-Cloud’s Action Plane** through many `planes:action:*` modules: operator contracts, central runtime, engine, registry, analytics, security, event bridge, agent runtime, API, scaling, observability, orchestrator, server, identity, compliance, and kernel bridge.

That matches your current instruction: **for now, AEP inside Data-Cloud**. The structure is acceptable if the docs and architecture tests keep enforcing that this is a migration/deployment convenience, not a semantic ownership reversal.

## 1.3 Plane model

The active plane architecture is a strong organizing model. It defines planes, surfaces, modules, and runtime truth, and explicitly rejects vague “capability area” language. It also defines a target repo layout with `planes`, `delivery`, `extensions`, `contracts`, `deploy`, and `integration-tests`.

This is the right organization for a production product suite because it separates:

- product architecture boundaries,
- user/API surfaces,
- implementation modules,
- runtime truth,
- migration rules.

Keep this model. Do not revert to capability-area naming.

---

# 2. AEP inside Data-Cloud: current state

## 2.1 Boundary clarity is good

The AEP architecture doc is now explicit: AEP is a formal, adaptive, agentic event processing platform grounded in adaptive ESP, and Data-Cloud remains the governed data/storage substrate.

It also correctly states that AEP has “partially implemented foundations” and that much implementation remains co-located under `products/data-cloud/planes/action/*` for migration compatibility.

This is much better than claiming full production readiness prematurely.

## 2.2 Dissertation traceability exists and is useful

`products/aep/docs/DISSERTATION_TRACEABILITY.md` maps dissertation concepts to AEP concepts and implementation anchors. It explicitly says modern advancements extend typed event operators and do not replace PatternSpec/EPL/operator graphs/governed lifecycle.

That is exactly the framing needed for coherence.

## 2.3 Agent capability foundation exists

The core `EventOperator` contract exists with `id`, `kind`, `version`, `validate`, `compile`, and `process`.

The canonical `EventOperatorCapability` contract extends `AgentCapability<EventContext<I>, EventOperatorResult<O>>` and implements the AEP `EventOperator<I, O>` execution contract. The runtime adapter declares agent reference, capability role, side-effect profile, schemas, model/tool/memory/retrieval/guardrail/replay/uncertainty/human-review/observability policies.

The `OperatorKind` enum includes standard pattern operators and all agent operator kinds: `AGENT_PREDICATE`, `AGENT_ENRICH`, `AGENT_EXTRACT`, `AGENT_PATTERN_SYNTHESIS`, `AGENT_EXPLANATION`, `AGENT_REVIEW`, `AGENT_ACTION`, and `AGENT_REFLECTION`.

There is also an architecture contract test verifying `EventOperatorCapability` is an `AgentCapability` and implements `EventOperator`, and it checks canonical agent capability roles plus governance requirements for side-effecting action capabilities.

This is a major positive. The architectural foundation for your two principles is already present.

## 2.4 PatternSpec foundation exists but is still lightweight

`PatternSpecValidator` validates required sections, semantics, emit, lifecycle, operator shape, agent operator output schemas, and `AGENT_ACTION` governance/tool policy requirements.

`PatternSpecCompiler` compiles structurally valid PatternSpec maps into deterministic runtime graph contracts, producing a `CompiledPattern` with root runtime node, node order, metadata, semantics, emit, lifecycle, and governance.

This is good, but it is still mostly structural. It is not yet a complete production-grade EPL compiler with deep type checking, schema compatibility, executable graph binding, time semantics, uncertainty enforcement, replay guarantees, policy checks, and runtime deployment.

## 2.5 Uncertainty foundation exists but needs production calibration

`UncertaintyPropagator` implements deterministic propagation rules across AND, OR, SEQ, NOT/ABSENCE, WITHIN/WINDOW, TIMES/REPEAT, and all agent operators. It tracks event detection confidence, attribute confidence, temporal confidence, source reliability, pattern confidence, model confidence, retrieval confidence, input completeness, and calibration score.

This is a strong foundation. But production readiness still requires calibration, thresholds, golden datasets, replay validation, and operator-wide enforcement.

## 2.6 Agent runtime governance exists but is complex and needs productization

`GovernedAgentDispatcher` is a substantial governance-aware decorator over agent dispatch. It handles release guard, grant validation, invariant monitoring, mastery checks, version context checks, task classification, mode selection, trace recording, and OpenTelemetry spans.

It also records denial events and governance decisions when dispatch is denied, and it blocks execution based on release state, mastery state, approval proof, verification proof, and mode-selection decisions.

This is directionally strong, but the class is doing a lot. It should be decomposed into smaller policy/evaluator components before being treated as production stable.

---

# 3. Production readiness state

## 3.1 Readiness is explicitly blocked

The readiness file says:

```yaml
status: blocked
lifecycleExecutionAllowed: false
ordinaryLifecycleProductEnabled: false
```

It also requires platform-provider mode, bootstrap separation, runtime-truth providers, product neutrality, and Action Plane governance.

This is correct and should remain blocked until the product-provider gates have executable evidence.

## 3.2 CI is broad but advisory in places

`.github/workflows/data-cloud-ci.yml` is named “Data Cloud CI (Advisory).” It compiles selected modules, runs backend tests, frontend type checks/tests, architecture tests, route manifest drift, doc boundary lint, tenant isolation audit, connector validation, agent governance validation, boundary-language checks, maturity proof gate, SDK generation, smoke E2E advisory, backup drill advisory, Helm/k8s render validation, security-scan advisory, and bundle budget checks.

That is a strong gate set, but the initial build matrix does **not** compile/check every active Data-Cloud and Action Plane module. It focuses on shared-spi, runtime-composition, launcher, sdk, agent-registry, integration-tests, and some checks. Given the generated settings include many more modules, production readiness needs an all-active-module check.

## 3.3 Release workflow is stricter

`data-cloud-release.yml` validates release environment variables/secrets, blocks localhost release URLs, builds/checks selected modules, runs UI readiness, strict runtime profile checks, strict smoke E2E, strict backup drill, blocking dependency vulnerability check, and SBOM generation.

This is good, but the release workflow also checks only selected modules in its main build/test step. It should include all active Data-Cloud and co-located Action Plane modules, or explicitly justify excluded modules as non-release artifacts.

---

# 4. Key gaps to close

## Gap 1 — Data-Cloud is still blocked as platform-provider

The readiness file is honest. Keep it blocked until:

- platform-provider mode proof passes,
- runtime-truth provider proof passes,
- product-neutrality proof passes,
- Action Plane governance proof passes,
- release gates are strict and complete,
- all active modules compile/test/check,
- Data-Cloud is not enabled as ordinary lifecycle product.

## Gap 2 — AEP co-location is not yet migration-complete

The plane architecture says AEP modules may remain under `products/data-cloud/planes/action/*`, but this is temporary.

For now, that is acceptable. But production docs must distinguish:

```text
Code location: products/data-cloud/planes/action/*
Semantic ownership: AEP
Persistence/data substrate: Data-Cloud
```

## Gap 3 — Build/release gates do not cover all active modules

Generated settings include many Data-Cloud modules, but CI/release build commands cover only a subset.

This is the biggest practical production risk.

## Gap 4 — PatternSpec is still structural

The current validator/compiler are good anchors, but they are not yet enough for production-grade adaptive ESP. They must gain schema registry integration, output type checks, operator compatibility checks, time semantics enforcement, uncertainty propagation enforcement, replay semantics, and runtime DAG binding.

## Gap 5 — Agent operator contracts exist, but runtime integration must be universal

The contracts and tests exist. The next step is making every event-processing agent use in the Action Plane go through `EventOperatorCapability`, not direct callbacks, detector-specific agents, ad hoc dispatchers, or service-level shortcuts.

## Gap 6 — GovernedAgentDispatcher is too large

`GovernedAgentDispatcher` includes release checks, grants/invariants, mastery, version context, task classification, mode selection, memory retrieval, trace ledger, and OTel.

That is a lot for one class. It should be decomposed into:

```text
AgentReleaseGate
AgentGrantGate
AgentInvariantGate
AgentMasteryGate
AgentVersionGate
AgentModeSelectionGate
AgentMemoryRetrievalStage
AgentTraceStage
AgentDispatchPipeline
```

## Gap 7 — Data-Cloud production security must be enforced, not just documented

Data-Cloud owns audit, retention, encryption support, and governance. The production suite needs hard tests proving:

- tenant isolation,
- audit completeness,
- encryption/redaction,
- policy enforcement,
- route auth metadata,
- storage plugin behavior,
- backup/restore,
- runtime truth drift.

Some of these checks exist, but they must become strict release criteria for all active surfaces.

---

# 5. Production-ready product suite plan

## Phase 0 — Freeze coherent boundaries

**Goal:** Prevent future drift.

### Tasks

1. Keep `products/data-cloud/ARCHITECTURE.md` as canonical boundary summary.
2. Keep `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md` as canonical target organization.
3. Keep `products/aep/ARCHITECTURE.md` as canonical AEP semantic architecture.
4. Update any active docs that still imply Data-Cloud owns AEP/EventCloud semantics.
5. Add CI doc-boundary lint for:
   - “Data-Cloud owns EventCloud”
   - “Data-Cloud owns PatternSpec”
   - “Data-Cloud owns CEP”
   - “AEP is a Data-Cloud plane” outside migration notes.

6. Keep `AEP = separate adaptive event intelligence platform` even while code is co-located under `planes/action`.

### Exit criteria

- No active doc contradicts Data-Cloud/AEP/EventCloud ownership.
- Boundary lint passes.
- Plane architecture is the single source of organization language.

---

## Phase 1 — Make build coverage complete

**Goal:** The suite must compile/test all active modules, not just selected ones.

### Tasks

1. Generate a canonical Data-Cloud module list from `config/generated/settings-gradle-includes.kts`.
2. Add a Gradle task/script:

```bash
pnpm check:data-cloud-all-active-modules
```

or:

```bash
./gradlew $(node scripts/list-data-cloud-gradle-modules.mjs --check-tasks)
```

3. Include all active modules:
   - all `planes:data:*`,
   - all `planes:event:*`,
   - all `planes:context:*` when added,
   - all `planes:intelligence:*`,
   - all `planes:governance:*`,
   - all `planes:operations:*`,
   - all `planes:action:*`,
   - all `delivery:*`,
   - all `extensions:*`,
   - `contracts`,
   - `integration-tests`.

4. Mark modules as one of:
   - `release-blocking`,
   - `advisory`,
   - `experimental`,
   - `deprecated`,
   - `excluded with reason`.

5. CI must fail if an active release-blocking module is omitted.

### Exit criteria

- CI has a strict all-active-module compile check.
- Release gate has a strict all-release-module `check`.
- Every excluded module has an explicit reason.

---

## Phase 2 — Product-provider readiness unblock

**Goal:** Change readiness from `blocked` to `ready` only with executable proof.

### Tasks

1. Keep `lifecycleExecutionAllowed: false`.
2. Keep Data-Cloud as `platform-provider`, not ordinary lifecycle product.
3. Run and enforce:
   - `scripts/check-data-cloud-platform-provider-readiness.mjs`
   - `scripts/check-data-cloud-platform-providers.mjs`
   - `scripts/check-kernel-provider-mode.mjs`

4. Add proof artifacts under `.kernel/evidence/data-cloud/`.
5. Validate all evidence refs exist.
6. Ensure Data-Cloud core fallback catalog roots do not hardcode business product roots.

The readiness script already checks required gates, required runtime signals, evidence refs, Action Plane governance requirements, conformance proof, and product neutrality.

### Exit criteria

- `products/data-cloud/lifecycle/readiness-evidence.yaml` can move from `blocked` to `ready`.
- All checks are executable, not manually asserted.
- Product neutrality test blocks hardcoded business product roots.

---

## Phase 3 — Harden Data-Cloud planes

**Goal:** Make Data-Cloud independently production-grade as a governed storage/data platform.

### Data Plane

Tasks:

1. Enforce tenant-scoped entity operations.
2. Add contract tests for entity CRUD.
3. Add data-quality checks.
4. Add schema evolution and compatibility tests.
5. Raise coverage threshold above the current lowered baseline.

The entity module’s coverage threshold is explicitly lowered to 0.20 to match actual coverage, which is not production-grade.

Exit criteria:

- Entity/Data Plane coverage target moves toward 0.70+ for critical code.
- All data operations require tenant context.
- Data-quality failures produce structured evidence.

### Event Plane

Tasks:

1. Clarify Data-Cloud EventLog vs AEP EventCloud.
2. Add contract tests for append/read/tail/replay.
3. Add offset and idempotency tests.
4. Add Postgres Testcontainers coverage.
5. Add replay consistency tests.

The event store module already describes itself as warm-tier event log store and includes Testcontainers dependencies.

Exit criteria:

- Data-Cloud EventLog contract is stable.
- EventLog does not expose CEP or PatternSpec semantics.
- EventLog is suitable as persistence substrate.

### Governance Plane

Tasks:

1. Centralize tenant isolation.
2. Add policy checks for every route category.
3. Ensure audit events for all mutating operations.
4. Add retention/redaction/encryption tests.
5. Add legal hold and deletion lifecycle tests.

Exit criteria:

- No production route without auth/tenant/policy metadata.
- Audit coverage is measurable.
- Encryption/redaction is test-backed.

### Operations Plane

Tasks:

1. Runtime truth registry.
2. Health/degraded/unavailable states.
3. Dependency probes.
4. Backup/restore runbooks.
5. SLO/cost budgets.
6. Alert rules.

Exit criteria:

- Runtime truth is queryable by UI/API.
- Strict release smoke and backup drills pass.
- Degraded dependencies are surfaced correctly.

---

## Phase 4 — Harden co-located AEP Action Plane

**Goal:** Make AEP-in-Data-Cloud production-ready without confusing ownership.

### Tasks

1. Keep Action Plane as compatibility/migration area.
2. Enforce AEP package boundary tests.
3. Keep `com.ghatana.aep` from depending on `com.ghatana.datacloud`.
4. Add reverse rule: Data-Cloud core planes must not import `com.ghatana.aep`.
5. Move shared contracts to stable `operator-contracts`.
6. Document every Action Plane module as:
   - AEP semantic module,
   - Data-Cloud persistence bridge,
   - Action compatibility API,
   - or migration-only.

The current AEP boundary test blocks AEP from depending on Data-Cloud and other product namespaces.

### Exit criteria

- Action Plane module inventory is explicit.
- Boundary tests cover both directions.
- AEP semantics stay isolated even while physically co-located.

---

## Phase 5 — Complete PatternSpec production path

**Goal:** Move from structural PatternSpec to executable governed PatternSpec.

### Tasks

1. Convert map-based PatternSpec to typed model.
2. Add JSON Schema/protobuf for PatternSpec.
3. Integrate schema registry.
4. Add operator compatibility checks.
5. Add time semantics validation.
6. Add uncertainty semantics validation.
7. Add agent replay-policy validation.
8. Add lifecycle state validation.
9. Add governance policy validation.
10. Compile PatternSpec into runtime DAG.
11. Bind DAG to EventCloud source/sink and operator runtime.
12. Add golden tests:
    - valid patterns,
    - invalid patterns,
    - agent predicate inside `SEQ`,
    - action operator without approval rejected,
    - uncertainty threshold enforced,
    - replay deterministic.

### Exit criteria

- PatternSpec compiler produces executable DAG.
- Every PatternSpec execution is observable and replayable.
- Agent operators work seamlessly in pattern definitions.

---

## Phase 6 — Complete EventOperatorCapability runtime

**Goal:** No bypasses.

### Tasks

1. Find all direct agent dispatcher usage.
2. Wrap all event-processing agent interactions as `EventOperatorCapability`.
3. Find all old operator implementations not using `EventOperator`.
4. Migrate old operators to unified contract.
5. Add runtime adapter for:
   - `AGENT_PREDICATE`,
   - `AGENT_ENRICH`,
   - `AGENT_EXTRACT`,
   - `AGENT_PATTERN_SYNTHESIS`,
   - `AGENT_EXPLANATION`,
   - `AGENT_REVIEW`,
   - `AGENT_ACTION`,
   - `AGENT_REFLECTION`.

6. Split side-effecting actions from pure inference.
7. Enforce policy for side-effecting operators.
8. Add replay-safe agent execution records.

### Exit criteria

- All operator runtime paths use `EventOperator`.
- All event-processing agent runtime paths use `EventOperatorCapability`.
- Side-effecting action cannot run without tool policy, approval policy, audit policy, and idempotency.

---

## Phase 7 — Decompose GovernedAgentDispatcher

**Goal:** Production maintainability.

### Tasks

Refactor:

```text
GovernedAgentDispatcher
  -> AgentDispatchPipeline
  -> AgentReleaseGate
  -> AgentCapabilityGate
  -> AgentMasteryGate
  -> AgentVersionGate
  -> AgentApprovalGate
  -> AgentVerificationGate
  -> AgentModeSelectionStage
  -> AgentMemoryRetrievalStage
  -> AgentTraceStage
  -> AgentDelegateInvoker
```

### Exit criteria

- Each gate has isolated unit tests.
- Dispatcher orchestration has integration tests.
- No single class owns all governance logic.
- Trace ledger behavior remains identical.

---

## Phase 8 — Production release gates

**Goal:** Make release workflow authoritative.

### Tasks

1. Rename advisory CI language where appropriate.
2. Keep advisory jobs for developer feedback.
3. Make release workflow strict for:
   - all active module checks,
   - route/runtime truth drift,
   - OpenAPI drift,
   - SDK generation,
   - architecture tests,
   - tenant isolation,
   - connector validation,
   - agent governance validation,
   - maturity proof,
   - security scan,
   - SBOM,
   - smoke E2E,
   - backup/restore drill,
   - Helm/k8s render,
   - UI a11y/i18n,
   - SLO/cost/domain invariant checks.

4. Add release artifact bundle:
   - readiness evidence,
   - route manifest,
   - OpenAPI spec,
   - SBOM,
   - smoke report,
   - backup report,
   - security scan,
   - architecture test report,
   - coverage report.

### Exit criteria

- No production/staging release without strict evidence.
- Advisory CI cannot be mistaken as release certification.
- Release gate blocks missing environment secrets and localhost release endpoints, as it already begins doing.

---

# 6. Recommended maturity score at commit `30a4e442`

| Area                          | Score | Rationale                                                                                                       |
| ----------------------------- | ----: | --------------------------------------------------------------------------------------------------------------- |
| Vision coherence              |  8/10 | Active docs now clearly separate Data-Cloud/AEP/EventCloud and support temporary co-location.                   |
| Code organization             |  7/10 | Plane model and generated includes are strong, but Action Plane is large and mixed.                             |
| Build/release discipline      |  6/10 | CI/release gates are broad, but module coverage appears incomplete and CI is partly advisory.                   |
| Data-Cloud substrate maturity |  6/10 | Entity/event/governance/operations planes exist, but readiness is blocked and coverage/security need hardening. |
| AEP semantic foundation       |  7/10 | EventOperator, EventOperatorCapability, PatternSpec, uncertainty foundations exist.                             |
| AEP runtime completeness      |  5/10 | Compiler/validator are structural; executable adaptive runtime path is not complete.                            |
| Agent capability maturity     |  7/10 | Contracts/tests exist; full runtime migration remains.                                                          |
| Production readiness          |  5/10 | Strong direction and gates, but readiness is explicitly blocked.                                                |

Overall: **6.3/10 — architecturally promising, not yet production-ready.**

---

# 7. Immediate next todo list

Do these in order:

1. **Add all-active Data-Cloud module check** from generated settings.
2. **Update CI/release workflows** so active module coverage is explicit and complete.
3. **Keep readiness blocked** until executable evidence passes.
4. **Create Action Plane module inventory** and classify every module.
5. **Add reverse boundary tests**: Data/Event/Context/Governance must not import AEP.
6. **Migrate all event-processing agent execution through EventOperatorCapability**.
7. **Upgrade PatternSpec from structural validator to typed schema-backed compiler.**
8. **Bind PatternSpec compiler output to executable runtime DAG.**
9. **Add EventCloud/Data-Cloud bridge contract tests.**
10. **Decompose GovernedAgentDispatcher.**
11. **Raise low coverage thresholds for critical modules.**
12. **Make release workflow the only production certification gate.**

---

# Final recommendation

Keep AEP inside Data-Cloud for now, but formalize it as:

```text
products/data-cloud/planes/action/*
  = temporary co-located AEP/action implementation area

products/data-cloud/*
  = governed storage/data/product-provider substrate

products/aep/*
  = semantic architecture, specs, dissertation traceability, and eventual product boundary
```

The current codebase is moving in the right direction. The next hardening step is not another conceptual rewrite; it is **executable proof**: complete module checks, boundary tests, strict release gates, typed PatternSpec runtime, and universal capability-bound agent execution.
