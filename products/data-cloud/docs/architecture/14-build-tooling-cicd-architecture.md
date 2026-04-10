# Build, Tooling, and CI/CD Architecture

See [04-workspace-module-architecture.md](04-workspace-module-architecture.md), [17-deployment-environments.md](17-deployment-environments.md), and [`diagrams/build-cicd.mmd`](diagrams/build-cicd.mmd).

## Build Toolchain

| Area | Tooling | Evidence |
|---|---|---|
| Monorepo build | Gradle Kotlin DSL | root `settings.gradle.kts`, root `build.gradle.kts` |
| Product module discovery | explicit includes | `settings.gradle.kts` |
| Java conventions | shared convention plugins + module-local plugins | build files across product modules |
| UI build | Vite + TypeScript | `ui/package.json` |
| Packaging | Dockerfile, Helm chart, raw Kubernetes YAML, Terraform | `Dockerfile`, `helm/**`, `k8s/**`, `terraform/**` |
| Contract drift checks | bash script | `scripts/check-openapi-drift.sh` |
| E2E / smoke | Playwright and shell scripts | `ui/package.json`, `scripts/run-smoke-e2e.sh` |

## Implemented

- Gradle explicitly includes the Data Cloud modules and treats the product as a first-class build target from root.
- `platform-launcher` has the richest quality gates: Jacoco, SpotBugs, OWASP dependency check, JMH plugin, and test fixtures.
- UI tooling is isolated in `products/data-cloud/ui`.
- Product-local GitHub Actions currently include a security scanning workflow, not a full build/deploy workflow.

## Inferred

- CI/CD story is partly product-local and partly centralized at repo root or organizational pipelines. This is supported by the presence of rich deployment assets but only one product-local workflow file.

## Missing

- I did not find the product-local `.gitea` workflow files referenced by `docs/Data-Cloud_Product_Analysis_Report.md`.
- There is no product-local GitHub Actions workflow in `.github/workflows` that clearly builds, tests, publishes, and deploys the product end to end.

## Build Graph

| Module | Build Relationship |
|---|---|
| `launcher` | deployable jar consuming `platform-launcher` and `platform-api` |
| `platform-launcher` | runtime kernel depending on entity/event/config/analytics/spi/plugin modules |
| `platform-api` | extraction-stage API/app layer over shared domain/config/analytics modules |
| `agent-registry` | library/provider depending on `platform-launcher` |
| `feature-store-ingest` | application depending on `spi` and `platform-launcher` |
| `sdk` | codegen-oriented module with no checked-in main sources |

## Code Generation

**Implemented**
- `sdk` exists with tests but no checked-in main sources, implying generated artifacts land under `build/`.
- gRPC service classes imply proto generation is part of backend build flow.

**Missing**
- There is no single generated-code architecture note that explains ownership and publication expectations for `sdk`.

## Packaging Findings

| Finding | Evidence | Impact |
|---|---|---|
| Dockerfile claims “fat/shadow jar” but runs `:launcher:jar` | `Dockerfile`, `launcher/build.gradle.kts` | likely packaging ambiguity |
| Docker runtime copies only one jar | `COPY ... launcher/build/libs/*.jar app.jar` | dependency packaging may be fragile |
| Health probes assume HTTP server is enabled | Dockerfile and K8s probes | transport-flag mismatch risk |

## CI/CD Findings

### Implemented

- Security workflow runs dependency scanning, CodeQL, SpotBugs, secret detection, and container scanning.
- Helm chart supports autoscaling, probes, external secrets, network policy, and service monitor integration.
- Terraform docs describe AWS production/staging topologies and Helm integration.

### Missing

- No product-local automated release flow from source to image tag to Helm promotion was found in the inspected product workflow set.

### Recommended

- Add one product-local “build-and-verify” workflow and one “publish/deploy” workflow, or update docs to point to the central pipeline location if they already exist elsewhere.
