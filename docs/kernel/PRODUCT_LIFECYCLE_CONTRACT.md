# Product Lifecycle Contract

This document defines the contract between Kernel and products for lifecycle operations.

## Lifecycle Phases

Products define lifecycle phases in their `kernel-product.yaml`:

```yaml
phases:
  dev:
    defaultSurfaces: [backend-api, web]
    mode: parallel
  validate:
    defaultSurfaces: [backend-api, web]
    mode: parallel
  test:
    defaultSurfaces: [backend-api, web]
    mode: parallel
  build:
    defaultSurfaces: [backend-api, web]
    mode: sequential
  package:
    defaultSurfaces: [backend-api, web]
    mode: sequential
    defaultTargets: [container, static-web]
  deploy:
    defaultEnvironment: local
```

## Phase Modes

- **parallel**: Execute on all surfaces concurrently
- **sequential**: Execute on surfaces in dependency order

## Surface Configuration

Each surface declares its adapter and task mappings:

```yaml
surfaces:
  backend-api:
    adapter: gradle-java-service
    gradleModule: :products:product-name:module
    devTask: runServer
    buildTask: build
    testTask: test
    validateTask: check
```

## Contract Requirements

- Products must declare all lifecycle phases they support
- Surface adapters must be registered in the toolchain adapter registry
- Task names must match the adapter's expected task identifiers
- Health check configuration is required for all deployable surfaces

## Lifecycle Commands

Kernel provides CLI commands for lifecycle operations:

```bash
pnpm kernel product plan <product> <phase>
pnpm kernel product validate <product>
pnpm kernel product test <product>
pnpm kernel product build <product> [--surface <surface>]
pnpm kernel product package <product>
pnpm kernel product deploy <product> --env <env>
```

## Dry Run

All lifecycle commands support `--dry-run` to print execution plans without running tools.
