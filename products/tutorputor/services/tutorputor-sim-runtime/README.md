# TutorPutor Simulation Runtime Service

## Overview

The Simulation Runtime Service is the core orchestration layer for executing simulations in the TutorPutor platform. It manages simulation sessions, coordinates kernel execution, handles playback controls, and enforces safety constraints.

## Features

- **Session Management**: Create, manage, and terminate simulation sessions
- **Kernel Coordination**: Interface with domain-specific simulation kernels
- **Playback Controls**: Step forward/backward, seek to specific steps
- **Safety Guardrails**: Enforce execution limits and parameter bounds
- **State Persistence**: Redis-backed session state storage
- **Deterministic Replay**: Support for reproducible simulations
- **Analytics Integration**: Capture execution history for telemetry

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   API Gateway                           │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│            SimulationRuntimeService                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Session Management                              │  │
│  │  - createSession()                               │  │
│  │  - stepForward() / stepBackward()                │  │
│  │  - seekToStep()                                  │  │
│  │  - terminateSession()                            │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  State Management (Redis)                        │  │
│  │  - Session state persistence                     │  │
│  │  - Kernel state serialization                    │  │
│  │  - Execution history tracking                    │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                 KernelRegistry                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Domain Kernels                                  │  │
│  │  - DiscreteKernel (CS_DISCRETE)                  │  │
│  │  - PhysicsKernel (PHYSICS)                       │  │
│  │  - ChemistryKernel (CHEMISTRY)                   │  │
│  │  - BiologyKernel (BIOLOGY)                       │  │
│  │  - MedicineKernel (MEDICINE)                     │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Installation

```bash
pnpm add @tutorputor/sim-runtime
```

## Usage

### Basic Example

```typescript
import { SimulationRuntimeService } from '@tutorputor/sim-runtime';
import type { SimulationManifest } from '@tutorputor/contracts/v1/simulation';

// Initialize service
const runtimeService = new SimulationRuntimeService();

// Create a session
const manifest: SimulationManifest = {
  id: 'sim-123',
  title: 'Binary Search',
  domain: 'CS_DISCRETE',
  canvas: { width: 800, height: 600 },
  playback: { defaultSpeed: 1.0 },
  initialEntities: [...],
  steps: [...],
  lifecycle: {
    status: 'published',
    createdBy: 'userId',
    publishedAt: Date.now()
  },
  safety: {
    parameterBounds: { enforced: true },
    executionLimits: {
      maxSteps: 1000,
      maxRuntimeMs: 60000
    }
  },
  // ... other required fields
};

const sessionId = await runtimeService.createSession(manifest);

// Execute steps
const keyframe1 = await runtimeService.stepForward(sessionId);
const keyframe2 = await runtimeService.stepForward(sessionId);

// Seek to specific step
const keyframe5 = await runtimeService.seekToStep(sessionId, 5);

// Step backward
const keyframe4 = await runtimeService.stepBackward(sessionId);

// Get current state
const state = await runtimeService.getSessionState(sessionId);

// Terminate session
await runtimeService.terminateSession(sessionId);
```

### With Safety Constraints

```typescript
const manifest: SimulationManifest = {
  // ... other fields
  safety: {
    parameterBounds: {
      enforced: true,
      maxIterations: 1000
    },
    executionLimits: {
      maxSteps: 500,      // Maximum 500 steps
      maxRuntimeMs: 30000 // Maximum 30 seconds
    }
  }
};

const sessionId = await runtimeService.createSession(manifest);

// The runtime will enforce these limits automatically
// Attempting to exceed maxSteps will return the last valid keyframe
```

### Deterministic Replay

```typescript
const manifest: SimulationManifest = {
  // ... other fields
  replay: {
    deterministic: true,
    seedStrategy: 'fixed' // or 'perSession'
  }
};

// With deterministic replay, the same sequence of steps
// will always produce the same results
```

## API Reference

### SimulationRuntimeService

#### `createSession(manifest: SimulationManifest): Promise<SimulationSessionId>`

Creates a new simulation session.

**Parameters:**
- `manifest` - The simulation manifest to execute

**Returns:**
- `SimulationSessionId` - Unique session identifier

**Throws:**
- Error if manifest validation fails
- Error if kernel initialization fails

---

#### `stepForward(sessionId: SimulationSessionId): Promise<SimKeyframe>`

Advances the simulation by one step.

**Parameters:**
- `sessionId` - The session to advance

**Returns:**
- `SimKeyframe` - The new keyframe state

**Throws:**
- Error if session not found
- Error if already at the end

---

#### `stepBackward(sessionId: SimulationSessionId): Promise<SimKeyframe>`

Rewinds the simulation by one step.

**Parameters:**
- `sessionId` - The session to rewind

**Returns:**
- `SimKeyframe` - The new keyframe state

**Throws:**
- Error if session not found
- Error if already at the beginning

---

#### `seekToStep(sessionId: SimulationSessionId, stepIndex: number): Promise<SimKeyframe>`

Jumps to a specific step in the simulation.

**Parameters:**
- `sessionId` - The session to seek
- `stepIndex` - Target step index (0-based)

**Returns:**
- `SimKeyframe` - The keyframe at the target step

**Throws:**
- Error if session not found
- Error if step index out of bounds

---

#### `terminateSession(sessionId: SimulationSessionId): Promise<void>`

Terminates a session and releases resources.

**Parameters:**
- `sessionId` - The session to terminate

**Returns:**
- `void`

---

#### `getSessionState(sessionId: SimulationSessionId): Promise<SessionState>`

Retrieves the current state of a session.

**Parameters:**
- `sessionId` - The session to query

**Returns:**
- `SessionState` - Current session state including:
  - `currentStepIndex` - Current step position
  - `totalSteps` - Total number of steps
  - `currentTime` - Current playback time (ms)
  - `totalDuration` - Total duration (ms)
  - `currentKeyframe` - Current keyframe state
  - `executionHistory` - History of executed steps

---

## Session State

Sessions are stored in Redis with the following structure:

```typescript
interface SessionState {
  sessionId: SimulationSessionId;
  simulationId: string;
  manifest: SimulationManifest;
  kernelState: string;
  currentStepIndex: number;
  totalSteps: number;
  currentTime: number;
  totalDuration: number;
  isPlaying: boolean;
  playbackSpeed: number;
  currentKeyframe: SimKeyframe;
  executionHistory: ExecutionHistoryEntry[];
  startedAt: Date;
  lastInteractionAt: Date;
}
```

### Session Timeout

Sessions automatically expire after 30 minutes of inactivity. The timeout is reset on each interaction (step, seek, etc.).

## Kernel Integration

The runtime service integrates with domain-specific kernels through the `KernelRegistry`:

```typescript
import { KernelRegistry } from '@tutorputor/sim-runtime';

// Get available kernels
const domains = KernelRegistry.listDomains();
// ['CS_DISCRETE', 'PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'MEDICINE']

// Check if a kernel is available
const hasPhysics = KernelRegistry.has('PHYSICS');

// Get a kernel for a manifest
const kernel = await KernelRegistry.getKernel(manifest);
```

### Supported Kernels

| Domain | Kernel | Description |
|--------|--------|-------------|
| `CS_DISCRETE` | DiscreteKernel | Discrete algorithms and data structures |
| `PHYSICS` | PhysicsKernel | Physics simulations (Matter.js) |
| `CHEMISTRY` | ChemistryKernel | Chemical reactions and molecules |
| `BIOLOGY` | BiologyKernel | Biological systems |
| `MEDICINE` | MedicineKernel | Pharmacokinetics (PK/PD models) |

## Safety & Performance

### Execution Limits

The runtime enforces safety constraints defined in the manifest:

```typescript
safety: {
  executionLimits: {
    maxSteps: 1000,      // Maximum steps per session
    maxRuntimeMs: 60000  // Maximum runtime in milliseconds
  }
}
```

### Session Quotas

- Maximum 10 concurrent sessions per user
- Enforced at the API gateway level

### Performance Considerations

- **Entity Count**: Keep initial entities under 100 for optimal performance
- **Step Count**: Limit steps to under 200 for responsive playback
- **Canvas Size**: Recommended maximum 2048x2048 pixels
- **Session Cleanup**: Always terminate sessions when done

## Error Handling

```typescript
try {
  const sessionId = await runtimeService.createSession(manifest);
  const keyframe = await runtimeService.stepForward(sessionId);
} catch (error) {
  if (error.message.includes('Session not found')) {
    // Handle missing session
  } else if (error.message.includes('Already at the end')) {
    // Handle end of simulation
  } else {
    // Handle other errors
  }
}
```

## Testing

Run tests:

```bash
pnpm test
```

Run E2E tests:

```bash
pnpm test:e2e
```

## Configuration

### Environment Variables

```bash
# Redis connection
REDIS_URL=redis://localhost:6379

# Session timeout (milliseconds)
SESSION_TIMEOUT_MS=1800000  # 30 minutes

# Performance limits
MAX_ENTITIES_PER_SIMULATION=100
MAX_STEPS_PER_SIMULATION=200
```

## Monitoring

The runtime service emits metrics for monitoring:

- `simulation.session.created` - Session creation events
- `simulation.step.executed` - Step execution events
- `simulation.session.terminated` - Session termination events
- `simulation.error` - Error events

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for development guidelines.

## License

MIT License - see [LICENSE](../../LICENSE)

## Support

- Documentation: https://docs.tutorputor.com/simulation-runtime
- Issues: https://github.com/tutorputor/simulation-engine/issues
- Email: support@tutorputor.com
