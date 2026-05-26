Below is the full implementation todo list from the `600bebfa0832716d6589d5bcae223191138563cc` audit. I separated **YAPPC** into its own independent section as requested.

The biggest release blocker is still evidence integrity: the committed Data-Cloud release bundle at this commit targets `936924cf...`, not `600bebfa...`, so release evidence cannot prove this commit.  Data-Cloud also still classifies `delivery:api-contract-tests` and `integration-tests` as advisory/compile-gated instead of release-blocking. 

# 0. P0 Release Evidence and Gate Integrity

## DC-REL-001 — Regenerate target-commit release evidence

**Severity:** P0
**Where:**

```text
.kernel/evidence/data-cloud-release-bundle.json
.kernel/evidence/product-release-readiness.json
.kernel/evidence/data-cloud-active-modules.json
.kernel/evidence/action-plane-boundaries.json
.kernel/evidence/action-plane-module-inventory.json
.kernel/evidence/production-readiness-task-map.json
.kernel/evidence/agent-capability-duplicates.json
.kernel/evidence/agent-runtime-test-excludes.json
.kernel/evidence/agent-usage-audit.json
.kernel/evidence/data-cloud-operations-readiness.json
.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json
.github/workflows/data-cloud-ci.yml
scripts/check-evidence-current-commit.mjs
scripts/check-evidence-run-metadata.mjs
scripts/generate-data-cloud-release-bundle.mjs
```

**What to do:**

Regenerate all evidence at `600bebfa0832716d6589d5bcae223191138563cc`. Every evidence payload must bind to the audited commit through:

```text
commit
sourceCommitSha
targetCommitSha
```

The release bundle currently targets `936924cf...`, so it is stale for `600bebfa...`. 

**Tests / gates:**

```bash
pnpm check:evidence-current-commit
pnpm check:evidence-run-metadata
pnpm generate:data-cloud-release-bundle
pnpm check:data-cloud-release-gate
pnpm check:product-release-readiness
```

**Acceptance criteria:**

All evidence files either:

1. have commit metadata equal to `600bebfa0832716d6589d5bcae223191138563cc`, or
2. are explicitly excluded from release evidence and not referenced by release gates.

---

## DC-REL-002 — Make evidence-current-commit validate every nested bundle item

**Severity:** P0
**Where:**

```text
scripts/check-evidence-current-commit.mjs
scripts/generate-data-cloud-release-bundle.mjs
scripts/__tests__/check-evidence-current-commit.test.mjs
scripts/__tests__/generate-data-cloud-release-bundle.test.mjs
```

**What to do:**

Do not validate only the top-level bundle. Recursively validate nested payloads under:

```text
items.activeModuleEvidence.payload.evidenceRun.commit
items.actionPlaneBoundaryEvidence.payload.evidenceRun.commit
items.actionPlaneInventoryEvidence.payload.evidenceRun.commit
items.productionReadinessTaskMapEvidence.payload.evidenceRun.commit
items.agentCapabilityDuplicateEvidence.payload.evidenceRun.commit
items.agentRuntimeTestExcludeEvidence.payload.evidenceRun.commit
items.agentUsageAuditEvidence.payload.evidenceRun.commit/sourceCommitSha/targetCommitSha
items.productReleaseReadiness.payload.evidenceRun.commit/sourceCommitSha/targetCommitSha
items.aiGovernanceProof.payload.evidenceRun.commit
```

**Tests / gates:**

Add a fixture where one nested evidence item has a stale commit while the top-level bundle is current. The check must fail.

**Acceptance criteria:**

A stale nested evidence item cannot pass release.

---

## DC-REL-003 — Stop committing generated release evidence unless commit-bound

**Severity:** P1
**Where:**

```text
.kernel/evidence/**
release-evidence/**
.gitignore
.github/workflows/data-cloud-ci.yml
scripts/generate-data-cloud-release-bundle.mjs
```

**What to do:**

Choose one canonical model:

```text
Option A: committed evidence is allowed only when generated for the current commit.
Option B: release evidence is CI artifact only and is not committed.
```

Given repeated stale evidence conflicts, Option B is safer.

**Tests / gates:**

Add a cleanup/check script:

```text
scripts/check-committed-evidence-freshness.mjs
```

**Acceptance criteria:**

Merge is blocked if committed evidence references a different commit.

---

# 1. Data-Cloud Core Feature Completeness

## DC-E2E-001 — Promote Data-Cloud API contract tests to release-blocking

**Severity:** P1
**Where:**

```text
products/data-cloud/delivery/api-contract-tests
scripts/list-data-cloud-active-modules.mjs
scripts/generate-data-cloud-active-modules-evidence.mjs
scripts/__tests__/list-data-cloud-active-modules.test.mjs
scripts/__tests__/generate-data-cloud-active-modules-evidence.test.mjs
.github/workflows/data-cloud-ci.yml
package.json
```

**What to do:**

`delivery:api-contract-tests` is currently advisory with no `releaseCheckTask`. Make it release-blocking or add an equivalent release-blocking API contract task. 

**Tests / gates:**

```bash
./gradlew :products:data-cloud:delivery:api-contract-tests:check
pnpm check:data-cloud-release-gate
```

**Acceptance criteria:**

A runtime route/contract mismatch fails the release gate.

---

## DC-E2E-002 — Promote Data-Cloud integration tests to release-blocking

**Severity:** P1
**Where:**

```text
products/data-cloud/integration-tests
scripts/list-data-cloud-active-modules.mjs
scripts/generate-data-cloud-active-modules-evidence.mjs
.github/workflows/data-cloud-ci.yml
package.json
```

**What to do:**

`products:data-cloud:integration-tests` is currently advisory with `releaseCheckTask: null`. Make the integration suite release-blocking for Data-Cloud release. 

**Tests / gates:**

```bash
./gradlew :products:data-cloud:integration-tests:check
pnpm check:data-cloud-runbook-smoke
pnpm check:data-cloud-release-gate
```

**Acceptance criteria:**

Core Data-Cloud release cannot pass without integration tests.

---

## DC-DATA-001 — Add complete entity lifecycle E2E test

**Severity:** P1
**Where:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/
products/data-cloud/integration-tests/src/test/java/
```

**What to do:**

Add a real-path E2E covering:

```text
POST /entities/{collection}
→ schema validation
→ tenant resolution
→ entity persistence
→ event append
→ audit/outbox
→ GET entity
→ query with sort/filter/page
→ DELETE entity
→ delete event append
```

The handler already validates tenant/collection/payload, supports schema validation, transaction/outbox/audit hooks, idempotency, event append, query filtering/sorting, and delete CDC append.   

**Tests / gates:**

```text
EntityLifecycleE2ETest.java
EntityEventAuditIdempotencyGoldenTest.java
TenantIsolationTest.java
```

**Acceptance criteria:**

Journey A is proven by a release-blocking integration test.

---

## DC-DATA-002 — Add batch-delete confirmation token regression test

**Severity:** P1
**Where:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/
```

**What to do:**

The token flow is now fixed in code: both token creation and validation include tenant, collection, count, and timestamp.  Add tests so it cannot regress.

**Tests to add:**

```text
BatchDeleteConfirmationTokenTest
- dryRunReturnsValidToken
- executeAcceptsDryRunToken
- executeRejectsWrongTenant
- executeRejectsWrongCollection
- executeRejectsWrongCount
- executeRejectsExpiredToken
- executeRejectsTamperedToken
```

**Acceptance criteria:**

Dry-run → execute works, but tampered scope fails.

---

## DC-DATA-003 — Make batch save/delete transaction semantics explicit and production-safe

**Severity:** P1
**Where:**

```text
EntityCrudHandler.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/
products/data-cloud/docs/operations/RUNBOOK.md
products/data-cloud/contracts/openapi/data-cloud.yaml
```

**What to do:**

Batch save/delete currently documents best-effort per-item semantics. That may be acceptable, but production behavior must be explicitly represented in the API contract and tests.

Add clear contract fields:

```text
atomic: false
partialSuccessAllowed: true
errors[]
successfulIds[]
failedIds[]
operationId
correlationId
```

**Tests:**

```text
BatchEntityPartialFailureContractTest
BatchEntityNoSilentLossTest
```

**Acceptance criteria:**

Partial success is explicit and auditable, not accidental.

---

## DC-DATA-004 — Add real transaction failure rollback test

**Severity:** P1
**Where:**

```text
EntityCrudHandler.java
products/data-cloud/spi/TransactionManager
products/data-cloud/spi/EntityWriteOutboxProcessor
products/data-cloud/delivery/launcher/src/test/java/
```

**What to do:**

Test failures at each step:

```text
entity save succeeds, event append fails
entity save succeeds, audit payload creation fails
entity save succeeds, outbox add fails
transaction manager throws
```

**Acceptance criteria:**

No partial/corrupt state; response is correct; failure is logged/observed/audited where required.

---

## DC-DATA-005 — Add cross-tenant negative E2E tests for entity/query/event paths

**Severity:** P1
**Where:**

```text
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/TenantIsolationTest.java
products/data-cloud/integration-tests/src/test/java/
DataCloudSecurityFilter.java
EntityCrudHandler.java
```

**What to do:**

Security filter source rejects tenant mismatch, missing tenant, insufficient access, and missing metadata in production-like profiles.   Add full E2E tests:

```text
tenant A creates entity
tenant B attempts read
tenant B attempts query
tenant B attempts delete
tenant B attempts event replay
tenant B gets denied
no data appears in response/log/metric/event
denial is audited
```

**Acceptance criteria:**

Tenant B cannot infer tenant A’s data existence.

---

## DC-DATA-006 — Make update/archive first-class if exposed

**Severity:** P2
**Where:**

```text
EntityCrudHandler.java
DataCloudRouterBuilder.java
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/delivery/sdk
products/data-cloud/delivery/ui
```

**What to do:**

The prompt requires create/read/update/delete/archive. If update/archive are exposed through routes, contracts, SDK, or UI, verify full implementation. If not exposed, mark them explicitly as not supported or feature-flagged.

**Tests:**

```text
EntityUpdateContractTest
EntityArchiveContractTest
```

**Acceptance criteria:**

No route/SDK/UI advertises update/archive without backend implementation.

---

# 2. Data-Cloud Security, Governance, Audit, and Runtime Truth

## DC-SEC-001 — Add route metadata fail-closed E2E test

**Severity:** P1
**Where:**

```text
DataCloudSecurityFilter.java
RouteSecurityRegistry.java
products/data-cloud/delivery/launcher/src/test/java/
```

**What to do:**

Source rejects missing route metadata in production-like profiles.  Add a test that registers a route without metadata and verifies production/staging/sovereign requests fail closed.

**Acceptance criteria:**

Unknown production route never reaches handler.

---

## DC-SEC-002 — Add blocking audit failure injection tests for CRITICAL routes

**Severity:** P1
**Where:**

```text
DataCloudSecurityFilter.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/audit/
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/
```

**What to do:**

The filter uses blocking audit for critical production routes and should fail if audit persistence fails.  Add tests for:

```text
audit sink unavailable
audit record throws
audit timeout
audit service missing
```

**Acceptance criteria:**

Critical route does not complete if required audit cannot persist.

---

## DC-SEC-003 — Add audit redaction/privacy tests

**Severity:** P1
**Where:**

```text
DataCloudSecurityFilter.java
EventLogAuditService.java
EntityCrudHandler.java
products/data-cloud/planes/governance/core
products/data-cloud/delivery/launcher/src/test/java/
```

**What to do:**

Verify sensitive data is not leaked through:

```text
audit details
logs
error responses
metrics
trace attributes
CDC events
```

**Acceptance criteria:**

PII/secrets are redacted or omitted across audit/log/trace/error surfaces.

---

## DC-OPS-001 — Regenerate operations readiness proof at target commit

**Severity:** P1
**Where:**

```text
.kernel/evidence/data-cloud-operations-readiness.json
products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/
products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/
scripts/check-data-cloud-operations-readiness.mjs
```

**What to do:**

The operations readiness proof inside the bundle is stale for `936924...`.  Regenerate at target commit and add stricter validation for:

```text
runtime truth
health checks
dependency degraded state
backup/restore
SLO budget
cost budget
alerting
k8s/Helm render inputs
```

**Acceptance criteria:**

Operations readiness proof is target-commit-bound and behavior-backed.

---

## DC-OPS-002 — Add dependency failure/degraded E2E tests

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/operations/config
products/data-cloud/integration-tests
scripts/check-runtime-dependency-failure-injection.mjs
scripts/check-runtime-failure-injection.mjs
```

**What to do:**

Test failure of:

```text
entity store
event store
audit sink
policy engine
semantic index
agent runtime
```

**Acceptance criteria:**

Runtime truth reports degraded/unavailable correctly; API response is correct; no silent data loss.

---

# 3. Data-Cloud Event Plane and EventCloud/AEP Bridge

## DC-EVENT-001 — Add append/read/tail/replay/checkpoint E2E

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/event/core
products/data-cloud/planes/event/store
products/data-cloud/planes/action/event-bridge
products/data-cloud/integration-tests
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/aep.yaml
```

**What to do:**

Add complete Event Plane tests:

```text
append event
read event
tail stream
replay stream
checkpoint offset
retry after failure
DLQ on poison event
tenant isolation
```

**Acceptance criteria:**

Journey B can start from a real Data-Cloud event and reach AEP bridge behavior.

---

## DC-EVENT-002 — Add event ordering/idempotency tests

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/event/store
products/data-cloud/planes/action/event-bridge
products/data-cloud/integration-tests
```

**What to do:**

Verify:

```text
same idempotency key does not duplicate event
ordering is deterministic per tenant/stream
checkpoint resumes from correct offset
late events follow configured policy
```

**Acceptance criteria:**

No duplicate/skip/reorder under retry.

---

## DC-EVENT-003 — Separate Data-Cloud EventLog from AEP EventCloud semantics in tests

**Severity:** P2
**Where:**

```text
products/data-cloud/planes/event/**
products/data-cloud/planes/action/event-bridge/**
scripts/check-action-plane-boundaries.mjs
```

**What to do:**

Boundary evidence claims non-action Data-Cloud planes must not expose AEP-owned semantics such as EventCloud, PatternSpec, EventOperator, EventOperatorCapability, CEP, pattern promotion, or lifecycle semantics.  Add behavioral tests proving Data-Cloud EventLog remains storage-plane primitive while AEP owns pattern semantics.

**Acceptance criteria:**

No PatternSpec/CEP behavior is reachable from Data-Cloud Event Plane APIs.

---

# 4. AEP / Action Plane

## AEP-001 — Add PatternSpec full lifecycle transition E2E

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecValidator.java
products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/
products/data-cloud/planes/action/registry
products/data-cloud/planes/action/orchestrator
products/data-cloud/planes/action/engine
```

**What to do:**

Lifecycle states are now validated, including `candidate`, `approved`, `degraded`, `retired`, and `rollback`.  Add full E2E transition coverage:

```text
draft → candidate
candidate → shadow
shadow → recommended
recommended → approved
approved → active
active → predictive
active → degraded
degraded → rollback
active → retired
```

**Acceptance criteria:**

Illegal transition fails; legal transition emits audit/evidence.

---

## AEP-002 — Add production PatternSpec compile validation tests

**Severity:** P1
**Where:**

```text
PatternSpecCompiler.java
PatternSpecValidator.java
products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/
```

**What to do:**

Compiler now passes `commitSha`, `environment`, and capability registry into validator.  Add tests proving compile fails in production when missing:

```text
commit SHA binding
evidencePolicy
evidenceStore
approved evidence store scheme
capabilityRef
toolPolicy for side-effecting capability
outputSchema
replayPolicy
```

**Acceptance criteria:**

Production compile cannot bypass production validator.

---

## AEP-003 — Add PatternSpec execution/replay E2E

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/action/engine
products/data-cloud/planes/action/central-runtime
products/data-cloud/planes/action/orchestrator
products/data-cloud/planes/action/event-bridge
products/data-cloud/integration-tests
```

**What to do:**

Add real runtime tests:

```text
compile PatternSpec
attach event stream
match pattern
execute runtime DAG
emit derived event
record trace/metric/audit
replay same event stream
verify deterministic decision path
```

**Acceptance criteria:**

PatternSpec is not only parsed/compiled; it actually runs end to end.

---

## AEP-004 — Add side-effect governance E2E

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/action/security
products/data-cloud/planes/action/compliance
products/data-cloud/planes/action/orchestrator
products/data-cloud/planes/action/agent-runtime
```

**What to do:**

Test side-effecting action operators:

```text
requires tool policy
requires approval policy
requires idempotency
requires audit
requires rollback/compensation metadata
denied path is auditable
approved path executes once
```

**Acceptance criteria:**

No side-effecting capability can execute without governance proof.

---

## AEP-005 — Remove or isolate temporary compatibility modules

**Severity:** P2
**Where:**

```text
products/data-cloud/planes/action
products/data-cloud/planes/action/server
products/data-cloud/planes/action/kernel-bridge
products/data-cloud/extensions/kernel-bridge
products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md
```

**What to do:**

The action root, server, and kernel-bridge are marked temporary/migration-only in the inventory.  Create explicit migration tasks:

```text
action root → aggregator only or removed
action/server → delivery/api or AEP-owned server
action/kernel-bridge → extensions/kernel-bridge
```

**Acceptance criteria:**

No temporary compatibility module remains release-blocking indefinitely without an expiry task.

---

# 5. Agents

## AGENT-001 — Tighten agent usage audit exception registry

**Severity:** P1
**Where:**

```text
scripts/audit-agent-usage.mjs
products/data-cloud/planes/action/agent-runtime/docs/AGENT_USAGE_EXCEPTIONS.md
.kernel/evidence/agent-usage-audit.json
```

**What to do:**

The current audit allows broad production-looking patterns such as:

```text
AgentCapabilityExecutionFactory
AgentEventOperatorCapabilityAdapter
GovernedAgentDispatcher
AgentDispatchPipeline
AgentActionOperator
```

These may be valid, but broad exceptions can hide bypasses.  Convert exceptions into exact file/symbol allowlist entries with justification.

**Acceptance criteria:**

A new direct `TypedAgent` production invocation fails the audit unless explicitly reviewed.

---

## AGENT-002 — Add governed dispatch E2E test

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/action/agent-runtime
products/data-cloud/planes/action/orchestrator
products/data-cloud/planes/action/security
products/data-cloud/planes/action/compliance
products/data-cloud/integration-tests
```

**What to do:**

Test:

```text
pattern match
→ capabilityRef lookup
→ EventOperatorCapability invocation
→ GovernedAgentDispatcher policy check
→ side-effect allowed/denied
→ audit/evidence persisted
```

The adapter already requires policy maps, durable memory unless explicitly allowed, tenant match, trace ID, correlation ID, side-effect controls, and evidence fields.  

**Acceptance criteria:**

Agent action cannot bypass governed capability runtime.

---

## AGENT-003 — Add agent denial/failure tests

**Severity:** P1
**Where:**

```text
AgentEventOperatorCapabilityAdapter.java
products/data-cloud/planes/action/agent-runtime/src/test/java/
```

**What to do:**

Add tests for:

```text
tenant mismatch
missing traceId
missing correlationId
NoOpMemoryStore not explicitly allowed
SIDE_EFFECTING without allowedTools
SIDE_EFFECTING without approvalPolicy
SIDE_EFFECTING without audit policy
SIDE_EFFECTING without idempotencyRequired
agent timeout
agent failed
agent pending approval
```

**Acceptance criteria:**

Every denial path increments metrics and returns auditable evidence/error.

---

## AGENT-004 — Add replay-safety tests for agent actions

**Severity:** P1
**Where:**

```text
products/data-cloud/planes/action/agent-runtime
products/data-cloud/planes/action/orchestrator
products/data-cloud/planes/action/engine
```

**What to do:**

Test replay behavior for:

```text
pure agent capability
retrieval capability
side-effecting capability
non-replayable capability
idempotent replay
```

**Acceptance criteria:**

Replay either reproduces the same decision path or records explicit non-replayable evidence.

---

# 6. Audio-Video

## AV-001 — Add Vision and Multimodal to CI build/test matrix

**Severity:** P1
**Where:**

```text
.github/workflows/audio-video-ci.yml
products/audio-video/modules/vision/vision-service
products/audio-video/modules/intelligence/multimodal-service
```

**What to do:**

The generated registry includes Vision and Multimodal modules, but Audio-Video CI builds/tests STT and TTS only.   Add jobs:

```text
build-vision
test-vision
build-multimodal
test-multimodal
docker vision-service
docker multimodal-service
deploy vision-service
deploy multimodal-service
```

**Acceptance criteria:**

Vision and Multimodal cannot regress silently.

---

## AV-002 — Fix Audio-Video integration test Gradle path

**Severity:** P1
**Where:**

```text
.github/workflows/audio-video-ci.yml
products/audio-video/modules/integration-tests
```

**What to do:**

The workflow runs:

```text
:products:audio-video:speech-to-text:libs:stt-core-java:integrationTest
```

But generated registry modules use:

```text
:products:audio-video:modules:integration-tests
:products:audio-video:modules:speech:stt-service
:products:audio-video:modules:speech:tts-service
```

Fix the integration test path. 

**Acceptance criteria:**

The CI integration test task maps to an actual included Gradle module.

---

## AV-003 — Remove `continue-on-error` from Audio-Video integration tests

**Severity:** P1
**Where:**

```text
.github/workflows/audio-video-ci.yml
```

**What to do:**

Audio-Video integration tests currently use `continue-on-error: true`.  Remove it.

**Acceptance criteria:**

Audio-Video integration failure blocks CI/release.

---

## AV-004 — Add STT functional completeness tests

**Severity:** P1
**Where:**

```text
products/audio-video/modules/speech/stt-service
products/audio-video/modules/integration-tests
products/audio-video/libs/common
```

**What to test:**

```text
audio input validation
supported format validation
unsupported format rejection
language selection
transcription execution path
confidence metadata
tenant/security enforcement
persistence metadata
event/audit emission
timeout/cancellation
provider failure/degraded behavior
```

**Acceptance criteria:**

STT is proven as a production service, not just compiled/tested.

---

## AV-005 — Add TTS functional completeness tests

**Severity:** P1
**Where:**

```text
products/audio-video/modules/speech/tts-service
products/audio-video/modules/integration-tests
products/audio-video/libs/common
```

**What to test:**

```text
text validation
voice/model selection
synthesis execution
output format
cache behavior
streaming/batch behavior if exposed
tenant/security enforcement
persistence metadata
event/audit emission
timeout/cancellation
provider failure/degraded behavior
```

**Acceptance criteria:**

TTS is production-proven end to end.

---

## AV-006 — Add Vision service functional completeness tests

**Severity:** P1
**Where:**

```text
products/audio-video/modules/vision/vision-service
products/audio-video/modules/integration-tests
products/audio-video/libs/common
```

**What to test:**

```text
image/video validation
supported formats
object/text/scene extraction if claimed
confidence metadata
provider abstraction
tenant/security enforcement
persistence metadata
event/audit emission
failure/degraded behavior
```

**Acceptance criteria:**

Vision service is not a registered-but-unproven module.

---

## AV-007 — Add Multimodal service functional completeness tests

**Severity:** P1
**Where:**

```text
products/audio-video/modules/intelligence/multimodal-service
products/audio-video/modules/integration-tests
products/audio-video/libs/common
```

**What to test:**

```text
audio + video + text composition
routing to STT/TTS/Vision
result aggregation
provider fallback
tenant/security enforcement
persistence/event emission
Data-Cloud integration if claimed
AEP consumption if claimed
agent explain/review/action if claimed
```

**Acceptance criteria:**

Multimodal flow is production-proven.

---

## AV-008 — Add Audio-Video → Data-Cloud → AEP integration journey

**Severity:** P1
**Where:**

```text
products/audio-video/modules/integration-tests
products/data-cloud/integration-tests
products/data-cloud/planes/action/event-bridge
products/data-cloud/planes/event/store
```

**What to do:**

Add cross-product test:

```text
audio/video input received
→ service processes
→ result persisted or emitted
→ Data-Cloud stores metadata/evidence
→ AEP consumes event
→ agent review/explanation path is available if configured
```

**Acceptance criteria:**

Journey D is proven by release-blocking tests.

---

# 7. Shared Platform and Kernel Work

## KERNEL-001 — Ensure platform shared libs are not product-specific

**Severity:** P2
**Where:**

```text
platform/java/core
platform/java/domain
platform/java/runtime
platform/java/observability
platform/java/security
platform/java/audit
platform/java/messaging
platform/java/ai-integration
platform/java/data-governance
platform/java/tool-runtime
platform/contracts
scripts/check-platform-product-boundaries.mjs
scripts/check-domain-boundaries.mjs
```

**What to do:**

Audit and enforce that product-specific Data-Cloud/AEP/YAPPC/Audio-Video logic does not leak into platform libs.

**Acceptance criteria:**

Boundary check fails on product-specific imports/strings/config in generic platform modules.

---

## KERNEL-002 — Add shared observability conformance tests for product journeys

**Severity:** P2
**Where:**

```text
platform/java/observability
scripts/check-observability-conformance.mjs
products/data-cloud/**
products/audio-video/**
products/yappc/**
```

**What to do:**

For every core journey, require:

```text
correlationId
traceId
tenantId where safe
span names
metrics
failure/degraded metrics
audit correlation
```

**Acceptance criteria:**

No core route/action/job lacks trace/metric/audit correlation.

---

## KERNEL-003 — Add shared test fixture authenticity gate

**Severity:** P2
**Where:**

```text
platform/java/testing
scripts/check-test-authenticity.mjs
products/**/src/test/**
```

**What to do:**

Detect tests that only assert mocks/fixtures and never execute production wiring.

**Acceptance criteria:**

A feature cannot be marked complete if only mock-only tests exist.

---

## KERNEL-004 — Add cross-product release profile matrix

**Severity:** P2
**Where:**

```text
config/product-lifecycle-profiles.json
config/toolchain-adapter-registry.json
scripts/check-product-ci-matrices.mjs
scripts/check-release-profile-local-targets.mjs
```

**What to do:**

Ensure every product/provider in the registry has:

```text
build
test
validate
package
integration
release evidence
rollback/recovery if deployable
```

**Acceptance criteria:**

Data-Cloud, Audio-Video, YAPPC, and Kernel bridges have consistent release profile coverage.

---



# 9. Cross-Product End-to-End Journeys

## XPROD-001 — Add Data-Cloud → AEP → Agent action E2E

**Severity:** P1
**Where:**

```text
products/data-cloud/integration-tests
products/data-cloud/planes/event/store
products/data-cloud/planes/action/event-bridge
products/data-cloud/planes/action/engine
products/data-cloud/planes/action/agent-runtime
```

**What to do:**

Test:

```text
Data-Cloud event appended
→ AEP bridge tails event
→ PatternSpec matches
→ capabilityRef resolves
→ agent capability executes or is denied
→ audit/evidence/trace persists
```

**Acceptance criteria:**

Journey B/C is release-gated.

---

## XPROD-002 — Add Audio-Video → Data-Cloud → AEP → Agent E2E

**Severity:** P1
**Where:**

```text
products/audio-video/modules/integration-tests
products/data-cloud/integration-tests
products/data-cloud/planes/action/event-bridge
products/data-cloud/planes/action/agent-runtime
```

**What to do:**

Test:

```text
audio/video input
→ STT/Vision/Multimodal result
→ Data-Cloud metadata/evidence
→ event append
→ AEP pattern match
→ agent review/explain/action
```

**Acceptance criteria:**

Audio-Video is proven as part of the product suite, not isolated modules.

---

## XPROD-003 — Add YAPPC → Kernel → Data-Cloud → Agent E2E

**Severity:** P1
**Where:**

```text
products/yappc/integration
products/yappc/kernel-bridge
platform-kernel/**
products/data-cloud/extensions/kernel-bridge
products/data-cloud/planes/action/agent-runtime
```

**What to do:**

Test:

```text
YAPPC receives product intent
→ Kernel creates lifecycle plan
→ YAPPC generates artifact
→ Data-Cloud persists evidence
→ agent reviews artifact
→ validation/roundtrip passes
```

**Acceptance criteria:**

YAPPC, Kernel, Data-Cloud, and Agents work together end to end.

---

# 10. Documentation and Tracking Tasks

## DOC-001 — Create production-readiness task map for this audit

**Severity:** P2
**Where:**

```text
products/data-cloud/docs/audits/
products/yappc/docs/audits/
products/audio-video/docs/audits/
.kernel/evidence/
```

**What to do:**

Create one task map per product:

```text
DATA_CLOUD_FEATURE_COMPLETENESS_TASK_MAP.md
AEP_ACTION_PLANE_FEATURE_COMPLETENESS_TASK_MAP.md
AUDIO_VIDEO_FEATURE_COMPLETENESS_TASK_MAP.md
YAPPC_FEATURE_COMPLETENESS_TASK_MAP.md
```

**Acceptance criteria:**

Each task has owner, path, status, test, acceptance criteria, and release gate.

---

## DOC-002 — Add feature completeness matrix per product

**Severity:** P2
**Where:**

```text
products/data-cloud/docs/
products/audio-video/docs/
products/yappc/docs/
```

**What to do:**

For each product, document:

```text
feature
entrypoint
implementation path
contract
tests
status
gaps
release gate
```

**Acceptance criteria:**

No feature is tracked only in chat/audit output.

---

# Recommended Execution Order

1. `DC-REL-001`, `DC-REL-002`, `DC-REL-003`
2. `DC-E2E-001`, `DC-E2E-002`
3. `DC-DATA-001`, `DC-DATA-002`, `DC-DATA-004`, `DC-DATA-005`
4. `AEP-001`, `AEP-002`, `AEP-003`, `AEP-004`
5. `AGENT-001`, `AGENT-002`, `AGENT-003`, `AGENT-004`
6. `AV-001`, `AV-002`, `AV-003`, then AV functional tests
8. Cross-product E2E journeys
9. Documentation/task-map cleanup
