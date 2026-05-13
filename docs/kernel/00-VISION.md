# Kernel Vision

## Overview

Kernel is the product lifecycle platform that orchestrates product development, build, packaging, deployment, conformance, and operations for the Ghatana ecosystem.

## Goals

1. **Single Source of Truth**: Product registration in the canonical registry drives all downstream artifacts and workflows.
2. **Tool Abstraction**: Product developers should not need to know whether a product uses Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, or Terraform.
3. **Lifecycle Orchestration**: Kernel owns the lifecycle orchestration from dev through validate, test, build, package, deploy, verify, promote, and rollback.
4. **Governance First**: All gates, approvals, security checks, privacy validations, and observability requirements are enforced by Kernel.
5. **Product Neutral**: Kernel code stays product-neutral and does not import from products.

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
