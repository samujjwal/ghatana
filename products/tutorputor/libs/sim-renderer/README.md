# @tutorputor/sim-renderer

A comprehensive simulation rendering library for TutorPutor educational simulations. This library provides domain-specific entity renderers, animation utilities, and React hooks for building interactive simulation visualizations.

## Features

- **Domain-Specific Renderers**: Pre-built renderers for 5 educational domains
  - **CS_DISCRETE**: Nodes, edges, pointers for algorithm visualization
  - **PHYSICS**: Rigid bodies, springs, vectors, particles
  - **CHEMISTRY**: Atoms, bonds, molecules, reaction arrows
  - **BIOLOGY**: Cells, organelles, enzymes, genes
  - **MEDICINE**: PK compartments, doses, infection agents

- **Primitive Drawing Functions**: Low-level canvas drawing utilities
- **Animation System**: Easing functions and animation hooks
- **React Hooks**: Ready-to-use hooks for canvas rendering
- **Type Safety**: Full TypeScript support with type guards

## Installation

```bash
pnpm add @tutorputor/sim-renderer
```

## Usage

### Basic Canvas Rendering

```tsx
import { useRef } from 'react';
import type { SimEntity } from '@tutorputor/contracts/v1/simulation';
import {
  useRendererRegistry,
  useRenderContext,
  useCanvasRendering,
} from '@tutorputor/sim-renderer';

function SimulationCanvas({ entities }: { entities: SimEntity[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  
  const registry = useRendererRegistry();
  
  const context = useRenderContext({
    canvasRef,
    entities,
    width: 800,
    height: 600,
    zoom: 1,
    panOffset: { x: 0, y: 0 },
  });

  useCanvasRendering({
    canvasRef,
    registry,
    context,
    entities,
    showGrid: true,
    backgroundColor: '#f8fafc',
  });

  return (
    <canvas 
      ref={canvasRef} 
      width={800} 
      height={600}
      style={{ border: '1px solid #e2e8f0' }}
    />
  );
}
```

### Custom Renderer

```tsx
import type { EntityRenderer } from '@tutorputor/sim-renderer';
import { drawCircle, drawText } from '@tutorputor/sim-renderer';

const customRenderer: EntityRenderer = {
  entityTypes: ['myCustomEntity'],
  domain: 'CS_DISCRETE',
  
  render(entity, context, isHovered, isSelected) {
    const { worldToScreen, theme, zoom } = context;
    const screen = worldToScreen(entity.x, entity.y);
    
    drawCircle(
      context.ctx,
      screen.x,
      screen.y,
      30 * zoom,
      { color: theme.primary, opacity: 0.8 },
      { color: theme.border, width: 2 }
    );
    
    if (entity.label) {
      drawText(context.ctx, entity.label, screen.x, screen.y, {
        color: theme.foreground,
        fontSize: 12 * zoom,
      });
    }
  },
  
  hitTest(entity, worldX, worldY) {
    const dx = worldX - entity.x;
    const dy = worldY - entity.y;
    return dx * dx + dy * dy <= 30 * 30;
  },
  
  getBounds(entity) {
    return {
      x: entity.x - 30,
      y: entity.y - 30,
      width: 60,
      height: 60,
    };
  },
};
```

### Animation

```tsx
import { useAnimation } from '@tutorputor/sim-renderer';

function AnimatedEntity({ entityId }) {
  const { animate, stopAnimation, getAnimatedValue } = useAnimation({
    onUpdate: (values) => {
      // Re-render with new values
    },
  });

  const handleClick = () => {
    animate({
      entityId,
      property: 'x',
      fromValue: 0,
      toValue: 100,
      duration: 500,
      easing: 'easeOutCubic',
      delay: 0,
      onComplete: () => console.log('Animation complete'),
    });
  };

  return <button onClick={handleClick}>Animate</button>;
}
```

## API Reference

### Types

- `RenderContext`: Canvas rendering context with utilities
- `RenderTheme`: Color and style theme configuration
- `EntityRenderer<T>`: Interface for entity renderers
- `RendererRegistry`: Registry for managing renderers
- `EntityAnimation`: Animation configuration
- `LineStyle`, `FillStyle`, `TextStyle`: Style configurations

### Hooks

- `useRendererRegistry()`: Create a renderer registry
- `useRenderContext(options)`: Create a render context
- `useAnimation(options)`: Manage animations
- `useHitTest(options)`: Hit testing utilities
- `useCanvasRendering(options)`: Auto-render entities to canvas

### Primitives

- `drawRect`, `drawCircle`, `drawPolygon`, `drawDiamond`
- `drawLine`, `drawArrow`, `drawBezierCurve`
- `drawText`, `measureText`
- `drawSpring`, `drawVector`, `drawBond`
- `applyGlow`, `clearGlow`

### Easing

- `applyEasing(progress, easing)`: Apply easing function
- `lerp(from, to, t)`: Linear interpolation
- `lerpColor(from, to, t)`: Color interpolation
- `clamp(value, min, max)`: Clamp value

## Domains & Entity Types

### CS_DISCRETE
- `node`: Algorithm nodes with values
- `edge`: Connections between nodes
- `pointer`: Index pointers (i, j, left, right)

### PHYSICS
- `rigidBody`: Physical bodies with mass
- `spring`: Spring connections
- `vector`: Force/velocity vectors
- `particle`: Particles with lifetime

### CHEMISTRY
- `atom`: Chemical elements
- `bond`: Chemical bonds (single, double, triple)
- `molecule`: Molecule containers
- `reactionArrow`: Reaction arrows
- `energyProfile`: Energy diagrams

### BIOLOGY
- `cell`: Biological cells
- `organelle`: Cell organelles
- `compartment`: Reaction compartments
- `enzyme`: Enzymes with substrates
- `signal`: Signaling molecules
- `gene`: Genes with expression

### MEDICINE
- `pkCompartment`: PK compartments
- `dose`: Drug doses
- `infectionAgent`: Pathogens

## License

MIT
