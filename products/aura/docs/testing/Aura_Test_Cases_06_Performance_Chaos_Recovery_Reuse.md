# Aura Test Cases 06: Performance, Chaos, Recovery, and Shared Reuse

Version: 1.0
Date: March 13, 2026

## Scope

This suite closes the hardening gap between functional correctness and production readiness.

It covers:

- burst, load, stress, and soak behavior
- capacity limits, backpressure, and queue recovery
- dependency outages and graceful degradation
- backup, restore, schema migration, and replay safety
- concurrency and duplicate-submission integrity
- regression protection when Aura reuses shared Ghatana libraries and services

---

## A. Load, Stress, Soak, and Capacity

### AURA-OPS-034 Recommendation API survives burst traffic above expected peak without serving cross-user data
Level: Non-Functional
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_System_Architecture.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Load profile with burst traffic above expected beta peak.
Steps:
1. Run burst test against recommendation endpoint.
2. Inspect latency, error rate, and cache behavior.
Expected:
- Service sheds or absorbs burst safely.
- No cross-user response leakage occurs.
- Recovery to steady-state latency is automatic after the spike.

### AURA-OPS-035 Recommendation path passes sustained soak test without memory or file-handle leak
Level: Non-Functional
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Production-like traffic profile for at least 24 hours.
Steps:
1. Run soak workload across feed, detail, compare, and recommendation query.
2. Inspect memory, GC, open handles, and error accumulation.
Expected:
- Memory growth remains within agreed envelope.
- No progressive latency degradation or resource exhaustion occurs.

### AURA-OPS-036 Mixed user workload keeps feed, detail, compare, and assistant within service targets
Level: Non-Functional
Priority: P1
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: Mixed traffic profile reflecting real user path distribution.
Steps:
1. Run concurrent traffic for feed, product detail, compare, and assistant routes.
Expected:
- Interactive routes remain within documented latency and availability targets.
- One busy surface does not starve the others.

### AURA-OPS-037 Export jobs and ingestion jobs never starve interactive recommendation traffic
Level: Non-Functional
Priority: P1
Source Docs: `Aura_Data_Architecture.md`, `Aura_Engineering_Sprint_Plan_6_Months.md`
Preconditions: Large export queue and active ingestion backlog.
Steps:
1. Start batch export and ingestion workloads.
2. Run interactive recommendation traffic at the same time.
Expected:
- Interactive traffic preserves agreed SLOs.
- Background work is throttled or queued appropriately.

### AURA-OPS-038 Ingestion spike drains backlog predictably after upstream source flood
Level: Non-Functional
Priority: P1
Source Docs: `Aura_System_Architecture.md`, `Aura_Data_Architecture.md`
Preconditions: Source volume exceeds normal daily ingest by several multiples.
Steps:
1. Inject high-volume source batch.
2. Observe queue depth, lag, and freshness recovery.
Expected:
- Backlog drains within documented recovery window.
- Freshness scores recover after backlog clears.

### AURA-OPS-039 Event consumers apply backpressure instead of failing open under queue pressure
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Consumer lag and queue pressure artificially increased.
Steps:
1. Slow consumers.
2. Continue publishing events.
Expected:
- Backpressure or controlled throttling is observable.
- No silent event loss or uncontrolled duplicate processing occurs.

### AURA-OPS-040 Large catalog reindex or embedding rebuild does not corrupt live retrieval quality
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Existing live recommendation traffic and index rebuild job.
Steps:
1. Start full rebuild or major refresh.
2. Continue serving recommendation/search traffic.
Expected:
- Live retrieval remains available.
- Cutover or dual-read behavior prevents empty or corrupted result sets.

---

## B. Chaos, Dependency Failure, and Disaster Recovery

### AURA-OPS-041 Data Cloud cache-plugin outage degrades recommendation and session paths gracefully
Level: Integration
Priority: P0
Source Docs: `Aura_Technical_Stack_Blueprint.md`, `Aura_System_Architecture.md`
Preconditions: Data Cloud cache or rate-limit plugin can be disabled.
Steps:
1. Make the Data Cloud cache plugin unavailable.
2. Exercise feed, recommendation, and auth-dependent flows.
Expected:
- Aura serves uncached or reduced-quality paths where safe.
- Security and user isolation remain intact.

### AURA-OPS-042 Data Cloud relational-plugin interruption or failover does not create partial recommendation writes
Level: Integration
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Controlled Data Cloud relational-plugin interruption or failover event.
Steps:
1. Interrupt the Data Cloud relational path during recommendation logging and outcome capture.
2. Restore connectivity.
Expected:
- Partial writes are rolled back or retried safely.
- Recommendation ledger and outcome records remain consistent.

### AURA-OPS-043 Data Cloud object-storage plugin outage preserves ingestion and export intent without silent data loss
Level: Integration
Priority: P1
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Snapshot or export storage dependency in Data Cloud unavailable.
Steps:
1. Trigger raw payload write and export packaging during outage.
Expected:
- Work is retried, queued, or fails loudly with operator visibility.
- No successful status is reported without durable storage.

### AURA-OPS-044 AEP outage does not silently drop recommendation or outcome events
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: AEP publish path can be interrupted.
Steps:
1. Disable AEP during recommendation serving and outcome submission.
Expected:
- Publish failures are observable.
- Events are retried, queued, or transactionally compensated.
- No silent success without event persistence guarantee.

### AURA-OPS-045 Shared auth service or gateway degradation fails securely
Level: Integration
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Shared auth dependency available for fault injection.
Steps:
1. Degrade token validation or session lookup dependency.
2. Exercise login, authenticated query, export, and delete-account flows.
Expected:
- Failures are secure by default.
- Re-auth requirements for sensitive actions remain enforced.

### AURA-OPS-046 Third-party source slowness is bounded by timeout, retry, and circuit-breaker behavior
Level: Integration
Priority: P1
Source Docs: `Aura_System_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Slow retailer or brand source fixture.
Steps:
1. Run source ingestion against slow dependency.
Expected:
- Timeout and retry policy activates.
- Slow dependency does not stall unrelated ingest pipelines indefinitely.

### AURA-OPS-047 Backup and restore procedure meets defined RPO and RTO
Level: Non-Functional
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Backup snapshots and restore environment available.
Steps:
1. Restore Data Cloud relational and object-storage state from backup.
2. Validate core recommendation, profile, and audit data.
Expected:
- Restore completes within recovery objective.
- Data loss stays within allowed recovery point window.

### AURA-OPS-048 Rolling deployment with schema change remains backward compatible during cutover
Level: Integration
Priority: P0
Source Docs: `Aura_Database_Schema_Prisma.md`, `Aura_Monorepo_Structure.md`
Preconditions: Old and new application versions plus compatible migration plan.
Steps:
1. Deploy schema and app in rolling order.
2. Exercise old and new nodes during overlap.
Expected:
- Both versions operate safely during cutover window.
- No contract or migration mismatch breaks live traffic.

---

## C. Concurrency, Idempotency, and Data Integrity

### AURA-OPS-049 Duplicate outcome submission under client retry remains idempotent
Level: Integration
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: Same outcome payload retried multiple times.
Steps:
1. Submit identical outcome with repeated retries.
Expected:
- Single durable outcome record or deterministic merge result exists.
- No duplicate learning or duplicate safety escalation occurs.

### AURA-OPS-050 Multi-tab profile edit, override, and consent changes resolve deterministically
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: Same user active in multiple browser tabs.
Steps:
1. Edit profile in one tab.
2. Revoke consent or override attribute in another.
3. Refresh both tabs and fetch recommendations.
Expected:
- Final state is deterministic and auditable.
- UI surfaces stale-state conflicts or refresh requirements cleanly.

### AURA-OPS-051 Save, dismiss, and compare actions on same product family do not corrupt feedback history
Level: Integration
Priority: P1
Source Docs: `Aura_PRD_v1.md`, `Aura_Event_Architecture.md`
Preconditions: Same session produces rapid mixed actions.
Steps:
1. Save product.
2. Dismiss similar product.
3. Open compare and save from compare.
Expected:
- Feedback history preserves ordering and recommendation linkage.
- No duplicate or contradictory durable state is created.

### AURA-OPS-052 Export request followed by delete-account flow resolves safely and auditable
Level: Integration
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: Mature user account with exportable history.
Steps:
1. Request data export.
2. Immediately request account deletion with proper re-auth.
Expected:
- Product follows documented precedence and lifecycle rules.
- Audit trail records both actions and their final state.

### AURA-OPS-053 Live traffic plus historical replay does not duplicate derived projections
Level: Integration
Priority: P1
Source Docs: `Aura_Event_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Replay job and live traffic active together.
Steps:
1. Start replay of historical events.
2. Continue live feed, feedback, and outcome traffic.
Expected:
- Derived views remain correct.
- Replay markers or idempotency keys prevent double-counting.

### AURA-OPS-054 Retry storm after transient downstream failure does not amplify side effects
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Temporary downstream outage followed by recovery.
Steps:
1. Accumulate retriable failures.
2. Restore dependency and process backlog.
Expected:
- Retries do not produce duplicate writes, alerts, or notifications.
- Recovery processing remains bounded and observable.

---

## D. Shared Library, Service Reuse, and Monorepo Regression

### AURA-OPS-055 `@ghatana/ui`, `@ghatana/tokens`, and `@ghatana/theme` upgrades preserve Aura core journeys
Level: Integration
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_Monorepo_Structure.md`
Preconditions: Shared UI package update candidate.
Steps:
1. Upgrade shared UI, token, or theme package.
2. Run onboarding, feed, detail, compare, privacy, and assistant smoke tests.
Expected:
- No visual, interaction, or accessibility regression breaks core journeys.

### AURA-OPS-056 `@ghatana/accessibility-audit` gates Aura critical surfaces in CI
Level: Non-Functional
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_Engineering_Sprint_Plan_6_Months.md`
Preconditions: Aura critical routes available in CI or preview environment.
Steps:
1. Run accessibility audit against onboarding, feed, detail, compare, and consent center.
Expected:
- Failing accessibility score or critical violations block merge.
- Audit artifacts are stored for review.

### AURA-OPS-057 `@ghatana/api`, `@ghatana/utils`, and `@ghatana/realtime` wrappers preserve Aura client-state contracts
Level: Integration
Priority: P1
Source Docs: `Aura_API_Contracts.md`, `Aura_Monorepo_Structure.md`
Preconditions: Aura clients use shared transport/state helpers.
Steps:
1. Exercise auth refresh, retries, pagination, optimistic updates, and realtime invalidation.
Expected:
- Shared client libraries satisfy Aura-specific contract behavior without local reimplementation.

### AURA-OPS-058 Shared platform modules remain compatible with Aura integration tests
Level: Integration
Priority: P0
Source Docs: `Aura_Technical_Stack_Blueprint.md`, `Aura_Monorepo_Structure.md`
Preconditions: Version or behavior change in shared modules such as `products/aep/platform`, `products/data-cloud/platform`, `products/data-cloud/spi`, `governance`, `security`, `observability`, `ingestion`, or `testing`.
Steps:
1. Run Aura integration and contract suites after shared-module change.
Expected:
- Shared-module upgrade does not silently break Aura behavior.
- Breakages surface in impacted tests before merge.

### AURA-OPS-059 Shared auth services satisfy Aura's export, delete, and re-authentication flows
Level: Integration
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: Aura wired to shared auth service/gateway.
Steps:
1. Run login, token refresh, export request, and delete-account re-auth flow.
Expected:
- Shared auth path supports Aura's sensitive-action requirements without product-specific auth forks.

### AURA-OPS-060 Shared AI inference service and AI registry support Aura shadow, canary, and rollback workflow
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_AI_ML_Data_Operating_Model.md`
Preconditions: Aura ranking or inference model candidate available.
Steps:
1. Register model.
2. Run shadow or challenger flow.
3. Promote canary and trigger rollback.
Expected:
- Shared model-serving and registry components can execute Aura rollout policy without custom replacement.

### AURA-OPS-061 Reuse-first audit blocks unnecessary local duplicates of shared Ghatana capabilities
Level: Process
Priority: P1
Source Docs: `Aura_Task_Execution_Matrix.md`, `Aura_Monorepo_Structure.md`
Preconditions: New Aura feature proposal or implementation PR.
Steps:
1. Review new utilities, components, adapters, or services introduced by the change.
Expected:
- Team records whether shared platform, shared services, or existing product libraries were evaluated first.
- Duplicate wrappers or components require explicit justification to proceed.

### AURA-OPS-062 Gitea CI runs impacted Aura tests when shared packages, AEP, or Data Cloud change
Level: Integration
Priority: P0
Source Docs: `Aura_Engineering_Sprint_Plan_6_Months.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Change in shared package, shared service, or platform module used by Aura.
Steps:
1. Modify a shared dependency.
2. Run CI.
Expected:
- Impacted Aura contract, integration, accessibility, and regression suites are selected and executed.
- Merge is blocked if shared changes break Aura.
