# Aura Test Cases 04: Platform Architecture, Operations, Security, and Resilience

Version: 1.0
Date: March 13, 2026

## Scope

This suite covers:

- ingestion and canonicalization workflows
- caching, latency, and serving behavior
- security, privacy, auditability, and access control
- observability, drift, fairness, and rollback
- deployment, resilience, and failure handling

---

## A. Ingestion and Canonical Knowledge

### AURA-OPS-001 Source adapter ingests retailer feed into normalized catalog shape
Level: Integration
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`, `Aura_Data_Architecture.md`
Preconditions: Valid retailer or affiliate feed fixture.
Steps:
1. Run source adapter.
2. Inspect normalized product output.
Expected:
- Required product fields are normalized.
- Raw payload is preserved in the Data Cloud snapshot/object-storage path.

### AURA-OPS-002 Brand page scraping failure retries and preserves diagnostics
Level: Integration
Priority: P1
Source Docs: `Aura_System_Architecture.md`, `Aura_Event_Architecture.md`
Preconditions: Unreachable or malformed brand page source.
Steps:
1. Execute crawl.
Expected:
- Retry policy executes.
- Failure is observable and diagnosable.
- Dead-letter or investigation path exists when retries exhaust.

### AURA-OPS-003 Deduplication merges same product from multiple sources without losing provenance
Level: Integration
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Data_Architecture.md`
Preconditions: Two sources describing same product differently.
Steps:
1. Ingest both records.
Expected:
- Single canonical product is produced.
- Source provenance retains both origins.

### AURA-OPS-004 Freshness scoring degrades stale sources predictably
Level: Unit
Priority: P1
Source Docs: `Aura_System_Architecture.md`, `Aura_Data_Architecture.md`
Preconditions: Source timestamps across fresh and stale ranges.
Steps:
1. Compute freshness scores.
Expected:
- Newer records score higher.
- Threshold behavior matches product trust expectations.

### AURA-OPS-005 Enrichment worker adds ingredient, shade, and sentiment enrichments idempotently
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Product ready for enrichment.
Steps:
1. Run enrichment twice.
Expected:
- Canonical record is correct after both runs.
- No duplicate ingredient, shade, or sentiment rows appear.

### AURA-OPS-006 Source provenance store exposes field-level confidence
Level: Integration
Priority: P1
Source Docs: `Aura_System_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Product assembled from mixed-quality sources.
Steps:
1. Inspect provenance and confidence data.
Expected:
- Field-level origin and confidence are queryable.

### AURA-OPS-007 Review ingestion strips PII and preserves usable sentiment content
Level: Integration
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_Event_Architecture.md`
Preconditions: Review fixture with possible author-identifying data.
Steps:
1. Ingest reviews.
Expected:
- Raw PII is not stored in analytics-friendly structures.
- Review body and metadata still support sentiment extraction.

---

## B. Serving Path, Caching, and Performance

### AURA-OPS-008 Recommendation API p95 latency stays below target for supported traffic profile
Level: Non-Functional
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Load profile representative of supported beta traffic.
Steps:
1. Run load test on recommendation endpoint.
Expected:
- p95 remains within documented latency target.

### AURA-OPS-009 Cached recommendation path invalidates on profile change
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Cached recommendations exist.
Steps:
1. Update profile.
2. Request recommendations again.
Expected:
- Cache is invalidated or reranked correctly.
- User does not see stale pre-profile-change results.

### AURA-OPS-010 Catalog update invalidates stale recommendation candidates
Level: Integration
Priority: P1
Source Docs: `Aura_Event_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Product availability or ingredient data changes.
Steps:
1. Publish catalog update event.
2. Request recommendations again.
Expected:
- Updated product data influences next result set.

### AURA-OPS-011 Online and offline recommendation paths converge on same business rules
Level: Integration
Priority: P1
Source Docs: `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Same input user and product set processed through both paths.
Steps:
1. Compare online reranked output to nearline-precomputed output.
Expected:
- Hard constraints and trust rules are consistent across both paths.

### AURA-OPS-012 Explanation generation in response path does not exceed latency budget
Level: Non-Functional
Priority: P1
Source Docs: `Aura_Intelligence_Platform_Architecture.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: Response path includes explanation generation.
Steps:
1. Measure latency with explanation on and off.
Expected:
- Explanation path stays within performance budget.
- Graceful fallback exists if explanation service is slow.

### AURA-OPS-013 Recommendation cache never serves data across users
Level: Security
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Cached responses for multiple users.
Steps:
1. Request recommendations as user A and user B with overlapping inputs.
Expected:
- User-specific cache scoping prevents cross-user leakage.

---

## C. Security, Privacy, and Governance

### AURA-OPS-014 Optional high-sensitivity processing is impossible without scoped consent
Level: Integration
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: User lacks relevant scope.
Steps:
1. Attempt selfie or wearable-derived recommendation path.
Expected:
- Processing is blocked with consent-aware handling.
- Core service still remains usable.

### AURA-OPS-015 Core service processing remains available when all optional scopes are revoked
Level: E2E
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: User previously granted then revoked all optional scopes.
Steps:
1. Re-open feed, product detail, compare, saved items, export.
Expected:
- Core service functions remain operational.
- Revoked scopes no longer influence data or recommendations.

### AURA-OPS-016 Sensitive mutations create immutable audit log entries
Level: Integration
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Event_Architecture.md`
Preconditions: Profile update, consent change, export request, deletion, or outcome report.
Steps:
1. Execute each sensitive action.
Expected:
- Audit trail includes actor, resource, action, outcome, and timestamp.

### AURA-OPS-017 Analytics streams contain tokenized IDs only
Level: Integration
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_System_Architecture.md`
Preconditions: Analytics pipeline active.
Steps:
1. Inspect outbound analytics payloads.
Expected:
- No raw email, profile text, or unredacted PII appears.

### AURA-OPS-018 Rate limits protect API without blocking ordinary beta usage
Level: Non-Functional
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Load generation available.
Steps:
1. Send normal and abusive request patterns.
Expected:
- Abuse is throttled.
- Typical user behavior remains unaffected.

### AURA-OPS-019 CORS allows only approved origins
Level: Security
Priority: P1
Source Docs: `Aura_API_Contracts.md`
Preconditions: Multiple origin headers available.
Steps:
1. Call API from approved and unapproved origins.
Expected:
- Only approved origins receive CORS allowance.

### AURA-OPS-020 Secrets never appear in application logs or error payloads
Level: Security
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Misconfiguration and error paths exercisable.
Steps:
1. Trigger service startup and runtime errors.
Expected:
- Secrets and tokens remain redacted.

---

## D. Observability, Monitoring, and Fairness Operations

### AURA-OPS-021 All services emit structured logs, traces, and metrics
Level: Non-Functional
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_System_Architecture.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Multi-service workflow executed.
Steps:
1. Run recommendation request across services.
Expected:
- Correlated trace exists across service boundaries.
- Logs are structured and queryable.
- Metrics increment as expected.

### AURA-OPS-022 Recommendation quality dashboard includes outcome metrics, not only engagement
Level: Integration
Priority: P0
Source Docs: `Aura_System_Architecture.md`, `Aura_GTM_Strategy.md`, `Aura_24_Month_Strategy.md`
Preconditions: Analytics sink populated.
Steps:
1. Inspect dashboards.
Expected:
- Time-to-decision, shade-miss, adverse reaction, and return reduction are present.

### AURA-OPS-023 Drift detection alerts on score distribution shift
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_Event_Architecture.md`
Preconditions: Artificial score distribution drift.
Steps:
1. Push drift beyond threshold.
Expected:
- Drift alert event and alerting path are triggered.

### AURA-OPS-024 Fairness alert triggers when cohort quality gap exceeds tolerance
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_Event_Architecture.md`
Preconditions: Evaluation data with artificial cohort skew.
Steps:
1. Run fairness monitor.
Expected:
- Fairness alert is emitted and observable.

### AURA-OPS-025 Model deployment event links model version to training snapshot hash
Level: Contract
Priority: P1
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_Event_Architecture.md`
Preconditions: Model deployment path.
Steps:
1. Deploy model.
Expected:
- Deployment event includes model version and traceable training reference.

### AURA-OPS-026 Rollback path restores previous champion cleanly
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_System_Architecture.md`
Preconditions: Canary candidate causes regression.
Steps:
1. Trigger rollback.
Expected:
- Previous champion resumes serving.
- Rollback is visible in observability and audit systems.

---

## E. Resilience, Deployment, and Tooling

### AURA-OPS-027 AEP consumer failure routes message to retry then DLQ
Level: Integration
Priority: P0
Source Docs: `Aura_Event_Architecture.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: AEP consumer forced to fail persistently.
Steps:
1. Publish event.
Expected:
- Retry behavior occurs first.
- Message lands in DLQ after max attempts.

### AURA-OPS-028 Replay of historical events rebuilds derived state safely
Level: Integration
Priority: P1
Source Docs: `Aura_Event_Architecture.md`, `Aura_Intelligence_Platform_Architecture.md`
Preconditions: Historical event backlog available.
Steps:
1. Replay ingestion/profile/feedback events.
Expected:
- Derived caches or projections rebuild without duplicate side effects.

### AURA-OPS-029 Recommendation service outage degrades to deterministic fallback when possible
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Agent_Architecture.md`, `Aura_AI_Engine_Design.md`
Preconditions: ML or agent dependency unavailable.
Steps:
1. Execute supported recommendation request.
Expected:
- Deterministic fallback returns safe reduced-quality result or safe failure.
- No fabricated recommendation appears.

### AURA-OPS-030 Kubernetes or container restart does not corrupt in-flight idempotent workflows
Level: Integration
Priority: P1
Source Docs: `Aura_Technical_Stack_Blueprint.md`, `Aura_C4_Architecture_Diagrams.md`
Preconditions: In-flight background job.
Steps:
1. Restart container/pod mid-process.
Expected:
- Job either resumes safely or retries idempotently.

### AURA-OPS-031 CI validates schema, contract, and accessibility gates before merge
Level: Non-Functional
Priority: P0
Source Docs: `Aura_Engineering_Sprint_Plan_6_Months.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Candidate change set.
Steps:
1. Run CI pipeline.
Expected:
- Contract, unit, integration, and accessibility gates execute.
- Failing critical checks block merge.

### AURA-OPS-032 Monorepo changes do not break unrelated package builds
Level: Integration
Priority: P2
Source Docs: `Aura_Monorepo_Structure.md`
Preconditions: Modified package or service.
Steps:
1. Run affected and unaffected package builds/tests.
Expected:
- Workspace isolation behaves as expected.
- Shared contract changes surface downstream breakages early.

### AURA-OPS-033 Deployment diagram assumptions match real environment wiring
Level: Integration
Priority: P1
Source Docs: `Aura_C4_Architecture_Diagrams.md`, `Aura_Technical_Stack_Blueprint.md`
Preconditions: Staging environment.
Steps:
1. Compare running services, queues, storage, and ingress paths to docs.
Expected:
- Deployed topology matches documented architecture or discrepancy is recorded.

### AURA-OPS-063 Selfie inference path stores derived undertone only and never retains raw image artifacts
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_Data_Architecture.md`, `Aura_Shared_Platform_Integration_Spec.md`
Preconditions: Opt-in selfie pilot executed successfully and unsuccessfully in controlled environments.
Steps:
1. Run the selfie inference path with valid and invalid capture inputs.
2. Inspect Data Cloud datasets, object paths, audit records, and retry artifacts.
Expected:
- Raw selfie images are not retained after processing completes or fails.
- Only approved derived outputs and audit metadata persist.
- Failure handling does not leak raw image artifacts into logs, queues, or storage.

### AURA-OPS-064 High-sensitivity selfie pilot uses shared security and observability without exposing sensitive payloads
Level: Integration
Priority: P0
Source Docs: `Aura_Shared_Platform_Integration_Spec.md`, `Aura_System_Architecture.md`, `Aura_AI_Engine_Design.md`
Preconditions: Shared auth, audit, and observability services enabled for the pilot flow.
Steps:
1. Execute the opt-in selfie pilot from authenticated session to inference completion.
2. Inspect shared security, audit, trace, and log outputs.
Expected:
- Auth, consent scope, and audit enforcement run through shared platform services.
- Traces and logs remain correlated end to end without exposing selfie payload content.
- Operators can diagnose the flow from shared o11y data without reading sensitive raw inputs.
