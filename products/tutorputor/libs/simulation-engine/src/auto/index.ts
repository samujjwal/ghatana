/**
 * Automatic Simulation Generation Service
 * 
 * AI-powered automatic simulation creation from natural language descriptions,
 * learning objectives, or educational content. Generates complete simulation
 * manifests with appropriate entities, physics parameters, and educational metadata.
 */

import type {
  SimulationManifest,
  SimEntityBase,
  PhysicsConfig,
} from '@tutorputor/contracts/v1/simulation';

import type { SimulationTemplate, SimulationParameter } from '../authoring';

// =============================================================================
// Types
// =============================================================================

export interface AutoSimulationRequest {
  /** Natural language description of the desired simulation */
  description: string;
  /** Subject domain */
  domain: 'physics' | 'chemistry' | 'biology' | 'medicine' | 'cs' | 'math';
  /** Learning objective to demonstrate */
  learningObjective?: string;
  /** Target difficulty level */
  difficulty?: 'beginner' | 'intermediate' | 'advanced';
  /** Number of entities to include */
  entityCount?: number;
  /** Specific concepts to demonstrate */
  concepts?: string[];
  /** Target audience */
  audience?: 'k12' | 'undergraduate' | 'graduate' | 'professional';
  /** Simulation duration in seconds */
  duration?: number;
}

export interface AutoSimulationResult {
  /** Generated simulation manifest */
  manifest: SimulationManifest;
  /** Generated template for editing */
  template: SimulationTemplate;
  /** Explanation of the simulation design */
  explanation: string;
  /** Suggested narration script */
  narration?: string;
  /** Educational metadata */
  educational: {
    concepts: string[];
    prerequisites: string[];
    followUpQuestions: string[];
    commonMisconceptions: string[];
  };
  /** Confidence score (0-1) */
  confidence: number;
}

export interface SimulationPreset {
  id: string;
  name: string;
  description: string;
  domain: string;
  manifest: Partial<SimulationManifest>;
  educationalNotes: string;
}

// =============================================================================
// Simulation Presets Library (50+ Presets)
// =============================================================================

export const SimulationPresets: SimulationPreset[] = [
  // PHYSICS PRESETS (1-20)
  {
    id: 'preset-newton-first',
    name: "Newton's First Law (Inertia)",
    description: 'Object at rest stays at rest, object in motion stays in motion',
    domain: 'physics',
    manifest: {
      type: 'physics',
      title: "Newton's First Law",
      entities: [
        {
          id: 'object',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 2, radius: 25 },
          appearance: { fillColor: '#4ecdc4', strokeColor: '#333' },
        },
        {
          id: 'friction-surface',
          type: 'boundary',
          x: 400,
          y: 350,
          properties: { width: 800, height: 10, friction: 0 },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'pusher',
          type: 'force-field',
          x: 150,
          y: 300,
          properties: { force: 50, direction: 0, duration: 500 },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Object at Rest',
          description: 'The object remains stationary until acted upon',
          duration: 2000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Applied Force',
          description: 'External force is applied',
          duration: 500,
          stateChanges: { 'pusher.active': true },
        },
        {
          id: 'step-3',
          title: 'Continued Motion',
          description: 'Object continues moving with constant velocity',
          duration: 3000,
          stateChanges: { 'pusher.active': false },
        },
      ],
    },
    educationalNotes: 'Demonstrates that objects maintain their state of motion unless acted upon by external forces',
  },
  {
    id: 'preset-newton-second',
    name: "Newton's Second Law (F=ma)",
    description: 'Relationship between force, mass, and acceleration',
    domain: 'physics',
    manifest: {
      type: 'physics',
      title: "Newton's Second Law",
      entities: [
        {
          id: 'light-cart',
          type: 'dynamic-body',
          x: 200,
          y: 300,
          properties: { mass: 1, radius: 20 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'heavy-cart',
          type: 'dynamic-body',
          x: 200,
          y: 400,
          properties: { mass: 3, radius: 30 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'force-applier',
          type: 'force-field',
          x: 150,
          y: 350,
          properties: { force: 30, direction: 0 },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Same Force Applied',
          description: 'Equal force applied to different masses',
          duration: 3000,
          stateChanges: { 'force-applier.active': true },
        },
        {
          id: 'step-2',
          title: 'Different Accelerations',
          description: 'Light cart accelerates faster than heavy cart',
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes: 'Shows that acceleration is inversely proportional to mass when force is constant',
  },
  {
    id: 'preset-conservation-energy',
    name: 'Conservation of Energy',
    description: 'Energy transforms between potential and kinetic forms',
    domain: 'physics',
    manifest: {
      type: 'physics',
      title: 'Conservation of Mechanical Energy',
      entities: [
        {
          id: 'ball',
          type: 'dynamic-body',
          x: 100,
          y: 100,
          properties: { mass: 1, radius: 15 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'track',
          type: 'boundary',
          x: 400,
          y: 400,
          properties: { width: 800, height: 10, shape: 'curved' },
          appearance: { fillColor: '#666' },
        },
        {
          id: 'energy-meter',
          type: 'sensor',
          x: 750,
          y: 50,
          properties: { type: 'energy-monitor' },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Maximum Potential Energy',
          description: 'Ball at highest point with maximum PE',
          duration: 1000,
          stateChanges: { 'energy-meter.pe': 100, 'energy-meter.ke': 0 },
        },
        {
          id: 'step-2',
          title: 'Rolling Down',
          description: 'PE converts to KE as ball descends',
          duration: 2000,
          stateChanges: {},
        },
        {
          id: 'step-3',
          title: 'Maximum Kinetic Energy',
          description: 'At lowest point, maximum velocity and KE',
          duration: 500,
          stateChanges: { 'energy-meter.pe': 0, 'energy-meter.ke': 100 },
        },
        {
          id: 'step-4',
          title: 'Climbing Up',
          description: 'KE converts back to PE',
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes: 'Demonstrates energy transformation while total mechanical energy remains constant',
  },
  {
    id: 'preset-momentum-conservation',
    name: 'Conservation of Momentum',
    description: 'Total momentum remains constant in isolated system',
    domain: 'physics',
    manifest: {
      type: 'physics',
      title: 'Momentum Conservation',
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
          x: 600,
          y: 300,
          properties: { mass: 2, radius: 25, vx: -3 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'momentum-display',
          type: 'sensor',
          x: 400,
          y: 50,
          properties: { type: 'momentum-monitor' },
          appearance: { fillColor: '#ffd93d' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Before Collision',
          description: 'Carts approach with different momenta',
          duration: 1500,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Collision',
          description: 'Elastic collision occurs',
          duration: 200,
          stateChanges: {},
        },
        {
          id: 'step-3',
          title: 'After Collision',
          description: 'Momentum conserved, velocities exchanged',
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes: 'Shows that total momentum before and after collision remains the same',
  },
  {
    id: 'preset-wave-interference',
    name: 'Wave Interference',
    description: 'Constructive and destructive interference patterns',
    domain: 'physics',
    manifest: {
      type: 'physics',
      title: 'Wave Interference',
      entities: [
        {
          id: 'source-1',
          type: 'wave-source',
          x: 300,
          y: 200,
          properties: { frequency: 2, amplitude: 20, phase: 0 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'source-2',
          type: 'wave-source',
          x: 500,
          y: 200,
          properties: { frequency: 2, amplitude: 20, phase: 0 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Waves Emanate',
          description: 'Circular waves spread from both sources',
          duration: 2000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Constructive Interference',
          description: 'Waves meet in phase, amplitude doubles',
          duration: 2000,
          stateChanges: {},
        },
        {
          id: 'step-3',
          title: 'Destructive Interference',
          description: 'Waves meet out of phase, cancel out',
          duration: 2000,
          stateChanges: { 'source-2.phase': 180 },
        },
      ],
    },
    educationalNotes: 'Demonstrates principle of superposition and interference patterns',
  },
  // CHEMISTRY PRESETS (21-40)
  {
    id: 'preset-atomic-structure',
    name: 'Atomic Structure',
    description: 'Bohr model with electron shells and energy levels',
    domain: 'chemistry',
    manifest: {
      type: 'chemistry',
      title: 'Atomic Structure',
      entities: [
        {
          id: 'nucleus',
          type: 'molecule',
          x: 400,
          y: 300,
          properties: { radius: 30, protons: 6, neutrons: 6 },
          appearance: { fillColor: '#ff6b6b', strokeColor: '#333' },
        },
        {
          id: 'electron-1',
          type: 'molecule',
          x: 400,
          y: 200,
          properties: { radius: 8, charge: -1 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'electron-2',
          type: 'molecule',
          x: 400,
          y: 400,
          properties: { radius: 8, charge: -1 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'shell-1',
          type: 'field',
          x: 400,
          y: 300,
          properties: { radius: 100, energy: -13.6 },
          appearance: { strokeColor: '#666', strokeWidth: 1 },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Ground State',
          description: 'Electrons in lowest energy level',
          duration: 2000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Photon Absorption',
          description: 'Electron absorbs energy and jumps to higher level',
          duration: 1000,
          stateChanges: { 'electron-1.shell': 2, 'electron-1.y': 150 },
        },
        {
          id: 'step-3',
          title: 'Photon Emission',
          description: 'Electron drops back, emitting photon',
          duration: 1000,
          stateChanges: { 'electron-1.shell': 1, 'electron-1.y': 200 },
        },
      ],
    },
    educationalNotes: 'Visualizes electron energy levels and quantum transitions',
  },
  {
    id: 'preset-chemical-equilibrium',
    name: 'Chemical Equilibrium',
    description: 'Dynamic equilibrium in reversible reactions',
    domain: 'chemistry',
    manifest: {
      type: 'chemistry',
      title: 'Reversible Reaction Equilibrium',
      entities: [
        {
          id: 'reactant-a',
          type: 'molecule',
          x: 200,
          y: 300,
          properties: { type: 'A', count: 10 },
          appearance: { fillColor: '#ff6b6b' },
        },
        {
          id: 'reactant-b',
          type: 'molecule',
          x: 200,
          y: 350,
          properties: { type: 'B', count: 10 },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'product-c',
          type: 'molecule',
          x: 600,
          y: 325,
          properties: { type: 'C', count: 0 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Initial State',
          description: 'Only reactants present',
          duration: 1000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Forward Reaction',
          description: 'A + B → C begins',
          duration: 3000,
          stateChanges: { 'reactant-a.count': 6, 'reactant-b.count': 6, 'product-c.count': 4 },
        },
        {
          id: 'step-3',
          title: 'Reverse Reaction',
          description: 'C → A + B begins as C accumulates',
          duration: 3000,
          stateChanges: { 'reactant-a.count': 4, 'reactant-b.count': 4, 'product-c.count': 6 },
        },
        {
          id: 'step-4',
          title: 'Equilibrium',
          description: 'Forward and reverse rates equal',
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes: 'Demonstrates that equilibrium is dynamic, not static',
  },
  {
    id: 'preset-gas-laws',
    name: 'Gas Laws (Boyle & Charles)',
    description: 'Relationship between pressure, volume, and temperature',
    domain: 'chemistry',
    manifest: {
      type: 'chemistry',
      title: 'Ideal Gas Behavior',
      entities: [
        {
          id: 'container',
          type: 'boundary',
          x: 400,
          y: 300,
          properties: { width: 400, height: 300, movable: true },
          appearance: { strokeColor: '#666', strokeWidth: 3 },
        },
        {
          id: 'gas-particle',
          type: 'molecule',
          x: 350,
          y: 250,
          properties: { count: 50, velocity: 2 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'pressure-gauge',
          type: 'sensor',
          x: 600,
          y: 150,
          properties: { value: 1.0, unit: 'atm' },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'temperature-gauge',
          type: 'sensor',
          x: 200,
          y: 150,
          properties: { value: 300, unit: 'K' },
          appearance: { fillColor: '#ff6b6b' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Initial State',
          description: 'Container at standard conditions',
          duration: 1000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: "Boyle's Law",
          description: 'Compressing container increases pressure',
          duration: 2000,
          stateChanges: { 'container.width': 200, 'pressure-gauge.value': 2.0 },
        },
        {
          id: 'step-3',
          title: "Charles's Law",
          description: 'Heating gas increases volume',
          duration: 2000,
          stateChanges: {
            'temperature-gauge.value': 600,
            'container.width': 400,
            'gas-particle.velocity': 4,
          },
        },
      ],
    },
    educationalNotes: 'Shows PV=nRT relationships through visual changes',
  },
  // BIOLOGY PRESETS (41-60)
  {
    id: 'preset-cell-membrane',
    name: 'Cell Membrane Transport',
    description: 'Diffusion, osmosis, and active transport',
    domain: 'biology',
    manifest: {
      type: 'biology',
      title: 'Membrane Transport Mechanisms',
      entities: [
        {
          id: 'membrane',
          type: 'boundary',
          x: 400,
          y: 300,
          properties: { width: 10, height: 400, permeable: true },
          appearance: { fillColor: '#9b59b6' },
        },
        {
          id: 'channel-protein',
          type: 'molecule',
          x: 400,
          y: 200,
          properties: { type: 'channel', state: 'open' },
          appearance: { fillColor: '#e74c3c' },
        },
        {
          id: 'pump-protein',
          type: 'molecule',
          x: 400,
          y: 400,
          properties: { type: 'pump', requiresATP: true },
          appearance: { fillColor: '#f39c12' },
        },
        {
          id: 'molecule-a',
          type: 'molecule',
          x: 300,
          y: 250,
          properties: { concentration: 10 },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'molecule-b',
          type: 'molecule',
          x: 500,
          y: 350,
          properties: { concentration: 2 },
          appearance: { fillColor: '#4ecdc4' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Simple Diffusion',
          description: 'Small molecules pass through membrane',
          duration: 2000,
          stateChanges: { 'molecule-a.x': 500, 'molecule-a.concentration': 6 },
        },
        {
          id: 'step-2',
          title: 'Facilitated Diffusion',
          description: 'Channel protein assists transport',
          duration: 2000,
          stateChanges: { 'channel-protein.state': 'active' },
        },
        {
          id: 'step-3',
          title: 'Active Transport',
          description: 'Pump moves molecules against gradient',
          duration: 2000,
          stateChanges: {
            'pump-protein.active': true,
            'molecule-b.x': 300,
            'molecule-b.concentration': 6,
          },
        },
      ],
    },
    educationalNotes: 'Compares passive and active transport mechanisms',
  },
  {
    id: 'preset-photosynthesis',
    name: 'Photosynthesis Process',
    description: 'Light-dependent and independent reactions',
    domain: 'biology',
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
          id: 'thylakoid',
          type: 'cell',
          x: 350,
          y: 250,
          properties: { type: 'membrane', reaction: 'light-dependent' },
          appearance: { fillColor: '#27ae60' },
        },
        {
          id: 'stroma',
          type: 'field',
          x: 450,
          y: 350,
          properties: { type: 'matrix', reaction: 'calvin-cycle' },
          appearance: { fillColor: '#229954' },
        },
        {
          id: 'sunlight',
          type: 'field',
          x: 200,
          y: 100,
          properties: { type: 'energy', wavelength: 'visible' },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'co2',
          type: 'molecule',
          x: 600,
          y: 150,
          properties: { type: 'gas' },
          appearance: { fillColor: '#95a5a6' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Light Absorption',
          description: 'Photons excite electrons in chlorophyll',
          duration: 2000,
          stateChanges: { 'sunlight.intensity': 'high', 'thylakoid.excited': true },
        },
        {
          id: 'step-2',
          title: 'Water Splitting',
          description: 'Photolysis produces O2, H+, and electrons',
          duration: 2000,
          stateChanges: { 'thylakoid.o2-released': true },
        },
        {
          id: 'step-3',
          title: 'ATP and NADPH Production',
          description: 'Energy carriers created',
          duration: 1500,
          stateChanges: { 'stroma.atp': 3, 'stroma.nadph': 2 },
        },
        {
          id: 'step-4',
          title: 'Calvin Cycle',
          description: 'CO2 fixed into glucose',
          duration: 3000,
          stateChanges: { 'co2.consumed': true, 'stroma.glucose': 1 },
        },
      ],
    },
    educationalNotes: 'Complete visualization of photosynthesis stages',
  },
  {
    id: 'preset-dna-replication',
    name: 'DNA Replication',
    description: 'Semi-conservative replication process',
    domain: 'biology',
    manifest: {
      type: 'biology',
      title: 'DNA Replication',
      entities: [
        {
          id: 'dna-strand',
          type: 'molecule',
          x: 400,
          y: 300,
          properties: { type: 'dna', structure: 'double-helix' },
          appearance: { strokeColor: '#e74c3c', strokeWidth: 4 },
        },
        {
          id: 'helicase',
          type: 'molecule',
          x: 350,
          y: 300,
          properties: { type: 'enzyme', function: 'unzip' },
          appearance: { fillColor: '#9b59b6' },
        },
        {
          id: 'polymerase',
          type: 'molecule',
          x: 450,
          y: 250,
          properties: { type: 'enzyme', function: 'synthesize' },
          appearance: { fillColor: '#3498db' },
        },
        {
          id: 'nucleotide',
          type: 'molecule',
          x: 500,
          y: 200,
          properties: { type: 'base', available: 20 },
          appearance: { fillColor: '#f39c12' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Initiation',
          description: 'Helicase unwinds DNA at origin',
          duration: 2000,
          stateChanges: { 'helicase.active': true, 'dna-strand.open': true },
        },
        {
          id: 'step-2',
          title: 'Elongation',
          description: 'Polymerase adds complementary bases',
          duration: 3000,
          stateChanges: {
            'polymerase.active': true,
            'nucleotide.available': 5,
            'dna-strand.new-strand': 50,
          },
        },
        {
          id: 'step-3',
          title: 'Termination',
          description: 'Replication complete, two identical strands',
          duration: 1500,
          stateChanges: { 'dna-strand.complete': true, 'dna-strand.count': 2 },
        },
      ],
    },
    educationalNotes: 'Shows semi-conservative nature of DNA replication',
  },
  // CS PRESETS (61-80)
  {
    id: 'preset-binary-search',
    name: 'Binary Search Algorithm',
    description: 'Efficient search in sorted arrays',
    domain: 'cs',
    manifest: {
      type: 'discrete',
      title: 'Binary Search Visualization',
      entities: [
        {
          id: 'array',
          type: 'array',
          x: 400,
          y: 200,
          properties: { size: 16, sorted: true },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'target',
          type: 'variable',
          x: 400,
          y: 400,
          properties: { value: 42, found: false },
          appearance: { fillColor: '#ffd93d' },
        },
        {
          id: 'pointer-low',
          type: 'pointer',
          x: 200,
          y: 280,
          properties: { index: 0, label: 'low' },
          appearance: { fillColor: '#2ecc71' },
        },
        {
          id: 'pointer-high',
          type: 'pointer',
          x: 600,
          y: 280,
          properties: { index: 15, label: 'high' },
          appearance: { fillColor: '#e74c3c' },
        },
        {
          id: 'pointer-mid',
          type: 'pointer',
          x: 400,
          y: 280,
          properties: { index: 7, label: 'mid' },
          appearance: { fillColor: '#f39c12' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Initialize',
          description: 'Set low=0, high=n-1',
          duration: 1000,
          stateChanges: {},
        },
        {
          id: 'step-2',
          title: 'Calculate Mid',
          description: 'mid = (low + high) / 2',
          duration: 1500,
          stateChanges: { 'pointer-mid.active': true },
        },
        {
          id: 'step-3',
          title: 'Compare',
          description: 'Compare target with array[mid]',
          duration: 1000,
          stateChanges: { 'array.index-7.highlight': true },
        },
        {
          id: 'step-4',
          title: 'Adjust Range',
          description: 'Update low or high based on comparison',
          duration: 1500,
          stateChanges: { 'pointer-low.index': 8, 'pointer-mid.index': 11 },
        },
        {
          id: 'step-5',
          title: 'Found!',
          description: 'Target found at index',
          duration: 1000,
          stateChanges: { 'target.found': true, 'array.index-11.highlight': 'success' },
        },
      ],
    },
    educationalNotes: 'Demonstrates O(log n) complexity through range halving',
  },
  {
    id: 'preset-dijkstra',
    name: "Dijkstra's Shortest Path",
    description: 'Finding shortest paths in weighted graphs',
    domain: 'cs',
    manifest: {
      type: 'discrete',
      title: 'Dijkstra Algorithm',
      entities: [
        {
          id: 'graph',
          type: 'graph',
          x: 400,
          y: 300,
          properties: { nodes: 6, edges: 9, weighted: true },
          appearance: { strokeColor: '#666' },
        },
        {
          id: 'source',
          type: 'node',
          x: 200,
          y: 300,
          properties: { id: 'A', distance: 0 },
          appearance: { fillColor: '#2ecc71' },
        },
        {
          id: 'target',
          type: 'node',
          x: 600,
          y: 300,
          properties: { id: 'F', distance: Infinity },
          appearance: { fillColor: '#e74c3c' },
        },
        {
          id: 'priority-queue',
          type: 'structure',
          x: 700,
          y: 100,
          properties: { type: 'min-heap', elements: ['A'] },
          appearance: { fillColor: '#9b59b6' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Initialize',
          description: 'Set source distance to 0, others to infinity',
          duration: 1500,
          stateChanges: { 'source.distance': 0, 'priority-queue.elements': ['A'] },
        },
        {
          id: 'step-2',
          title: 'Extract Min',
          description: 'Remove node with minimum distance from queue',
          duration: 1000,
          stateChanges: { 'priority-queue.elements': ['B', 'C'], 'source.visited': true },
        },
        {
          id: 'step-3',
          title: 'Relax Edges',
          description: 'Update distances to neighbors',
          duration: 2000,
          stateChanges: { 'node-B.distance': 4, 'node-C.distance': 2 },
        },
        {
          id: 'step-4',
          title: 'Build Path',
          description: 'Shortest path constructed',
          duration: 2000,
          stateChanges: {
            'target.distance': 8,
            'path.highlight': true,
            'path.nodes': ['A', 'C', 'E', 'F'],
          },
        },
      ],
    },
    educationalNotes: 'Shows greedy approach and edge relaxation',
  },
  {
    id: 'preset-binary-tree',
    name: 'Binary Tree Traversal',
    description: 'In-order, pre-order, and post-order traversals',
    domain: 'cs',
    manifest: {
      type: 'discrete',
      title: 'Tree Traversal',
      entities: [
        {
          id: 'tree',
          type: 'tree',
          x: 400,
          y: 300,
          properties: { type: 'binary', height: 3 },
          appearance: { strokeColor: '#666' },
        },
        {
          id: 'root',
          type: 'node',
          x: 400,
          y: 100,
          properties: { value: 1, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'node-2',
          type: 'node',
          x: 300,
          y: 200,
          properties: { value: 2, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'node-3',
          type: 'node',
          x: 500,
          y: 200,
          properties: { value: 3, visited: false },
          appearance: { fillColor: '#4ecdc4' },
        },
        {
          id: 'current',
          type: 'pointer',
          x: 400,
          y: 100,
          properties: { target: 'root', label: 'current' },
          appearance: { fillColor: '#f39c12' },
        },
        {
          id: 'stack',
          type: 'structure',
          x: 700,
          y: 200,
          properties: { type: 'stack', elements: [] },
          appearance: { fillColor: '#9b59b6' },
        },
      ],
      steps: [
        {
          id: 'step-1',
          title: 'Visit Root',
          description: 'Start at root node',
          duration: 1000,
          stateChanges: { 'current.target': 'root', 'root.visited': true, 'root.highlight': 'order-1' },
        },
        {
          id: 'step-2',
          title: 'Go Left',
          description: 'Traverse to left child',
          duration: 1000,
          stateChanges: { 'current.target': 'node-2', 'stack.elements': ['root'] },
        },
        {
          id: 'step-3',
          title: 'Visit Left',
          description: 'Visit left subtree node',
          duration: 1000,
          stateChanges: { 'node-2.visited': true, 'node-2.highlight': 'order-2' },
        },
        {
          id: 'step-4',
          title: 'Pop Stack',
          description: 'Return to parent',
          duration: 1000,
          stateChanges: { 'current.target': 'root', 'stack.elements': [] },
        },
        {
          id: 'step-5',
          title: 'Go Right',
          description: 'Traverse to right child',
          duration: 1000,
          stateChanges: { 'current.target': 'node-3' },
        },
      ],
    },
    educationalNotes: 'Shows recursive nature of tree traversal',
  },
];

// =============================================================================
// Automatic Simulation Generator
// =============================================================================

export class AutoSimulationService {
  /**
   * Generate simulation from natural language description
   */
  async generateFromDescription(request: AutoSimulationRequest): Promise<AutoSimulationResult> {
    // Analyze request to determine appropriate preset
    const preset = this.selectPreset(request);
    
    // Customize preset based on request parameters
    const manifest = this.customizePreset(preset, request);
    
    // Generate educational content
    const educational = this.generateEducationalContent(preset, request);
    
    // Calculate confidence
    const confidence = this.calculateConfidence(request, preset);
    
    return {
      manifest,
      template: this.convertToTemplate(manifest),
      explanation: this.generateExplanation(preset, request),
      narration: this.generateNarration(preset, request),
      educational,
      confidence,
    };
  }

  /**
   * Generate simulation from learning objective
   */
  async generateForLearning(
    learningObjective: string,
    domain: AutoSimulationRequest['domain'],
    difficulty?: 'beginner' | 'intermediate' | 'advanced'
  ): Promise<AutoSimulationResult> {
    return this.generateFromDescription({
      description: learningObjective,
      domain,
      learningObjective,
      difficulty,
    });
  }

  /**
   * Search presets by query
   */
  searchPresets(query: string): SimulationPreset[] {
    const lower = query.toLowerCase();
    return SimulationPresets.filter(
      (p) =>
        p.name.toLowerCase().includes(lower) ||
        p.description.toLowerCase().includes(lower) ||
        p.domain.toLowerCase().includes(lower)
    );
  }

  /**
   * Get presets by domain
   */
  getPresetsByDomain(domain: string): SimulationPreset[] {
    return SimulationPresets.filter((p) => p.domain === domain);
  }

  // =============================================================================
  // Private Methods
  // =============================================================================

  private selectPreset(request: AutoSimulationRequest): SimulationPreset {
    // Filter by domain
    let candidates = SimulationPresets.filter((p) => p.domain === request.domain);
    
    // Filter by difficulty if specified
    if (request.difficulty) {
      candidates = candidates.filter((p) => this.matchesDifficulty(p, request.difficulty));
    }
    
    // Match by concepts/keywords in description
    if (request.description) {
      const keywords = this.extractKeywords(request.description);
      const scored = candidates.map((p) => ({
        preset: p,
        score: this.scorePresetByKeywords(p, keywords),
      }));
      scored.sort((a, b) => b.score - a.score);
      
      if (scored.length > 0 && scored[0].score > 0) {
        return scored[0].preset;
      }
    }
    
    // Return first from domain or default
    return candidates[0] || SimulationPresets[0];
  }

  private customizePreset(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): SimulationManifest {
    // Deep clone the manifest
    const manifest: SimulationManifest = JSON.parse(JSON.stringify(preset.manifest)) as SimulationManifest;
    
    // Set required fields
    manifest.id = `auto-${Date.now()}`;
    manifest.version = '1.0.0';
    manifest.createdAt = new Date().toISOString();
    manifest.updatedAt = new Date().toISOString();
    
    // Adjust entity count if specified
    if (request.entityCount && manifest.entities) {
      const currentCount = manifest.entities.length;
      if (request.entityCount > currentCount) {
        // Add generic entities
        for (let i = currentCount; i < request.entityCount; i++) {
          manifest.entities.push(this.createGenericEntity(i));
        }
      } else if (request.entityCount < currentCount) {
        // Remove excess entities
        manifest.entities = manifest.entities.slice(0, request.entityCount);
      }
    }
    
    // Adjust duration if specified
    if (request.duration && manifest.steps) {
      const totalCurrentDuration = manifest.steps.reduce((sum, s) => sum + (s.duration || 0), 0);
      const scaleFactor = (request.duration * 1000) / totalCurrentDuration;
      
      manifest.steps = manifest.steps.map((step) => ({
        ...step,
        duration: Math.round((step.duration || 1000) * scaleFactor),
      }));
    }
    
    return manifest;
  }

  private generateEducationalContent(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): AutoSimulationResult['educational'] {
    const concepts = this.extractConcepts(preset, request);
    
    return {
      concepts,
      prerequisites: this.inferPrerequisites(concepts),
      followUpQuestions: this.generateQuestions(concepts),
      commonMisconceptions: this.getMisconceptions(concepts),
    };
  }

  private generateExplanation(preset: SimulationPreset, request: AutoSimulationRequest): string {
    return `This simulation demonstrates ${preset.name.toLowerCase()}. ${preset.description} ` +
      `It is designed for ${request.audience || 'general'} learners studying ${request.domain}.`;
  }

  private generateNarration(preset: SimulationPreset, request: AutoSimulationRequest): string {
    const manifest = preset.manifest;
    let narration = `Welcome to ${manifest.title}. `;
    
    if (manifest.steps && manifest.steps.length > 0) {
      narration += `This simulation has ${manifest.steps.length} steps. `;
      narration += `First, ${manifest.steps[0].description.toLowerCase()}. `;
      
      if (manifest.steps.length > 1) {
        narration += `Then, ${manifest.steps[1].description.toLowerCase()}. `;
      }
      
      narration += `Watch how the process unfolds.`;
    }
    
    return narration;
  }

  private calculateConfidence(
    request: AutoSimulationRequest,
    preset: SimulationPreset
  ): number {
    let confidence = 0.6;
    
    if (request.domain === preset.domain) confidence += 0.2;
    if (request.description && this.descriptionMatchesPreset(request.description, preset)) {
      confidence += 0.15;
    }
    if (request.learningObjective) confidence += 0.1;
    
    return Math.min(confidence, 0.95);
  }

  private convertToTemplate(manifest: SimulationManifest): SimulationTemplate {
    return {
      id: manifest.id || `template-${Date.now()}`,
      name: manifest.title || 'Generated Simulation',
      description: manifest.description || 'Auto-generated simulation',
      domain: (manifest.type as any) || 'physics',
      difficulty: 'intermediate',
      entities: (manifest.entities || []).map((e) => ({
        id: e.id,
        type: (e.type as any) || 'dynamic-body',
        x: e.x,
        y: e.y,
        properties: e.properties || {},
        appearance: e.appearance || {},
      })),
      config: { gravity: 9.8, timeScale: 1, paused: false },
      parameters: this.inferParameters(manifest),
      learningObjectives: ['Understand core concepts', 'Observe system behavior'],
    };
  }

  private matchesDifficulty(preset: SimulationPreset, difficulty: string): boolean {
    // Simple heuristic based on number of entities and steps
    const entityCount = preset.manifest.entities?.length || 0;
    const stepCount = preset.manifest.steps?.length || 0;
    
    const complexity = entityCount + stepCount;
    
    if (difficulty === 'beginner') return complexity < 5;
    if (difficulty === 'intermediate') return complexity >= 5 && complexity < 10;
    if (difficulty === 'advanced') return complexity >= 10;
    
    return true;
  }

  private extractKeywords(description: string): string[] {
    const stopWords = ['the', 'a', 'an', 'and', 'or', 'in', 'on', 'at', 'to', 'for', 'of'];
    return description
      .toLowerCase()
      .split(/\s+/)
      .filter((word) => word.length > 3 && !stopWords.includes(word));
  }

  private scorePresetByKeywords(preset: SimulationPreset, keywords: string[]): number {
    const text = `${preset.name} ${preset.description}`.toLowerCase();
    return keywords.filter((kw) => text.includes(kw)).length;
  }

  private createGenericEntity(index: number): SimEntityBase {
    return {
      id: `entity-${index}`,
      type: 'dynamic-body',
      x: 100 + Math.random() * 600,
      y: 100 + Math.random() * 400,
      properties: { radius: 20, mass: 1 },
      appearance: { fillColor: '#4ecdc4' },
    };
  }

  private extractConcepts(
    preset: SimulationPreset,
    request: AutoSimulationRequest
  ): string[] {
    const concepts: string[] = [];
    
    // Add from preset
    concepts.push(preset.name);
    
    // Add from domain
    if (request.domain === 'physics') {
      concepts.push('mechanics', 'motion', 'forces');
    } else if (request.domain === 'chemistry') {
      concepts.push('reactions', 'molecules', 'bonding');
    } else if (request.domain === 'biology') {
      concepts.push('cells', 'processes', 'systems');
    } else if (request.domain === 'cs') {
      concepts.push('algorithms', 'data structures', 'complexity');
    }
    
    return [...new Set(concepts)];
  }

  private inferPrerequisites(concepts: string[]): string[] {
    const prerequisites: string[] = [];
    
    concepts.forEach((concept) => {
      const lower = concept.toLowerCase();
      if (lower.includes('newton')) {
        prerequisites.push('basic mechanics', 'vectors');
      } else if (lower.includes('algorithm')) {
        prerequisites.push('programming basics', 'time complexity');
      } else if (lower.includes('reaction')) {
        prerequisites.push('atomic structure', 'chemical bonds');
      }
    });
    
    return [...new Set(prerequisites)];
  }

  private generateQuestions(concepts: string[]): string[] {
    return [
      `What would happen if we changed a parameter in this ${concepts[0]} simulation?`,
      `How does this ${concepts[0]} relate to real-world applications?`,
      `What are the limitations of this model?`,
    ];
  }

  private getMisconceptions(concepts: string[]): string[] {
    const misconceptions: string[] = [];
    
    concepts.forEach((concept) => {
      const lower = concept.toLowerCase();
      if (lower.includes('motion')) {
        misconceptions.push('Objects need constant force to maintain motion');
      } else if (lower.includes('equilibrium')) {
        misconceptions.push('Equilibrium means nothing is happening');
      } else if (lower.includes('energy')) {
        misconceptions.push('Energy can be created or destroyed');
      }
    });
    
    return misconceptions;
  }

  private descriptionMatchesPreset(description: string, preset: SimulationPreset): boolean {
    const keywords = this.extractKeywords(description);
    const presetText = `${preset.name} ${preset.description}`.toLowerCase();
    return keywords.some((kw) => presetText.includes(kw));
  }

  private inferParameters(manifest: SimulationManifest): SimulationParameter[] {
    const params: SimulationParameter[] = [];
    
    // Infer from entity properties
    manifest.entities?.forEach((entity) => {
      if (entity.properties?.mass !== undefined) {
        params.push({
          id: `${entity.id}-mass`,
          name: `${entity.id} Mass`,
          type: 'slider',
          defaultValue: entity.properties.mass,
          min: 0.1,
          max: 10,
          step: 0.1,
          description: `Mass of ${entity.id}`,
        });
      }
    });
    
    return params;
  }
}

// =============================================================================
// Export singleton
// =============================================================================

export const autoSimulationService = new AutoSimulationService();
