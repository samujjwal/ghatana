# @ghatana/canvas-tools

Drawing and editing tool implementations for the Ghatana canvas.

## Usage

```ts
import { SelectTool, PanTool, ShapeTool, ConnectorTool } from '@ghatana/canvas-tools';
import { BaseTool } from '@ghatana/canvas-tools'; // extend for custom tools
```

## API Surface

- `SelectTool` — element selection and drag
- `PanTool` — viewport panning
- `ShapeTool` — shape drawing
- `TextTool` — text element creation
- `BrushTool` / `EraserTool` — freehand drawing
- `ZoomTool` — viewport zoom
- `ConnectorTool` — element connector
- `BaseTool` — base class for custom tools
