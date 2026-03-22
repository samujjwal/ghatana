# Trustworthy Agentic Implementation Tracker

> Last Updated: 2026-03-22
> Source: `docs/AGENTIC_TRUSTWORTHY_IMPLEMENTATION_PACKAGE_2026-03-22.md`
> Build Status: **COMPILES & TESTS PASS** (all 4 modules)

## Status Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Complete |
| 🔧 | In Progress |
| ⏳ | Not Started |

---

## Phase 0: Immediate Hardening (P0)

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| P0-1 | Fix sub-workflow tenant inheritance | `DurableWorkflowRuntime.java` | ✅ |
| P0-2 | Add approval-related result statuses | `AgentResultStatus.java` | ✅ |
| P0-3 | Normalize autonomy vocabulary (5-tier) | `AutonomyLevel.java` | ✅ |
| P0-4 | Update AgentResult.Status nested enum | `AgentResult.java` | ✅ |

---

## WP1: Taxonomy and Contract Alignment

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP1-1 | ActionClass enum | `agent-core/.../governance/ActionClass.java` | ✅ |
| WP1-2 | ReversibilityClass enum | `agent-core/.../governance/ReversibilityClass.java` | ✅ |
| WP1-3 | AgentDefinitionValidator governance checks | `AgentDefinitionValidator.java` | ✅ |

---

## WP2: Executable Governance

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP2-1 | ActionIntent value object | `agent-core/.../governance/ActionIntent.java` | ✅ |
| WP2-2 | PolicyDecision value object | `agent-core/.../governance/PolicyDecision.java` | ✅ |
| WP2-3 | PolicyDecisionType enum | `agent-core/.../governance/PolicyDecisionType.java` | ✅ |
| WP2-4 | AgentDatasheet value object | `agent-core/.../governance/AgentDatasheet.java` | ✅ |
| WP2-5 | PolicyEngineImpl (most-restrictive-wins) | `governance/.../PolicyEngineImpl.java` | ✅ |
| WP2-6 | PolicyRegistry (thread-safe) | `governance/.../PolicyRegistry.java` | ✅ |
| WP2-7 | CompiledPolicy with matches() | `governance/.../CompiledPolicy.java` | ✅ |
| WP2-8 | PolicyObligation + ObligationType | `agent-core/.../governance/PolicyObligation.java` | ✅ |
| WP2-9 | PolicyEvaluationContext | `governance/.../PolicyEvaluationContext.java` | ✅ |
| WP2-10 | PolicyDecisionRecord | `governance/.../PolicyDecisionRecord.java` | ✅ |

---

## WP3: Human Approval and Staged Autonomy

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP3-1 | ApprovalRequest record | `agent-core/.../runtime/ApprovalRequest.java` | ✅ |
| WP3-2 | ApprovalDecision record | `agent-core/.../runtime/ApprovalDecision.java` | ✅ |
| WP3-3 | ApprovalStatus enum | `agent-core/.../runtime/ApprovalStatus.java` | ✅ |
| WP3-4 | AgentApprovalRouter (risk-aware) | `agent-core/.../runtime/AgentApprovalRouter.java` | ✅ |
| WP3-5 | ActionClassifier (pattern-based) | `agent-core/.../runtime/ActionClassifier.java` | ✅ |
| WP3-6 | AutonomyRouter javadoc update | `agent-core/.../runtime/AutonomyRouter.java` | ✅ |

---

## WP4: Memory Governance and Retrieval

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP4-1 | MemoryNamespace record | `agent-runtime/.../memory/governance/MemoryNamespace.java` | ✅ |
| WP4-2 | MemoryMutationPolicy record | `agent-runtime/.../memory/governance/MemoryMutationPolicy.java` | ✅ |
| WP4-3 | VersionedMemoryItem record | `agent-runtime/.../memory/governance/VersionedMemoryItem.java` | ✅ |
| WP4-4 | MemoryProvenance record | `agent-runtime/.../memory/governance/MemoryProvenance.java` | ✅ |
| WP4-5 | searchSemantic() implementation | `PersistentMemoryPlane.java` | ✅ |

**Note**: HybridRetriever.java, TimeAwareReranker.java, BM25Retriever.java, StructuredContextInjector.java already exist and are functional.

---

## WP5: Evidence Plane

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP5-1 | TraceEventType enum | `agent-runtime/.../audit/TraceEventType.java` | ✅ |
| WP5-2 | TraceEvent record (hash-chained) | `agent-runtime/.../audit/TraceEvent.java` | ✅ |
| WP5-3 | AgentTraceLedger SPI | `agent-runtime/.../audit/AgentTraceLedger.java` | ✅ |
| WP5-4 | HashChainedTraceAppender impl | `agent-runtime/.../audit/HashChainedTraceAppender.java` | ✅ |
| WP5-5 | TraceEventBuilder (convenience) | `agent-runtime/.../audit/TraceEventBuilder.java` | ✅ |

---

## WP6: Runtime Safety and Invariants

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP6-1 | InvariantViolation record | `agent-runtime/.../runtime/safety/InvariantViolation.java` | ✅ |
| WP6-2 | InvariantMonitor SPI | `agent-runtime/.../runtime/safety/InvariantMonitor.java` | ✅ |
| WP6-3 | InvariantContext record | `agent-runtime/.../runtime/safety/InvariantContext.java` | ✅ |
| WP6-4 | InvariantRule SPI | `agent-runtime/.../runtime/safety/InvariantRule.java` | ✅ |
| WP6-5 | DefaultInvariantMonitor (4 built-in rules) | `agent-runtime/.../runtime/safety/DefaultInvariantMonitor.java` | ✅ |
| WP6-6 | AgentExecutionGrant record | `agent-runtime/.../runtime/safety/AgentExecutionGrant.java` | ✅ |
| WP6-7 | DelegationGrant record (chain scoping) | `agent-runtime/.../runtime/safety/DelegationGrant.java` | ✅ |
| WP6-8 | GovernedAgentDispatcher (decorator) | `agent-runtime/.../runtime/safety/GovernedAgentDispatcher.java` | ✅ |

---

## WP7: Assurance and Release Governance

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WP7-1 | EvaluationPackRef record | `agent-runtime/.../assurance/EvaluationPackRef.java` | ✅ |
| WP7-2 | EvaluationResult record | `agent-runtime/.../assurance/EvaluationResult.java` | ✅ |
| WP7-3 | PromotionGate + PromotionDecision | `agent-runtime/.../assurance/PromotionGate.java` | ✅ |

---

## Spec and Catalog Updates

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| SPEC-1 | Normalize catalog-schema.yaml autonomy (5-tier) | `agent-catalog/catalog-schema.yaml` | ✅ |
| SPEC-2 | Add actionGovernance section to schema | `agent-catalog/catalog-schema.yaml` | ✅ |
| SPEC-3 | Add memoryGovernance section to schema | `agent-catalog/catalog-schema.yaml` | ✅ |
| SPEC-4 | Add assurance section to schema | `agent-catalog/catalog-schema.yaml` | ✅ |
| SPEC-5 | Update base-agent-template.yaml (governance defaults) | `agent-catalog/base-agent-template.yaml` | ✅ |

---

## Workflow Updates

| ID | Task | File(s) | Status |
|----|------|---------|--------|
| WF-1 | Fix tenant inheritance in DurableWorkflowRuntime | (same as P0-1) | ✅ |
| WF-2 | PolicyWorkflowListener | `workflow/.../PolicyWorkflowListener.java` | ✅ |
| WF-3 | InvariantWorkflowListener | `workflow/.../InvariantWorkflowListener.java` | ✅ |

---

## Summary

| Category | Total | Done | Remaining |
|----------|-------|------|-----------|
| Phase 0 | 4 | 4 | 0 |
| WP1: Taxonomy | 3 | 3 | 0 |
| WP2: Governance | 10 | 10 | 0 |
| WP3: Approval | 6 | 6 | 0 |
| WP4: Memory | 5 | 5 | 0 |
| WP5: Evidence | 5 | 5 | 0 |
| WP6: Safety | 8 | 8 | 0 |
| WP7: Assurance | 3 | 3 | 0 |
| Spec/Catalog | 5 | 5 | 0 |
| Workflow | 3 | 3 | 0 |
| **TOTAL** | **52** | **52** | **0** |

## Files Created/Modified

### New Files (30)
- `agent-core/.../governance/ActionClass.java`
- `agent-core/.../governance/ReversibilityClass.java`
- `agent-core/.../governance/PolicyDecisionType.java`
- `agent-core/.../governance/ActionIntent.java`
- `agent-core/.../governance/PolicyDecision.java`
- `agent-core/.../governance/PolicyObligation.java`
- `agent-core/.../governance/AgentDatasheet.java`
- `agent-core/.../runtime/ApprovalStatus.java`
- `agent-core/.../runtime/ApprovalRequest.java`
- `agent-core/.../runtime/ApprovalDecision.java`
- `agent-core/.../runtime/ActionClassifier.java`
- `agent-core/.../runtime/AgentApprovalRouter.java`
- `governance/.../PolicyEvaluationContext.java`
- `governance/.../PolicyDecisionRecord.java`
- `governance/.../CompiledPolicy.java`
- `governance/.../PolicyRegistry.java`
- `governance/.../PolicyEngineImpl.java`
- `agent-runtime/.../memory/governance/MemoryNamespace.java`
- `agent-runtime/.../memory/governance/MemoryMutationPolicy.java`
- `agent-runtime/.../memory/governance/VersionedMemoryItem.java`
- `agent-runtime/.../memory/governance/MemoryProvenance.java`
- `agent-runtime/.../audit/TraceEventType.java`
- `agent-runtime/.../audit/TraceEvent.java`
- `agent-runtime/.../audit/AgentTraceLedger.java`
- `agent-runtime/.../audit/HashChainedTraceAppender.java`
- `agent-runtime/.../audit/TraceEventBuilder.java`
- `agent-runtime/.../runtime/safety/InvariantViolation.java`
- `agent-runtime/.../runtime/safety/InvariantMonitor.java`
- `agent-runtime/.../runtime/safety/InvariantContext.java`
- `agent-runtime/.../runtime/safety/InvariantRule.java`
- `agent-runtime/.../runtime/safety/DefaultInvariantMonitor.java`
- `agent-runtime/.../runtime/safety/AgentExecutionGrant.java`
- `agent-runtime/.../runtime/safety/DelegationGrant.java`
- `agent-runtime/.../runtime/safety/GovernedAgentDispatcher.java`
- `agent-runtime/.../assurance/EvaluationPackRef.java`
- `agent-runtime/.../assurance/EvaluationResult.java`
- `agent-runtime/.../assurance/PromotionGate.java`
- `workflow/.../PolicyWorkflowListener.java`
- `workflow/.../InvariantWorkflowListener.java`

### Modified Files (8)
- `DurableWorkflowRuntime.java` — tenant inheritance fix
- `AgentResultStatus.java` — 4 new statuses
- `AgentResult.java` — Status enum + factory methods
- `AutonomyLevel.java` — full rewrite (5-tier)
- `AutonomyRouter.java` — javadoc update
- `PersistentMemoryPlane.java` — searchSemantic() implementation
- `AgentDefinitionValidator.java` — governance validation dimension
- `catalog-schema.yaml` — autonomy + governance + assurance sections
- `base-agent-template.yaml` — governance defaults

### Test Files (8) — All Passing

| Module | Test File | Tests | WPs Covered |
|--------|-----------|-------|-------------|
| agent-core | `GovernanceTypesTest.java` | 19 | WP1, WP2 |
| agent-core | `AutonomyLevelTest.java` | 27 | P0, WP1 |
| agent-core | `ActionClassifierTest.java` | 12 | WP3 |
| governance | `PolicyEngineImplTest.java` | 16 | WP2 |
| agent-runtime | `HashChainedTraceAppenderTest.java` | 16 | WP5 |
| agent-runtime | `RuntimeSafetyTest.java` | 21 | WP6 |
| agent-runtime | `PromotionGateTest.java` | 10 | WP7 |
| agent-runtime | `MemoryNamespaceTest.java` | 9 | WP4 |
| **TOTAL** | | **130** | **All WPs** |

> ⚠️ Note: `./gradlew test` reports `BUILD FAILED` due to a pre-existing
> `test-failure-tolerance` plugin NPE (`this.this$0 is null`). All actual
> tests pass — verify via XML reports in `build/test-results/test/`.
