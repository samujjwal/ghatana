# Agent System Modernization Blueprint

**Date:** 2026-04-06  
**Status:** Proposed architecture and migration blueprint  
**Primary scope:** `platform`, `platform-kernel`, `products/aep`, `products/data-cloud`, `products/audio-video`  
**Focus:** framework, runtime, registry, specification, governance, privacy, security, observability, explainability, extensibility

## 1. Why this document exists

The repository already contains a serious agent foundation:

- a shared typed agent contract in `platform/java/agent-core`
- an AEP-owned advanced runtime in `products/aep/aep-agent-runtime`
- durable registry and context/memory-adjacent capabilities in `products/data-cloud`
- workflow, tool runtime, and plugin/kernel infrastructure in `platform/java/workflow`, `platform/java/tool-runtime`, and `platform-kernel`

The problem is not "there is no agent system." The problem is that the current system is **split across strong pieces without one coherent operating model**. The result is:

- document drift
- duplicate or overlapping authority boundaries
- partial spec-to-runtime execution
- no single release model for agent definitions, policies, tools, and evaluations
- uneven product adoption, especially for `audio-video`

This blueprint turns the current pieces into a **single platform-wide agentic architecture** without flattening everything into one giant module.

## 2. Executive recommendation

Do **not** re-introduce a giant shared `platform/java/agent-runtime` monolith.

Instead, make the agent system work as a **layered architecture**:

1. **Platform contracts and control standards** stay in platform:
   - `platform/java/agent-core`
   - `platform/java/tool-runtime`
   - `platform/java/workflow`
   - `platform-kernel/*`

2. **AEP remains the primary execution runtime** for advanced agent orchestration:
   - dispatch
   - execution policy
   - assurance
   - learning loops
   - governed execution

3. **Data Cloud becomes the durable system of record** for:
   - agent releases and metadata
   - memory artifacts
   - evaluation artifacts
   - rollout state
   - autonomy state
   - trace/index search

4. **Products integrate through adapters, not forks of the core model**:
   - AEP uses full runtime ownership
   - Data Cloud owns persistence and context/memory services
   - Audio-Video exposes specialized media capabilities as agent tools and optionally as domain agents

5. Build one **versioned release model** around:
   - `AgentSpec`
   - `AgentRelease`
   - `AgentInstanceConfig`
   - `PolicyPack`
   - `ToolContract`
   - `EvaluationPack`
   - `MemoryContract`

## 2.1 Non-negotiable quality gates

The modernization should treat the following as release-blocking quality gates, not aspirational follow-up work:

1. **Privacy and purpose limitation are enforced before access, not after logging**
   - memory retrieval, context hydration, and tool inputs must pass tenant, consent, and purpose checks
   - the existing `DataAccessBroker` seam in `platform/java/data-governance` should become mandatory for governed data access
   - every durable memory contract must declare retention, redaction, shareability, and deletion behavior

2. **Security controls travel with the agent release**
   - every deployable release should carry threat-model, policy-pack, signing, and runtime-admission metadata
   - side-effecting tools must execute through one governed boundary with sandbox, approval, egress, and audit decisions attached
   - the existing `ToolSandbox`, `ApprovalGateway`, `PolicyAsCodeEngine`, and `AgentTraceLedger` seams should be composed into a single release-aware control path

3. **Observability is privacy-safe and decision-complete**
   - tracing must cover planning, retrieval, tool use, memory writes, policy denials, approvals, and external commits
   - telemetry must be redacted by default so prompts, memory fragments, and tool payloads do not become a shadow data leak
   - every traceable run should carry agent release ID, policy pack version, and explanation bundle reference

4. **Explainability is part of execution, not just documentation**
   - the system should persist the minimum evidence needed to answer: what the agent saw, what it decided, what policy checks ran, what tools it used, and why the action was allowed or denied
   - this is especially important for DSLA/NDSLA-style learning and partially observable execution, where later review depends on durable evidence rather than prompt reconstruction

5. **Capability maturity must be explicit**
   - releases should declare which parts of the merged self-learning spec they actually implement: memory hierarchy, learning level, signal routing, evaluation rigor, delegation depth, and explainability guarantees
   - do not market an agent as "self-learning" or "autonomous" when the runtime only provides retrieval plus prompts with no governed promotion or evidence path

## 3. What the repo already has

### 3.1 Shared platform foundations

The current shared foundations are strong and should be kept:

| Capability               | Current location                                                   | What it already gives you                                                          |
| ------------------------ | ------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| Typed agent contract     | `platform/java/agent-core`                                         | `TypedAgent`, `AgentDescriptor`, `AgentResult`, `AgentType`, provider SPI, loaders |
| Full spec parsing        | `platform/java/agent-core/.../framework/spec/AgentSpecLoader.java` | bridges the large YAML spec into runtime definitions                               |
| Tool abstraction         | `platform/java/agent-core/.../framework/tools`                     | `FunctionTool`, `ToolRegistry`                                                     |
| Tool governance          | `platform/java/tool-runtime`                                       | sandbox, approval workflow, change approval, execution monitor                     |
| Durable orchestration    | `platform/java/workflow`                                           | definition-driven workflow runtime, state store, step operator registry            |
| Plugin/kernel boundaries | `platform-kernel`                                                  | canonical registry and plugin/module lifecycle                                     |

### 3.2 AEP foundations

AEP is already the richest runtime owner:

| Capability              | Current location                                                  | Notes                                                    |
| ----------------------- | ----------------------------------------------------------------- | -------------------------------------------------------- |
| Advanced runtime        | `products/aep/aep-agent-runtime`                                  | memory, dispatch, learning, resilience, assurance, audit |
| Central registry facade | `products/aep/aep-central-runtime`                                | catalog-backed discovery and materialization             |
| Tiered dispatch         | `products/aep/aep-agent-runtime/.../CatalogAgentDispatcher.java`  | Tier-J, Tier-S, Tier-L                                   |
| Governed execution      | `products/aep/aep-agent-runtime/.../GovernedAgentDispatcher.java` | invariants, trace ledger, denial path                    |
| Pipeline bridge         | `products/aep/aep-agent-runtime/.../AgentOperatorFactory.java`    | agent-to-operator mapping                                |

### 3.3 Data Cloud foundations

Data Cloud already behaves like the durable memory and registry plane:

| Capability                  | Current location                                           | Notes                                                                |
| --------------------------- | ---------------------------------------------------------- | -------------------------------------------------------------------- |
| Durable registry backend    | `products/data-cloud/agent-registry`                       | persists descriptors/configs, keeps cache, publishes registry events |
| Context gateway             | `products/data-cloud/platform-api/.../ContextGateway.java` | token-budgeted context selection for LLM-style agents                |
| Autonomy control            | `products/data-cloud/platform-api/.../client/autonomy`     | stateful autonomy and transition model                               |
| Policy/config compilation   | `products/data-cloud/platform-config`                      | config registry, policy compiler, OPA integration, routing compiler  |
| Plugin-driven extensibility | `products/data-cloud/platform-plugins`                     | storage and compute plugin implementations                           |

### 3.4 Audio-Video foundations

Audio-Video is not yet a first-class agent runtime, but it is a very good **capability mesh**:

| Capability                          | Current location                             | Notes                                       |
| ----------------------------------- | -------------------------------------------- | ------------------------------------------- |
| Speech, vision, multimodal services | `products/audio-video/modules/*`             | domain execution services                   |
| Model registry/store                | `products/audio-video/libs/common/.../model` | model metadata and local registry behaviors |
| Desktop orchestration surface       | `products/audio-video/apps/desktop`          | user-facing workflow surface                |

The right move is not to force Audio-Video to become another AEP clone. The right move is to make it a **provider of domain tools, models, and domain agents**.

## 4. Current architectural problems

### 4.1 Documentation drift is real

The repo currently has multiple authoritative-looking documents that disagree with each other or with code:

- `docs/SHARED_LIBRARY_REGISTRY.md` says `platform/java/agent-runtime` is active, but `settings.gradle.kts` says advanced runtime was relocated to `products:aep:aep-agent-runtime`.
- `docs/SHARED_LIBRARY_REGISTRY.md` still points `plugin` to `platform/java/plugin`, while root settings mark that path as archived in favor of `platform-kernel`.
- `docs/adr/ADR-001-typed-agent-framework.md` describes a six-type taxonomy, while `AgentType.java` is now effectively nine canonical types plus deprecated `LLM`.
- `docs/agent-system/README.md` references `Unified_Self_Learning_Agents_Spec_Final.md`, but the actual document in the folder is `Unified_Self_Learning_Agents_Spec_Merged.md`.

This is not cosmetic. It causes wrong ownership decisions and wrong dependency decisions.

### 4.2 The spec is richer than the executable runtime contract

`docs/agent-system/agent-spec.md` and `AgentSpecLoader` support a much richer model than what the runtime consistently materializes. Today:

- `AgentSpecLoader.extractDefinition()` maps only a subset of the full spec into `AgentDefinition`
- governance, evaluation, reasoning portfolio, interoperability, memory, and assurance are mostly labels or partial config, not first-class runtime behavior
- product catalogs still contain legacy and semi-legacy field combinations

This creates a familiar failure mode: **beautiful specification, partial enforcement**.

### 4.3 Registry authority is split

Today there are at least four meaningful registry surfaces:

- platform catalog definitions under `platform/agent-catalog`
- product catalogs under AEP and Data Cloud
- AEP central runtime registry/materializer
- Data Cloud durable registry

Each is useful. None is yet the single release authority for the whole lifecycle.

### 4.4 Runtime ownership is partly right but not cleanly expressed

The codebase is actually moving toward a sensible model:

- contracts in platform
- advanced runtime in AEP
- persistence in Data Cloud

But the docs and some naming still imply a shared monolithic runtime. That ambiguity will keep producing accidental duplication.

### 4.5 Tool execution is not yet standardized enough

There is a local abstraction stack:

- `ToolRegistry`
- `FunctionTool`
- `ToolSandbox`
- approval workflows

But there is no single standard contract for:

- tool discovery
- tool schemas
- approval metadata
- tool result envelopes
- remote tool adapters
- transport-agnostic interoperability

This matters because modern agent systems increasingly need both:

- in-process tools
- remote tools exposed over standard protocols

### 4.6 Observability is not yet future-proof

You already have observability hooks, but not a unified agent telemetry vocabulary across:

- planning
- retrieval
- tool execution
- memory writes
- policy decisions
- promotion/evaluation
- delegation chains

Without that, the system can run, but it will be hard to debug, govern, or improve at scale.

### 4.7 Audio-Video is under-integrated into the agent model

Audio-Video has strong domain services, but it is mostly treated as product-specific AI infrastructure rather than a reusable agentic capability domain. That leaves a gap for:

- multimodal tool invocation
- media-specific memory artifacts
- agent-visible capability descriptors
- cross-product orchestration

### 4.8 Cross-cutting trust gaps are still optional in practice

The repo already contains several important seams for trust and governance, but they are not yet wired together as mandatory runtime behavior:

- `platform/java/data-governance/.../DataAccessBroker` gives a consent + purpose gate, but the agent blueprint does not yet require all governed retrieval and tool-access paths to use it
- `platform/java/policy-as-code/.../PolicyAsCodeEngine` exists, but policy decisions are not consistently attached to every runtime action, memory mutation, and rollout transition
- `products/aep/aep-agent-runtime/.../MemorySecurityManager` and `MemoryRedactionFilter` exist, but the documents do not currently make privacy-preserving memory writes and reads a universal requirement
- `products/aep/aep-agent-runtime/.../AgentTraceLedger` provides a strong append-only trace primitive, but explainability and evaluation still rely too much on convention rather than a required evidence bundle
- `platform/java/tool-runtime/.../ApprovalGateway` and `ToolSandbox` exist, but they are not yet presented as the only legitimate path for sensitive side effects

This is the central modernization problem for privacy, security, observability, and explainability: the ingredients exist, but they are not yet mandatory, versioned, and release-scoped.

## 5. Target architecture

## 5.1 Future-state operating model

Use a five-layer operating model:

### Layer A: Specification and release plane

**Owns:** what an agent is, what version is deployable, and what policy/eval packs are attached.

Primary home:

- `platform/java/agent-core`
- Data Cloud durable stores for release persistence

Primary artifacts:

- `AgentSpec`
- `AgentRelease`
- `AgentInstanceConfig`
- `PolicyPack`
- `EvaluationPack`
- `ToolContract`
- `MemoryContract`

### Layer B: Control and governance plane

**Owns:** approval, rollout, promotion, policy, rollout state, tenancy, risk budgets.

Primary home:

- `platform/java/tool-runtime`
- `platform/java/workflow`
- `platform/java/policy-as-code`
- `platform/java/identity`
- `products/data-cloud/platform-config`

### Layer C: Execution plane

**Owns:** live invocation, planning, dispatch, retries, assurance, checkpointing, delegation, HITL escalation.

Primary home:

- `products/aep/aep-agent-runtime`
- `products/aep/orchestrator`
- `products/aep/aep-engine`

### Layer D: Memory, context, and evaluation plane

**Owns:** episodic memory, semantic memory, procedural memory, context packing, evaluation traces, autonomy state, release audit.

Primary home:

- `products/data-cloud/platform-api`
- `products/data-cloud/platform-config`
- `products/data-cloud/agent-registry`
- `products/data-cloud/platform-analytics`

### Layer E: Product capability plane

**Owns:** domain-specific tools, models, adapters, and product workflows.

Primary home:

- `products/audio-video/*`
- product-specific provider registries
- product-specific catalogs

## 5.2 The key architectural rule

**Agent behavior may be product-owned, but the release contract, telemetry vocabulary, and governance envelope must be platform-consistent.**

That is the point of leverage.

## 6. What should live where

### 6.1 Keep in `platform/java/agent-core`

This module should become the canonical shared contract layer and nothing more.

It should own:

- `TypedAgent`, `AgentDescriptor`, `AgentResult`, `AgentType`
- `AgentSpec` and `AgentSpecLoader`
- `AgentDefinition`, `AgentInstance`-style runtime subset models
- `AgentLogicProvider` SPI
- portable tool descriptor types
- portable memory/evaluation contract types
- portable action classification and autonomy enums
- spec validators
- release manifest model classes

It should **not** own:

- AEP-specific dispatch logic
- durable registry infrastructure
- heavy learning pipelines
- product-specific orchestration behavior

### 6.2 Keep in `products/aep/aep-agent-runtime`

This remains the main advanced runtime.

It should own:

- live dispatch and tier resolution
- execution policy and retry behavior
- delegation and depth/fanout enforcement
- governed dispatch
- learning and promotion workflows
- trace ledger generation
- runtime memory adapters
- operator tree execution bridges

It should be the **default runtime** for complex agent execution across the company, even when the agent definition is shared.

### 6.3 Expand `products/data-cloud/agent-registry` into a release registry

This should become more than a descriptor store.

It should persist and query:

- `AgentRelease`
- signed release metadata
- evaluation summaries
- rollout state
- deprecation state
- compatibility metadata
- tool capability indices
- memory contract metadata

This is the place to answer:

- what version is approved?
- what version is live for tenant X?
- what evaluation pack approved it?
- what policy bundle applies?

### 6.4 Use `platform/java/tool-runtime` as the standard tool execution boundary

This module should be treated as the canonical tool governance layer.

Add or standardize:

- tool execution envelope
- tool policy tags
- approval hooks
- reversible/irreversible action metadata
- sandbox policy mapping
- audit event emission
- remote tool adapter interface

### 6.5 Use `platform/java/workflow` for durable planning, not just generic workflows

Do not create a second durable planning engine elsewhere.

Instead:

- make planning agents compile plan steps into `workflow` definitions
- use `DurableWorkflowRuntime` for long-running multi-step plans
- treat AEP as the main event-driven execution substrate

### 6.6 Use `platform-kernel` for packaging, isolation, and capability discovery

The kernel/plugin system should own:

- capability registration
- module/plugin packaging boundaries
- dependency resolution
- trusted loading boundaries
- runtime isolation for pluggable providers

This is the correct substrate for future agent packs and domain capability plugins.

### 6.7 Do not turn Audio-Video into a parallel agent framework

Instead, add:

- an Audio-Video capability catalog
- tool adapters for STT, TTS, vision, multimodal inference
- optional domain agents for media orchestration
- memory adapters for media artifacts and embeddings

That lets the rest of the system use Audio-Video as a multimodal capability domain.

## 7. Spec vNext: what must change

## 7.1 Split specification into six first-class artifacts

The current single giant spec is conceptually rich but operationally overloaded. Keep the unified document as reference, but implement the runtime contract as six executable artifacts:

1. **AgentSpec**
   - identity
   - capabilities
   - reasoning profile
   - interfaces
   - tools/resources
   - execution model
   - governance defaults
   - memory contract
   - observability contract

2. **AgentRelease**
   - immutable release ID
   - spec digest
   - policy pack digest
   - eval pack digest
   - compatible runtime versions
   - signing/attestation metadata

3. **AgentInstanceConfig**
   - tenant overrides
   - model/provider selection
   - budgets
   - rollout flags
   - environment bindings

4. **PolicyPack**
   - allowed action classes
   - reversible/irreversible rules
   - delegation budgets
   - egress and sandbox rules
   - data classification handling

5. **EvaluationPack**
   - benchmark suite
   - shadow tests
   - regression gates
   - promotion rules

6. **MemoryContract**
   - memory classes allowed
   - retention and decay rules
   - provenance requirements
   - redaction requirements
   - shareability mode

## 7.2 Normalize enum values across docs, spec, and code

A mandatory cleanup item:

- one canonical agent type set
- one canonical autonomy scale
- one canonical determinism scale
- one canonical state mutability scale
- one canonical failure mode set

Then provide compatibility mappings for legacy YAML.

Do not keep silently divergent values like:

- `mission-critical` vs `critical`
- `semi-autonomous` vs `supervised`
- `persistent` vs `external-state`
- `fully-deterministic` vs `full`

The loader may support aliases, but the **stored canonical value must be singular**.

## 7.3 Make governance executable, not descriptive

The spec must compile directly into:

- approval requirements
- sandbox policy
- delegation policy
- memory write policy
- rollout gates
- observability sampling rules

If a field cannot be enforced or interpreted, move it into a clearly non-normative section.

## 7.4 Add release and compatibility metadata

Every deployable agent release should carry:

- `agentReleaseId`
- `specVersion`
- `runtimeCompatibility`
- `toolContractVersion`
- `policyPackVersion`
- `evaluationPackVersion`
- `memoryContractVersion`
- artifact digest
- signing/attestation reference

This is essential for rollback and mixed-runtime environments.

The implementation detail should respect current code reality: the existing spec loader already uses `agentSpecVersion` as the canonical field in `AgentSpec`, so the modernization should preserve compatibility with that field rather than introducing a second, conflicting source of truth for spec format versioning.

## 7.5 Make trust metadata part of `AgentRelease`

`AgentRelease` should not only describe deployability. It should also describe the trust envelope required to admit and operate the agent:

- `dataClassesHandled`
- `permittedPurposes`
- `consentRequirementProfile`
- `redactionProfileId`
- `threatModelId`
- `policyPackId`
- `telemetryContractVersion`
- `explanationContractVersion`
- `evaluationPackId`
- `capabilityMaturityProfile`

That gives the release registry enough information to answer practical operator questions:

- can this release access sensitive tenant memory?
- which purposes is it allowed to process data for?
- what explanation contract does it emit?
- what learning level and promotion path does it actually support?
- which redaction rules and threat model were reviewed before activation?

## 8. Runtime model: what must become first-class

## 8.1 Standard runtime loop

All advanced agents should be understood as executing this loop:

1. load spec + release + instance config
2. establish execution budget and governance envelope
3. hydrate working context
4. retrieve memory/context
5. plan or select next action
6. execute tool/operator/sub-agent step
7. verify result against policy and schema
8. commit external effects if allowed
9. append trace and memory artifacts
10. trigger learning/evaluation side effects asynchronously

Today the pieces exist, but they need one canonical runtime contract.

## 8.2 Separate authoritative execution from exploratory reasoning

The DSLA/NDSLA documents are directionally right. Operationally, the system should adopt:

- **explore tier**: probabilistic, retrieval-heavy, LLM-capable, proposal-producing
- **authority tier**: deterministic or constrained execution, schema-validating, policy-enforcing

Do not let exploratory output commit directly unless:

- the action class is allowed
- required invariants passed
- approval conditions passed
- trace/logging requirements are satisfied

This should be a universal rule for AEP, Data Cloud, and future cross-product agents.

## 8.3 Treat planning as workflow compilation

Planning agents should not invent private orchestration semantics when a durable workflow already exists.

Recommended rule:

- planning agent produces plan graph
- plan graph compiles into `workflow` runtime definitions
- AEP or workflow runtime executes durable steps

This gives better pause/resume, checkpointing, auditability, and HITL insertion.

## 8.4 Make tool execution transport-agnostic

The runtime should support:

- in-process Java tools
- platform-managed sandbox tools
- remote service tools
- MCP-compatible tools

All four should map into one normalized `ToolContract` and one normalized `ToolExecutionResult`.

## 9. Registry model: what the system needs

## 9.1 One logical registry, multiple physical backends

Keep one logical model with distinct sub-registries:

- **Definition Registry**: source-controlled spec definitions
- **Release Registry**: approved signed deployable releases
- **Runtime Registry**: live instances and leases
- **Capability Registry**: tool/model/provider capabilities
- **Memory Registry**: memory namespaces and retention policies
- **Evaluation Registry**: test packs, historical scores, promotion evidence

Recommended physical ownership:

- source definitions: repo + CI validation
- release/runtime/eval/memory indices: Data Cloud
- live execution leases: AEP runtime + Data Cloud mirror

## 9.2 Add release states

Every agent release should move through explicit states:

- `DRAFT`
- `VALIDATED`
- `SHADOW`
- `CANARY`
- `ACTIVE`
- `DEPRECATED`
- `RETIRED`
- `BLOCKED`

This is missing today and is required for safe future growth.

## 9.3 Add tenant-scoped rollout records

Rollout should not be encoded as ad hoc config. It should be stored as first-class records:

- tenant
- environment
- release target
- traffic split
- fallback release
- kill switch
- approval status

## 10. Memory system: how to make it practical

## 10.1 Make Data Cloud the durable memory plane

Use Data Cloud as the durable substrate for:

- episodic traces
- semantic facts
- procedural skills/policies
- evaluation artifacts
- retrieval indices

Keep runtime-local working memory in AEP or local runtime components, but all durable memory classes should have a Data Cloud representation.

## 10.2 Standardize memory classes

Use five practical memory classes:

- **working**: per turn or per run, ephemeral
- **task-state**: resumable execution state
- **episodic**: trace-like experience records
- **semantic**: durable facts and summaries
- **procedural**: reusable skills, policies, prompts, tool recipes

Each memory write must carry:

- tenant
- agent ID
- release ID
- provenance
- confidence
- determinism class
- retention class
- visibility/shareability

## 10.3 Add promotion rules from episodic to procedural

Do not let learned procedures become authoritative just because they were seen often.

Required path:

1. episodic evidence
2. consolidation proposal
3. evaluation pack
4. policy gate
5. human approval where required
6. promoted procedural artifact
7. release registration

## 10.4 Privacy-preserving and poisoning-resistant memory lifecycle

The merged self-learning spec is directionally correct that durable memory is a capability multiplier. It is also a capability multiplier for failure if governance is weak. The practical lifecycle should therefore be:

1. classify the candidate memory write
2. redact sensitive fields before persistence
3. attach provenance, confidence, source agent, release ID, and determinism class
4. evaluate tenant, consent, and purpose eligibility before durable write
5. store with retention and deletion semantics
6. gate promotion through evaluation, policy, and human review where required
7. detect poisoning, low-quality repetition, and cross-tenant leakage patterns
8. support deletion, expiry, and re-compaction without orphaning explanation traces

The current repo already has good starting points:

- `MemorySecurityManager` for access decisions
- `TenantIsolatingMemorySecurityManager` for tenant separation
- `MemoryRedactionFilter` for pre-write sanitization
- `AgentTraceLedger` for append-only traceability

What is missing is one mandatory lifecycle that all products use, especially when agents start sharing context, emitting cross-agent learning signals, or promoting episodic evidence into procedural behavior.

## 11. Tool system modernization

## 11.1 Align the internal tool model with current industry direction

As of 2026-04-06, the relevant external direction is clear:

- the **Model Context Protocol revision 2025-03-26** formalizes lifecycle negotiation, tools, resources, prompts, sampling, progress, cancellation, and logging
- the **OpenAI Responses API** and function-calling guidance emphasize JSON-schema-based tools and strict structured outputs

Practical recommendation:

- keep internal `ToolRegistry` and `ToolSandbox`
- introduce a normalized `ToolContract` model in `agent-core`
- add an MCP adapter layer in `tool-runtime`
- add provider adapters for built-in/remote/custom tool surfaces

## 11.2 Standard tool envelope

Every tool call should produce:

- tool ID and version
- invocation ID
- caller agent ID and release ID
- tenancy
- risk/action class
- request schema version
- normalized result envelope
- side-effect summary
- policy decision
- approval decision, if applicable
- trace correlation IDs

## 11.3 Action classes must be enforced at tool boundary

Use action classes consistently:

- `READ`
- `DRAFT`
- `WRITE_REVERSIBLE`
- `WRITE_IRREVERSIBLE`
- `CALL_EXTERNAL`
- `DELEGATE`
- `MEMORY_MUTATION`
- `POLICY_CHANGE`

The sandbox and approval system should understand these natively.

## 12. Observability and evaluation

## 12.1 Use OpenTelemetry as the base standard, but version your agent schema

OpenTelemetry is the right base. However, as of 2026-04-06 the OpenTelemetry GenAI semantic conventions are still marked **Development**, so you should not hard-wire your entire internal model to experimental names.

Recommended approach:

- emit stable OTel base spans/logs/metrics now
- define an internal `ghatana.agent.*` semantic layer
- optionally dual-emit OTel GenAI conventions where supported
- keep a version tag for your internal agent telemetry contract

## 12.2 Minimum trace graph for every advanced run

Every advanced agent run should emit spans/events for:

- run start
- context retrieval
- planner invocation
- tool execution
- sub-agent delegation
- policy evaluation
- approval request
- memory write
- evaluation gate
- external commit
- run completion/failure

## 12.3 Metrics that actually matter

Track at least:

- run latency
- time to first action
- tool success/failure
- delegation depth
- policy denials
- approval wait time
- token usage
- retrieval hit quality
- memory promotion rate
- rollback rate
- release-level regression rate

## 12.4 Explainability contract

Every advanced run should emit or persist an explanation bundle with at least:

- request or goal summary
- context sources used
- memory artifacts read and written
- plan or policy path taken
- tools invoked and their side-effect summaries
- approval and denial decisions
- confidence and uncertainty summary
- release ID, policy pack version, and evaluation pack reference

This bundle should be durable enough for operator review and sparse enough to avoid becoming a second uncontrolled copy of sensitive data. In practice, that means referencing redacted artifacts and policy decisions rather than indiscriminately copying prompts and raw payloads into logs.

For DSLA/NDSLA-style agents, explainability should also include:

- what learning signals were emitted or consumed
- whether the run changed any durable memory candidate
- whether any promotion path was triggered
- whether the agent was operating in explore tier or authority tier

## 12.5 Privacy-safe telemetry

Do not let observability become an accidental data exfiltration path.

Minimum rules:

- prompts, retrieved memory chunks, and tool payloads should be redacted, sampled, or referenced by ID unless explicitly approved for secure debugging
- telemetry export policies should be release-aware and tenant-aware
- denied policy decisions should still be observable without leaking the blocked payload
- cross-agent correlations should preserve causality without broadening data visibility
- explanation bundles and traces should use the same retention and deletion controls as the governed data they reference

## 13. Governance, rollout, and trust

## 13.1 Policy distribution should use bundle semantics

OPA's current bundle model is the right pattern for policy/data delivery:

- versioned bundles
- hot reload
- eventual consistency
- policy/data loaded together

Use that for:

- action governance
- memory governance
- tenancy rules
- external egress rules
- release admission rules

## 13.2 Rollout control should be provider-agnostic

For release gating and canary behavior, adopt an OpenFeature-style abstraction for rollout decisions rather than hard-coding one flag backend. That gives:

- cleaner experimentation
- tenant-aware rollout
- canary and shadow toggles
- provider swap flexibility

## 13.3 Sign and attest release artifacts

Agent releases should be signed and verifiable. Sigstore is a strong fit for:

- release bundle signing
- policy/eval pack signing
- attestation of CI-produced artifacts
- later verification at deploy and runtime admission

Use signing at the `AgentRelease` bundle boundary, not only at container-image boundary.

## 13.4 Security and privacy gates are release gates

An agent release should not move to `CANARY` or `ACTIVE` unless the following are true:

- threat model reviewed for the declared action classes and data classes
- policy pack attached and distributable
- required redaction profile configured
- telemetry contract version pinned
- explanation contract version pinned
- evaluation pack passed for the declared capability maturity profile
- rollback and kill-switch path verified

This is where privacy, security, observability, and explainability become practical engineering constraints rather than architecture prose.

## 14. Product-specific modernization

## 14.1 AEP

### Keep

- AEP as advanced runtime owner
- tiered dispatch
- governed dispatcher
- operator/pipeline bridge
- orchestration and checkpointing

### Add

- canonical runtime execution envelope
- release-aware dispatch
- evaluation-aware promotion
- workflow compilation for planning agents
- native tool-runtime integration for all side-effecting actions

### Remove or reduce

- product-local schema drift in catalogs
- runtime assumptions encoded only in comments/docs

## 14.2 Data Cloud

### Keep

- durable registry role
- context gateway
- autonomy controller
- policy/config compiler
- plugin-driven persistence

### Add

- release registry
- evaluation registry
- memory registry
- signed artifact metadata
- retrieval-quality and memory-governance APIs
- rollout state storage

### Make explicit

Data Cloud is the **durable brain and evidence plane**, not the main live execution runtime.

## 14.3 Audio-Video

### Keep

- service mesh design
- model registry/store
- multimodal services
- desktop orchestration surfaces

### Add

- `audio-video` capability descriptors in the agent catalog
- tool adapters for STT/TTS/vision/multimodal operations
- optional `planning` or `composite` domain agents for media workflows
- media memory artifact support in Data Cloud
- cross-product invocation via standardized tool and capability contracts

### Do not do

- do not clone AEP runtime internals into Audio-Video
- do not create a second incompatible tool/agent taxonomy

## 15. Concrete work plan

## Phase 0: Normalize truth sources

**Why:** Without this, every later refactor will keep inheriting confusion.

**Do:**

- update `docs/SHARED_LIBRARY_REGISTRY.md`
- update `docs/adr/ADR-001-typed-agent-framework.md`
- update `docs/agent-system/README.md`
- add a single "current ownership" matrix for platform/AEP/Data Cloud/Audio-Video

**Where:**

- `docs/SHARED_LIBRARY_REGISTRY.md`
- `docs/adr/ADR-001-typed-agent-framework.md`
- `docs/agent-system/README.md`

## Phase 1: Canonicalize enums and spec storage

**Why:** This is the smallest change with the largest long-term payoff.

**Do:**

- define one canonical enum mapping in `agent-core`
- make loaders accept aliases but store canonical values
- add validation failures for unsupported mixed vocabulary
- version the canonical `AgentSpec` output explicitly

**Where:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/*`
- `platform/java/agent-core/.../framework/spec/*`
- `platform/java/agent-core/.../framework/loader/*`
- `platform/agent-catalog/catalog-schema.yaml`

## Phase 2: Introduce release artifacts

**Why:** Definitions alone are not deployable operating units.

**Do:**

- add `AgentRelease`, `PolicyPack`, `EvaluationPack`, `MemoryContract` models
- persist them in Data Cloud
- tie AEP materialization to release IDs, not only raw definitions

**Where:**

- `platform/java/agent-core`
- `products/data-cloud/agent-registry`
- `products/aep/aep-central-runtime`

## Phase 3: Unify tool contracts

**Why:** Tool execution is where governance, safety, interoperability, and modern agent standards meet.

**Do:**

- add `ToolContract` and `ToolExecutionResult`
- standardize action classes and approval metadata
- add MCP-aligned adapter interfaces
- route all side-effecting tool calls through `tool-runtime`

**Where:**

- `platform/java/agent-core/.../framework/tools`
- `platform/java/tool-runtime`
- `products/aep/aep-agent-runtime`

## Phase 4: Make planning compile into durable workflows

**Why:** Durable, inspectable planning is more valuable than planner-specific private orchestration logic.

**Do:**

- add planner-to-workflow compilation interfaces
- store plan graphs and execution lineage
- standardize pause/resume/HITL injection

**Where:**

- `platform/java/workflow`
- `platform/java/agent-core/.../planning`
- `products/aep/orchestrator`

## Phase 5: Turn Data Cloud into the evidence plane

**Why:** Durable memory, evaluation, rollout, and registry data should live together.

**Do:**

- extend registry persistence schema
- add memory/eval/release indices
- expose release and memory governance queries
- add retrieval-quality and promotion evidence storage

**Where:**

- `products/data-cloud/agent-registry`
- `products/data-cloud/platform-api`
- `products/data-cloud/platform-config`
- `products/data-cloud/platform-analytics`

## Phase 6: Make observability first-class

**Why:** You cannot govern or improve what you cannot inspect consistently.

**Do:**

- define internal agent semantic conventions
- instrument runtime phases consistently
- dual-emit OTel-aligned GenAI spans where practical
- attach release ID and policy pack version to every traceable run

**Where:**

- `platform/java/observability`
- `products/aep/aep-agent-runtime`
- `products/data-cloud/platform-analytics`
- `platform/java/tool-runtime`

## Phase 7: Bring Audio-Video into the system as a domain capability provider

**Why:** Multimodal execution should be part of the unified system, not an island.

**Do:**

- define Audio-Video capability descriptors
- add tool adapters for service invocation
- add optional orchestrator agents for common media workflows
- define media artifact memory contracts

**Where:**

- `products/audio-video/modules/*`
- `products/audio-video/libs/common`
- `platform/agent-catalog` or product-local catalogs
- `products/data-cloud` memory/index layers

## 16. Non-goals

This blueprint does **not** recommend:

- a new global shared mega-runtime module
- rewriting AEP to chase a trendy external framework
- forcing every product to use every advanced runtime feature
- replacing platform-kernel with a second registry stack
- letting experimental probabilistic agents bypass deterministic authority and governance

## 17. External standards and current alignment notes

These are the external standards and platform directions most worth aligning to as of **2026-04-06**:

1. **Model Context Protocol, protocol revision 2025-03-26**
   - useful for tool/resource/prompt interoperability
   - relevant because the repo already needs a better remote-tool contract

2. **OpenAI Responses API + function calling guidance**
   - relevant because modern agent runtimes increasingly normalize around schema-driven tool calling and structured outputs
   - useful for provider adapters and strict tool schemas

3. **OpenTelemetry semantic conventions**
   - use stable OTel base conventions now
   - GenAI conventions are still under development, so treat them as optional dual-emission rather than your sole internal schema

4. **OPA bundle model**
   - useful for distributable, reloadable governance packs

5. **Sigstore**
   - useful for signing and verifying release bundles, attestations, and deployable artifacts

6. **OpenFeature**
   - useful as the conceptual model for rollout-provider abstraction and canary/shadow gating
7. **NIST AI RMF and the Generative AI Profile**
   - useful for framing governed AI lifecycle work as mapped risk-management functions rather than ad hoc controls
   - especially relevant for release gates, evaluation, monitoring, and incident response

8. **NIST Privacy Framework**
   - useful for translating privacy requirements into identifiable engineering control categories across data access, retention, and deletion

9. **NIST Explainable AI principles**
   - useful for setting the minimum bar for meaningful explanation artifacts rather than vague "reasoning summaries"

10. **OWASP guidance for GenAI and agentic applications**
   - useful for practical threat modeling around prompt injection, tool abuse, data leakage, memory poisoning, and over-privileged agent workflows

## 18. Bottom line

The repo is already much closer to a strong agent platform than it may feel from the documentation. The main job now is not invention. It is **consolidation of authority**:

- one canonical spec vocabulary
- one release model
- one advanced runtime owner
- one durable evidence plane
- one tool governance boundary
- one telemetry vocabulary

If you do that, the existing architecture can evolve into a highly effective, extensible, and future-ready agent system without another destabilizing rewrite.

## 19. Agent as a Dynamically Pluggable and Swappable Artifact

The current system treats agents as statically registered implementations. An agent exists if its `AgentLogicProvider` is on the classpath and registered at startup. This is insufficient for a production platform that needs:

- Hot-reload of new agent versions without runtime restart
- Canary activation of a new agent alongside the current one
- Zero-downtime swap during version upgrade (a `VERSION_UPGRADE` handoff)
- Isolation of agent classloaders so incompatible dependency versions do not collide
- Revocation of a misbehaving agent without taking down the whole runtime
- Discovery of what any given agent is capable of before wiring it into an execution plan

The infrastructure in `platform-kernel` (`KernelPluginRuntimeManager`) already supports JAR-based hot-reload with classloader isolation. The gap is that it has no agent-specific semantics: it does not understand capability declarations, interaction modes, supervision roles, handoff protocols, or the phased swap contract. This section defines those missing pieces.

### 19.1 The AgentPackage — Standard Deployable Artifact

An **AgentPackage** is the atomic deployable unit for an agent in the Ghatana runtime. It is a signed JAR (or JAR-equivalent bundle) that contains:

1. The agent implementation classes
2. A `META-INF/agent-manifest.yaml` — the `AgentCapabilityManifest`
3. A Sigstore attestation bundle (linked via `AgentRelease.signingReference`)
4. A `META-INF/MANIFEST.MF` extension: `Agent-Main-Class`, `Agent-Id`, `Agent-Version`

The package is versioned and signed at the `AgentRelease` boundary (from Section 7.4). The runtime accepts a package only if:

- The Sigstore attestation verifies against the repo's trust root
- The `agentReleaseId` inside the manifest matches a `VALIDATED`, `SHADOW`, `CANARY`, or `ACTIVE` record in the release registry (Section 9.2)
- The declared `compatibleRuntimeVersions` include the current AEP runtime version

### 19.2 AgentCapabilityManifest — What the Agent Declares It Can Do

The `AgentCapabilityManifest` is the machine-readable, version-stable declaration of everything an agent is capable of at runtime. It lives inside the `AgentPackage` and is loaded before any agent code is executed.

```yaml
# META-INF/agent-manifest.yaml
agentId: "fraud-detector"
agentVersion: "2.1.0"
agentReleaseId: "rel-uuid-here"
mainClass: "com.example.FraudDetectorAgent"

# Interaction modes this agent can participate in
interactionModes:
  - REQUEST_RESPONSE
  - ASYNC_REQUEST
  - EVENT_DRIVEN

# Composition roles
compositionRoles:
  - FOLLOWER
  - VOTER

# Supervision
supervisionRoles:
  - SUPERVISEE
supervisionStrategy: RESTART
maxRestarts: 3
restartWindowSeconds: 60

# Handoff
handoffCapability: BIDIRECTIONAL
handoffReasons:
  - COMPLETION
  - FAILURE_RECOVERY
  - VERSION_UPGRADE

# Repetition
maxIterations: 10
maxRecursionDepth: 5
maxRetries: 3
retryStrategy: EXPONENTIAL_BACKOFF

# Self-learning
learningLevel: L3 # matches LearningEngine.LearningLevel
emitsLearningSignals: true
receivesCrossAgentSignals: false

# Context sharing
contextSharingScope: WITHIN_SESSION
sharesContextEntries:
  - "fraud-score"
  - "risk-bucket"

# Tool contracts declared
declaredToolContractIds:
  - "fraud-detector:score-transaction:v2"
  - "fraud-detector:flag-entity:v1"
```

This manifest must be validated by `AgentCapabilityManifestValidator` before the package is admitted into the runtime. Fields that are absent default to the most restrictive safe value (e.g., `handoffCapability: NONE`, `contextSharingScope: NONE`, `learningLevel: L0`).

### 19.3 AgentPackageLoader — Agent-Specific Loading on the Kernel Substrate

`AgentPackageLoader` is the agent-specific facade over `KernelPluginRuntimeManager`. It:

1. Accepts an `AgentPackage` (a `Path` to the signed JAR or a remote URI)
2. Verifies the Sigstore attestation
3. Validates the `AgentRelease` state in Data Cloud
4. Delegates to `KernelPluginRuntimeManager.loadPlugin(...)` for classloader isolation
5. Reads and validates `AgentCapabilityManifest` from the loaded classloader
6. Registers the agent and its capability manifest with `AgentRegistry` and `KernelRegistry`
7. Emits an `AGENT_PACKAGE_LOADED` structured event

Unloading (`AgentPackageLoader.unload(agentId)`) performs the reverse and transitions the release state to `DEPRECATED` or `RETIRED` as appropriate.

### 19.4 Zero-Downtime Hot-Swap via AgentSwapCoordinator

Hot-swap is the most complex pluggability operation. The `AgentSwapCoordinator` orchestrates:

```
Phase 1 — Load New:
  Load v2 package alongside v1 (both active in classloader registry)
  Validate v2 manifest, capabilities, and release state
  Set v2 release state to CANARY

Phase 2 — Drain In-Flight:
  Stop routing new requests to v1
  Wait for all in-flight v1 requests to complete (or timeout)
  v1 remains callable for timeout duration as fallback

Phase 3 — Handoff:
  Issue AgentHandoff(source=v1, target=v2, reason=VERSION_UPGRADE)
  Transfer task-state memory snapshots from v1 to v2 namespace
  Transfer shared context entries owned by v1 to v2

Phase 4 — Cut-Over:
  Set v2 release state to ACTIVE
  Set v1 release state to DEPRECATED
  Remove v1 classloader after grace period
  Emit AGENT_SWAP_COMPLETE event
```

The swap must emit structured telemetry at each phase boundary and must be resumable (if the coordinator crashes during Phase 3, it can restart from Phase 3 state persisted in Data Cloud).

### 19.5 Capability Discovery via KernelRegistry

Once an `AgentPackage` is loaded, its `AgentCapabilityManifest` is registered in the `KernelRegistry` under the capability ID `"agent:{agentId}"`. The AEP central runtime and the `DelegationManager` use this registry to:

- Discover agents that support a given `InteractionMode`
- Filter agents by `CompositionRole` before forming a composite
- Verify that a target agent supports `RECEIVE` handoff before initiating a transfer
- Check `learningLevel` before routing learning signals

This makes the kernel registry the **live, queryable catalog of what every agent can do right now** — not just a static list of registered implementations.

---

## 20. Comprehensive Inter-Agent Interaction and Protocol System

This section defines concrete, machine-enforceable contracts for every major interaction pattern that agents participate in. All contracts live in `platform/java/agent-core` so that AEP, Data Cloud, audio-video, and all products share the same vocabulary.

The existing codebase has partial implementations of several of these patterns:

| Pattern       | Existing                                                                                                 | Gap                                                                 |
| ------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Delegation    | `DelegationManager`, `DelegationRequest`, `DelegationStatus`                                             | No correlation envelope, no timeout contract                        |
| Composition   | `OrchestrationStrategy`, `SequentialOrchestration`, `ParallelOrchestration`, `HierarchicalOrchestration` | No voting, no fan-out/scatter-gather, no `CompositionPolicy` record |
| Communication | `ConversationManager`, `Message`                                                                         | No typed message envelope, no broadcast, no streaming               |
| Self-learning | `LearningEngine` (L0-L5), `LearningOutcome`                                                              | No cross-agent `LearningSignal` SPI, no signal routing              |
| Context       | `AgentContext`                                                                                           | No `SharedContext` with scoped propagation across agents            |
| Supervision   | None                                                                                                     | Fully missing                                                       |
| Handoff       | None                                                                                                     | Fully missing                                                       |
| Repetition    | None                                                                                                     | Fully missing                                                       |
| Pluggability  | `AgentLogicProvider` SPI, `KernelPluginRuntimeManager`                                                   | No agent-level `AgentCapabilityManifest` or swap protocol           |

### 20.1 Interaction Modes

Every agent declares which interaction modes it supports in its `AgentCapabilityManifest`. The runtime validates that a requested mode is supported before attempting communication.

```
REQUEST_RESPONSE   — Synchronous one-to-one call with a typed result.
                     Caller blocks on Promise. Governed by latencySla from AgentDescriptor.

ASYNC_REQUEST      — Asynchronous one-to-one call with correlation ID.
                     Caller receives an invocationId immediately.
                     Result is delivered via callback or polling.

STREAMING          — Long-running call producing a sequence of partial results.
                     Caller receives an AsyncStreamProducer<O>.
                     Used for token-by-token LLM output, live dashboards, etc.

EVENT_DRIVEN       — Agent subscribes to typed events and reacts.
                     Publisher does not wait for result.
                     Governed by back-pressure and dead-letter policies.

BROADCAST          — One-to-many notification with no individual result expected.
                     Subject to fan-out limits in the PolicyPack.
```

The `AgentInteractionProtocol` interface is the uniform entry point into an agent for all modes:

```java
public interface AgentInteractionProtocol {

    /** Synchronous or async request-response. Mode determined by AgentRequest.mode. */
    Promise<AgentResponse> request(AgentRequest request);

    /** Streaming invocation — returns a lazy stream of partial results. */
    Promise<AsyncStreamProducer<Object>> stream(AgentRequest request);

    /** Publish a typed event to this agent (EVENT_DRIVEN mode). */
    Promise<Void> publish(AgentEvent event);

    /** Returns the modes this agent currently supports. */
    Set<InteractionMode> supportedModes();
}
```

All inter-agent messages carry a standard envelope:

```java
public record AgentMessage(
    String messageId,           // UUID
    String correlationId,       // trace correlation — same across entire conversation
    String sessionId,           // conversation session (nullable if stateless)
    String sourceAgentId,
    String targetAgentId,       // null for broadcast
    InteractionMode mode,
    Object payload,
    Map<String, String> headers,
    Instant sentAt,
    Duration ttl               // null = no expiry
) {}
```

### 20.2 Supervision Contract

Supervision defines **what happens when an agent fails**. It is distinct from retry (which is about repeating the same operation) — supervision is about structural failure management at the agent-instance level.

```java
public record SupervisionContract(
    String supervisorAgentId,
    Set<String> superviseeAgentIds,
    SupervisionStrategy strategy,
    int maxRestarts,
    Duration restartWindow,
    Set<String> supervisedFailureClasses,  // FQCN of exceptions to handle
    boolean escalateUnhandledFailures,     // if true, failures not in supervisedFailureClasses escalate
    String escalationTargetAgentId        // nullable — if null, escalate to platform alert
) {}

public enum SupervisionStrategy {
    /** Restart the failed supervisee. */
    RESTART,
    /** Restart only the failed supervisee's current task, not the agent itself. */
    RESTART_TASK,
    /** Escalate the failure to the supervisor's supervisor or platform. */
    ESCALATE,
    /** Isolate the failed agent (stop routing to it) but keep others running. */
    ISOLATE,
    /** Log the failure and continue — for non-critical supervisees only. */
    LOG_AND_CONTINUE,
    /** Shut down all supervisees when one fails. Used for atomically coupled composites. */
    SHUTDOWN_ALL
}
```

A supervisor registers `SupervisionContract`s with the `SupervisionRegistry` (a new sub-registry in `platform-kernel`). The `GovernedAgentDispatcher` checks the registry before routing to any agent and enforces supervision decisions on failure.

Watchdog supervision (peer-to-peer health monitoring) is expressed as a `SupervisionContract` where `supervisorAgentId` has role `PEER_WATCHDOG` — it does not coordinate the work but observes and escalates.

### 20.3 Composition Contract

Composition defines **how agents work together** to produce a joint result. The existing `OrchestrationStrategy` provides SEQUENTIAL, PARALLEL, and HIERARCHICAL. The expanded set:

```java
public record CompositionPolicy(
    String compositionId,
    CompositionPattern pattern,
    List<String> memberAgentIds,        // ordered for SEQUENTIAL/PIPELINE, unordered otherwise
    Map<String, CompositionRole> roles, // agentId → role
    VotingPolicy votingPolicy,          // only relevant for VOTING pattern
    AggregationStrategy aggregation,    // how results are combined for FAN_OUT_FAN_IN / SCATTER_GATHER
    boolean failFast,                   // abort all if any member fails
    Duration compositionTimeout,
    int maxConcurrentMembers           // for PARALLEL/FAN_OUT: cap on concurrent execution
) {}

public enum CompositionPattern {
    /** Each agent processes the result of the previous. Output of agent[n] is input of agent[n+1]. */
    SEQUENTIAL,
    /** All agents process the same input concurrently. Results aggregated. */
    PARALLEL,
    /** Leader decomposes the task, distributes to followers, aggregates their results. */
    FAN_OUT_FAN_IN,
    /** All agents vote on the same input. Majority/unanimous/weighted decision applied. */
    VOTING,
    /** Agents are chained with typed transformations between them. */
    PIPELINE,
    /** Input is sharded across agents (scatter); their results are merged (gather). */
    SCATTER_GATHER
}

public enum CompositionRole {
    LEADER,            // coordinates FAN_OUT_FAN_IN or HIERARCHICAL
    FOLLOWER,          // executes tasks assigned by LEADER
    PEER,              // equals in PARALLEL / VOTING / SCATTER_GATHER
    SEQUENCED,         // member of a SEQUENTIAL or PIPELINE chain
    VOTER,             // participates in VOTING
    AGGREGATOR         // collects and reduces results (can be a dedicated agent or the LEADER)
}

public enum VotingPolicy {
    MAJORITY,                     // > 50% agreement
    UNANIMOUS,                    // all agree
    WEIGHTED,                     // each voter has a weight; result if sum(weights) > threshold
    FIRST_EXCEEDING_THRESHOLD     // first agent to produce a result exceeding confidence threshold wins
}

public enum AggregationStrategy {
    TAKE_ALL,          // return all results
    TAKE_FIRST,        // return the first successful result
    MERGE,             // merge maps/lists by key
    REDUCE_BY_CONFIDENCE,  // return the result with the highest confidence score
    CUSTOM             // use a registered AggregationFunction SPI
}
```

### 20.4 Handoff Protocol

A **handoff** is the structured transfer of a task and its context from one agent to another. Handoffs are distinct from delegation (which is ad hoc task routing) because they:

- Transfer task state (persisted checkpoints, partial results)
- Transfer working memory and session context
- Maintain trace continuity (same `correlationId`)
- Are governed (a `WRITE_IRREVERSIBLE` action class in the `ActionClass` taxonomy)
- Can be triggered by the agent, the runtime, or a version upgrade

```java
public record AgentHandoff(
    String handoffId,
    String correlationId,          // must match the in-flight execution's correlationId
    String sourceAgentId,
    String sourceReleaseId,
    String targetAgentId,
    String targetReleaseId,
    HandoffReason reason,
    AgentContextSnapshot contextSnapshot,
    Object taskState,              // serialized resumable state (may be null for COMPLETION handoffs)
    Set<String> transferredCapabilities, // capabilities the target must have to accept
    HandoffAcknowledgement acknowledgement, // NONE (fire-and-forget), REQUIRED (block until ACK)
    Instant initiatedAt,
    Duration handoffTimeout
) {}

public enum HandoffReason {
    COMPLETION,        // agent finished its portion; pass to next in chain
    DELEGATION,        // ad hoc delegation to a more appropriate specialist
    FAILURE_RECOVERY,  // source failed; target takes over
    SPECIALIZATION,    // source recognized it cannot handle this; target is more capable
    LOAD_BALANCE,      // source is overloaded; target has capacity
    VERSION_UPGRADE    // zero-downtime hot-swap (Section 19.4)
}

public record AgentContextSnapshot(
    String snapshotId,
    String sourceAgentId,
    String sessionId,
    String correlationId,
    Map<String, Object> workingMemory,      // current turn's in-memory state
    Map<String, Object> sharedContextRefs,  // IDs of SharedContext records to transfer ownership of
    List<String> episodicMemoryIds,         // IDs of episodic records the target may read
    Map<String, Object> planState,          // serialized PlanGraph state if the agent was planning
    Instant capturedAt
) {}

public enum HandoffAcknowledgement { NONE, REQUIRED }
```

The `HandoffCoordinator` (a new service in `products/aep/aep-agent-runtime`) orchestrates the multi-step handoff protocol:

1. Validate target agent accepts `RECEIVE` handoff capability
2. Persist `AgentHandoff` record (action class: `WRITE_IRREVERSIBLE`)
3. Snapshot context and task state into Data Cloud
4. Notify target agent via `AgentInteractionProtocol.publish(handoffEvent)`
5. Wait for `HandoffAcknowledgement` if required
6. Deactivate source agent's task lease
7. Emit `AGENT_HANDOFF_COMPLETE` telemetry span

### 20.5 Repetition and Loop Governance

Repetition governs **controlled iteration** within an agent's execution. This is distinct from supervision (which governs structural failure) and from retry (which is a resilience concern). Repetition answers: "when should this agent stop looping?"

```java
public record RepetitionPolicy(
    int maxIterations,             // hard cap on loop count (0 = unlimited, requires explicit stop)
    int maxRecursionDepth,         // hard cap on recursive self-invocation depth
    int maxRetries,                // max retries on transient failures before escalating
    Duration retryBackoffBase,     // base duration for backoff computation
    RetryStrategy retryStrategy,
    Set<ActionClass> retryableActionClasses, // only retry for these action classes
    TerminationCondition terminationCondition,
    double convergenceThreshold    // for CONVERGENCE termination: stop when result delta < threshold
) {}

public enum RetryStrategy {
    IMMEDIATE,            // retry right away
    LINEAR_BACKOFF,       // backoff = n * retryBackoffBase
    EXPONENTIAL_BACKOFF,  // backoff = 2^n * retryBackoffBase (with jitter)
    CIRCUIT_BREAK         // stop retrying; open circuit for cooling period
}

public enum TerminationCondition {
    MAX_ITERATIONS,      // stop when iteration count hits maxIterations
    CONVERGENCE,         // stop when output delta < convergenceThreshold
    EXPLICIT_STOP,       // stop only on agent-emitted STOP signal
    TIMEOUT,             // stop when compositionTimeout is exceeded
    POLICY_GATE          // stop when a policy evaluation returns DENY
}
```

The `RepetitionGovernor` (implemented in `platform/java/tool-runtime`) tracks iteration counts per `correlationId` and enforces the policy. On violation:

- Iteration limit: throw `RepetitionLimitExceededException`, trigger `ESCALATE` in supervision
- Recursion limit: throw `RecursionDepthExceededException`, isolate the agent
- Retry limit: apply `CircuitBreaker`, emit `CIRCUIT_OPEN` metric

### 20.6 Self-Learning Signal Contract

The existing `LearningEngine` (L0-L5) runs a local reflect pass against an agent's own `MemoryStore`. The gap is **cross-agent and cross-session learning signals** — a structured way for agents, users, and external evaluators to emit typed feedback that the learning system consumes.

```java
public record LearningSignal(
    String signalId,
    String correlationId,          // links signal to the execution that produced it
    String emittingAgentId,        // who emitted the signal (agent or platform)
    String targetAgentId,          // which agent should learn from this signal
    String agentReleaseId,         // release version being evaluated
    LearningSignalType type,
    Object observation,            // what was observed
    Object expectedOutcome,        // what the expected result was (nullable)
    Object actualOutcome,          // what actually happened
    double confidence,             // 0.0–1.0: signal reliability
    Map<String, Object> features,  // context features at signal time (for ML)
    LearningSignalSource source,   // who originated the signal
    Instant emittedAt
) {}

public enum LearningSignalType {
    POSITIVE_REINFORCEMENT,  // agent did the right thing; reinforce
    NEGATIVE_REINFORCEMENT,  // agent should not have done this; penalize
    CORRECTION,              // here is what the agent should have done instead
    OBSERVATION,             // neutral factual observation; no reward/penalty
    PREFERENCE,              // human indicated preference between two outcomes
    FAILURE_SIGNAL,          // agent failed on this input; provide context for analysis
    SUCCESS_SIGNAL           // agent succeeded; record as positive evidence
}

public enum LearningSignalSource {
    AGENT_SELF,             // the agent emitted its own learning signal (reflection)
    PEER_AGENT,             // another agent in the composition evaluated this agent
    HUMAN_FEEDBACK,         // a human operator provided feedback
    AUTOMATED_EVAL,         // an EvaluationPack gate ran and produced a signal
    PLATFORM_MONITOR        // the platform observed a metric crossing a threshold
}
```

The `LearningSignalRouter` (new, in `platform/java/agent-core`) receives signals and:

1. Validates signal against the target agent's `RepetitionPolicy` and `PolicyPack`
2. Routes to the target agent's `LearningEngine` for batch processing in the next reflect cycle
3. If `LearningSignalSource == HUMAN_FEEDBACK`: promote to `PromotionEvidence` immediately (Phase 5)
4. Emits `ghatana.agent.learning.signal_received` telemetry

Cross-agent signal routing is governed: a `PEER_AGENT` can only emit signals targeting agents it was in a `CompositionPolicy` with, or agents it supervised.

### 20.7 Context Sharing Contract

The existing `AgentContext` is per-invocation and not shared across agents. `SharedContext` adds scoped, explicitly owned, multi-agent readable state.

```java
public record SharedContext(
    String contextId,
    String ownerAgentId,            // the agent that created and owns this context
    String sessionId,
    String tenantId,
    ContextSharingScope scope,
    Map<String, SharedContextEntry> entries,
    Set<String> authorizedAgentIds, // empty = all agents within scope
    Instant createdAt,
    Instant expiresAt,              // null = session-scoped (expires when session ends)
    boolean immutable               // if true, only the owner can write new entries
) {}

public record SharedContextEntry(
    String key,
    Object value,
    String writtenByAgentId,
    Instant writtenAt,
    MergeStrategy mergeStrategy    // how conflicts are resolved on concurrent writes
) {}

public enum ContextSharingScope {
    NONE,             // not shared (default, same as per-invocation AgentContext)
    WITHIN_SESSION,   // shared among agents in the same session
    WITHIN_TENANT,    // shared among all agents for the same tenant
    WITHIN_COMPOSITION, // shared only among agents in the same CompositionPolicy
    GLOBAL            // shared across all tenants (platform-level, highly restricted)
}

public enum MergeStrategy {
    LAST_WRITER_WINS,
    FIRST_WRITER_WINS,
    OWNER_ONLY,       // only the ownerAgentId can update this key
    APPEND_LIST,      // value is treated as a list; new writes append
    MERGE_MAP         // value is treated as a map; new writes deep-merge
}

public interface SharedContextRepository {
    Promise<SharedContext> publish(SharedContext context);
    Promise<Optional<SharedContext>> findById(String contextId);
    Promise<List<SharedContext>> findBySession(String sessionId, ContextSharingScope scope);
    Promise<SharedContextEntry> writeEntry(String contextId, SharedContextEntry entry, String writingAgentId);
    Promise<Void> revoke(String contextId, String requestingAgentId);
    Promise<Void> transferOwnership(String contextId, String newOwnerAgentId);
}
```

`AgentContext` (the per-invocation context) gains a `readSharedContext(String contextId)` method that is a read-through from `SharedContextRepository`, subject to scope and authorization checks.

### 20.8 The Universal Agent Runtime Contract

All the contracts above are coordinated by the **Universal Agent Runtime Contract** — the complete set of obligations an agent must satisfy to participate in the platform at full capability. An agent that does not implement all parts still works — it simply operates with a reduced capability profile.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Universal Agent Runtime Contract                      │
│                                                                         │
│  AgentCapabilityManifest  (declares what this agent can do)             │
│    ├── Interaction Modes   (§20.1)  — REQUEST_RESPONSE, ASYNC, etc.    │
│    ├── Supervision Roles   (§20.2)  — SUPERVISOR, SUPERVISEE, WATCHDOG │
│    ├── Composition Roles   (§20.3)  — LEADER, FOLLOWER, VOTER, etc.    │
│    ├── Handoff Capability  (§20.4)  — NONE, SEND_ONLY, RECEIVE_ONLY,  │
│    │                                   BIDIRECTIONAL                    │
│    ├── Repetition Policy   (§20.5)  — maxIterations, strategy, etc.    │
│    ├── Learning Level      (§20.6)  — L0–L5; emits/receives signals    │
│    └── Context Scope       (§20.7)  — scope of shared context access   │
│                                                                         │
│  AgentPackage              (the deployable artifact)                    │
│    ├── Signed JAR with isolated classloader                             │
│    ├── AgentCapabilityManifest (above)                                  │
│    └── AgentRelease reference (links to release lifecycle §9.2)         │
│                                                                         │
│  Runtime Enforcement                                                    │
│    ├── AgentPackageLoader  — load/unload/hot-reload                     │
│    ├── AgentSwapCoordinator — zero-downtime hot-swap                    │
│    ├── SupervisionRegistry — supervision graph management               │
│    ├── HandoffCoordinator  — handoff protocol execution                 │
│    ├── RepetitionGovernor  — loop/recursion/retry enforcement           │
│    ├── LearningSignalRouter — routes cross-agent signals                │
│    └── SharedContextRepository — scoped context store                  │
└─────────────────────────────────────────────────────────────────────────┘
```

**The key rule:** Any runtime component (AEP dispatcher, orchestrator, DelegationManager) that interacts with an agent must read its `AgentCapabilityManifest` from the `KernelRegistry` before initiating any interaction. If the agent does not declare a required capability, the operation is rejected with a structured error, not a silent null or NPE.

### 20.9 Interaction Pattern Decision Guide

Use this table when designing a new agent interaction:

| You want to...                                    | Use this pattern                    | Key types                                    |
| ------------------------------------------------- | ----------------------------------- | -------------------------------------------- |
| Ask one agent to do something, wait for result    | `REQUEST_RESPONSE`                  | `AgentRequest`, `AgentResponse`              |
| Ask one agent to do something, don't block        | `ASYNC_REQUEST`                     | `AgentRequest` + correlation ID              |
| Get live output as it is produced                 | `STREAMING`                         | `AgentRequest`, `AsyncStreamProducer<O>`     |
| Notify agents of an event, no reply needed        | `EVENT_DRIVEN` / `BROADCAST`        | `AgentEvent`                                 |
| Run agents one after another (output → input)     | `SEQUENTIAL` / `PIPELINE`           | `CompositionPolicy`                          |
| Run agents simultaneously on same input           | `PARALLEL`                          | `CompositionPolicy`                          |
| Distribute a task, collect all results            | `FAN_OUT_FAN_IN` / `SCATTER_GATHER` | `CompositionPolicy`                          |
| Get a consensus decision from multiple agents     | `VOTING`                            | `CompositionPolicy`, `VotingPolicy`          |
| Transfer a task to a more capable agent           | `DELEGATION` (DelegationManager)    | `DelegationRequest`                          |
| Transfer task + state to another agent (graceful) | `HANDOFF`                           | `AgentHandoff`, `HandoffCoordinator`         |
| Replace an agent version without downtime         | `VERSION_UPGRADE` handoff           | `AgentSwapCoordinator`                       |
| Monitor and manage another agent's failures       | `SUPERVISION`                       | `SupervisionContract`, `SupervisionRegistry` |
| Control how many times an agent repeats           | `REPETITION`                        | `RepetitionPolicy`, `RepetitionGovernor`     |
| Share state across agents in a session            | `CONTEXT_SHARING`                   | `SharedContext`, `SharedContextRepository`   |
| Teach an agent from another agent's outcome       | `LEARNING_SIGNAL`                   | `LearningSignal`, `LearningSignalRouter`     |

---

## Sources

- Model Context Protocol specification overview and tools:
  - https://modelcontextprotocol.io/specification/2025-03-26/basic/index
  - https://modelcontextprotocol.io/specification/2025-03-26/server/tools
- OpenAI Responses API and function calling:
  - https://platform.openai.com/docs/api-reference/responses/retrieve
  - https://platform.openai.com/docs/guides/function-calling
  - https://help.openai.com/en/articles/8555517-function-calling-in-the-openai-api
- OpenTelemetry semantic conventions:
  - https://opentelemetry.io/docs/specs/semconv/
  - https://opentelemetry.io/docs/specs/semconv/gen-ai/
  - https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/
  - https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-metrics/
- Open Policy Agent bundles:
  - https://www.openpolicyagent.org/docs/latest/management-bundles/
- Sigstore:
  - https://docs.sigstore.dev/
  - https://docs.sigstore.dev/cosign/verifying/verify/
- OpenFeature:
  - https://openfeature.dev/specification/
  - https://openfeature.dev/docs/reference/concepts/provider
- NIST AI RMF, Generative AI profile, Privacy Framework, and Explainable AI:
  - https://www.nist.gov/itl/ai-risk-management-framework
  - https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
  - https://www.nist.gov/privacy-framework
  - https://www.nist.gov/publications/four-principles-explainable-artificial-intelligence
- OWASP GenAI and agentic security guidance:
  - https://genai.owasp.org/
  - https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/
