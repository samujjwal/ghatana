# Kernel Product Lifecycle Platform — Implementation-Ready Plan

**Repository:** `samujjwal/ghatana`  
**Primary goal:** evolve Kernel from a registry/script-router into a true end-to-end product development, build, packaging, deployment, conformance, and operations platform.

---

## 0. Executive Direction

Kernel should become the platform where a product developer declares product intent and Kernel owns the lifecycle orchestration.

A normal product developer should not need to know whether the product is using Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, Terraform, Playwright, Vitest, or another tool for a lifecycle phase.

The default developer-facing contract should be:

```bash
kernel product plan digital-marketing build
kernel product dev digital-marketing
kernel product validate digital-marketing
kernel product test digital-marketing
kernel product build digital-marketing
kernel product package digital-marketing
kernel product deploy digital-marketing --env local
kernel product verify digital-marketing --env local
kernel product promote digital-marketing --from staging --to prod
kernel product rollback digital-marketing --env prod
```

Power users can still override toolchains, but only through Kernel-governed adapter contracts. They may choose different execution tools, but they cannot bypass Kernel gates, artifacts, observability, security, privacy, deployment, rollback, or boundary rules.

---

## 1. Current Codebase Reality

This plan is based on the current Kernel-related codebase shape.

### 1.1 Current Kernel Strengths

Kernel already has:

1. A canonical product registry:
   - `config/canonical-product-registry.json`
   - `config/canonical-product-registry-schema.json`

2. Generated product shape:
   - `config/product-shape.json`

3. Generated product CI/package artifacts:
   - `config/generated/ci-matrix.json`
   - `config/generated/package-scripts.json`
   - `config/generated/pnpm-workspace-entries.yaml`
   - `config/generated/settings-gradle-includes.kts`

4. A product task router:
   - `scripts/run-product-task.mjs`

5. Product registry artifact generation:
   - `scripts/generate-product-registry-artifacts.mjs`

6. A product scaffolder:
   - `scripts/scaffold-product.mjs`
   - `scripts/check-product-scaffolder.mjs`

7. Affected-product resolution:
   - `scripts/resolve-affected-products.mjs`

8. Product boundary and consumption docs:
   - `docs/kernel/KERNEL_PRODUCT_BOUNDARY.md`
   - `docs/kernel/KERNEL_CONSUMPTION_GUIDE.md`
   - `docs/kernel/PRODUCT_CONFORMANCE_SPEC.md`
   - `docs/kernel/PRODUCT_MANIFEST_SPEC.md`

9. Kernel purity docs and gates:
   - `docs/kernel/KERNEL_PURITY_RULES.md`
   - Kernel purity checks in the platform/kernel Gradle setup

10. Runtime template checks:
    - `scripts/check-runtime-template-conformance.mjs`
    - `config/docker/templates/product-java-service.Dockerfile.template`
    - `config/docker/templates/product-node-api.Dockerfile.template`
    - `config/docker/templates/product-node-web.Dockerfile.template`

11. Shared product UI shell:
    - `platform/typescript/product-shell`
    - `@ghatana/product-shell`

12. Digital Marketing product registration and implementation:
    - `products/digital-marketing/dm-domain-packs/domain-pack.json`
    - `products/digital-marketing/dm-domain-packs/build.gradle.kts`
    - `products/digital-marketing/dm-api/build.gradle.kts`
    - `products/digital-marketing/ui/package.json`

### 1.2 Current Kernel Limitations

Kernel currently behaves mostly as:

```text
registry + generated metadata + script router + conformance checks
```

It does not yet behave as:

```text
product lifecycle orchestration platform
```

Key limitations:

1. `scripts/run-product-task.mjs` delegates directly to pnpm or Gradle.
2. `pnpm product <productId> build` is not guaranteed to build all declared product surfaces.
3. Product lifecycle phases are not first-class modeled concepts.
4. The registry knows `gradleModules` and `pnpmPackages`, but not lifecycle intent.
5. The registry does not model:
   - lifecycle profiles
   - toolchain adapters
   - artifact contracts
   - deployment targets
   - environment policies
   - release/promotion/rollback requirements
6. Deployment is only represented by Dockerfiles, runtime templates, local compose references, and checks; it is not a first-class Kernel lifecycle phase.
7. Shared product shell covers product UI shell concerns, but not lifecycle/deployment/control-plane surfaces.
8. Product scaffolding generates useful files but still exposes tool-specific package and Gradle behavior instead of Kernel-owned lifecycle behavior.
9. Runtime conformance checks are currently token/path-based in places and need to evolve toward schema-backed lifecycle validation.
10. Product developers still need to understand tool-specific behavior for many tasks.

---

## 2. Non-Negotiable Design Principles

### 2.1 Kernel is not a god product

Kernel owns platform-level capabilities only:

```text
product lifecycle
product registry
toolchain adapter selection
surface orchestration
artifact contracts
deployment contracts
environment contracts
security gates
privacy gates
observability gates
policy gates
plugin lifecycle
shared shell/design primitives
conformance checks
generated CI
```

Products own:

```text
domain model
business logic
domain workflows
domain UI content
product-specific policies
product-specific integrations
product-specific adapters
product-specific runtime config
```

### 2.2 Tools are execution backends

Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, Terraform, Vitest, Playwright, etc. should be hidden from default product developer workflows.

They should be represented as Kernel toolchain adapters.

### 2.3 Product developers declare intent

Product developers declare:

```yaml
productId: digital-marketing
lifecycleProfile: standard-web-api-product
surfaces:
  backend-api:
    runtime: java-service
  web:
    runtime: static-web
```

Kernel decides:

```text
which commands to run
which order to run them in
which gates are required
which artifacts are expected
which environment config is needed
how deployment is verified
how rollback works
```

### 2.4 Power users can extend, not bypass

Power users can register custom adapters. However, Kernel still validates:

```text
adapter contract
artifact contract
lifecycle outputs
observability
security
privacy
health checks
conformance
rollback policy
```

### 2.5 Fail closed

Any missing required contract, artifact, environment binding, health check, or conformance result must fail closed.

No silent fallback.

---

## 3. Target Architecture

```text
Developer / Kernel Studio
        |
        v
Kernel Product CLI
        |
        v
Kernel Lifecycle Engine
        |
        +--> Product Registry Loader
        +--> Lifecycle Profile Resolver
        +--> Surface Selector
        +--> Toolchain Adapter Registry
        +--> Execution Graph Planner
        +--> Gate Resolver
        +--> Artifact Resolver
        +--> Environment Resolver
        +--> Deployment Planner
        |
        v
Toolchain Adapters
        |
        +--> Gradle Java Adapter
        +--> pnpm Vite React Adapter
        +--> pnpm Node API Adapter
        +--> Docker Build Adapter
        +--> Compose Deploy Adapter
        +--> Kubernetes Adapter
        +--> Helm Adapter
        +--> Terraform Adapter
        +--> Vitest Adapter
        +--> Playwright Adapter
        |
        v
Build/Test/Package/Deploy Tools
```

---

## 4. New Top-Level Capability Areas

Implement Kernel lifecycle platform across these capability areas:

1. Product lifecycle contracts
2. Product lifecycle engine
3. Toolchain adapter system
4. Artifact system
5. Environment system
6. Deployment system
7. Release/promotion/rollback system
8. Plugin lifecycle system
9. Product shell lifecycle UI
10. Product scaffolding upgrade
11. Registry and generated artifact upgrade
12. Conformance expansion
13. CI/CD generation upgrade
14. Digital Marketing pilot migration
15. Cross-product migration

---

# 5. Workstream A — Canonical Lifecycle Contracts

## A1. Add lifecycle contract docs

### Add

```text
docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md
docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md
docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md
docs/kernel/PRODUCT_ENVIRONMENT_CONTRACT.md
docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md
docs/kernel/PRODUCT_RELEASE_PROMOTION_CONTRACT.md
docs/kernel/PRODUCT_POWER_USER_EXTENSION_GUIDE.md
```

### Update

```text
docs/kernel/KERNEL_PRODUCT_BOUNDARY.md
docs/kernel/KERNEL_CONSUMPTION_GUIDE.md
docs/kernel/PRODUCT_CONFORMANCE_SPEC.md
docs/kernel/PRODUCT_MANIFEST_SPEC.md
docs/kernel/CI_GATE_MATRIX.md
```

### Content requirements

`PRODUCT_LIFECYCLE_CONTRACT.md` must define these phases:

```text
create
bootstrap
dev
validate
test
build
package
release
deploy
verify
promote
rollback
operate
retire
```

For each phase define:

```text
purpose
inputs
outputs
required gates
optional gates
artifact expectations
environment expectations
failure policy
adapter responsibilities
product responsibilities
Kernel responsibilities
```

Example phase contract:

```yaml
phase: build
purpose: Build all selected implemented product surfaces.
inputs:
  - productId
  - sourceRef
  - surfaceSelector
  - lifecycleProfile
  - environment
outputs:
  - lifecycle-plan.json
  - lifecycle-result.json
  - build-manifest.json
  - artifact-manifest.json
  - conformance-summary.json
requiredGates:
  - registry-validation
  - lifecycle-profile-validation
  - manifest-validation
  - dependency-policy
  - source-boundary-check
  - product-build
  - product-test
  - product-conformance
failurePolicy: fail-closed
```

### Acceptance criteria

- All lifecycle phases are documented.
- Gradle/pnpm/Vite/Docker are not exposed as the primary developer contract.
- Adapter responsibilities are clearly separate from Kernel and product responsibilities.
- Existing docs link to the new lifecycle contract.
- No product-specific domain logic is introduced into Kernel docs except as examples clearly marked product-side.

---

# 6. Workstream B — Registry and Schema Expansion

## B1. Extend canonical product registry schema

### Update

```text
config/canonical-product-registry-schema.json
```

### Add schema sections

Add fields to `Product`:

```json
{
  "lifecycleProfile": {
    "type": "string"
  },
  "lifecycle": {
    "$ref": "#/definitions/ProductLifecycleConfiguration"
  },
  "toolchain": {
    "$ref": "#/definitions/ProductToolchainConfiguration"
  },
  "artifacts": {
    "$ref": "#/definitions/ProductArtifactsConfiguration"
  },
  "deployment": {
    "$ref": "#/definitions/ProductDeploymentConfiguration"
  },
  "environments": {
    "$ref": "#/definitions/ProductEnvironmentConfiguration"
  }
}
```

Add definitions:

```text
ProductLifecycleConfiguration
LifecyclePhaseConfiguration
ProductToolchainConfiguration
SurfaceToolchainMapping
ProductArtifactsConfiguration
ProductArtifactDeclaration
ProductDeploymentConfiguration
ProductEnvironmentConfiguration
ProductRollbackConfiguration
```

### Important rule

Do not remove `gradleModules` or `pnpmPackages` yet. Keep them as adapter inputs during migration.

Future state can make them generated or adapter-specific.

## B2. Add lifecycle profile registry

### Add

```text
config/product-lifecycle-profiles.json
config/product-lifecycle-profiles-schema.json
```

### Initial profiles

```text
standard-web-api-product
backend-only-java-service
frontend-only-web-product
mobile-plus-api-product
sdk-product
platform-provider-product
shared-service-product
domain-pack-only-product
```

### Example

```json
{
  "version": "1.0.0",
  "profiles": {
    "standard-web-api-product": {
      "description": "A product with backend API and web UI surfaces.",
      "defaultSurfaces": {
        "dev": ["backend-api", "web"],
        "validate": ["backend-api", "web"],
        "test": ["backend-api", "web"],
        "build": ["backend-api", "web"],
        "package": ["backend-api", "web"],
        "deploy": ["backend-api", "web"]
      },
      "requiredGates": {
        "validate": [
          "registry-validation",
          "manifest-validation",
          "lifecycle-contract-validation"
        ],
        "build": [
          "registry-validation",
          "manifest-validation",
          "typecheck",
          "unit-test",
          "conformance"
        ],
        "deploy": [
          "artifact-validation",
          "environment-validation",
          "health-check",
          "observability-check"
        ]
      },
      "defaultAdapters": {
        "backend-api": "gradle-java-service",
        "web": "pnpm-vite-react",
        "package.backend-api": "docker-buildx",
        "package.web": "docker-static-web",
        "deploy.local": "compose-local"
      }
    }
  }
}
```

## B3. Add toolchain adapter registry

### Add

```text
config/toolchain-adapter-registry.json
config/toolchain-adapter-registry-schema.json
```

### Example

```json
{
  "version": "1.0.0",
  "adapters": {
    "gradle-java-service": {
      "kind": "build-tool",
      "supportedSurfaceTypes": ["backend-api", "worker", "operator"],
      "supportedPhases": ["dev", "validate", "test", "build", "package"],
      "requires": ["gradleModules"],
      "outputs": ["jvm-classes", "test-report", "coverage-report", "jar"]
    },
    "pnpm-vite-react": {
      "kind": "build-tool",
      "supportedSurfaceTypes": ["web"],
      "supportedPhases": ["dev", "validate", "test", "build", "package"],
      "requires": ["packagePath"],
      "outputs": ["static-web-bundle", "test-report", "typecheck-report"]
    },
    "compose-local": {
      "kind": "deployment-tool",
      "supportedSurfaceTypes": ["backend-api", "web", "worker"],
      "supportedPhases": ["deploy", "verify", "rollback"],
      "requires": ["deployment.local.composeFile"],
      "outputs": ["deployment-manifest", "health-check-report"]
    }
  }
}
```

## B4. Add environment registry

### Add

```text
config/environments/local.json
config/environments/dev.json
config/environments/staging.json
config/environments/prod.json
config/environments/environment-schema.json
```

### Example local

```json
{
  "schemaVersion": "1.0.0",
  "id": "local",
  "deploymentTarget": "compose-local",
  "secretsProvider": "local-env",
  "configProvider": "local-files",
  "approvalRequired": false,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "health-check"
  ],
  "observabilityProfile": "local-standard"
}
```

### Example prod

```json
{
  "schemaVersion": "1.0.0",
  "id": "prod",
  "deploymentTarget": "kubernetes",
  "secretsProvider": "external-secret-store",
  "configProvider": "environment-config-service",
  "approvalRequired": true,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "security",
    "privacy",
    "license-policy",
    "conformance",
    "e2e",
    "performance",
    "rollback-plan",
    "approval"
  ],
  "observabilityProfile": "prod-standard"
}
```

## B5. Update generated product shape

### Update

```text
config/product-shape.json
scripts/generate-product-registry-artifacts.mjs
```

### Add generated fields

```json
{
  "products": {
    "digital-marketing": {
      "ui": true,
      "uiMode": "web",
      "surfaces": ["backend-api", "web"],
      "surfaceStatuses": {
        "backend-api": "implemented",
        "web": "implemented"
      },
      "clientPackages": ["products/digital-marketing/ui/package.json"],
      "lifecycleProfile": "standard-web-api-product",
      "lifecyclePhases": ["dev", "validate", "test", "build", "package", "deploy"],
      "deploymentTargets": ["local-compose"],
      "artifactTypes": ["jvm-service", "static-web-bundle", "container-image"]
    }
  }
}
```

## B6. Update CI matrix generation

### Update

```text
config/generated/ci-matrix.json
scripts/generate-product-registry-artifacts.mjs
```

### Add fields

```json
{
  "products": ["digital-marketing"],
  "productsWithUI": ["digital-marketing"],
  "productsWithTests": ["digital-marketing"],
  "productsWithIntegrationTests": [],
  "productsWithLifecycle": ["digital-marketing"],
  "productsWithDeployLocal": ["digital-marketing"],
  "lifecycleProfiles": {
    "digital-marketing": "standard-web-api-product"
  },
  "surfacesByProduct": {
    "digital-marketing": ["backend-api", "web"]
  },
  "phasesByProduct": {
    "digital-marketing": ["validate", "test", "build", "package", "deploy"]
  }
}
```

### Acceptance criteria

- Registry validates old and new fields during migration.
- Lifecycle-enabled products are discoverable through generated files.
- CI matrix can drive lifecycle workflows.
- Existing products continue to work while migration proceeds.
- Products without lifecycle fields get explicit migration warnings.

---

# 7. Workstream C — Kernel Lifecycle Engine

## C1. Add lifecycle package

### Add

```text
platform/typescript/kernel-lifecycle/package.json
platform/typescript/kernel-lifecycle/tsconfig.json
platform/typescript/kernel-lifecycle/vitest.config.ts
platform/typescript/kernel-lifecycle/src/index.ts
```

### Add domain files

```text
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePlan.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleResult.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleStep.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleEvent.ts
platform/typescript/kernel-lifecycle/src/domain/ProductSurface.ts
platform/typescript/kernel-lifecycle/src/domain/ProductArtifact.ts
platform/typescript/kernel-lifecycle/src/domain/ProductEnvironment.ts
platform/typescript/kernel-lifecycle/src/domain/ProductGate.ts
platform/typescript/kernel-lifecycle/src/domain/ProductFailurePolicy.ts
```

### Add planning files

```text
platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts
platform/typescript/kernel-lifecycle/src/planning/ProductLifecycleGraphBuilder.ts
platform/typescript/kernel-lifecycle/src/planning/SurfaceSelector.ts
platform/typescript/kernel-lifecycle/src/planning/LifecycleProfileResolver.ts
platform/typescript/kernel-lifecycle/src/planning/GateResolver.ts
platform/typescript/kernel-lifecycle/src/planning/ArtifactResolver.ts
platform/typescript/kernel-lifecycle/src/planning/EnvironmentResolver.ts
platform/typescript/kernel-lifecycle/src/planning/ToolchainResolver.ts
```

### Add execution files

```text
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts
platform/typescript/kernel-lifecycle/src/execution/ExecutionContext.ts
platform/typescript/kernel-lifecycle/src/execution/ExecutionLogger.ts
platform/typescript/kernel-lifecycle/src/execution/ExecutionFailureHandler.ts
platform/typescript/kernel-lifecycle/src/execution/ExecutionResultCollector.ts
```

### Add I/O files

```text
platform/typescript/kernel-lifecycle/src/io/CanonicalRegistryLoader.ts
platform/typescript/kernel-lifecycle/src/io/LifecycleProfileLoader.ts
platform/typescript/kernel-lifecycle/src/io/ToolchainAdapterRegistryLoader.ts
platform/typescript/kernel-lifecycle/src/io/EnvironmentLoader.ts
platform/typescript/kernel-lifecycle/src/io/ProductLifecyclePlanWriter.ts
platform/typescript/kernel-lifecycle/src/io/ProductLifecycleResultWriter.ts
platform/typescript/kernel-lifecycle/src/io/ProductArtifactManifestWriter.ts
platform/typescript/kernel-lifecycle/src/io/KernelOutPathResolver.ts
```

### Add validation files

```text
platform/typescript/kernel-lifecycle/src/validation/ProductLifecycleContractValidator.ts
platform/typescript/kernel-lifecycle/src/validation/ProductRegistryLifecycleValidator.ts
platform/typescript/kernel-lifecycle/src/validation/ProductSurfaceValidator.ts
platform/typescript/kernel-lifecycle/src/validation/ProductEnvironmentValidator.ts
platform/typescript/kernel-lifecycle/src/validation/ProductArtifactValidator.ts
platform/typescript/kernel-lifecycle/src/validation/ProductGateValidator.ts
```

### Add tests

```text
platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecyclePlanner.test.ts
platform/typescript/kernel-lifecycle/src/__tests__/SurfaceSelector.test.ts
platform/typescript/kernel-lifecycle/src/__tests__/LifecycleProfileResolver.test.ts
platform/typescript/kernel-lifecycle/src/__tests__/ToolchainResolver.test.ts
platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecycleExecutor.test.ts
platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecycleContractValidator.test.ts
```

## C2. Implement lifecycle phase enum

```ts
export type ProductLifecyclePhase =
  | 'create'
  | 'bootstrap'
  | 'dev'
  | 'validate'
  | 'test'
  | 'build'
  | 'package'
  | 'release'
  | 'deploy'
  | 'verify'
  | 'promote'
  | 'rollback'
  | 'operate'
  | 'retire';
```

## C3. Implement lifecycle plan structure

```ts
export interface ProductLifecyclePlan {
  schemaVersion: '1.0.0';
  productId: string;
  phase: ProductLifecyclePhase;
  lifecycleProfile: string;
  environment?: string;
  sourceRef?: string;
  surfaces: ProductSurfaceSelection[];
  gates: ProductGatePlan[];
  steps: ProductLifecycleStep[];
  expectedArtifacts: ProductExpectedArtifact[];
  outputDirectory: string;
}
```

## C4. Implement lifecycle result structure

```ts
export interface ProductLifecycleResult {
  schemaVersion: '1.0.0';
  productId: string;
  phase: ProductLifecyclePhase;
  status: 'succeeded' | 'failed' | 'skipped';
  startedAt: string;
  completedAt: string;
  steps: ProductLifecycleStepResult[];
  gates: ProductGateResult[];
  artifacts: ProductArtifact[];
  outputDirectory: string;
  failure?: {
    stepId: string;
    message: string;
    cause?: string;
  };
}
```

## C5. Add scripts

### Add

```text
scripts/kernel-product.mjs
scripts/run-product-lifecycle.mjs
scripts/print-product-lifecycle-plan.mjs
```

### Update

```text
package.json
```

Add scripts:

```json
{
  "kernel": "node ./scripts/kernel-product.mjs",
  "product:lifecycle": "node ./scripts/run-product-lifecycle.mjs",
  "product:plan": "node ./scripts/print-product-lifecycle-plan.mjs"
}
```

## C6. Keep old command compatibility

### Update

```text
scripts/run-product-task.mjs
```

Change it from direct pnpm/Gradle delegation to compatibility wrapper:

```text
pnpm product <productId> <task> [surface]
```

maps to:

```text
node scripts/run-product-lifecycle.mjs <task> <productId> --surface <surface>
```

Only supported lifecycle tasks should be accepted:

```text
dev
validate
test
build
package
deploy
verify
release
promote
rollback
```

Legacy unsupported tasks can be passed through only with explicit flag:

```bash
pnpm product digital-marketing raw build web
```

or:

```bash
kernel product exec digital-marketing --adapter gradle-java-service --command build
```

## C7. Lifecycle output path

Every run writes:

```text
.kernel/out/products/<productId>/<phase>/<yyyyMMdd-HHmmss>/
  lifecycle-plan.json
  lifecycle-result.json
  logs/
```

## Acceptance criteria

- `pnpm kernel product plan digital-marketing build` prints a plan and writes `lifecycle-plan.json`.
- `pnpm kernel product build digital-marketing --dry-run` prints all steps without running tools.
- `pnpm kernel product build digital-marketing` runs all selected surfaces, not only pnpm packages.
- `pnpm product digital-marketing build` still works, but delegates to lifecycle engine.
- Planner is unit tested without invoking external tools.
- Executor is tested using fake adapters.

---

# 8. Workstream D — Toolchain Adapter System

## D1. Add toolchain package

### Add

```text
platform/typescript/kernel-toolchains/package.json
platform/typescript/kernel-toolchains/tsconfig.json
platform/typescript/kernel-toolchains/vitest.config.ts
platform/typescript/kernel-toolchains/src/index.ts
```

### Add core files

```text
platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapterContext.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts
platform/typescript/kernel-toolchains/src/ToolchainExecutionResult.ts
platform/typescript/kernel-toolchains/src/ToolchainPlanStep.ts
platform/typescript/kernel-toolchains/src/ToolchainOutputValidator.ts
```

### Add adapters

```text
platform/typescript/kernel-toolchains/src/adapters/GradleJavaServiceAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/GradleJavaLibraryAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/PnpmViteReactAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/PnpmNodeApiAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/VitestAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/PlaywrightAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/DockerBuildxAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/ComposeLocalAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/NoopDocumentationAdapter.ts
```

## D2. Adapter interface

```ts
export interface ToolchainAdapter {
  readonly id: string;
  readonly supportedPhases: ProductLifecyclePhase[];
  readonly supportedSurfaceTypes: ProductSurfaceType[];

  plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]>;
  execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult>;
  validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult>;
}
```

## D3. Gradle Java service adapter

### Responsibilities

```text
- resolve Gradle module for a product surface
- plan dev/test/build/validate/package commands
- run Gradle tasks through child_process
- collect test reports
- collect coverage reports
- collect JAR/build outputs
- validate expected outputs
```

### Mapping

For `digital-marketing` backend API:

```text
surface: backend-api
path: products/digital-marketing/dm-api
gradle module: :products:digital-marketing:dm-api
```

Phase mapping:

```text
dev       -> runDmosApiServer if configured, else bootRun/run if declared
validate  -> check
test      -> test
build     -> build
package   -> assemble or installDist, depending runtime profile
```

Important: backend dev cannot be guessed reliably. Add explicit lifecycle config for backend dev command.

## D4. pnpm Vite React adapter

### Responsibilities

```text
- resolve packagePath
- run dev/build/test/typecheck scripts
- collect dist output
- collect test results where configured
- validate static bundle
```

For `digital-marketing` web:

```text
packagePath: products/digital-marketing/ui/package.json
dev: pnpm --dir products/digital-marketing/ui dev
build: pnpm --dir products/digital-marketing/ui build
test: pnpm --dir products/digital-marketing/ui test
```

## D5. Adapter safety rules

Every adapter must:

```text
- declare supported phases
- declare supported surface types
- support dry-run planning
- provide structured execution result
- validate outputs
- emit logs
- fail closed
- avoid shell injection
- use explicit args arrays instead of shell strings
```

## D6. Tests

### Add

```text
platform/typescript/kernel-toolchains/src/__tests__/GradleJavaServiceAdapter.test.ts
platform/typescript/kernel-toolchains/src/__tests__/PnpmViteReactAdapter.test.ts
platform/typescript/kernel-toolchains/src/__tests__/ToolchainAdapterRegistry.test.ts
platform/typescript/kernel-toolchains/src/__tests__/ToolchainOutputValidator.test.ts
```

### Test requirements

Use fake command runner, not real Gradle/pnpm in unit tests.

Integration tests can run real tools separately:

```text
platform/typescript/kernel-toolchains/src/__tests__/integration/GradleJavaServiceAdapter.integration.test.ts
platform/typescript/kernel-toolchains/src/__tests__/integration/PnpmViteReactAdapter.integration.test.ts
```

## Acceptance criteria

- Lifecycle engine does not know Gradle/pnpm internals.
- All tool-specific logic lives in adapters.
- Digital Marketing web and backend build through adapters.
- Backend `dev` is explicitly mapped through lifecycle config.
- Adapter outputs are validated.

---

# 9. Workstream E — Artifact System

## E1. Add artifact package

### Add

```text
platform/typescript/kernel-artifacts/package.json
platform/typescript/kernel-artifacts/tsconfig.json
platform/typescript/kernel-artifacts/vitest.config.ts
platform/typescript/kernel-artifacts/src/index.ts
```

### Add files

```text
platform/typescript/kernel-artifacts/src/ProductArtifact.ts
platform/typescript/kernel-artifacts/src/ProductArtifactManifest.ts
platform/typescript/kernel-artifacts/src/ProductBuildManifest.ts
platform/typescript/kernel-artifacts/src/ProductReleaseManifest.ts
platform/typescript/kernel-artifacts/src/ProductDeploymentManifest.ts
platform/typescript/kernel-artifacts/src/ProductArtifactRegistry.ts
platform/typescript/kernel-artifacts/src/ProductArtifactResolver.ts
platform/typescript/kernel-artifacts/src/ProductArtifactValidator.ts
platform/typescript/kernel-artifacts/src/ArtifactFingerprint.ts
platform/typescript/kernel-artifacts/src/ArtifactStorage.ts
```

## E2. Add schemas

### Add

```text
config/schemas/product-artifact-manifest.schema.json
config/schemas/product-build-manifest.schema.json
config/schemas/product-release-manifest.schema.json
config/schemas/product-deployment-manifest.schema.json
```

## E3. Build artifact manifest

Every build writes:

```text
.kernel/out/products/<productId>/build/<timestamp>/artifact-manifest.json
```

Example:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "phase": "build",
  "sourceRef": "main",
  "artifacts": [
    {
      "surface": "backend-api",
      "type": "jvm-service",
      "path": "products/digital-marketing/dm-api/build/libs/dm-api.jar",
      "fingerprint": "sha256:...",
      "producedBy": "gradle-java-service"
    },
    {
      "surface": "web",
      "type": "static-web-bundle",
      "path": "products/digital-marketing/ui/dist",
      "fingerprint": "sha256:...",
      "producedBy": "pnpm-vite-react"
    }
  ]
}
```

## E4. Package artifact manifest

Every package phase writes:

```text
.kernel/out/products/<productId>/package/<timestamp>/artifact-manifest.json
```

Artifact examples:

```text
container-image
static-web-image
jvm-runtime-distribution
node-runtime-distribution
mobile-bundle
sdk-package
```

## E5. Acceptance criteria

- Build phase emits artifact manifest.
- Package phase consumes build artifact manifest.
- Deploy phase consumes package/release artifact manifest.
- Fingerprints are calculated.
- Missing declared artifact fails build/package/deploy.
- Artifact schema validation is enforced.

---

# 10. Workstream F — Deployment Platform

## F1. Add deployment package

### Add

```text
platform/typescript/kernel-deployment/package.json
platform/typescript/kernel-deployment/tsconfig.json
platform/typescript/kernel-deployment/vitest.config.ts
platform/typescript/kernel-deployment/src/index.ts
```

### Add files

```text
platform/typescript/kernel-deployment/src/DeploymentTarget.ts
platform/typescript/kernel-deployment/src/DeploymentPlan.ts
platform/typescript/kernel-deployment/src/DeploymentResult.ts
platform/typescript/kernel-deployment/src/DeploymentEnvironment.ts
platform/typescript/kernel-deployment/src/DeploymentHealthCheck.ts
platform/typescript/kernel-deployment/src/DeploymentRollbackPlan.ts
platform/typescript/kernel-deployment/src/DeploymentPromotionPolicy.ts
platform/typescript/kernel-deployment/src/DeploymentManifest.ts
platform/typescript/kernel-deployment/src/DeploymentVerifier.ts
```

### Add adapters

```text
platform/typescript/kernel-deployment/src/adapters/ComposeDeploymentAdapter.ts
platform/typescript/kernel-deployment/src/adapters/KubernetesDeploymentAdapter.ts
platform/typescript/kernel-deployment/src/adapters/HelmDeploymentAdapter.ts
platform/typescript/kernel-deployment/src/adapters/TerraformDeploymentAdapter.ts
```

## F2. Add deployment config

### Add

```text
config/deployment/deployment-targets.json
config/deployment/deployment-targets-schema.json
config/deployment/runtime-profiles.json
config/deployment/health-check-profiles.json
config/deployment/rollback-policies.json
config/deployment/promotion-policies.json
```

## F3. Local deployment first

Implement `compose-local` first.

### Add/standardize

```text
config/docker/templates/product-runtime.compose.yaml
config/docker/templates/product-observability.compose.yaml
```

If these are intended template references but incomplete, make them real Kernel-owned templates.

### Add per-product generated deployment overlays

```text
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/health-checks.json
```

## F4. Deployment behavior

```bash
kernel product deploy digital-marketing --env local
```

Should:

```text
1. Resolve product.
2. Resolve environment.
3. Resolve deployment target.
4. Load latest package artifact manifest unless --artifact is supplied.
5. Validate deployment contract.
6. Render deployment plan.
7. Run deployment adapter.
8. Run health checks.
9. Run smoke checks.
10. Emit deployment-manifest.json.
```

## F5. Acceptance criteria

- `kernel product deploy <product> --env local` works for Digital Marketing.
- Deploy does not rebuild unless `--build` is explicitly passed.
- Deploy validates artifacts before starting.
- Deploy emits deployment manifest.
- Verify checks health endpoints and expected surfaces.
- Rollback plan exists before non-local deploy is allowed.

---

# 11. Workstream G — Release, Promotion, Rollback

## G1. Add release contract

### Add

```text
platform/typescript/kernel-release/package.json
platform/typescript/kernel-release/src/ProductRelease.ts
platform/typescript/kernel-release/src/ProductReleaseManifest.ts
platform/typescript/kernel-release/src/ProductPromotionPlan.ts
platform/typescript/kernel-release/src/ProductRollbackPlan.ts
platform/typescript/kernel-release/src/ProductApprovalGate.ts
```

Alternative: keep these under `kernel-deployment` initially to avoid package sprawl, then split when mature.

## G2. Add commands

```bash
kernel product release digital-marketing
kernel product promote digital-marketing --from dev --to staging
kernel product promote digital-marketing --from staging --to prod
kernel product rollback digital-marketing --env prod
```

## G3. Promotion requirements

For staging/prod:

```text
- artifact manifest exists
- deployment manifest exists
- release manifest exists
- security checks passed
- privacy checks passed
- license checks passed
- conformance checks passed
- e2e checks passed
- performance checks passed
- rollback plan generated
- approval gate satisfied
```

## Acceptance criteria

- Local deploy can be lightweight.
- Non-local deploy must be promotion/release based.
- Production deployment cannot be performed from an unverified build.
- Rollback is always planned before promotion.

---

# 12. Workstream H — Plugin Lifecycle Platform

## H1. Add plugin registry

### Add

```text
config/kernel-plugin-registry.json
config/kernel-plugin-registry-schema.json
```

## H2. Add plugin lifecycle hooks

Model hooks:

```text
onProductRegistered
onProductBootstrapped
onProductDevStarted
onProductValidated
onProductTested
onProductBuildStarted
onProductBuildCompleted
onProductPackaged
onProductDeployStarted
onProductDeployed
onProductVerified
onProductPromoted
onProductRolledBack
onProductRetired
```

## H3. Add plugin runtime contracts

### Add/update Java modules

```text
platform-kernel/kernel-plugin-lifecycle/
platform-kernel/kernel-plugin-testing/
```

If adding Java modules is too disruptive, start with TypeScript config/schema and Java SPI docs, then wire Java later.

## H4. Plugin registry example

```json
{
  "version": "1.0.0",
  "plugins": {
    "plugin-audit-trail": {
      "kind": "platform-plugin",
      "capabilities": ["audit.event.write", "audit.event.search"],
      "requiredRuntimeServices": ["tenant-context", "correlation-id"],
      "lifecycleHooks": ["onProductBuildCompleted", "onProductDeployed"]
    },
    "plugin-compliance": {
      "kind": "platform-plugin",
      "capabilities": ["rulepack.validate", "policy.evaluate"],
      "lifecycleHooks": ["onProductValidated", "onProductDeployStarted"]
    },
    "plugin-human-approval": {
      "kind": "platform-plugin",
      "capabilities": ["approval.request", "approval.resolve"],
      "lifecycleHooks": ["onProductPromoted"]
    }
  }
}
```

## H5. Product manifest plugin binding expansion

Update product manifests, including:

```text
products/digital-marketing/dm-domain-packs/domain-pack.json
```

Add:

```json
{
  "pluginBindings": {
    "plugin-audit-trail": {
      "requiredHooks": ["onProductBuildCompleted", "onProductDeployed"],
      "runtimeRequired": true
    },
    "plugin-compliance": {
      "requiredHooks": ["onProductValidated", "onProductDeployStarted"],
      "runtimeRequired": true
    }
  }
}
```

## Acceptance criteria

- Product plugin consumption is validated against plugin registry.
- Plugin lifecycle hooks are not hardcoded by product.
- Missing plugin lifecycle binding fails conformance.
- Plugins remain product-neutral.

---

# 13. Workstream I — Product Shell and Kernel Studio Expansion

## I1. Extend product shell contracts

### Update

```text
platform/typescript/product-shell/src/index.ts
platform/typescript/product-shell/src/types.ts
```

### Add contracts

```text
platform/typescript/product-shell/src/contracts/product-lifecycle.ts
platform/typescript/product-shell/src/contracts/product-artifact.ts
platform/typescript/product-shell/src/contracts/product-deployment.ts
platform/typescript/product-shell/src/contracts/product-environment.ts
platform/typescript/product-shell/src/contracts/product-health.ts
```

## I2. Add lifecycle UI components

### Add

```text
platform/typescript/product-shell/src/components/lifecycle/ProductLifecycleStatusPanel.tsx
platform/typescript/product-shell/src/components/lifecycle/ProductLifecyclePlanView.tsx
platform/typescript/product-shell/src/components/lifecycle/ProductLifecycleRunHistory.tsx
platform/typescript/product-shell/src/components/artifacts/ProductArtifactList.tsx
platform/typescript/product-shell/src/components/deployment/ProductDeploymentStatusCard.tsx
platform/typescript/product-shell/src/components/deployment/ProductEnvironmentBadge.tsx
platform/typescript/product-shell/src/components/deployment/ProductHealthCheckPanel.tsx
platform/typescript/product-shell/src/components/deployment/ProductPromotionAction.tsx
platform/typescript/product-shell/src/components/deployment/ProductRollbackAction.tsx
platform/typescript/product-shell/src/components/conformance/ProductConformanceSummaryCard.tsx
```

## I3. Add Kernel Studio package

### Add

```text
platform/typescript/kernel-studio/package.json
platform/typescript/kernel-studio/src/App.tsx
platform/typescript/kernel-studio/src/pages/ProductsPage.tsx
platform/typescript/kernel-studio/src/pages/ProductDetailPage.tsx
platform/typescript/kernel-studio/src/pages/ProductLifecyclePage.tsx
platform/typescript/kernel-studio/src/pages/ProductArtifactsPage.tsx
platform/typescript/kernel-studio/src/pages/ProductDeploymentsPage.tsx
platform/typescript/kernel-studio/src/pages/ProductEnvironmentsPage.tsx
platform/typescript/kernel-studio/src/pages/ProductConformancePage.tsx
```

## I4. Acceptance criteria

- Products do not create custom lifecycle/deployment status UI.
- Shared lifecycle components consume Kernel lifecycle result contracts.
- Kernel Studio can show plan, build history, artifact history, deployment history, health checks, and rollback state.
- Access remains route-entitlement-driven.

---

# 14. Workstream J — Scaffolder Upgrade

## J1. Update scaffolder

### Update

```text
scripts/scaffold-product.mjs
scripts/check-product-scaffolder.mjs
```

## J2. Generated product files

Scaffolder should add:

```text
products/<id>/kernel-product.yaml
products/<id>/lifecycle.local.yaml
products/<id>/lifecycle.dev.yaml
products/<id>/runtime/runtime-profile.yaml
products/<id>/deploy/local.compose.yaml
products/<id>/deploy/local.env.example
products/<id>/deploy/health-checks.json
products/<id>/conformance/lifecycle-fixtures.json
products/<id>/conformance/deployment-fixtures.json
products/<id>/conformance/artifact-fixtures.json
```

## J3. Generated `kernel-product.yaml`

```yaml
schemaVersion: 1.0.0
productId: sample-product
lifecycleProfile: standard-web-api-product

surfaces:
  backend-api:
    source: products/sample-product
    runtime: java-service
    adapter: gradle-java-service
  web:
    source: products/sample-product/client/web
    runtime: static-web
    adapter: pnpm-vite-react

phases:
  dev:
    defaultSurfaces: [backend-api, web]
  validate:
    defaultSurfaces: [backend-api, web]
  test:
    defaultSurfaces: [backend-api, web]
  build:
    defaultSurfaces: [backend-api, web]
  package:
    defaultTargets: [container, static-web]
  deploy:
    defaultEnvironment: local
```

## J4. Update scaffolder contract test

`check-product-scaffolder.mjs` must validate:

```text
- kernel-product.yaml generated
- lifecycle.local.yaml generated
- runtime profile generated
- deploy local compose overlay generated
- artifact fixture generated
- deployment fixture generated
- generated product supports lifecycle plan
- generated product supports lifecycle dry-run build
```

## Acceptance criteria

- New products can be generated with Kernel lifecycle defaults.
- Generated product passes lifecycle contract checks.
- Generated product supports `kernel product plan`, `validate`, `test`, and `build` in dry-run.
- Generated product does not require developer to manually decide Gradle/pnpm/Vite wiring.

---

# 15. Workstream K — Registry Artifact Generator Upgrade

## K1. Update generator

### Update

```text
scripts/generate-product-registry-artifacts.mjs
scripts/merge-generated-package-scripts.mjs
```

## K2. Generate lifecycle package scripts

Current scripts like:

```json
"build:digital-marketing": "pnpm product digital-marketing build"
```

should remain but route to lifecycle:

```json
"build:digital-marketing": "pnpm kernel product build digital-marketing",
"test:digital-marketing": "pnpm kernel product test digital-marketing",
"dev:digital-marketing": "pnpm kernel product dev digital-marketing",
"package:digital-marketing": "pnpm kernel product package digital-marketing",
"deploy:local:digital-marketing": "pnpm kernel product deploy digital-marketing --env local"
```

Surface-specific scripts:

```json
"build:digital-marketing-web": "pnpm kernel product build digital-marketing --surface web",
"build:digital-marketing-gateway": "pnpm kernel product build digital-marketing --surface backend-api",
"dev:digital-marketing-web": "pnpm kernel product dev digital-marketing --surface web",
"dev:digital-marketing-gateway": "pnpm kernel product dev digital-marketing --surface backend-api"
```

## K3. Generate lifecycle CI matrix

Update `config/generated/ci-matrix.json` as described in Workstream B.

## K4. Acceptance criteria

- Generated root package scripts are lifecycle-first.
- Existing `pnpm product` commands keep compatibility.
- Generated artifacts are deterministic.
- `check:product-registry-artifacts` fails if lifecycle generated artifacts are stale.

---

# 16. Workstream L — Conformance Expansion

## L1. Add lifecycle conformance scripts

### Add

```text
scripts/check-product-lifecycle-contracts.mjs
scripts/check-toolchain-adapter-contracts.mjs
scripts/check-product-artifact-contracts.mjs
scripts/check-product-environment-contracts.mjs
scripts/check-product-deployment-contracts.mjs
scripts/check-product-release-contracts.mjs
scripts/check-product-promotion-contracts.mjs
scripts/check-product-rollback-contracts.mjs
scripts/check-kernel-platform-lifecycle.mjs
```

## L2. Update package.json

Add:

```json
{
  "check:product-lifecycle-contracts": "node ./scripts/check-product-lifecycle-contracts.mjs",
  "check:toolchain-adapter-contracts": "node ./scripts/check-toolchain-adapter-contracts.mjs",
  "check:product-artifact-contracts": "node ./scripts/check-product-artifact-contracts.mjs",
  "check:product-environment-contracts": "node ./scripts/check-product-environment-contracts.mjs",
  "check:product-deployment-contracts": "node ./scripts/check-product-deployment-contracts.mjs",
  "check:kernel-platform-lifecycle": "pnpm check:product-lifecycle-contracts && pnpm check:toolchain-adapter-contracts && pnpm check:product-artifact-contracts && pnpm check:product-environment-contracts && pnpm check:product-deployment-contracts"
}
```

## L3. Conformance rules

The checks must fail when:

```text
- active product lacks lifecycle profile after migration cutoff
- lifecycle profile does not exist
- lifecycle phase references unknown adapter
- lifecycle phase references unimplemented surface
- adapter does not support requested phase
- adapter does not support requested surface
- declared artifact type is unknown
- deployable product lacks deployment target
- deployable product lacks health check profile
- production deploy target lacks rollback policy
- production deploy target lacks approval policy
- product plugin binding references unknown plugin
```

## L4. Acceptance criteria

- Lifecycle conformance can be run independently.
- Lifecycle conformance is part of `check:kernel-product-boundary-audit`.
- Errors are explicit and actionable.
- No token-only validation where schema validation is possible.

---

# 17. Workstream M — CI/CD Workflow Upgrade

## M1. Add lifecycle workflows

### Add

```text
.github/workflows/product-lifecycle-validate.yml
.github/workflows/product-lifecycle-build.yml
.github/workflows/product-lifecycle-package.yml
.github/workflows/product-lifecycle-deploy-local.yml
.github/workflows/product-release.yml
.github/workflows/product-promotion.yml
```

## M2. Workflow behavior

Pull request:

```text
- resolve affected products
- lifecycle validate
- lifecycle test
- lifecycle build
- artifact validation
- conformance checks
```

Main branch:

```text
- lifecycle validate
- lifecycle test
- lifecycle build
- lifecycle package
- release candidate manifest
```

Environment deployment:

```text
- consume release artifact
- deploy
- verify
- produce deployment manifest
```

## M3. Reuse affected-product resolver

Do not create a new impact model. Reuse/extend:

```text
scripts/resolve-affected-products.mjs
```

Add lifecycle impact reasons:

```text
lifecycle-profile:<file>
toolchain-adapter:<file>
environment:<file>
deployment-target:<file>
artifact-schema:<file>
```

## M4. Acceptance criteria

- Changed platform lifecycle package affects all lifecycle-enabled products.
- Changed product-only files affect only that product.
- Changed product shell affects products with UI.
- Changed deployment target affects deployable products.
- Docs-only changes do not trigger product builds unless docs conformance is changed.

---

# 18. Workstream N — Digital Marketing Pilot

Digital Marketing should be the first proof of the platform because it has both backend and web surfaces and currently shows the exact problem: Kernel knows the product but does not truly orchestrate the lifecycle.

## N1. Current Digital Marketing facts

Current files:

```text
products/digital-marketing/dm-domain-packs/domain-pack.json
products/digital-marketing/dm-domain-packs/build.gradle.kts
products/digital-marketing/dm-api/build.gradle.kts
products/digital-marketing/ui/package.json
products/digital-marketing/gradle/dmos-quality-gates.gradle.kts
```

Current surfaces from registry:

```text
backend-api -> products/digital-marketing/dm-api
web         -> products/digital-marketing/ui
```

Current UI package:

```text
dev   -> vite
build -> route-contract test + tsc --noEmit + vite build + bundle budget
test  -> vitest unit
```

Current backend API has:

```text
build/check/test via Gradle
runDmosApiServer task for local server
OpenAPI generation/validation
Jacoco gates
DMOS quality gates
```

## N2. Add product lifecycle config

### Add

```text
products/digital-marketing/kernel-product.yaml
products/digital-marketing/lifecycle.local.yaml
products/digital-marketing/runtime/runtime-profile.yaml
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/health-checks.json
products/digital-marketing/conformance/lifecycle-fixtures.json
products/digital-marketing/conformance/artifact-fixtures.json
products/digital-marketing/conformance/deployment-fixtures.json
```

## N3. Digital Marketing `kernel-product.yaml`

```yaml
schemaVersion: 1.0.0
productId: digital-marketing
lifecycleProfile: standard-web-api-product

surfaces:
  backend-api:
    source: products/digital-marketing/dm-api
    runtime: java-service
    adapter: gradle-java-service
    gradleModule: ":products:digital-marketing:dm-api"
    devTask: runDmosApiServer
    buildTask: build
    testTask: test
    validateTask: check
    health:
      type: http
      path: /health
      portVariable: DMOS_API_PORT
      defaultPort: 8080

  web:
    source: products/digital-marketing/ui
    runtime: static-web
    adapter: pnpm-vite-react
    packagePath: products/digital-marketing/ui/package.json
    devScript: dev
    buildScript: build
    testScript: test
    health:
      type: http
      path: /
      portVariable: DMOS_WEB_PORT
      defaultPort: 5173

phases:
  dev:
    defaultSurfaces: [backend-api, web]
    mode: parallel
  validate:
    defaultSurfaces: [backend-api, web]
  test:
    defaultSurfaces: [backend-api, web]
  build:
    defaultSurfaces: [backend-api, web]
  package:
    defaultTargets: [backend-api-container, web-static-container]
  deploy:
    defaultEnvironment: local
```

## N4. Registry update

### Update

```text
config/canonical-product-registry.json
```

For `digital-marketing`, add:

```json
{
  "lifecycleProfile": "standard-web-api-product",
  "lifecycleConfigPath": "products/digital-marketing/kernel-product.yaml",
  "toolchain": {
    "profile": "java-api-plus-vite-web",
    "adapters": {
      "backend-api": "gradle-java-service",
      "web": "pnpm-vite-react",
      "package.backend-api": "docker-buildx",
      "package.web": "docker-static-web",
      "deploy.local": "compose-local"
    }
  },
  "artifacts": {
    "backend-api": {
      "type": "jvm-service",
      "packaging": "container"
    },
    "web": {
      "type": "static-web-bundle",
      "packaging": "static-web-container"
    }
  },
  "deployment": {
    "targets": ["local-compose"],
    "defaultEnvironment": "local",
    "healthChecks": ["http"],
    "rollback": {
      "strategy": "previous-artifact"
    }
  }
}
```

## N5. Expected Digital Marketing lifecycle behavior

### `kernel product dev digital-marketing`

```text
1. Validate registry.
2. Validate lifecycle config.
3. Start backend API using Gradle adapter and runDmosApiServer.
4. Start web using pnpm Vite adapter.
5. Print local URLs.
6. Validate dev health checks.
```

### `kernel product build digital-marketing`

```text
1. Validate registry.
2. Validate lifecycle profile.
3. Validate domain pack manifest.
4. Build/test/check backend API.
5. Build/test/check web UI.
6. Run route-contract checks.
7. Run bundle budget.
8. Run DMOS quality gates.
9. Emit lifecycle result.
10. Emit artifact manifest.
```

### `kernel product package digital-marketing`

```text
1. Consume build artifact manifest.
2. Package backend API.
3. Package web UI.
4. Produce package artifact manifest.
```

### `kernel product deploy digital-marketing --env local`

```text
1. Consume package artifact manifest.
2. Render local compose deployment.
3. Deploy local services.
4. Run health checks.
5. Emit deployment manifest.
```

## N6. Acceptance criteria

- Digital Marketing can be developed without manually invoking pnpm/Gradle.
- `kernel product build digital-marketing` builds both backend and web.
- `kernel product build digital-marketing --surface web` builds only web.
- `kernel product build digital-marketing --surface backend-api` builds only backend.
- `kernel product dev digital-marketing` starts both surfaces.
- Local deploy produces deployment manifest.
- Existing `pnpm product digital-marketing build` remains compatible but delegates to Kernel lifecycle.

---

# 19. Workstream O — Product Migration Order

Migrate in this order:

```text
1. digital-marketing
2. phr
3. flashit
4. finance
5. data-cloud
6. yappc
7. tutorputor
8. audio-video
9. dcmaar
10. security-gateway
11. demo/example products last
```

## Why this order

1. `digital-marketing`: clear standard web + Java API product.
2. `phr`: similar business product, likely strong compliance needs.
3. `flashit`: web + mobile + Node API complexity.
4. `finance`: backend-heavy domain suite; useful after lifecycle engine matures.
5. `data-cloud`: platform-provider; needs more careful boundary handling.
6. `yappc`: platform-provider and app-creator; should consume lifecycle once stable.
7. `tutorputor`: simulation/AI/content complexity.
8. Shared services and demos last.

## Migration requirements per product

Each product must add:

```text
kernel-product.yaml
lifecycle.local.yaml
runtime/runtime-profile.yaml
deploy/health-checks.json
conformance/lifecycle-fixtures.json
conformance/artifact-fixtures.json
conformance/deployment-fixtures.json
```

Each registry entry must add:

```text
lifecycleProfile
lifecycleConfigPath
toolchain
artifacts
deployment
```

Each product must pass:

```bash
kernel product plan <product> build
kernel product validate <product>
kernel product test <product>
kernel product build <product>
```

Deployable products must pass:

```bash
kernel product package <product>
kernel product deploy <product> --env local
kernel product verify <product> --env local
```

---

# 20. Workstream P — Security, Privacy, Observability Native Gates

## P1. Add lifecycle gates

### Add config

```text
config/security/product-lifecycle-security-gates.json
config/privacy/product-lifecycle-data-classification.json
config/observability/product-lifecycle-observability.json
config/policy/product-lifecycle-policy-vocabulary.json
```

## P2. Gate types

```text
security-preflight
secret-scan
license-policy
dependency-policy
data-classification-check
tenant-context-check
audit-event-check
observability-check
redaction-check
policy-action-resource-check
plugin-binding-check
approval-check
```

## P3. Lifecycle integration

Add security/privacy/observability gates to lifecycle profiles:

```json
{
  "profiles": {
    "standard-web-api-product": {
      "requiredGates": {
        "validate": [
          "security-preflight",
          "policy-action-resource-check",
          "plugin-binding-check"
        ],
        "deploy": [
          "secret-scan",
          "observability-check",
          "health-check",
          "audit-event-check"
        ]
      }
    }
  }
}
```

## P4. Acceptance criteria

- Products with sensitive data get stronger lifecycle gates.
- Production deploy requires observability and rollback gates.
- Plugin-consuming products must validate plugin bindings.
- Data classification must influence deployment gates.
- No product can deploy without health checks.

---

# 21. Workstream Q — Docs Cleanup and Canonicalization

## Q1. Add Kernel docs index

### Add/update

```text
docs/kernel/README.md
```

Canonical structure:

```text
docs/kernel/00-VISION.md
docs/kernel/01-ARCHITECTURE.md
docs/kernel/02-PRODUCT_LIFECYCLE.md
docs/kernel/03-TOOLCHAIN_ADAPTERS.md
docs/kernel/04-ARTIFACTS.md
docs/kernel/05-DEPLOYMENT.md
docs/kernel/06-PLUGIN_PLATFORM.md
docs/kernel/07-CONFORMANCE.md
docs/kernel/08-SECURITY_PRIVACY_OBSERVABILITY.md
docs/kernel/09-PRODUCT_DEVELOPER_GUIDE.md
docs/kernel/10-POWER_USER_EXTENSION_GUIDE.md
docs/kernel/11-MIGRATION_GUIDE.md
```

## Q2. Add docs checks

### Add

```text
scripts/check-kernel-doc-taxonomy.mjs
scripts/check-kernel-doc-crosslinks.mjs
scripts/check-kernel-doc-truth.mjs
```

## Q3. Acceptance criteria

- No duplicate lifecycle docs.
- No generated audit docs treated as canonical docs.
- New lifecycle docs are linked from Kernel README.
- Product developer guide has one canonical entry point.
- Power-user extension guide has one canonical entry point.

---

# 22. Implementation Phases

## Phase 0 — Contract and alignment

### Deliverables

```text
docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md
docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md
docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md
docs/kernel/PRODUCT_ENVIRONMENT_CONTRACT.md
docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md
docs/kernel/PRODUCT_RELEASE_PROMOTION_CONTRACT.md
```

### Acceptance

```text
- Contracts reviewed.
- Ownership boundaries clear.
- No product-specific logic moved into Kernel.
```

## Phase 1 — Schema/config foundation

### Deliverables

```text
config/canonical-product-registry-schema.json updated
config/product-lifecycle-profiles.json added
config/toolchain-adapter-registry.json added
config/environments/local.json added
config/deployment/deployment-targets.json added
```

### Acceptance

```text
- Schema validates.
- Digital Marketing registry can include lifecycle fields.
- Generated product-shape can include lifecycle metadata.
```

## Phase 2 — Lifecycle engine MVP

### Deliverables

```text
platform/typescript/kernel-lifecycle
platform/typescript/kernel-toolchains
scripts/kernel-product.mjs
scripts/run-product-lifecycle.mjs
```

### Acceptance

```bash
pnpm kernel product plan digital-marketing build
pnpm kernel product build digital-marketing --dry-run
```

## Phase 3 — Tool adapters MVP

### Deliverables

```text
GradleJavaServiceAdapter
PnpmViteReactAdapter
VitestAdapter
PlaywrightAdapter
```

### Acceptance

```bash
pnpm kernel product build digital-marketing --surface web
pnpm kernel product build digital-marketing --surface backend-api
pnpm kernel product build digital-marketing
```

## Phase 4 — Artifact system

### Deliverables

```text
platform/typescript/kernel-artifacts
config/schemas/product-artifact-manifest.schema.json
artifact manifest output
```

### Acceptance

```text
- Build emits artifact-manifest.json.
- Missing expected artifacts fail closed.
```

## Phase 5 — Digital Marketing pilot complete

### Deliverables

```text
products/digital-marketing/kernel-product.yaml
products/digital-marketing/lifecycle.local.yaml
products/digital-marketing/runtime/runtime-profile.yaml
products/digital-marketing/deploy/local.compose.yaml
```

### Acceptance

```bash
pnpm kernel product dev digital-marketing
pnpm kernel product validate digital-marketing
pnpm kernel product test digital-marketing
pnpm kernel product build digital-marketing
```

## Phase 6 — Package and local deploy

### Deliverables

```text
platform/typescript/kernel-deployment
DockerBuildxAdapter
ComposeDeploymentAdapter
deployment manifest output
```

### Acceptance

```bash
pnpm kernel product package digital-marketing
pnpm kernel product deploy digital-marketing --env local
pnpm kernel product verify digital-marketing --env local
```

## Phase 7 — Conformance and CI

### Deliverables

```text
check-product-lifecycle-contracts.mjs
check-toolchain-adapter-contracts.mjs
check-product-artifact-contracts.mjs
check-product-deployment-contracts.mjs
product lifecycle workflows
```

### Acceptance

```bash
pnpm check:kernel-platform-lifecycle
```

## Phase 8 — Product migration

Migrate products in order listed above.

## Phase 9 — Production deployment

Add:

```text
Kubernetes adapter
Helm adapter
Terraform adapter
promotion policies
approval gates
rollback gates
production observability gates
```

---

# 23. Immediate First Sprint Backlog

## Sprint 1 objective

Make Kernel able to plan product lifecycle and dry-run Digital Marketing build without exposing Gradle/pnpm as the developer-facing contract.

## Sprint 1 tasks

### Task 1 — Add lifecycle docs

Files:

```text
docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md
docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md
docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md
docs/kernel/PRODUCT_ENVIRONMENT_CONTRACT.md
docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md
```

Done when:

```text
- docs are added
- docs link from KERNEL_CONSUMPTION_GUIDE.md
- docs link from KERNEL_PRODUCT_BOUNDARY.md
```

### Task 2 — Add lifecycle profile config

Files:

```text
config/product-lifecycle-profiles.json
config/product-lifecycle-profiles-schema.json
```

Done when:

```text
- standard-web-api-product exists
- backend-only-java-service exists
- frontend-only-web-product exists
```

### Task 3 — Add toolchain adapter registry

Files:

```text
config/toolchain-adapter-registry.json
config/toolchain-adapter-registry-schema.json
```

Done when:

```text
- gradle-java-service exists
- pnpm-vite-react exists
- docker-buildx exists
- compose-local exists
```

### Task 4 — Extend registry schema

File:

```text
config/canonical-product-registry-schema.json
```

Done when:

```text
- lifecycleProfile supported
- lifecycleConfigPath supported
- toolchain supported
- artifacts supported
- deployment supported
```

### Task 5 — Add Digital Marketing lifecycle entry

File:

```text
config/canonical-product-registry.json
```

Done when:

```text
- digital-marketing declares standard-web-api-product
- digital-marketing points to products/digital-marketing/kernel-product.yaml
- digital-marketing declares backend-api and web adapters
```

### Task 6 — Add Digital Marketing lifecycle config

Files:

```text
products/digital-marketing/kernel-product.yaml
products/digital-marketing/lifecycle.local.yaml
```

Done when:

```text
- backend-api maps to :products:digital-marketing:dm-api
- backend-api dev maps to runDmosApiServer
- web maps to products/digital-marketing/ui/package.json
```

### Task 7 — Add lifecycle package skeleton

Files:

```text
platform/typescript/kernel-lifecycle/package.json
platform/typescript/kernel-lifecycle/src/index.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts
platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts
```

Done when:

```text
- package builds
- planner can load product lifecycle config
- planner can produce dry-run plan
```

### Task 8 — Add toolchain package skeleton

Files:

```text
platform/typescript/kernel-toolchains/package.json
platform/typescript/kernel-toolchains/src/index.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts
```

Done when:

```text
- package builds
- registry can load adapter definitions
- fake adapter can be used in tests
```

### Task 9 — Add CLI script

Files:

```text
scripts/kernel-product.mjs
scripts/run-product-lifecycle.mjs
```

Done when:

```bash
pnpm kernel product plan digital-marketing build
```

prints a lifecycle plan.

### Task 10 — Add tests

Files:

```text
platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecyclePlanner.test.ts
platform/typescript/kernel-toolchains/src/__tests__/ToolchainAdapterRegistry.test.ts
```

Done when:

```text
- tests pass
- no external tools invoked in unit tests
```

---

# 24. First Sprint Acceptance Checklist

```text
[ ] Product lifecycle docs added.
[ ] Lifecycle profiles config added.
[ ] Toolchain adapter registry added.
[ ] Registry schema extended.
[ ] Digital Marketing lifecycle fields added to registry.
[ ] Digital Marketing kernel-product.yaml added.
[ ] Lifecycle package skeleton builds.
[ ] Toolchain package skeleton builds.
[ ] CLI can print Digital Marketing build plan.
[ ] Unit tests pass.
[ ] No product-domain logic added to Kernel platform code.
[ ] Existing pnpm/Gradle scripts still work.
```

---

# 25. Definition of Done for the Full Initiative

Kernel can be called a real product platform when:

```text
[ ] Product developers use Kernel commands for dev/test/build/package/deploy.
[ ] Tool choice is hidden by default.
[ ] Power users can override tools through adapter contracts.
[ ] Product lifecycle phases are schema-backed.
[ ] Product registry declares lifecycle intent.
[ ] Lifecycle engine creates execution plans.
[ ] Toolchain adapters execute concrete tools.
[ ] Build emits artifact manifests.
[ ] Package consumes artifact manifests.
[ ] Deploy consumes package/release manifests.
[ ] Environments are modeled explicitly.
[ ] Promotion and rollback are modeled explicitly.
[ ] Security/privacy/observability gates are native.
[ ] Plugin lifecycle hooks are modeled explicitly.
[ ] Product shell exposes lifecycle/deployment status using shared components.
[ ] Scaffolder creates lifecycle-aware products.
[ ] CI is generated from lifecycle declarations.
[ ] Digital Marketing is fully migrated.
[ ] At least PHR and FlashIt are migrated after Digital Marketing.
[ ] Kernel purity and product boundary rules remain enforced.
```

---

# 26. Key Risks and Mitigations

## Risk 1 — Kernel becomes too product-specific

Mitigation:

```text
- Keep lifecycle profiles generic.
- Keep Digital Marketing-specific config under products/digital-marketing.
- Enforce Kernel purity checks.
- Do not add product names into Kernel source.
```

## Risk 2 — Tool abstraction becomes too generic and useless

Mitigation:

```text
- Start with concrete adapters: Gradle Java and pnpm Vite React.
- Use Digital Marketing as a real pilot.
- Add abstractions only when two products need the same behavior.
```

## Risk 3 — Registry becomes too large

Mitigation:

```text
- Keep registry as product index.
- Move detailed product lifecycle config to products/<id>/kernel-product.yaml.
- Keep generated files separate.
```

## Risk 4 — Duplicate lifecycle and CI logic

Mitigation:

```text
- Lifecycle engine is source of truth.
- CI invokes lifecycle engine.
- Generated scripts invoke lifecycle engine.
- Old run-product-task delegates to lifecycle engine.
```

## Risk 5 — Deployment becomes unsafe

Mitigation:

```text
- Start with local deploy only.
- Require artifact manifests.
- Require health checks.
- Require rollback plan before non-local deploy.
- Require approval gates for prod.
```

---

# 27. Final Implementation Principle

Do not build another wrapper around Gradle and pnpm.

Build a Kernel-owned lifecycle platform where:

```text
Kernel owns the product lifecycle.
Adapters own the tool execution.
Products own the business behavior.
```

That is the distinction that turns Kernel from a collection of helpful scripts into a real end-to-end product platform.
