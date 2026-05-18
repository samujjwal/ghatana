# @ghatana/kernel-deployment

Kernel deployment platform for product lifecycle.

## Purpose

Orchestrates and validates deployment operations during the product lifecycle. Manages deployment targets, deployment plans, deployment verification, and rollback coordination across Ghatana products.

## Key Concepts

- **DeploymentPlan** — structured description of where and how a product is deployed
- **DeploymentTarget** — a named environment (e.g., staging, production) with its configuration
- **DeploymentVerifier** — post-deploy verification ensuring the deployment is healthy
- **DeploymentDomain** — core domain types and rules for deployment lifecycle

## Usage

```ts
import { DeploymentPlan, DeploymentVerifier } from "@ghatana/kernel-deployment";
```

## Directory Structure

```
src/
  domain/     # Core deployment domain types
```

## Ownership

Platform Kernel Engineering. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
