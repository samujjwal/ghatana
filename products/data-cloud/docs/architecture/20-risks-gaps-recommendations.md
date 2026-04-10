# Risks, Gaps, and Recommendations

See [01-executive-summary.md](01-executive-summary.md), [14-build-tooling-cicd-architecture.md](14-build-tooling-cicd-architecture.md), and [`diagrams/build-cicd.mmd`](diagrams/build-cicd.mmd).

## Risk Matrix

| Title | Description | Evidence | Affected Areas | Impact | Severity | Recommendation | Priority |
|---|---|---|---|---|---|---|---|
| Partial runtime split | `platform-api` and `platform-client` exist as extraction targets, but `platform-launcher` still owns much of the real runtime | build comments in `platform-api/build.gradle.kts`, `platform-client/build.gradle.kts`, launcher-owned routes | backend modularity, onboarding, dependency graph | duplicate abstractions and unclear ownership | high | either finish Phase 2 extraction or collapse unused seams | P0 |
| UI transport duplication | browser code has three competing API/service layers | `ui/src/api/*`, `ui/src/lib/api/*`, `ui/src/services/*` | frontend correctness and maintainability | stale flows and wrong endpoint usage | high | standardize on one client layer and delete or deprecate the others | P0 |
| OpenAPI drift | live launcher route `/api/v1/events/{offset}` is missing from spec | `scripts/check-openapi-drift.sh --warn-only` result, `docs/openapi.yaml` | docs, client generation, review accuracy | external contract cannot be trusted | high | make spec parity blocking in CI and consolidate to one spec | P0 |
| Optional security enforcement | security filter exists but is not wired by default in standalone bootstrap | `DataCloudSecurityFilter.java`, `DataCloudHttpLauncherBootstrap.java`, `DataCloudHttpServer.start()` logs | direct standalone deployments | accidental under-protection | high | require explicit security mode at startup | P0 |
| Packaging ambiguity | Dockerfile assumes a fat jar but launcher build does not obviously produce one | `Dockerfile`, `launcher/build.gradle.kts` | container deployment | broken or brittle runtime image | high | fix packaging path and test image startup in CI | P0 |
| Placeholder modules | `platform-client`, `platform-governance`, `sdk`, and `platform` have incomplete/empty checked-in sources | source counts and file inspection | architecture comprehension | misleading dependency map | medium | mark clearly as scaffolding or remove until needed | P1 |
| Weak structural tests | architecture boundary tests do not enforce much | `platform-api/src/test/.../ModuleBoundaryTest.java` | regression prevention | false confidence | medium | replace placeholders with real ArchUnit or graph checks | P1 |
| Naming and contract drift | collections vs entities, workflows vs pipelines, old routes vs new routes | `ui/src/routes.tsx`, `ui/e2e/collections.spec.ts`, `ui/src/lib/api/workflows.ts` | UI/backend alignment | avoidable translation and onboarding cost | medium | publish canonical vocabulary and update tests/docs | P1 |
| Dependency sprawl | one product pulls many storage and client stacks | `platform-launcher/build.gradle.kts`, Helm/Terraform assets | runtime complexity and security surface | increased maintenance load | medium | narrow per-module dependency scopes and extract lighter clients | P1 |
| Observability blind spots | tracing and security-state visibility are weaker than metrics/logging | startup code and observability searches | operations | slower diagnosis | medium | add workflow-level telemetry and security-mode metrics | P2 |

## Quick Wins

1. Update `products/data-cloud/docs/openapi.yaml` for `/api/v1/events/{offset}` and any other current launcher-only paths.
2. Point `ui/e2e/collections.spec.ts` at `/data` and the current collection adapter behavior.
3. Add a startup assertion or explicit warning mode for missing API key resolver / policy engine in standalone deployments.
4. Add a short README to `platform-client`, `platform-governance`, and `sdk` explaining current status.
5. Add one image-build smoke test that runs the Docker image and hits `/health`.

## Medium-Term Refactors

1. Finish the `platform-launcher` split so `platform-api` owns transport/app services and a real `platform-client` owns downstream integration helpers.
2. Extract warm-tier event store code into a dedicated small module so `feature-store-ingest` does not depend on the full runtime kernel.
3. Collapse frontend transport onto `ui/src/lib/api/*` and remove the older `src/api` and `src/services` collection flows.
4. Replace placeholder structural tests with real module boundary rules and route/spec parity checks.

## Deeper Structural Improvements

1. Make one canonical contract tree for REST, gRPC, event schemas, and config contracts.
2. Reduce `platform-launcher` into narrower runtime assemblies:
   - transport/bootstrap
   - storage/connectors
   - distributed/edge modes
   - observability/security helpers
3. Publish a storage-profile architecture note describing hot/warm/cold/analytics routing and ownership.
4. Create a maintained subsystem ownership map aligned with Data Cloud versus AEP responsibilities.

## Recommended Roadmap

| Phase | Focus | Outcomes |
|---|---|---|
| Phase 1 | stabilization | spec parity, security startup clarity, Docker packaging verification, UI E2E refresh |
| Phase 2 | duplication removal | one browser API layer, real boundary tests, placeholder module status cleanup |
| Phase 3 | structural extraction | finish or simplify the `platform-launcher` split and extract lighter shared modules |
| Phase 4 | platform hardening | contract catalog, observability coverage matrix, deployment automation clarity |

## Final Assessment

**Implemented**
- Data Cloud is a substantial, real product with concrete runtime, UI, plugin, worker, and deployment assets.

**Inferred**
- Most architectural problems are not missing capability; they are drift and half-finished modularization.

**Missing**
- A fully consistent canonical contract and module ownership story.

**Recommended**
- Prioritize stabilization and duplication removal before adding more extraction layers or new surface area.
