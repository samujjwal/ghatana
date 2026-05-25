Below is a **granular, implementation-ready task backlog** for moving **Data-Cloud + co-located AEP/Action Plane** toward a production-ready product suite at commit `d951b48a6ee8197ec6bb24919e21134844fe30c6`.

The governing architecture is now:

> **Data-Cloud owns governed data/storage substrate. AEP owns adaptive event intelligence semantics. Action Plane is the temporary co-located AEP/runtime compatibility area. Agent is the root abstraction. Event processing is exposed through `EventOperatorCapability`, not `AgentOperator extends EventOperator`.**

This is consistent with current Data-Cloud and AEP architecture docs. Data-Cloud explicitly excludes CEP, PatternSpec/EPL, EventCloud, EventOperatorCapability semantics, pattern learning/adaptation, and agent orchestration from its ownership.  AEP architecture now states that the agent is the root abstraction and event processing is exposed as an `EventOperatorCapability`. 

---

# P0 — Keep readiness blocked until evidence is current and executable

## DC-P0-001 — Regenerate all evidence at current commit

**Where**

* `.kernel/evidence/data-cloud-active-modules.json`
* `.kernel/evidence/action-plane-boundaries.json`
* `.kernel/evidence/product-release-readiness.json`
* `.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json`

**What to do**

Regenerate evidence at `d951b48a6ee8197ec6bb24919e21134844fe30c6` or the current HEAD of the implementation branch.

**Why**

Current evidence references `e346600...`, not `d951b48...`; readiness correctly remains blocked until evidence matches the current commit.  

**Commands**

```bash
pnpm check:data-cloud-active-module-evidence
pnpm check:action-plane-boundaries
pnpm check:product-release-readiness
pnpm check:ai-governance-behavioral-proof
pnpm check:evidence-current-commit
```

**Acceptance criteria**

* Every evidence JSON with `evidenceRun.commit` matches current HEAD.
* `scripts/check-evidence-current-commit.mjs` passes.
* No readiness field is manually flipped without executable proof.

---

## DC-P0-002 — Keep `readiness-evidence.yaml` blocked until all hard conditions pass

**Where**

* `products/data-cloud/lifecycle/readiness-evidence.yaml`

**What to do**

Do not change:

```yaml
status: blocked
lifecycleExecutionAllowed: false
ordinaryLifecycleProductEnabled: false
```

until all `keepStatusBlockedUntil` conditions are satisfied.

**Why**

The readiness file requires current-commit evidence, release-readiness pass, zero AI-governance warnings, active-module compile/release checks, and Action Plane boundary tests. 

**Acceptance criteria**

* `pnpm check:data-cloud-platform-provider-readiness` passes.
* Product remains registered as `platform-provider`.
* Data-Cloud is not enabled as an ordinary lifecycle product.

---

## DC-P0-003 — Add evidence freshness summary to CI output

**Where**

* `.github/workflows/data-cloud-ci.yml`
* `.github/workflows/data-cloud-release.yml`
* `scripts/check-evidence-current-commit.mjs`

**What to do**

Enhance CI/release output to list stale files explicitly, for example:

```text
Stale evidence:
- .kernel/evidence/data-cloud-active-modules.json generated for e346600..., expected d951b48...
- .kernel/evidence/action-plane-boundaries.json generated for e346600..., expected d951b48...
```

**Acceptance criteria**

* CI summary names each stale file.
* Release gate fails on stale evidence.
* Developers do not need to inspect JSON manually to find stale proof.

---

# P1 — Active module coverage and release-blocking build proof

## DC-P1-001 — Keep generated active-module list as the source of truth

**Where**

* `config/generated/settings-gradle-includes.kts`
* `scripts/list-data-cloud-active-modules.mjs`

**What to do**

Continue deriving active Data-Cloud modules from generated Gradle includes, not hand-maintained workflow lists.

**Why**

The generated includes list 36 Data-Cloud modules, including Action Plane modules.  The module-list script parses generated settings, classifies modules, and generates Gradle task lists. 

**Acceptance criteria**

* `node scripts/list-data-cloud-active-modules.mjs --validate` passes.
* New Data-Cloud modules fail validation unless classified.
* No workflow hardcodes a partial module list for release checks.

---

## DC-P1-002 — Make all release-blocking module checks strict in release workflow

**Where**

* `.github/workflows/data-cloud-release.yml`

**What to do**

Keep this pattern:

```bash
TASKS="$(node ./scripts/list-data-cloud-active-modules.mjs --scope=release-blocking --task=check --format=shell)"
./gradlew ${TASKS} --no-daemon
```

This is already present and should be treated as canonical. 

**Add**

* Upload Gradle test reports for all release-blocking modules.
* Fail if generated task list is empty.
* Fail if any release-blocking module has no `check` task.

**Acceptance criteria**

* 34/34 release-blocking modules are checked in release gate.
* Advisory modules remain advisory only with documented reason.
* Release evidence includes task list and result.

---

## DC-P1-003 — Compile all active Java modules in advisory CI

**Where**

* `.github/workflows/data-cloud-ci.yml`

**What to do**

Keep and harden the existing all-active compile step:

```bash
TASKS="$(node ./scripts/list-data-cloud-active-modules.mjs --scope=all-active --task=compileJava --format=shell)"
./gradlew ${TASKS} --no-daemon
```

The CI workflow already does this. 

**Acceptance criteria**

* CI compiles all active Java modules.
* CI regenerates active-module evidence.
* CI runs Action Plane boundary checks.
* CI checks evidence freshness.

---

## DC-P1-004 — Promote advisory modules when they become production surfaces

**Where**

* `scripts/list-data-cloud-active-modules.mjs`

**Current advisory modules**

* `:products:data-cloud:delivery:api-contract-tests`
* `:products:data-cloud:integration-tests`

**What to do**

Keep advisory only if these modules are test-only and not deployable production runtime. If any advisory module exposes production contracts, generated artifacts, or release evidence, promote it to `release-blocking`.

**Acceptance criteria**

* Advisory list remains tiny and justified.
* No production runtime module is advisory.
* Generated evidence explains each advisory classification.

---

# P2 — Data-Cloud/AEP/Action Plane boundary hardening

## DC-P2-001 — Preserve Data-Cloud non-goals

**Where**

* `products/data-cloud/ARCHITECTURE.md`
* `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`

**What to do**

Keep Data-Cloud non-goals explicit:

* no CEP ownership,
* no PatternSpec/EPL ownership,
* no EventCloud ownership,
* no EventOperatorCapability semantics,
* no pattern learning/adaptation,
* no agent orchestration.

**Why**

The Data-Cloud architecture currently states this correctly. 

**Acceptance criteria**

* Active docs never position Data-Cloud as EventCloud or CEP.
* Data-Cloud may store metadata/evidence but must not own AEP behavior.
* Boundary language lint catches drift.

---

## DC-P2-002 — Expand Action Plane boundary scanner to delivery/extensions

**Where**

* `scripts/check-action-plane-boundaries.mjs`

**Current state**

The scanner checks non-action Data-Cloud planes and excludes `planes/action`. It catches AEP internal imports, Action Plane Gradle deps, EventCloud semantics, PatternSpec/EPL, EventOperatorCapability, CEP, and adaptive runtime semantics. 

**What to add**

Scan these roots too:

```text
products/data-cloud/delivery
products/data-cloud/extensions
products/data-cloud/contracts
```

With explicit allowlists:

```text
delivery/runtime-composition may compose planes
extensions/kernel-bridge may use public contracts/SPI
contracts must not import runtime implementation
```

**Acceptance criteria**

* Non-action planes cannot import Action Plane internals.
* Delivery UI/API cannot import backend internals except through generated clients/contracts.
* Extensions depend on SPI/contracts only.
* Boundary evidence includes scanned roots and violation counts.

---

## DC-P2-003 — Add ArchUnit parity tests for script boundary rules

**Where**

* `products/data-cloud/planes/*/src/test/java`
* `products/data-cloud/delivery/*/src/test/java`
* `products/data-cloud/extensions/*/src/test/java`

**What to do**

Mirror `check-action-plane-boundaries.mjs` rules with Java ArchUnit tests:

```text
planes.data.. must not depend on com.ghatana.aep..
planes.event.. must not depend on products.data-cloud.planes.action..
planes.governance.. must not depend on AEP runtime packages
contracts must not depend on implementation packages
```

**Acceptance criteria**

* Script and ArchUnit enforce same rules.
* CI runs both.
* A violation fails before release.

---

## DC-P2-004 — Create Action Plane module inventory

**Where**

Create:

```text
products/data-cloud/planes/action/MODULE_INVENTORY.md
```

**What to include**

For every Action Plane module:

| Module | Role | Owner | Release-blocking | Public surface | Allowed dependencies | Required tests |
| ------ | ---- | ----- | ---------------- | -------------- | -------------------- | -------------- |

Cover:

```text
action
operator-contracts
central-runtime
engine
registry
analytics
security
event-bridge
agent-runtime
api
scaling
observability
orchestrator
server
identity
compliance
kernel-bridge
```

**Acceptance criteria**

* Every Action Plane module has role and owner.
* Every module has explicit production status.
* Migration-only modules are identified.
* Runtime modules are distinct from persistence bridge modules.

---

# P3 — Agent capability architecture cleanup

## DC-P3-001 — Fully retire stale `AgentOperator` terminology

**Where**

* `products/aep/ARCHITECTURE.md`
* `products/aep/docs/specs/*`
* `products/aep/docs/DISSERTATION_TRACEABILITY.md`
* `docs/adr/*`
* `docs/03-architecture/*`
* `.github/copilot-instructions.md`

**What to do**

Use:

```text
Agent root abstraction
EventOperatorCapability
capabilityRef
capability binding
AgentCapability
```

Do not use:

```text
AgentOperatorAdapter
AgentOperatorFactory
AgentOperator extends EventOperator
agent-as-operator
```

**Why**

The repository now has a guard script that enforces current capability-based terminology.

**Acceptance criteria**

* `node scripts/check-agent-capability-duplicates.mjs` passes.
* All PatternSpec examples use `capabilityRef`.

---

## DC-P3-002 — Rename or supersede stale spec docs

**Where**

* `products/aep/docs/specs/AGENT_OPERATOR_SPEC.md`
* `docs/adr/ADR-agent-as-event-operator.md`

**What to do**

Either rename or explicitly supersede:

```text
AGENT_OPERATOR_SPEC.md -> EVENT_OPERATOR_CAPABILITY_SPEC.md
ADR-agent-as-event-operator.md -> superseded by ADR-agent-capability-event-operator.md
```

**Acceptance criteria**

* Old files either removed, renamed, or marked superseded.
* New docs define `EventOperatorCapability`.
* No contradictory ADRs remain active.

---

## DC-P3-003 — Keep `EventOperatorCapability` as canonical event-agent bridge

**Where**

* `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/agent/capability/EventOperatorCapability.java`

**Current state**

`EventOperatorCapability<I, O>` extends both `AgentCapability<EventContext<I>, EventOperatorResult<O>>` and `EventOperator<I, O>`. 

**What to do**

Preserve this type as canonical.

**Acceptance criteria**

* No parallel `AgentOperator` contract is reintroduced.
* PatternSpec binds to capabilities.
* Event runtime sees capability as an `EventOperator`.
* Agent registry sees capability as an `AgentCapability`.

---

## DC-P3-004 — Strengthen capability architecture tests

**Where**

* `products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/operator/agent/EventOperatorCapabilityArchitectureContractTest.java`

**Current state**

The test verifies `EventOperatorCapability` is both `AgentCapability` and `EventOperator`, validates capability descriptors, canonical agent capability roles, and governance requirements for `AGENT_ACTION`. 

**Add tests**

* `EventOperatorCapability` must expose `capabilityId`.
* `AGENT_ACTION` must require:

  * tool policy,
  * approval/review policy,
  * audit policy,
  * idempotency policy.
* `AGENT_PREDICATE` must be pure inference by default.
* `AGENT_REVIEW` must not self-approve high-risk production changes.
* `AGENT_PATTERN_SYNTHESIS` must emit pattern suggestion, not active deployment.

**Acceptance criteria**

* Capability roles are tested.
* Side-effect policy behavior is tested.
* Pattern lifecycle safety is tested.

---

# P4 — AgentEventOperatorCapabilityAdapter production hardening

## DC-P4-001 — Remove temporary `process(Event)` bridge by deadline

**Where**

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/registry/AgentEventOperatorCapabilityAdapter.java`

**Current state**

The adapter has a temporary `process(Event)` bridge marked for removal by `2026-06-30`. 

**What to do**

* Find all callers using `process(Event)`.
* Migrate them to `process(EventContext, OperatorRuntimeContext)`.
* Remove bridge after deadline.
* Keep compatibility check failing after deadline.

**Acceptance criteria**

* No runtime path uses `process(Event)`.
* Temporary bridge removed by deadline.
* Tests fail if bridge survives past deadline.

---

## DC-P4-002 — Add adapter instantiation tests for required policies

**Where**

* `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/registry/AgentEventOperatorCapabilityAdapterTest.java`

**Current behavior**

Constructor requires non-empty model/tool/memory/retrieval/guardrail/replay/uncertainty/human-review/observability policies and durable memory store unless explicitly allowed. 

**Add tests**

* Empty `modelPolicy` fails.
* Empty `toolPolicy` fails.
* Empty `memoryPolicy` fails.
* Empty `retrievalPolicy` fails.
* Empty `guardrailPolicy` fails.
* Empty `replayPolicy` fails.
* Empty `uncertaintyPolicy` fails.
* Empty `humanReviewPolicy` fails.
* Empty `observabilityPolicy` fails.
* No-op memory store fails unless `allowNoOpMemoryStore=true`.

**Acceptance criteria**

* All policy requirements are enforced by tests.
* No silent fallback policy exists.
* No default tenant or fake memory path exists.

---

## DC-P4-003 — Add tenant/trace/correlation runtime tests

**Where**

* `AgentEventOperatorCapabilityAdapterTest.java`

**Current behavior**

The adapter requires tenant match, trace ID, and correlation ID when creating agent context. 

**Add tests**

* Event tenant differs from runtime tenant → fail.
* Missing trace ID → fail.
* Missing correlation ID → fail.
* Blank tenant ID → fail.
* Valid context maps into `AgentContext`.

**Acceptance criteria**

* Capability execution is tenant-safe.
* Trace/correlation are mandatory.
* No fallback tenant identity exists.

---

## DC-P4-004 — Add uncertainty/evidence mapping tests

**Where**

* `AgentEventOperatorCapabilityAdapterTest.java`

**Current behavior**

The adapter maps agent result confidence into `UncertaintyContext`, includes retrieval/calibration confidence, evidence, tenant, trace, correlation, latency, audit policy, and idempotency indicators. 

**Add tests**

* `modelConfidence` equals clamped agent confidence.
* `retrievalConfidence` comes from agent metrics when present.
* `calibrationScore` comes from agent metrics when present.
* Evidence includes:

  * `agentRef`,
  * `capabilityId`,
  * `operatorId`,
  * `tenantId`,
  * `traceId`,
  * `correlationId`,
  * `sideEffectProfile`,
  * `idempotencyRequired`.
* NaN confidence clamps to `0.0`.

**Acceptance criteria**

* Every output has explainable uncertainty.
* Every output has replay/audit evidence.
* Metrics are not empty.

---

# P5 — PatternSpec production path

## DC-P5-001 — Convert PatternSpec from map structure to typed model

**Where**

* `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec`
* `products/aep/contracts/schemas`

**Current state**

`PatternSpecValidator` accepts `Map<String, Object>` and performs structural validation. 

**What to do**

Create typed records/classes:

```text
PatternSpec
PatternMetadata
PatternSemantics
PatternExpression
PatternEmit
PatternLifecycle
PatternGovernance
PatternObservability
CapabilityBinding
```

**Acceptance criteria**

* Map parsing happens at boundary only.
* Internal compiler uses typed model.
* Invalid type cannot enter runtime.

---

## DC-P5-002 — Validate `capabilityRef` against registry

**Where**

* `PatternSpecValidator.java`
* `PatternSpecCompiler.java`
* capability registry module under Action Plane

**Current state**

Validator requires `capabilityRef` for agent operators. 

**What to add**

* Resolve `capabilityRef`.
* Verify capability exists.
* Verify capability kind matches operator.
* Verify input/output schema compatibility.
* Verify side-effect profile matches governance policy.

**Acceptance criteria**

* Unknown `capabilityRef` fails.
* Wrong capability kind fails.
* Side-effecting capability without production policy fails.

---

## DC-P5-003 — Enforce production governance fields

**Where**

* `PatternSpecValidator.java`

**Current state**

Production validation requires evidence policy/store, commit SHA, and approval/review policy. 

**Add**

* Require `governance.owner`.
* Require `governance.riskLevel`.
* Require `governance.rollbackPolicy`.
* Require `governance.auditPolicy`.
* Require `lifecycle.state` to be valid transition target.
* Require `lifecycle.evidenceStore` to use Data-Cloud/AEP-approved store.

**Acceptance criteria**

* Production PatternSpec without owner fails.
* Production PatternSpec without rollback policy fails.
* Invalid lifecycle transition fails.

---

## DC-P5-004 — Compile PatternSpec into executable runtime DAG

**Where**

* `PatternSpecCompiler.java`
* `PatternRuntimeNode.java`
* `PatternPipelineAdapter.java`

**What to do**

Move beyond structural compilation:

1. Resolve event types.
2. Resolve operator kinds.
3. Resolve capability bindings.
4. Resolve schemas.
5. Resolve time policy.
6. Resolve uncertainty policy.
7. Generate runtime DAG.
8. Bind DAG to EventCloud/EventLog source and sinks.
9. Validate replay mode.
10. Validate shadow vs active behavior.

**Acceptance criteria**

* CompiledPattern is executable.
* Shadow patterns produce no side effects.
* Active patterns require approval.
* DAG contains capability nodes for `AGENT_*`.

---

## DC-P5-005 — Add golden PatternSpec tests

**Where**

* `products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec`

**Test cases**

* Valid `SEQ` with `AGENT_PREDICATE`.
* Valid `WINDOW` with `AGENT_ENRICH`.
* Valid learning pipeline with `AGENT_PATTERN_SYNTHESIS`.
* Invalid unknown operator.
* Invalid unknown `capabilityRef`.
* Invalid missing output schema.
* Invalid production missing commit SHA.
* Invalid production missing evidence store.
* Invalid `AGENT_ACTION` missing tool policy.
* Invalid `AGENT_ACTION` missing approval policy.
* Invalid side-effect capability used in shadow without no-op action policy.

**Acceptance criteria**

* Tests cover success and failure paths.
* Golden YAML/JSON examples live in `products/aep/docs/examples`.
* Runtime DAG snapshots are stable.

---

# P6 — Data-Cloud planes production hardening

## DC-P6-001 — Data Plane tenant isolation

**Where**

* `products/data-cloud/planes/data/entity`

**Tasks**

* Add tenant-scoped CRUD tests.
* Add cross-tenant read denial tests.
* Add cross-tenant update/delete denial tests.
* Add tenant missing/blank denial tests.
* Add audit event expectation for mutating operations.

**Acceptance criteria**

* No entity operation succeeds without tenant context.
* Cross-tenant operations fail closed.
* Audit evidence is emitted.

---

## DC-P6-002 — Event Plane EventLog contract hardening

**Where**

* `products/data-cloud/planes/event/core`
* `products/data-cloud/planes/event/store`

**Tasks**

* Define Data-Cloud EventLog contract separately from AEP EventCloud.
* Test append/read/tail/replay.
* Test offset consistency.
* Test idempotency.
* Test retention.
* Test tenant isolation.
* Test replay determinism for storage-plane events.

**Acceptance criteria**

* EventLog does not expose CEP/EventCloud semantics.
* EventLog can serve as AEP persistence substrate.
* EventLog plugin behavior is contract-tested.

---

## DC-P6-003 — Governance Plane audit/encryption/retention

**Where**

* `products/data-cloud/planes/governance/core`

**Tasks**

* Add audit policy model.
* Add retention policy model.
* Add redaction policy model.
* Add encryption policy model.
* Add legal hold support.
* Add tests for all policy outcomes.

**Acceptance criteria**

* Every mutating operation can produce audit evidence.
* Sensitive fields can be redacted/encrypted.
* Retention/legal hold rules are test-backed.
* Production profile fails if policy engine missing.

---

## DC-P6-004 — Operations Plane runtime truth

**Where**

* `products/data-cloud/planes/operations/config`
* `products/data-cloud/extensions/kernel-bridge`

**Tasks**

* Ensure runtime truth exposes:

  * live/degraded/unavailable,
  * dependencies,
  * health snapshots,
  * tenant scoping,
  * provenance refs,
  * artifact refs.
* Add failure injection tests.
* Add degraded dependency tests.
* Add UI/API contract for runtime truth.

**Acceptance criteria**

* Runtime truth provider tests pass.
* Release evidence includes runtime truth proof.
* UI does not infer runtime availability locally.

---

# P7 — CI and release gate tightening

## DC-P7-001 — Make release gate authoritative

**Where**

* `.github/workflows/data-cloud-release.yml`

**Tasks**

Keep strict release flow and ensure it includes:

* active module classification,
* release-blocking Gradle checks,
* stale evidence check,
* product release readiness,
* AI governance behavioral proof,
* smoke E2E strict,
* backup drill strict,
* SBOM,
* dependency vulnerability check,
* OpenAPI breaking-change check,
* route runtime truth drift,
* i18n/a11y/SLO/cost/domain invariants.

**Acceptance criteria**

* Advisory CI cannot certify production.
* Release gate is the only production certification.
* Every release artifact is uploaded.

---

## DC-P7-002 — Stop treating generated evidence as proof unless commands executed

**Where**

* `scripts/generate-data-cloud-active-modules-evidence.mjs`
* `.kernel/evidence/data-cloud-active-modules.json`

**Current concern**

Evidence currently proves tasks are generated, not necessarily that all tasks completed successfully unless paired with workflow execution. 

**What to do**

Extend evidence with:

```json
{
  "execution": {
    "gradleExitStatus": 0,
    "executedTasks": [...],
    "failedTasks": [],
    "skippedTasks": [],
    "durationMs": 12345
  }
}
```

**Acceptance criteria**

* Evidence distinguishes generated tasks from executed tasks.
* Release gate uploads execution result evidence.
* Readiness requires executed proof, not generated proof alone.

---

## DC-P7-003 — Add current-commit evidence to release artifact bundle

**Where**

* `.github/workflows/data-cloud-release.yml`

**Tasks**

Upload:

```text
.kernel/evidence/data-cloud-active-modules.json
.kernel/evidence/action-plane-boundaries.json
.kernel/evidence/product-release-readiness.json
.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json
.kernel/evidence/data-cloud-release-runtime-profile.json
.kernel/evidence/openapi-breaking-changes.json
```

**Acceptance criteria**

* Release artifact bundle contains all readiness proofs.
* Bundle evidence commit equals released commit.
* Missing artifact fails release.

---

# P8 — Documentation cleanup and coherence

## DC-P8-001 — Update all docs to EventOperatorCapability language

**Where**

* `products/aep/docs/specs/PATTERNSPEC.md`
* `products/aep/docs/specs/EVENT_OPERATOR.md`
* `products/aep/docs/specs/UNCERTAINTY_SEMANTICS.md`
* `products/aep/docs/DISSERTATION_TRACEABILITY.md`
* `products/aep/docs/architecture/AGENTIC_EVENT_PROCESSOR_E2E_AGENTIC_IMPLEMENTATION_PLAN.md`
* `docs/03-architecture/current-features/AGENTIC_EVENT_PROCESSOR_CURRENT_FEATURES.md`
* `docs/03-architecture/planned-features/AGENTIC_EVENT_PROCESSOR_PLANNED_FEATURES.md`

**Tasks**

* Replace stale agent-as-operator wording.
* Use `capabilityRef` in examples.
* Explain that EventOperatorCapability implements event-operator contract.
* Keep dissertation mapping intact.
* Make modern AI/LLM/RAG/tool use extensions capability-bound.

**Acceptance criteria**

* `check-agent-capability-duplicates.mjs` passes.
* Docs are internally consistent.
* No conflicting ADRs.

---

## DC-P8-002 — Create a production-readiness map

**Where**

Create:

```text
products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md
```

**Content**

Include sections:

```text
Boundary readiness
Active module readiness
Evidence readiness
Data Plane readiness
Event Plane readiness
Governance Plane readiness
Operations Plane readiness
Action Plane readiness
PatternSpec readiness
EventOperatorCapability readiness
Release gate readiness
```

Each task should include:

```text
ID
Status
Owner
Path
Blocking? yes/no
Evidence command
Evidence file
Acceptance criteria
```

**Acceptance criteria**

* One canonical task map exists.
* Readiness YAML links to it.
* Release workflow links to it in summary.

---

# P9 — Action Plane runtime decomposition

## DC-P9-001 — Decompose large agent dispatch governance

**Where**

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

**Tasks**

Split into:

```text
AgentDispatchPipeline
AgentReleaseGate
AgentCapabilityGate
AgentInvariantGate
AgentMasteryGate
AgentVersionGate
AgentApprovalGate
AgentVerificationGate
AgentModeSelectionStage
AgentMemoryRetrievalStage
AgentTraceStage
AgentDelegateInvoker
```

**Acceptance criteria**

* Each gate has isolated unit tests.
* Dispatcher integration test proves same behavior.
* Trace events remain identical.
* No gate silently allows missing policy.

---

## DC-P9-002 — Add execution-path audit for direct agent bypass

**Where**

Create:

```text
scripts/check-agent-runtime-bypass.mjs
```

**Detect**

* direct `TypedAgent.process` outside capability adapter,
* direct `AgentDispatcher.dispatch` from PatternSpec/runtime paths,
* model calls outside approved agent runtime,
* tool calls outside capability/tool policy,
* direct production action calls without `AgentAction`/capability policy.

**Acceptance criteria**

* Script runs in CI and release.
* Violations fail release.
* Allowlist is tiny and documented.

---

# P10 — AEP adaptive ESP completion

## DC-P10-001 — Implement predictive/recommended/shadow lifecycle persistence

**Where**

* `products/data-cloud/planes/action/registry`
* `products/data-cloud/planes/action/engine`
* `products/aep/docs/specs/PATTERN_LIFECYCLE.md`

**Tasks**

Implement lifecycle states:

```text
DRAFT
CANDIDATE
VALIDATED
SHADOW
RECOMMENDED
APPROVED
ACTIVE
DEGRADED
RETIRED
```

**Acceptance criteria**

* Invalid transitions fail.
* Recommended pattern cannot become active without policy.
* Shadow pattern has no production side effects.
* Lifecycle events are persisted and auditable.

---

## DC-P10-002 — Implement learning-to-recommendation flow

**Where**

* `products/data-cloud/planes/action/analytics`
* `products/data-cloud/planes/action/engine`
* `products/data-cloud/planes/action/orchestrator`

**Tasks**

* Correlated event group mining.
* Similarity-based extraction.
* Pattern candidate scoring.
* `pattern.suggested` event.
* Shadow deployment.
* Review request.
* Promotion/retirement.

**Acceptance criteria**

* Learning never mutates active rules directly.
* Pattern suggestions are typed events.
* Expert/human/agent review is auditable.
* False-positive/false-negative shadow metrics are stored.

---

## DC-P10-003 — Complete EventCloud persistence bridge

**Where**

* `products/data-cloud/planes/action/event-bridge`
* `products/data-cloud/planes/event/store`

**Tasks**

* Define AEP EventCloud SPI.
* Implement Data-Cloud storage bridge.
* Test append/tail/replay/checkpoint/watermark/partial-match persistence.
* Ensure Data-Cloud remains storage-only.

**Acceptance criteria**

* EventCloud semantics remain AEP-owned.
* Data-Cloud bridge passes SPI contract tests.
* No PatternSpec/EPL logic leaks into Data-Cloud planes.

---

# P11 — Final release posture

## DC-P11-001 — Readiness transition PR

**Where**

* `products/data-cloud/lifecycle/readiness-evidence.yaml`

**Tasks**

Only after all blockers pass, open a dedicated PR that changes:

```yaml
status: blocked
```

to:

```yaml
status: ready
```

or a more precise status:

```yaml
status: release-candidate
```

**Required evidence in PR**

* Current-commit evidence.
* Release workflow run.
* Active-module evidence.
* Boundary evidence.
* AI governance evidence.
* Product release readiness evidence.
* Smoke E2E evidence.
* Backup drill evidence.
* SBOM/security evidence.

**Acceptance criteria**

* No readiness transition mixed with unrelated code.
* PR body links evidence artifacts.
* Release workflow passes.

---

# Priority order

Implement in this exact order:

1. Regenerate evidence at current commit.
2. Make `check-data-cloud-platform-provider-readiness` pass without weakening it.
3. Normalize all docs to `EventOperatorCapability`.
4. Expand boundary checks beyond `planes/*`.
5. Add Action Plane module inventory.
6. Add executed-task evidence, not only generated-task evidence.
7. Harden `AgentEventOperatorCapabilityAdapter` tests.
8. Complete PatternSpec typed model and registry validation.
9. Compile PatternSpec into executable runtime DAG.
10. Implement lifecycle persistence and shadow/recommended pattern flow.
11. Complete EventCloud persistence bridge.
12. Decompose `GovernedAgentDispatcher`.
13. Add direct-agent-bypass scanner.
14. Produce a dedicated readiness transition PR.

This backlog keeps the product coherent, preserves the current architectural direction, and moves Data-Cloud + co-located AEP from “well-structured but blocked” toward a production-ready platform suite.
