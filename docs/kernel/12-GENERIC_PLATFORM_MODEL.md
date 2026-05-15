# Generic Platform Model

**Classification:** target-architecture

## Overview

Kernel uses a provider-based model to support diverse ProductUnit shapes and ecosystems. This document explains the ProductUnit abstraction, the provider model, the current Ghatana file-backed registry provider, and the target architecture for external providers.

## ProductUnit Definition

A ProductUnit is the fundamental abstraction in Kernel representing any deployable, governable artifact that can undergo lifecycle operations. ProductUnits are product-neutral and can represent:

- Monorepo products within the Ghatana ecosystem
- External repositories and standalone projects
- Backend services and APIs
- Web applications and portals
- Mobile applications (iOS, Android)
- SDKs and libraries
- Plugins and extensions
- Domain packs and feature bundles
- Data pipelines and ETL workflows
- Agent runtimes and AI systems

ProductUnits are defined by their surfaces (deployable components), lifecycle profile, governance requirements, and provider references for registry, source, artifact, deployment, and health operations.

## Provider Model

Kernel uses providers to abstract operations across different product shapes and ecosystems. Providers are interfaces that Kernel contracts define, with implementations supplied by:

- The Ghatana file-backed registry (current executable provider)
- External repository providers (target architecture)
- Cloud provider integrations (target architecture)
- Custom provider implementations (target architecture)

### Provider Types

- **RegistryProvider**: Reads product metadata and lifecycle configuration
- **SourceProvider**: Accesses source code, triggers builds, manages branches
- **ToolchainProvider**: Executes build tools (Gradle, pnpm, Docker, etc.)
- **ArtifactProvider**: Stores and retrieves build artifacts
- **DeploymentProvider**: Deploys to environments (Compose, Kubernetes, Helm, etc.)
- **EnvironmentProvider**: Manages environment configuration and secrets
- **SecretsProvider**: Securely accesses deployment secrets
- **TelemetryProvider**: Emits lifecycle events and metrics
- **ApprovalProvider**: Manages human approval workflows
- **HealthProvider**: Performs health checks and status reporting
- **GateProvider**: Executes governance gates and compliance checks

## Current Ghatana File Registry Provider

The current executable provider is the Ghatana file-backed registry located at `config/canonical-product-registry.json`. This provider:

- Reads product metadata from the canonical registry JSON file
- Converts registry entries into ProductUnit representations
- Preserves lifecycle configuration and surface definitions
- Validates enabled products have lifecycle configuration paths
- Provides read-only access to product metadata

**Important**: `config/canonical-product-registry.json` is provider data, not the whole Kernel model. Kernel contracts define the ProductUnit shape, and the Ghatana file registry provider is one implementation of the RegistryProvider interface.

## Future External Provider Examples

The target architecture supports external providers for diverse ecosystems:

### External Repository Provider
- Reads product metadata from external Git repositories
- Supports GitHub, GitLab, Bitbucket, and custom Git servers
- Uses branch-based or tag-based versioning
- Integrates with external CI/CD systems

### Cloud Provider Integration
- AWS: CodePipeline, EKS, S3 artifacts, CloudFormation
- GCP: Cloud Build, GKE, Cloud Storage, Deployment Manager
- Azure: Azure Pipelines, AKS, Blob Storage, ARM templates

### Custom Provider Implementations
- Organization-specific registry systems
- Legacy build and deployment systems
- Custom governance and approval workflows
- Domain-specific toolchain adapters

## Product Shape Capability Matrix

| ProductUnit Kind | Supported Surfaces | Example Products | Lifecycle Status |
|-----------------|-------------------|-----------------|-----------------|
| business-product | backend-api, web, worker, portal, mobile | Digital Marketing, Finance, FlashIt | Enabled (Digital Marketing), Planned (others) |
| platform-provider | backend-api, web, operator | YAPPC, Data Cloud | Planned |
| shared-service | backend-api, worker | Auth Gateway, Incident Service | Planned |
| demo-example | backend-api, web | Tutorial products | Planned |
| domain-pack | domain-pack | Domain-specific feature bundles | Planned |
| sdk | sdk | Client libraries, API SDKs | Planned |
| plugin | plugin | Kernel plugins | Planned |
| data-pipeline | data-pipeline | ETL workflows, data processing | Planned |
| agent-runtime | agent-runtime | AI agent runtimes | Planned |
| external-application | backend-api, web, mobile | External repositories | Target architecture |

## Boundary Rules

Kernel core must never import product code. Product-specific behavior arrives through:

1. **Contracts**: Public TypeScript contracts in `kernel-product-contracts`
2. **Provider Data**: Registry, source, artifact, deployment, health providers
3. **Plugin Bindings**: Plugin registry with lifecycle hooks and gate outputs
4. **Product Lifecycle Config**: `kernel-product.yaml` in product directories

### Dependency Direction

```
Kernel Core
  ↓ depends on
Kernel Product Contracts
  ↓ depends on
Provider Implementations
  ↓ depends on
Product-Specific Systems (external)
```

**Forbidden**: Kernel core importing from `products/*`, Kernel core depending on specific product implementations.

## Current-State vs Target-State Classification

| Capability | Current state | Target state |
|------------|---------------|--------------|
| ProductUnit contract | Existing partial; strict guards and public exports are hardened | Stable contract for all governable ProductUnits |
| Provider contracts | Existing partial | Multi-provider contract family with registry, source, artifact, deployment, health, telemetry, and secrets providers |
| GhatanaFileRegistryProvider | Existing partial file-backed provider | Strict file provider plus external/cloud provider implementations |
| External providers | Target architecture | Git, cloud, and organization registry providers |
| Provider-backed lifecycle execution | Partial | Provider-selected execution path with canonical lifecycle truth across products |

### Current State (Executable)
- Ghatana file-backed registry provider
- Digital Marketing lifecycle execution enabled
- Surface adapters for Gradle, pnpm, Docker, Compose
- Basic gate execution and health checks
- File-based lifecycle configuration

### Target State (Architecture)
- Multiple provider implementations (file, external, cloud)
- ProductUnit abstraction for all product shapes
- Plugin registry with lifecycle hooks
- Event-driven lifecycle truth
- Health snapshots and governance integration
- External repository support
- Cloud provider integrations

### Migration Path

1. Define ProductUnit contracts (in progress)
2. Implement provider contracts (in progress)
3. Wrap Ghatana file registry as provider (in progress)
4. Add plugin registry (pending)
5. Implement event contracts (pending)
6. Implement health snapshots (pending)
7. Add external provider support (future)
8. Enable additional products (future)

## Product Examples

### Digital Marketing (Current Executable)
- Kind: business-product
- Surfaces: backend-api, web
- Registry Provider: Ghatana file registry
- Lifecycle Status: Enabled
- Profile: standard-web-api-product

### Finance (Shape Validation Target)
- Kind: business-product
- Surfaces: backend-api, operator, sdk
- Registry Provider: Ghatana file registry
- Lifecycle Status: Planned
- Profile: backend-only-java-service

### FlashIt (Shape Validation Target)
- Kind: business-product
- Surfaces: web, mobile-ios, mobile-android, backend-api
- Registry Provider: Ghatana file registry
- Lifecycle Status: Planned
- Profile: mobile-plus-api-product

### YAPPC (Shape Validation Target)
- Kind: platform-provider
- Surfaces: backend-api, web, operator
- Registry Provider: Ghatana file registry
- Lifecycle Status: Planned
- Profile: platform-provider-product

### External Repository (Target Architecture)
- Kind: external-application
- Surfaces: backend-api, web
- Registry Provider: External Git provider
- Lifecycle Status: Target architecture
- Profile: standard-web-api-product
