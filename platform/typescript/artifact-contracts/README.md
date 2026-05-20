# @ghatana/artifact-contracts

Shared canonical contracts for the Ghatana artifact compiler/decompiler pipeline.

## Purpose

This package provides the **single source of truth** for all contracts shared between:
- The TS-side artifact compiler (`@ghatana/artifact-compiler-ts`)
- The Java/backend scanner service (`artifact-compiler-service`)
- Studio UX (`@ghatana/ghatana-studio`)
- Canvas projections (`@ghatana/canvas`)
- UI Builder projections (`@ghatana/ui-builder`)
- DS Generator projections (`@ghatana/ds-generator`)

## Key Contracts

### Source contracts (`./source`)
- `SourceSpan` — precise byte-offset location within a file
- `SourceFile` — file classification and metadata
- `SourceRef` — stable reference to a region in a versioned repository
- `SourceAcquisitionDescriptor` — how to acquire a source repository

### Model contracts (`.` or `./model`)
- `ArtifactNode` — a single logical artifact unit (component, page, hook, etc.)
- `ArtifactEdge` — dependency relationship between nodes
- `LogicalArtifactModel` — the full scanned model of a repository

### Provenance contracts (`./provenance`)
- `ProvenanceRecord` — derivation chain for a single artifact node
- `OwnershipRegion` — region of generated code owned by user vs generator
- `FileOwnershipMap` / `WorkspaceOwnershipMap` — full ownership map

### Fidelity contracts (`./fidelity`)
- `LossPoint` — a single aspect of source intent that was lost
- `FidelityReport` — overall round-trip fidelity score with loss points
- `ResidualIsland` — source construct that cannot be modelled
- `ResidualIslandReport` — all residuals in a pipeline run

### Evidence contracts (`./evidence`)
- `CompileResult` — output of a compile pipeline run
- `DecompileResult` — output of a decompile pipeline run
- `EvidencePack` — durable, inspectable artifact of a pipeline run

## Usage

```ts
import {
  type LogicalArtifactModel,
  createLogicalArtifactModel,
  computeFidelityReport,
} from "@ghatana/artifact-contracts";
```

## Rules

- Do NOT add product-specific logic here.
- Do NOT add pipeline implementation code here (use `@ghatana/artifact-compiler-ts`).
- All types must be Zod-backed for runtime validation at boundaries.
