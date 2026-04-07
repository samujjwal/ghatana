# Finance and PHR Remaining Todo Items

Date: 2026-04-06
Source audit: [products/finance-phr-v5-audit-report.md](/Users/samujjwal/Development/ghatana/products/finance-phr-v5-audit-report.md)

## Cross-Cutting

- Repo-wide `100%` test coverage is still not achieved.

## Finance

- `FIN-P1-007` Complete regulatory reporting implementation.
- `FIN-P1-008` Expand integration and contract coverage.
- `FIN-P1-009` Add performance verification for fraud and transaction paths.
- `FIN-P1-013` Add distributed tracing and correlation propagation.
- `FIN-P1-014` Create SLO/SLI dashboards after tracing lands.
- `FIN-P2-001` Add fraud-decision explainability coverage.

## PHR

- `PHR-P0-001` Implement the web frontend application.
- `PHR-P0-002` Implement the mobile application.
- `PHR-P1-010` Complete telemedicine beyond current scheduling, cancel, and notification flows.
- `PHR-P1-011` Implement the FHIR R4 server and resource providers on top of the existing transformation layer.
- `PHR-P1-012` Expand integration, API, and security coverage beyond the currently targeted backend regressions.
- `PHR-P1-009` Bridge the durable notification outbox to real email, SMS, and push delivery providers.
- `PHR-P1-013` Generalize rate limiting beyond consent operations.
- `PHR-P1-015` Broaden retry handling beyond emergency-review and notification seams.
- `PHR-P1-016` Add distributed tracing.
- `PHR-P1-017` Create SLO/SLI dashboards after tracing lands.
- `PHR-P2-001` Produce HIPAA validation and compliance evidence.
- `PHR-P2-002` Implement Nepal HIE integration.
- `PHR-P2-003` Implement HL7 lab integration.
