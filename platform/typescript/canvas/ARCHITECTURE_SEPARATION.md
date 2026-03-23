# Canvas Architecture Separation - Generic Core vs YAPPC

**Date:** January 23, 2026  
**Status:** ✅ Complete

---

## 🎯 Overview

The canvas implementation has been properly separated into two distinct layers:

1. **Generic Core Canvas** - Application-agnostic, reusable canvas system
2. **YAPPC Implementation** - YAPPC-specific configuration and behavior

This separation allows the canvas to be used by any application while providing a complete YAPPC implementation as a reference.

---

## 📦 Architecture Layers

### Layer 1: Generic Core Canvas (Reusable)

**Location:** `src/core/`

**Purpose:** Provides application-agnostic canvas functionality that can be configured for any multi-layer canvas application.

**Key Components:**

#### Configuration System
- `canvas-config.ts` - Generic configuration interfaces
  - `LayerConfig<TLayer>` - Generic layer definition
  - `PhaseConfig<TPhase>` - Generic phase definition
  - `RoleConfig<TRole>` - Generic role definition
  - `CanvasConfig<TLayer, TPhase, TRole>` - Complete configuration interface

#### Layer System
- `generic-layer-system.ts` - Application-agnostic layer detection
  - `GenericLayerDetector<TLayer>` - Configurable layer detector
  - Layer transition management
  - Zoom-to-layer mapping (configurable)

#### Chrome System
- `generic-chrome.tsx` - Generic UI components
  - Configuration-driven phase colors
  - Configuration-driven role display
  - Generic state atoms

#### Action System
- `action-registry.ts` - Generic action registry (already generic)
  - Works with any action context type
  - Configurable action categories

---

### Layer 2: YAPPC Implementation (Application-Specific)

**Location:** `src/yappc/`

**Purpose:** YAPPC-specific implementation using the generic canvas system.

**Key Components:**

#### YAPPC Configuration
- `yappc-config.ts` - YAPPC-specific types and configuration
  - `YAPPCLayer` - 5 semantic layers
  - `YAPPCPhase` - 7 lifecycle phases
  - `YAPPCRole` - 9 persona roles
  - `YAPPCActionContext` - YAPPC action context
  - `createYAPPCConfig()` - Configuration factory

#### YAPPC Actions
- `yappc-actions.ts` - YAPPC-specific action definitions
  - Re-exports existing actions with YAPPC types
  - Helper functions for YAPPC action retrieval

#### YAPPC Integration
- `yappc-canvas.ts` - Complete YAPPC setup
  - `initializeYAPPCCanvas()` - One-line initialization
  - Connects all YAPPC components

---

## 🔄 Usage Patterns

### Pattern 1: Using YAPPC Canvas (Recommended for YAPPC Apps)

```typescript
import { initializeYAPPCCanvas } from '@ghatana/canvas/yappc';
import { IntegratedCanvasChrome } from '@ghatana/canvas';

// Initialize YAPPC canvas once at app startup
initializeYAPPCCanvas();

function App() {
  return (
    <IntegratedCanvasChrome projectName="My YAPPC Project">
      <YourCanvasContent />
    </IntegratedCanvasChrome>
  );
}
```

### Pattern 2: Creating a Custom Canvas Application

```typescript
import { 
  setCanvasConfig, 
  CanvasConfig,
  GenericLayerDetector 
} from '@ghatana/canvas/core';

// Define your layers
type MyLayers = 'overview' | 'detail' | 'code';

// Define your phases
type MyPhases = 'plan' | 'build' | 'test';

// Define your roles
type MyRoles = 'designer' | 'developer';

// Create your configuration
const myConfig: CanvasConfig<MyLayers, MyPhases, MyRoles> = {
  appName: 'MyApp',
  layers: {
    overview: {
      name: 'overview',
      zoomRange: [0.1, 1.0],
      description: 'High-level overview',
      primaryFocus: 'System architecture',
    },
    detail: {
      name: 'detail',
      zoomRange: [1.0, 3.0],
      description: 'Detailed view',
      primaryFocus: 'Components',
    },
    code: {
      name: 'code',
      zoomRange: [3.0, Infinity],
      description: 'Code level',
      primaryFocus: 'Implementation',
    },
  },
  phases: {
    plan: {
      name: 'plan',
      displayName: 'Planning',
      color: { primary: '#1976d2', background: '#e3f2fd', text: '#0d47a1' },
      description: 'Planning phase',
    },
    build: {
      name: 'build',
      displayName: 'Building',
      color: { primary: '#388e3c', background: '#e8f5e9', text: '#1b5e20' },
      description: 'Building phase',
    },
    test: {
      name: 'test',
      displayName: 'Testing',
      color: { primary: '#f57c00', background: '#fff3e0', text: '#e65100' },
      description: 'Testing phase',
    },
  },
  roles: {
    designer: {
      name: 'designer',
      displayName: 'Designer',
      icon: '🎨',
      color: '#00897b',
      description: 'UI/UX Designer',
    },
    developer: {
      name: 'developer',
      displayName: 'Developer',
      icon: '💻',
      color: '#388e3c',
      description: 'Software Developer',
    },
  },
  layerActions: {
    overview: [],
    detail: [],
    code: [],
  },
  phaseActions: {
    plan: [],
    build: [],
    test: [],
  },
  roleActions: {
    designer: [],
    developer: [],
  },
  universalActions: [],
  defaultLayer: 'overview',
  defaultPhase: 'plan',
  defaultRoles: ['developer'],
  getLayerFromZoom: (zoom) => {
    if (zoom < 1.0) return 'overview';
    if (zoom < 3.0) return 'detail';
    return 'code';
  },
};

// Set configuration
setCanvasConfig(myConfig);

// Use the canvas
function App() {
  return (
    <IntegratedCanvasChrome projectName="My Custom Canvas">
      <YourCanvasContent />
    </IntegratedCanvasChrome>
  );
}
```

---

## 📁 File Organization

```
libs/ghatana-canvas/src/
├── core/                           # Generic Canvas Core
│   ├── canvas-config.ts            # Configuration system
│   ├── generic-layer-system.ts     # Generic layer detection
│   ├── generic-chrome.tsx          # Generic chrome components
│   ├── action-registry.ts          # Generic action registry
│   └── ... (other core files)
│
├── yappc/                          # YAPPC Implementation
│   ├── yappc-config.ts             # YAPPC configuration
│   ├── yappc-actions.ts            # YAPPC actions
│   ├── yappc-canvas.ts             # YAPPC initialization
│   └── index.ts                    # YAPPC exports
│
├── actions/                        # Action Definitions (used by YAPPC)
│   ├── layer-actions.ts            # Layer-specific actions
│   ├── phase-actions.ts            # Phase-specific actions
│   ├── role-actions.ts             # Role-specific actions
│   └── action-initializer.ts      # Action initialization
│
├── handlers/                       # Action Handlers (used by YAPPC)
│   └── canvas-handlers.ts          # Canvas operation handlers
│
├── components/                     # UI Components (generic)
│   ├── panels/                     # Panel components
│   ├── CommandPalette.tsx          # Command palette
│   ├── EnhancedContextMenu.tsx     # Context menu
│   └── IntegratedCanvasChrome.tsx  # Main chrome
│
├── hooks/                          # React Hooks (generic)
│   ├── useLayerDetection.ts        # Layer detection hook
│   └── useAvailableActions.ts      # Action resolution hook
│
└── index.ts                        # Main exports
```

---

## 🔌 Extension Points

### 1. Custom Layers

Define your own layer structure:

```typescript
type MyLayers = 'macro' | 'micro' | 'nano';

const myLayers: Record<MyLayers, LayerConfig<MyLayers>> = {
  macro: { name: 'macro', zoomRange: [0.1, 1.0], ... },
  micro: { name: 'micro', zoomRange: [1.0, 5.0], ... },
  nano: { name: 'nano', zoomRange: [5.0, Infinity], ... },
};
```

### 2. Custom Phases

Define your own workflow phases:

```typescript
type MyPhases = 'ideate' | 'prototype' | 'refine';

const myPhases: Record<MyPhases, PhaseConfig<MyPhases>> = {
  ideate: { name: 'ideate', displayName: 'Ideation', ... },
  prototype: { name: 'prototype', displayName: 'Prototyping', ... },
  refine: { name: 'refine', displayName: 'Refinement', ... },
};
```

### 3. Custom Roles

Define your own user roles:

```typescript
type MyRoles = 'admin' | 'editor' | 'viewer';

const myRoles: Record<MyRoles, RoleConfig<MyRoles>> = {
  admin: { name: 'admin', displayName: 'Administrator', icon: '👑', ... },
  editor: { name: 'editor', displayName: 'Editor', icon: '✏️', ... },
  viewer: { name: 'viewer', displayName: 'Viewer', icon: '👁️', ... },
};
```

### 4. Custom Actions

Define your own actions:

```typescript
const myActions: GenericActionDefinition<MyContext>[] = [
  {
    id: 'my-action',
    label: 'My Custom Action',
    icon: '⚡',
    category: 'layer',
    handler: async (context) => {
      // Your custom logic
    },
  },
];
```

---

## 🎨 Design Principles

### 1. Separation of Concerns
- **Core**: Generic, reusable, application-agnostic
- **YAPPC**: Specific, configured, application-aware

### 2. Type Safety
- Generic types throughout core (`TLayer`, `TPhase`, `TRole`)
- Concrete types in YAPPC (`YAPPCLayer`, `YAPPCPhase`, `YAPPCRole`)

### 3. Configuration-Driven
- All behavior driven by configuration
- No hard-coded YAPPC-specific logic in core
- Easy to create new canvas applications

### 4. Backward Compatibility
- Existing YAPPC code continues to work
- New generic system is additive
- Migration path is clear

---

## 🔄 Migration Guide

### For YAPPC Applications

**Before:**
```typescript
import { IntegratedCanvasChrome } from '@ghatana/canvas';

// Canvas was implicitly YAPPC
function App() {
  return <IntegratedCanvasChrome>...</IntegratedCanvasChrome>;
}
```

**After:**
```typescript
import { initializeYAPPCCanvas } from '@ghatana/canvas/yappc';
import { IntegratedCanvasChrome } from '@ghatana/canvas';

// Explicitly initialize YAPPC
initializeYAPPCCanvas();

function App() {
  return <IntegratedCanvasChrome>...</IntegratedCanvasChrome>;
}
```

### For New Applications

```typescript
import { setCanvasConfig } from '@ghatana/canvas/core';
import { IntegratedCanvasChrome } from '@ghatana/canvas';

// Create your own configuration
const myConfig = { ... };
setCanvasConfig(myConfig);

function App() {
  return <IntegratedCanvasChrome>...</IntegratedCanvasChrome>;
}
```

---

## ✅ Benefits

### 1. Reusability
- Core canvas can be used by any application
- No YAPPC-specific dependencies in core

### 2. Maintainability
- Clear separation of concerns
- Easy to update YAPPC without affecting core
- Easy to add new applications

### 3. Type Safety
- Generic types ensure type safety
- Compile-time checking for configurations

### 4. Flexibility
- Applications can customize layers, phases, roles
- Actions can be application-specific
- UI can be themed per application

### 5. Testability
- Core can be tested independently
- YAPPC can be tested independently
- Mock configurations for testing

---

## 📊 Summary

| Aspect | Generic Core | YAPPC Implementation |
|--------|-------------|---------------------|
| **Location** | `src/core/` | `src/yappc/` |
| **Purpose** | Reusable canvas system | YAPPC-specific config |
| **Types** | Generic (`TLayer`, `TPhase`) | Concrete (`YAPPCLayer`, `YAPPCPhase`) |
| **Dependencies** | None (self-contained) | Depends on core |
| **Customization** | Configuration-driven | Pre-configured for YAPPC |
| **Usage** | Any application | YAPPC applications |

---

## 🚀 Next Steps

1. **Update existing YAPPC apps** to use `initializeYAPPCCanvas()`
2. **Create new canvas applications** using the generic core
3. **Add more YAPPC-specific features** in the `yappc/` module
4. **Document custom canvas creation** with examples
5. **Create templates** for common canvas patterns

---

**Status:** ✅ Architecture separation complete and documented
