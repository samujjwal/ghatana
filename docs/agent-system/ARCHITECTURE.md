# Agent System Architecture

> **Owner:** Platform Team / AEP Team | **Status:** Active | **Last Updated:** 2026-04-14

---

## 1. Overview

The Ghatana agent system is a **layered, platform-wide agentic architecture**. It is not a single runtime monolith. Authority and responsibility are split across the five layers accepted in ADR-020.

| Layer | Location | Owns |
|-------|----------|------|
| Specification and Release | `platform/java/agent-core` | `AgentSpec` / `AgentDefinition`, `AgentDescriptor`, nine-type taxonomy, `AgentRelease` |
| Control and Governance | `platform/java/tool-runtime`, `platform/java/workflow`, `platform/java/policy-as-code` | tool governance, policy, approval, workflow control |
| Execution | `products/data-cloud/planes/action/agent-runtime` | dispatch, lifecycle execution, assurance, checkpointing, delegation |
| Memory, Context, and Evaluation | `products/data-cloud/extensions/agent-registry`, Data Cloud memory/runtime modules | memory, evaluation, traces, rollout and promotion evidence |
| Product Capability | `products/*/` | domain-specific logic, tools, personas, roles, and adapters behind platform contracts |

---

## 2. Nine-Type Agent Taxonomy

All agents implement `TypedAgent<I, O>`.

| Type | Determinism | Description | Location |
|------|-------------|-------------|----------|
| `DETERMINISTIC` | 100% | Rules, thresholds, FSMs, policy, pattern matching | `agent-core/deterministic/` |
| `PROBABILISTIC` | 0% | ML models, Bayesian inference, LLMs | `agent-core/probabilistic/` |
| `HYBRID` | Partial | Fast-path deterministic + probabilistic fallback | `agent-core/hybrid/` |
| `ADAPTIVE` | 0% | Multi-armed bandits, Thompson Sampling | `agent-core/adaptive/` |
| `COMPOSITE` | Varies | Ensemble voting, fan-out/fan-in, sub-agent DAGs | `agent-core/composite/` |
| `REACTIVE` | 100% | Low-latency triggers, circuit breakers | `agent-core/reactive/` |
| `STREAM_PROCESSOR` | Varies | Event-driven stateful stream processing | `agent-core/stream/` |
| `PLANNING` | Varies | Goal-directed, HTN, ReAct, workflow orchestration | `agent-core/planning/` |
| `CUSTOM` | Varies | Domain-specific extensible types | Runtime registration |

---

## 3. Three-Plane Architecture (AEP)

AEP is the Execution layer implementation within the five-layer model. Internally it still uses control, execution, and data/learning planes, but those planes are subordinate to ADR-020's repo-wide boundaries.

```
┌─────────────────────────────────────────────────────────────────────┐
│ CONTROL PLANE (AEP Server + Registry)                               │
│  ├─ Agent catalog management (AgentSpec, AgentRelease)              │
│  ├─ Pipeline definition and deployment                              │
│  ├─ Policy governance (GovernedAgentDispatcher)                     │
│  └─ Human-in-the-loop (HITL) workflows                              │
├─────────────────────────────────────────────────────────────────────┤
│ EXECUTION PLANE (AEP Engine + Orchestrator)                         │
│  ├─ Event intake and pattern matching                               │
│  ├─ Pipeline DAG execution                                          │
│  ├─ Three-tier agent dispatch (Tier-J / Tier-S / Tier-L)           │
│  ├─ Checkpoint and recovery                                         │
│  └─ Backpressure and scaling                                        │
├─────────────────────────────────────────────────────────────────────┤
│ DATA & LEARNING PLANE (Event Cloud + Data Cloud)                    │
│  ├─ Event log (immutable)                                           │
│  ├─ Run ledger (execution traces)                                   │
│  ├─ Agent memory (episodic, semantic, procedural)                   │
│  ├─ Learning records (episodes, evaluations, policies)              │
│  └─ Audit and compliance                                            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Three-Tier Dispatch System

### Tier-J: Java-Implemented Agents

- **Resolution order:** First (highest priority)
- **Use case:** Native Java agents with full framework integration
- **Registration:** `CatalogAgentDispatcher.registerJavaAgent("id", new MyAgent())`

### Tier-S: External Service Agents

- **Use case:** agents implemented as HTTP microservices
- **Registration:** via `agent-catalog.yaml` with `endpoint` field

### Tier-L: LLM-Backed Agents

- **Use case:** Language model–backed agents (GPT-4, Anthropic, local models)
- **Registration:** via `agent-catalog.yaml` with `llmConfig` block

---

## 5. Agent Execution Flow

```
Event Ingestion → Pattern Matching → Pipeline Trigger
                                          │
                                          ▼
                                     Agent Step → 3-Tier Dispatch
                                          │              │
                                          │    ┌─────────┴──────────┐
                                          │   Tier-J             Tier-L
                                          │   (Java)             (LLM)
                                          │
                                          ▼
                                   Execution Record
                                          │
                                          ▼
                                    GovernedResult
                            (invariant checked, logged, traced)
```

---

## 6. Registry Endpoints (Canonical)

The AEP Central Registry is the sole registry authority. Products use these endpoints:

| Use | Endpoint |
|-----|----------|
| List agents | `GET /api/v1/agents` (AEP) |
| Execute agent | `POST /api/v1/agents/:agentId/execute` |
| Agent metadata | `GET /api/v1/agents/:agentId` |

**Products must not expose their own `/api/agents` registry endpoints.**

---

## 7. Release Model

An agent lifecycle progresses through formal `AgentRelease` records stored in Data Cloud:

```
DRAFT → VALIDATED → SHADOW → CANARY → ACTIVE → DEPRECATED → RETIRED
                         ↘ BLOCKED from any live state
```

`SHADOW` is runnable for internal evaluation only. `CANARY` and `ACTIVE` may serve responses. Runtime code uses `isRunnable()` and `isResponseServing()` instead of a single ambiguous dispatchability flag.

See [agent-spec.md](./agent-spec.md) for the full YAML schema governing `id`, `name`, `namespace`, `version`, `status`, `owners`, `tags`, and `summary` fields.

---

## 8. Five-Layer Operating Model (ADR-020)

From ADR-020 (adopted 2026-04):

| Layer | Responsibility |
|-------|---------------|
| **Specification and Release** | `AgentSpec` YAML artifacts, `AgentDefinition`, and `AgentRelease` records |
| **Control/Governance** | `GovernedAgentDispatcher`, policy checks, HITL workflow |
| **Execution** | AEP engine — runtime dispatch, DAG, checkpoint |
| **Memory/Context/Evaluation** | Data Cloud — memory, evaluation, audit, learning candidates |
| **Product Capability** | Product-owned domain behavior, roles, personas, tools |

---

## 9. Key Integration Points

| Concern | Platform Component | AEP Integration |
|---------|-------------------|-----------------|
| Runtime interface | `TypedAgent<I,O>` | `aep-agent-runtime` execution |
| Registry metadata | `AgentDescriptor` | `aep-registry` catalog |
| Pipeline step | `EventOperatorCapability` binding | `aep-engine` pipeline steps |
| Safety | Invariant checking | `GovernedAgentDispatcher` |
| Learning | Learning levels L0–L5 | `aep-analytics` feedback loop |
| Memory | `platform/java/agent-memory` SPI | Data Cloud `evidence-store` |

---

## Related Documents

- [README.md](./README.md) — Navigation index for all agent docs
- [IMPLEMENTATION_GUIDE.md](./Agent_Implementation_Guide.md) — Practical coding guide
- [agent-spec.md](./agent-spec.md) — Agent YAML specification schema
- [Unified_Self_Learning_Agents_Spec_Merged.md](./Unified_Self_Learning_Agents_Spec_Merged.md) — DSLA/NDSLA theoretical specification
- [docs/adr/ADR-001-typed-agent-framework.md](../adr/ADR-001-typed-agent-framework.md)
- [docs/adr/ADR-020-agent-system-five-layer-architecture.md](../adr/ADR-020-agent-system-five-layer-architecture.md)
