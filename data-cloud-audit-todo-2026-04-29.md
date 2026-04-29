# Data-Cloud Audit TODO List — 2026-04-29

Companion to `dta-cloud-audit-report-2026-04-29.md`.
Flat list of action items for `products/data-cloud`.

**Legend**
- **Priority:** P0 (blocker) · P1 (high) · P2 (medium)
- **Complexity:** S (≤4h) · M (4–16h) · L (16–40h) · XL (>40h)
- **Term:** ST (this sprint / 1–2 weeks) · MT (1–3 months) · LT (3+ months)

## Action Items

| # | Priority | ID | Area | Title | Complexity | Term |
|---|---|---|---|---|---|---|
| 1 | P0 | DC-A1 | platform-client | Delete `products/data-cloud/platform-client/` or formalize it as a real Gradle module with source, tests, README, and settings include | M | ST |
| 2 | P0 | DC-A2 | governance | Remove stale `platform-client` and deleted `platform` references from `products/data-cloud/gradle/coverage-gates.gradle` and related docs/ADRs | S | ST |
| 3 | P0 | DC-A3 | governance | Add CI enforcement so coverage governance fails when it references nonexistent modules | M | ST |
| 4 | P1 | DC-A4 | ownership | Add missing `OWNER.md` files for included modules: `agent-catalog`, `api`, `integration-tests`, `kernel-bridge`, `platform-analytics`, `platform-api`, `platform-config`, `platform-entity`, `platform-event`, `platform-governance`, `platform-launcher`, `platform-plugins` | M | ST |
| 5 | P1 | DC-A5 | build-hygiene | Burn down the diagnostics backlog under `products/data-cloud` starting with unused imports/fields, unchecked casts, and deprecated Testcontainers APIs | L | ST |
| 6 | P1 | DC-A6 | agent-catalog | Add focused tests for YAML schema/business-rule validation, duplicate IDs, invalid globs, and malformed catalog metadata | M | ST |
| 7 | P1 | DC-A7 | kernel-bridge | Expand bridge integration coverage for mapping, failure propagation, and tenant-safe boundary handling | M | ST |
| 8 | P1 | DC-A8 | ui | Add live browser E2E tests against a real launcher for top user/operator journeys: Intelligent Hub, SQL Workspace, Workflows, Trust Center, Alerts | L | MT |
| 9 | P1 | DC-A9 | platform-plugins | Add real-backend conformance coverage for optional plugins/connectors with provider-backed tests where feasible | L | MT |
| 10 | P1 | DC-A10 | feature-store-ingest | Extract `WarmTierEventLogStore` or a narrower event-store contract out of `platform-launcher` to remove the heavyweight dependency | L | MT |
| 11 | P2 | DC-A11 | docs | Update generated/reference ADRs and product docs to distinguish “verified locally”, “integration-validated”, and “deployment-validated” claims more precisely | M | ST |
| 12 | P2 | DC-A12 | scripts | Add smoke validation for critical product scripts, especially `check-openapi-drift.sh` and local stack helpers | S | ST |
| 13 | P2 | DC-A13 | helm | Add Helm lint/template validation to product CI and document expected values profiles | M | MT |
| 14 | P2 | DC-A14 | terraform | Add `terraform validate` and policy checks for the product IaC layer in CI | M | MT |
| 15 | P2 | DC-A15 | platform-api | Expand real optional-backend coverage for websocket/browser interplay and AI/model-registry degraded-mode behavior | M | MT |
| 16 | P2 | DC-A16 | platform-entity | Add property/contract tests for record mutability, schema evolution, and connector capability negotiation | M | MT |
| 17 | P2 | DC-A17 | platform-event | Add sustained replay/load verification to complement existing replay and idempotency coverage | M | MT |
| 18 | P2 | DC-A18 | integration-tests | Add explicit telemetry assertions for critical integration journeys so metrics/tracing behavior is verified, not just functional outcomes | M | MT |
| 19 | P2 | DC-A19 | sdk | Expand generated-client contract replay coverage across Java, TypeScript, and Python outputs in CI | M | MT |
| 20 | P2 | DC-A20 | libs/ui-components | Add explicit tests if `libs/ui-components` owns nontrivial behavior beyond re-exports, and add ownership metadata | S | MT |

## Suggested Execution Order

1. `DC-A1`
2. `DC-A2`
3. `DC-A3`
4. `DC-A4`
5. `DC-A5`
6. `DC-A6`
7. `DC-A7`
8. `DC-A10`
9. `DC-A8`
10. `DC-A9`
11. `DC-A11` to `DC-A20`
