# Canvas Document Guide

> **Package**: `@ghatana/canvas`
> **Status**: Production
> **Last Updated**: 2026-05

## Overview

The Canvas package provides the visual editing runtime for Ghatana Studio. It manages a `CanvasDocument` — a versioned, typed record of nodes and edges — and exposes a `HybridCanvasController` for atomic mutations.

---

## CanvasDocument Schema

```ts
import { createCanvasDocument } from "@ghatana/canvas";

const doc = createCanvasDocument("doc-uuid", "My Canvas");
// {
//   id: "doc-uuid",
//   name: "My Canvas",
//   schemaVersion: "1.0.0",
//   nodes: {},
//   edges: {},
// }
```

**Schema version**: `CANVAS_DOCUMENT_SCHEMA_VERSION = "1.0.0"`

`CanvasDocument` is Zod-validated. Import from canvas document schema to validate:

```ts
import { CanvasDocumentSchema } from "@ghatana/canvas";
const validated = CanvasDocumentSchema.parse(rawJson);
```

---

## Canvas Store

Use the **factory** — not the singleton — for testability and multi-instance scenarios:

```ts
import { createCanvasStore } from "@ghatana/canvas";

const store = createCanvasStore(); // Creates an isolated Jotai store
```

The `hybridCanvasStore` singleton is **deprecated**. All new code must use `createCanvasStore()`.

---

## HybridCanvasController

The controller is the only mutation surface for canvas state. All operations are atomic.

```ts
import { HybridCanvasController, createCanvasStore } from "@ghatana/canvas";

const store = createCanvasStore();
const controller = new HybridCanvasController(store, {
  idProvider: () => crypto.randomUUID(), // Required: no Math.random()
});

// Add a node
controller.addNode({ type: "component", label: "Button" });

// Move a node
controller.updateNodePosition(nodeId, { x: 100, y: 200 });

// Select nodes
controller.setSelection([nodeId]);

// Composite atomic operations
controller.duplicateSelected();  // atomic
controller.groupSelected();      // atomic
controller.ungroupSelected();    // atomic
```

### ID Provider Rule

Node and edge IDs **must** use `crypto.randomUUID()`. Never use `Math.random()`.

---

## Deterministic Layout

When importing a document into the canvas, use the grid layout helper from Studio:

```ts
const GRID_COLUMNS = 4;
const CELL_WIDTH = 220;
const CELL_HEIGHT = 120;
const MARGIN_X = 40;
const MARGIN_Y = 40;

function gridPosition(index: number) {
  const col = index % GRID_COLUMNS;
  const row = Math.floor(index / GRID_COLUMNS);
  return {
    x: MARGIN_X + col * (CELL_WIDTH + MARGIN_X),
    y: MARGIN_Y + row * (CELL_HEIGHT + MARGIN_Y),
  };
}
```

This replaces any random layout — canvas positions must be deterministic and reproducible.

---

## BuilderCanvasProjectionAdapter

Projects between `BuilderDocument` (UI Builder model) and canvas nodes/edges:

```ts
import {
  builderToCanvas,
  canvasToBuilder,
} from "@ghatana/ghatana-studio/adapters/BuilderCanvasProjectionAdapter";

// Project builder document → canvas representation
const { nodes, edges } = builderToCanvas(builderDoc);

// Merge canvas positions back into builder document
const updatedDoc = canvasToBuilder({ document: builderDoc, nodes });
```

---

## CI Gate

```bash
pnpm check:canvas-document-roundtrip
```
