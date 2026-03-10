/**
 * Custom Pendulum Kernel Example
 * 
 * This example demonstrates how to create a custom physics kernel
 * for simulating a simple pendulum using the SDK.
 *
 * @doc.type example
 * @doc.purpose Demonstrate custom kernel creation with SDK
 * @doc.layer product
 * @doc.pattern Example
 */

import {
  createKernel,
  defineKernelPlugin,
  pluginRegistry,
} from '@ghatana/tutorputor-sim-sdk';
import type { SimEntityBase } from '@ghatana/tutorputor-contracts/v1/simulation';

// ============================================
// 1. Define the custom state interface
// ============================================

interface PendulumState {
  /** Current angle from vertical (radians) */
  angle: number;
  /** Angular velocity (rad/s) */
  angularVelocity: number;
  /** Gravitational acceleration (m/s²) */
  gravity: number;
  /** Pendulum length (meters) */
  length: number;
  /** Time elapsed (seconds) */
  time: number;
  /** Energy tracking */
  kineticEnergy: number;
  potentialEnergy: number;
}

// ============================================
// 2. Create the kernel using the builder
// ============================================

const pendulumKernel = createKernel<PendulumState>()
  // Set domain identifier
  .domain('physics')
  
  // Add description
  .describe('Simple pendulum simulation with energy conservation tracking')
  
  // Initialize state from manifest
  .initState((manifest) => {
    const config = manifest.domainConfig as { gravity?: { y: number }; length?: number } | undefined;
    return {
      angle: Math.PI / 4, // 45 degrees initial
      angularVelocity: 0,
      gravity: Math.abs(config?.gravity?.y ?? 9.81),
      length: config?.length ?? 1,
      time: 0,
      kineticEnergy: 0,
      potentialEnergy: 0,
    };
  })
  
  // Initialize entities with pivot and bob positions
  .initEntities((entities) => {
    return entities.map((entity) => {
      if (entity.entityType === 'pivot') {
        return {
          ...entity,
          position: { x: 0, y: 0 },
          visual: { ...entity.visual, shape: 'circle', size: 0.5, color: '#333' },
        };
      }
      if (entity.entityType === 'pendulum_bob') {
        // Initial position at 45 degrees
        const angle = Math.PI / 4;
        const length = 100; // Visual length
        return {
          ...entity,
          position: {
            x: Math.sin(angle) * length,
            y: Math.cos(angle) * length, // Positive y is down
          },
          visual: { ...entity.visual, shape: 'circle', size: 1.5, color: '#4A90D9' },
        };
      }
      if (entity.entityType === 'rod') {
        return {
          ...entity,
          visual: { ...entity.visual, shape: 'rectangle', size: 0.1, color: '#666' },
        };
      }
      return entity;
    });
  })
  
  // Step function - physics simulation
  .onStep((entities, state, _step) => {
    const dt = 0.016; // 16ms timestep
    
    // Simple pendulum equation: θ'' = -(g/L) * sin(θ)
    const angularAcceleration = -(state.gravity / state.length) * Math.sin(state.angle);
    
    // Update velocity and angle using Euler integration
    const newVelocity = state.angularVelocity + angularAcceleration * dt;
    const newAngle = state.angle + newVelocity * dt;
    const newTime = state.time + dt;
    
    // Calculate energies
    const mass = 1; // Assume unit mass
    const height = state.length * (1 - Math.cos(newAngle));
    const kineticEnergy = 0.5 * mass * Math.pow(newVelocity * state.length, 2);
    const potentialEnergy = mass * state.gravity * height;
    
    // Update bob position
    const visualLength = 100; // Visual scale
    const updatedEntities: SimEntityBase[] = entities.map((entity) => {
      if (entity.entityType === 'pendulum_bob') {
        return {
          ...entity,
          position: {
            x: Math.sin(newAngle) * visualLength,
            y: Math.cos(newAngle) * visualLength,
          },
        };
      }
      return entity;
    });
    
    return {
      entities: updatedEntities,
      state: {
        ...state,
        angle: newAngle,
        angularVelocity: newVelocity,
        time: newTime,
        kineticEnergy,
        potentialEnergy,
      },
    };
  })
  
  // Smooth interpolation between frames
  .interpolate((entities, t) => {
    // Linear interpolation for smooth animation
    return entities.map((entity) => ({
      ...entity,
      visual: {
        ...entity.visual,
        opacity: 1 - 0.1 * Math.sin(t * Math.PI), // Subtle pulse effect
      },
    }));
  })
  
  // Extract analytics from state
  .analytics((state) => ({
    currentAngle: (state.angle * 180 / Math.PI).toFixed(2) + '°',
    angularVelocity: state.angularVelocity.toFixed(4) + ' rad/s',
    kineticEnergy: state.kineticEnergy.toFixed(4) + ' J',
    potentialEnergy: state.potentialEnergy.toFixed(4) + ' J',
    totalEnergy: (state.kineticEnergy + state.potentialEnergy).toFixed(4) + ' J',
    simulationTime: state.time.toFixed(2) + ' s',
  }))
  
  // Lifecycle hooks for logging
  .onAfterInit(() => {
    console.log('Pendulum simulation initialized');
  })
  .onBeforeStep((stepIndex) => {
    if (stepIndex % 100 === 0) {
      console.log(`Step ${stepIndex}`);
    }
  })
  .onError((error) => {
    console.error('Pendulum simulation error:', error);
  })
  
  // Build the kernel factory
  .build();

// ============================================
// 3. Register as a plugin
// ============================================

const pendulumPlugin = defineKernelPlugin({
  metadata: {
    id: 'physics-pendulum',
    name: 'Simple Pendulum Kernel',
    version: '1.0.0',
    description: 'Physics simulation kernel for simple pendulum motion with energy tracking',
    author: 'Tutorputor Team',
    license: 'MIT',
    tags: ['physics', 'pendulum', 'mechanics', 'oscillation'],
  },
  domain: 'physics',
  supportedTypes: ['simple_pendulum', 'pendulum', 'harmonic_motion'],
  isAsync: false,
  createKernel: pendulumKernel,
});

// Register with the global plugin registry
pluginRegistry.register(pendulumPlugin);

// ============================================
// 4. Example usage
// ============================================

export function createPendulumSimulation() {
  const manifest = {
    id: crypto.randomUUID(),
    version: '1.0',
    domain: 'physics' as const,
    title: 'Simple Pendulum',
    description: 'Demonstrates simple harmonic motion of a pendulum',
    entities: [
      {
        id: 'pivot',
        label: 'Pivot',
        entityType: 'pivot',
        visual: { color: '#333', size: 0.5, shape: 'circle' as const, opacity: 1 },
        position: { x: 0, y: 0 },
      },
      {
        id: 'rod',
        label: 'Rod',
        entityType: 'rod',
        visual: { color: '#666', size: 0.1, shape: 'rectangle' as const, opacity: 1 },
        position: { x: 0, y: 50 },
      },
      {
        id: 'bob',
        label: 'Bob',
        entityType: 'pendulum_bob',
        visual: { color: '#4A90D9', size: 1.5, shape: 'circle' as const, opacity: 1 },
        position: { x: 0, y: 100 },
      },
    ],
    steps: Array.from({ length: 300 }, (_, i) => ({
      id: `step-${i}`,
      stepNumber: i + 1,
      description: `Time: ${(i * 0.016).toFixed(2)}s`,
      algorithm: 'euler_integration',
      actions: [],
      duration: 16, // 16ms per frame = 60fps
    })),
    keyframes: [],
    domainConfig: {
      gravity: { x: 0, y: 9.81 },
      length: 1,
    },
  };

  return manifest;
}

// Export for use in tests or other modules
export { pendulumKernel, pendulumPlugin };
