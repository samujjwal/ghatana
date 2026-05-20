# @ghatana/artifact-compiler-ts

TypeScript/TSX artifact compiler and decompiler for the Ghatana artifact pipeline.

## Overview

This package provides the TypeScript/React side of the Ghatana artifact
compiler/decompiler pipeline. It converts between:

- **TSX/TS source** ↔ **LogicalArtifactModel** (via `@ghatana/artifact-contracts`)
- **LogicalArtifactModel** → **BuilderDocument** (for `@ghatana/ui-builder`)
- **LogicalArtifactModel** → **CanvasDocument** (for `@ghatana/canvas`)
- **LogicalArtifactModel** → **DS Generator config** (for `@ghatana/ds-generator`)

Fidelity scoring and residual island detection are built-in so Studio can
surface unrepresentable constructs for human review.

## Key Modules

| Module | Purpose |
|--------|---------|
| `decompileTsx()` | TSX/TS source → `LogicalArtifactModel` |
| `compileReact()` | `LogicalArtifactModel` → React/TSX source |
| `projectToBuilder()` | Model → `BuilderDocument` shape |
| `projectToCanvas()` | Model → `CanvasDocument` shape |
| `projectToDs()` | Model → DS Generator config |
| `fidelityGate()` | Classify a `FidelityReport` as clean / review-recommended / blocked |
| `aggregateFidelityReports()` | Aggregate per-node reports into one pipeline report |
| `scoreArtifactNode()` | Score an individual node's fidelity |
| `detectResidualIslands()` | Find constructs the pipeline cannot model |

## Usage

### Decompile TSX

```ts
import { decompileTsx } from "@ghatana/artifact-compiler-ts";

const result = decompileTsx({
  label: "My Workspace",
  modelId: crypto.randomUUID(),
  files: [
    { relativePath: "src/components/Button.tsx", content: "..." },
    { relativePath: "src/pages/Home.tsx", content: "..." },
  ],
  designSystemComponentNames: new Set(["Button", "Badge", "Card"]),
});

console.log(result.model.nodes);        // ArtifactNode[]
console.log(result.fidelityReport);     // FidelityReport
```

### Project to Canvas

```ts
import { projectToCanvas } from "@ghatana/artifact-compiler-ts";

const { document } = projectToCanvas(result.model, {
  layoutAlgorithm: "layered",
});
// Feed document.nodes and document.edges to @ghatana/canvas
```

### Detect Residual Islands

```ts
import { detectResidualIslands } from "@ghatana/artifact-compiler-ts";
import * as ts from "typescript";

const sourceFile = ts.createSourceFile("file.tsx", code, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const report = detectResidualIslands(model, {
  parsedFiles: [{ relativePath: "file.tsx", sourceFile }],
});

for (const island of report.islands) {
  console.log(island.kind, island.severity, island.description);
}
```

## Rules

- All TSX parsing uses the TypeScript compiler API — no regex.
- Fidelity scores are computed from explicit loss points, never guessed.
- Residual stubs are emitted with `// RESIDUAL:` markers for review.
- No `Math.random()` — all layout positions are deterministic.
- No `any` types — all function parameters and returns are explicitly typed.

## Dependencies

- `@ghatana/artifact-contracts` (workspace) — canonical contract types
- `typescript` — compiler API for AST parsing
- `zod` — runtime validation at boundaries
