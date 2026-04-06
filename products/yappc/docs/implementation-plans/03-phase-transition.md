# Phase Transition & Lifecycle — Detailed Implementation Plan

**Priority:** P1 HIGH  
**Current State:** 5/10 — Infrastructure exists (`GateEvaluator`, `AdvancePhaseUseCase`, `TransitionSpec`); frontend phase transitions use manual `if/else` chains; AI gate evaluation not wired  
**Target State:** 10/10 — AI-powered gate evaluation; automated phase readiness; predictive transition timing  
**Estimated Effort:** 4 sprints (~32 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `YappcLifecycleService.java` | `core/services-lifecycle/` | ✅ Main service |
| `AdvancePhaseUseCase.java` | Same | ✅ Use case |
| `GateEvaluator.java` | Same | ✅ Gate evaluation |
| `PhaseGateValidator.java` | Same | ✅ Validation |
| `TransitionConfigLoader.java` | Same | ✅ YAML-driven config |
| `TransitionSpec.java` | Same | ✅ Transition spec model |
| `StageSpec.java` / `StageConfigLoader.java` | Same | ✅ Stage spec |
| `GateOrchestratorOperator.java` | Same | ✅ ActiveJ operator |
| `AgentDispatchOperator.java` | Same | ✅ Agent dispatch |
| `AgentExecutorOperator.java` | Same | ✅ Agent execution |
| `BackpressureOperator.java` | Same | ✅ Backpressure handling |
| `YappcPolicyEngine.java` | Same | ✅ Policy evaluation |
| `AepEventBridge.java` | Same | ✅ AEP event emission |
| `DurableEventCloudPublisher.java` | Same | ✅ Durable publishing |
| `YappcAiService.java` | Same | ✅ Ollama-based AI for lifecycle |
| `ToolAwareOllamaCompletionService.java` | Same | ✅ Tool-aware completions |
| 8 `PhaseType` phases | `core/yappc-services/domain/` | ✅ Intent→Evolve |
| 8 phase service impls | `core/yappc-services/` | ✅ All 8 implemented |
| Frontend `deploy.tsx` | `frontend/apps/web/src/` | ⚠️ Hardcoded if/else (line 85-89) |
| AI readiness assessment | — | **MISSING** |
| Automated gate agents | — | **MISSING** (agents exist but not wired as gates) |
| Rollback capability | — | **MISSING** |

### The `deploy.tsx` Problem (Line 85-89)

```typescript
// CURRENT — manual if/else chain
if (phase === 'Build') {
  nextPhase = 'Test';
} else if (phase === 'Test') {
  nextPhase = 'Release';
} // ...
```

This must be replaced with an API call that consults `YappcLifecycleService`.

---

## 2. Target Architecture

```
User or Agent requests phase advance
  │
  ▼
PhaseTransitionController.advance()
  ├── Auth: caller must have PHASE_ADVANCE permission
  ├── Load TransitionSpec for (currentPhase → targetPhase)
  │
  ▼
AdvancePhaseUseCase.execute()
  ├── AIReadinessAssessor.assess()
  │     ├── Query YAPPCAIService with project context
  │     ├── Returns: ReadinessReport{ready, warnings, blockers, confidence}
  │     └── If NOT ready → return ReadinessReport to caller (don't advance)
  │
  ├── GateEvaluator.evaluate(gates: List<GateSpec>)
  │     ├── For each gate: call the registered GateAgent
  │     ├── Gates: test coverage %, code review status, security scan, docs done
  │     └── Aggregate: pass/fail with per-gate detail
  │
  ├── If all gates pass:
  │     ├── AepEventBridge.emit(PhaseTransitionEvent)
  │     ├── YappcLifecycleService.updatePhase(projectId, newPhase)
  │     └── Emit notification to project collaborators
  │
  └── If any gate fails:
        ├── Return detailed failure: which gates, why, how to fix
        └── Optionally trigger ApprovalService for override
```

---

## 3. The 8-Phase Model

```
Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
```

### Phase Gate Requirements

| Phase | → Next Phase | AI Gates | Hard Gates |
|-------|-------------|----------|------------|
| Intent | Shape | AI: requirements clarity score ≥ 0.7 | All requirements have acceptance criteria |
| Shape | Validate | AI: design completeness score ≥ 0.8 | Architecture ADR approved |
| Validate | Generate | AI: validation coverage ≥ 80% | Security review passed |
| Generate | Run | AI: code quality score ≥ 0.75 | Test coverage ≥ 80% |
| Run | Observe | AI: deployment health ≥ 0.9 | Smoke tests passed |
| Observe | Learn | AI: observability completeness ≥ 0.8 | SLO targets defined |
| Learn | Evolve | AI: learning synthesis complete | Retrospective documented |
| Evolve | Intent (next cycle) | AI: evolution plan quality ≥ 0.7 | Team review completed |

---

## 4. Implementation Tasks

### Sprint 1 — AI Readiness Assessor (8 days)

#### T1.1 — Create `AIReadinessAssessor` [NEW] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/assessment/AIReadinessAssessor.java`

```java
/**
 * @doc.type class
 * @doc.purpose Uses AI to assess whether a project is ready to advance to the next lifecycle phase.
 * @doc.layer product
 * @doc.pattern Assessor
 */
public final class AIReadinessAssessor {
    private final YAPPCAIService aiService;
    private final ProjectContextBuilder contextBuilder;
    
    public Promise<ReadinessReport> assess(String projectId, PhaseType currentPhase, PhaseType targetPhase) {
        return contextBuilder.build(projectId, currentPhase)
            .then(ctx -> {
                String prompt = buildReadinessPrompt(ctx, currentPhase, targetPhase);
                return aiService.complete(AIRequest.of(prompt).withWorkflow("phase_readiness"));
            })
            .map(response -> parseReadinessReport(response));
    }
    
    private String buildReadinessPrompt(ProjectContext ctx, PhaseType from, PhaseType to) {
        return """
            Assess readiness to advance from %s to %s phase.
            
            Project context:
            - Requirements completed: %d/%d
            - Open issues: %d
            - Test coverage: %.1f%%
            - Last build status: %s
            - Code review backlog: %d PRs
            
            Return JSON: {
              "ready": boolean,
              "confidence": 0.0-1.0,
              "score": 0.0-10.0,
              "blockers": ["...", "..."],
              "warnings": ["...", "..."],
              "recommendations": ["...", "..."]
            }
            """.formatted(
                from, to,
                ctx.completedRequirements(), ctx.totalRequirements(),
                ctx.openIssues(),
                ctx.testCoverage(),
                ctx.lastBuildStatus(),
                ctx.pendingReviews()
            );
    }
}
```

#### T1.2 — Create `ReadinessReport` Value Object [NEW] [S]
```java
public record ReadinessReport(
    boolean ready,
    double confidence,
    double score,
    List<String> blockers,
    List<String> warnings,
    List<String> recommendations
) {
    public boolean hasBlockers() { return !blockers.isEmpty(); }
}
```

#### T1.3 — Create `ProjectContextBuilder` [NEW] [M]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/assessment/ProjectContextBuilder.java`

Aggregates project state from:
- Requirements repository (count, completion)
- Issue tracker (open/closed issue counts)
- Build system (last build status, coverage)
- Code review queue (pending PRs)

Returns `ProjectContext` record with all signals for the AI prompt.

---

### Sprint 2 — AI-Powered Gate Evaluation (8 days)

#### T2.1 — Define Gate Types and Gate Specs [MOD] [M]
**File:** `config/agents/phase-transition-events.yaml` [MOD]

Update YAML gate specs to include AI evaluation parameters:

```yaml
gates:
  - id: test-coverage-gate
    phase: GENERATE
    type: AI_EVALUATED
    agent: testing-specialists
    threshold: 0.80
    prompt_template: test_coverage_assessment
    hard_fail: true        # fails the entire transition
    
  - id: requirements-clarity-gate
    phase: INTENT
    type: AI_EVALUATED
    agent: planning-agent
    threshold: 0.70
    prompt_template: requirements_clarity_assessment
    hard_fail: false       # produces warning only
    
  - id: security-scan-gate
    phase: VALIDATE
    type: DETERMINISTIC    # not AI-evaluated; binary pass/fail from scan report
    checker: SecurityScanResultChecker
    hard_fail: true
```

#### T2.2 — Enhance `GateEvaluator` with AI Agent Dispatch [MOD] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/gate/GateEvaluator.java`

```java
public Promise<GateEvaluationResult> evaluate(List<GateSpec> gates, ProjectContext ctx) {
    List<Promise<SingleGateResult>> evaluations = gates.stream()
        .map(gate -> evaluateGate(gate, ctx))
        .toList();
    
    return Promise.ofCallback(callback ->
        Promises.all(evaluations)
            .map(results -> GateEvaluationResult.aggregate(results))
            .whenComplete(callback)
    );
}

private Promise<SingleGateResult> evaluateGate(GateSpec gate, ProjectContext ctx) {
    return switch (gate.type()) {
        case AI_EVALUATED -> agentDispatcher.dispatch(gate.agent(), ctx)
            .map(agentResult -> SingleGateResult.fromAgent(gate, agentResult));
        case DETERMINISTIC -> deterministic
            .check(gate.checker(), ctx)
            .map(passed -> SingleGateResult.binary(gate, passed));
        case APPROVAL_REQUIRED -> approvalService
            .createApproval(buildApprovalCommand(gate, ctx), systemPrincipal)
            .map(approval -> SingleGateResult.pendingApproval(gate, approval));
    };
}
```

#### T2.3 — Remove `deploy.tsx` Hardcoded Phase Logic [MOD] [M]
**File:** `frontend/apps/web/src/pages/deploy.tsx` (lines 85-89 and surrounding)

Replace hardcoded `if/else` with API call:

```typescript
// BEFORE
if (phase === 'Build') { nextPhase = 'Test'; }

// AFTER
const handleAdvancePhase = useCallback(async () => {
  const result = await lifecycleApi.advancePhase({
    projectId: currentProject.id,
    currentPhase: currentPhase,
  });
  
  if (result.type === 'ADVANCED') {
    setPhase(result.newPhase);
    showToast('Phase advanced successfully');
  } else if (result.type === 'NOT_READY') {
    setReadinessReport(result.readinessReport);
    showReadinessModal(result.readinessReport);
  } else if (result.type === 'GATES_FAILED') {
    setGateFailures(result.gateResults);
    showGateFailurePanel(result.gateResults);
  }
}, [currentProject.id, currentPhase]);
```

#### T2.4 — Create `PhaseGateResultPanel` UI [NEW] [M]
**File:** `frontend/apps/web/src/features/lifecycle/PhaseGateResultPanel.tsx`

Display gate evaluation results to the user:
- ✅ Passed gates (green)
- ⚠️ Warning gates (yellow, not blocking)  
- ❌ Failed gates (red, with explanation and fix link)
- ⏳ Pending approval gates (with link to approval queue)
- AI readiness score (0-10) with confidence indicator

---

### Sprint 3 — Rollback & History (8 days)

#### T3.1 — Implement Phase History Persistence [NEW] [M]

```sql
-- V006__phase_transition_history.sql
CREATE TABLE phase_transitions (
    transition_id   VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    project_id      VARCHAR(36) NOT NULL,
    from_phase      VARCHAR(50) NOT NULL,
    to_phase        VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- COMPLETED | ROLLED_BACK | FAILED
    triggered_by    VARCHAR(36) NOT NULL,
    ai_score        DECIMAL(4,2),
    ai_confidence   DECIMAL(4,3),
    gate_results    JSONB,
    started_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    notes           TEXT
);

CREATE INDEX idx_phase_transitions_project ON phase_transitions(project_id, started_at DESC);
```

**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/PhaseTransitionRepository.java` [NEW]

#### T3.2 — Implement Rollback Use Case [NEW] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/RollbackPhaseUseCase.java`

```java
/**
 * @doc.type class
 * @doc.purpose Rolls back a project to the previous lifecycle phase, reversing artifacts and gates.
 * @doc.layer product
 * @doc.pattern Use Case
 */
public final class RollbackPhaseUseCase {
    
    public Promise<RollbackResult> execute(String projectId, String rationale, UserPrincipal initiator) {
        // 1. Verify caller has PHASE_ROLLBACK permission
        rbacEvaluator.requirePermission(initiator, YappcPermission.PHASE_ROLLBACK);
        
        // 2. Load transition history
        return phaseTransitionRepository.findLastTransition(projectId, initiator.tenantId())
            .then(lastTransition -> {
                // 3. Revert phase in project
                // 4. Create rollback approval if phase was RELEASE or DEPLOY
                // 5. Emit PhaseRolledBackEvent to AEP
                // 6. Notify collaborators
                // 7. Log audit entry
            });
    }
}
```

#### T3.3 — Create Phase History UI [NEW] [M]
**File:** `frontend/apps/web/src/features/lifecycle/PhaseHistoryTimeline.tsx`

Display the full phase transition history for a project:
- Chronological timeline of transitions
- AI score at each transition
- Gate results summary
- Who triggered each transition
- Rollback button for last transition (with confirmation)

---

### Sprint 4 — AI Predictive Timing (8 days)

#### T4.1 — Implement `PhaseProgressionPredictor` [NEW] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/prediction/PhaseProgressionPredictor.java`

```java
/**
 * @doc.type class
 * @doc.purpose Predicts when a project will be ready to advance to the next phase using historical data and AI.
 * @doc.layer product
 * @doc.pattern Predictor
 */
public final class PhaseProgressionPredictor {
    private final YAPPCAIService aiService;
    private final PhaseTransitionRepository historyRepository;
    
    /**
     * Returns a prediction of how many days until the project can advance.
     */
    public Promise<PhaseProgressionPrediction> predict(String projectId, PhaseType currentPhase) {
        return historyRepository.findAverageTransitionDuration(currentPhase)
            .then(avgDuration -> {
                String prompt = buildPredictionPrompt(projectId, currentPhase, avgDuration);
                return aiService.complete(AIRequest.of(prompt).withWorkflow("phase_prediction"));
            })
            .map(response -> parsePrediction(response));
    }
}
```

#### T4.2 — Show Progress Predictions in Dashboard [NEW] [M]
**File:** `frontend/apps/web/src/features/lifecycle/PhaseProgressionWidget.tsx`

Show in project dashboard:
- Current phase + time in current phase
- AI prediction: "Estimated X days until ready for [next phase]"
- Confidence bar
- Top 3 blockers slowing progress

#### T4.3 — Predictive Alert for Stuck Projects [NEW] [M]
**File:** `core/services-lifecycle/src/.../lifecycle/prediction/StuckProjectDetector.java`

Scheduled job (daily) that:
1. Queries all active projects
2. For each: checks days in current phase vs. average
3. If `current_days > avg_days * 1.5` → fires `ProjectProgressAlert` event → notification to PM

```java
// Triggered by scheduled AEP pipeline event
public Promise<Void> detectStuckProjects() {
    return projectRepository.findAllActive()
        .then(projects -> Promises.all(projects.stream()
            .map(this::checkProgressForProject)
            .toList()))
        .toVoid();
}
```

---

## 5. Testing Requirements

| Test | Scenarios |
|------|-----------|
| `AIReadinessAssessorTest` | Ready project advances; not-ready project returns blockers |
| `GateEvaluatorTest` | All gate types; mixed pass/fail; approval-pending gate |
| `AdvancePhaseUseCaseTest` | Successful advance; gate failure; not-ready from AI |
| `RollbackPhaseUseCaseTest` | Rollback from any phase; permission denied |
| `PhaseProgressionPredictorTest` | Prediction returned; AI failure → fallback estimate |
| `StuckProjectDetectorTest` | Detects stuck; not-stuck project ignored |

### Frontend Tests

| Test | File |
|------|------|
| `PhaseGateResultPanel.test.tsx` | Renders all gate states |
| `deploy.tsx` — replaced API call | Advance success; gates failed; not ready |
| `PhaseHistoryTimeline.test.tsx` | Timeline renders; rollback button shown |

---

## 6. Observability

```
yappc_phase_transitions_total{from, to, status, tenant_id}
yappc_phase_gate_evaluations_total{gate_id, phase, result}
yappc_phase_transition_duration_seconds{from, to}
yappc_phase_ai_readiness_score{phase, tenant_id}             histogram
yappc_phase_rollbacks_total{from_phase, tenant_id}
yappc_phase_stuck_projects_detected_total
yappc_phase_prediction_accuracy{phase}                       gauge (updated periodically)
```
