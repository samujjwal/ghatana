# @ghatana/product-manifest-contracts

Kernel-owned product manifest schema and validation contracts.

## Purpose

Defines the canonical schemas and Zod validation contracts for all product manifests used in the Ghatana kernel lifecycle system: artifact manifests, build manifests, deployment manifests, and release manifests. This package is the single source of truth for what a valid product manifest looks like.

## Key Concepts

- **ArtifactManifestSchema** — Zod schema for product artifact manifests
- **BuildManifestSchema** — Zod schema for product build manifests
- **DeploymentManifestSchema** — Zod schema for product deployment manifests
- **ReleaseManifestSchema** — Zod schema for product release manifests

## Usage

```ts
import {
  ArtifactManifestSchema,
  BuildManifestSchema,
} from "@ghatana/product-manifest-contracts";

const manifest = ArtifactManifestSchema.parse(rawInput);
```

## Ownership

Platform Kernel Engineering. Schemas here are referenced by the config JSON Schema files in `config/`. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
