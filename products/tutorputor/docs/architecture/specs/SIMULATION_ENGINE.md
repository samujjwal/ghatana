# Tutorputor Simulation Engine

> **Universal Simulation Protocol (USP)** - A manifest-driven simulation architecture for educational simulations.

## Overview

The Tutorputor Simulation Engine provides a comprehensive framework for creating, running, and refining educational simulations across multiple domains:

- **Discrete** - Algorithm visualizations (sorting, searching, graphs)
- **Physics** - Rigid body dynamics using Rapier WASM
- **Chemistry** - Reactions, titrations, molecular equilibrium
- **Biology** - Cellular division, gene expression, ecology
- **Economics** - System dynamics, supply/demand, investment
- **Medicine** - Pharmacokinetics, epidemiology (SIR/SEIR)

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend                                  │
│   ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│   │ SimulationCanvas │ │ SimulationPlayer │ │ SimulationStudio │         │
│   └───────────────┘ └───────────────┘ └───────────────┘         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway                                 │
│   /api/v1/simulations/manifests                                 │
│   /api/v1/simulations/sessions                                  │
│   /api/v1/simulations/refine                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   sim-author    │ │   sim-runtime   │ │     sim-nl      │
│   LLM-powered   │ │   Kernel mgmt   │ │   NL Interface  │
│   generation    │ │   State mgmt    │ │   Refinement    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ Discrete Kernel │ │ Physics Kernel  │ │ Chemistry Kernel│
├─────────────────┤ ├─────────────────┤ ├─────────────────┤
│ Biology Kernel  │ │ Economics Kernel│ │ Medicine Kernel │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

## Quick Start

### 1. Create a Simulation Manifest

```typescript
import type { SimulationManifest } from "@tutorputor/contracts/v1/simulation";

const manifest: SimulationManifest = {
  id: crypto.randomUUID(),
  version: "1.0",
  domain: "discrete",
  title: "Bubble Sort Visualization",
  description: "Step-by-step visualization of bubble sort algorithm",
  entities: [
    {
      id: "e1",
      label: "5",
      entityType: "array_element",
      value: 5,
      position: { x: 0, y: 0 },
    },
    {
      id: "e2",
      label: "3",
      entityType: "array_element",
      value: 3,
      position: { x: 60, y: 0 },
    },
    {
      id: "e3",
      label: "8",
      entityType: "array_element",
      value: 8,
      position: { x: 120, y: 0 },
    },
  ],
  steps: [
    {
      id: "s1",
      stepNumber: 1,
      description: "Compare elements at positions 0 and 1",
      algorithm: "bubble_sort",
      duration: 1000,
    },
    {
      id: "s2",
      stepNumber: 2,
      description: "Swap 5 and 3",
      algorithm: "bubble_sort",
      duration: 1000,
    },
    // ... more steps
  ],
};
```

### 2. Create a Runtime Session

```typescript
import { createRuntimeService } from "@tutorputor/sim-runtime";

const runtime = createRuntimeService();
const sessionId = await runtime.createSession(manifest);

// Step through the simulation
const keyframe1 = await runtime.stepForward(sessionId);
const keyframe2 = await runtime.stepForward(sessionId);

// Seek to a specific time
const seekedKeyframe = await runtime.seekTo(sessionId, 5000);

// Get current state
const state = await runtime.getState(sessionId);
```

### 3. Use AI to Generate Manifests

```typescript
import { SimAuthorService } from "@tutorputor/sim-author";

const author = new SimAuthorService(aiProvider);
const manifest = await author.generateManifest({
  prompt: "Show how quicksort works on an array of 6 numbers",
  domain: "discrete",
  difficulty: "intermediate",
});
```

### 4. Refine with Natural Language

```typescript
import { createNLService } from "@tutorputor/sim-nl";

const nlService = createNLService();
nlService.startConversation("session-1", manifest);

const result = await nlService.refine(
  "session-1",
  "make the first element blue"
);
if (result.success) {
  console.log("Updated manifest:", result.manifest);
}

const result2 = await nlService.refine("session-1", "slow down the animation");
```

## Packages

| Package                               | Description                            |
| ------------------------------------- | -------------------------------------- |
| `@tutorputor/contracts/v1/simulation` | TypeScript types and interfaces        |
| `@tutorputor/sim-author`              | AI-powered manifest generation         |
| `@tutorputor/sim-runtime`             | Runtime execution and state management |
| `@tutorputor/sim-nl`                  | Natural language refinement            |
| `@tutorputor/sim-sdk`                 | SDK for building custom kernels        |

## Building Custom Kernels

Use the SDK to create domain-specific kernels:

```typescript
import { createKernel } from "@tutorputor/sim-sdk";

interface PendulumState {
  angle: number;
  angularVelocity: number;
  gravity: number;
  length: number;
}

const pendulumKernel = createKernel<PendulumState>()
  .domain("physics")
  .describe("Simple pendulum physics simulation")
  .initState((manifest) => ({
    angle: Math.PI / 4, // Initial angle
    angularVelocity: 0,
    gravity: manifest.domainConfig?.gravity?.y ?? -9.81,
    length: 1,
  }))
  .initEntities((entities) =>
    entities.map((e) => ({
      ...e,
      position: { x: 0, y: -100 }, // Pivot at top
    }))
  )
  .onStep((entities, state, step) => {
    // Simple pendulum physics
    const angularAcceleration =
      -(state.gravity / state.length) * Math.sin(state.angle);
    const dt = 0.016; // 16ms

    const newVelocity = state.angularVelocity + angularAcceleration * dt;
    const newAngle = state.angle + newVelocity * dt;

    // Update bob position
    const updatedEntities = entities.map((e) => {
      if (e.entityType === "pendulum_bob") {
        return {
          ...e,
          position: {
            x: Math.sin(newAngle) * state.length * 100,
            y: -Math.cos(newAngle) * state.length * 100,
          },
        };
      }
      return e;
    });

    return {
      entities: updatedEntities,
      state: { ...state, angle: newAngle, angularVelocity: newVelocity },
    };
  })
  .analytics((state) => ({
    currentAngle: state.angle,
    maxVelocity: state.angularVelocity,
  }))
  .build();
```

Register the kernel:

```typescript
import { pluginRegistry, defineKernelPlugin } from "@tutorputor/sim-sdk";

pluginRegistry.register(
  defineKernelPlugin({
    metadata: {
      id: "pendulum-kernel",
      name: "Pendulum Simulation Kernel",
      version: "1.0.0",
      description: "Simple pendulum physics",
      author: "Your Name",
    },
    domain: "physics",
    supportedTypes: ["pendulum", "simple_harmonic_motion"],
    isAsync: false,
    createKernel: pendulumKernel,
  })
);
```

## Frontend Components

### SimulationCanvas

```tsx
import { SimulationCanvas } from "@tutorputor/web/components/simulation";

<SimulationCanvas
  keyframe={currentKeyframe}
  width={800}
  height={600}
  showGrid={true}
  onEntityClick={(entity) => console.log("Clicked:", entity)}
  enableControls={true}
/>;
```

### SimulationPlayer

```tsx
import { SimulationPlayer } from "@tutorputor/web/components/simulation";

<SimulationPlayer
  state={simulationState}
  onPlay={() => runtime.play(sessionId)}
  onPause={() => runtime.pause(sessionId)}
  onStepForward={() => runtime.stepForward(sessionId)}
  onStepBackward={() => runtime.stepBackward(sessionId)}
  onSeek={(time) => runtime.seekTo(sessionId, time)}
  onSpeedChange={(speed) => runtime.setPlaybackSpeed(sessionId, speed)}
  onReset={() => runtime.reset(sessionId)}
/>;
```

### SimulationStudio

```tsx
import { SimulationStudio } from "@tutorputor/web/components/simulation";

<SimulationStudio
  initialManifest={manifest}
  onSave={(updatedManifest) => saveManifest(updatedManifest)}
  onNLRefine={(input) => nlService.refine(sessionId, input)}
  onAIGenerate={(prompt, domain) => author.generateManifest({ prompt, domain })}
/>;
```

## API Endpoints

| Endpoint                                 | Method | Description            |
| ---------------------------------------- | ------ | ---------------------- |
| `/api/v1/simulations/manifests`          | POST   | Create new manifest    |
| `/api/v1/simulations/manifests/:id`      | GET    | Get manifest by ID     |
| `/api/v1/simulations/manifests/generate` | POST   | AI-generate manifest   |
| `/api/v1/simulations/sessions`           | POST   | Create runtime session |
| `/api/v1/simulations/sessions/:id/step`  | POST   | Step forward/backward  |
| `/api/v1/simulations/sessions/:id/seek`  | POST   | Seek to time           |
| `/api/v1/simulations/sessions/:id`       | DELETE | End session            |
| `/api/v1/simulations/refine`             | POST   | NL refinement          |
| `/api/v1/simulations/kernels`            | GET    | List available kernels |

## Testing

```typescript
import {
  testKernel,
  createMockManifest,
  createSortingEntities,
} from "@tutorputor/sim-sdk/testing";
import { DiscreteKernel } from "@tutorputor/sim-runtime";

describe("DiscreteKernel", () => {
  it("should sort array correctly", () => {
    const manifest = createMockManifest({
      domain: "discrete",
      entities: createSortingEntities([5, 3, 8, 1, 2]),
      steps: [
        {
          id: "s1",
          description: "Sort",
          algorithm: "bubble_sort",
          duration: 1000,
        },
      ],
    });

    testKernel(new DiscreteKernel(), manifest)
      .initialize()
      .runAll()
      .assertSorted()
      .assertEntityCount(5);
  });
});
```

## License

MIT © Ghatana
