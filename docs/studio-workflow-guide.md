# Studio Workflow Guide

> **Package**: `@ghatana/ghatana-studio`
> **Status**: Production
> **Last Updated**: 2026-05

## Overview

Ghatana Studio is the unified product development shell. It integrates the canvas, UI builder, artifact compiler/decompiler, and design system generator into a single authoring workflow.

---

## Routes

| Path | Page | Purpose |
|------|------|---------|
| `/` | `HomePage` | Dashboard |
| `/builder` | `BuilderPage` | Visual component editor |
| `/design-system` | `DesignSystemPage` | Design system generator |
| `/canvas` | `CanvasPage` | Canvas visualization |
| `/artifacts` | `ArtifactsPage` | Artifact manifest + decompile jobs |
| `/import` | `ImportDecompilePage` | Import and decompile TSX/TS source |
| `/fidelity-report` | `FidelityReportPage` | Fidelity loss point visualization |
| `/preview` | `PreviewPage` | Sandboxed generated-code preview |

---

## Authoring Workflow

### 1. Import Existing Source (Decompile)

Navigate to `/import`. Upload `.ts` or `.tsx` files (max 1MB). The page:

1. Parses the file using `@ghatana/artifact-compiler-ts` `decompileTsx()`.
2. Runs `detectResidualIslands()`.
3. Shows a fidelity traffic light (🟢 ≥95%, 🟡 ≥75%, 🔴 <75%).
4. Lists residual islands that require human review.

Navigate to `/fidelity-report` (via "View Report") to see loss points.

### 2. Visual Editing (Builder)

Navigate to `/builder`. The `BuilderStudio` section renders:
- A `VisualCanvas` backed by `@ghatana/canvas` `HybridCanvasController`.
- `BuilderCanvasProjectionAdapter` syncs `BuilderDocument` ↔ canvas nodes.

All canvas mutations go through the controller — no direct canvas state writes.

### 3. Preview Generated Output

Navigate to `/preview` (via state: `{ source, mimeType, title }`). The `PreviewPage`:

- Renders source in a sandboxed `<iframe srcdoc>` with `sandbox="allow-scripts"`.
- Wraps HTML fragments in a document shell.
- Shows loading/ready/error status.

### 4. Design System Integration

Navigate to `/design-system`. The `DesignSystemPage`:

- Selects a brand preset.
- Generates CSS / JSON / Tailwind / React theme output.
- Uses `@ghatana/ds-generator` target emitters.

---

## Adapters

### `BuilderCanvasProjectionAdapter`

```ts
import {
  builderToCanvas,
  canvasToBuilder,
} from "../adapters/BuilderCanvasProjectionAdapter";

// Project builder doc → canvas nodes + edges
const { nodes, edges } = builderToCanvas(doc);

// Merge canvas positions back into builder doc
const updated = canvasToBuilder({ document: doc, nodes });
```

### `ArtifactStudioWorkflowAdapter`

```ts
import {
  createDecompileJobState,
  buildDecompileJobResult,
  fidelityTrafficLight,
  fidelitySummaryText,
} from "../adapters/ArtifactStudioWorkflowAdapter";

const jobState = createDecompileJobState("Button.tsx");
// ... run decompile ...
const result = buildDecompileJobResult(model, fidelityReport, residualReport);
const light = fidelityTrafficLight(result.fidelityScore); // 🟢 / 🟡 / 🔴
const text = fidelitySummaryText(result.fidelityScore);   // "High fidelity" etc.
```

---

## Decompile Jobs Panel (ArtifactsPage)

`ArtifactsPage` maintains a local `DecompileJobState[]` list:

- **Decompile Jobs Panel**: lists all jobs with fidelity traffic light and quick actions.
- **Residual Review Queue**: surfaces islands requiring human review.
- **Navigate to `/import`**: start a new decompile job.

---

## Navigation

Studio nav items are defined in `studioNavigation.ts`. The `"builder"` route is wired with:
- `StudioRouteId`: `"builder"`
- Ownership metadata: `ownerProduct: "yappc"`, `layer: "studio"`
- Translation key: `studio.navigation.builder`

---

## CI Gate

```bash
pnpm check:studio-authoring-workflows
```
