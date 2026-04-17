# Critical Journey E2E Evidence - 2026-04-16

Owner: TutorPutor Platform
Scope: TP-008 release-critical proof artifacts

## Journeys

| Journey ID | Flow | Required Proof | Last Run | Status |
|---|---|---|---|---|
| CJ-001 | Sign-in -> learner dashboard load | Playwright pass + screenshot + trace | pending | in-progress |
| CJ-002 | Teacher creates assignment -> learner submission | Playwright pass + API log extract | pending | in-progress |
| CJ-003 | Payment upgrade -> entitlement change | Playwright/API pass + DB verification | pending | in-progress |
| CJ-004 | Compliance export request -> downloadable artifact | API integration pass + export checksum | pending | in-progress |
| CJ-005 | User deletion request -> verification token -> cascade completion | API flow pass + SQL verification | pending | in-progress |

## Evidence Requirements

1. Include command output hashes for reproducibility.
2. Attach Playwright trace zip for each UI journey.
3. Record correlation IDs for every API flow.
4. Link to SQL verification outputs for data-changing flows.

## References

- CRITICAL_JOURNEY_E2E_CHECKLIST.md
- CRITICAL_JOURNEY_E2E_RUNBOOK.md
- CRITICAL_JOURNEY_E2E_SIGNOFF_TEMPLATE.md
- GDPR_DELETION_FLOW_RUNBOOK.md
