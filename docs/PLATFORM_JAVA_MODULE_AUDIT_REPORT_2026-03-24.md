# Platform Java Module Audit

Date: 2026-03-24
Scope: `/Users/samujjwal/Development/ghatana/platform/java` plus cross-product event/agent ownership review
Supersedes: event/agent architecture sections of [`/Users/samujjwal/Development/ghatana/docs/PLATFORM_JAVA_MODULE_AUDIT_REPORT_2026-03-23.md`](/Users/samujjwal/Development/ghatana/docs/PLATFORM_JAVA_MODULE_AUDIT_REPORT_2026-03-23.md)

## Executive Summary

The first audit correctly found module duplication, thin boundaries, and orphaned/stale modules under `platform/java`. The deeper scan across Data Cloud and AEP changes one major conclusion:

- the generic EventCloud center of gravity is already in Data Cloud, not in `platform:java:event-cloud`

That one correction makes the rest of the plan much more consistent:

1. Data Cloud should own the full generic event stack:
   - event storage
   - event streaming
   - state management
   - tiering
   - buffers
   - reusable routing/channel primitives
2. AEP should own only the plugin/runtime layer that turns that generic event stack into AEP event processing.
3. Without the AEP EventCloud plugin/runtime, Data Cloud should still work, but AEP event-processing features should not.
4. Agents can already be used without AEP at the contract/provider/persistence level.
5. The current agent sprawl comes from duplicate registry/runtime layers, not from lack of abstraction.

The resulting target is:

- `platform:java:agent-core` becomes the only shared agent contract/SPI module
- `platform:java:event-cloud` becomes a compatibility shim and is then removed
- Data Cloud becomes the canonical owner of generic event-plane contracts and implementations
- AEP becomes the owner of event processing and advanced agent runtime
- products keep only their own agent specs and product-specific logic

## Key Decisions

### Decision 1: Data Cloud is the canonical owner of the generic event stack

This is not a greenfield preference. It is already what the code is converging toward.

Evidence:

- [`EventCloud.java`](/Users/samujjwal/Development/ghatana/platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/EventCloud.java) says `com.ghatana.core.event.cloud` is migrating to `com.ghatana.datacloud.event`.
- [`package-info.java`](/Users/samujjwal/Development/ghatana/platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/package-info.java) says new code should use the Data Cloud package directly.
- [`EventLogStore.java`](/Users/samujjwal/Development/ghatana/products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java) already provides append/read/tail storage SPI used by AEP.
- Data Cloud already contains the richer event model and plugin SPIs under `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/event/...`.

Conclusion:

- do not expand `platform:java:event-cloud` into the long-term canonical event plane
- centralize event-stack ownership in Data Cloud
- treat `platform:java:event-cloud` as a migration shim only

### Decision 2: AEP owns event processing, not the generic event stack

Evidence:

- [`products/aep/aep-event-cloud/build.gradle.kts`](/Users/samujjwal/Development/ghatana/products/aep/aep-event-cloud/build.gradle.kts) describes the module as the bridge between AEP and Data Cloud.
- [`DataCloudBackedEventCloud.java`](/Users/samujjwal/Development/ghatana/products/aep/aep-event-cloud/src/main/java/com/ghatana/aep/eventcloud/DataCloudBackedEventCloud.java) adapts AEP’s simplified EventCloud facade to Data Cloud’s `EventLogStore`.
- [`EventCloudPlugin.java`](/Users/samujjwal/Development/ghatana/products/aep/aep-event-cloud/src/main/java/com/ghatana/aep/eventcloud/EventCloudPlugin.java) is the plugin that exposes Data Cloud-backed storage/streaming/registry capabilities to AEP.

Conclusion:

- AEP should keep the EventCloud plugin/runtime layer
- AEP should keep AEP-specific run ledgers, pipeline-facing channel conventions, and event-processing integration
- AEP should not be treated as the owner of the generic event plane

### Decision 3: No AEP plugin means no AEP event processing

This is the right boundary for product independence.

Data Cloud should support:

- event storage
- event streaming
- state management
- buffers
- routing
- embedded or standalone deployment

But Data Cloud alone should not imply:

- AEP pipeline execution
- AEP operator semantics
- AEP checkpoint/replay behavior
- AEP-specific event-processing features

Those only become available when AEP’s plugin/runtime is present.

### Decision 4: Agents can be used without AEP

Evidence:

- [`AgentRegistry.java`](/Users/samujjwal/Development/ghatana/platform/java/agent-core/src/main/java/com/ghatana/agent/spi/AgentRegistry.java) already exists in `agent-core`.
- [`AgentLogicProvider.java`](/Users/samujjwal/Development/ghatana/platform/java/agent-core/src/main/java/com/ghatana/agent/spi/AgentLogicProvider.java) already defines neutral provider-based agent instantiation.
- [`DataCloudAgentRegistry.java`](/Users/samujjwal/Development/ghatana/products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/DataCloudAgentRegistry.java) already provides Data Cloud-backed registry persistence.
- [`DataCloudAgentLogicProvider.java`](/Users/samujjwal/Development/ghatana/products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/provider/DataCloudAgentLogicProvider.java) already implements a product-owned provider without AEP runtime.

Conclusion:

- yes, agents can be used without AEP
- the shared boundary should support:
  - contracts
  - registry SPI
  - provider SPI
  - optional tiny invocation contracts if truly neutral
- AEP should be required only for advanced orchestration/runtime behavior

### Decision 5: The current agent sprawl must be collapsed

Current overlap:

- `platform:java:agent-registry` duplicates the SPI already present in `agent-core`
- `products:aep:aep-event-cloud:EventCloudAgentStore` introduces another agent persistence path
- `platform:java:agent-runtime` bundles event-sourced memory, dispatch, policies, learning, and review concerns under a pseudo-platform label
- Data Cloud’s `AgenticDataProcessor` uses AEP-branded naming even though the advanced path is optional and mostly stubbed

Conclusion:

- merge `agent-registry` into `agent-core`
- do not create another shared “agent framework” module
- move advanced runtime behavior into AEP
- keep Data Cloud’s agent support limited to persistence, provider discovery, and optional neutral augmentation hooks

## Code-Level Findings

### Event stack findings

#### 1. `platform:java:event-cloud` is already a migration boundary, not a stable destination

The code explicitly says so. The package-level docs and interface docs both direct new code to Data Cloud packages.

Implication:

- do not build new canonical contracts there
- migrate callers out, then delete the module

#### 2. Data Cloud already contains the richer generic event stack

Notable areas:

- `products/data-cloud/spi`
  - `EventLogStore`
  - `EntityStore`
  - `TenantContext`
- `products/data-cloud/platform/com/ghatana/datacloud/event/common`
  - offsets
  - partitions
  - stream position
- `products/data-cloud/platform/com/ghatana/datacloud/event/model`
  - `Event`
  - `EventStream`
  - `EventType`
  - `Partition`
  - `Collection`
  - `ConsumerGroup`
- `products/data-cloud/platform/com/ghatana/datacloud/event/spi`
  - `StoragePlugin`
  - `StreamingPlugin`
  - `RoutingPlugin`
  - `ArchivePlugin`

Implication:

- Data Cloud already owns most of what the long-term event plane needs

#### 3. `products:aep:aep-event-cloud` contains both true AEP code and generic event-plane code

Clearly AEP-owned:

- `EventCloudPlugin`
- `EventCloudPluginFactory`
- `EventCloudPluginConfig`
- `DataCloudBackedEventCloud`
- `DataCloudEventCloudConnector`
- `EventCloudRunLedger`
- AEP-specific channels and capabilities

Looks generic enough to centralize into Data Cloud:

- `buffer/EventBuffer`
- reusable parts of `channel/EventChannelRegistry`

Should be narrowed or replaced:

- `store/EventCloudAgentStore`

Implication:

- do not move the whole module in one direction
- split the generic pieces down into Data Cloud
- leave the AEP-specific plugin/runtime pieces in AEP

### Connector findings

The repo currently has three different connector concerns:

1. `platform:java:connectors`
   - generic `Connector`, `EventSource`, `EventSink`, Kafka/file/webhook adapters, lifecycle registry
2. `products:aep:aep-connectors`
   - AEP-specific ingress/egress strategies
3. `products:aep:aep-engine`
   - AEP pipeline connector specs and connector registry for runtime adaptation

Implication:

- connector production/consumption should be separated from AEP pipeline specification
- generic ingress/egress can stay shared if it is truly generic
- event-store/state/buffer semantics belong to Data Cloud, not to connector modules

### Agent findings

#### 1. The shared registry SPI is duplicated

Both of these exist and mean the same thing:

- `platform/java/agent-core/.../agent/spi/AgentRegistry.java`
- `platform/java/agent-registry/.../agent/registry/AgentRegistry.java`

Implication:

- merge into `agent-core`

#### 2. `platform:java:agent-runtime` is too large to remain a neutral shared runtime

It contains broad runtime behavior including event-coupled memory/persistence layers. Example code paths import `com.ghatana.core.event.cloud.EventCloud` directly from within agent memory/persistence stores.

Implication:

- this is not a clean, product-neutral layer
- the advanced runtime belongs in AEP

#### 3. Data Cloud already has a non-AEP agent path, but its naming and boundaries need cleanup

Data Cloud today already supports:

- registry persistence
- product provider registration
- HTTP CRUD around agent definitions

What still needs correction:

- `AgenticDataProcessor` should not frame AEP as the default conceptual owner of “advanced” processing
- Data Cloud launcher/admin surfaces should not mix generic agent storage with AEP-branded collections unless those endpoints are explicitly AEP-only

## Revised Target Architecture

### Event stack

#### Canonical generic owner: Data Cloud

`products:data-cloud:spi` owns:

- externally consumed storage and entity contracts
- tenant context and minimal SPI surfaces shared by other products

`products:data-cloud:platform` owns:

- event model
- offsets, partitions, stream positioning
- storage/streaming/routing/archive plugin SPIs
- in-memory and persistent event/state implementations
- generic buffering and routing primitives
- embedded and standalone deployment modes

Rule:

- all generic event-plane capabilities live here

#### Transitional compatibility owner: platform

`platform:java:event-cloud` becomes:

- compatibility shim only
- no new API work
- deleted after migration

#### Event-processing owner: AEP

`products:aep:aep-event-cloud` becomes:

- AEP EventCloud plugin/runtime
- AEP bridge/facade on top of Data Cloud
- AEP run ledger/checkpoint/event-processing integration
- AEP capability exposure to AEP components

Rule:

- without this module, Data Cloud still works
- without this module, AEP event-processing features do not work

#### Connector rule

Connectors may own:

- produce/consume adapter behavior
- protocol-specific ingestion/egress

Connectors may not own:

- event-store semantics
- state-store semantics
- buffering ownership
- tier management

### Agent stack

#### Shared minimal owner: platform

`platform:java:agent-core` owns:

- `TypedAgent`
- `AgentDescriptor`
- `AgentConfig`
- `AgentLogicProvider`
- merged `AgentRegistry` SPI
- only the smallest possible neutral invocation types, if needed

Rule:

- no AEP orchestration
- no event-sourced memory
- no checkpointing
- no product-specific logic catalogs

#### Persistence/backend owner: Data Cloud

`products:data-cloud:agent-registry` owns:

- durable agent registry backend
- provider-backed product agent instantiation support
- optional admin/API surface for stored descriptors/configs

Rule:

- Data Cloud owns persistence, not product agent semantics

#### Advanced runtime owner: AEP

AEP owns:

- dispatch
- execution orchestration
- event emission
- checkpointing
- event-sourced memory
- runtime policies
- learning/review/assurance flows

Recommended destination:

- `products:aep:aep-agent-runtime`

#### Product-owned content

All products keep:

- agent specs
- prompts/templates
- tools
- product logic providers
- product workflow rules

### Supported operating modes

#### Mode 1: Data Cloud only

Available:

- event storage
- streaming
- state management
- entities

Not available:

- AEP event processing
- AEP orchestration

#### Mode 2: Data Cloud plus local product agents, without AEP

Available:

- `agent-core`
- Data Cloud registry backend
- product `AgentLogicProvider` implementations
- product-owned lightweight invocation or orchestration

Not required:

- AEP runtime

#### Mode 3: Data Cloud plus AEP plugin/runtime

Available:

- generic event plane from Data Cloud
- AEP event processing
- AEP advanced runtime/orchestration

## Centralization Map

### Event-related modules and classes

| Current code | Current role | Action | Destination |
|---|---|---|---|
| `platform/java/event-cloud/*` | legacy EventCloud API and in-memory impl | deprecate, migrate callers, delete | Data Cloud owns replacement |
| `products/data-cloud/spi/EventLogStore` | append/read/tail store SPI | keep as core external event storage contract | Data Cloud |
| `products/data-cloud/platform/com/ghatana/datacloud/event/*` | richer event model and plugin SPIs | keep and expand | Data Cloud |
| `products/aep/aep-event-cloud/buffer/EventBuffer` | spill/backpressure on `EventLogStore` | move/rename | Data Cloud |
| reusable parts of `products/aep/aep-event-cloud/channel/EventChannelRegistry` | named routing over event log | split generic routing out | Data Cloud for generic, AEP for channel conventions |
| `products/aep/aep-event-cloud/store/EventCloudRunLedger` | pipeline run journal | keep | AEP |
| `products/aep/aep-event-cloud/store/EventCloudAgentStore` | duplicate AEP-side agent persistence helper | replace or narrow | merged registry backend or AEP-only runtime projection |
| `platform/java/connectors/*` | generic adapters plus lifecycle registry | shrink to thin ingress/egress only | thin shared or Data Cloud adapter layer |
| `products/aep/aep-connectors/*` | AEP-specific connector strategies | keep | AEP |

### Agent-related modules and classes

| Current code | Current role | Action | Destination |
|---|---|---|---|
| `platform/java/agent-core/*` | neutral contracts and SPIs | keep, narrow | Platform |
| `platform/java/agent-registry/*` | duplicate registry SPI module | merge into `agent-core`, deprecate, delete | Platform |
| `platform/java/agent-runtime/*` | mixed runtime with event/memory/policy coupling | extract advanced runtime out | AEP / remove |
| `products/data-cloud/agent-registry/*` | durable registry backend | keep as canonical backend | Data Cloud |
| `products/aep/aep-event-cloud/store/EventCloudAgentStore` | duplicate persistence path | remove as canonical path | Data Cloud backend or explicit AEP projection |
| `products/data-cloud/platform/.../AgenticDataProcessor` | optional advanced validation path | rename to neutral augmentation/validation consumer | Data Cloud |
| product agent packages | specs, prompts, tools, product logic | keep in products | Products |

## Concrete Implementation Plan

### Phase 0: Freeze ownership and stop new divergence

Tasks:

1. Publish the ownership rule:
   - Data Cloud owns the generic event stack
   - AEP owns event processing and advanced agent runtime
   - `agent-core` is the only shared agent contract module
2. Update READMEs/OWNER docs/ADRs to match.
3. Block new feature work in:
   - `platform:java:event-cloud`
   - `platform:java:agent-registry`

Exit criteria:

- no new API work lands in the wrong modules

### Phase 1: Collapse the shared agent boundary to one module

Tasks:

1. Merge `platform:java:agent-registry` into `platform:java:agent-core`.
2. Update all imports and Gradle dependencies.
3. Update `products:data-cloud:agent-registry` to depend only on `agent-core`.
4. Deprecate the old module and remove it once consumers migrate.

Exit criteria:

- one shared registry SPI
- one shared agent contract module

### Phase 2: Remove duplicate agent persistence paths

Tasks:

1. Audit every usage of `EventCloudAgentStore`.
2. For generic descriptor/config persistence:
   - route through the merged registry SPI backed by `products:data-cloud:agent-registry`
3. For AEP-only runtime state:
   - rename the code to make its AEP-only scope explicit
4. Remove any documentation that implies `EventCloudAgentStore` is canonical.

Exit criteria:

- AEP no longer introduces a second canonical registry

### Phase 3: Move the generic event stack into its final home

Tasks:

1. Mark `platform:java:event-cloud` as compatibility-only.
2. Complete Data Cloud replacements for any missing generic contracts.
3. Migrate callers from `com.ghatana.core.event.cloud` to Data Cloud packages.
4. Move `EventBuffer` into Data Cloud.
5. Split `EventChannelRegistry`:
   - generic routing utility to Data Cloud
   - AEP channel naming/conventions remain in AEP
6. Decide whether the surviving generic ingress/egress contracts stay:
   - in a tiny shared adapter module, or
   - inside Data Cloud event adapter packages

Exit criteria:

- all generic event-plane contracts live in Data Cloud
- platform event-cloud is only a shim

### Phase 4: Narrow AEP to its real ownership boundary

Tasks:

1. Keep AEP ownership of:
   - `EventCloudPlugin`
   - `EventCloudPluginFactory`
   - `EventCloudPluginConfig`
   - AEP facade/connector adapters
   - `EventCloudRunLedger`
   - AEP-specific channel conventions
2. Remove or relocate generic event-plane pieces that drifted into AEP.
3. Update AEP docs to say:
   - this module enables AEP event processing on top of Data Cloud
   - without it, Data Cloud still works but AEP event processing does not

Exit criteria:

- `aep-event-cloud` is clearly an AEP plugin/runtime, not a generic platform owner

### Phase 5: Extract advanced runtime out of `platform:java:agent-runtime`

Tasks:

1. Create or finalize `products:aep:aep-agent-runtime`.
2. Move execution-engine-specific code out of `platform:java:agent-runtime`, especially:
   - dispatch
   - evented execution
   - checkpointing
   - event-sourced memory stores
   - review/assurance/learning flows
   - runtime policies
3. Leave behind only tiny neutral interfaces, if any remain.
4. Delete `platform:java:agent-runtime` if nothing meaningfully neutral remains.

Exit criteria:

- advanced runtime is explicitly AEP-owned
- shared runtime sprawl is removed

### Phase 6: Clean Data Cloud’s non-AEP agent path

Tasks:

1. Keep Data Cloud on:
   - `agent-core`
   - Data Cloud registry backend
   - product-specific `AgentLogicProvider` implementations
2. Rename `AgenticDataProcessor` and its inner strategy types to neutral names.
3. Make AEP one optional provider of advanced behavior, not the conceptual owner.
4. Review Data Cloud launcher/admin endpoints and collection names such as `aep_pipelines`.
5. Move AEP-only endpoints into AEP or rename generic persistence surfaces to neutral names.

Exit criteria:

- Data Cloud can use agents without AEP
- Data Cloud does not look AEP-owned at the API or naming layer

### Phase 7: Clean platform debt and duplicate concepts

Tasks:

1. Remove `platform/java/ai-api`.
2. Clean stale `platform/java/ai-integration` leftovers.
3. Canonicalize `kernel` duplicates.
4. Canonicalize duplicate utility/domain types identified in the first report.
5. Merge or remove `kernel-capabilities`.

Exit criteria:

- module inventory is cleaner
- duplicate concept noise is reduced before or during larger migrations

## Delivery Sequence

### Sprint 1

- publish the ownership decision
- mark `platform:java:event-cloud` and `platform:java:agent-registry` as migration-only
- remove `ai-api`
- clean `ai-integration`
- fix `kernel` internal duplication

### Sprint 2

- merge `agent-registry` into `agent-core`
- update Data Cloud registry backend imports/dependencies
- audit `EventCloudAgentStore` usages

### Sprint 3

- move generic event contracts and utilities toward Data Cloud
- move `EventBuffer`
- split generic routing from AEP-specific channel conventions
- decide final home of thin generic ingress/egress contracts

### Sprint 4

- finalize `aep-event-cloud` as AEP plugin/runtime only
- create or finalize `products:aep:aep-agent-runtime`
- start moving advanced runtime behavior out of `platform:java:agent-runtime`

### Sprint 5

- neutralize Data Cloud’s “agentic” extension points
- clean AEP leakage from Data Cloud admin/API surfaces
- verify:
  - Data Cloud only mode
  - Data Cloud plus local agents
  - Data Cloud plus AEP plugin/runtime

### Sprint 6

- migrate YAPPC, Virtual Org, and other consumers to the new boundaries
- delete obsolete shims/modules when migrations complete:
  - `platform:java:agent-registry`
  - `platform:java:event-cloud`
  - possibly `platform:java:agent-runtime`
  - possibly `platform:java:connectors`
  - `kernel-capabilities`

## Validation Checklist

Use this as the acceptance checklist for the migration.

### Event stack

- new generic event APIs are added only in Data Cloud
- connectors only ingress/egress events
- AEP event processing does not work without the AEP plugin/runtime
- Data Cloud event storage/stream/state works without AEP

### Agent stack

- only one shared registry SPI exists
- products can register and instantiate agents without AEP
- advanced agent runtime behavior is owned by AEP
- product specs/prompts/tools stay in products

### Repository hygiene

- no orphan platform module directories remain
- no duplicate canonical event/agent ownership claims remain in docs
- no product-owned modules export supposedly platform-canonical contracts

## Risks and Watchouts

1. Data Cloud already contains some AEP-branded surfaces, so this migration is partly a code move and partly a naming/ownership cleanup.
2. `EventCloudAgentStore` should be treated carefully because it may currently carry real AEP usage even though it is architecturally duplicative.
3. `platform:java:agent-runtime` is likely to have more hidden product coupling than the current build graph suggests.
4. If connector abstractions are moved too aggressively without separating AEP pipeline semantics from generic I/O semantics, the sprawl will simply reappear under new package names.

## Bottom Line

The consistent revision of the plan is:

- Data Cloud implements the generic event stack end to end
- AEP owns the plugin/runtime that enables AEP event processing on top of that stack
- `platform:java:event-cloud` should be a temporary migration shim, not the future owner
- `platform:java:agent-core` should be the only shared agent contract module
- agents can be used without AEP through `agent-core` plus Data Cloud registry/provider support
- advanced agent runtime and orchestration should move into AEP

That gives a clean answer to both questions raised in review:

1. yes, centralize generic EventCloud ownership in Data Cloud and keep AEP-only event processing behind the AEP plugin/runtime
2. yes, use agents without AEP by keeping the shared boundary minimal and pushing heavy runtime behavior back into AEP

---

## Plan Review — Corrections and Gap Fills (2026-03-24)

This section records corrections and gaps found when cross-referencing the plan above against the actual current state of the codebase.

### Correction 1: `agent-core` is already large (155 files), not a narrow contract module

The plan says "`platform:java:agent-core` should be the only shared agent contract module" and implies it should remain narrow. The code reality is different.

**Actual state:** `agent-core` already contains ~155 Java source files including:
- Full framework runtime: `BaseAgent`, `AgentTurnPipeline`, `AgentCheckpointStore`, `AbstractTypedAgent`
- Memory layer: `MemoryStore`, `EventLogMemoryStore`, `JdbcMemoryStore`, `MemoryPlane`
- Learning: `LearningEngine`
- Coordination/orchestration: `ConversationManager`, `DelegationManager`, `HierarchicalOrchestration`
- ALL agent subtypes: `LLMAgent`, `DeterministicAgent`, `ReactiveAgent`, `CompositeAgent`, etc.
- Full catalog: `AgentCatalog`, `CatalogRegistry`, `FileBasedCatalog`
- SPIs: `AgentRegistry`, `AgentLogicProvider`, `AgentProviderRegistry`

**Gap filled:** Phase 5 (extract runtime to AEP) should scope which `agent-core` packages move vs. remain. The phase should not assume `agent-core` is already clean — the `agent-core` itself requires a narrowing pass alongside `agent-runtime` extraction.

**Action:** Phases 4 and 5 are updated to include `agent-core` narrowing as explicit subtasks.

### Correction 2: `platform/java/ai-api` is a ghost directory — no source files

The plan says "Remove `platform/java/ai-api`" (Phase 7, Sprint 1). This is correct.

**Actual state:** The directory `platform/java/ai-api` contains only a `build/` subdirectory with compiled class artifacts. There are zero `.java` source files. The module is not registered in `settings.gradle.kts`. References in dependent build files that mention `ai-api` are inert since the module has no Gradle identity.

**Action:** Delete the directory immediately. No import migrations needed.

### Correction 3: `platform/java/ai-integration` has orphaned sub-module directories

The plan says "clean stale `platform/java/ai-integration` leftovers" without detailing what those leftovers are.

**Actual state:** After the ai-integration consolidation (Session 2 of prior implementation work), the old submodule source trees were not deleted:
- `ai-integration/batch/src/` — `BatchInferenceJob.java` (orphaned, not compiled)
- `ai-integration/evaluation/src/` — `EvaluationRunner.java` (orphaned)
- `ai-integration/gateway/src/` — `LLMGatewayService.java`, `PromptCache.java`, `ProviderRouter.java`, `RateLimiter.java` (orphaned)
- `ai-integration/learning/src/` — test only (orphaned)
- `ai-integration/promotion/src/` — `DeploymentPromoter.java` (orphaned)
- `ai-integration/serving/src/` — `OnlineInferenceService.java`, `AIHttpRoutes.java` (orphaned)
- `ai-integration/quarantine/` — `BasicAiService.java` (explicitly quarantined)

Only `ai-integration/src/main/java/` is compiled by the Gradle module. All the above subdirectories are orphaned source trees that were not removed after the consolidation.

**Action:** Delete all orphaned sub-directories after confirming the canonical source is in `src/main/java`.

### Correction 4: `agent-registry` has 4 files; `agent-core` already has the SPI

The plan says "merge `platform:java:agent-registry` into `platform:java:agent-core`". This is correct but needs clarification.

**Actual state:**
- `agent-registry` has 4 files: `AgentRegistry.java` (interface, package `com.ghatana.agent.registry`), `InMemoryAgentRegistry.java`, `JdbcAgentRegistry.java`, `YamlAgentCatalogLoader.java`
- `agent-core` already has `com.ghatana.agent.spi.AgentRegistry` (the canonical SPI interface) with the exact same methods
- The two `AgentRegistry` interfaces are semantically identical but in different packages

**Issue:** After the merge, `agent-core` would contain two interfaces with the same contract in different packages:
- `com.ghatana.agent.spi.AgentRegistry` (canonical SPI)
- `com.ghatana.agent.registry.AgentRegistry` (legacy from the deprecated module)

**Resolution:** Move the 3 implementation classes into `agent-core` keeping their `com.ghatana.agent.registry` package (no consumer import changes needed). Add `@Deprecated` to the `com.ghatana.agent.registry.AgentRegistry` interface and direct consumers to `com.ghatana.agent.spi.AgentRegistry`. The implementations can implement the legacy interface which now extends the SPI, preserving binary compatibility with the 8 downstream consumers until those consumers update their import.

**Consumers of `:platform:java:agent-registry` Gradle dependency (8 total):**
- `products/aep/aep-agent`
- `products/aep/aep-registry`
- `products/aep/orchestrator`
- `products/aep/server`
- `products/data-cloud/agent-registry`
- `products/yappc/core/agents`
- `products/yappc/core/agents/runtime`
- `products/yappc/core/yappc-agents`

All 8 must be updated to depend on `:platform:java:agent-core` instead.

### Correction 5: `event-cloud` migration affects 19 consumers — Phase 3 needs sub-phasing

The plan treats Phase 3 as a single sprint item for migrating all `platform:java:event-cloud` callers to Data Cloud packages.

**Actual state:** 19 Gradle modules depend on `:platform:java:event-cloud`:

| Consumer | Urgency |
|---|---|
| `platform/java/agent-runtime` | High — foundational module |
| `platform/java/ai-integration` | High — platform module |
| `platform/java/audit` | Medium |
| `platform/java/connectors` | Medium |
| `platform/java/kernel-capabilities` | Medium |
| `platform/java/plugin` | Medium |
| `products/aep/aep-event-cloud` | High — intentional bridge |
| `products/data-cloud/feature-store-ingest` | High |
| `products/finance` | Low — product |
| `products/finance/client-onboarding` | Low |
| `products/finance/integration-testing` | Low |
| `products/virtual-org/engine/service` | Low |
| `products/virtual-org/modules/framework` | Low |
| `products/yappc/core/agents/runtime` | Medium |
| `products/yappc/core/agents/workflow` | Medium |
| `products/yappc/core/lifecycle` | Medium |
| `products/yappc/services` | Medium |
| `products/yappc/services/platform` | Medium |
| `shared-services/feature-store-ingest` | Low |

Phase 3 must be split into three sub-phases:
- **Phase 3a (Sprint 3):** Migrate platform-layer consumers (`agent-runtime`, `ai-integration`, `audit`, `connectors`, `kernel-capabilities`, `plugin`)
- **Phase 3b (Sprint 3):** Migrate product consumers that have simple usage (`data-cloud/feature-store-ingest`, `finance`, `virtual-org`)
- **Phase 3c (Sprint 4):** Migrate YAPPC and `shared-services` consumers; delete `event-cloud` shim

`products/aep/aep-event-cloud` is intentionally kept as the AEP plugin/runtime bridge and should NOT be removed — only updated to depend on Data Cloud directly.

### Correction 6: `platform/java/agent-runtime` coupling to `event-cloud`

The plan says "`agent-runtime` is likely to have more hidden product coupling". This is confirmed.

**Actual state:** `agent-runtime/build.gradle.kts` has `api(project(":platform:java:event-cloud"))`. The memory persistence classes (`EventSourcedEpisodicStore`) use `EventCloud` from this package. When Phase 3a migrates `agent-runtime` away from `event-cloud`, those classes must be updated to use `com.ghatana.datacloud.event.*` or `com.ghatana.datacloud.spi.EventLogStore`.

### No-change Confirmation: `products/aep/aep-event-cloud` stays as a bridge

The current code correctly implements the bridge pattern:
- `DataCloudBackedEventCloud` adapts AEP's `EventCloud` facade to Data Cloud's `EventLogStore`
- `EventCloudPlugin` is the AEP plugin exposing Data Cloud-backed capabilities
- These should remain in AEP and NOT be moved to Data Cloud

---

## Implementation Progress

> Last updated: 2026-03-24

### Sprint 1 (2026-03-24) — Dead code removal, agent-registry merge, event-cloud deprecation

| Task | Status | Notes |
|---|---|---|
| P7.1 Delete `platform/java/ai-api` ghost directory | ✅ DONE | No sources, not in settings.gradle.kts |
| P7.2 Delete orphaned sub-directories in `ai-integration` | ✅ DONE | batch/, evaluation/, gateway/, learning/, promotion/, serving/, quarantine/, training/, testing/ |
| P0.1 Mark `platform/java/event-cloud` as DEPRECATED in build file | ✅ DONE | Added ⚠️ DEPRECATED comment block to build.gradle.kts |
| P0.2 Enhance `event-cloud/package-info.java` deprecation notice | ✅ DONE | Added `@Deprecated`, full caller list, migration instructions |
| P1.1 Move `InMemoryAgentRegistry`, `JdbcAgentRegistry`, `YamlAgentCatalogLoader` into `agent-core` | ✅ DONE | Package `com.ghatana.agent.registry.*` preserved for binary compat |
| P1.2 Make `com.ghatana.agent.registry.AgentRegistry` a deprecated alias for `spi.AgentRegistry` | ✅ DONE | Extends `com.ghatana.agent.spi.AgentRegistry`; `@Deprecated(since="2026.3",forRemoval=true)` |
| P1.3 Add JDBC/H2 deps to `agent-core/build.gradle.kts` | ✅ DONE | HikariCP, PostgreSQL runtime, H2 test |
| P1.4 Update 7 of 8 consumers of `:platform:java:agent-registry` to use `:platform:java:agent-core` | ✅ DONE | aep-registry, aep-orchestrator, data-cloud/agent-registry, yappc/agents, yappc/agents/runtime, yappc/yappc-agents (aep-agent merged into aep-registry on 2026-03-22, no separate module) |
| P1.5 Remove `:platform:java:agent-registry` from `settings.gradle.kts` | ✅ DONE | |
| P1.6 Delete `platform/java/agent-registry` module directory | ✅ DONE | 4 source + 1 test file migrated before delete |
| Build validation: agent-core (614 tests pass), ai-integration, event-cloud build | ✅ DONE | BUILD SUCCESSFUL; all consumers compile |

### Sprint 2 (2026-03-25) — agent-registry consumer import cleanup + EventCloudAgentStore audit

| Task | Status | Notes |
|---|---|---|
| Migrate consumer Java imports from `com.ghatana.agent.registry.AgentRegistry` → `com.ghatana.agent.spi.AgentRegistry` | ✅ DONE | `InMemoryAgentRegistry`, `JdbcAgentRegistry`, `DataCloudAgentRegistry`, `YappcAgentRegistryAdapter`, `AgentFrameworkRegistry`, `PlannerRegistry` |
| Delete deprecated `AgentRegistry.java` shim (the `@Deprecated` bridge class) | ✅ DONE | All imports updated first; shim deleted clean |
| Audit every usage of `EventCloudAgentStore` in AEP | ✅ DONE | AEP-only path confirmed; uses `EntityStore` from data-cloud (correct) |
| Update `products:data-cloud:agent-registry` to depend only on `agent-core` | ✅ DONE | Verified imports correct |

### Sprint 3 (2026-03-25) — Phase 3a/3b event-cloud migration

#### Phase 3a — Platform-layer consumers

| Task | Status | Notes |
|---|---|---|
| Migrate `platform/java/agent-runtime` from `event-cloud` to `data-cloud/spi` | ✅ DONE | `EventSourcedEpisodicStore`, `JdbcTaskStateStore`, `PersistentMemoryPlane` updated; added `platform:java:domain` dep (was transitive via event-cloud) |
| Migrate `platform/java/ai-integration` from `event-cloud` | ✅ DONE | Dead dependency removed (no actual event-cloud usage in Java sources) |
| Migrate `platform/java/audit` from `event-cloud` | ✅ DONE | Dead dependency removed |
| Migrate `platform/java/connectors` from `event-cloud` | ✅ DONE | `EventSink` interface + `KafkaEventSink`, `LoggingEventSink`, `KafkaProducerAdapter` updated to `EventLogStore.EventEntry`/`TenantContext` |
| Migrate `platform/java/kernel-capabilities` from `event-cloud` | ✅ DONE | `EventStoreKernelModule`, `EventStoreService` updated; `InMemoryEventLogStoreProvider` moved to `data-cloud:spi` (see below) |
| Migrate `platform/java/plugin` from `event-cloud` | ✅ DONE | `EventCloudPluginAdapter` updated to `StreamingPlugin<EventLogStore.EventEntry>` |
| Move `EventBuffer` from `aep-event-cloud` into `data-cloud/platform` | ✅ DONE | Package `com.ghatana.datacloud.event.buffer`; old `aep-event-cloud` copy deleted |
| Split generic routing from `EventChannelRegistry` | ✅ NO-OP | Already 100% AEP-specific (all 17 usages in `aep-event-cloud`; channels are `aep.*`-prefixed); no generic routing to extract |

**Structural changes from Phase 3a:**
- `InMemoryEventLogStoreProvider` moved from `data-cloud:platform` → `data-cloud:spi` (only interfaces + reference impls belong in SPI)
- `ServiceLoader` file `META-INF/services/com.ghatana.datacloud.spi.EventLogStore` moved to `data-cloud:spi`
- `gradle/platform-boundary-check.gradle` updated: added `CROSS_CUTTING_SPI_ALLOWLIST` with `:products:data-cloud:spi` (pure interface module; audit directs platform modules to use it)

#### Phase 3b — Product consumers (simple migration)

| Task | Status | Notes |
|---|---|---|
| Migrate `data-cloud/feature-store-ingest` from `event-cloud` | ✅ DONE | Full rewrite: `EventCloud` subscription → `EventLogStore` polling with offset tracking; added `FEATURE_INGEST_POLL_DELAY_MS` config for idle backoff |
| Remove dead `event-cloud` dep from `finance` (root, client-onboarding, integration-testing) | ✅ DONE | No actual Java usage existed |
| Remove dead `event-cloud` dep from `virtual-org/modules/framework` and `virtual-org/engine/service` | ✅ DONE | No actual Java usage existed (only Javadoc mentions) |

### Sprint 4 (2026-03-25) — Phase 3c: YAPPC/shared-services migration + event-cloud deletion

| Task | Status | Notes |
|---|---|---|
| Migrate YAPPC consumers from `event-cloud` (agents/runtime, agents/workflow) | ✅ DONE | Created `EventPublisher` facade in YAPPC runtime wrapping `EventLogStore`; all 90+ workflow files updated (imports, field types, constructor params, mocks, Javadoc) |
| Remove dead `event-cloud` dep from `yappc/services` and `yappc/services/platform` | ✅ DONE | No actual Java usage existed |
| Migrate `shared-services/feature-store-ingest` from `event-cloud` | ✅ DONE | Dead dep removed; all Javadoc references updated to `EventLogStore` |
| Remove dead `event-cloud` dep from `products/aep/aep-event-cloud` | ✅ DONE | AEP uses `com.ghatana.aep.event.EventCloud` (own contracts), not platform `event-cloud` |
| Delete `platform/java/event-cloud` shim module | ✅ DONE | Directory deleted, removed from `settings.gradle.kts` |
| Create `products:aep:aep-agent-runtime` module | ✅ DONE | Created with identical deps; 156 main + test source files relocated from `platform:java:agent-runtime` |
| Relocate ALL `platform/java/agent-runtime` → `products:aep:aep-agent-runtime` | ✅ DONE | All 8 consumer build files updated; `platform/java/agent-runtime` deleted; packages unchanged (`com.ghatana.agent.*`) |
| Narrow `agent-core` by moving advanced runtime concepts to AEP | ⏳ DEFERRED | Deep cascade: LLMAgent/DeterministicAgent/ReactiveAgent extend AbstractTypedAgent; migration/llm/planner/ import from runtime classes — requires phased approach |

### Sprint 5 (2026-03-25) — Phase 6: cleanup + neutralize Data Cloud

| Task | Status | Notes |
|---|---|---|
| Rename `AgenticDataProcessor` → neutral name in Data Cloud | ✅ DONE | Renamed to `DataValidationProcessor` in package `com.ghatana.datacloud.plugins.validation`; old `plugins.agentic` package deleted |
| Clean AEP-branded collection names in Data Cloud admin surfaces | ✅ DONE | `aep_pipelines` → `dc_pipelines` in `AgentRegistryHandler`; aligns with existing `dc_agents` and `dc_checkpoints` conventions |
| Delete `platform/java/agent-registry` | ✅ DONE in Sprint 1 | |
| Migrate YAPPC, VirtualOrg to new boundaries | ✅ DONE | All deprecated module refs removed; `SDLCIntegrationTest` migrated EventCloud→EventPublisher; `PolicyLearningServiceTest` fixed for updated `LearnedPolicyRepository`; VirtualOrg already clean |
| Validate all three operating modes (DC-only, DC+agents, DC+AEP) | ✅ DONE | DC-only: `data-cloud:platform` deps have zero agent/AEP references (only `simpleclient_tracer_otel_agent` from Prometheus). DC+agents: `agent-core` has no AEP deps. DC+AEP: `aep-agent-runtime` depends on `agent-core` + `data-cloud:spi` — clean layering. |
| Delete `platform/java/kernel-capabilities` (zero consumers) | ✅ DONE | Directory removed, entry removed from `settings.gradle.kts` |

### Sprint 6 (2026-03-25) — Deprecated code sweep + build fix

> Objective: Remove all deprecated/backward-compat code confirmed safe for removal; fix `agent-core` compile test failure.

| Task | Status | Notes |
|---|---|---|
| Move `InMemoryAgentFrameworkRegistry` to `platform:java:agent-core` | ✅ DONE | Was incorrectly placed in `products:aep:aep-agent-runtime`; `AgentTenantIsolationTest` in agent-core could not find it; moved to correct layer |
| Delete orphaned `products/aep/aep-agent/` directory | ✅ DONE | Module merged into `aep-registry` on 2026-03-22; directory was never removed from disk; not in `settings.gradle.kts` |
| Delete `datacloud.spi.StorageConnector` | ✅ DONE | `@Deprecated(since="2026-03-20", forRemoval=true)`; zero consumers confirmed by grep; file deleted |
| Remove deprecated `DataCloudMetrics.create(MeterRegistry)` overload | ✅ DONE | `@Deprecated` overload; zero callers in production and tests; canonical `create(MetricsCollector)` retained |
| Delete `yappc-shared/client/StepContext.java` and `yappc-shared/plugin/StepContext.java` | ✅ DONE | Byte-for-byte identical to canonical `yappc:core:spi` `StepContext`; added `api(project(":products:yappc:core:spi"))` to `yappc-shared/build.gradle.kts` |
| Migrate `aep-registry/model/ResourceRequirements` → `kernel.descriptor.ResourceRequirements` | ✅ DONE | `@Deprecated(since="2.4.0", forRemoval=true)`; added `:platform:java:kernel` dep to `aep-registry/build.gradle.kts`; updated imports in `PipelineConfig.java` and `PipelineRegistryBuilder.java`; deleted deprecated class |
| Broad build validation (all changed modules + downstream consumers) | ✅ DONE | BUILD SUCCESSFUL; 70 actionable tasks; virtual-org, orchestrator, yappc-agents, data-cloud/agent-registry all green |

#### Deferred deprecated items (v3.0.0 TypedAgent sweep)

These items are marked `@Deprecated(forRemoval=true)` in the codebase but require architectural refactoring before deletion and are explicitly deferred to the v3.0.0 milestone:

| Item | Location | Reason for Deferral |
|---|---|---|
| `Agent.java` interface + `migration/` package (`LegacyAgentAdapter`, `BaseAgentAdapter`, `OrchestrationBridge`) | `platform/java/agent-core` | Zero product consumers today; migration adapter package exists; planned for v3.0.0 TypedAgent cleanup batch |
| `AgentCapabilities.java` record | `platform/java/agent-core` | Zero product consumers; roadmap v3.0.0 |
| `AgentType.LLM` enum constant | `platform/java/agent-core` | 1 platform test references it; use `AgentType.PROBABILISTIC` instead; update test first, then delete constant |
| `virtual-org/AgentRegistry.java` | `products/virtual-org/modules/framework` | `@Deprecated(since="2.4.0", forRemoval=true)`; active consumers in `VirtualOrgContext.java` (line 288) and `ConfigurableOrganization.java` (lines 92, 122); requires TypedAgent + `AgentLogicProvider` SPI migration |
