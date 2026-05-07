# ADR-020: Agent System Five-Layer Architecture

**Status:** Accepted  
**Date:** 2026-04-07  
**Decision Makers:** Platform Architecture Team  
**Phase:** 8 — Agent System Modernization

## Context

The Ghatana agent system evolved from strong individual pieces — `platform/java/agent-core`,
`products/data-cloud/planes/action/agent-runtime`, `products/data-cloud/extensions/agent-registry` — without a single
coherent operating model binding them together.

This produced four concrete problems:

1. **Documentation drift** — multiple authoritative-looking docs disagreed with each other
   and with code (stale taxonomy, stale module paths, stale ownership tables).
2. **Split registry authority** — agent definitions, releases, and live state lived in at least
   four distinct registry surfaces with no single lifecycle owner.
3. **Partial spec enforcement** — the YAML spec was richer than what the runtime actually
   materialized; governance, evaluation, and assurance were labels rather than runtime
   behavior.
4. **No release model** — there was no versioned, signed, policy-linked `AgentRelease`
   artifact, which made rollback, canary promotion, and audit impossible.

The blueprint (`AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md`) described a target
architecture: treat the system as five explicit layers with clear ownership, artifact
boundaries, and lifecycle contracts. This ADR formalizes that decision.

## Decision

Adopt a **five-layer operating model** for the platform-wide agent system. Each layer has
one authority and one primary home. The boundaries are explicit and enforced by module
structure, Gradle tasks, and ArchUnit rules.

### Layer A — Specification and Release Plane

**Owns:** what an agent is, what version is deployable, and which policy/eval packs are
attached.

**Primary home:** `platform/java/agent-core`

**Artifacts:** `AgentSpec`, `AgentRelease`, `AgentInstanceConfig`, `PolicyPack`,
`EvaluationPack`, `ToolContract`, `MemoryContract`, `AgentCapabilityManifest`

**Rules:**

- `agent-core` is a shared contract layer only. It must not own AEP-specific dispatch logic,
  durable registry infrastructure, or product-specific orchestration behavior.
- Every deployable agent release is represented as an immutable, versioned `AgentRelease`
  record carrying spec digest, policy pack, evaluation pack, signing reference, trust
  metadata, and release lifecycle state.
- Release lifecycle states are: `DRAFT → VALIDATED → SHADOW → CANARY → ACTIVE →
  DEPRECATED → RETIRED` (or `BLOCKED` at any point). Transitions are state-machine
  validated.

### Layer B — Control and Governance Plane

**Owns:** approval, rollout, promotion, policy, tenancy, risk budgets.

**Primary home:** `platform/java/tool-runtime`, `platform/java/workflow`,
`platform/java/policy-as-code`, `platform/java/identity`,
`products/data-cloud/planes/operations/config`

**Rules:**

- All side-effecting tool calls must route through `ToolExecutor` in `tool-runtime`.
- `ApprovalGateway` + `ToolSandbox` + `PolicyAsCodeEngine` + `AgentTraceLedger` are
  composed as one mandatory control path. There is no separate, lighter-weight bypass.
- `platform/java/workflow` is the canonical durable planning runtime. Planning agents compile
  `PlanGraph` → `Workflow`; no product should invent a private orchestration engine.

### Layer C — Execution Plane

**Owns:** live invocation, planning dispatch, retries, assurance, checkpointing, delegation,
HITL escalation.

**Primary home:** `products/data-cloud/planes/action/agent-runtime`, `products/data-cloud/planes/action/orchestrator`,
`products/data-cloud/planes/action/engine`

**Rules:**

- AEP is the default advanced execution runtime across the company.
- `GovernedAgentDispatcher` is the single entry gate: it checks release state, kill switch,
  manifest interaction modes, supervision contracts, and telemetry before delegating to any
  agent implementation.
- Agent packages are loaded and hot-swapped through `AgentPackageLoader` backed by
  `KernelPluginRuntimeManager`. No direct class-loading bypasses this facade.

### Layer D — Memory, Context, and Evaluation Plane

**Owns:** episodic memory, semantic memory, procedural memory, context packing, evaluation
traces, autonomy state, release audit.

**Primary home:** `products/data-cloud/delivery/api`, `products/data-cloud/planes/operations/config`,
`products/data-cloud/extensions/agent-registry`

**Rules:**

- Data Cloud is the single durable store for `AgentRelease`, `EvaluationResult`,
  `MemoryNamespace`, `PromotionEvidence`, `AgentRolloutRecord`, `AgentHandoff`, and
  `SwapHandle`.
- Memory retrieval and context hydration must pass `DataAccessBroker` consent + purpose
  checks before access. Privacy-safe by default; no opt-in required from callers.
- Episodic-to-procedural memory promotion follows a 7-step gated path including evaluation
  gates, policy gates, optional HITL, and a `PromotionEvidence` record.

### Layer E — Product Capability Plane

**Owns:** domain-specific tools, models, adapters, and product workflows.

**Primary home:** `products/audio-video/*`, product-specific provider registries

**Rules:**

- Audio-Video exposes capabilities as `ToolContract`-described `ToolHandler` adapters
  registered with `ToolExecutor`. It does not clone AEP runtime behavior.
- Product capability tools are discoverable through the AEP agent catalog.
- Domain agents (e.g., `AudioTranscriptionAgent`) are implemented as `AbstractTypedAgent`
  with proper `AgentDescriptor` and registered via `AgentLogicProvider` SPI.

### The Key Architectural Rule

> **Agent behavior may be product-owned, but the release contract, telemetry vocabulary,
> and governance envelope must be platform-consistent.**

## Authority Matrix

| Concern | Module | Layer | Owner |
|---------|--------|-------|-------|
| Agent contracts + SPI | `platform/java/agent-core` | A | Platform |
| Advanced runtime (dispatch, governed execution) | `products/data-cloud/planes/action/agent-runtime` | C | AEP |
| Durable registry (releases, metadata) | `products/data-cloud/extensions/agent-registry` | D | Data Cloud |
| Tool governance | `platform/java/tool-runtime` | B | Platform |
| Durable workflow / planning compilation | `platform/java/workflow` | B | Platform |
| Plugin/kernel packaging and isolation | `platform-kernel` | B/C | Platform |
| Multimodal capability tools | `products/audio-video` | E | Audio-Video |

## Rationale

- Keeping `agent-core` as a pure contract + spec layer prevents it from becoming a dumping
  ground for product-specific runtime behavior.
- AEP already has the richest runtime. Formalizing it as the execution layer prevents
  redundant runtime implementations in other products.
- Data Cloud already behaves like a durable registry + memory plane. Formalizing this
  concentrates durable storage in a product with proper schema management (Flyway), SPI
  contracts, and Testcontainers-backed integration tests.
- Treating tool execution as a Layer B concern with `ToolExecutor` as the single mandatory
  path makes governance complete: every side-effecting action is policy-checked, sandboxed,
  approval-routed, and audit-emitted.
- The five-layer model makes cross-cutting trust, privacy, security, and explainability
  tractable: each layer has a clear insertion point for its trust gate (DataAccessBroker in
  D, ToolSandbox/ApprovalGateway in B, GovernedAgentDispatcher in C).

## Consequences

- Products that currently hold lightweight local registry endpoints, local tool maps, or
  local planning engines will need to delegate to the appropriate layer.
- `agent-memory` (if still in the build) must either relocate to Data Cloud Layer D or be
  deleted. Its state must be resolved explicitly in `settings.gradle.kts` and documentation.
- The five-layer model becomes the default for new agent system decisions. Future changes
  that span layers require updating this ADR or creating a superseding ADR.
- ArchUnit and Gradle boundary checks enforce that `agent-core` does not depend on product
  modules, and that product capability layers do not pull AEP execution internals.

## Alternatives Considered

1. **Single shared `platform/java/agent-runtime` monolith.** Rejected. A shared production
   runtime used by multiple products creates tight coupling, merge conflicts, and prevents
   product-specific runtime tuning. AEP's existing advanced runtime is already the right
   execution home.
2. **Per-product agent frameworks.** Rejected. This would reproduce all the drift problems
   the modernization is trying to fix. Products should build on shared platform contracts,
   not fragment them.
3. **Two-layer model (platform contracts + product execution).** Considered but insufficient.
   Memory governance, tool execution governance, and plugin packaging each require their own
   clear ownership. Collapsing them into one product layer would recreate the original
   authority confusion.
