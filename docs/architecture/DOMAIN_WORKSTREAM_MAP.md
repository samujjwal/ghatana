# Domain Workstream Map

Target commit baseline: `3d11768b045870c73b7f1ad7761a7b916203f768`

Authoritative human-readable domain map for governance truth. Every implementation slice must re-check the latest state of the target baseline before editing code, docs, contracts, schemas, or generated workspace wiring.

**Classification:** declared-only

## Current-State Classifications

- Existing and executable (evidence: `pnpm test`, `./gradlew check`, `node scripts/check-current-state-claims.mjs`)
- Existing but partial
- Declared only
- Target architecture
- Anti-pattern

## Phase / Journey Crosswalk

| Domain | Phases | Journeys | Evidence | Validation |
| --- | --- | --- | --- | --- |
| Platform Coherence & Governance | Phase 0, Phase 8 | J1, J2, J3, J4, J5, J6, J7 | `docs/GOVERNANCE.md`, `scripts/check-doc-truth.mjs`, `config/domain-registry.json` | `node scripts/check-doc-authority.mjs`, `node scripts/validate-domain-registry.mjs`, `node scripts/check-domain-boundaries.mjs` |
| Product Development Kernel Lifecycle | Phase 2, Phase 3 | J2, J4 | `platform/typescript/kernel-product-contracts`, `platform/typescript/kernel-lifecycle`, `scripts/kernel-product.mjs`, `products/digital-marketing/kernel-product.yaml` | `pnpm --dir platform/typescript/kernel-product-contracts test`, `pnpm --dir platform/typescript/kernel-lifecycle test`, `node scripts/check-kernel-platform-lifecycle.mjs`, `node scripts/check-digital-marketing-lifecycle-pilot.mjs` |
| Toolchain Adapter Runtime | Phase 2, Phase 3 | J2, J4 | `platform/typescript/kernel-toolchains`, `config/command-registry-manifest.json`, `scripts/check-toolchain-adapter-contracts.mjs` | `pnpm --dir platform/typescript/kernel-toolchains test`, `pnpm --dir platform/typescript/kernel-toolchains typecheck`, `node scripts/check-toolchain-adapter-contracts.mjs` |
| Artifact Supply Chain & Provenance | Phase 3 | J2, J4, J6 | `platform/typescript/kernel-artifacts`, `platform/typescript/kernel-providers/src/artifacts`, `platform/typescript/kernel-providers/src/provenance` | `pnpm --dir platform/typescript/kernel-artifacts test`, `pnpm --dir platform/typescript/kernel-providers test`, `node scripts/check-product-artifact-contracts.mjs` |
| Deployment, Environment & Release | Phase 3 | J2, J4 | `platform/typescript/kernel-deployment`, `platform/typescript/kernel-release`, `config/deployment-targets.json`, `config/rollback-policies.json` | `pnpm --dir platform/typescript/kernel-deployment test`, `pnpm --dir platform/typescript/kernel-release test`, `node scripts/check-product-deployment-contracts.mjs`, `node scripts/check-product-environment-contracts.mjs` |
| Data Cloud Runtime Truth | Phase 5, Phase 8 | J3, J5, J6 | `products/data-cloud`, `products/data-cloud/libs/kernel-bridge-providers`, `scripts/check-kernel-provider-mode.mjs`, `scripts/check-data-access-contract.mjs` | `pnpm --dir products/data-cloud/libs/kernel-bridge-providers test`, `node scripts/check-kernel-provider-mode.mjs`, `node scripts/check-data-access-contract.mjs` |
| AEP Agent Runtime Governance | Phase 4 | J3 | `products/aep`, `platform/java/agent-core`, `scripts/check-agentic-lifecycle-action-contracts.mjs` | `node scripts/check-agentic-lifecycle-action-contracts.mjs`, `./gradlew :platform:java:agent-core:check` |
| Semantic Artifact Intelligence | Phase 6 | J5 | `platform/typescript/kernel-product-contracts/src/artifact-intelligence`, `scripts/check-yappc-artifact-intelligence-boundary.mjs` | `pnpm --dir platform/typescript/kernel-product-contracts test`, `node scripts/check-yappc-artifact-intelligence-boundary.mjs` |
| Canvas, Diagramming & Context | Phase 1, Phase 8 | J1, J5 | `platform/typescript/canvas`, `platform/typescript/ui-builder`, `docs/platform-libraries` | `pnpm --dir platform/typescript/canvas test`, `node scripts/check-deprecated-imports.mjs` |
| UI Builder Preview | Phase 1 | J1, J2 | `platform/typescript/ui-builder`, `platform/typescript/ghatana-studio`, `scripts/check-studio-kernel-api.mjs` | `pnpm --dir platform/typescript/ghatana-studio test`, `node scripts/check-studio-kernel-api.mjs` |
| Design System Registry & Generator | Phase 1, Phase 8 | J1, J2, J5 | `platform/typescript/design-system`, `platform/typescript/ds-registry`, `platform/typescript/ds-generator`, `platform/typescript/LIBRARY_GOVERNANCE.md` | `node scripts/check-platform-package-governance.js`, `node scripts/check-deprecated-imports.mjs`, `pnpm --dir platform/typescript/design-system test` |
| Ghatana Studio | Phase 1 | J1, J2, J3, J4, J5, J6 | `platform/typescript/ghatana-studio`, `scripts/check-studio-kernel-api.mjs` | `pnpm --dir platform/typescript/ghatana-studio test`, `node scripts/check-studio-kernel-api.mjs` |
| Product Domain Packs | Phase 7 | J7 | `products/phr/domain-pack-manifest.yaml`, `products/finance/domain-pack-manifest.yaml`, `products/flashit/domain-pack-manifest.yaml`, `products/digital-marketing/dm-domain-packs/domain-pack.json` | `node scripts/check-product-manifest-contracts.mjs`, `node scripts/check-product-conformance.mjs` |
| Event Streaming & Operational Graph | Phase 3, Phase 5 | J3, J5, J6 | `platform/typescript/events`, `platform/typescript/realtime`, `platform/java/workflow`, `products/data-cloud/planes/data/entity` | `pnpm --dir platform/typescript/events test`, `node scripts/check-openapi-contract-canonical.mjs` |
| Security, Privacy & Policy Compliance | Phase 0, Phase 8 | J2, J4, J6, J7 | `platform/java/security`, `config/security-secret-scan.json`, `scripts/check-security-workflow-coverage.mjs`, `scripts/check-secret-default-credentials.mjs` | `node scripts/check-security-workflow-coverage.mjs`, `node scripts/check-secret-default-credentials.mjs`, `./gradlew :platform:java:security:check` |
| Observability, Health & Operations | Phase 3, Phase 5, Phase 8 | J2, J3, J4, J6 | `platform/java/observability`, `monitoring`, `scripts/check-observability-conformance.mjs`, `platform/typescript/kernel-providers/src/health` | `node scripts/check-observability-conformance.mjs`, `pnpm --dir platform/typescript/kernel-providers test`, `./gradlew :platform:java:observability:check` |
| Testing, Verification & Quality Gates | Phase 0, Phase 8 | J1, J2, J3, J4, J5, J6, J7 | `scripts`, `integration-tests`, `platform/java/testing`, `vitest.shared.config.ts` | `pnpm test`, `./gradlew check`, `node scripts/check-production-stubs.mjs`, `node scripts/check-doc-truth.mjs` |

## Domain Workstreams

### 1. Platform Coherence & Governance

- Exact focus: repo-wide governance truth, ownership boundaries, package naming, current-state discipline, and validation surfaces.
- Owner layer: platform
- Current module/package associations: `config/canonical-product-registry.json`, `config/domain-registry.json`, `docs/GOVERNANCE.md`, `scripts/check-doc-truth.mjs`, `scripts/check-domain-boundaries.mjs`
- Consumers: all platform modules, all products, shared services, CI checks
- Current-state classification: Existing but partial
- Owned capabilities: authoritative truth surfaces, ownership mapping, deprecated import enforcement, duplication exception policy
- Forbidden ownership: product-specific runtime logic, product-local lifecycle execution, transport-specific business logic
- Validation gates: `node scripts/check-doc-authority.mjs`, `node scripts/validate-domain-registry.mjs`, `node scripts/check-domain-boundaries.mjs`, `node scripts/check-deprecated-imports.mjs`, `node scripts/check-current-state-claims.mjs`

### 2. Product Development Kernel Lifecycle

- Exact focus: typed product-unit contracts, lifecycle planning, gated execution, lifecycle results, and deterministic manifests.
- Owner layer: platform-kernel
- Current module/package associations: `platform/typescript/kernel-product-contracts`, `platform/typescript/kernel-lifecycle`, `scripts/kernel-product.mjs`, `products/digital-marketing/kernel-product.yaml`
- Consumers: Digital Marketing pilot, product registry tooling, studio and kernel APIs
- Current-state classification: Existing and executable (evidence: `pnpm --dir platform/typescript/kernel-product-contracts test`, `pnpm --dir platform/typescript/kernel-lifecycle test`)
- Owned capabilities: product-unit modeling, lifecycle planning, lifecycle execution, result/event emission, pilot lifecycle validation
- Forbidden ownership: product-specific branching in generic kernel code, direct Data Cloud plane coupling, silent lifecycle enablement for blocked products
- Validation gates: `pnpm --dir platform/typescript/kernel-product-contracts test`, `pnpm --dir platform/typescript/kernel-lifecycle test`, `node scripts/check-kernel-platform-lifecycle.mjs`, `node scripts/check-digital-marketing-lifecycle-pilot.mjs`

### 3. Toolchain Adapter Runtime

- Exact focus: controlled build/test/dev/deploy adapters with explicit capabilities, output evidence, and fail-closed unsupported adapters.
- Owner layer: platform-kernel
- Current module/package associations: `platform/typescript/kernel-toolchains`, `config/command-registry-manifest.json`, `scripts/check-toolchain-adapter-contracts.mjs`
- Consumers: kernel lifecycle planner, lifecycle execution, product surfaces
- Current-state classification: Existing and executable (evidence: `pnpm --dir platform/typescript/kernel-toolchains test`, `node scripts/check-toolchain-adapter-contracts.mjs`)
- Owned capabilities: `gradle-java-service`, `pnpm-vite-react`, output validation, structured execution results, adapter governance
- Forbidden ownership: product-local shell scripts as primary lifecycle runtime, unsupported mobile success paths, unsafe command expansion
- Validation gates: `pnpm --dir platform/typescript/kernel-toolchains test`, `pnpm --dir platform/typescript/kernel-toolchains typecheck`, `node scripts/check-toolchain-adapter-contracts.mjs`

### 4. Artifact Supply Chain & Provenance

- Exact focus: artifact manifest contracts, provenance capture, fingerprinting, and artifact-to-run linkage.
- Owner layer: platform-kernel
- Current module/package associations: `platform/typescript/kernel-artifacts`, `platform/typescript/kernel-providers/src/artifacts`, `platform/typescript/kernel-providers/src/provenance`
- Consumers: lifecycle execution, deployment, release, observability, audit workflows
- Current-state classification: Existing but partial
- Owned capabilities: artifact manifests, artifact references, provenance records, bootstrap artifact persistence
- Forbidden ownership: fake artifact success, product-specific storage adapters under shared platform packages
- Validation gates: `pnpm --dir platform/typescript/kernel-artifacts test`, `pnpm --dir platform/typescript/kernel-providers test`, `node scripts/check-product-artifact-contracts.mjs`

### 5. Deployment, Environment & Release

- Exact focus: deployment manifests, environment validation, health verification, approvals, promotion and rollback contracts.
- Owner layer: platform-kernel
- Current module/package associations: `platform/typescript/kernel-deployment`, `platform/typescript/kernel-release`, `config/deployment-targets.json`, `config/rollback-policies.json`
- Consumers: lifecycle execution, operator workflows, release governance
- Current-state classification: Existing but partial
- Owned capabilities: deployment contracts, release contracts, promotion planning, rollback manifests, health references
- Forbidden ownership: fake promotion success, unsupported environment defaults, product-specific deployment logic in shared contracts
- Validation gates: `pnpm --dir platform/typescript/kernel-deployment test`, `pnpm --dir platform/typescript/kernel-release test`, `node scripts/check-product-deployment-contracts.mjs`, `node scripts/check-product-environment-contracts.mjs`

### 6. Data Cloud Runtime Truth

- Exact focus: platform-provider runtime truth, provider-bridge readiness, and boundary-safe integration with kernel lifecycle.
- Owner layer: product
- Current module/package associations: `products/data-cloud`, `products/data-cloud/libs/kernel-bridge-providers`, `scripts/check-kernel-provider-mode.mjs`, `scripts/check-data-access-contract.mjs`
- Consumers: kernel provider mode, product runtime truth, platform-provider governance
- Current-state classification: Existing but partial
- Owned capabilities: platform-provider shape, runtime truth evidence, provider bridge boundary
- Forbidden ownership: ordinary lifecycle enablement, shared platform packaging of Data Cloud internals, direct kernel imports of `planes/**`
- Validation gates: `pnpm --dir products/data-cloud/libs/kernel-bridge-providers test`, `node scripts/check-kernel-provider-mode.mjs`, `node scripts/check-data-access-contract.mjs`

### 7. AEP Agent Runtime Governance

- Exact focus: central agent registry governance, runtime policy, and agent execution control surfaces.
- Owner layer: product
- Current module/package associations: `products/aep`, `platform/java/agent-core`, `scripts/check-agentic-lifecycle-action-contracts.mjs`
- Consumers: product agent runtimes, lifecycle agentic actions, registry consumers
- Current-state classification: Existing but partial
- Owned capabilities: agent registration, execution policy, central runtime governance
- Forbidden ownership: duplicate product-local registries, ungoverned agent execution, product-specific leakage into shared agent contracts
- Validation gates: `node scripts/check-agentic-lifecycle-action-contracts.mjs`, `./gradlew :platform:java:agent-core:check`

### 8. Semantic Artifact Intelligence

- Exact focus: semantic artifact references, graph evidence, dependency evidence, hotspot and residual-island reporting.
- Owner layer: platform-kernel
- Current module/package associations: `platform/typescript/kernel-product-contracts/src/artifact-intelligence`, `scripts/check-yappc-artifact-intelligence-boundary.mjs`
- Consumers: YAPPC, lifecycle planning, governance review, change analysis
- Current-state classification: Existing but partial
- Owned capabilities: semantic artifact references, artifact graph contracts, readiness evidence
- Forbidden ownership: direct YAPPC implementation coupling inside shared contracts, undocumented target-state claims
- Validation gates: `pnpm --dir platform/typescript/kernel-product-contracts test`, `node scripts/check-yappc-artifact-intelligence-boundary.mjs`

### 9. Canvas, Diagramming & Context

- Exact focus: canvas primitives, graph context, and diagram-aware authoring surfaces.
- Owner layer: platform
- Current module/package associations: `platform/typescript/canvas`, `platform/typescript/ui-builder`, `docs/platform-libraries`
- Consumers: studio, design tooling, product authoring surfaces
- Current-state classification: Existing but partial
- Owned capabilities: canvas runtime, diagram plugins, context injection
- Forbidden ownership: deprecated split canvas package names, product-specific canvas forks under platform scope
- Validation gates: `pnpm --dir platform/typescript/canvas test`, `node scripts/check-deprecated-imports.mjs`

### 10. UI Builder Preview

- Exact focus: preview-safe rendering, authoring preview boundaries, and preview security posture.
- Owner layer: platform
- Current module/package associations: `platform/typescript/ui-builder`, `platform/typescript/ghatana-studio`, `scripts/check-studio-kernel-api.mjs`
- Consumers: studio, design-system registry workflows, preview validation
- Current-state classification: Existing but partial
- Owned capabilities: preview orchestration, studio preview integration, preview-safe API boundaries
- Forbidden ownership: unsafe product preview shortcuts, product-specific preview runtimes in shared platform packages
- Validation gates: `pnpm --dir platform/typescript/ghatana-studio test`, `node scripts/check-studio-kernel-api.mjs`

### 11. Design System Registry & Generator

- Exact focus: canonical design-system package governance, registry generation, and schema-backed component governance.
- Owner layer: platform
- Current module/package associations: `platform/typescript/design-system`, `platform/typescript/ds-registry`, `platform/typescript/ds-generator`, `platform/typescript/LIBRARY_GOVERNANCE.md`
- Consumers: product web apps, studio, UI builder, accessibility workflows
- Current-state classification: Existing but partial
- Owned capabilities: canonical package registry, registry generation, package naming enforcement, component schema governance
- Forbidden ownership: deprecated package aliases, product-prefixed packages under `platform/typescript`
- Validation gates: `node scripts/check-platform-package-governance.js`, `node scripts/check-deprecated-imports.mjs`, `pnpm --dir platform/typescript/design-system test`

### 12. Ghatana Studio

- Exact focus: studio shell, kernel API client surfaces, and cross-cutting design/product authoring flows.
- Owner layer: platform
- Current module/package associations: `platform/typescript/ghatana-studio`, `scripts/check-studio-kernel-api.mjs`
- Consumers: product teams, UI builder, kernel lifecycle pilot
- Current-state classification: Existing but partial
- Owned capabilities: studio shell, kernel client integration, authoring entry point
- Forbidden ownership: direct product implementation imports, undocumented kernel API drift
- Validation gates: `pnpm --dir platform/typescript/ghatana-studio test`, `node scripts/check-studio-kernel-api.mjs`

### 13. Product Domain Packs

- Exact focus: product manifest truth, domain-pack ownership, and product-local capability declarations.
- Owner layer: product
- Current module/package associations: `products/phr/domain-pack-manifest.yaml`, `products/finance/domain-pack-manifest.yaml`, `products/flashit/domain-pack-manifest.yaml`, `products/digital-marketing/dm-domain-packs/domain-pack.json`
- Consumers: product scaffolding, manifests, runtime packaging, lifecycle registry
- Current-state classification: Existing but partial
- Owned capabilities: product manifest truth, domain capability declarations, packaging metadata
- Forbidden ownership: platform-wide product domain logic, unregistered product packs, fake manifest conformance
- Validation gates: `node scripts/check-product-manifest-contracts.mjs`, `node scripts/check-product-conformance.mjs`

### 14. Event Streaming & Operational Graph

- Exact focus: event contracts, realtime flow modeling, and operational graph/event surfaces.
- Owner layer: platform
- Current module/package associations: `platform/typescript/events`, `platform/typescript/realtime`, `platform/java/workflow`, `products/data-cloud/planes/data/entity`
- Consumers: Data Cloud, AEP, product integrations, observability workflows
- Current-state classification: Existing but partial
- Owned capabilities: event contracts, realtime transport, operational graph/event propagation
- Forbidden ownership: product-specific event schemas in generic platform packages, silent event failures
- Validation gates: `pnpm --dir platform/typescript/events test`, `node scripts/check-openapi-contract-canonical.mjs`

### 15. Security, Privacy & Policy Compliance

- Exact focus: security validation, privacy gates, policy enforcement, and compliance evidence.
- Owner layer: platform
- Current module/package associations: `platform/java/security`, `config/security-secret-scan.json`, `scripts/check-security-workflow-coverage.mjs`, `scripts/check-secret-default-credentials.mjs`
- Consumers: regulated products, deployment flows, lifecycle gates, CI
- Current-state classification: Existing and executable (evidence: `node scripts/check-security-workflow-coverage.mjs`, `node scripts/check-secret-default-credentials.mjs`)
- Owned capabilities: security gates, policy enforcement, secret hygiene, privacy/compliance evidence hooks
- Forbidden ownership: product-local security forks, default credentials, silent compliance bypasses
- Validation gates: `node scripts/check-security-workflow-coverage.mjs`, `node scripts/check-secret-default-credentials.mjs`, `./gradlew :platform:java:security:check`

### 16. Observability, Health & Operations

- Exact focus: structured logs, metrics, traces, health semantics, and operational validation.
- Owner layer: platform
- Current module/package associations: `platform/java/observability`, `monitoring`, `scripts/check-observability-conformance.mjs`, `platform/typescript/kernel-providers/src/health`
- Consumers: all deployable services, lifecycle verification, operations tooling
- Current-state classification: Existing and executable (evidence: `node scripts/check-observability-conformance.mjs`, `pnpm --dir platform/typescript/kernel-providers test`)
- Owned capabilities: observability contracts, health snapshots, operational readiness checks
- Forbidden ownership: silent health failures, unverifiable deployment health, product-specific observability drift in shared modules
- Validation gates: `node scripts/check-observability-conformance.mjs`, `pnpm --dir platform/typescript/kernel-providers test`, `./gradlew :platform:java:observability:check`

### 17. Testing, Verification & Quality Gates

- Exact focus: contract checks, boundary checks, lifecycle verification, and anti-theatre enforcement.
- Owner layer: platform
- Current module/package associations: `scripts`, `integration-tests`, `platform/java/testing`, `vitest.shared.config.ts`
- Consumers: all modules, CI, audit and release workflows
- Current-state classification: Existing and executable (evidence: `pnpm test`, `./gradlew check`, `node scripts/check-production-stubs.mjs`)
- Owned capabilities: regression tests, conformance checks, anti-theatre enforcement, focused validation gates
- Forbidden ownership: object-literal test theatre, disabled tests without tracking, unvalidated lifecycle claims
- Validation gates: `pnpm test`, `./gradlew check`, `node scripts/check-production-stubs.mjs`, `node scripts/check-doc-truth.mjs`

## Forbidden Ownership Rules

- Platform governance domains do not own product-specific runtime behavior.
- Shared platform packages do not import product implementations.
- Kernel lifecycle code does not own product-specific branching for Data Cloud, YAPPC, PHR, Finance, or FlashIt enablement.
- Product-local lifecycle runners are anti-patterns and must be flagged.

## Five-Phase Implementation Sequence

1. Establish governance truth.
2. Stabilize Product Development Kernel lifecycle foundation.
3. Harden provider bridges and platform-provider boundaries.
4. Expand regulated and multi-surface readiness evidence.
5. Enable broader lifecycle execution only after gates, adapters, manifests, and tests are validated and ready for use.