# Testing

**Version:** 2.0 | **Updated:** 2026-01-19

This folder contains the detailed test case packs for API, service/integration, UI end-to-end, and non-functional/compliance coverage. All docs enhanced in v2.0 with OWASP security tests, Emergency QR tests, FCHV flow tests, tenant isolation tests, Nepali locale tests, load/stress specs, and browser compatibility.

Files:

- `phr_api_testcases.md` — Contract-level API tests including Emergency QR, rate limiting, circuit breaker, tenant isolation, OWASP security, and performance targets
- `phr_service_integration_testcases.md` — Module and persistence tests including cross-tenant isolation, consent cache, circuit breaker integration, document integrity, and FCHV registration
- `phr_ui_e2e_testcases.md` — User-facing E2E scenarios including Emergency QR UI, FCHV flows, offline mode, Nepali locale, WCAG 2.2 AA audit, and browser compatibility matrix
- `phr_nonfunctional_and_compliance_testcases.md` — Quality, security, and compliance tests including OWASP Top 10 suite, DPIA validation, pen test requirements, load/stress specs, data retention, Nepali ASR accuracy, and breach notification
- `phr_test_automation_mapping.md` — Planned suite paths and owners for every testcase family
- `phr_seed_data_and_test_fixture_plan.md` — Shared fixture tenants, actor seeds, external stubs, and deterministic reset rules

Release-gate automation:

- `./gradlew :products:phr:phrReleaseGate` runs the current backend release-gate regression set used before staging sign-off.
