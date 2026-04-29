# AEP Audit TODO List — 2026-04-29

Companion to `aep-audit-report-2026-04-29.md`.
Flat actionable list for `products/aep`.

**Legend**
- **Priority:** P0 (blocker) · P1 (high) · P2 (medium)
- **Complexity:** S (≤4h) · M (4–16h) · L (16–40h) · XL (>40h)
- **Term:** ST (this sprint / 1–2 weeks) · MT (1–3 months) · LT (3+ months)

## Action Items

| # | Priority | ID | Area | Title | Complexity | Term |
|---|---|---|---|---|---|---|
| 1 | P0 | AEP-A1 | source-integrity | Correct package declaration mismatches in `FrequentSequenceMiner`, `TemporalCorrelationAnalyzer`, and `EventPipeline` | M | ST |
| 2 | P0 | AEP-A2 | governance | Add CI-pinned targeted checks for `AepOpenApiSurfaceDriftTest` and `AgentExecutionServiceTest` to prevent compile/regression drift | M | ST |
| 3 | P0 | AEP-A3 | quality | Burn down highest-risk diagnostics classes first (package/path mismatches, raw generics, suppressions in critical runtime paths) | L | ST |
| 4 | P1 | AEP-A4 | ownership | Add module-level `OWNER.md` files for all included AEP modules (not only root `products/aep/OWNER.md`) | M | ST |
| 5 | P1 | AEP-A5 | quality | Burn down remaining diagnostics under `products/aep` (unused imports/fields, unnecessary suppressions, deprecated APIs) | XL | ST |
| 6 | P1 | AEP-A6 | governance | Replace or ticket every runtime placeholder/TODO in security/discovery/governance paths with explicit GH issue IDs and acceptance criteria | M | ST |
| 7 | P1 | AEP-A7 | tests | Re-enable or rewrite disabled cases in `RegistryAndFactoryTest` so they assert real SPI behavior instead of remaining skipped | M | ST |
| 8 | P1 | AEP-A8 | contracts | Pin currently passing `AepOpenApiSurfaceDriftTest` and `AgentExecutionServiceTest` in CI with hard failure on regressions | M | ST |
| 9 | P2 | AEP-A9 | scaling | Expand `aep-scaling` test depth beyond policy manager to include autoscaling failure/recovery and policy conflict paths | M | MT |
| 10 | P2 | AEP-A10 | observability | Expand `aep-observability` integration tests for tracing and metrics propagation in multi-module scenarios | M | MT |
| 11 | P2 | AEP-A11 | compliance | Add deeper compliance automation integration tests (retention execution, failure retries, operational telemetry assertions) | M | MT |
| 12 | P2 | AEP-A12 | CI hardening | Add focused-module CI matrix (`contracts`, `server`, `orchestrator`, `aep-scaling`) to detect compile drift before merge | M | MT |
| 13 | P2 | AEP-A13 | docs | Update `products/aep/README.md` and module READMEs with verified-vs-blocked test matrix from current audit evidence | S | ST |
| 14 | P2 | AEP-A14 | test tooling | Document canonical Java test execution path (Gradle module tasks) since direct path-based `runTests` discovery is unreliable in current workspace | S | ST |

## Suggested Execution Order

1. `AEP-A1`
2. `AEP-A2`
3. `AEP-A3`
4. `AEP-A4`
5. `AEP-A5`
6. `AEP-A8`
7. `AEP-A6`
8. `AEP-A7`
9. `AEP-A9` to `AEP-A14`
