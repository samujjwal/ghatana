# Finance and PHR Audit Remaining Items

Date: 2026-04-06
Source audit: [products/finance-phr-v5-audit-report.md](/Users/samujjwal/Development/ghatana/products/finance-phr-v5-audit-report.md)

## Purpose

This file replaces the audit's stale status lines with a code-verified snapshot of what is actually still open after the current remediation sessions.

## Completed Or Stale Audit Items

### Finance

- `FIN-P0-001` Complete in code: duplicate fraud agent/result surfaces were consolidated earlier in the session.
- `FIN-P0-002` Complete in code: fraud inference now uses the runtime model integration seam instead of only hardcoded thresholds.
- `FIN-P0-003` Complete in code: finance AI persistence support, repositories, migration, and persistence tests exist.
- `FIN-P1-003` Already implemented before this file: [products/finance/src/main/java/com/ghatana/finance/ai/FinanceAgentOrchestratorImpl.java](/Users/samujjwal/Development/ghatana/products/finance/src/main/java/com/ghatana/finance/ai/FinanceAgentOrchestratorImpl.java) retries agent execution up to three attempts.
- `FIN-P1-004` Partially addressed in this session: [products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java](/Users/samujjwal/Development/ghatana/products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java) now enforces expiring idempotency with conflicting replay detection, but it is still process-local rather than durable.
- `FIN-P1-010` Partially addressed in this session: transaction processing now has per-tenant in-process rate limiting, but no Redis/distributed limiter.
- `FIN-P1-011` Partially addressed in this session: transaction boundary validation and sanitization are now explicit, but API-layer request validation still needs confirmation across Finance entrypoints.

### PHR

- `PHR-P0-003` Complete in backend code: durable repositories, migrations, runtime config, and repository persistence tests exist.
- `PHR-P0-004` Complete in backend code: password verification and lockout hardening were implemented earlier in the session.
- `PHR-P1-005` Complete in backend code: mandatory emergency review workflow exists with notification and audit seams.
- `PHR-P1-006` Complete in backend code: audit idempotency was fixed earlier in the session.
- `PHR-P1-007` Complete in backend code: patient write rollback/compensation and transaction boundary protection were added earlier in the session.
- `PHR-P1-008` Complete for the emergency-review seam: repo-native retry/circuit-breaker adapters exist for emergency review notification and audit flows.
- `PHR-P1-009` Partially addressed in this session: appointment reminders, consent change notifications, emergency-access notifications, and telemedicine schedule/cancel notifications now flow through [products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrNotificationSender.java](/Users/samujjwal/Development/ghatana/products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrNotificationSender.java), but no concrete email/SMS/push transport adapter is registered yet.
- `PHR-P1-013` Already partially implemented before this file: consent operations already had in-process rate limiting.
- `PHR-P1-014` Largely addressed in backend code: sanitization utilities are wired across major PHR services, but API-surface confirmation remains open.
- `PHR-P1-015` Partially addressed: retry exists for emergency-review notification/audit and the new PHR notification boundary, not yet for every external seam.

## Remaining Backend Work

### Finance Remaining

#### High Priority

- `FIN-P1-004` Durable idempotency store for transaction processing.
  Current gap: idempotency is expiring and conflict-aware, but still local to the process.
  Likely work: persist idempotency keys/results in the finance persistence layer and define replay semantics across restarts.

- `FIN-P1-010` Distributed rate limiting.
  Current gap: limiter is in-process only.
  Likely work: move to shared limiter storage or a gateway/filter layer with consistent 429 handling.

- `FIN-P1-011` API boundary validation sweep.
  Current gap: transaction service is hardened, but the audit asks for input validation at the API layer across Finance surfaces.
  Likely work: verify BFF/controllers/contracts and add request-schema enforcement where still missing.

- `FIN-P1-012` Broader resilience patterns beyond current agent retry and ledger circuit breaker.
  Current gap: some resilience exists, but not as a consistent policy across all outbound Finance seams.
  Likely work: inventory external/service boundaries, wrap missing ones with platform resilience primitives, and add regression tests.

#### Medium Priority

- `FIN-P1-007` Regulatory reporting completion.
- `FIN-P1-008` Expand deep integration and contract coverage.
- `FIN-P1-009` Performance verification for fraud and transaction paths.
- `FIN-P1-013` Distributed tracing and correlation propagation.
- `FIN-P1-014` SLO/SLI dashboards after tracing is in place.
- `FIN-P2-001` Explainability for fraud decisions.

### PHR Remaining

#### High Priority

- `PHR-P0-001` Web frontend application remains unimplemented in this audit stream.
- `PHR-P0-002` Mobile application remains unimplemented in this audit stream.
- `PHR-P1-010` Telemedicine remains scaffold-level on the product side beyond scheduling/cancel state management and notifications.
- `PHR-P1-011` FHIR R4 server remains open: transformations exist, full server/resource providers do not.
- `PHR-P1-012` Broader integration/API/security coverage expansion remains open even though many targeted regressions now exist.

#### Medium Priority

- `PHR-P1-009` Register a concrete notification transport adapter for email/SMS/push delivery.
- `PHR-P1-013` Generalize rate limiting beyond consent operations.
- `PHR-P1-015` Broaden retry handling beyond emergency-review and notification seams.
- `PHR-P1-016` Distributed tracing.
- `PHR-P1-017` SLO/SLI dashboards after tracing.
- `PHR-P2-001` HIPAA validation and compliance evidence.
- `PHR-P2-002` Nepal HIE integration.
- `PHR-P2-003` HL7 lab integration.

## Verification Notes

- PHR notification slice verification passed in the current session: focused notification/appointment/consent/telemedicine tests were green.
- Finance transaction hardening in this session must be verified with focused Finance tests after code changes land.
- Repo-wide `100%` coverage is still not achieved and should not be reported as complete.
