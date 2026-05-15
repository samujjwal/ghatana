# Kernel Vision

**Classification:** target-architecture

## Overview

Kernel is a generic ProductUnit lifecycle execution and governance platform. Kernel orchestrates product development, build, packaging, deployment, conformance, and operations through a provider-based model that supports multiple product shapes and ecosystems.

## Generic Platform Model

Kernel ProductUnit can represent:
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

The current executable provider is the Ghatana file-backed registry. External provider support is target architecture until implemented.

## Current-State Discipline

- Digital Marketing is the current lifecycle execution proof target.
- Other products are shape-validation targets unless `lifecycleStatus=enabled` and executable checks prove lifecycle execution.
- ProductUnit and provider contracts exist, with provider-backed execution still maturing through the file-backed registry provider.

## Goals

1. **Single Source of Truth**: Product registration in the canonical registry drives all downstream artifacts and workflows.
2. **Tool Abstraction**: Product developers should not need to know whether a product uses Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, or Terraform.
3. **Lifecycle Orchestration**: Kernel owns the lifecycle orchestration from dev through validate, test, build, package, deploy, verify, promote, and rollback.
4. **Governance First**: All gates, approvals, security checks, privacy validations, and observability requirements are enforced by Kernel.
5. **Product Neutral**: Kernel code stays product-neutral and does not import from products. Product-specific behavior arrives through contracts, provider data, plugin bindings, or product lifecycle config.
6. **Provider-Based Architecture**: Kernel uses providers for registry, source, artifact, deployment, environment, secrets, telemetry, and health operations, enabling support for diverse product shapes and ecosystems.

## Developer Contract

The default developer-facing contract is:

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

Power users can still override toolchains through Kernel-governed adapter contracts, but they cannot bypass Kernel gates, artifacts, observability, security, privacy, deployment, rollback, or boundary rules.
