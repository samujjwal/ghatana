# @ghatana/canvas-react

React components for the Ghatana canvas — HybridCanvas, topology layer, and hybrid rendering.

## Usage

```tsx
import { HybridCanvas, useHybridCanvas } from '@ghatana/canvas-react';
import { BaseTopologyNode, useTopology } from '@ghatana/canvas-react';
```

## API Surface

- `HybridCanvas` — main hybrid renderer component
- `FreeformLayer`, `GraphLayer`, `LayerContainer` — layer sub-components
- `BaseTopologyNode`, `BaseTopologyEdge` — topology node/edge base
- `useHybridCanvas`, `useViewport`, `useSelection` — canvas hooks
- `HybridCanvasController` — programmatic canvas control
