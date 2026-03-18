# @yappc/canvas

A high-performance, portable canvas library for building interactive visual applications with full AFFiNE feature parity, tailored for Ghatana's ecosystem.

## Features

- **🎨 Rich Element Types**: Shapes, connectors, brushes, text, and pipeline nodes
- **⚡ High Performance**: Stacking canvases with DPI-aware rendering
- **🎯 Tool System**: Select, shape, brush, text tools with modifier key support
- **🎨 Theme Support**: Light/dark themes with customizable color schemes
- **📊 Telemetry Integration**: Built-in analytics tracking
- **🔌 Provider System**: Optional providers for colors, flags, and telemetry
- **🌐 RTL Support**: Right-to-left text rendering
- **📝 Advanced Text**: Multiline wrapping, vertical alignment, padding
- **🔧 Pipeline Nodes**: Data-driven workflow visualization
- **🎭 Standalone**: Works without external dependencies

## Installation

```bash
pnpm add @yappc/canvas
```

## Quick Start

```typescript
import { YAPPCanvasRenderer } from '@yappc/canvas/core/canvas-renderer';
import { ShapeElement } from '@yappc/canvas/elements/shape';

// Create canvas
const container = document.getElementById('canvas-container');
const canvas = new YAPPCanvasRenderer(container, {
  width: 1200,
  height: 800,
  theme: 'light',
  enableStackingCanvas: true
});

// Add a shape
const shape = new ShapeElement({
  id: 'shape-1',
  xywh: JSON.stringify([100, 100, 200, 100]),
  index: '1',
  shapeType: 'rect',
  fillColor: '#10b981',
  strokeColor: '#065f46',
  text: 'Hello Canvas!',
  radius: 8
});

canvas.addElement(shape);
```

## Core Concepts

### Canvas Renderer

The main class for managing canvas rendering and interactions.

```typescript
const canvas = new YAPPCanvasRenderer(container, {
  width: 1200,
  height: 800,
  theme: 'light',
  enableStackingCanvas: true,  // Multi-layer rendering
  enableDomRenderer: false,     // Experimental DOM mode
  telemetry: telemetryProvider, // Optional analytics
  colorProvider: colorProvider, // Optional theming
  flagProvider: flagProvider    // Optional feature flags
});
```

### Elements

#### Shape Element

```typescript
import { ShapeElement } from '@yappc/canvas/elements/shape';

const shape = new ShapeElement({
  id: 'shape-1',
  xywh: JSON.stringify([x, y, width, height]),
  index: Date.now().toString(),
  shapeType: 'rect', // 'rect' | 'circle' | 'diamond' | 'triangle' | 'ellipse'
  fillColor: '#10b981',
  strokeColor: '#065f46',
  strokeWidth: 2,
  strokeStyle: 'solid', // 'solid' | 'dashed' | 'none'
  text: 'Text inside shape',
  textColor: '#111827',
  textVerticalAlign: 'middle', // 'top' | 'middle' | 'bottom'
  padding: [8, 12], // [vertical, horizontal]
  textWrap: true,
  radius: 8,
  shadow: {
    offsetX: 2,
    offsetY: 2,
    blur: 4,
    color: 'rgba(0,0,0,0.1)'
  }
});
```

#### Pipeline Node Element

```typescript
import { PipelineNodeElement } from '@yappc/canvas/elements/pipeline-node';

const node = new PipelineNodeElement({
  id: 'node-1',
  xywh: JSON.stringify([x, y, 200, 150]),
  index: Date.now().toString(),
  label: 'Transform Data',
  nodeType: 'transform',
  status: 'running', // 'idle' | 'running' | 'success' | 'error' | 'warning'
  inputs: [
    { id: 'in1', label: 'Input', type: 'input', position: 'left' }
  ],
  outputs: [
    { id: 'out1', label: 'Output', type: 'output', position: 'right' }
  ],
  metadata: { /* custom data */ },
  icon: '⚙️'
});

// Update status
node.setStatus('success');

// Get port position for connections
const portPos = node.getPortPosition('out1');
```

#### Connector Element

```typescript
import { ConnectorElement } from '@yappc/canvas/elements/connector';

const connector = new ConnectorElement({
  id: 'conn-1',
  xywh: JSON.stringify([0, 0, 100, 100]),
  index: Date.now().toString(),
  startPoint: { x: 100, y: 100 },
  endPoint: { x: 300, y: 200 },
  strokeColor: '#6366f1',
  strokeWidth: 2,
  connectorType: 'curved', // 'straight' | 'orthogonal' | 'curved'
  arrowStyle: 'arrow' // 'none' | 'arrow' | 'diamond'
});
```

### Tools

```typescript
// Set active tool
canvas.toolManager.setActiveTool('shape');

// Available tools: 'select', 'shape', 'brush', 'text'

// Shift-constrain for shapes (maintains aspect ratio)
// Hold Shift while dragging to create perfect squares/circles
```

### Providers

#### Telemetry Provider

```typescript
const telemetryProvider = {
  track: (event: string, properties?: Record<string, any>) => {
    // Send to PostHog, Mixpanel, etc.
    console.log('Event:', event, properties);
  }
};

// Events tracked:
// - canvas.loaded
// - canvas.element.created/updated/finalized/deleted
// - canvas.viewport.panned/zoomed
// - canvas.render.error
```

#### Color Provider

```typescript
const colorProvider = {
  getColorValue: (color?: string, fallback?: string) => {
    return color ?? fallback ?? '#000000';
  },
  getColorScheme: () => 'light' // or 'dark'
};
```

#### Flag Provider

```typescript
const flagProvider = {
  getFlag: (name: string, defaultValue?: boolean) => {
    // Check feature flags
    return featureFlags[name] ?? defaultValue ?? false;
  }
};
```

### Events

```typescript
// Listen to canvas events
canvas.on('elementAdd', (element) => {
  console.log('Element added:', element);
});

canvas.on('elementSelect', (element) => {
  console.log('Element selected:', element);
});

canvas.on('elementRemove', (element) => {
  console.log('Element removed:', element);
});

canvas.on('viewportChange', (viewport) => {
  console.log('Viewport changed:', viewport);
});
```

## Advanced Features

### Stacking Canvases

Enable multi-layer rendering for better performance with many elements:

```typescript
const canvas = new YAPPCanvasRenderer(container, {
  enableStackingCanvas: true
});
```

### Text Rendering

Advanced text features with padding, alignment, and RTL support:

```typescript
const shape = new ShapeElement({
  // ... other props
  text: 'Multi-line text\nwith line breaks',
  textVerticalAlign: 'top',
  padding: [12, 16],
  textWrap: true,
  textAlign: 'left'
});
```

### Theme Integration

```typescript
import { getTheme } from '@yappc/canvas/theme/defaults';

const theme = getTheme('dark');
console.log(theme.colors.shapeFillColor); // '#059669'
```

## DOM-Based UI Builder

Create reactive UIs with drag-and-drop:

```typescript
import { UIBuilderPage } from './ui-builder/UIBuilderPage';

// Provides:
// - Component palette with drag-drop
// - Property panel for editing
// - Telemetry integration
// - Real-time updates
```

## API Reference

### YAPPCanvasRenderer

**Constructor**
- `new YAPPCanvasRenderer(container: HTMLElement, options: CanvasOptions)`

**Methods**
- `addElement(element: CanvasElement): void`
- `removeElement(element: CanvasElement): void`
- `selectElement(element: CanvasElement): void`
- `clearSelection(): void`
- `render(): void`
- `destroy(): void`
- `on(event: string, callback: Function): void`
- `emit(event: string, ...args: any[]): void`

### CanvasOptions

```typescript
interface CanvasOptions {
  width: number;
  height: number;
  tool?: ToolOptions;
  theme?: 'light' | 'dark';
  colorProvider?: ColorProvider;
  flagProvider?: FlagProvider;
  telemetry?: TelemetryProvider;
  enableStackingCanvas?: boolean;
  enableDomRenderer?: boolean;
}
```

## Examples

See `apps/canvas-demo` for complete examples:

- Basic canvas with shapes
- Pipeline workflow visualization
- DOM-based UI builder
- Telemetry integration

## Development

```bash
# Install dependencies
pnpm install

# Build library
pnpm --filter @yappc/canvas build

# Run demo
pnpm --filter canvas-demo dev

# Type check
pnpm --filter @yappc/canvas typecheck
```

## Architecture

- **Core**: Canvas renderer, viewport, layer management
- **Elements**: Base element class and specialized elements
- **Tools**: Tool system with pointer event handling
- **Theme**: Color schemes and defaults
- **Types**: TypeScript definitions and interfaces

## Contributing

Follow ghatana coding guidelines:
- TypeScript strict mode
- JSDoc comments for public APIs
- No external DI dependencies
- Minimal imports
- Gold standard code quality

## License

MIT

## Credits

Built with inspiration from AFFiNE, tailored for Ghatana's ecosystem with additional features for data-driven workflows and UI building.
