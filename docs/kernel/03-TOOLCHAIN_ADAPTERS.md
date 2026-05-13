# Toolchain Adapters

## Overview

Toolchain adapters abstract tool-specific operations behind a common interface. Product developers should not need to know whether a product uses Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, or Terraform.

## Adapter Interface

All adapters implement the `ToolchainAdapter` interface:

```typescript
interface ToolchainAdapter {
  id: string;
  supportedPhases: ProductLifecyclePhase[];
  supportedSurfaceTypes: ProductSurfaceType[];
  plan(context: ToolchainAdapterContext): Promise<ToolchainPlanStep[]>;
  execute(context: ToolchainAdapterContext): Promise<ToolchainExecutionResult>;
  validateOutputs(context: ToolchainAdapterContext): Promise<ToolchainOutputValidationResult>;
}
```

## Available Adapters

### Build Adapters

#### GradleJavaServiceAdapter
- **ID**: `gradle-java-service`
- **Phases**: dev, validate, test, build
- **Surfaces**: backend-api, worker, operator
- **Operations**: Gradle build, test, bootRun

#### PnpmViteReactAdapter
- **ID**: `pnpm-vite-react`
- **Phases**: dev, validate, test, build
- **Surfaces**: web
- **Operations**: pnpm dev, pnpm test, pnpm build

### Test Adapters

#### VitestAdapter
- **ID**: `vitest`
- **Phases**: test
- **Surfaces**: backend-api, web
- **Operations**: vitest run

#### PlaywrightAdapter
- **ID**: `playwright`
- **Phases**: test
- **Surfaces**: web
- **Operations**: playwright test

### Package Adapters

#### DockerBuildxAdapter
- **ID**: `docker-buildx`
- **Phases**: package
- **Surfaces**: backend-api, web
- **Operations**: docker buildx build

### Deployment Adapters

#### ComposeLocalAdapter
- **ID**: `compose-local`
- **Phases**: deploy, verify
- **Surfaces**: backend-api, web
- **Operations**: docker compose up, docker compose ps

#### KubernetesDeploymentAdapter
- **ID**: `kubernetes`
- **Phases**: deploy, verify, rollback
- **Surfaces**: backend-api, web, worker, operator
- **Operations**: kubectl apply, kubectl rollout status, kubectl rollout undo

#### HelmDeploymentAdapter
- **ID**: `helm`
- **Phases**: deploy, verify, rollback
- **Surfaces**: backend-api, web, worker, operator
- **Operations**: helm install/upgrade, helm test, helm rollback

#### TerraformDeploymentAdapter
- **ID**: `terraform`
- **Phases**: deploy, verify, rollback
- **Surfaces**: backend-api, web, worker, operator
- **Operations**: terraform apply, terraform plan, terraform destroy

### Mobile Adapters

#### XcodeIosAdapter
- **ID**: xcode-ios
- **Phases**: build, test
- **Surfaces**: mobile-ios
- **Operations**: xcodebuild

#### GradleAndroidAdapter
- **ID**: gradle-android
- **Phases**: build, test
- **Surfaces**: mobile-android
- **Operations**: ./gradlew assemble, ./gradlew test

## Adapter Registry

Adapters are registered in the toolchain adapter system. Adapters are implemented in `platform/typescript/kernel-toolchains/src/adapters/` and exported from the package index.

Example adapter configuration:
```json
{
  "id": "gradle-java-service",
  "supportedPhases": ["dev", "validate", "test", "build"],
  "supportedSurfaceTypes": ["backend-api", "worker", "operator"]
}
```
