# @ghatana/canvas-core

Canvas core types, base element classes, and coordinate utilities.

## Overview

This package is the foundational layer of the split `@ghatana/canvas` library.  
Import from here when you only need canvas **types** and low-level utilities — no React, no plugin system.

## Usage

```ts
import type { Bounds, CanvasDocument, ElementType } from '@ghatana/canvas-core';
import { Bound } from '@ghatana/canvas-core';
```

## API Surface

- Core canvas document and element types (`CanvasDocument`, `CanvasDocumentElement`, …)
- Geometry utilities (`Bound`)
- Plugin system data-only types (`CanvasElementData`, `GraphNodeData`, `GraphEdgeData`)

## Related packages

| Package | Purpose |
|---------|---------|
| `@ghatana/canvas-react` | React components (HybridCanvas, topology layer) |
| `@ghatana/canvas-plugins` | Plugin registry and plugin API |
| `@ghatana/canvas-chrome` | Canvas chrome components (panels, command palette) |
| `@ghatana/canvas-tools` | Drawing and editing tools |
| `@ghatana/canvas` | Umbrella re-export (backward-compat) |
