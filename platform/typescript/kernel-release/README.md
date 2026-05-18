# @ghatana/kernel-release

Kernel release management for product promotion and rollback.

## Purpose

Manages the release lifecycle for Ghatana products: planning promotions between environments, validating release manifests, executing rollback plans, and gating release approval. Part of the kernel lifecycle chain that runs after deployment verification.

## Key Concepts

- **ProductRelease** — a record of a product release, linking artifact manifests, deployment manifests, and release notes
- **ProductReleaseManifest** — the authoritative release manifest written as truth output
- **ProductPromotionPlan** — describes the steps to promote from one environment to the next
- **ProductRollbackPlan** — structured rollback instructions for a given release
- **ProductApprovalGate** — a gate requiring explicit approval before release proceeds

## Usage

```ts
import {
  ProductRelease,
  ProductReleaseManifest,
  ProductPromotionPlan,
  ProductRollbackPlan,
} from "@ghatana/kernel-release";
```

## Directory Structure

```
src/
  ProductApprovalGate.ts      # Approval gate type and logic
  ProductPromotionPlan.ts     # Promotion plan type
  ProductRelease.ts           # Release domain type
  ProductReleaseManifest.ts   # Release manifest schema
  ProductRollbackPlan.ts      # Rollback plan type
  verifier/                   # Release verification rules
```

## Ownership

Platform Kernel Engineering. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
