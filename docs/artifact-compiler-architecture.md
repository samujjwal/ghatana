# Artifact Compiler / Decompiler Architecture

> **Status**: Production
> **Package**: `@ghatana/artifact-compiler-ts`
> **Contracts**: `@ghatana/artifact-contracts`
> **Last Updated**: 2026-05

## Overview

The artifact compiler/decompiler pipeline converts between **TypeScript/React source code** and a **`LogicalArtifactModel`** — a structured, typed, Zod-validated representation of the logical components, pages, hooks, utilities, and their relationships in a codebase.

```
Source (TSX/TS files)
        ↓  decompileTsx()
LogicalArtifactModel
        ↓  compileReact()
Generated React/TSX source
```

Every step is lossless-by-intent: all loss points are enumerated in a `FidelityReport`, never silently dropped.

---

## Packages

### `@ghatana/artifact-contracts`

Shared Zod-validated schemas and types used by both the compiler and the Studio.

| Module | Purpose |
|--------|---------|
| `src/model.ts` | `LogicalArtifactModel`, `ArtifactNode`, `ArtifactEdge`, `ArtifactKind` |
| `src/fidelity.ts` | `FidelityReport`, `LossPoint`, `ResidualIslandReport`, `computeFidelityReport()` |
| `src/source.ts` | `SourceRef`, `SourceFile`, `SourceSpan` |
| `src/provenance.ts` | `ProvenanceRecord`, `FileOwnershipMap` |
| `src/evidence.ts` | `CompileResult`, `DecompileResult`, `EvidencePack` |

**Key invariants:**
- `LogicalArtifactModel.schemaVersion` is always `"1.0.0"`.
- `ArtifactNode.nodes` is a `Record<string, ArtifactNode>` keyed by `relativePath`.
- Every node has `classificationConfidence: number` in `[0, 1]`.

### `@ghatana/artifact-compiler-ts`

The pipeline implementation.

| Module | Purpose |
|--------|---------|
| `src/decompile/tsx.ts` | `decompileTsx(input)` — source → model |
| `src/compile/react.ts` | `compileReact(model, options?)` — model → source |
| `src/fidelity/scorer.ts` | `fidelityGate()`, `aggregateFidelityReports()`, `FIDELITY_THRESHOLDS` |
| `src/residual/residual-islands.ts` | `detectResidualIslands(model)` |
| `src/projection/builder.ts` | `projectToBuilder(model)` |
| `src/projection/canvas.ts` | `projectToCanvas(model)` |
| `src/projection/ds.ts` | `projectToDs(model)` |

---

## Decompiler: `decompileTsx()`

### Input

```ts
interface DecompileTsxInput {
  readonly label: string;           // Human-readable model name
  readonly modelId: string;         // UUID
  readonly files: readonly DecompileSourceFile[];
  readonly designSystemComponentNames?: ReadonlySet<string>;
}

interface DecompileSourceFile {
  readonly relativePath: string;    // Used as node ID
  readonly content: string;         // Raw TypeScript/TSX source
}
```

### Output

```ts
interface DecompileTsxResult {
  readonly model: LogicalArtifactModel;
  readonly fidelityReport: FidelityReport;
  readonly perFileFidelity: ReadonlyMap<string, FidelityReport>;
}
```

### Node Classification

Each source file produces one `ArtifactNode`. The decompiler infers:

| Signal | Inferred Kind |
|--------|--------------|
| Path contains `components/`, file is `.tsx` with JSX return | `"component"` |
| Path contains `pages/` or file has `default export` | `"page"` |
| Path starts with `use` | `"hook"` |
| Path contains `layout` | `"layout"` |
| Path ends with `.test.ts` | `"test"` |
| All other TS files | `"utility"` |

`classificationConfidence` is `1.0` when no loss points are emitted, `0.8` when loss points are present.

### Loss Points

Loss points are emitted for:
- Dynamic `import()` expressions (severity: `info`)
- Missing or ambiguous export symbols (severity: `warning`)

---

## Compiler: `compileReact()`

### Input

A `LogicalArtifactModel`. Optionally:
```ts
interface CompileReactOptions {
  readonly confidenceThreshold?: number;    // Default: 0.7
  readonly designSystemPackage?: string;    // Default: "@ghatana/design-system"
  readonly emitDefaultExport?: boolean;     // Default: false
}
```

### Output

```ts
interface CompileReactResult {
  readonly emittedFiles: readonly EmittedFile[];
  readonly overallFidelity: FidelityReport;
}

interface EmittedFile {
  readonly relativePath: string;
  readonly content: string;         // Generated TypeScript/TSX
  readonly isResidualStub: boolean;
  readonly fidelity: FidelityReport;
}
```

### Residual Stubs

Nodes with `classificationConfidence < confidenceThreshold` are emitted as **residual stubs** with a `// RESIDUAL:` comment header. These must be reviewed by a human before use in production.

---

## Fidelity System

```ts
// Thresholds
const FIDELITY_THRESHOLDS = {
  CLEAN: 0.95,              // fidelityGate → "clean"
  REVIEW_RECOMMENDED: 0.75, // fidelityGate → "review-recommended"
  BLOCKED: 0.5,             // fidelityGate → "blocked"
};

// Usage
const verdict = fidelityGate(report); // "clean" | "review-recommended" | "blocked"
```

Score is computed as: `max(0, 1 - Σ(confidenceImpact))` across all loss points.

---

## Round-Trip Stability

The pipeline guarantees:

1. **Source → model → source → model (two-pass)**: the second model has ≥ as many nodes as the first.
2. **Schema version stability**: both passes emit `schemaVersion: "1.0.0"`.
3. **No silent loss**: every structural limitation is enumerated in a `FidelityReport`.
4. **Provenance preserved**: each node references its source file via `sourceRef.relativePath`.

See `src/__tests__/roundtrip.test.ts` for the full regression suite.

---

## Residual Island Detection

```ts
const report = detectResidualIslands(model);
// report.islands: Array<{ islandId, reason, nodeIds }>
// report.summary: { totalIslands, criticalIslands }
```

An "island" is a connected subgraph of nodes that share a residual classification pattern (dynamic imports, low confidence, unsupported kind chains).

---

## Studio Integration

The Studio consumes this pipeline via:

- `ImportDecompilePage.tsx` — file upload + decompile workflow
- `FidelityReportPage.tsx` — loss point visualization
- `ArtifactsPage.tsx` — decompile job queue + residual review queue
- `ArtifactStudioWorkflowAdapter.ts` — typed bridge between compiler results and Studio state

---

## CI Gates

```bash
pnpm check:artifact-compiler-contracts   # Contract surface stability
pnpm check:canvas-document-roundtrip     # Canvas schema + UI Builder
pnpm check:ds-generator-golden           # DS generator target emitters
pnpm check:studio-authoring-workflows    # Studio route/adapter integration
```
