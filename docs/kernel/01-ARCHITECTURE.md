# Kernel Architecture

## Core Components

### 1. Canonical Product Registry

- **Location**: `config/canonical-product-registry.json`
- **Purpose**: Single source of truth for product registration
- **Schema**: `config/canonical-product-registry-schema.json`

### 2. Lifecycle Engine

- **Package**: `@ghatana/kernel-lifecycle` (platform/typescript/kernel-lifecycle)
- **Purpose**: Plans and executes product lifecycle phases
- **Phases**: dev, validate, test, build, package, deploy, verify, promote, rollback

### 3. Toolchain Adapters

- **Package**: `@ghatana/kernel-toolchains` (platform/typescript/kernel-toolchains)
- **Purpose**: Abstract toolchain-specific operations behind a common interface
- **Adapters**: GradleJavaServiceAdapter, PnpmViteReactAdapter, VitestAdapter, PlaywrightAdapter, DockerBuildxAdapter, ComposeLocalAdapter, XcodeIosAdapter, GradleAndroidAdapter, KubernetesDeploymentAdapter, HelmDeploymentAdapter, TerraformDeploymentAdapter

### 4. Artifact System

- **Package**: `@ghatana/kernel-artifacts` (platform/typescript/kernel-artifacts)
- **Purpose**: Manage artifact manifests and validation
- **Schema**: Zod-based validation for artifact contracts

### 5. Release Management

- **Package**: `@ghatana/kernel-release` (platform/typescript/kernel-release)
- **Purpose**: Handle release, promotion, and rollback operations
- **Components**: ProductReleaseManager, ProductPromotionPlanManager, ProductRollbackPlanManager, ProductApprovalGateManager

### 6. Plugin Platform

- **Registry**: `config/kernel-plugin-registry.json`
- **Purpose**: Enable platform and product plugins to hook into lifecycle events
- **Hooks**: onProductRegistered, onProductBootstrapped, onProductDevStarted, onProductValidated, onProductTested, onProductBuildStarted, onProductBuildCompleted, onProductPackaged, onProductDeployStarted, onProductDeployed, onProductVerified, onProductPromoted, onProductRolledBack, onProductRetired

## Data Flow

1. Product registration in canonical registry
2. Generator creates derived artifacts (product-shape.json, CI matrix, package scripts)
3. Lifecycle planner creates execution plans based on product lifecycle profile
4. Toolchain adapters execute plans using appropriate tools
5. Artifact system validates outputs
6. Release management handles promotion and rollback
7. Plugin platform executes registered lifecycle hooks

## Boundary Rules

- Kernel code must stay product-neutral
- Platform modules must not import from `products/**`
- Products consume Kernel through public package exports or declared bridge/adapters
- Registry entries distinguish business-product, platform-provider, shared-service, domain-pack, and demo/example
