# Agent System Modernization Blueprint

**Date:** 2026-04-06  
**Status:** Proposed architecture and migration blueprint  
**Primary scope:** `platform`, `platform-kernel`, `products/aep`, `products/data-cloud`, `products/audio-video`  
**Focus:** framework, runtime, registry, specification, governance, observability, extensibility

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

## 18. Bottom line

The repo is already much closer to a strong agent platform than it may feel from the documentation. The main job now is not invention. It is **consolidation of authority**:

- one canonical spec vocabulary
- one release model
- one advanced runtime owner
- one durable evidence plane
- one tool governance boundary
- one telemetry vocabulary

If you do that, the existing architecture can evolve into a highly effective, extensible, and future-ready agent system without another destabilizing rewrite.

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
