# Agentic Trustworthy Implementation Package

Date: 2026-03-22  
Status: Proposed implementation package targeting the current monorepo state  
Primary scope: `platform/java/agent-core`, `platform/java/agent-runtime`, `platform/java/governance`, `platform/java/workflow`, `platform/agent-catalog`, `docs/agent-spec.md`

## 1. Purpose

This document is the single detailed implementation package for making agentic processing in the current Ghatana codebase trustworthy, reliable, accountable, secure, privacy-honoring, ethically governable, and auditable at scale.

It is intentionally grounded in:

- The current repository implementation and its actual gaps
- The current agent specification and runtime abstractions
- The attached research notes:
  - `/Users/samujjwal/Downloads/agentic_ai_trustworthy_framework.md`
  - `/Users/samujjwal/Downloads/agentic-governance-deep-research.md`
  - `/Users/samujjwal/Downloads/agentic-memory-deep-dive.md`

This package is not a generic future-state essay. It is a concrete implementation plan that targets the present codebase and identifies what must change, where it should change, how it should be validated, and in what order it should be delivered.

## 2. Executive Summary

The codebase already contains several strong foundations:

- A typed agent contract with lifecycle semantics:
  - `platform/java/agent-core/.../TypedAgent.java`
  - `platform/java/agent-core/.../AbstractTypedAgent.java`
- A rich declarative specification model:
  - `docs/agent-spec.md`
  - `platform/java/agent-core/.../framework/spec/AgentSpec.java`
  - `platform/java/agent-core/.../framework/spec/AgentSpecLoader.java`
- Multiple runtime primitives for resilience, workflow durability, memory, and security:
  - `platform/java/workflow/.../DurableWorkflowRuntime.java`
  - `platform/java/agent-core/.../framework/checkpoint/*`
  - `platform/java/governance/.../security/*`
  - `platform/java/agent-runtime/.../memory/*`

However, the current system is better at describing governed agent behavior than enforcing it. The most important current-state problem is the gap between declaration and runtime guarantee.

The core implementation direction in this document is:

1. Turn the spec into an enforceable control plane, not just metadata.
2. Make every privileged agent action pass through identity, policy, approval, and evidence layers.
3. Treat memory as governed infrastructure with ownership, provenance, versioning, retention, and audit.
4. Add a tamper-evident evidence plane for action traces, approvals, and policy decisions.
5. Move from confidence-only autonomy decisions to risk-aware action governance.
6. Build staged promotion, invariant monitoring, replay, and assurance packages for production release.

## 3. Reviewed Current-State Surface

### 3.1 Core Docs and ADRs

- `docs/agent-spec.md`
- `docs/adr/ADR-001-typed-agent-framework.md`
- `docs/adr/ADR-004-activej-framework.md`
- `docs/AGENT_SPEC_MIGRATION_PLAN.md`

### 3.2 Core Platform Modules

- `platform/java/agent-core`
- `platform/java/agent-runtime`
- `platform/java/governance`
- `platform/java/workflow`
- `platform/agent-catalog`

### 3.3 Key Current Files Reviewed

- `platform/java/agent-core/src/main/java/com/ghatana/agent/TypedAgent.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AbstractTypedAgent.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentResult.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentResultStatus.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentConfig.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentDescriptor.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpec.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpecLoader.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyLevel.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyRouter.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/GovernanceEngine.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/MemoryStore.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/EventLogMemoryStore.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/JdbcMemoryStore.java`
- `platform/java/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/PersistentMemoryPlane.java`
- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyEngine.java`
- `platform/java/governance/src/main/java/com/ghatana/platform/governance/security/TenantIsolationEnforcer.java`
- `platform/java/governance/src/main/java/com/ghatana/platform/governance/security/SsrfGuard.java`
- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/DurableWorkflowRuntime.java`
- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/AuditWorkflowListener.java`
- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/MetricsWorkflowListener.java`
- `platform/agent-catalog/catalog-schema.yaml`
- `platform/agent-catalog/base-agent-template.yaml`

## 4. Current-State Findings

### 4.1 What is already strong

- The type-safe agent contract is directionally correct and should remain the foundation.
- The spec model is broad enough to support advanced governance if the runtime catches up.
- Tenant isolation and SSRF protection are explicitly modeled and tested as reusable controls.
- Durable workflow orchestration already has lifecycle events, state storage, and retry semantics.
- Memory is already treated as a first-class subsystem in both legacy and newer runtime layers.

### 4.2 What is currently weak or unsafe

#### Finding A: tenant inheritance breaks in sub-workflows

`DurableWorkflowRuntime.executeSubWorkflow()` starts sub-workflows with the hard-coded tenant `"sub-tenant"` rather than inheriting the parent tenant.

Impact:

- Breaks tenant isolation in delegated execution
- Invalidates trust claims for multi-tenant workflow orchestration
- Produces unreliable audit records and incorrect scoping

Current file:

- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/DurableWorkflowRuntime.java`

#### Finding B: the spec is richer than the enforcement path

`AgentSpec` models all of the following:

- memory bindings
- memory retention policy
- governance approvals
- data handling
- risk profile
- evaluation release gates
- observability audit mode
- security authn/authz/network policy

But `AgentSpecLoader.extractDefinition()` currently extracts only a narrow runtime subset:

- identity basics
- capabilities
- first input/output contract
- tools
- timeout and retry
- autonomy and criticality as labels
- governance policy refs as labels
- max cost per call

Impact:

- Governance is mostly descriptive, not executable
- Approval semantics are not carried into runtime control flow
- Security and observability requirements are not guaranteed from the spec
- Memory governance declarations do not become enforced memory behavior

Current files:

- `docs/agent-spec.md`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpec.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpecLoader.java`

#### Finding C: policy enforcement is underpowered

The governance package exposes `PolicyEngine`, but it is currently only a minimal interface and there is no substantial runtime policy execution pipeline tied to:

- tool execution
- workflow steps
- memory operations
- action approvals
- delegation
- evidence recording

Impact:

- Policy refs cannot act as real hard controls
- Governance decisions cannot be relied upon as an execution boundary
- Compliance evidence remains incomplete

Current files:

- `platform/java/governance/src/main/java/com/ghatana/governance/PolicyEngine.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/GovernanceEngine.java`

#### Finding D: memory governance is not consistently real

The default in-memory memory store explicitly simulates governance rather than applying it. The JDBC implementation supports retention deletion, but not a broader governed memory lifecycle. `GovernanceEngine` defaults to `GovernancePolicy.noOp()`, and its audit sink can also be no-op.

Impact:

- Governance passes can succeed without actual enforcement
- Redaction, deletion, and retention can be inconsistently applied
- Memory cannot yet serve as a strong privacy/compliance substrate

Current files:

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/EventLogMemoryStore.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/JdbcMemoryStore.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/GovernanceEngine.java`

#### Finding E: autonomy semantics are inconsistent

Autonomy is currently inconsistent across:

- `docs/agent-spec.md`
- `platform/agent-catalog/catalog-schema.yaml`
- `platform/java/agent-core/.../AutonomyLevel.java`
- `platform/java/agent-core/.../AutonomyRouter.java`

Examples:

- Spec: `advisory`, `assisted`, `semi-autonomous`, `autonomous`
- Catalog: `autonomous`, `semi-autonomous`, `supervised`
- Runtime: `AUTONOMOUS`, `SUPERVISED`, `MANUAL`
- Router documentation refers to `PENDING_APPROVAL`, but `AgentResultStatus` does not define it

Impact:

- Approval logic cannot be consistently automated
- UIs and workflows can drift from runtime behavior
- Policy mapping becomes fragile

Current files:

- `docs/agent-spec.md`
- `platform/agent-catalog/catalog-schema.yaml`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyLevel.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyRouter.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentResultStatus.java`

#### Finding F: memory retrieval and archival are not yet assurance-grade

`PersistentMemoryPlane.searchSemantic()` is stubbed and returns an empty list. Memory archival to EventCloud is explicitly lenient and best-effort.

Impact:

- Retrieval usefulness is limited
- Memory-driven reasoning cannot yet support strong replay or justification
- Evidence durability is not strong enough for liability-sensitive use cases

Current files:

- `platform/java/agent-runtime/src/main/java/com/ghatana/agent/memory/persistence/PersistentMemoryPlane.java`

#### Finding G: workflow audit is observable but not tamper-evident

Workflow lifecycle audit today is structured logging, not a cryptographically verifiable evidence chain.

Impact:

- Good for operations
- Not yet strong enough for regulated audit, dispute resolution, or cross-system liability evidence

Current files:

- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/AuditWorkflowListener.java`

## 5. Implementation Goals

The implementation package should deliver the following outcomes:

1. A deployable agent cannot perform privileged work without executable policy checks.
2. Every privileged action can be reconstructed, justified, and cryptographically chained.
3. Tenant and identity boundaries hold across workflows, delegation, and memory.
4. Memory becomes governed, versioned, and provenance-aware.
5. Autonomy is staged and risk-aware, not confidence-only.
6. The spec becomes a build-time and runtime contract, not just documentation.
7. High-risk agent deployments produce an assurance package that is readable by engineering, security, compliance, and auditors.

## 6. Target Architecture

The target architecture is a seven-plane control model.

### 6.1 Plane 1: Specification Plane

Responsibility:

- Canonical description of agent identity, permissions, memory, evaluation, and risk

Target outputs:

- Canonical `Agent Datasheet`
- Spec-to-runtime compiled definition
- Build-time conformance checks

Current codebase anchor:

- `docs/agent-spec.md`
- `AgentSpec.java`
- `AgentSpecLoader.java`
- `AgentDefinitionValidator.java`

### 6.2 Plane 2: Identity and Delegation Plane

Responsibility:

- Who is acting
- Under whose authority
- For what scope
- For how long

Target outputs:

- JIT agent grants
- delegation graph
- scoped tool credentials
- tenant-preserving sub-workflows

Current codebase anchor:

- `TenantIsolationEnforcer`
- `TenantContext`
- workflow runtime

### 6.3 Plane 3: Action Governance Plane

Responsibility:

- Decide whether an action is allowed, denied, delayed, escalated, or approved

Target outputs:

- action classification
- policy decisions
- human approval requests
- reversibility and compensation rules

Current codebase anchor:

- `AutonomyRouter`
- `AutonomyLevel`
- `PolicyEngine`
- `GovernanceEngine`
- `DurableWorkflowRuntime`

### 6.4 Plane 4: Memory Governance Plane

Responsibility:

- Govern how memory is stored, updated, shared, retained, and deleted

Target outputs:

- governed memory namespaces
- versioned facts
- memory ownership
- provenance and retention enforcement

Current codebase anchor:

- `MemoryStore`
- `EventLogMemoryStore`
- `JdbcMemoryStore`
- `PersistentMemoryPlane`

### 6.5 Plane 5: Evidence Plane

Responsibility:

- Durable, tamper-evident record of what happened and why

Target outputs:

- cryptographically chained action log
- approval ledger
- policy decision ledger
- memory mutation ledger

Current codebase anchor:

- workflow audit listener
- kernel audit capabilities
- EventCloud archival path

### 6.6 Plane 6: Runtime Safety Plane

Responsibility:

- Prevent cascading failure, budget exhaustion, unsafe delegation, and unbounded autonomy

Target outputs:

- kill switches
- circuit breakers
- action budgets
- delegation depth caps
- invariant monitors

Current codebase anchor:

- `AbstractTypedAgent`
- workflow retries/timeouts
- agent config failure modes
- runtime resilience pieces

### 6.7 Plane 7: Assurance Plane

Responsibility:

- Prove sufficient due care for release, promotion, and audit

Target outputs:

- evaluation packs
- replay packs
- promotion gates
- safety/assurance case
- regulator/customer evidence package

Current codebase anchor:

- spec evaluation model
- workflow listeners
- tests and metrics

## 7. Target Data Model Additions

### 7.1 Add `AgentDatasheet`

Create a new immutable artifact:

- package target:
  - `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/AgentDatasheet.java`

Fields:

- `agentId`
- `version`
- `namespace`
- `owners`
- `riskTier`
- `autonomyTier`
- `criticality`
- `allowedActionClasses`
- `toolPermissions`
- `memoryBindings`
- `dataClassification`
- `retentionPolicy`
- `approvalRules`
- `evaluationPackRefs`
- `killSwitchProcedure`
- `rollbackStrategy`
- `auditMode`
- `deploymentContexts`
- `lastReviewedAt`
- `nextReviewAt`

Generation sources:

- `AgentSpec`
- runtime registration metadata
- policy registry
- deployment config

Purpose:

- compliance artifact
- operator inventory record
- source of truth for governance UI and approval routing

### 7.2 Add `ActionIntent`

Create a new typed action abstraction:

- package target:
  - `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/governance/ActionIntent.java`

Fields:

- `traceId`
- `agentId`
- `tenantId`
- `actionClass`
- `targetType`
- `targetId`
- `toolId`
- `argsHash`
- `reversibilityClass`
- `criticality`
- `requestedBy`
- `delegatedFrom`
- `datasheetVersion`

Action classes:

- `READ`
- `DRAFT`
- `WRITE_REVERSIBLE`
- `WRITE_IRREVERSIBLE`
- `CALL_EXTERNAL`
- `DELEGATE`
- `MEMORY_MUTATION`
- `POLICY_CHANGE`

### 7.3 Add `PolicyDecision`

Create:

- `ALLOW`
- `DENY`
- `ESCALATE`
- `ALLOW_WITH_APPROVAL`
- `ALLOW_WITH_COMPENSATION`
- `ALLOW_WITH_MONITORING`

Fields:

- `decision`
- `policyRefsApplied`
- `matchedRules`
- `reasons`
- `requiredApprovals`
- `obligations`
- `expiresAt`

### 7.4 Add `ApprovalRequest` and `ApprovalDecision`

Create:

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/ApprovalRequest.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/ApprovalDecision.java`

Fields:

- request identity
- trace identity
- action summary
- risk summary
- plain-language explanation
- approving roles
- deadline
- status
- signed decision metadata

### 7.5 Extend `AgentResultStatus`

Add:

- `PENDING_APPROVAL`
- `DENIED`
- `CANCELLED`
- `ROLLED_BACK`

This is required to align router behavior, workflow behavior, and UI state models.

## 8. Spec and Catalog Changes

### 8.1 Normalize autonomy vocabulary

Recommended canonical vocabulary:

- `advisory`
- `draft`
- `supervised`
- `bounded-autonomous`
- `autonomous`

Rationale:

- clear progression
- better mapping to staged autonomy
- less ambiguity than the current mixed terms

Required changes:

- update `docs/agent-spec.md`
- update `platform/agent-catalog/catalog-schema.yaml`
- update runtime enum and mappers
- reject old or mixed values unless passed through an explicit migration alias layer

### 8.2 Add explicit action governance section to the spec

Add to `docs/agent-spec.md`:

```yaml
actionGovernance:
  allowedActionClasses:
    - READ
    - DRAFT
  reversibility:
    default: reversible
    overrides:
      - actionClass: WRITE_IRREVERSIBLE
        requiresApproval: true
  delegation:
    maxDepth: 2
    maxFanOut: 5
  budgets:
    maxCostPerTurnUsd: 1.50
    maxToolCallsPerTurn: 12
    maxExternalCallsPerTurn: 3
```

### 8.3 Add memory governance section

Extend `memoryModel` with:

- namespace
- owner
- versioning mode
- sharing mode
- conflict resolution policy
- provenance requirements
- verification interval

### 8.4 Add evaluation and promotion fields

Extend spec to include:

- release gate references
- shadow canary requirements
- invariant set references
- promotion criteria

## 9. Code Changes by Module

### 9.1 `platform/java/agent-core`

#### Objective

Make agent execution governance-aware at the contract and lifecycle layer.

#### Changes

- Extend `AgentResultStatus`
- Introduce `ActionIntent`
- Introduce `PolicyDecision`
- Introduce `AgentDatasheet`
- Introduce approval domain objects
- Update `AutonomyLevel`
- Replace confidence-only routing with risk-aware action routing
- Extend `AgentDefinitionValidator` to validate:
  - normalized autonomy
  - action classes
  - memory governance completeness
  - evaluation gate completeness for high-risk agents
  - privileged tool declarations

#### New classes

- `framework/governance/ActionIntent.java`
- `framework/governance/ActionClass.java`
- `framework/governance/ReversibilityClass.java`
- `framework/governance/PolicyDecision.java`
- `framework/governance/AgentDatasheet.java`
- `framework/runtime/ApprovalRequest.java`
- `framework/runtime/ApprovalDecision.java`
- `framework/runtime/ApprovalStatus.java`
- `framework/runtime/AgentApprovalRouter.java`
- `framework/runtime/ActionClassifier.java`

#### Existing classes to modify

- `AgentResultStatus.java`
- `AutonomyLevel.java`
- `AutonomyRouter.java`
- `AgentSpecLoader.java`
- `AgentDefinitionValidator.java`

### 9.2 `platform/java/governance`

#### Objective

Turn governance into executable policy enforcement rather than a passive interface.

#### Changes

- Add concrete `PolicyEngine` implementation
- Add policy registry and compiled policy model
- Support evaluation for:
  - action intents
  - memory operations
  - workflow step execution
  - tool access
  - tenant boundary checks
- Add policy obligation support:
  - approval required
  - log full artifacts
  - redact output
  - require compensation plan
  - elevate monitoring

#### New classes

- `PolicyEngineImpl.java`
- `PolicyRegistry.java`
- `CompiledPolicy.java`
- `PolicyObligation.java`
- `PolicyEvaluationContext.java`
- `PolicyDecisionRecord.java`

#### Integration points

- `AutonomyRouter`
- tool execution path
- memory stores
- workflow runtime

### 9.3 `platform/java/workflow`

#### Objective

Make workflows trustworthy multi-agent execution envelopes rather than just durable step graphs.

#### Changes

- Fix parent-tenant inheritance for sub-workflows
- Carry policy and trace context into child workflows
- Add step-level action intents and policy decisions
- Add approval step type
- Add compensation verification
- Add workflow-level invariant monitors
- Add kill-switch aware run suspension

#### New or changed classes

- modify `DurableWorkflowRuntime.java`
- extend `WorkflowLifecycleEvent`
- add `ApprovalWorkflowListener`
- add `PolicyWorkflowListener`
- add `InvariantWorkflowListener`

### 9.4 `platform/java/agent-runtime`

#### Objective

Provide a production-grade execution and evidence substrate.

#### Changes

- Add execution hooks before and after agent dispatch
- add action ledger append
- add delegation grant propagation
- add JIT scoped credentials
- add invariant monitor integration
- add replay export package

#### New classes

- `dispatch/ActionGovernedDispatcher.java`
- `dispatch/AgentExecutionEnvelope.java`
- `identity/AgentExecutionGrant.java`
- `identity/DelegationGrant.java`
- `audit/AgentTraceLedger.java`
- `audit/HashChainedTraceAppender.java`
- `monitoring/InvariantMonitor.java`
- `monitoring/InvariantViolation.java`

### 9.5 `platform/java/agent-runtime` memory

#### Objective

Make memory useful and governable.

#### Changes

- implement `PersistentMemoryPlane.searchSemantic()`
- add hybrid retrieval:
  - dense
  - lexical
  - metadata filter
  - rerank
  - justification
- add memory versioning and provenance
- add namespace isolation
- add memory mutation audit entries
- add governed shared memory mode for multi-agent use

#### New classes

- `memory/retrieval/HybridMemoryRetriever.java`
- `memory/retrieval/MemoryReranker.java`
- `memory/retrieval/MemoryJustifier.java`
- `memory/governance/MemoryNamespace.java`
- `memory/governance/MemoryMutationPolicy.java`
- `memory/governance/VersionedMemoryItem.java`
- `memory/governance/MemoryProvenance.java`

#### Existing classes to modify

- `PersistentMemoryPlane.java`
- `MemoryStoreAdapter.java`
- legacy store adapters where needed

### 9.6 `platform/agent-catalog`

#### Objective

Bring catalog declarations in line with runtime-enforced governance.

#### Changes

- normalize autonomy enum
- add action governance block
- add memory governance block
- add evaluation gate references
- add privileged tool review metadata
- add datasheet generation metadata

#### Files to modify

- `platform/agent-catalog/catalog-schema.yaml`
- `platform/agent-catalog/base-agent-template.yaml`
- concrete agent YAMLs as follow-up migration work

## 10. Detailed Work Packages

### WP1: Taxonomy and Contract Alignment

#### Goal

Remove semantic drift between spec, catalog, validator, and runtime.

#### Deliverables

- normalized autonomy vocabulary
- normalized determinism and state vocabulary where needed
- new action governance schema
- new approval statuses

#### Acceptance criteria

- no runtime enum differs from canonical schema values without explicit alias mapping
- no docs mention result statuses missing from code
- all agent YAMLs validate against the same vocabulary

#### Tests

- spec loader tests
- validator tests
- catalog schema conformance tests

### WP2: Executable Governance

#### Goal

Ensure every privileged action is policy-evaluated before execution.

#### Deliverables

- concrete `PolicyEngineImpl`
- policy registry
- policy decision object
- runtime enforcement hooks

#### Acceptance criteria

- any `WRITE_IRREVERSIBLE` action without approval policy is denied
- any cross-tenant action intent is denied unless explicitly allowed
- any unapproved privileged tool call is denied

#### Tests

- unit tests for policy evaluation
- integration tests with dispatcher and workflow
- replay tests showing allow/deny/escalate outcomes

### WP3: Human Approval and Staged Autonomy

#### Goal

Move from confidence-only approval to risk-aware staged autonomy.

#### Deliverables

- approval request model
- pending approval status
- approval workflow support
- action classification

#### Acceptance criteria

- low-risk read actions can auto-run
- high-risk irreversible actions always require approval or dead-zone delay
- approval requests carry plain-language explanation and risk summary

#### Tests

- approval routing tests
- workflow approval gate tests
- UI/API contract tests when UI surfaces are added

### WP4: Memory Governance and Retrieval

#### Goal

Make memory useful, safe, and reviewable.

#### Deliverables

- real semantic retrieval
- provenance and versioning
- governed shared memory
- retention and redaction enforcement

#### Acceptance criteria

- memory retrieval returns results under hybrid retrieval tests
- fact updates create versions, not blind replacement
- governed shared memory rejects unauthorized mutation
- retention job actually removes or redacts eligible items

#### Tests

- retrieval correctness tests
- provenance/versioning tests
- retention/redaction tests
- multi-agent shared memory consistency tests

### WP5: Evidence Plane

#### Goal

Make privileged execution and governance decisions tamper-evident.

#### Deliverables

- append-only action trace ledger
- chained hashes
- approval ledger
- memory mutation ledger

#### Acceptance criteria

- every privileged action produces a ledger entry
- any missing or altered event breaks chain verification
- replay package reconstructs action path, approval path, and policy path

#### Tests

- hash-chain integrity tests
- replay/reconstruction tests
- tamper-detection tests

### WP6: Runtime Safety and Invariants

#### Goal

Prevent catastrophic failure and autonomy drift.

#### Deliverables

- invariant monitor framework
- delegation depth caps
- budget ceilings
- kill-switch support
- automatic demotion triggers

#### Acceptance criteria

- invariant violations surface immediately and are auditable
- excessive delegation or cost exhausts safely
- kill-switch stops new privileged actions

#### Tests

- saturation tests
- budget tests
- circuit-breaker tests
- kill-switch drills

### WP7: Assurance and Release Governance

#### Goal

Prove fitness for production in a way that engineering and compliance can both consume.

#### Deliverables

- datasheet generation
- evaluation pack references
- promotion gate runner
- assurance case template

#### Acceptance criteria

- every production agent has a datasheet
- every high-risk agent has an assurance package
- promotion requires green evaluation, security, and rollback evidence

#### Tests

- datasheet generation tests
- evaluation gate tests
- assurance package completeness checks

## 11. Proposed Runtime Flow

### 11.1 Target flow for privileged actions

```text
request
  -> agent dispatch
  -> datasheet lookup
  -> action classification
  -> policy evaluation
  -> approval routing if required
  -> execution grant issuance
  -> tool or workflow execution
  -> invariant monitoring
  -> evidence append
  -> result + replay metadata
```

### 11.2 Target flow for memory mutation

```text
memory write request
  -> memory namespace resolution
  -> ownership and permission check
  -> policy evaluation
  -> conflict/version check
  -> mutation apply
  -> provenance record attach
  -> evidence append
  -> retention index update
```

## 12. Concrete Schema Proposals

### 12.1 Agent Datasheet example

```yaml
agentId: "agent.procurement-assistant"
version: "2.1.0"
namespace: "platform.procurement"
riskTier: "tier-2"
autonomyTier: "supervised"
criticality: "high"
owners:
  - team: "platform-agent-runtime"
    role: "technical-owner"
  - team: "procurement-ops"
    role: "domain-owner"
allowedActionClasses:
  - READ
  - DRAFT
  - WRITE_REVERSIBLE
toolPermissions:
  - toolId: "purchase-order-service"
    actions: ["READ", "DRAFT"]
memoryBindings:
  - namespace: "tenant.procurement.shared"
    access: "read-write"
dataClassification: "internal"
retentionPolicy:
  defaultDays: 90
approvalRules:
  - actionClass: "WRITE_IRREVERSIBLE"
    requiredRoles: ["manager", "compliance"]
evaluationPackRefs:
  - "eval.procurement.regression.v1"
killSwitchProcedure: "runbook://agent-control/procurement-assistant"
auditMode: "regulated"
lastReviewedAt: "2026-03-22"
nextReviewAt: "2026-06-22"
```

### 12.2 Action ledger event example

```json
{
  "eventId": "evt_01H...",
  "traceId": "trc_01H...",
  "previousHash": "sha256:...",
  "agentId": "agent.procurement-assistant",
  "tenantId": "tenant-acme",
  "actionClass": "WRITE_REVERSIBLE",
  "toolId": "purchase-order-service",
  "targetType": "purchase_order",
  "targetId": "po_123",
  "argsHash": "sha256:...",
  "policyDecision": "ALLOW_WITH_APPROVAL",
  "approvals": [
    {
      "requestId": "apr_001",
      "decision": "APPROVED",
      "approver": "manager@acme"
    }
  ],
  "modelVersion": "gpt-5.4",
  "datasheetVersion": "2.1.0",
  "timestamp": "2026-03-22T08:00:00Z"
}
```

## 13. Detailed Validation Strategy

### 13.1 Build-time validation

- schema validation for all agent YAMLs
- spec-to-runtime conformance tests
- vocabulary alignment tests
- policy coverage checks for privileged agents

### 13.2 Unit validation

- policy engine rule tests
- approval routing tests
- memory versioning tests
- evidence hash-chain tests

### 13.3 Integration validation

- end-to-end privileged action with approval
- sub-workflow tenant inheritance
- governed shared memory mutation
- replay package reconstruction

### 13.4 Adversarial validation

- prompt injection against tool selection
- cross-tenant access attempts
- approval bypass attempts
- memory poisoning attempts
- evidence tampering attempts

### 13.5 Production validation

- shadow mode metrics
- canary release comparison
- invariant violation rate
- rollback drill success
- kill-switch drill success

## 14. Rollout Plan

### Phase 0: Immediate hardening

Target: 1-2 weeks

- fix `sub-tenant` bug
- align autonomy/result status vocabulary
- add spec/runtime conformance check failing CI on drift
- document current unsupported spec sections explicitly

### Phase 1: Policy and approval substrate

Target: 2-6 weeks

- implement executable policy engine
- add action intent model
- add approval request/decision domain
- integrate with dispatcher and workflow

### Phase 2: Memory governance and evidence

Target: 6-12 weeks

- real memory governance
- semantic retrieval implementation
- evidence ledger with hash chaining
- memory mutation audit and provenance

### Phase 3: staged autonomy and safety monitors

Target: 10-16 weeks

- risk-aware autonomy routing
- delegation caps
- action budgets
- invariant monitors
- kill-switch integration

### Phase 4: assurance package and platformization

Target: 14-20 weeks

- datasheet generation
- evaluation packs
- promotion gates
- assurance case templates

## 15. Prioritized Backlog

### P0

- Fix sub-workflow tenant inheritance in `DurableWorkflowRuntime`
- Add missing approval-related result statuses
- Normalize autonomy vocabulary
- Fail CI when spec fields are silently dropped from runtime extraction

### P1

- Implement `PolicyEngineImpl`
- Add `ActionIntent` and `PolicyDecision`
- Add approval workflow primitives
- Add `AgentDatasheet`
- Replace confidence-only autonomy decisions

### P2

- Implement semantic memory retrieval
- Add provenance/versioned facts
- Add memory namespace governance
- Add action trace ledger with chained hashes

### P3

- Add promotion gates and assurance cases
- Add regulator/customer evidence bundles
- Add cross-agent shared-memory consensus policies

## 16. Acceptance Criteria for Program Completion

The program should not be considered complete until all of the following are true:

- Every production agent has a generated datasheet.
- Every privileged action is policy-evaluated before execution.
- Every privileged action creates an evidence record in a tamper-evident chain.
- Sub-workflows preserve tenant, trace, and policy context.
- Memory retrieval works in production and governed memory mutation is enforced.
- High-risk agent releases require approval, evaluation, rollback proof, and assurance pack.
- The spec is either enforced or rejected; silent dropping of governance intent is eliminated.

## 17. Open Questions

- Should the primary action ledger live in EventCloud, a dedicated audit-trail module, or both?
- Should approval workflows live in `platform/java/workflow` or a dedicated governance-runtime module?
- Should datasheets be generated at build time, deploy time, or both?
- How much of the kernel audit/hash-chain capability should be reused directly versus wrapped for agent-runtime usage?
- Which product team owns rollout of policy definitions after the platform substrate exists?

## 18. Recommended First Implementation Slice

If only one slice is approved immediately, it should be:

1. Fix tenant inheritance in workflows.
2. Normalize autonomy and approval status vocabulary.
3. Add `ActionIntent`, `PolicyDecision`, and a concrete `PolicyEngineImpl`.
4. Route privileged tool calls through policy before execution.
5. Append a structured action audit event for each privileged action.

This slice delivers the fastest move from descriptive governance to actual runtime control.

## 19. Appendix: Focused Validation Notes from This Review

Focused test execution was attempted against:

- `:platform:java:workflow:test --tests com.ghatana.platform.workflow.runtime.DurableWorkflowRuntimeTest`
- `:platform:java:agent-core:test --tests com.ghatana.agent.framework.memory.EventLogMemoryStoreTest`
- governance security tests in `platform/java/governance`

Observed outcome:

- The workflow test cases executed and passed before the task failed due a Gradle-side issue unrelated to workflow behavior.
- The `EventLogMemoryStore` test cases executed and passed before the task failed due the same Gradle-side issue.
- The broader Gradle run ended with `Com_ghatana_test_failure_tolerance_gradle.getThreshold()` being null during test task execution.
- Governance test compilation emitted warnings; governance test execution did not complete because the build aborted on the Gradle-side failure.

This means the review findings above are based on code inspection plus partial targeted test evidence, not on a fully clean module-wide test run.
