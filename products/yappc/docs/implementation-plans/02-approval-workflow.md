# Approval Workflow — Detailed Implementation Plan

**Priority:** P0 BLOCKER  
**Current State:** 5/10 — approval endpoints now cover list/create/get/approve/reject through `ApprovalHttpHandlers`; full state machine, notifications, risk scoring, and AEP events remain  
**Target State:** 10/10 — Real JDBC-persisted approval workflow with AI-powered risk scoring and routing  
**Estimated Effort:** 4 sprints (~32 engineer-days)

---

## 1. Current State Analysis

### What Exists

| Component | Location | Status |
|-----------|----------|--------|
| `ApprovalRequest.java` (runtime) | `core/agents/runtime/` | Exists — model only |
| `ApprovalDecision.java` | `core/agents/runtime/` | Exists — model only |
| `HumanInTheLoopCoordinatorAgent.java` | `core/agents/runtime/` | Exists — orchestration logic |
| `ApprovalRequest.java` (lifecycle) | `core/services-lifecycle/` | Exists — different model, not unified |
| `HumanApprovalService.java` | `core/services-lifecycle/` | Interface defined |
| `JdbcHumanApprovalService.java` | `core/services-lifecycle/` | JDBC implementation wired into the current lifecycle API slice |
| `ApprovalHttpHandlers.java` | `core/services-lifecycle/` API layer | Exists — live create/get/list/approve/reject handlers |
| `ApprovalHttpHandlersTest.java` | `core/services-lifecycle/` tests | Exists — 12 passing tests covering all current handlers |

### Critical Gaps

1. **Approval API coverage is incomplete** — the current live slice supports list/approve/reject, but not the full create/get/override workflow in this plan
2. **Two `ApprovalRequest` models still exist** — runtime and lifecycle representations remain fragmented
3. **Approval state machine is still shallow** — current flow does not yet model the full pending → reviewing → approved/rejected/expired lifecycle envisioned here
4. **No event emission on approval decisions** — AEP bridge not called
5. **No notification system** — approvers are not notified
6. **No audit trail** for approval decisions
7. **AI risk scoring for approvals** — entirely missing (planned but not started)

---

## 2. Target Architecture

```
[Initiator]                    [Approver]                     [System]
    │                              │                               │
    │ Submit Change Request        │                               │
    ├──────────────────────────────►                               │
    │                              │                               │
    │              CreateApprovalRequest (REST)                    │
    ├──────────────────────────────────────────────────────────────►
    │                              │                 JdbcHumanApprovalService
    │                              │                 ● Persists row (status=PENDING)
    │                              │                 ● AIRiskEvaluator scores request
    │                              │                 ● Routes to approvers via ApprovalRouter
    │                              │                 ● Emits ApprovalCreated event → AEP
    │         Notification         │◄──── NotificationService ─────┤
    │                              │                               │
    │                              │ ReviewApprovalRequest (REST)  │
    │                              ├──────────────────────────────►│
    │                              │                 ● Validates approver permission
    │                              │                 ● Updates status (APPROVED/REJECTED)
    │                              │                 ● Records decision + rationale
    │                              │                 ● Emits ApprovalDecided event → AEP
    │                              │                               │
    │                              │               AepEventBridge fires:
    │                              │               ● PhaseTransitionTrigger (if gate approval)
    │                              │               ● AgentTask (downstream agents)
```

---

## 3. Data Model

### Unified `Approval` Domain Model

**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/domain/Approval.java` [NEW]

```java
/**
 * @doc.type class
 * @doc.purpose Represents a single human-in-the-loop approval instance with full lifecycle state.
 * @doc.layer product
 * @doc.pattern Aggregate Root
 */
public record Approval(
    String approvalId,
    String tenantId,
    String requesterId,
    String projectId,
    String phaseId,
    ApprovalType type,         // PHASE_GATE | CODE_CHANGE | SECURITY_REVIEW | RELEASE
    ApprovalStatus status,     // PENDING | IN_REVIEW | APPROVED | REJECTED | EXPIRED
    String subject,            // Human-readable description
    String context,            // JSON blob: changed files, risk factors, agent outputs
    String assignedTo,         // userId of assigned approver (null = unassigned)
    List<ApprovalDecision> decisions,
    RiskScore riskScore,       // AI-computed risk
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt
) {
    public boolean isPending() { return status == ApprovalStatus.PENDING; }
    public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    public boolean requiresMultipleApprovers() { return riskScore != null && riskScore.level() == RiskLevel.HIGH; }
}
```

**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/domain/ApprovalDecision.java` [MOD — unify with runtime model]

```java
public record ApprovalDecision(
    String decisionId,
    String approvalId,
    String approverId,
    DecisionOutcome outcome,   // APPROVED | REJECTED | DELEGATED | DEFERRED
    String rationale,          // Required for REJECTED; encouraged for all
    Instant decidedAt
) {}
```

### Database Schema

```sql
-- V005__create_approval_tables.sql
CREATE TABLE approvals (
    approval_id   VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL,
    requester_id  VARCHAR(36)  NOT NULL,
    project_id    VARCHAR(36)  NOT NULL,
    phase_id      VARCHAR(100),
    type          VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    subject       TEXT         NOT NULL,
    context       JSONB,
    assigned_to   VARCHAR(36),
    risk_level    VARCHAR(20),
    risk_score    DECIMAL(5,2),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ
);

CREATE TABLE approval_decisions (
    decision_id   VARCHAR(36)  PRIMARY KEY,
    approval_id   VARCHAR(36)  NOT NULL REFERENCES approvals(approval_id),
    approver_id   VARCHAR(36)  NOT NULL,
    outcome       VARCHAR(20)  NOT NULL,
    rationale     TEXT,
    decided_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approvals_tenant_status ON approvals(tenant_id, status);
CREATE INDEX idx_approvals_assigned ON approvals(assigned_to, status);
CREATE INDEX idx_approvals_project ON approvals(project_id, phase_id);
```

---

## 4. Implementation Tasks

### Sprint 1 — Domain & Persistence Layer (8 days)

#### T1.1 — Unify Approval Domain Models [MOD] [M]
Merge `core/agents/runtime/ApprovalRequest.java` and `core/services-lifecycle/ApprovalRequest.java` into one canonical `Approval` aggregate in `core/services-lifecycle/src/.../approval/domain/`.

Update all references in:
- `HumanInTheLoopCoordinatorAgent.java`
- `YappcAgentSystem.java`
- Any other consumers

#### T1.2 — Create Approval Repository Interface [NEW] [S]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/ApprovalRepository.java`

```java
public interface ApprovalRepository {
    Promise<Approval> save(Approval approval);
    Promise<Optional<Approval>> findById(String approvalId, String tenantId);
    Promise<List<Approval>> findPendingByAssignee(String userId, String tenantId);
    Promise<List<Approval>> findByProject(String projectId, String tenantId);
    Promise<List<Approval>> findByStatus(ApprovalStatus status, String tenantId);
    Promise<Approval> update(Approval approval);
    Promise<ApprovalDecision> saveDecision(ApprovalDecision decision);
}
```

#### T1.3 — Implement JDBC Approval Repository [NEW] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/JdbcApprovalRepository.java`

Implement all interface methods using ActiveJ `Promise`-based JDBC (using the existing `platform:java:database` module patterns).

Key implementation points:
- All writes use database transactions (`BEGIN` / `COMMIT` / `ROLLBACK`)
- `save()` generates `approvalId` via `UUID.randomUUID()`
- `update()` uses optimistic locking via `updated_at` timestamp comparison
- `findPendingByAssignee()` checks both `assigned_to = userId` AND `assigned_to IS NULL` (pool approvals)

#### T1.4 — Run and Validate DB Migration [NEW] [S]
**File:** `core/services-lifecycle/src/main/resources/db/migration/V005__create_approval_tables.sql`

Create the Flyway migration. Verify it runs cleanly in the test container used by integration tests.

---

### Sprint 2 — Service Layer (8 days)

#### T2.1 — Create `ApprovalService` Interface [NEW] [S]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/ApprovalService.java`

```java
public interface ApprovalService {
    /** Create a new approval request. Returns the created Approval with assigned approver(s). */
    Promise<Approval> createApproval(CreateApprovalCommand command, UserPrincipal requester);
    
    /** Retrieve approval by ID, scoped to tenant. */
    Promise<Approval> getApproval(String approvalId, UserPrincipal caller);
    
    /** List all pending approvals for the calling user. */
    Promise<List<Approval>> listPendingForUser(UserPrincipal caller);
    
    /** Submit a decision on an approval (APPROVED / REJECTED / DELEGATED). */
    Promise<Approval> decide(String approvalId, ApprovalDecisionCommand command, UserPrincipal approver);
    
    /** Override an approval (requires APPROVAL_OVERRIDE permission). */
    Promise<Approval> override(String approvalId, String rationale, UserPrincipal admin);
    
    /** Expire stale approvals (called by scheduler). */
    Promise<Integer> expireStaleApprovals(Duration olderThan);
}
```

#### T2.2 — Implement `DefaultApprovalService` [NEW] [L]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/DefaultApprovalService.java`

```java
/**
 * @doc.type class
 * @doc.purpose Orchestrates full approval lifecycle: creation, routing, decision, event emission.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultApprovalService implements ApprovalService {
    private final ApprovalRepository repository;
    private final ApprovalRouter router;           // routes to the right approver
    private final AepEventBridge eventBridge;      // emits events on state changes
    private final NotificationService notifications;
    private final SecurityAuditLogger auditLogger;
    
    @Override
    public Promise<Approval> createApproval(CreateApprovalCommand command, UserPrincipal requester) {
        return router.resolveApprovers(command)
            .then(approvers -> {
                Approval approval = buildApproval(command, requester, approvers);
                return repository.save(approval);
            })
            .then(saved -> {
                eventBridge.emit(ApprovalCreatedEvent.from(saved));
                notifications.notifyApprovers(saved);
                auditLogger.logApprovalCreated(saved, requester);
                return Promise.of(saved);
            });
    }
    
    @Override
    public Promise<Approval> decide(String approvalId, ApprovalDecisionCommand command, UserPrincipal approver) {
        return repository.findById(approvalId, approver.tenantId())
            .then(optApproval -> {
                Approval approval = optApproval.orElseThrow(() -> new NotFoundException("Approval not found"));
                validateApproverPermission(approval, approver);
                ApprovalDecision decision = buildDecision(command, approver);
                Approval updated = applyDecision(approval, decision);
                return repository.update(updated)
                    .then(saved -> {
                        repository.saveDecision(decision);
                        eventBridge.emit(ApprovalDecidedEvent.from(saved, decision));
                        auditLogger.logApprovalDecision(saved, decision, approver);
                        return Promise.of(saved);
                    });
            });
    }
}
```

#### T2.3 — Create `ApprovalRouter` [NEW] [M]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/ApprovalRouter.java`

Routing rules (loaded from YAML config or DB):
- `PHASE_GATE` approval → route to users with `phase:advance` permission in the project
- `CODE_CHANGE` → route to project code owners
- `SECURITY_REVIEW` → route to security team role
- `RELEASE` → route to release manager role

```java
public final class ApprovalRouter {
    public Promise<List<String>> resolveApprovers(CreateApprovalCommand command) {
        return switch (command.type()) {
            case PHASE_GATE -> resolvePhaseGateApprovers(command);
            case CODE_CHANGE -> resolveCodeOwners(command);
            case SECURITY_REVIEW -> resolveSecurityReviewers(command);
            case RELEASE -> resolveReleaseManagers(command);
        };
    }
}
```

#### T2.4 — Wire `HumanInTheLoopCoordinatorAgent` to `ApprovalService` [MOD] [M]
**File:** `core/agents/runtime/HumanInTheLoopCoordinatorAgent.java`

Replace any hardcoded approval logic with proper calls to `ApprovalService`. The agent should:
1. Create an approval request via `ApprovalService.createApproval()`
2. Suspend the agent execution (return `Promise` that completes only when approved)
3. Resume execution with the `Approval` result after the human decision

---

### Sprint 3 — API Expansion (8 days)

#### T3.1 — Expand Live Approval HTTP Handlers into Full Workflow API [MOD] [L]

**Current Baseline:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/ApprovalHttpHandlers.java`  
Extend the live handler set to cover create/get/override flows and wire the eventual `ApprovalService` via constructor injection.

```java
/**
 * @doc.type class
 * @doc.purpose REST API controller for approval workflow operations.
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class ApprovalController {
    private final ApprovalService approvalService;
    private final ApprovalDtoMapper mapper;
    
    // POST /api/v1/approvals
    public Promise<HttpResponse> createApproval(HttpRequest request) {
        UserPrincipal caller = TenantContext.getCurrentUser();
        CreateApprovalCommand command = parseBody(request, CreateApprovalCommand.class);
        return approvalService.createApproval(command, caller)
            .map(approval -> HttpResponse.ofCode(201)
                .withBody(mapper.toDto(approval))
                .withHeader(CONTENT_TYPE, "application/json"));
    }
    
    // GET /api/v1/approvals/pending
    public Promise<HttpResponse> listPending(HttpRequest request) {
        UserPrincipal caller = TenantContext.getCurrentUser();
        return approvalService.listPendingForUser(caller)
            .map(approvals -> HttpResponse.ok200()
                .withBody(mapper.toDtoList(approvals)));
    }
    
    // PUT /api/v1/approvals/{approvalId}/decision
    public Promise<HttpResponse> submitDecision(HttpRequest request) {
        UserPrincipal caller = TenantContext.getCurrentUser();
        String approvalId = request.getPathParameter("approvalId");
        ApprovalDecisionCommand command = parseBody(request, ApprovalDecisionCommand.class);
        return approvalService.decide(approvalId, command, caller)
            .map(approval -> HttpResponse.ok200()
                .withBody(mapper.toDto(approval)));
    }
}
```

#### T3.2 — Create `ApprovalDtoMapper` [NEW] [S]
**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/ApprovalDtoMapper.java`

Maps between `Approval` domain objects and JSON DTOs. Keeps domain model free from JSON annotations.

#### T3.3 — Create `CreateApprovalCommand` and `ApprovalDecisionCommand` [NEW] [S]

```java
public record CreateApprovalCommand(
    String projectId,
    String phaseId,
    ApprovalType type,
    String subject,
    String context    // JSON string with additional details
) {}

public record ApprovalDecisionCommand(
    DecisionOutcome outcome,
    String rationale,
    String delegateTo   // userId to delegate to, nullable
) {}
```

#### T3.4 — Register API Routes [MOD] [S]
**File:** Bootstrap/routing file in `core/services-lifecycle/`

```java
router.post("/api/v1/approvals", approvalController::createApproval);
router.get("/api/v1/approvals/pending", approvalController::listPending);
router.get("/api/v1/approvals/:approvalId", approvalController::getApproval);
router.put("/api/v1/approvals/:approvalId/decision", approvalController::submitDecision);
router.put("/api/v1/approvals/:approvalId/override", approvalController::override);
```

Apply `JwtAuthFilter` and `RBACEvaluator` to all routes (from Plan 01).

---

### Sprint 4 — Frontend & AI Integration (8 days)

#### T4.1 — Approval Queue UI Component [NEW] [M]
**File:** `frontend/apps/web/src/features/approvals/ApprovalQueue.tsx`

```typescript
interface ApprovalQueueProps {}

const ApprovalQueue: React.FC<ApprovalQueueProps> = () => {
  const { data: approvals, isLoading } = useQuery({
    queryKey: ['approvals', 'pending'],
    queryFn: fetchPendingApprovals,
    refetchInterval: 30_000,  // Poll every 30s; WebSocket push is Phase 2
  });

  return (
    <div className="space-y-4">
      {approvals?.map(approval => (
        <ApprovalCard
          key={approval.approvalId}
          approval={approval}
          onDecide={handleDecision}
        />
      ))}
    </div>
  );
};
```

#### T4.2 — Approval Detail & Decision Panel [NEW] [M]
**File:** `frontend/apps/web/src/features/approvals/ApprovalCard.tsx`

Display:
- Subject + context summary
- AI risk score badge (LOW/MEDIUM/HIGH with color coding)
- Timeline of decisions so far
- Decision form: radio (Approve/Reject/Delegate) + rationale text area
- Submit button (disabled until rationale filled for REJECT)

#### T4.3 — AI Risk Score Display [NEW] [S]
**File:** `frontend/apps/web/src/features/approvals/RiskScoreBadge.tsx`

```typescript
interface RiskScoreBadgeProps {
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  riskScore: number;
  factors: string[];
}

const RiskScoreBadge: React.FC<RiskScoreBadgeProps> = ({ riskLevel, riskScore, factors }) => {
  const colorMap = {
    LOW: 'bg-green-100 text-green-800',
    MEDIUM: 'bg-yellow-100 text-yellow-800',
    HIGH: 'bg-orange-100 text-orange-800',
    CRITICAL: 'bg-red-100 text-red-800',
  } satisfies Record<typeof riskLevel, string>;

  return (
    <Tooltip content={<ul>{factors.map(f => <li key={f}>{f}</li>)}</ul>}>
      <span className={`px-2 py-1 rounded text-xs font-semibold ${colorMap[riskLevel]}`}>
        {riskLevel} ({riskScore.toFixed(1)})
      </span>
    </Tooltip>
  );
};
```

#### T4.4 — Add AI Risk Evaluator to `DefaultApprovalService` [NEW] [M]

**File:** `core/services-lifecycle/src/main/java/com/ghatana/yappc/lifecycle/approval/ApprovalRiskEvaluator.java`

Uses `YAPPCAIService` to score the risk of the approval:

```java
/**
 * @doc.type class
 * @doc.purpose Uses AI to evaluate the risk level of a pending approval request and suggest routing.
 * @doc.layer product
 * @doc.pattern Risk Evaluator
 */
public final class ApprovalRiskEvaluator {
    private final YAPPCAIService aiService;
    
    public Promise<RiskScore> evaluate(CreateApprovalCommand command) {
        String prompt = buildRiskPrompt(command);
        return aiService.complete(prompt)
            .map(response -> parseRiskScore(response))
            .exceptionally(ex -> RiskScore.fallback(RiskLevel.MEDIUM));  // safe default
    }
    
    private String buildRiskPrompt(CreateApprovalCommand command) {
        return """
            Analyze the following change for risk level and required approval rigor.
            Type: %s
            Subject: %s
            Context: %s
            
            Return JSON: {"level": "LOW|MEDIUM|HIGH|CRITICAL", "score": 0.0-10.0, "factors": ["reason1", "reason2"]}
            """.formatted(command.type(), command.subject(), command.context());
    }
}
```

---

## 5. Testing Requirements

### Unit Tests

| Test | Method Coverage |
|------|----------------|
| `DefaultApprovalServiceTest` | Create, decide (approve/reject/delegate), override, expire |
| `ApprovalRouterTest` | All 4 approval types route correctly |
| `ApprovalRiskEvaluatorTest` | AI returns risk; AI fails → safe default used |
| `JdbcApprovalRepositoryTest` | All CRUD operations + optimistic locking |
| `ApprovalControllerTest` | All endpoints return correct status codes |

### Integration Tests

| Scenario | File |
|----------|------|
| Full flow: create → notify → decide → emit AEP event | `integration-tests/approval/` |
| RBAC: user without `approval:review` cannot decide | `integration-tests/approval/rbac/` |
| Concurrent decisions: optimistic lock prevents double-approve | `integration-tests/approval/concurrency/` |
| AI risk evaluator: LLM unavailable → fallback risk used | `integration-tests/approval/resilience/` |

### No Hardcoded Response Test

```java
@Test
void approvalControllerMustNotReturnHardcodedData() {
    // This test fails if any response contains literal "dummy", "fake", "mock", "hardcoded"
    HttpResponse response = controller.createApproval(buildValidRequest());
    assertThat(response.getBody()).doesNotContainAnyOf("dummy", "fake", "mock", "hardcoded");
}
```

---

## 6. Observability

### Metrics

```
yappc_approval_created_total{type, tenant_id}               counter
yappc_approval_decided_total{type, outcome, tenant_id}      counter
yappc_approval_pending_duration_seconds{type}               histogram
yappc_approval_risk_score{type}                             histogram
yappc_approval_expired_total{type}                          counter
yappc_approval_ai_risk_evaluation_duration_seconds          histogram
```

### Alert Rules

```yaml
# Approval queue growing (approvers not responding)
- alert: ApprovalQueueBacklog
  expr: yappc_approval_pending_count > 20
  for: 1h
  annotations:
    summary: "Approval queue backlog > 20 items"

# High-risk approvals pending without action
- alert: HighRiskApprovalPending
  expr: yappc_approval_pending_by_risk{risk="HIGH"} > 0
  for: 2h
```
