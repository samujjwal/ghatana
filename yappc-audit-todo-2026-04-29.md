# YAPPC Audit TODO (2026-04-29)

Simple prioritized action list derived from `yappc-audit-report-2026-04-29.md`.

**Legend**
- **Priority:** P0 (blocker) · P1 (high) · P2 (medium)
- **Complexity:** S (<=4h) · M (4-16h) · L (16-40h)
- **Term:** ST (this sprint / 1-2 weeks) · MT (1-3 months) · LT (3+ months)

| ID | Priority | Complexity | Term | Area | Todo Item | Success Criteria |
|---|---|---|---|---|---|---|
| YAPPC-001 | P0 | L | ST | Quality/Build | Burn down compile and diagnostics debt across `products/yappc` | `get_errors(products/yappc)` reduced from 3645 to zero critical compile errors; CI green for touched modules |
| YAPPC-002 | P0 | M | ST | API Contracts | Add canonical OpenAPI parity task in `core/yappc-api` and wire to `check` | Task exists, fails on drift, and runs in CI on every PR |
| YAPPC-003 | P0 | M | ST | Runtime Correctness | Replace placeholder-success behavior in `GitHubActionsCiCdAdapter` with fail-closed behavior until real integration is complete | Adapter returns explicit failure/not-implemented status; tests assert no false success |
| YAPPC-004 | P0 | M | ST | Security/Auth | Replace placeholder auth/security scan pathways in frontend API routes with real validated implementations | Auth and scan endpoints use concrete data paths and pass integration tests |
| YAPPC-005 | P1 | M | ST | Test Coverage | Add integration tests for `YappcApiController` happy/edge/error paths | Targeted API integration suite passes and covers contract + error handling |
| YAPPC-006 | P1 | M | ST | E2E | Replace placeholder route implementations for project test/devsecops surfaces and add E2E tests | User journeys run without placeholder components and E2E passes |
| YAPPC-007 | P1 | S | ST | Governance | Increase ownership coverage (`OWNER.md`) across major active modules | OWNER metadata present for all critical modules |
| YAPPC-008 | P1 | M | ST | Repo Hygiene | Remove or relocate checked-in generated `bin/` artifacts from source-managed module trees | No product-owned generated binary trees tracked in core modules |
| YAPPC-009 | P1 | M | MT | Infra Compliance | Replace placeholder SBOM generation in infrastructure adapters with real toolchain-backed SBOM output | SBOM artifacts generated and validated in CI |
| YAPPC-010 | P1 | M | MT | Observability | Add structured logs/metrics assertions for CI/CD and API critical flows | Critical flows emit latency/outcome metrics and correlation-friendly logs |
| YAPPC-011 | P1 | M | MT | AI Safety | Add deterministic fallback and output-validation tests for generation/scoring services | Unsafe/low-confidence outputs are blocked or downgraded by policy tests |
| YAPPC-012 | P2 | S | MT | Tooling | Tighten TODO/placeholder scanning exclusions to avoid `node_modules`/generated pollution | Scanner reports only first-party actionable entries |
| YAPPC-013 | P2 | S | MT | Documentation | Align historical audit docs and completion trackers with current executable task names | No stale task references; docs match actual Gradle tasks |
| YAPPC-014 | P2 | M | MT | Contracts | Reconcile and version OpenAPI artifacts under `api/` and `docs/api/` to single source of truth | Contract source ownership explicit; drift checks pass |
| YAPPC-015 | P2 | S | LT | Performance | Execute and baseline frontend/backend performance suites for key YAPPC workflows | Baselines published and regression thresholds enforced |
| YAPPC-016 | P2 | M | LT | Reliability | Add resilience tests (timeouts/retries/circuit behavior) for external adapters | Failure modes are deterministic and observable in tests |
| YAPPC-017 | P2 | M | LT | Privacy/Audit | Add end-to-end audit trail verification for sensitive operations | User actions generate complete, queryable audit records |
| YAPPC-018 | P2 | M | LT | Standardization | Normalize package/module naming and boundary checks in mixed Java/TS surfaces | No package drift warnings; boundary rules pass in CI |

## Suggested Execution Waves

1. Wave 1 (Immediate): YAPPC-001, YAPPC-002, YAPPC-003, YAPPC-004
2. Wave 2 (Short-term hardening): YAPPC-005, YAPPC-006, YAPPC-007, YAPPC-008
3. Wave 3 (Mid-term quality): YAPPC-009, YAPPC-010, YAPPC-011, YAPPC-012, YAPPC-013, YAPPC-014
4. Wave 4 (Long-term maturity): YAPPC-015, YAPPC-016, YAPPC-017, YAPPC-018
