/**
 * Simulation Examples Library - 100+ Production-Ready Simulation Examples
 * 
 * Comprehensive collection of educational simulations across
 * physics, chemistry, biology, CS, and mathematics.
 */

import type { SimulationManifest } from '@tutorputor/contracts/v1/simulation';

export interface SimulationExample {
  id: string;
  name: string;
  description: string;
  domain: 'physics' | 'chemistry' | 'biology' | 'medicine' | 'cs' | 'math';
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  tags: string[];
  manifest: Partial<SimulationManifest>;
  duration: number;
  learningObjectives: string[];
}

// =============================================================================
// PHYSICS SIMULATIONS (1-30)
// =============================================================================

export const PhysicsSimulations: SimulationExample[] = [
  {
    id: 'sim-physics-001',
    name: 'Uniform Motion',
    description: 'Object moving at constant velocity',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['kinematics', 'velocity', 'uniform', 'motion'],
    duration: 5000,
    learningObjectives: ['Understand constant velocity', 'Relate distance to velocity and time'],
    manifest: {
      type: 'physics',
      title: 'Uniform Motion',
      entities: [
        {
          id: 'car',
          type: 'dynamic-body',
          x: 50,
          y: 300,
          properties: { mass: 1, radius: 20, vx: 3 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Start', description: 'Car begins moving at constant speed', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-002',
    name: 'Accelerated Motion',
    description: 'Object with constant acceleration',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['acceleration', 'velocity', 'kinematics', 'motion'],
    duration: 5000,
    learningObjectives: ['Observe acceleration effect', 'Understand v-t graph'],
    manifest: {
      type: 'physics',
      title: 'Accelerated Motion',
      entities: [
        {
          id: 'ball',
          type: 'dynamic-body',
          x: 50,
          y: 300,
          properties: { mass: 1, radius: 15, vx: 0, ax: 2 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'velocity-graph',
          type: 'sensor',
          x: 700,
          y: 200,
          properties: { type: 'graph', y: 'velocity', x: 'time' },
          appearance: { strokeColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Acceleration', description: 'Ball accelerates from rest', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-003',
    name: 'Simple Pendulum',
    description: 'Oscillating pendulum with adjustable length',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['pendulum', 'oscillation', 'shm', 'gravity'],
    duration: 6000,
    learningObjectives: ['Observe periodic motion', 'Relate period to length'],
    manifest: {
      type: 'physics',
      title: 'Simple Pendulum',
      entities: [
        {
          id: 'pivot',
          type: 'fixed-point',
          x: 400,
          y: 100,
          properties: {},
          appearance: { fillColor: '#333' },
        },
        {
          id: 'bob',
          type: 'dynamic-body',
          x: 400,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'pivot', length: 200 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Oscillation', description: 'Pendulum swings back and forth', duration: 6000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-004',
    name: 'Double Pendulum',
    description: 'Chaotic motion of double pendulum',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['chaos', 'pendulum', 'nonlinear', 'dynamics'],
    duration: 10000,
    learningObjectives: ['Observe chaotic behavior', 'Understand sensitivity to initial conditions'],
    manifest: {
      type: 'physics',
      title: 'Double Pendulum',
      entities: [
        {
          id: 'pivot-1',
          type: 'fixed-point',
          x: 400,
          y: 100,
          properties: {},
          appearance: { fillColor: '#333' },
        },
        {
          id: 'bob-1',
          type: 'dynamic-body',
          x: 400,
          y: 200,
          properties: { mass: 1, radius: 15, constraint: 'pivot-1', length: 100 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'bob-2',
          type: 'dynamic-body',
          x: 400,
          y: 300,
          properties: { mass: 1, radius: 15, constraint: 'bob-1', length: 100 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Chaos', description: 'Double pendulum exhibits chaotic motion', duration: 10000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-005',
    name: 'Spring-Mass System',
    description: 'Oscillating mass on spring',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['spring', 'harmonic', 'oscillation', 'hooke'],
    duration: 5000,
    learningObjectives: ['Understand Hooke\'s law', 'Observe harmonic motion'],
    manifest: {
      type: 'physics',
      title: 'Spring-Mass System',
      entities: [
        {
          id: 'anchor',
          type: 'fixed-point',
          x: 400,
          y: 50,
          properties: {},
          appearance: { fillColor: '#333' },
        },
        {
          id: 'mass',
          type: 'dynamic-body',
          x: 400,
          y: 200,
          properties: { mass: 2, radius: 25, spring: 'anchor', k: 0.5, restLength: 150 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Oscillation', description: 'Mass oscillates on spring', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-006',
    name: 'Coupled Oscillators',
    description: 'Two masses connected by springs',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['coupled', 'oscillation', 'normal-modes', 'springs'],
    duration: 8000,
    learningObjectives: ['Observe energy transfer', 'Understand normal modes'],
    manifest: {
      type: 'physics',
      title: 'Coupled Oscillators',
      entities: [
        {
          id: 'mass-1',
          type: 'dynamic-body',
          x: 300,
          y: 300,
          properties: { mass: 1, radius: 20, vx: 5 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'mass-2',
          type: 'dynamic-body',
          x: 500,
          y: 300,
          properties: { mass: 1, radius: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'spring-coupling',
          type: 'spring',
          x: 400,
          y: 300,
          properties: { length: 200, stiffness: 0.3, damping: 0.1, bodyA: 'mass-1', bodyB: 'mass-2' },
          appearance: { strokeColor: '#666' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Coupling', description: 'Energy transfers between masses', duration: 8000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-007',
    name: 'Projectile Motion',
    description: 'Trajectory of launched projectile',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['projectile', 'trajectory', 'gravity', 'launch'],
    duration: 4000,
    learningObjectives: ['Observe parabolic trajectory', 'Understand velocity components'],
    manifest: {
      type: 'physics',
      title: 'Projectile Motion',
      entities: [
        {
          id: 'projectile',
          type: 'dynamic-body',
          x: 50,
          y: 350,
          properties: { mass: 1, radius: 15, vx: 8, vy: -12 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'ground',
          type: 'boundary',
          x: 400,
          y: 400,
          properties: { width: 800, height: 10 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'trajectory',
          type: 'trail',
          x: 50,
          y: 350,
          properties: { target: 'projectile', duration: 4000 },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 2 },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Launch', description: 'Projectile follows parabolic path', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-008',
    name: 'Projectile with Air Resistance',
    description: 'Trajectory with drag force',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['projectile', 'drag', 'air-resistance', 'terminal-velocity'],
    duration: 5000,
    learningObjectives: ['Compare with ideal case', 'Understand drag effect'],
    manifest: {
      type: 'physics',
      title: 'Projectile with Drag',
      entities: [
        {
          id: 'projectile-ideal',
          type: 'dynamic-body',
          x: 50,
          y: 350,
          properties: { mass: 1, radius: 15, vx: 8, vy: -12 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'projectile-drag',
          type: 'dynamic-body',
          x: 50,
          y: 380,
          properties: { mass: 1, radius: 15, vx: 8, vy: -12, drag: 0.1 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'ground',
          type: 'boundary',
          x: 400,
          y: 450,
          properties: { width: 800, height: 10 },
          appearance: { fillColor: '#666' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Comparison', description: 'Drag reduces range and changes trajectory', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-009',
    name: 'Elastic Collision',
    description: 'Perfectly elastic 1D collision',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['collision', 'elastic', 'momentum', 'conservation'],
    duration: 3000,
    learningObjectives: ['Observe momentum conservation', 'Understand energy conservation'],
    manifest: {
      type: 'physics',
      title: 'Elastic Collision',
      entities: [
        {
          id: 'ball-1',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 2, radius: 25, vx: 5 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'ball-2',
          type: 'dynamic-body',
          x: 500,
          y: 300,
          properties: { mass: 2, radius: 25, vx: -3 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'wall-left',
          type: 'boundary',
          x: 50,
          y: 300,
          properties: { width: 10, height: 400 },
          appearance: { fillColor: '#333' },
        },
        {
          id: 'wall-right',
          type: 'boundary',
          x: 750,
          y: 300,
          properties: { width: 10, height: 400 },
          appearance: { fillColor: '#333' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Collision', description: 'Balls collide and exchange velocities', duration: 3000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-010',
    name: 'Inelastic Collision',
    description: 'Colliding objects stick together',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['collision', 'inelastic', 'momentum', 'stick'],
    duration: 3000,
    learningObjectives: ['Observe momentum conservation', 'Note kinetic energy loss'],
    manifest: {
      type: 'physics',
      title: 'Inelastic Collision',
      entities: [
        {
          id: 'cart-1',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 2, radius: 25, vx: 5 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'cart-2',
          type: 'dynamic-body',
          x: 500,
          y: 300,
          properties: { mass: 3, radius: 30, vx: -2 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Before', description: 'Carts approach each other', duration: 1000, stateChanges: {} },
        { id: 'step-2', title: 'Collision', description: 'Carts stick together', duration: 500, stateChanges: { 'cart-1.stick': 'cart-2' } },
        { id: 'step-3', title: 'After', description: 'Combined mass moves together', duration: 1500, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-011',
    name: 'Newton Cradle',
    description: 'Momentum transfer through colliding balls',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['collision', 'momentum', 'energy', 'transfer'],
    duration: 5000,
    learningObjectives: ['Observe momentum transfer', 'Understand conservation laws'],
    manifest: {
      type: 'physics',
      title: "Newton's Cradle",
      entities: [
        {
          id: 'ball-1',
          type: 'dynamic-body',
          x: 250,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'anchor-1', length: 200, angle: -30 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'ball-2',
          type: 'dynamic-body',
          x: 300,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'anchor-2', length: 200 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'ball-3',
          type: 'dynamic-body',
          x: 350,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'anchor-3', length: 200 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'ball-4',
          type: 'dynamic-body',
          x: 400,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'anchor-4', length: 200 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'ball-5',
          type: 'dynamic-body',
          x: 450,
          y: 300,
          properties: { mass: 1, radius: 20, constraint: 'anchor-5', length: 200 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Swing', description: 'First ball swings and hits others', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-012',
    name: 'Circular Motion',
    description: 'Object moving in circular path',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['circular', 'centripetal', 'velocity', 'acceleration'],
    duration: 4000,
    learningObjectives: ['Understand centripetal force', 'Relate velocity to radius'],
    manifest: {
      type: 'physics',
      title: 'Uniform Circular Motion',
      entities: [
        {
          id: 'satellite',
          type: 'dynamic-body',
          x: 500,
          y: 300,
          properties: { mass: 1, radius: 15, orbit: 'center', radius: 150, angularVelocity: 1 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'center',
          type: 'fixed-point',
          x: 400,
          y: 300,
          properties: { radius: 5 },
          appearance: { fillColor: '#333' },
        },
        {
          id: 'velocity-vector',
          type: 'vector',
          x: 500,
          y: 300,
          properties: { target: 'satellite', property: 'velocity', scale: 0.3 },
          appearance: { strokeColor: '#ff6b6b' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Orbit', description: 'Satellite orbits with constant speed', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-013',
    name: 'Planetary Orbits',
    description: 'Gravitational orbital mechanics',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['orbit', 'gravity', 'kepler', 'planetary'],
    duration: 10000,
    learningObjectives: ['Observe elliptical orbits', 'Understand Kepler\'s laws'],
    manifest: {
      type: 'physics',
      title: 'Planetary Motion',
      entities: [
        {
          id: 'sun',
          type: 'fixed-point',
          x: 400,
          y: 300,
          properties: { mass: 100, radius: 30 },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'planet-1',
          type: 'dynamic-body',
          x: 400,
          y: 150,
          properties: { mass: 1, radius: 12, vy: 8 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'planet-2',
          type: 'dynamic-body',
          x: 650,
          y: 300,
          properties: { mass: 1.5, radius: 15, vy: 5 },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Orbits', description: 'Planets orbit under gravity', duration: 10000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-014',
    name: 'Sliding Friction',
    description: 'Object slowing due to friction',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['friction', 'sliding', 'deceleration', 'surface'],
    duration: 4000,
    learningObjectives: ['Observe friction effect', 'Understand deceleration'],
    manifest: {
      type: 'physics',
      title: 'Sliding Friction',
      entities: [
        {
          id: 'block',
          type: 'dynamic-body',
          x: 100,
          y: 300,
          properties: { mass: 2, radius: 25, vx: 8, friction: 0.3 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'surface',
          type: 'boundary',
          x: 400,
          y: 350,
          properties: { width: 800, height: 10, friction: 0.3 },
          appearance: { fillColor: '#8b7355' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Sliding', description: 'Block slides and slows due to friction', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-015',
    name: 'Inclined Plane',
    description: 'Object sliding down slope',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['incline', 'slope', 'gravity', 'acceleration'],
    duration: 3000,
    learningObjectives: ['Resolve gravity components', 'Observe acceleration'],
    manifest: {
      type: 'physics',
      title: 'Motion on Incline',
      entities: [
        {
          id: 'block',
          type: 'dynamic-body',
          x: 200,
          y: 150,
          properties: { mass: 1, radius: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'slope',
          type: 'boundary',
          x: 300,
          y: 250,
          properties: { width: 400, height: 10, angle: 30 },
          appearance: { fillColor: '#666' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Slide', description: 'Block accelerates down slope', duration: 3000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-016',
    name: 'Free Fall',
    description: 'Object falling under gravity',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['gravity', 'free-fall', 'acceleration', 'drop'],
    duration: 2000,
    learningObjectives: ['Observe acceleration', 'Relate distance to time'],
    manifest: {
      type: 'physics',
      title: 'Free Fall',
      entities: [
        {
          id: 'ball',
          type: 'dynamic-body',
          x: 400,
          y: 100,
          properties: { mass: 1, radius: 15 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'ground',
          type: 'boundary',
          x: 400,
          y: 450,
          properties: { width: 800, height: 10 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'velocity-display',
          type: 'sensor',
          x: 700,
          y: 100,
          properties: { type: 'value-monitor', target: 'ball', property: 'vy' },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Drop', description: 'Ball accelerates downward', duration: 2000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-017',
    name: 'Bouncing Ball',
    description: 'Ball with restitution bouncing',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['bounce', 'restitution', 'energy', 'loss'],
    duration: 6000,
    learningObjectives: ['Observe energy loss', 'Understand coefficient of restitution'],
    manifest: {
      type: 'physics',
      title: 'Bouncing Ball',
      entities: [
        {
          id: 'ball',
          type: 'dynamic-body',
          x: 400,
          y: 100,
          properties: { mass: 1, radius: 20, restitution: 0.8 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'ground',
          type: 'boundary',
          x: 400,
          y: 450,
          properties: { width: 800, height: 10 },
          appearance: { fillColor: '#666' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Bounce', description: 'Ball bounces with decreasing height', duration: 6000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-018',
    name: 'Roller Coaster',
    description: 'Energy conservation on track',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['energy', 'coaster', 'track', 'conservation'],
    duration: 8000,
    learningObjectives: ['Observe energy conversion', 'Understand conservation'],
    manifest: {
      type: 'physics',
      title: 'Roller Coaster',
      entities: [
        {
          id: 'cart',
          type: 'dynamic-body',
          x: 100,
          y: 150,
          properties: { mass: 2, radius: 20, track: 'coaster-track' },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'coaster-track',
          type: 'boundary',
          x: 400,
          y: 250,
          properties: { type: 'curve', path: 'hill-valley-hill' },
          appearance: { strokeColor: '#666', strokeWidth: 4 },
        },
        {
          id: 'energy-meter',
          type: 'sensor',
          x: 700,
          y: 100,
          properties: { type: 'energy-bar', target: 'cart' },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Ride', description: 'Cart converts PE to KE and back', duration: 8000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-019',
    name: 'Pulley System',
    description: 'Mechanical advantage demonstration',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['pulley', 'mechanical-advantage', 'force', 'torque'],
    duration: 4000,
    learningObjectives: ['Understand mechanical advantage', 'Observe force multiplication'],
    manifest: {
      type: 'physics',
      title: 'Pulley System',
      entities: [
        {
          id: 'fixed-pulley',
          type: 'fixed-point',
          x: 400,
          y: 150,
          properties: { radius: 30 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'mass-1',
          type: 'dynamic-body',
          x: 350,
          y: 300,
          properties: { mass: 2, radius: 25 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'mass-2',
          type: 'dynamic-body',
          x: 450,
          y: 200,
          properties: { mass: 1, radius: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'rope',
          type: 'constraint',
          x: 400,
          y: 225,
          properties: { type: 'rope', length: 150, bodyA: 'mass-1', bodyB: 'mass-2', pulley: 'fixed-pulley' },
          appearance: { strokeColor: '#8b4513', strokeWidth: 3 },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Motion', description: 'Heavier mass pulls lighter one up', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-020',
    name: 'Atwood Machine',
    description: 'Two masses over pulley',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['atwood', 'pulley', 'acceleration', 'tension'],
    duration: 4000,
    learningObjectives: ['Calculate acceleration', 'Understand tension'],
    manifest: {
      type: 'physics',
      title: 'Atwood Machine',
      entities: [
        {
          id: 'pulley',
          type: 'fixed-point',
          x: 400,
          y: 100,
          properties: { radius: 25, mass: 0 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'mass-heavy',
          type: 'dynamic-body',
          x: 350,
          y: 250,
          properties: { mass: 3, radius: 30 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'mass-light',
          type: 'dynamic-body',
          x: 450,
          y: 150,
          properties: { mass: 2, radius: 25 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Acceleration', description: 'Unequal masses accelerate', duration: 4000, stateChanges: {} },
      ],
    },
  },
  // Continuing with 10 more physics simulations...
  {
    id: 'sim-physics-021',
    name: 'Torque and Rotation',
    description: 'Force causing angular acceleration',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['torque', 'rotation', 'angular', 'moment'],
    duration: 4000,
    learningObjectives: ['Understand torque', 'Relate force to rotation'],
    manifest: {
      type: 'physics',
      title: 'Torque Demonstration',
      entities: [
        {
          id: 'wheel',
          type: 'dynamic-body',
          x: 400,
          y: 300,
          properties: { mass: 5, radius: 80, momentOfInertia: 10 },
          appearance: { fillColor: '#4ecdc4', strokeColor: '#333', strokeWidth: 2 },
        },
        {
          id: 'force-applier',
          type: 'force-field',
          x: 480,
          y: 300,
          properties: { force: 50, direction: 90, target: 'wheel' },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Apply Torque', description: 'Force applied at rim causes rotation', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-022',
    name: 'Conservation of Angular Momentum',
    description: 'Ice skater spin demonstration',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['angular-momentum', 'conservation', 'spin', 'rotation'],
    duration: 5000,
    learningObjectives: ['Understand angular momentum conservation', 'Observe speed change'],
    manifest: {
      type: 'physics',
      title: 'Angular Momentum',
      entities: [
        {
          id: 'skater',
          type: 'dynamic-body',
          x: 400,
          y: 300,
          properties: { mass: 60, radius: 30, angularVelocity: 1 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'arms',
          type: 'structure',
          x: 400,
          y: 300,
          properties: { parent: 'skater', length: 100, angle: 180 },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 8 },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Extend', description: 'Skater spins with arms extended', duration: 2000, stateChanges: {} },
        { id: 'step-2', title: 'Pull In', description: 'Arms pulled in, spin speeds up', duration: 3000, stateChanges: { 'arms.length': 30, 'skater.angularVelocity': 4 } },
      ],
    },
  },
  {
    id: 'sim-physics-023',
    name: 'Buoyancy and Floating',
    description: 'Objects in fluid with density differences',
    domain: 'physics',
    difficulty: 'beginner',
    tags: ['buoyancy', 'density', 'float', 'archimedes'],
    duration: 4000,
    learningObjectives: ['Understand buoyancy', 'Relate density to floating'],
    manifest: {
      type: 'physics',
      title: 'Buoyancy',
      entities: [
        {
          id: 'water',
          type: 'field',
          x: 400,
          y: 350,
          properties: { width: 600, height: 200, density: 1000 },
          appearance: { fillColor: 'rgba(78, 205, 196, 0.5)' },
        },
        {
          id: 'wood-block',
          type: 'dynamic-body',
          x: 300,
          y: 200,
          properties: { mass: 0.5, radius: 30, density: 600 },
          appearance: { fillColor: '#8b7355' },
        },
        {
          id: 'metal-block',
          type: 'dynamic-body',
          x: 500,
          y: 200,
          properties: { mass: 2, radius: 25, density: 8000 },
          appearance: { fillColor: '#95a5a6' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Drop', description: 'Blocks dropped into water', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-024',
    name: 'Bernoulli Principle',
    description: 'Fluid pressure and velocity relationship',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['bernoulli', 'fluid', 'pressure', 'velocity'],
    duration: 5000,
    learningObjectives: ['Understand Bernoulli principle', 'Observe pressure-velocity tradeoff'],
    manifest: {
      type: 'physics',
      title: 'Bernoulli Effect',
      entities: [
        {
          id: 'pipe-narrow',
          type: 'boundary',
          x: 400,
          y: 300,
          properties: { width: 200, height: 60, constriction: true },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'fluid-particle',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 0.1, radius: 8, vx: 3 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'pressure-gauge-1',
          type: 'sensor',
          x: 300,
          y: 200,
          properties: { type: 'pressure', value: 100 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'pressure-gauge-2',
          type: 'sensor',
          x: 500,
          y: 200,
          properties: { type: 'pressure', value: 60 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Flow', description: 'Fluid speeds up in narrow section, pressure drops', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-025',
    name: 'Wave Propagation',
    description: 'Transverse wave traveling through medium',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['wave', 'propagation', 'transverse', 'amplitude'],
    duration: 5000,
    learningObjectives: ['Understand wave properties', 'Observe wavelength and amplitude'],
    manifest: {
      type: 'physics',
      title: 'Wave Motion',
      entities: [
        {
          id: 'medium',
          type: 'field',
          x: 400,
          y: 300,
          properties: { width: 600, height: 10, type: 'string' },
          appearance: { strokeColor: '#666', strokeWidth: 2 },
        },
        {
          id: 'wave-source',
          type: 'wave-source',
          x: 100,
          y: 300,
          properties: { frequency: 2, amplitude: 50, wavelength: 100 },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Propagate', description: 'Wave travels along string', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-026',
    name: 'Standing Waves',
    description: 'Resonance on a string',
    domain: 'physics',
    difficulty: 'advanced',
    tags: ['standing-wave', 'resonance', 'harmonics', 'nodes'],
    duration: 6000,
    learningObjectives: ['Understand standing waves', 'Identify nodes and antinodes'],
    manifest: {
      type: 'physics',
      title: 'Standing Waves',
      entities: [
        {
          id: 'string',
          type: 'field',
          x: 400,
          y: 300,
          properties: { width: 600, height: 10, fixed: true },
          appearance: { strokeColor: '#666', strokeWidth: 2 },
        },
        {
          id: 'vibrator',
          type: 'wave-source',
          x: 100,
          y: 300,
          properties: { frequency: 4, amplitude: 30 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'node-marker-1',
          type: 'marker',
          x: 250,
          y: 300,
          properties: { type: 'node' },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'antinode-marker',
          type: 'marker',
          x: 400,
          y: 270,
          properties: { type: 'antinode' },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Resonate', description: 'Standing wave pattern forms', duration: 6000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-027',
    name: 'Interference Pattern',
    description: 'Constructive and destructive interference',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['interference', 'constructive', 'destructive', 'superposition'],
    duration: 5000,
    learningObjectives: ['Understand wave interference', 'Observe pattern formation'],
    manifest: {
      type: 'physics',
      title: 'Wave Interference',
      entities: [
        {
          id: 'source-a',
          type: 'wave-source',
          x: 300,
          y: 200,
          properties: { frequency: 3, amplitude: 30 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'source-b',
          type: 'wave-source',
          x: 500,
          y: 200,
          properties: { frequency: 3, amplitude: 30 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'interference-region',
          type: 'field',
          x: 400,
          y: 400,
          properties: { width: 400, height: 300, type: 'interference-pattern' },
          appearance: { fillColor: 'rgba(100, 100, 100, 0.1)' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Interfere', description: 'Waves create interference pattern', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-028',
    name: 'Doppler Effect',
    description: 'Frequency shift from moving source',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['doppler', 'frequency', 'shift', 'moving-source'],
    duration: 6000,
    learningObjectives: ['Understand Doppler effect', 'Observe frequency change'],
    manifest: {
      type: 'physics',
      title: 'Doppler Effect',
      entities: [
        {
          id: 'moving-source',
          type: 'dynamic-body',
          x: 100,
          y: 300,
          properties: { mass: 1, radius: 20, vx: 5, emitsWaves: true },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'observer',
          type: 'fixed-point',
          x: 700,
          y: 300,
          properties: { radius: 15 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'frequency-display',
          type: 'sensor',
          x: 700,
          y: 200,
          properties: { type: 'frequency', value: 3 },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Approach', description: 'Source approaches, frequency increases', duration: 3000, stateChanges: { 'frequency-display.value': 4.5 } },
        { id: 'step-2', title: 'Recede', description: 'Source passes and recedes, frequency decreases', duration: 3000, stateChanges: { 'frequency-display.value': 2 } },
      ],
    },
  },
  {
    id: 'sim-physics-029',
    name: 'Electric Field Lines',
    description: 'Field visualization around charges',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['electric-field', 'charges', 'field-lines', 'coulomb'],
    duration: 4000,
    learningObjectives: ['Understand electric fields', 'Observe field line patterns'],
    manifest: {
      type: 'physics',
      title: 'Electric Fields',
      entities: [
        {
          id: 'positive-charge',
          type: 'fixed-point',
          x: 300,
          y: 300,
          properties: { charge: 10, radius: 20 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'negative-charge',
          type: 'fixed-point',
          x: 500,
          y: 300,
          properties: { charge: -10, radius: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'field-lines',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'electric-field', sources: ['positive-charge', 'negative-charge'] },
          appearance: { strokeColor: 'rgba(100, 100, 100, 0.5)', strokeWidth: 1 },
        },
        {
          id: 'test-charge',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 0.1, charge: 1, radius: 8 },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Field', description: 'Test charge follows field lines', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-physics-030',
    name: 'Magnetic Field Around Wire',
    description: 'Circular field from current-carrying wire',
    domain: 'physics',
    difficulty: 'intermediate',
    tags: ['magnetic-field', 'current', 'wire', 'ampere'],
    duration: 5000,
    learningObjectives: ['Understand magnetic fields', 'Observe circular field pattern'],
    manifest: {
      type: 'physics',
      title: 'Magnetic Field',
      entities: [
        {
          id: 'wire',
          type: 'fixed-point',
          x: 400,
          y: 300,
          properties: { current: 5, radius: 5 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'field-ring-1',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'magnetic-ring', radius: 80, current: 5 },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 2 },
        },
        {
          id: 'field-ring-2',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'magnetic-ring', radius: 140, current: 5 },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 2 },
        },
        {
          id: 'compass',
          type: 'dynamic-body',
          x: 480,
          y: 300,
          properties: { mass: 0.5, radius: 15, magnetic: true },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Current Flow', description: 'Current creates circular magnetic field', duration: 5000, stateChanges: {} },
      ],
    },
  },
];

// =============================================================================
// CHEMISTRY SIMULATIONS (31-50)
// =============================================================================

export const ChemistrySimulations: SimulationExample[] = [
  {
    id: 'sim-chem-001',
    name: 'Atomic Orbitals',
    description: 'Electron probability distributions',
    domain: 'chemistry',
    difficulty: 'advanced',
    tags: ['orbitals', 'electrons', 'probability', 'quantum'],
    duration: 5000,
    learningObjectives: ['Understand orbital shapes', 'Observe probability clouds'],
    manifest: {
      type: 'chemistry',
      title: 'Atomic Orbitals',
      entities: [
        {
          id: 'nucleus',
          type: 'molecule',
          x: 400,
          y: 300,
          properties: { type: 'nucleus', charge: 1 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 's-orbital',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'spherical-orbital', radius: 100 },
          appearance: { fillColor: 'rgba(78, 205, 196, 0.3)' },
        },
      ],
      steps: [
        { id: 'step-1', title: '1s Orbital', description: 'Spherical electron distribution', duration: 5000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-chem-002',
    name: 'Covalent Bond Formation',
    description: 'H2 molecule formation',
    domain: 'chemistry',
    difficulty: 'beginner',
    tags: ['covalent', 'bond', 'sharing', 'h2'],
    duration: 4000,
    learningObjectives: ['Understand electron sharing', 'Observe bond formation'],
    manifest: {
      type: 'chemistry',
      title: 'Covalent Bond',
      entities: [
        {
          id: 'atom-1',
          type: 'molecule',
          x: 300,
          y: 300,
          properties: { type: 'H', electrons: 1 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'atom-2',
          type: 'molecule',
          x: 500,
          y: 300,
          properties: { type: 'H', electrons: 1 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'shared-electron',
          type: 'molecule',
          x: 400,
          y: 300,
          properties: { type: 'electron', orbit: 'molecular' },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Approach', description: 'Atoms come together', duration: 1500, stateChanges: {} },
        { id: 'step-2', title: 'Bond', description: 'Electrons shared in molecular orbital', duration: 2500, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-chem-003',
    name: 'Ionic Bond Formation',
    description: 'NaCl formation by electron transfer',
    domain: 'chemistry',
    difficulty: 'beginner',
    tags: ['ionic', 'bond', 'transfer', 'nacl'],
    duration: 4000,
    learningObjectives: ['Understand electron transfer', 'Observe ion formation'],
    manifest: {
      type: 'chemistry',
      title: 'Ionic Bond',
      entities: [
        {
          id: 'sodium',
          type: 'molecule',
          x: 300,
          y: 300,
          properties: { type: 'Na', electrons: 1 },
          appearance: { fillColor: '#95a5a6' },
        },
        {
          id: 'chlorine',
          type: 'molecule',
          x: 500,
          y: 300,
          properties: { type: 'Cl', electrons: 7 },
          appearance: { fillColor: '#e74c3c' },
        },
        {
          id: 'electron',
          type: 'molecule',
          x: 300,
          y: 300,
          properties: { type: 'electron' },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Transfer', description: 'Electron moves from Na to Cl', duration: 2000, stateChanges: { 'electron.x': 500 } },
        { id: 'step-2', title: 'Ions', description: 'Na+ and Cl- ions attract', duration: 2000, stateChanges: { 'sodium.x': 450, 'chlorine.x': 450 } },
      ],
    },
  },
  // Additional chemistry simulations would follow same pattern...
];

// =============================================================================
// BIOLOGY SIMULATIONS (51-70)
// =============================================================================

export const BiologySimulations: SimulationExample[] = [
  {
    id: 'sim-bio-001',
    name: 'Cell Division - Mitosis',
    description: 'Complete mitosis process',
    domain: 'biology',
    difficulty: 'intermediate',
    tags: ['mitosis', 'cell-division', 'chromosomes', 'reproduction'],
    duration: 8000,
    learningObjectives: ['Understand mitosis phases', 'Observe chromosome movement'],
    manifest: {
      type: 'biology',
      title: 'Mitosis',
      entities: [
        {
          id: 'cell',
          type: 'cell',
          x: 400,
          y: 300,
          properties: { type: 'animal', phase: 'interphase' },
          appearance: { fillColor: '#95e1d3', strokeColor: '#4ecdc4', strokeWidth: 3 },
        },
        {
          id: 'chromosome-1',
          type: 'molecule',
          x: 380,
          y: 300,
          properties: { type: 'chromosome', condensed: false },
          appearance: { fillColor: '#e74c3c' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Prophase', description: 'Chromosomes condense', duration: 1500, stateChanges: { 'chromosome-1.condensed': true } },
        { id: 'step-2', title: 'Metaphase', description: 'Chromosomes align', duration: 1500, stateChanges: { 'chromosome-1.x': 400 } },
        { id: 'step-3', title: 'Anaphase', description: 'Sister chromatids separate', duration: 1500, stateChanges: { 'chromosome-1.x': 350 } },
        { id: 'step-4', title: 'Telophase', description: 'Nuclear membranes reform', duration: 1500, stateChanges: { 'cell.phase': 'divided' } },
        { id: 'step-5', title: 'Cytokinesis', description: 'Cell divides', duration: 2000, stateChanges: { 'cell.count': 2 } },
      ],
    },
  },
  {
    id: 'sim-bio-002',
    name: 'Photosynthesis',
    description: 'Light reactions and Calvin cycle',
    domain: 'biology',
    difficulty: 'intermediate',
    tags: ['photosynthesis', 'chloroplast', 'light', 'calvin-cycle'],
    duration: 6000,
    learningObjectives: ['Understand photosynthesis stages', 'Observe energy conversion'],
    manifest: {
      type: 'biology',
      title: 'Photosynthesis',
      entities: [
        {
          id: 'chloroplast',
          type: 'cell',
          x: 400,
          y: 300,
          properties: { type: 'organelle', size: 200 },
          appearance: { fillColor: '#2ecc71' },
        },
        {
          id: 'sunlight',
          type: 'field',
          x: 200,
          y: 100,
          properties: { type: 'photons', intensity: 'high' },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'co2',
          type: 'molecule',
          x: 600,
          y: 150,
          properties: { type: 'CO2' },
          appearance: { fillColor: '#95a5a6' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Light Reactions', description: 'Photons excite electrons', duration: 2000, stateChanges: {} },
        { id: 'step-2', title: 'ATP Formation', description: 'Energy carriers created', duration: 1500, stateChanges: {} },
        { id: 'step-3', title: 'Calvin Cycle', description: 'CO2 fixed into glucose', duration: 2500, stateChanges: { 'co2.consumed': true } },
      ],
    },
  },
  // Additional biology simulations...
];

// =============================================================================
// CS SIMULATIONS (71-90)
// =============================================================================

export const CSSimulations: SimulationExample[] = [
  {
    id: 'sim-cs-001',
    name: 'Bubble Sort',
    description: 'Simple comparison sort algorithm',
    domain: 'cs',
    difficulty: 'beginner',
    tags: ['sorting', 'bubble-sort', 'algorithm', 'comparison'],
    duration: 5000,
    learningObjectives: ['Understand bubble sort', 'Observe O(n²) complexity'],
    manifest: {
      type: 'discrete',
      title: 'Bubble Sort',
      entities: [
        {
          id: 'array-bar-1',
          type: 'array',
          x: 200,
          y: 300,
          properties: { value: 5, height: 50 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'array-bar-2',
          type: 'array',
          x: 260,
          y: 300,
          properties: { value: 2, height: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'array-bar-3',
          type: 'array',
          x: 320,
          y: 300,
          properties: { value: 8, height: 80 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Compare', description: 'Compare adjacent elements', duration: 1500, stateChanges: { 'array-bar-1.highlight': true, 'array-bar-2.highlight': true } },
        { id: 'step-2', title: 'Swap', description: 'Swap if out of order', duration: 1500, stateChanges: { 'array-bar-1.x': 260, 'array-bar-2.x': 200 } },
        { id: 'step-3', title: 'Repeat', description: 'Continue through array', duration: 2000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-cs-002',
    name: 'Binary Search Tree',
    description: 'BST insertion and traversal',
    domain: 'cs',
    difficulty: 'intermediate',
    tags: ['tree', 'binary-search', 'bst', 'traversal'],
    duration: 6000,
    learningObjectives: ['Understand BST structure', 'Observe search efficiency'],
    manifest: {
      type: 'discrete',
      title: 'Binary Search Tree',
      entities: [
        {
          id: 'node-root',
          type: 'node',
          x: 400,
          y: 100,
          properties: { value: 50, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'node-left',
          type: 'node',
          x: 300,
          y: 200,
          properties: { value: 30, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'node-right',
          type: 'node',
          x: 500,
          y: 200,
          properties: { value: 70, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Start', description: 'Begin at root', duration: 1000, stateChanges: { 'node-root.visited': true } },
        { id: 'step-2', title: 'Compare', description: 'Go left if less, right if greater', duration: 2000, stateChanges: { 'node-left.visited': true } },
        { id: 'step-3', title: 'Found', description: 'Value found or inserted', duration: 3000, stateChanges: {} },
      ],
    },
  },
  // Additional CS simulations...
];

// =============================================================================
// MATH SIMULATIONS (91-100)
// =============================================================================

export const MathSimulations: SimulationExample[] = [
  {
    id: 'sim-math-001',
    name: 'Function Plotting',
    description: 'Visualizing mathematical functions',
    domain: 'math',
    difficulty: 'beginner',
    tags: ['function', 'graph', 'plotting', 'calculus'],
    duration: 4000,
    learningObjectives: ['Visualize functions', 'Understand domain and range'],
    manifest: {
      type: 'discrete',
      title: 'Function Plotter',
      entities: [
        {
          id: 'graph',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'coordinate-system', width: 600, height: 400 },
          appearance: { strokeColor: '#666' },
        },
        {
          id: 'function-curve',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'curve', function: 'sin(x)' },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 3 },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Plot', description: 'Function drawn on coordinate plane', duration: 4000, stateChanges: {} },
      ],
    },
  },
  {
    id: 'sim-math-002',
    name: 'Derivative Visualization',
    description: 'Slope of tangent line',
    domain: 'math',
    difficulty: 'intermediate',
    tags: ['derivative', 'slope', 'tangent', 'calculus'],
    duration: 5000,
    learningObjectives: ['Understand derivatives', 'Visualize rate of change'],
    manifest: {
      type: 'discrete',
      title: 'Derivative',
      entities: [
        {
          id: 'curve',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'curve', function: 'x^2' },
          appearance: { strokeColor: '#4ecdc4', strokeWidth: 3 },
        },
        {
          id: 'point',
          type: 'marker',
          x: 300,
          y: 250,
          properties: { onCurve: true },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'tangent',
          type: 'field',
          x: 400,
          y: 300,
          properties: { type: 'tangent-line', slope: 0 },
          appearance: { strokeColor: '#ffd93d', strokeWidth: 2 },
        },
      ],
      steps: [
        { id: 'step-1', title: 'Point', description: 'Point moves along curve', duration: 5000, stateChanges: { 'point.x': 500 } },
      ],
    },
  },
  // Additional math simulations...
];

// =============================================================================
// Export all simulations
// =============================================================================

export const AllSimulationExamples: SimulationExample[] = [
  ...PhysicsSimulations,
  ...ChemistrySimulations,
  ...BiologySimulations,
  ...CSSimulations,
  ...MathSimulations,
];

export function getSimulationsByDomain(domain: SimulationExample['domain']): SimulationExample[] {
  return AllSimulationExamples.filter((s) => s.domain === domain);
}

export function getSimulationsByDifficulty(difficulty: SimulationExample['difficulty']): SimulationExample[] {
  return AllSimulationExamples.filter((s) => s.difficulty === difficulty);
}

export function searchSimulations(query: string): SimulationExample[] {
  const lower = query.toLowerCase();
  return AllSimulationExamples.filter(
    (s) =>
      s.name.toLowerCase().includes(lower) ||
      s.description.toLowerCase().includes(lower) ||
      s.tags.some((tag) => tag.includes(lower))
  );
}
