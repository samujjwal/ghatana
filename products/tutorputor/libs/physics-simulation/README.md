# @tutorputor/physics-simulation

> TutorPutor physics simulation library with Konva rendering, Jotai state management, Yjs collaboration, and Zod validation.

## Features

- 🎨 **Konva Rendering** - High-performance canvas rendering for physics entities
- ⚡ **Jotai State** - Reactive state management with undo/redo atoms
- ✅ **Zod Validation** - Schema validation for manifests and entities
- 🎯 **Drag & Drop** - React DnD integration for entity toolbox
- 💾 **Serialization** - Import/Export simulation manifests
- 🧩 **Composable** - Modular components for custom layouts
- 👥 **Collaboration** - Real-time multi-user editing with Yjs CRDT
- 🔌 **Adapters** - Pluggable rendering backend abstraction

## Installation

```bash
pnpm add @tutorputor/physics-simulation
```

## Quick Start

```tsx
import {
  useSimulation,
  useSimulationKeyboardShortcuts,
  SimulationCanvas,
  SimulationToolbar,
  EntityToolbox,
  PhysicsPropertyPanel,
  TOOLBOX_ITEMS,
} from '@ghatana/physics-simulation';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { Provider as JotaiProvider } from 'jotai';

function SimulationBuilder() {
  const sim = useSimulation();
  useSimulationKeyboardShortcuts();

  return (
    <JotaiProvider>
      <DndProvider backend={HTML5Backend}>
        <SimulationToolbar {...sim} />
        <div className="flex">
          <EntityToolbox items={TOOLBOX_ITEMS} />
          <SimulationCanvas
            entities={sim.entities}
            selectedEntityId={sim.selectedEntity?.id}
            isPreviewMode={sim.isPreviewMode}
            onSelectEntity={sim.selectEntity}
            onEntityMove={(id, x, y) => sim.updateEntity(id, { x, y })}
            onEntityDrop={sim.addEntity}
          />
          <PhysicsPropertyPanel
            selectedEntity={sim.selectedEntity}
            onUpdateEntity={sim.updateEntity}
            onDeleteEntity={sim.deleteEntity}
          />
        </div>
      </DndProvider>
    </JotaiProvider>
  );
}
```

## Entity Types

| Type | Icon | Description |
|------|------|-------------|
| BALL | ⚽ | Bouncy sphere affected by gravity |
| BOX | 📦 | Rectangular object that can slide and stack |
| PLATFORM | ▬ | Static horizontal surface |
| RAMP | ◢ | Inclined surface for rolling objects |
| PULLEY | ⚙️ | Rotating wheel for rope systems |
| SPRING | 〰️ | Elastic connector between objects |
| PENDULUM | ⚖️ | Swinging weight on fixed point |
| WALL | 🧱 | Static vertical barrier |
| LEVER | ↔️ | Pivoting bar for mechanical advantage |
| WHEEL | ⚙ | Rotating circle with axle |

## API Reference

### Hooks

#### `useSimulation()`
Main hook for simulation state and actions.

```tsx
const {
  // State
  entities,           // PhysicsEntity[]
  selectedEntity,     // PhysicsEntity | null
  physicsConfig,      // PhysicsConfig
  isPreviewMode,      // boolean
  canUndo,            // boolean
  canRedo,            // boolean
  historyStatus,      // { current: number, total: number }
  
  // Actions
  addEntity,          // (type, x, y) => void
  updateEntity,       // (id, changes) => void
  deleteEntity,       // (id) => void
  selectEntity,       // (id | null) => void
  clearAll,           // () => void
  undo,               // () => void
  redo,               // () => void
  setPreviewMode,     // (enabled) => void
  setPhysicsConfig,   // (config) => void
  
  // Import/Export
  exportManifest,     // (filename?) => void
  importManifest,     // (file) => Promise<Result>
} = useSimulation();
```

#### `useSimulationKeyboardShortcuts()`
Enables keyboard shortcuts for undo (Cmd/Ctrl+Z) and redo (Cmd/Ctrl+Y).

```tsx
useSimulationKeyboardShortcuts();
```

### Components

#### `<SimulationCanvas />`
Main canvas with Konva rendering and drag-drop support.

#### `<EntityToolbox />`
Grid of draggable entity items.

#### `<PhysicsPropertyPanel />`
Property editor for selected entity.

#### `<PhysicsConfigPanel />`
Global physics world settings.

#### `<SimulationToolbar />`
Header toolbar with undo/redo and import/export.

### State Atoms

Direct Jotai atoms for advanced use cases:

```tsx
import {
  simulationEntitiesAtom,
  simulationPhysicsConfigAtom,
  undoAtom,
  redoAtom,
  addEntityAtom,
  updateEntityAtom,
  deleteEntityAtom,
} from '@ghatana/physics-simulation';
```

### Serialization

```tsx
import {
  createManifest,
  downloadManifest,
  parseManifest,
  migrateManifest,
  readManifestFromFile,
} from '@ghatana/physics-simulation';

// Export
const manifest = createManifest(entities, physicsConfig);
downloadManifest(manifest, 'my-simulation.json');

// Import
const result = await readManifestFromFile(file);
if (result.success) {
  loadEntities(result.manifest);
}
```

## Types

```typescript
interface PhysicsEntity {
  id: string;
  type: EntityType;
  x: number;
  y: number;
  width?: number;
  height?: number;
  radius?: number;
  rotation?: number;
  appearance: EntityAppearance;
  physics: PhysicsProperties;
}

interface PhysicsConfig {
  gravity: number;
  friction: number;
  timeScale: number;
  collisionEnabled: boolean;
}

interface PhysicsProperties {
  mass: number;
  friction: number;
  restitution: number;
  isStatic: boolean;
}
```

## Migration from Custom Implementation

If migrating from a custom Konva implementation:

1. Replace `Entity` with `PhysicsEntity`
2. Move `properties` to `physics` field
3. Move `color` to `appearance.color`
4. Use `useSimulation()` hook instead of `useState`
5. Replace custom undo/redo with `useSimulationKeyboardShortcuts()`

## Collaboration

Enable real-time multi-user editing with Yjs:

```tsx
import {
  usePhysicsCollaboration,
  CollaborationCursors,
  CollaborationStatusBar,
} from '@ghatana/physics-simulation';

function CollaborativeSimulation({ roomId }) {
  const {
    users,
    currentUser,
    isConnected,
    syncStatus,
    updateCursor,
    updateSelection,
  } = usePhysicsCollaboration(roomId, userId, userName, {
    serverUrl: 'ws://localhost:1234',
    enablePersistence: true,
  });

  return (
    <div>
      <CollaborationStatusBar
        isConnected={isConnected}
        syncStatus={syncStatus}
        users={users}
        currentUser={currentUser}
      />
      <SimulationCanvas onMouseMove={(e) => updateCursor(e.x, e.y)} />
      <CollaborationCursors users={users} />
    </div>
  );
}
```

### Collaboration Features

- **Real-time sync** - Entities sync across all connected users
- **User cursors** - See where other users are pointing
- **User presence** - Avatar indicators for online collaborators
- **Offline support** - IndexedDB persistence for offline work
- **Selection awareness** - See what entities others are editing

## Rendering Adapters

The library provides a unified rendering adapter interface for different backends:

```tsx
import {
  IRenderingAdapter,
  KonvaRenderingAdapter,
  selectBestAdapter,
  KONVA_CAPABILITIES,
  REACT_FLOW_CAPABILITIES,
  WEBGL_CAPABILITIES,
} from '@ghatana/physics-simulation';

// Auto-select best adapter based on requirements
const backend = selectBestAdapter({
  needsPhysics: true,
  expectedElementCount: 1000,
});

// Create adapter instance
const adapter = new KonvaRenderingAdapter({
  onSelect: (id) => console.log('Selected:', id),
  onMove: (id, x, y) => console.log('Moved:', id, x, y),
});

await adapter.initialize(containerElement);
adapter.render(entities);
```

### Available Backends

| Backend | Use Case | Max Elements | Physics | 3D |
|---------|----------|--------------|---------|-----|
| `konva` | Physics simulations, games | 10,000 | ✅ | ❌ |
| `react-flow` | Node/edge diagrams | 5,000 | ❌ | ❌ |
| `webgl` | Large datasets, 3D | 100,000 | ✅ | ✅ |

## License

MIT © Ghatana
