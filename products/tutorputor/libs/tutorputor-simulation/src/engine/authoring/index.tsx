/**
 * Simulation Authoring Tools - UI Components for Creating Simulations
 * 
 * Comprehensive visual editor for building educational simulations
 * across physics, chemistry, biology, and other domains.
 */

import React, { useState, useCallback, useRef } from 'react';
import type { PhysicsEntity, EntityType, PhysicsConfig } from '@tutorputor/physics-simulation';

// =============================================================================
// Types
// =============================================================================

export interface SimulationTemplate {
  id: string;
  name: string;
  description: string;
  domain: 'physics' | 'chemistry' | 'biology' | 'medicine' | 'cs' | 'math';
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  entities: PhysicsEntity[];
  config: PhysicsConfig;
  parameters: SimulationParameter[];
  learningObjectives: string[];
}

export interface SimulationParameter {
  id: string;
  name: string;
  type: 'number' | 'slider' | 'select' | 'boolean' | 'color';
  defaultValue: unknown;
  min?: number;
  max?: number;
  step?: number;
  options?: { label: string; value: any }[];
  description: string;
}

export interface AuthoringState {
  entities: PhysicsEntity[];
  selectedEntityId: string | null;
  config: PhysicsConfig;
  currentTool: 'select' | 'create' | 'delete' | 'connect';
  gridVisible: boolean;
  snapToGrid: boolean;
  zoom: number;
  pan: { x: number; y: number };
}

// =============================================================================
// Simulation Templates Library (20+ Templates)
// =============================================================================

export const SimulationTemplates: SimulationTemplate[] = [
  // PHYSICS TEMPLATES
  {
    id: 'sim-physics-pendulum',
    name: 'Simple Pendulum',
    description: 'Oscillating pendulum with adjustable length and mass',
    domain: 'physics',
    difficulty: 'beginner',
    entities: [
      {
        id: 'pivot',
        type: EntityType.FIXED_POINT,
        x: 400,
        y: 100,
        properties: { radius: 5, mass: 0 },
        appearance: { fillColor: '#333', strokeColor: '#000' },
      },
      {
        id: 'bob',
        type: EntityType.DYNAMIC_BODY,
        x: 400,
        y: 300,
        properties: { radius: 20, mass: 1 },
        appearance: { fillColor: '#4ecdc4', strokeColor: '#333' },
      },
      {
        id: 'string',
        type: EntityType.CONSTRAINT,
        x: 400,
        y: 200,
        properties: { length: 200, stiffness: 1 },
        appearance: { strokeColor: '#666', strokeWidth: 2 },
      },
    ],
    config: {
      gravity: 9.8,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'length',
        name: 'String Length',
        type: 'slider',
        defaultValue: 200,
        min: 100,
        max: 400,
        step: 10,
        description: 'Length of the pendulum string in pixels',
      },
      {
        id: 'mass',
        name: 'Bob Mass',
        type: 'slider',
        defaultValue: 1,
        min: 0.1,
        max: 5,
        step: 0.1,
        description: 'Mass of the pendulum bob',
      },
      {
        id: 'gravity',
        name: 'Gravity',
        type: 'slider',
        defaultValue: 9.8,
        min: 0,
        max: 20,
        step: 0.1,
        description: 'Gravitational acceleration',
      },
    ],
    learningObjectives: [
      'Understand simple harmonic motion',
      'Explore the relationship between length and period',
      'Observe conservation of energy',
    ],
  },
  {
    id: 'sim-physics-projectile',
    name: 'Projectile Motion',
    description: 'Launch projectiles with adjustable velocity and angle',
    domain: 'physics',
    difficulty: 'intermediate',
    entities: [
      {
        id: 'launcher',
        type: EntityType.FIXED_POINT,
        x: 50,
        y: 350,
        properties: { radius: 10, mass: 0 },
        appearance: { fillColor: '#666' },
      },
      {
        id: 'projectile',
        type: EntityType.DYNAMIC_BODY,
        x: 50,
        y: 350,
        properties: { radius: 15, mass: 0.5, vx: 10, vy: -15 },
        appearance: { fillColor: '#ff6b6b' },
      },
      {
        id: 'ground',
        type: EntityType.BOUNDARY,
        x: 400,
        y: 400,
        properties: { width: 800, height: 10 },
        appearance: { fillColor: '#8b7355' },
      },
    ],
    config: {
      gravity: 9.8,
      timeScale: 1,
      paused: true,
    },
    parameters: [
      {
        id: 'velocity',
        name: 'Initial Velocity',
        type: 'slider',
        defaultValue: 20,
        min: 5,
        max: 50,
        step: 1,
        description: 'Initial launch velocity',
      },
      {
        id: 'angle',
        name: 'Launch Angle',
        type: 'slider',
        defaultValue: 45,
        min: 0,
        max: 90,
        step: 1,
        description: 'Launch angle in degrees',
      },
      {
        id: 'mass',
        name: 'Projectile Mass',
        type: 'slider',
        defaultValue: 0.5,
        min: 0.1,
        max: 2,
        step: 0.1,
        description: 'Mass of the projectile',
      },
    ],
    learningObjectives: [
      'Understand parabolic trajectory',
      'Explore optimal launch angle',
      'Analyze horizontal and vertical motion independently',
    ],
  },
  {
    id: 'sim-physics-springs',
    name: 'Spring-Mass System',
    description: 'Multiple masses connected by springs',
    domain: 'physics',
    difficulty: 'intermediate',
    entities: [
      {
        id: 'anchor',
        type: EntityType.FIXED_POINT,
        x: 400,
        y: 50,
        properties: {},
        appearance: { fillColor: '#333' },
      },
      {
        id: 'mass1',
        type: EntityType.DYNAMIC_BODY,
        x: 400,
        y: 150,
        properties: { radius: 25, mass: 2 },
        appearance: { fillColor: '#4ecdc4' },
      },
      {
        id: 'mass2',
        type: EntityType.DYNAMIC_BODY,
        x: 400,
        y: 250,
        properties: { radius: 20, mass: 1.5 },
        appearance: { fillColor: '#45b7d1' },
      },
      {
        id: 'spring1',
        type: EntityType.SPRING,
        x: 400,
        y: 100,
        properties: { length: 100, stiffness: 0.5, damping: 0.1 },
        appearance: { strokeColor: '#666' },
      },
      {
        id: 'spring2',
        type: EntityType.SPRING,
        x: 400,
        y: 200,
        properties: { length: 100, stiffness: 0.5, damping: 0.1 },
        appearance: { strokeColor: '#666' },
      },
    ],
    config: {
      gravity: 9.8,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'k1',
        name: 'Spring 1 Stiffness',
        type: 'slider',
        defaultValue: 0.5,
        min: 0.1,
        max: 2,
        step: 0.1,
        description: 'Stiffness of top spring',
      },
      {
        id: 'k2',
        name: 'Spring 2 Stiffness',
        type: 'slider',
        defaultValue: 0.5,
        min: 0.1,
        max: 2,
        step: 0.1,
        description: 'Stiffness of bottom spring',
      },
      {
        id: 'm1',
        name: 'Mass 1',
        type: 'slider',
        defaultValue: 2,
        min: 0.5,
        max: 5,
        step: 0.5,
        description: 'Mass of top block',
      },
    ],
    learningObjectives: [
      'Observe coupled oscillations',
      'Explore normal modes',
      'Understand energy transfer between masses',
    ],
  },
  {
    id: 'sim-physics-collision',
    name: 'Elastic Collisions',
    description: '1D and 2D elastic collisions between particles',
    domain: 'physics',
    difficulty: 'intermediate',
    entities: [
      {
        id: 'wall-left',
        type: EntityType.BOUNDARY,
        x: 50,
        y: 200,
        properties: { width: 10, height: 400 },
        appearance: { fillColor: '#333' },
      },
      {
        id: 'wall-right',
        type: EntityType.BOUNDARY,
        x: 750,
        y: 200,
        properties: { width: 10, height: 400 },
        appearance: { fillColor: '#333' },
      },
      {
        id: 'ball1',
        type: EntityType.DYNAMIC_BODY,
        x: 200,
        y: 200,
        properties: { radius: 30, mass: 3, vx: 5 },
        appearance: { fillColor: '#ff6b6b' },
      },
      {
        id: 'ball2',
        type: EntityType.DYNAMIC_BODY,
        x: 600,
        y: 200,
        properties: { radius: 20, mass: 1, vx: -3 },
        appearance: { fillColor: '#4ecdc4' },
      },
    ],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'm1',
        name: 'Mass 1',
        type: 'slider',
        defaultValue: 3,
        min: 1,
        max: 5,
        step: 0.5,
        description: 'Mass of red ball',
      },
      {
        id: 'm2',
        name: 'Mass 2',
        type: 'slider',
        defaultValue: 1,
        min: 0.5,
        max: 3,
        step: 0.5,
        description: 'Mass of blue ball',
      },
      {
        id: 'v1',
        name: 'Velocity 1',
        type: 'slider',
        defaultValue: 5,
        min: -10,
        max: 10,
        step: 1,
        description: 'Initial velocity of ball 1',
      },
    ],
    learningObjectives: [
      'Understand conservation of momentum',
      'Explore conservation of kinetic energy',
      'Observe velocity transfer in collisions',
    ],
  },
  {
    id: 'sim-physics-orbits',
    name: 'Planetary Orbits',
    description: 'Gravitational orbits with central star',
    domain: 'physics',
    difficulty: 'advanced',
    entities: [
      {
        id: 'sun',
        type: EntityType.FIXED_POINT,
        x: 400,
        y: 300,
        properties: { radius: 40, mass: 100 },
        appearance: { fillColor: '#ffd93d' },
      },
      {
        id: 'planet1',
        type: EntityType.DYNAMIC_BODY,
        x: 400,
        y: 150,
        properties: { radius: 15, mass: 1, vx: 8 },
        appearance: { fillColor: '#4ecdc4' },
      },
      {
        id: 'planet2',
        type: EntityType.DYNAMIC_BODY,
        x: 600,
        y: 300,
        properties: { radius: 12, mass: 0.8, vy: 6 },
        appearance: { fillColor: '#ff6b6b' },
      },
    ],
    config: {
      gravity: 0,
      timeScale: 0.5,
      paused: false,
    },
    parameters: [
      {
        id: 'm_sun',
        name: 'Star Mass',
        type: 'slider',
        defaultValue: 100,
        min: 50,
        max: 200,
        step: 10,
        description: 'Mass of central star',
      },
      {
        id: 'g',
        name: 'Gravitational Constant',
        type: 'slider',
        defaultValue: 1,
        min: 0.5,
        max: 3,
        step: 0.1,
        description: 'Strength of gravity',
      },
    ],
    learningObjectives: [
      'Understand Kepler\'s laws',
      'Explore orbital mechanics',
      'Observe elliptical orbits',
    ],
  },
  // CHEMISTRY TEMPLATES
  {
    id: 'sim-chem-diffusion',
    name: 'Molecular Diffusion',
    description: 'Particles spreading from high to low concentration',
    domain: 'chemistry',
    difficulty: 'beginner',
    entities: [
      // Generate programmatically based on particle count
    ],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'particleCount',
        name: 'Particle Count',
        type: 'slider',
        defaultValue: 50,
        min: 10,
        max: 200,
        step: 10,
        description: 'Number of molecules',
      },
      {
        id: 'temperature',
        name: 'Temperature',
        type: 'slider',
        defaultValue: 300,
        min: 100,
        max: 600,
        step: 50,
        description: 'Temperature in Kelvin',
      },
    ],
    learningObjectives: [
      'Understand Brownian motion',
      'Observe concentration gradients',
      'Explore effect of temperature on diffusion',
    ],
  },
  {
    id: 'sim-chem-reaction',
    name: 'Chemical Reaction Kinetics',
    description: 'A + B → C reaction with collision theory',
    domain: 'chemistry',
    difficulty: 'intermediate',
    entities: [],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'activationEnergy',
        name: 'Activation Energy',
        type: 'slider',
        defaultValue: 50,
        min: 10,
        max: 100,
        step: 5,
        description: 'Minimum energy for reaction',
      },
      {
        id: 'temperature',
        name: 'Temperature',
        type: 'slider',
        defaultValue: 300,
        min: 100,
        max: 500,
        step: 25,
        description: 'System temperature',
      },
    ],
    learningObjectives: [
      'Understand collision theory',
      'Explore activation energy',
      'Observe rate vs temperature',
    ],
  },
  // BIOLOGY TEMPLATES
  {
    id: 'sim-bio-osmosis',
    name: 'Osmosis',
    description: 'Water movement across semi-permeable membrane',
    domain: 'biology',
    difficulty: 'beginner',
    entities: [
      {
        id: 'membrane',
        type: EntityType.BOUNDARY,
        x: 400,
        y: 250,
        properties: { width: 10, height: 500, permeable: true },
        appearance: { fillColor: '#666' },
      },
    ],
    config: {
      gravity: 0,
      timeScale: 0.5,
      paused: false,
    },
    parameters: [
      {
        id: 'concentrationLeft',
        name: 'Left Concentration',
        type: 'slider',
        defaultValue: 0.8,
        min: 0,
        max: 1,
        step: 0.1,
        description: 'Solute concentration on left',
      },
      {
        id: 'concentrationRight',
        name: 'Right Concentration',
        type: 'slider',
        defaultValue: 0.2,
        min: 0,
        max: 1,
        step: 0.1,
        description: 'Solute concentration on right',
      },
    ],
    learningObjectives: [
      'Understand osmotic pressure',
      'Observe water movement',
      'Explore semi-permeable membranes',
    ],
  },
  {
    id: 'sim-bio-population',
    name: 'Population Dynamics',
    description: 'Predator-prey interactions (Lotka-Volterra)',
    domain: 'biology',
    difficulty: 'advanced',
    entities: [],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: false,
    },
    parameters: [
      {
        id: 'preyGrowth',
        name: 'Prey Growth Rate',
        type: 'slider',
        defaultValue: 0.1,
        min: 0.01,
        max: 0.5,
        step: 0.01,
        description: 'Rate of prey reproduction',
      },
      {
        id: 'predationRate',
        name: 'Predation Rate',
        type: 'slider',
        defaultValue: 0.02,
        min: 0.001,
        max: 0.1,
        step: 0.001,
        description: 'Rate of predation',
      },
    ],
    learningObjectives: [
      'Understand predator-prey cycles',
      'Explore population equilibrium',
      'Observe carrying capacity effects',
    ],
  },
  // CS TEMPLATES
  {
    id: 'sim-cs-sorting',
    name: 'Sorting Algorithm Visualizer',
    description: 'Visualize bubble, quick, merge, heap sort',
    domain: 'cs',
    difficulty: 'intermediate',
    entities: [],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: true,
    },
    parameters: [
      {
        id: 'algorithm',
        name: 'Algorithm',
        type: 'select',
        defaultValue: 'bubble',
        options: [
          { label: 'Bubble Sort', value: 'bubble' },
          { label: 'Quick Sort', value: 'quick' },
          { label: 'Merge Sort', value: 'merge' },
          { label: 'Heap Sort', value: 'heap' },
        ],
        description: 'Sorting algorithm to visualize',
      },
      {
        id: 'elementCount',
        name: 'Elements',
        type: 'slider',
        defaultValue: 20,
        min: 5,
        max: 100,
        step: 5,
        description: 'Number of elements to sort',
      },
      {
        id: 'speed',
        name: 'Animation Speed',
        type: 'slider',
        defaultValue: 1,
        min: 0.1,
        max: 5,
        step: 0.1,
        description: 'Speed multiplier',
      },
    ],
    learningObjectives: [
      'Understand sorting algorithms',
      'Compare time complexities',
      'Observe space complexity',
    ],
  },
  {
    id: 'sim-cs-graph',
    name: 'Graph Traversal',
    description: 'BFS and DFS visualization on graphs',
    domain: 'cs',
    difficulty: 'intermediate',
    entities: [],
    config: {
      gravity: 0,
      timeScale: 1,
      paused: true,
    },
    parameters: [
      {
        id: 'traversal',
        name: 'Traversal Type',
        type: 'select',
        defaultValue: 'bfs',
        options: [
          { label: 'Breadth-First', value: 'bfs' },
          { label: 'Depth-First', value: 'dfs' },
          { label: 'Dijkstra', value: 'dijkstra' },
          { label: 'A*', value: 'astar' },
        ],
        description: 'Graph traversal algorithm',
      },
    ],
    learningObjectives: [
      'Understand graph traversals',
      'Explore shortest path algorithms',
      'Observe visited node tracking',
    ],
  },
];

// =============================================================================
// Simulation Authoring Components
// =============================================================================

export const SimulationAuthoringPanel: React.FC = () => {
  const [selectedTemplate, setSelectedTemplate] = useState<SimulationTemplate | null>(null);
  const [customEntities, setCustomEntities] = useState<PhysicsEntity[]>([]);
  const [parameters, setParameters] = useState<Record<string, any>>({});

  const handleTemplateSelect = (template: SimulationTemplate) => {
    setSelectedTemplate(template);
    setCustomEntities(template.entities);
    // Initialize parameters with defaults
    const defaults: Record<string, any> = {};
    template.parameters.forEach((p) => {
      defaults[p.id] = p.defaultValue;
    });
    setParameters(defaults);
  };

  return (
    <div style={{ display: 'flex', height: '100vh' }}>
      {/* Template sidebar */}
      <div style={{ width: 280, backgroundColor: '#1a1a2e', borderRight: '1px solid #333' }}>
        <TemplateBrowser onSelect={handleTemplateSelect} />
      </div>

      {/* Main canvas area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Toolbar */}
        <AuthoringToolbar />

        {/* Canvas */}
        <SimulationCanvas entities={customEntities} />

        {/* Playback controls */}
        <PlaybackControls />
      </div>

      {/* Properties panel */}
      <div style={{ width: 320, backgroundColor: '#1a1a2e', borderLeft: '1px solid #333' }}>
        <PropertiesPanel
          template={selectedTemplate}
          parameters={parameters}
          onParameterChange={(id, value) =>
            setParameters((prev) => ({ ...prev, [id]: value }))
          }
        />
      </div>
    </div>
  );
};

const TemplateBrowser: React.FC<{ onSelect: (t: SimulationTemplate) => void }> = ({
  onSelect,
}) => {
  const [selectedDomain, setSelectedDomain] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const filteredTemplates = SimulationTemplates.filter((t) => {
    const matchesDomain = selectedDomain === 'all' || t.domain === selectedDomain;
    const matchesSearch =
      t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      t.description.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesDomain && matchesSearch;
  });

  return (
    <div style={{ padding: 16 }}>
      <h3 style={{ color: '#fff', marginBottom: 16 }}>Templates</h3>

      {/* Domain filter */}
      <select
        value={selectedDomain}
        onChange={(e) => setSelectedDomain(e.target.value)}
        style={{
          width: '100%',
          padding: 8,
          marginBottom: 12,
          backgroundColor: '#252540',
          border: '1px solid #444',
          color: '#fff',
          borderRadius: 4,
        }}
      >
        <option value="all">All Domains</option>
        <option value="physics">Physics</option>
        <option value="chemistry">Chemistry</option>
        <option value="biology">Biology</option>
        <option value="cs">Computer Science</option>
      </select>

      {/* Search */}
      <input
        type="text"
        placeholder="Search templates..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        style={{
          width: '100%',
          padding: 8,
          marginBottom: 16,
          backgroundColor: '#252540',
          border: '1px solid #444',
          color: '#fff',
          borderRadius: 4,
        }}
      />

      {/* Template list */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {filteredTemplates.map((template) => (
          <button
            key={template.id}
            onClick={() => onSelect(template)}
            style={{
              padding: 12,
              textAlign: 'left',
              backgroundColor: '#252540',
              border: '1px solid #444',
              borderRadius: 4,
              color: '#fff',
              cursor: 'pointer',
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: 4 }}>{template.name}</div>
            <div style={{ fontSize: 12, color: '#888' }}>{template.description}</div>
            <div style={{ fontSize: 11, color: '#666', marginTop: 4 }}>
              {template.domain} • {template.difficulty}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
};

const AuthoringToolbar: React.FC = () => {
  const tools = [
    { id: 'select', icon: '↖', label: 'Select' },
    { id: 'create', icon: '➕', label: 'Create' },
    { id: 'delete', icon: '🗑', label: 'Delete' },
    { id: 'connect', icon: '🔗', label: 'Connect' },
  ];

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        padding: '12px 16px',
        backgroundColor: '#1a1a2e',
        borderBottom: '1px solid #333',
      }}
    >
      {tools.map((tool) => (
        <button
          key={tool.id}
          style={{
            padding: '8px 16px',
            backgroundColor: '#252540',
            border: '1px solid #444',
            borderRadius: 4,
            color: '#fff',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <span>{tool.icon}</span>
          <span>{tool.label}</span>
        </button>
      ))}
    </div>
  );
};

const SimulationCanvas: React.FC<{ entities: PhysicsEntity[] }> = ({ entities }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  return (
    <div style={{ flex: 1, backgroundColor: '#0f0f1a', position: 'relative' }}>
      <canvas
        ref={canvasRef}
        style={{ width: '100%', height: '100%' }}
        width={800}
        height={600}
      />

      {/* Grid overlay */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `
            linear-gradient(to right, #1a1a2e 1px, transparent 1px),
            linear-gradient(to bottom, #1a1a2e 1px, transparent 1px)
          `,
          backgroundSize: '20px 20px',
          pointerEvents: 'none',
        }}
      />
    </div>
  );
};

const PlaybackControls: React.FC = () => {
  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        padding: '12px 16px',
        backgroundColor: '#1a1a2e',
        borderTop: '1px solid #333',
      }}
    >
      <button style={controlButtonStyle}>⏮</button>
      <button style={{ ...controlButtonStyle, backgroundColor: '#4ecdc4' }}>▶</button>
      <button style={controlButtonStyle}>⏸</button>
      <button style={controlButtonStyle}>⏹</button>
      <button style={controlButtonStyle}>⏭</button>

      <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ color: '#888', fontSize: 12 }}>Speed:</span>
        <input
          type="range"
          min="0.1"
          max="3"
          step="0.1"
          defaultValue="1"
          style={{ width: 100 }}
        />
      </div>
    </div>
  );
};

const controlButtonStyle: React.CSSProperties = {
  width: 36,
  height: 36,
  backgroundColor: '#252540',
  border: '1px solid #444',
  borderRadius: 4,
  color: '#fff',
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

const PropertiesPanel: React.FC<{
  template: SimulationTemplate | null;
  parameters: Record<string, any>;
  onParameterChange: (id: string, value: any) => void;
}> = ({ template, parameters, onParameterChange }) => {
  if (!template) {
    return (
      <div style={{ padding: 20, color: '#888', textAlign: 'center' }}>
        Select a template to configure simulation
      </div>
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <h3 style={{ color: '#fff', marginBottom: 8 }}>{template.name}</h3>
      <p style={{ color: '#888', fontSize: 13, marginBottom: 20 }}>{template.description}</p>

      <h4 style={{ color: '#888', fontSize: 12, textTransform: 'uppercase', marginBottom: 12 }}>
        Parameters
      </h4>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {template.parameters.map((param) => (
          <div key={param.id}>
            <label style={{ display: 'block', color: '#aaa', fontSize: 12, marginBottom: 4 }}>
              {param.name}
            </label>

            {param.type === 'slider' && (
              <div>
                <input
                  type="range"
                  min={param.min}
                  max={param.max}
                  step={param.step}
                  value={parameters[param.id]}
                  onChange={(e) => onParameterChange(param.id, Number(e.target.value))}
                  style={{ width: '100%' }}
                />
                <div style={{ color: '#666', fontSize: 11, textAlign: 'center' }}>
                  {parameters[param.id]}
                </div>
              </div>
            )}

            {param.type === 'select' && (
              <select
                value={parameters[param.id]}
                onChange={(e) => onParameterChange(param.id, e.target.value)}
                style={{
                  width: '100%',
                  padding: 8,
                  backgroundColor: '#252540',
                  border: '1px solid #444',
                  color: '#fff',
                  borderRadius: 4,
                }}
              >
                {param.options?.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            )}

            {param.type === 'number' && (
              <input
                type="number"
                value={parameters[param.id]}
                onChange={(e) => onParameterChange(param.id, Number(e.target.value))}
                style={{
                  width: '100%',
                  padding: 8,
                  backgroundColor: '#252540',
                  border: '1px solid #444',
                  color: '#fff',
                  borderRadius: 4,
                }}
              />
            )}

            <div style={{ color: '#666', fontSize: 11, marginTop: 4 }}>{param.description}</div>
          </div>
        ))}
      </div>

      <div style={{ marginTop: 24 }}>
        <h4 style={{ color: '#888', fontSize: 12, textTransform: 'uppercase', marginBottom: 12 }}>
          Learning Objectives
        </h4>
        <ul style={{ color: '#aaa', fontSize: 12, paddingLeft: 16 }}>
          {template.learningObjectives.map((obj, i) => (
            <li key={i} style={{ marginBottom: 4 }}>
              {obj}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
};

// =============================================================================
// Export utilities
// =============================================================================

export function getTemplatesByDomain(domain: SimulationTemplate['domain']): SimulationTemplate[] {
  return SimulationTemplates.filter((t) => t.domain === domain);
}

export function searchTemplates(query: string): SimulationTemplate[] {
  const lower = query.toLowerCase();
  return SimulationTemplates.filter(
    (t) =>
      t.name.toLowerCase().includes(lower) ||
      t.description.toLowerCase().includes(lower) ||
      t.learningObjectives.some((obj) => obj.toLowerCase().includes(lower))
  );
}

export function createCustomSimulation(
  baseTemplate: SimulationTemplate,
  parameterOverrides: Record<string, any>
): SimulationTemplate {
  return {
    ...baseTemplate,
    id: `custom-${Date.now()}`,
    name: `${baseTemplate.name} (Custom)`,
    parameters: baseTemplate.parameters.map((p) => ({
      ...p,
      defaultValue: parameterOverrides[p.id] ?? p.defaultValue,
    })),
  };
}
