# Aura Reference Slice: Recommendation Generation

> **Status**: Day 20 Planning Pass + Release Gate Review — COMPLETE  
> **Date**: 2026-01-19  
> **Reference Slice**: Recommendation Generation with Shade Matching & Safety Validation

## 1. Slice Selection Rationale

The Recommendation Generation slice exercises the full AI-native platform surface:

1. **6 agents in single flow**: Discovery → Shade Matching → Ingredient Safety → Community → Commerce → Explanation
2. **All 7 platform layers**: ingestion → knowledge → intelligence → decision → orchestration → delivery → observability
3. **All 5 contract families**: Autonomy, Analytics, Schema, Experience, Packaging (+ API)
4. **AI-native requirements**: explainability, governance, event-driven coordination

## 2. Generic Kernel Services Required

| Service | Module | Aura Use |
|:--------|:------|:---------|
| Agent Framework (BaseAgent, AgentRegistry) | platform/java/agent-framework | Agent lifecycle, registry, turn management |
| Agent Memory (EventLog, retrieval) | platform/java/agent-memory | Event-sourced episodic/semantic memory |
| Event Store + AEP Adapter | kernel/event-store | Cross-process events via AEP boundaries |
| Immutable Audit Trail | kernel/audit-trail | Decision audit for explainability |
| Observability (Micrometer + OTEL) | kernel/observability | Agent execution metrics, decision tracing |
| Multi-Tenancy / IAM | kernel/iam | Tenant isolation, user auth |
| Configuration Engine | kernel/config-engine | Policy templates (ingredient rules, shade ontology) |
| Resilience Patterns | kernel/resilience-patterns | Circuit breaker, timeouts, graceful degradation |
| Schema Registry | kernel/event-store/validation | Event schema versioning + validation |

## 3. Domain-Specific Services (Aura-Owned)

| Agent / Service | Tier | Purpose |
|:---------------|:-----|:--------|
| Discovery Agent | REFLEX | Pattern matching for product discovery, minConfidence 0.7 |
| Shade Matching Agent | REFLEX | Deterministic shade matching, minConfidence 0.85 |
| Ingredient Safety Agent | DELIBERATIVE | LLM-backed safety validation, minConfidence 0.75, HITL for allergen alerts |
| Community Intelligence Agent | DELIBERATIVE | Community signal aggregation, minConfidence 0.70 |
| Commerce Agent | REFLEX | Pricing, availability, deal optimization, minConfidence 0.90 |
| Explanation Agent | DELIBERATIVE | Reason code generation, minConfidence 0.60, HITL for < 0.6 |

## 4. Contract Surface Declarations

### 4.1 AutonomyContract

```
AgentCapabilityDeclarations:
  - Discovery Agent: REFLEX, minConfidence=0.7, humanReview=false
  - Shade Matching: REFLEX, minConfidence=0.85, humanReview=false
  - Ingredient Safety: DELIBERATIVE, minConfidence=0.75, humanReview=true
  - Community Intel: DELIBERATIVE, minConfidence=0.70, humanReview=false
  - Commerce: REFLEX, minConfidence=0.90, humanReview=false
  - Explanation: DELIBERATIVE, minConfidence=0.60, humanReview=true

ModelGovernanceRules:
  - shade_model_promotion: ≥3 validation suite passes required
  - safety_alert_bias_check: bias audit before production deployment
```

### 4.2 AnalyticsContract

| Metric | Type | Tags |
|:-------|:-----|:-----|
| `aura.recommendation.generated_total` | COUNTER | `agent_tier`, `confidence_bucket` |
| `aura.recommendation.served` | COUNTER | `served_position`, `interaction_type` |
| `aura.safety_alert.triggered_total` | COUNTER | `alert_type` |
| `aura.explanation.helpfulness` | GAUGE | `satisfaction_score` |
| `aura.agent_execution_time_ms` | HISTOGRAM | `agent_name`, `percentile` |

### 4.3 SchemaContract

| Event | Compatibility | Format |
|:------|:-------------|:-------|
| RecommendationGeneratedEvent | BACKWARD | JSON Schema v7 |
| RecommendationServedEvent | BACKWARD | JSON Schema v7 |
| UserFeedbackCapturedEvent | BACKWARD | JSON Schema v7 |
| SafetyAlertTriggeredEvent | BACKWARD | JSON Schema v7 |

### 4.4 ExperienceContract

| Screen | Route | Entry Component |
|:-------|:------|:---------------|
| Recommendation Feed | `/products/recommendations` | RecommendationFeed |
| Product Detail + Explanation | `/products/{id}/details` | ProductDetail |
| Profile Preferences | `/profile/preferences` | YouIndexEditor |

### 4.5 API + Packaging Contracts

- **API**: POST/GET `/api/v1/recommendations`, POST `/api/v1/feedback`
- **Pack**: `aura.recommendation`, T1_CORE, depends on `kernel.agent-framework`, `kernel.event-store`, `kernel.audit-trail`

## 5. Release Gate G4: Platform-Readiness Criteria

Per KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md, no architecture is platform-ready until Aura passes:

| # | Gate Criterion | Validated By |
|:--|:---------------|:-------------|
| 1 | AI-native telemetry (agent metrics + decision tracing) | AnalyticsContract + Observability |
| 2 | Explainability (reason codes + audit trail for every recommendation) | AuditTrailStore + Explanation Agent |
| 3 | Event-plane usage (AEP topic registration + schema validation + idempotency) | SchemaContract + SchemaGovernanceValidator |
| 4 | Autonomous controls (AutonomyContract + HITL for high-risk) | AutonomyGovernanceValidator |
| 5 | All 6 agents registered in canonical agent registry | AgentFrameworkRegistry |
| 6 | AutonomyContract validation passes | ContractRegistry + validators |
| 7 | AEP topic registration complete (5 topics) | AEP adapter configuration |
| 8 | Data Cloud schema validated for all managed data paths | SchemaContractBridge |
| 9 | Observability baseline established (dashboards) | AnalyticsContractBridge |
| 10 | Audit trail captures all recommendation decisions | AuditTrailStore + HashChainService |

## 6. Implementation Program Summary

### Architecture Phase (Days 1-20) — COMPLETE

| Phase | Days | Status | Artifacts |
|:------|:-----|:-------|:----------|
| Freeze architecture drift | 1-2 | ✅ | Purity guardrails, deprecation annotations |
| Canonical capability migration | 3-4 | ✅ | descriptor.KernelCapability, KernelPlugin |
| Registry canonicalization | 5 | ✅ | @KernelInternal, sub-registry marking |
| Scope/policy abstractions | 6-9 | ✅ | ScopeDescriptor, CrossScopeAuditService, ScopeBoundaryEnforcer, KernelInterScopeBus |
| Legacy duplicate deprecation | 10 | ✅ | CrossProductConfigResolver, CrossProductModelRegistry deprecated |
| AppPlatform alignment mapping | 11-14 | ✅ | CANONICAL_CAPABILITY_MAPPING.md (6+7+12+35 services classified) |
| Contract system skeleton | 15 | ✅ | 6 contract families + ContractRegistry + ContractValidator |
| Event/schema validation alignment | 16 | ✅ | SchemaContractBridge + SchemaGovernanceValidator |
| AI/analytics/autonomy alignment | 17 | ✅ | AiAutonomyContractBridge + AnalyticsContractBridge + AutonomyGovernanceValidator |
| Finance reference-slice | 18 | ✅ | Post-Trade Settlement slice with 8 generic + 6 domain services |
| PHR reference-slice | 19 | ✅ | Consent Workflow slice with 7 generic + 6 domain services |
| Aura reference-slice + release gate | 20 | ✅ | Recommendation Generation slice + G4 criteria |

### Test Baseline

- **210 total, 205 passed, 0 failed, 5 skipped**
- Kernel + all products compile clean

### Next Phase: Execution

The architecture program has established:
1. **Canonical type system** — all legacy types deprecated with `forRemoval=true`
2. **Contract surface** — 6 families with validation hooks and governance validators
3. **Bridge interfaces** — SchemaContractBridge, AiAutonomyContractBridge, AnalyticsContractBridge
4. **Three reference slices** — Finance (post-trade), PHR (consent), Aura (recommendation)
5. **Release gates** — G4 criteria for platform-readiness

Ready to proceed to implementation phase.
