# Agent System Documentation

> **Owner:** Platform Team / AEP Team | **Status:** Active | **Last Updated:** 2026-04-14

---

## Documentation Map

| Document | Audience | Content |
|----------|----------|---------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Architects, engineers | Nine-type taxonomy, three-plane model, dispatch system, registry, ADR references |
| [Agent_Implementation_Guide.md](./Agent_Implementation_Guide.md) | Agent developers | Minimal implementation, registration, agent type deep dives, testing |
| [agent-spec.md](./agent-spec.md) | All | YAML specification schema reference (fields, types, examples) |
| [Unified_Self_Learning_Agents_Spec_Merged.md](./Unified_Self_Learning_Agents_Spec_Merged.md) | Architects | Authoritative DSLA / NDSLA theoretical specification (47 sections) |

---

## Quick Links

- **Implement a new agent**: [Agent_Implementation_Guide.md §1](./Agent_Implementation_Guide.md)
- **Understand the taxonomy**: [ARCHITECTURE.md §2](./ARCHITECTURE.md#2-nine-type-agent-taxonomy)
- **Register an agent**: [ARCHITECTURE.md §4](./ARCHITECTURE.md#4-three-tier-dispatch-system)
- **YAML agent spec**: [agent-spec.md](./agent-spec.md)
- **ADR decisions**: [../adr/ADR-001](../adr/ADR-001-typed-agent-framework.md), [ADR-020](../adr/ADR-020-agent-system-five-layer-architecture.md)
- **AEP product docs**: `products/aep/docs/`

### 3.2 DSLA vs NDSLA vs Hybrid (Theory → Practice Mapping)

| Concept        | Theory Document                       | Implementation                   | Use in AEP                     |
| -------------- | ------------------------------------- | -------------------------------- | ------------------------------ |
| **DSLA**       | Deterministic Self-Learning Agent     | `DeterministicAgent` class       | Policy enforcement, validation |
| **NDSLA**      | Non-Deterministic Self-Learning Agent | `ProbabilisticAgent`, `LLMAgent` | LLM reasoning, classification  |
| **Hybrid**     | Two-tier (Explore → Authoritative)    | `HybridAgent` with routing       | Production recommendation      |
| **Transition** | 7-stage hardening path                | `GovernedAgentDispatcher`        | Learning → Production flow     |

---

## 4. Implementation Architecture

### 4.1 Five-Layer Operating Model

See [ADR-020: Agent System Five-Layer Architecture](/docs/adr/ADR-020-agent-system-five-layer-architecture.md) for the full decision record.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  LAYER A: SPECIFICATION AND RELEASE PLANE                                │
│  ├─ AgentSpec, AgentRelease, AgentInstanceConfig, PolicyPack             │
│  ├─ EvaluationPack, ToolContract, MemoryContract                         │
│  ├─ AgentCapabilityManifest (pluggability contract)                      │
│  └─ platform/java/agent-core (contracts only — no product runtime)      │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER B: CONTROL AND GOVERNANCE PLANE                                   │
│  ├─ ToolExecutor + ApprovalGateway + ToolSandbox (mandatory path)        │
│  ├─ PolicyAsCodeEngine + AgentTraceLedger                                │
│  ├─ DurableWorkflowRuntime + PlanCompiler (planning compilation)         │
│  └─ platform/java/tool-runtime, workflow, policy-as-code, identity       │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER C: EXECUTION PLANE                                                │
│  ├─ GovernedAgentDispatcher (single execution gate)                      │
│  ├─ AgentPackageLoader + AgentSwapCoordinator (hot-swap)                 │
│  ├─ CatalogAgentDispatcher (Tier-J / Tier-S / Tier-L resolution)         │
│  └─ products/aep/aep-agent-runtime, orchestrator, aep-engine            │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER D: MEMORY, CONTEXT, AND EVALUATION PLANE                          │
│  ├─ AgentReleaseRepository + DataCloudAgentReleaseRepository             │
│  ├─ MemoryPromotionService (7-step episodic→procedural path)             │
│  ├─ EvaluationResultRepository + MemoryNamespaceRepository               │
│  └─ products/data-cloud/platform-api, agent-registry, platform-config   │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER E: PRODUCT CAPABILITY PLANE                                       │
│  ├─ Audio-Video ToolHandler adapters (STT, TTS, vision, multimodal)      │
│  ├─ AudioTranscriptionAgent + MultimodalAnalysisAgent (domain agents)    │
│  └─ products/audio-video/*, product-specific provider registries         │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Execution Tier Resolution

AEP implements a **three-tier dispatch model** (see `CatalogAgentDispatcher.java`):

| Tier       | Name                 | Resolution Order | Use Case                              |
| ---------- | -------------------- | ---------------- | ------------------------------------- |
| **Tier-J** | Java Implemented     | 1st              | Native Java agents (TypedAgent beans) |
| **Tier-S** | Service Orchestrated | 2nd              | Pipeline with delegation chains       |
| **Tier-L** | LLM Executed         | 3rd              | Prompt-based LLM execution            |

---

## 5. Critical Integration Points

### 5.1 Platform Contracts

| Contract            | Location                                        | Purpose             |
| ------------------- | ----------------------------------------------- | ------------------- |
| **AgentType**       | `platform/java/agent-core/AgentType.java`       | 9-type taxonomy     |
| **TypedAgent**      | `platform/java/agent-core/TypedAgent.java`      | Core interface      |
| **AgentResult**     | `platform/java/agent-core/AgentResult.java`     | Structured output   |
| **AgentDescriptor** | `platform/java/agent-core/AgentDescriptor.java` | Identity & metadata |
| **AgentContext**    | `framework/api/AgentContext.java`               | Execution context   |

### 5.2 AEP Integration

| Component                   | Path                          | Responsibility              |
| --------------------------- | ----------------------------- | --------------------------- |
| **AgentDispatcher**         | `aep-agent-runtime/dispatch/` | Tier resolution & routing   |
| **AgentOperatorFactory**    | `aep-agent-runtime/registry/` | Agent → Operator bridge     |
| **GovernedAgentDispatcher** | `runtime/safety/`             | Invariant checking & safety |
| **CatalogRegistry**         | `aep-registry/`               | Agent catalog persistence   |

### 5.3 Memory System (Agent-Scoped)

| Memory Type           | Interface                   | Implementation Status |
| --------------------- | --------------------------- | --------------------- |
| **Working Memory**    | `framework/api/MemoryStore` | ✅ In-Memory          |
| **Episodic Memory**   | `memory/EpisodicMemory`     | ✅ Via Data Cloud     |
| **Semantic Memory**   | `memory/SemanticMemory`     | ✅ Via Data Cloud     |
| **Procedural Memory** | `memory/ProceduralMemory`   | ✅ Via Data Cloud     |
| **Task-State**        | `framework/checkpoint/`     | ✅ Checkpointed       |

---

## 6. Governance and Safety

### 6.1 Determinism Guarantees

| Guarantee         | Description                         | Implementation                                    |
| ----------------- | ----------------------------------- | ------------------------------------------------- |
| **FULL**          | Same input → same output, always    | `DeterministicAgent`, `DeterminismGuarantee.FULL` |
| **CONFIG_SCOPED** | Deterministic within config version | Versioned agent configs                           |
| **NONE**          | Probabilistic/stochastic            | `ProbabilisticAgent`, LLM agents                  |
| **EVENTUAL**      | Converges over time                 | `AdaptiveAgent`                                   |

### 6.2 Learning Levels (L0-L5)

| Level  | Description                 | Implementation       |
| ------ | --------------------------- | -------------------- |
| **L0** | No learning                 | Static agents        |
| **L1** | Memory-only                 | Episodic writes      |
| **L2** | Retrieval/policy adaptation | AEP learning loop    |
| **L3** | Skill induction             | Procedure extraction |
| **L4** | Planner/prompt optimization | Template evolution   |
| **L5** | Parameter updates           | Adapter tuning       |

### 6.3 Safety Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│  EXPLORATORY LAYER (NDSLA)                                  │
│  ├─ Broad retrieval                                         │
│  ├─ Candidate generation                                    │
│  ├─ Hypothesis formation                                    │
│  └─ Non-authoritative proposals                             │
├─────────────────────────────────────────────────────────────┤
│  VERIFICATION LAYER                                         │
│  ├─ Schema validation                                       │
│  ├─ Invariant checking                                      │
│  ├─ Policy enforcement                                      │
│  └─ Confidence thresholds                                     │
├─────────────────────────────────────────────────────────────┤
│  DETERMINISTIC AUTHORITY (DSLA)                             │
│  ├─ Externally committing actions                           │
│  ├─ Canonical state updates                                  │
│  ├─ Audit logging                                           │
│  └─ Rollback capability                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. Development Guidelines

### 7.1 Creating a New Agent

```java
/**
 * Example: Deterministic validation agent
 *
 * @doc.type class
 * @doc.purpose Validates incoming events against policy rules
 * @doc.layer product
 * @doc.pattern Deterministic Agent
 */
public class PolicyValidationAgent implements TypedAgent<Event, ValidationResult> {

    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("policy-validator")
            .name("Policy Validation Agent")
            .type(AgentType.DETERMINISTIC)
            .determinism(DeterminismGuarantee.FULL)
            .criticality(Criticality.HIGH)
            .autonomyLevel(AutonomyLevel.AUTONOMOUS)
            .build();
    }

    @Override
    public Promise<AgentResult<ValidationResult>> process(AgentContext ctx, Event input) {
        // 1. Canonicalize input
        // 2. Match rules
        // 3. Apply invariants
        // 4. Return deterministic result
    }
}
```

### 7.2 Agent Registration

```java
// In your service module
@Inject
public void registerAgents(CatalogAgentDispatcher dispatcher) {
    dispatcher.registerJavaAgent("policy-validator", new PolicyValidationAgent());
}
```

### 7.3 Agent Specification (YAML)

```yaml
agentSpecVersion: "1.0.0"
metadata:
  id: "policy-validator"
  name: "Policy Validation Agent"
  version: "1.0.0"
  status: active

identity:
  agentType: deterministic
  subtype: policy-engine
  determinismGuarantee: full
  autonomyLevel: autonomous

capabilities:
  declaredCapabilities:
    - id: validate-policy
      inputTypes: [event]
      outputTypes: [validation-result]
      determinismLevel: fully-deterministic

learningModel:
  learningLevel: L1 # Memory-only for audit trails

governance:
  policyRefs:
    - policy.event-validation.v1
```

---

## 8. Migration and Compatibility

### 8.1 From Legacy Agent Interface

| Legacy                  | Modern Replacement         | Migration Path              |
| ----------------------- | -------------------------- | --------------------------- |
| `Agent`                 | `TypedAgent<I,O>`          | Extend `AbstractTypedAgent` |
| `AgentCapabilities`     | `AgentDescriptor`          | Use builder pattern         |
| `process(Event)`        | `process(AgentContext, I)` | Add type parameters         |
| `AgentResult` (untyped) | `AgentResult<O>`           | Generic typing              |

### 8.2 Current Deprecations

| Deprecated         | Replacement                  | Removal Target    |
| ------------------ | ---------------------------- | ----------------- |
| `AgentType.LLM`    | `PROBABILISTIC` + subtype    | v3.0.0            |
| `platform.agent.*` | `platform.java.agent-core.*` | Q2 2026           |
| `Agent` (untyped)  | `TypedAgent`                 | Ongoing migration |

---

## 9. Operational Considerations

### 9.1 Monitoring

| Metric                  | Source                   | Alert Threshold                         |
| ----------------------- | ------------------------ | --------------------------------------- |
| Agent execution latency | `AgentMetrics`           | p99 < 100ms (deterministic), < 5s (LLM) |
| Confidence score        | `AgentResult.confidence` | < 0.7 for review                        |
| Policy violation rate   | Governance plane         | > 0 triggers escalation                 |
| Memory growth           | Memory plane             | Per-tenant limits                       |
| Learning promotion rate | Learning plane           | Baseline tracking                       |

### 9.2 Scaling Patterns

| Pattern    | Agent Type     | Scaling Strategy  |
| ---------- | -------------- | ----------------- |
| Horizontal | Stateless      | Kubernetes HPA    |
| Sharded    | State-local    | Key-based routing |
| Singleton  | State-external | Leader election   |
| Pool       | Composite      | Sub-agent pools   |

---

## 10. References

### Core Specifications

- [Unified Self-Learning Agents Spec](./Unified_Self_Learning_Agents_Spec_Merged.md) - Theory foundation
- [Agent Implementation Guide](./Agent_Implementation_Guide.md) - Practical development
- [AEP Integration Architecture](./AEP_Integration_Architecture.md) - Product integration
- [agent-spec.md](./agent-spec.md) - YAML contract reference
- [Agent System Modernization Blueprint](./AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md) - Current-state review and target architecture

### Architecture Decisions

- [ADR-001: Typed Agent Framework](../adr/ADR-001-typed-agent-framework.md) - Foundation ADR
- [ADR-017: AEP Runtime Hardening](../adr/ADR-017-aep-runtime-hardening.md) - Recent hardening

### AEP Documentation

- [AEP World-Class Report](../../products/aep/docs/AEP_WORLD_CLASS_AGENTIC_EVENT_PROCESSING_REPORT_2026-03-23.md)
- [AEP Deep Audit](../../products/aep/docs/AEP_V2_DEEP_AUDIT_2026-03-19.md)
- [AEP Topology](../../products/aep/docs/TOPOLOGY.md)
- [AEP Operational Runbook](../../products/aep/docs/OPERATIONAL_RUNBOOK.md)

### Platform Contracts

- [platform/java/agent-core](../../../platform/java/agent-core/) - Core implementation
- [platform/agent-catalog](../../../platform/agent-catalog/) - Catalog definitions
- [platform/contracts](../../../platform/contracts/) - Cross-module contracts

---

## 11. Document Changelog

| Date       | Change                                           | Author       |
| ---------- | ------------------------------------------------ | ------------ |
| 2026-04-05 | Created unified master index                     | AI Assistant |
| 2026-04-05 | Integrated DSLA/NDSLA theory with implementation | AI Assistant |
| 2026-04-05 | Mapped AEP 7-phase implementation                | AI Assistant |

---

## 12. Next Steps for Readers

1. **Engineers**: Review [Agent_Implementation_Guide.md](./Agent_Implementation_Guide.md) for code examples
2. **Architects**: Read [Unified_Self_Learning_Agents_Spec_Merged.md](./Unified_Self_Learning_Agents_Spec_Merged.md) for theory
3. **AEP Developers**: Study [AEP_Integration_Architecture.md](./AEP_Integration_Architecture.md)
4. **All**: Bookmark this page for quick navigation

---

**Questions or updates?** Contact: Platform Architecture Team  
**Review cadence:** Quarterly (aligned with boundary audits)
