# Platform Java Module Audit

Date: 2026-03-23
Scope: `/Users/samujjwal/Development/ghatana/platform/java`

Revision note: the deeper cross-product event/agent ownership review in [`/Users/samujjwal/Development/ghatana/docs/PLATFORM_JAVA_MODULE_AUDIT_REPORT_2026-03-24.md`](/Users/samujjwal/Development/ghatana/docs/PLATFORM_JAVA_MODULE_AUDIT_REPORT_2026-03-24.md) supersedes the event/agent architecture plan in this document.

## Executive Summary

`platform/java` currently contains 23 top-level directories, but only 22 are real Gradle modules included by the root build. One directory, `ai-api`, is an on-disk orphan: it has no `build.gradle(.kts)`, no sources, and no include in [`/Users/samujjwal/Development/ghatana/settings.gradle.kts`](/Users/samujjwal/Development/ghatana/settings.gradle.kts).

The platform layer is actively used overall, but it has three structural issues:

1. Several modules are clearly foundational and heavily used (`core`, `testing`, `observability`, `domain`, `http`, `database`), so they should be preserved.
2. Several modules are thin facades or partially consolidated boundaries (`kernel-capabilities`, `agent-registry`, `connectors`, `distributed-cache`, `audit`) and should be simplified or merged.
3. There are multiple duplicate concepts and even duplicate type names across modules, including duplicate packages inside `kernel` itself. This is the highest-value cleanup area because it creates ambiguity even when the modules are technically “used.”

## Method

This audit used:

- Root Gradle inclusion from [`/Users/samujjwal/Development/ghatana/settings.gradle.kts`](/Users/samujjwal/Development/ghatana/settings.gradle.kts)
- Per-module `build.gradle.kts` dependencies
- Source/test file counts under each module
- Repo-wide Gradle dependency references to `:platform:java:*`
- Duplicate filename scan across `platform/java`
- Manual inspection of high-risk modules and consolidation leftovers

Notes:

- “Build consumers” below means other Gradle build files referencing a platform module, excluding the module’s own build file.
- A high build-consumer count means the module is structurally depended on, not necessarily that every public type is healthy.

## Module Inventory

| Module | Included in root build | Sources (main/test) | Build consumers | Assessment |
|---|---:|---:|---:|---|
| `agent-core` | Yes | 166 / 37 | 31 | Keep, but split agent contracts from templating/runtime-facing helpers |
| `agent-registry` | Yes | 5 / 1 | 7 | Very thin; strong merge candidate into `agent-core` unless independent evolution is required |
| `agent-runtime` | Yes | 156 / 4 | 8 | Used, but broad and memory-heavy; probably too much hidden consolidation |
| `ai-api` | No | 0 / 0 | 0 | Remove directory; orphan |
| `ai-integration` | Yes | 52 / 12 | 23 | Keep, but clean up stale submodule leftovers and reduce scope creep |
| `audit` | Yes | 6 / 2 | 26 | Used, but too small for a separate boundary given overlap with observability and governance |
| `config` | Yes | 21 / 3 | 26 | Keep |
| `connectors` | Yes | 15 / 8 | 1 | Very weak adoption; likely merge into `event-cloud` or product-owned modules |
| `core` | Yes | 83 / 23 | 68 | Keep as primary foundation module |
| `database` | Yes | 40 / 16 | 46 | Keep |
| `distributed-cache` | Yes | 6 / 3 | 3 | Review hard; likely too small for a dedicated module today |
| `domain` | Yes | 63 / 11 | 48 | Keep, but reduce duplication with agent/auth/audit models elsewhere |
| `event-cloud` | Yes | 8 / 5 | 18 | Keep if it remains canonical event abstraction; otherwise merge with connectors-facing pieces |
| `governance` | Yes | 26 / 6 | 21 | Keep only if boundary is narrowed; today it overlaps with security and http |
| `http` | Yes | 17 / 7 | 48 | Keep |
| `kernel` | Yes | 83 / 18 | 21 | Keep, but needs internal canonicalization immediately |
| `kernel-capabilities` | Yes | 28 / 0 | 0 | Strongest merge candidate; pure facade/aggregation module today |
| `observability` | Yes | 63 / 15 | 69 | Keep |
| `plugin` | Yes | 37 / 6 | 12 | Keep only if it remains the single plugin abstraction; currently overlaps with `kernel` |
| `runtime` | Yes | 9 / 4 | 12 | Keep, but shrink to pure ActiveJ/runtime concerns |
| `security` | Yes | 63 / 11 | 29 | Keep, but remove overlap with governance/domain/kernel-capabilities |
| `testing` | Yes | 55 / 16 | 105 | Keep; this is the most broadly reused support module |
| `workflow` | Yes | 49 / 15 | 22 | Keep |

## Usage Findings

### Clearly active and should stay

These modules are deeply wired into products and other shared modules:

- `core`
- `testing`
- `observability`
- `domain`
- `http`
- `database`
- `security`
- `config`
- `workflow`
- `ai-integration`
- `agent-core`

These should not be removed. The work here is boundary cleanup, not deletion.

### Thin or weakly justified boundaries

#### `kernel-capabilities`

This module has zero real Gradle consumers while depending on:

- `kernel`
- `security`
- `config`
- `database`
- `observability`
- `audit`
- `event-cloud`
- `core`

It is acting as a convenience umbrella, not a stable architectural boundary. That usually increases ambiguity because callers can bypass meaningful separation by depending on the aggregate.

Recommendation: merge it into `kernel`, or delete it and replace any future need with explicit dependencies.

#### `agent-registry`

This module has only 5 main files and 7 build consumers. It looks more like a specialized subpackage of the agent platform than a standalone module.

Recommendation: merge into `agent-core` unless there is a concrete need for separate release cadence or alternate implementations.

#### `connectors`

This module has only 1 Gradle consumer (`products/aep/aep-registry`) and mixes:

- connector abstractions
- Kafka implementations
- event ingress/egress
- HTTP/webhook source concerns

Recommendation: either:

- merge generic event ingress abstractions into `event-cloud` and `http`, or
- move product-specific connector implementations into the owning products

#### `distributed-cache`

This module has only 6 main files and 3 build consumers. It depends only on `kernel` and provides a Redis-backed cache abstraction.

Recommendation: if cache APIs are truly cross-cutting, fold into `database` or `runtime`; if not, move to the product/platform SDKs that actually need it.

#### `audit`

This module is used, but it contains only 6 main files and already depends on `observability`, `domain`, and `event-cloud`. It currently looks more like a narrow concern packaged as its own module rather than a full platform boundary.

Recommendation: keep the audit API if needed, but consider:

- merging `AuditEvent` and query abstractions into `observability`, or
- treating audit as a subpackage within `governance` if its primary meaning is compliance and accountability

### Orphans and stale consolidation leftovers

#### `ai-api`

`ai-api` exists on disk but:

- is not included in [`/Users/samujjwal/Development/ghatana/settings.gradle.kts`](/Users/samujjwal/Development/ghatana/settings.gradle.kts)
- has no build file
- has no source files

Recommendation: delete the directory.

#### `ai-integration` submodule leftovers

The root module [`/Users/samujjwal/Development/ghatana/platform/java/ai-integration/build.gradle.kts`](/Users/samujjwal/Development/ghatana/platform/java/ai-integration/build.gradle.kts) is the only included Gradle module, but this directory still contains old submodule-style folders such as:

- `gateway/` with its own `build.gradle`
- `serving/` with its own `build.gradle`
- `batch/`
- `evaluation/`
- `learning/`
- `promotion/`
- `testing/`
- `training/`
- `quarantine/`

This is a strong sign of incomplete consolidation. The README is also stale and still describes the old “AI Platform” layout rather than the actual current build shape.

Recommendation:

- decide whether these are meant to be source sets folded into `ai-integration`, or historical leftovers
- delete or migrate the unused standalone `build.gradle` files under `gateway/` and `serving/`
- refresh the README to match the real module structure

## Duplication Findings

The biggest cleanup opportunity is duplication of domain concepts and utility types across modules.

### High-risk duplicate concepts across modules

#### Agent model duplication

- `AgentRegistry.java` exists in both `agent-registry` and `agent-core`
- `AgentInfo.java` exists in both `agent-core` and `domain`
- `AgentSpec.java` exists in both `agent-core` and `domain`
- `AgentMetrics.java` exists in both `agent-core` and `domain`
- `HealthStatus.java` exists in `agent-core`, `domain`, `database`, `plugin`, and `kernel`

Impact:

- unclear canonical ownership of agent contracts
- easy drift between product-facing agent models and runtime/SPI models

Recommendation: choose one canonical package for each agent concept, ideally:

- `agent-core` for runtime/SPI contracts
- `domain` for persistent/shared domain records only if they are materially different

Do not keep two public abstractions with the same business meaning.

#### Auth and RBAC duplication

- `Role.java` exists in `security`, `governance`, `domain`, and `kernel-capabilities`
- `AuthorizationService.java` exists in `security` and `kernel-capabilities`
- `AuthResult.java` exists in `domain` and `kernel-capabilities`
- `User.java` exists in `security` and `domain`

Impact:

- auth behavior is split across policy, domain, kernel capability, and transport boundaries

Recommendation:

- `security` should own executable authentication/authorization behavior
- `governance` should own policy decisions and guardrails
- `domain` should only keep pure product-neutral auth data if truly necessary
- `kernel-capabilities` should not define parallel auth models if the module survives at all

#### Audit duplication

- `AuditEvent.java` exists in both `audit` and `domain`

Recommendation: keep one canonical `AuditEvent` type. If products need projections or persistence forms, name them differently.

#### Validation and utility duplication

- `ValidationResult.java` exists in `core`, `config` twice, and `domain`
- `ConfigurationException.java` exists in `core` and `config`
- `JsonUtils.java` exists in `core` and `kernel`
- `Result.java` exists twice in `core`
- `ErrorCode.java` exists twice in `core`
- `ServiceEndpoint.java` exists twice in `core`
- `RedisCacheConfig.java` exists twice in `database`

Recommendation: canonicalize these immediately. Duplicate infra utility types create low-signal churn and widespread import confusion.

#### Plugin duplication

- `PluginContext.java`, `PluginRegistry.java`, `PluginManifest.java`, and `PluginLoader.java` exist in both `plugin` and `kernel`

Recommendation: either:

- `plugin` owns all plugin abstractions and `kernel` depends on it, or
- `kernel` owns the abstractions and `plugin` becomes implementation-only

Do not keep both as public canonical layers.

### Internal duplication inside `kernel`

This is the sharpest local problem because the duplication is inside one module:

- `com.ghatana.kernel.boundary.BoundaryPolicyResolver`
- `com.ghatana.kernel.policy.BoundaryPolicyResolver`
- `com.ghatana.kernel.contract.ContractRegistry`
- `com.ghatana.kernel.contracts.ContractRegistry`
- `com.ghatana.kernel.contract.ContractValidator`
- `com.ghatana.kernel.contracts.ContractValidator`

These are not harmless overloads. They represent parallel canonicalization attempts with overlapping semantics.

Recommendation: make this the first implementation cleanup inside code.

## Simplification Recommendations

### Recommended target shape

Keep these as canonical platform modules:

- `core`
- `domain`
- `database`
- `http`
- `observability`
- `testing`
- `runtime`
- `config`
- `workflow`
- `event-cloud`
- `security`
- `governance`
- `ai-integration`
- `agent-core`
- `kernel`
- `plugin`

Merge or remove these:

- remove `ai-api`
- merge `agent-registry` into `agent-core`
- merge or remove `kernel-capabilities`
- merge `connectors` into `event-cloud` plus product-owned adapters
- merge `distributed-cache` into `database` or `runtime`
- fold `audit` into `observability` or narrow it to a much smaller API-only surface

### Boundary rules after cleanup

- `core` owns generic shared primitives only
- `domain` owns shared business records only
- `runtime` owns ActiveJ/runtime primitives only
- `http` owns HTTP transport and filters only
- `security` owns authN/authZ implementation only
- `governance` owns cross-tenant policy and compliance rules only
- `observability` owns metrics/tracing/logging/audit telemetry only
- `kernel` owns kernel abstractions only, not parallel copies of plugin/contract policy stacks

## Expanded Cross-Product Findings

The product scan changed the implementation plan in three important ways.

### 1. `agent-runtime` is not purely “platform,” but it is also not purely “AEP”

Evidence from current build wiring:

- [`/Users/samujjwal/Development/ghatana/products/aep/orchestrator/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/aep/orchestrator/build.gradle.kts) depends on `agent-core`, `agent-runtime`, and `agent-registry`
- [`/Users/samujjwal/Development/ghatana/products/virtual-org/engine/service/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/virtual-org/engine/service/build.gradle.kts) depends on `agent-core` and `agent-runtime`
- [`/Users/samujjwal/Development/ghatana/products/yappc/core/yappc-agents/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/yappc/core/yappc-agents/build.gradle.kts) depends on `agent-core`, `agent-registry`, `agent-runtime`, and also directly on `products:aep:aep-registry`

Interpretation:

- the repository currently treats agent execution/runtime as a shared capability
- but the actual orchestration center of gravity is in AEP
- meanwhile YAPPC and Virtual Org have their own product-specific agent frameworks and logic

So the right move is not “delete agent runtime because it is AEP,” but:

- make the neutral parts truly platform-owned
- move orchestration, dispatch, memory pipelines, checkpointing, and evented execution wiring into AEP-owned modules
- let other products depend only on neutral agent contracts unless they intentionally opt into AEP execution

### 2. Agent concepts are duplicated in platform and products

Examples from the product scan:

- YAPPC has `products:yappc:core:agents`, `products:yappc:core:yappc-agents`, `products:yappc:core:yappc-domain`, and `products:yappc:services:lifecycle` all containing agent-related concepts
- Virtual Org contains its own `Agent`, `AgentRegistry`, runtime, memory, and workflow classes
- Data Cloud contains `products:data-cloud:agent-registry` and agent-oriented HTTP endpoints
- Finance and Tutorputor define product agents on top of platform agent abstractions

Interpretation:

- the platform should own only the generic agent contracts and SPIs
- product agent specs, product prompts, product workflow steps, and product-specific orchestration logic should live in the products

### 3. Data Cloud already hints at optional AEP integration and should be formalized that way

Evidence:

- [`/Users/samujjwal/Development/ghatana/products/data-cloud/agent-registry/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/data-cloud/agent-registry/build.gradle.kts) implements the registry against Data Cloud
- [`/Users/samujjwal/Development/ghatana/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/agentic/AgenticDataProcessor.java`](/Users/samujjwal/Development/ghatana/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/agentic/AgenticDataProcessor.java) already attempts optional AEP validation via `ServiceLoader`
- [`/Users/samujjwal/Development/ghatana/products/aep/aep-event-cloud/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/aep/aep-event-cloud/build.gradle.kts) currently describes Data Cloud as mandatory backing storage for AEP

Interpretation:

- Data Cloud should remain independently usable
- AEP should be an optional event-processing and agent-execution layer that can sit on top of Data Cloud
- agent registry persistence can be Data Cloud-backed without requiring AEP runtime

### 4. Event Cloud ownership is split incorrectly today

Current state:

- `platform:java:event-cloud` contains only a small API surface
- `products:aep:aep-event-cloud` acts as the “canonical bridge” and currently frames Data Cloud as mandatory for AEP
- Data Cloud platform contains a large amount of EventCloud storage, plugin, streaming, and state-management logic

Target state:

- `platform:java:event-cloud` should own the canonical abstraction
- Data Cloud should implement storage/state management for Event Cloud
- connectors should only produce/consume events
- AEP should consume Event Cloud as a client/runtime, not define Event Cloud ownership

This aligns with the desired principle:

- Event Cloud manages event storage, state layers, buffers, and durable/in-memory backends
- connectors are adapters for ingress/egress only
- AEP uses Event Cloud, but does not own its core abstraction

## Revised Target Architecture

### Agent stack

#### Platform-owned

- `platform:java:agent-core`
  - canonical `AgentSpec`, `AgentDescriptor`, `AgentLogicProvider`, `AgentRegistry` SPI, execution request/response DTOs, tool SPI only if product-neutral
- `products:data-cloud:agent-registry`
  - persistence-backed implementation of the registry SPI

#### AEP-owned

- `products:aep:*`
  - dispatch
  - execution orchestration
  - checkpointing
  - memory pipelines
  - event emission
  - execution policies
  - pipeline runtime

This likely becomes a dedicated module such as `products:aep:aep-agent-runtime` or a clearer split inside `products:aep:orchestrator`.

#### Product-owned

- YAPPC, Virtual Org, Finance, Tutorputor, Software Org
  - agent specs
  - prompts/templates
  - product tools
  - product workflow phases
  - product agent orchestration rules

Rule:

- product-specific agent classes stay in product modules
- only generic SPI and neutral contracts belong in platform

### Event stack

#### Platform-owned

- `platform:java:event-cloud`
  - canonical `EventCloud`, `EventRecord`, `EventStream`, subscriptions, offsets, tier/state abstractions, buffer abstractions, persistence SPI

#### Data-Cloud-owned

- `products:data-cloud:*`
  - durable storage implementations
  - tiered storage
  - in-memory and persistent state backends
  - streaming/storage plugins
  - embedded and remote deployment modes

#### Connector-owned

- `platform:java:connectors` or successor module
  - ingress and egress only
  - Kafka/webhook/file adapters
  - no ownership of event store, state store, or event log semantics

#### AEP-owned

- `products:aep:aep-event-cloud`
  - adapter/plugin that lets AEP use platform Event Cloud backed by Data Cloud
  - no canonical ownership of Event Cloud contracts

## Concrete Implementation Plan

### Workstream A: Clean inventory and remove obvious structural debt

#### A1. Remove dead/stale module artifacts

1. Delete `platform/java/ai-api`.
2. Remove or archive non-included standalone submodule leftovers under `platform/java/ai-integration`:
   - `gateway/build.gradle`
   - `serving/build.gradle`
   - stale submodule directories that are no longer part of the root build
3. Rewrite [`/Users/samujjwal/Development/ghatana/platform/java/ai-integration/README.md`](/Users/samujjwal/Development/ghatana/platform/java/ai-integration/README.md) to describe the actual merged structure.

Exit criteria:

- every top-level directory under `platform/java` is either:
  - included Gradle module, or
  - documented non-module directory

#### A2. Add repository inventory guardrail

Add a verification task or script that reports:

- top-level `platform/java/*` directories
- root-build include status
- build file presence
- README presence
- owner presence

Fail CI if a new module appears without explicit registration.

### Workstream B: Canonicalize platform duplicates before moving modules

#### B1. Fix `kernel` internal duplication first

Canonicalize to one package family:

- choose `com.ghatana.kernel.contract` or `com.ghatana.kernel.contracts`
- choose `com.ghatana.kernel.boundary` or `com.ghatana.kernel.policy`

Concrete tasks:

1. Pick the package to keep.
2. Update all imports in `kernel`.
3. Mark parallel classes deprecated.
4. Remove deprecated duplicates once consumers are migrated.

Exit criteria:

- no duplicate `ContractRegistry`, `ContractValidator`, or `BoundaryPolicyResolver` classes in `kernel`

#### B2. Canonicalize utility duplicates

Concrete targets:

- `core`: keep one `Result`, `ErrorCode`, `ServiceEndpoint`, `ConfigurationException`
- `database`: keep one `RedisCacheConfig`
- `audit`/`domain`: keep one `AuditEvent`
- `core`/`kernel`: keep one `JsonUtils`

Exit criteria:

- no duplicate utility type names with the same meaning across platform modules

### Workstream C: Split neutral agent contracts from AEP execution

This is the most important architectural workstream.

#### C1. Freeze the platform agent boundary

Decide that `platform:java:agent-core` may contain only:

- agent descriptor/spec contracts
- agent registry SPI
- agent logic provider SPI
- neutral agent invocation model
- tool SPI if it is not YAPPC/Virtual Org specific

It must not contain:

- AEP pipeline orchestration
- AEP event publishing
- product-specific templates/prompts
- product-specific agent catalogs

#### C2. Merge `agent-registry` into `agent-core`

Move the current SPI from `platform:java:agent-registry` into `platform:java:agent-core`.

Then:

1. update consumers
2. deprecate `platform:java:agent-registry`
3. remove the module after migration

Reason:

- the separate module is too thin
- Data Cloud can still implement the registry through `products:data-cloud:agent-registry`

#### C3. Extract AEP-owned execution runtime

Create a product-owned execution module, preferably:

- `products:aep:aep-agent-runtime`

Move into it from `platform:java:agent-runtime` anything that is execution-engine specific, including:

- dispatch
- checkpointing
- memory orchestration
- evented execution/publishing
- AEP-oriented resilience and runtime policies

Keep in platform only the neutral interfaces, if any remain. If nothing neutral remains, delete `platform:java:agent-runtime`.

#### C4. Make AEP optional for consumers

After extraction:

- YAPPC and Virtual Org may depend on AEP execution only through an explicit adapter dependency
- Data Cloud must not depend on AEP execution
- product agent specs remain in product modules

Concrete migration targets:

- remove direct `products:aep:*` runtime assumptions from YAPPC agent aggregators where the dependency is only for generic execution
- keep explicit AEP adapters only where YAPPC intentionally uses AEP orchestration

#### C5. Normalize product ownership of agent content

Move or keep content by rule:

- platform:
  - neutral contracts only
- products:
  - `AgentSpec` instances
  - prompts/templates
  - product agent catalogs
  - product workflow phase logic
  - product adapters

That means:

- YAPPC agent classes stay in YAPPC
- Virtual Org agent classes stay in Virtual Org
- Finance/Tutorputor/Software Org agent logic stays in those products
- only shared contracts remain in platform

Exit criteria:

- no product module needs `products:aep:*` just to define or persist its own agent specs
- Data Cloud can run without AEP

### Workstream D: Rebuild Event Cloud as a true platform abstraction

#### D1. Expand `platform:java:event-cloud` to be the canonical event plane

It should own:

- event records
- streams
- offsets
- subscriptions
- append/read contracts
- state tier abstractions
- buffer abstractions
- checkpoint/store interfaces

This is broader than the current tiny API surface and is necessary to stop Event Cloud semantics from drifting into AEP and Data Cloud separately.

#### D2. Re-scope connectors

Change `connectors` so it only covers ingress/egress adapters:

- Kafka producers/consumers
- webhook sources
- file ingress/egress

Remove from connector ownership:

- event store management
- state store management
- buffer ownership
- pipeline registry semantics

After this split, either:

- rename it to a clearer adapter module, or
- merge it into `event-cloud` plus `http`

#### D3. Re-scope `products:aep:aep-event-cloud`

`products:aep:aep-event-cloud` should become:

- AEP adapter/plugin for platform Event Cloud backed by Data Cloud

It should not define Event Cloud as an AEP concept.

Concrete work:

1. audit all public types in `products:aep:aep-event-cloud`
2. move generic contracts to `platform:java:event-cloud`
3. keep only AEP adapter/plugin glue in the AEP module

#### D4. Formalize Data Cloud as an Event Cloud implementation

Move/retain Event Cloud storage responsibilities in Data Cloud:

- in-memory backend
- persistent event log backend
- tiered storage
- plugin-backed streaming/storage integrations
- embedded and service deployment modes

This makes the layering clean:

- platform owns contract
- Data Cloud owns implementation
- AEP owns usage/orchestration
- connectors own ingress/egress

Exit criteria:

- AEP can use Event Cloud through the platform API
- Data Cloud can expose Event Cloud-backed storage without requiring AEP
- connector modules do not own event state semantics

### Workstream E: Product migration sequence

#### E1. Data Cloud

1. keep `products:data-cloud:agent-registry` as the canonical persistence-backed registry implementation
2. remove any accidental compile-time dependence on AEP runtime
3. preserve optional AEP integration only through explicit SPI or `ServiceLoader`
4. document “Data Cloud standalone” and “Data Cloud with AEP” as separate supported modes

#### E2. AEP

1. create or clarify AEP-owned execution runtime module
2. move orchestration and runtime wiring out of platform agent modules
3. reframe `aep-event-cloud` as adapter/plugin, not owner
4. ensure `orchestrator` depends on platform contracts plus AEP runtime, not the other way around

#### E3. YAPPC

1. remove accidental mixing of:
   - agent contracts
   - agent specs
   - AEP integration
   - product workflows
2. keep YAPPC agent specs and catalogs inside YAPPC
3. keep AEP integration behind explicit adapters such as lifecycle/event bridge modules

#### E4. Virtual Org

1. keep Virtual Org agent/runtime concepts product-owned
2. depend on platform agent contracts only where truly shared
3. treat AEP execution as optional integration, not foundation

### Workstream F: Guardrails after migration

1. Add architecture rules that ban:
   - product modules exporting platform-canonical agent contracts
   - platform modules exporting AEP-specific execution contracts
   - connector modules owning event-store or state-store abstractions
2. Add a duplicate-concept check for:
   - `AgentRegistry`
   - `AgentSpec`
   - `AuditEvent`
   - `Role`
   - `PluginRegistry`
   - `ContractRegistry`
3. Add an ownership matrix document for:
   - platform contracts
   - product specs
   - product adapters
   - optional integrations

## Concrete Delivery Sequence

### Sprint 1

- remove `ai-api`
- clean `ai-integration` leftovers
- canonicalize `kernel` duplicates
- document target ownership for agent and event layers

### Sprint 2

- merge `agent-registry` into `agent-core`
- create AEP-owned execution runtime module
- start moving execution-specific classes out of `platform:java:agent-runtime`

### Sprint 3

- expand `platform:java:event-cloud` contracts
- re-scope connectors to ingress/egress only
- re-scope `products:aep:aep-event-cloud` to adapter/plugin only

### Sprint 4

- migrate YAPPC and Virtual Org to the new boundaries
- verify Data Cloud standalone mode
- delete obsolete platform modules if migration is complete:
  - `agent-registry`
  - `kernel-capabilities`
  - possibly `agent-runtime`
  - possibly `connectors`

## Prioritized Action List

### Highest priority

- Remove `ai-api`
- Canonicalize duplicate classes inside `kernel`
- Merge `agent-registry` into `agent-core`
- Define the split between neutral agent contracts and AEP execution runtime
- Reframe Event Cloud as platform-owned, with Data Cloud as implementation and AEP as consumer

### Medium priority

- Remove stale `ai-integration` submodule leftovers
- Merge `kernel-capabilities`
- Re-scope `connectors`
- Normalize YAPPC and Virtual Org product agent ownership

### Lower priority

- Review whether `audit` should remain standalone
- Review whether `distributed-cache` should remain standalone
- Tighten owner/README/CI policy for all platform modules

## Bottom Line

The earlier report correctly identified duplication and thin modules, but the product scan makes the migration direction much clearer:

- `agent-core` should become the single neutral agent contract module
- AEP should own execution runtime and orchestration
- product agent specs and product-specific logic should stay in product modules
- Data Cloud must stay independently usable without AEP
- Event Cloud should be platform-owned, implemented by Data Cloud, and consumed by AEP through adapters

That gives a consistent model for all four concerns raised in review and turns the cleanup into a concrete architectural migration instead of a generic shared-library tidy-up.
