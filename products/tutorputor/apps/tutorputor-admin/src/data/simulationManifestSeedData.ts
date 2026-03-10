/**
 * Seed Data for Simulation Manifests
 *
 * Mock simulation definitions that link to Learning Units.
 * Used for development and demonstration of the Simulation Builder.
 *
 * @doc.type module
 * @doc.purpose Seed data for SimulationManifests
 * @doc.layer data
 * @doc.pattern Seed
 */

// ============================================================================
// Types (inline until contracts export is verified)
// ============================================================================

export interface SimulationParameter {
  id: string;
  name: string;
  type: "number" | "boolean" | "enum";
  default: number | boolean | string;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  enumValues?: string[];
  label: string;
  description?: string;
}

export interface SimulationObservable {
  id: string;
  name: string;
  type: "number" | "boolean" | "string" | "vector2d";
  unit?: string;
  label: string;
  description?: string;
}

export interface SimulationEntity {
  id: string;
  type: string;
  name: string;
  position: { x: number; y: number };
  properties: Record<string, unknown>;
  visual?: {
    shape?: "circle" | "rectangle" | "sprite";
    color?: string;
    size?: { width: number; height: number };
    spriteUrl?: string;
  };
}

export interface SimulationConstraint {
  id: string;
  type: "gravity" | "collision" | "spring" | "air_resistance" | "fixed_point";
  entities: string[];
  properties?: Record<string, unknown>;
}

export interface SimulationSuccessCriteria {
  type: "parameter_match" | "time_limit" | "event_sequence" | "state_reached";
  target?: number;
  tolerance?: number;
  timeLimit?: number;
  events?: string[];
}

export interface SimulationBlueprintSeed {
  id: string;
  name: string;
  description: string;
  domain: string;
  version: number;
  status: "draft" | "testing" | "published";

  // Links to Learning Units
  linkedLearningUnits: string[];
  claimRefs?: string[];

  // Canvas configuration
  canvas: {
    width: number;
    height: number;
    background?: string;
    gridEnabled?: boolean;
    coordinateSystem?: "cartesian" | "screen";
  };

  // Entities in the simulation
  entities: SimulationEntity[];

  // Physics/Logic constraints
  constraints: SimulationConstraint[];

  // User-controllable parameters
  parameters: SimulationParameter[];

  // Measurable observables
  observables: SimulationObservable[];

  // Success criteria for tasks
  successCriteria?: SimulationSuccessCriteria[];

  // Scaffolding and hints
  scaffolds?: {
    hints: string[];
    unlockAfterAttempts: number;
  };

  // Metadata
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  tenantId: string;

  // Preview
  thumbnailUrl?: string;
  tags?: string[];
}

// ============================================================================
// Physics Simulations
// ============================================================================

export const fallingObjectsVacuumSim: SimulationManifest = {
  id: "sim-falling-objects-vacuum",
  name: "Falling Objects in Vacuum",
  description:
    "Drop objects of different masses in a vacuum or with adjustable air resistance to explore gravity and free fall.",
  domain: "physics",
  version: 1,
  status: "published",

  linkedLearningUnits: ["lu-physics-free-fall-001"],
  claimRefs: ["C1", "C2"],

  canvas: {
    width: 800,
    height: 600,
    background: "#1a1a2e",
    gridEnabled: false,
    coordinateSystem: "cartesian",
  },

  entities: [
    {
      id: "bowling-ball",
      type: "rigid_body",
      name: "Bowling Ball",
      position: { x: 200, y: 500 },
      properties: {
        mass: 5,
        radius: 30,
        dragCoefficient: 0.47,
      },
      visual: {
        shape: "circle",
        color: "#3b82f6",
        size: { width: 60, height: 60 },
      },
    },
    {
      id: "feather",
      type: "rigid_body",
      name: "Feather",
      position: { x: 400, y: 500 },
      properties: {
        mass: 0.01,
        radius: 10,
        dragCoefficient: 1.2,
        surfaceArea: 0.05,
      },
      visual: {
        shape: "sprite",
        spriteUrl: "/assets/feather.svg",
        size: { width: 40, height: 60 },
      },
    },
    {
      id: "ground",
      type: "static_body",
      name: "Ground",
      position: { x: 400, y: 50 },
      properties: {
        width: 700,
        height: 20,
      },
      visual: {
        shape: "rectangle",
        color: "#4b5563",
        size: { width: 700, height: 20 },
      },
    },
  ],

  constraints: [
    {
      id: "gravity",
      type: "gravity",
      entities: ["bowling-ball", "feather"],
      properties: {
        acceleration: 9.81,
        direction: { x: 0, y: -1 },
      },
    },
    {
      id: "air-resistance",
      type: "air_resistance",
      entities: ["bowling-ball", "feather"],
      properties: {
        enabled: true,
        densityMultiplier: 1.0,
      },
    },
    {
      id: "floor-collision",
      type: "collision",
      entities: ["bowling-ball", "feather", "ground"],
      properties: {
        restitution: 0.3,
      },
    },
  ],

  parameters: [
    {
      id: "air-resistance-slider",
      name: "airResistance",
      type: "number",
      default: 1.0,
      min: 0,
      max: 1,
      step: 0.01,
      label: "Air Resistance",
      description: "Adjust from 0 (vacuum) to 1 (normal air)",
      unit: "normalized",
    },
    {
      id: "drop-height",
      name: "dropHeight",
      type: "number",
      default: 10,
      min: 5,
      max: 50,
      step: 1,
      label: "Drop Height",
      unit: "m",
    },
  ],

  observables: [
    {
      id: "bowling-ball-velocity",
      name: "bowlingBallVelocity",
      type: "number",
      unit: "m/s",
      label: "Bowling Ball Velocity",
    },
    {
      id: "feather-velocity",
      name: "featherVelocity",
      type: "number",
      unit: "m/s",
      label: "Feather Velocity",
    },
    {
      id: "time-difference",
      name: "timeDifference",
      type: "number",
      unit: "s",
      label: "Landing Time Difference",
    },
  ],

  successCriteria: [
    {
      type: "parameter_match",
      target: 0,
      tolerance: 0.25,
    },
  ],

  scaffolds: {
    hints: [
      "What happens to air resistance when you slide it all the way to the left?",
      "In a vacuum, there is no air to push against objects.",
      "Without air resistance, gravity affects all objects equally regardless of mass.",
    ],
    unlockAfterAttempts: 2,
  },

  createdAt: "2024-01-15T10:00:00Z",
  updatedAt: "2024-03-20T14:30:00Z",
  createdBy: "author-physics-001",
  tenantId: "tenant-default",

  thumbnailUrl: "/assets/thumbnails/falling-objects.png",
  tags: ["physics", "gravity", "free-fall", "air-resistance"],
};

export const projectileMotionSim: SimulationManifest = {
  id: "sim-projectile-motion",
  name: "Projectile Motion",
  description:
    "Launch projectiles at different angles and velocities to understand parabolic motion.",
  domain: "physics",
  version: 1,
  status: "published",

  linkedLearningUnits: ["lu-physics-projectile-001"],
  claimRefs: ["C1", "C3"],

  canvas: {
    width: 1000,
    height: 600,
    background: "#0f172a",
    gridEnabled: true,
    coordinateSystem: "cartesian",
  },

  entities: [
    {
      id: "launcher",
      type: "static_body",
      name: "Launcher",
      position: { x: 50, y: 100 },
      properties: {},
      visual: {
        shape: "sprite",
        spriteUrl: "/assets/launcher.svg",
        size: { width: 80, height: 40 },
      },
    },
    {
      id: "projectile",
      type: "rigid_body",
      name: "Ball",
      position: { x: 50, y: 100 },
      properties: {
        mass: 1,
        radius: 15,
      },
      visual: {
        shape: "circle",
        color: "#f97316",
        size: { width: 30, height: 30 },
      },
    },
    {
      id: "target",
      type: "sensor",
      name: "Target",
      position: { x: 500, y: 100 },
      properties: {
        width: 40,
        height: 80,
      },
      visual: {
        shape: "rectangle",
        color: "#22c55e",
        size: { width: 40, height: 80 },
      },
    },
    {
      id: "ground",
      type: "static_body",
      name: "Ground",
      position: { x: 500, y: 30 },
      properties: {
        width: 900,
        height: 20,
      },
      visual: {
        shape: "rectangle",
        color: "#4b5563",
        size: { width: 900, height: 20 },
      },
    },
  ],

  constraints: [
    {
      id: "gravity",
      type: "gravity",
      entities: ["projectile"],
      properties: {
        acceleration: 9.81,
        direction: { x: 0, y: -1 },
      },
    },
    {
      id: "ground-collision",
      type: "collision",
      entities: ["projectile", "ground"],
      properties: {
        restitution: 0.1,
      },
    },
  ],

  parameters: [
    {
      id: "launch-angle",
      name: "launchAngle",
      type: "number",
      default: 45,
      min: 0,
      max: 90,
      step: 1,
      label: "Launch Angle",
      unit: "°",
    },
    {
      id: "launch-velocity",
      name: "launchVelocity",
      type: "number",
      default: 20,
      min: 5,
      max: 50,
      step: 1,
      label: "Launch Velocity",
      unit: "m/s",
    },
    {
      id: "target-distance",
      name: "targetDistance",
      type: "number",
      default: 50,
      min: 20,
      max: 100,
      step: 5,
      label: "Target Distance",
      unit: "m",
    },
  ],

  observables: [
    {
      id: "max-height",
      name: "maxHeight",
      type: "number",
      unit: "m",
      label: "Maximum Height",
    },
    {
      id: "range",
      name: "range",
      type: "number",
      unit: "m",
      label: "Horizontal Range",
    },
    {
      id: "flight-time",
      name: "flightTime",
      type: "number",
      unit: "s",
      label: "Flight Time",
    },
    {
      id: "distance-to-target",
      name: "distanceToTarget",
      type: "number",
      unit: "m",
      label: "Distance to Target",
    },
  ],

  successCriteria: [
    {
      type: "parameter_match",
      target: 0,
      tolerance: 2.0,
    },
  ],

  scaffolds: {
    hints: [
      "Try adjusting the angle first, then fine-tune the velocity.",
      "45° gives the maximum range for a given velocity on flat ground.",
      "Higher angles give more height but less distance.",
    ],
    unlockAfterAttempts: 3,
  },

  createdAt: "2024-02-10T09:00:00Z",
  updatedAt: "2024-04-05T11:20:00Z",
  createdBy: "author-physics-001",
  tenantId: "tenant-default",

  thumbnailUrl: "/assets/thumbnails/projectile-motion.png",
  tags: ["physics", "kinematics", "projectile", "parabola"],
};

// ============================================================================
// Chemistry Simulations
// ============================================================================

export const enzymeKineticsSim: SimulationManifest = {
  id: "sim-enzyme-kinetics",
  name: "Enzyme Kinetics and Inhibition",
  description:
    "Explore Michaelis-Menten kinetics and different types of enzyme inhibition using Lineweaver-Burk plots.",
  domain: "chemistry",
  version: 1,
  status: "published",

  linkedLearningUnits: ["lu-chemistry-enzyme-001"],
  claimRefs: ["C1"],

  canvas: {
    width: 900,
    height: 600,
    background: "#1e1e2e",
    gridEnabled: true,
    coordinateSystem: "cartesian",
  },

  entities: [
    {
      id: "enzyme",
      type: "molecule",
      name: "Enzyme",
      position: { x: 200, y: 300 },
      properties: {
        Vmax: 100,
        Km: 10,
      },
      visual: {
        shape: "sprite",
        spriteUrl: "/assets/enzyme.svg",
        size: { width: 80, height: 80 },
      },
    },
    {
      id: "substrate",
      type: "molecule",
      name: "Substrate",
      position: { x: 350, y: 300 },
      properties: {
        concentration: 50,
      },
      visual: {
        shape: "circle",
        color: "#6366f1",
        size: { width: 30, height: 30 },
      },
    },
    {
      id: "inhibitor",
      type: "molecule",
      name: "Inhibitor",
      position: { x: 500, y: 300 },
      properties: {
        type: "competitive",
        Ki: 5,
        concentration: 0,
      },
      visual: {
        shape: "circle",
        color: "#ef4444",
        size: { width: 25, height: 25 },
      },
    },
    {
      id: "plot-area",
      type: "graph",
      name: "Lineweaver-Burk Plot",
      position: { x: 650, y: 300 },
      properties: {
        xAxis: "1/[S]",
        yAxis: "1/V",
      },
      visual: {
        shape: "rectangle",
        color: "#1f2937",
        size: { width: 400, height: 300 },
      },
    },
  ],

  constraints: [],

  parameters: [
    {
      id: "inhibitor-type",
      name: "inhibitorType",
      type: "enum",
      default: "none",
      enumValues: ["none", "competitive", "non_competitive", "uncompetitive"],
      label: "Inhibitor Type",
    },
    {
      id: "inhibitor-concentration",
      name: "inhibitorConcentration",
      type: "number",
      default: 0,
      min: 0,
      max: 50,
      step: 1,
      label: "Inhibitor Concentration",
      unit: "µM",
    },
    {
      id: "substrate-range",
      name: "substrateRange",
      type: "number",
      default: 100,
      min: 20,
      max: 200,
      step: 10,
      label: "Substrate Range",
      unit: "µM",
    },
  ],

  observables: [
    {
      id: "apparent-km",
      name: "apparentKm",
      type: "number",
      unit: "µM",
      label: "Apparent Km",
    },
    {
      id: "apparent-vmax",
      name: "apparentVmax",
      type: "number",
      unit: "µmol/min",
      label: "Apparent Vmax",
    },
    {
      id: "y-intercept",
      name: "yIntercept",
      type: "number",
      label: "1/Vmax (y-intercept)",
    },
    {
      id: "x-intercept",
      name: "xIntercept",
      type: "number",
      label: "-1/Km (x-intercept)",
    },
  ],

  successCriteria: [
    {
      type: "parameter_match",
      target: 0.85,
      tolerance: 0.15,
    },
  ],

  scaffolds: {
    hints: [
      "Look at where the lines intersect the y-axis (1/Vmax).",
      "In competitive inhibition, Vmax stays the same but Km appears to increase.",
      "In non-competitive inhibition, Vmax decreases but Km stays the same.",
    ],
    unlockAfterAttempts: 2,
  },

  createdAt: "2024-03-01T08:00:00Z",
  updatedAt: "2024-05-10T16:45:00Z",
  createdBy: "author-biochem-001",
  tenantId: "tenant-default",

  thumbnailUrl: "/assets/thumbnails/enzyme-kinetics.png",
  tags: [
    "biochemistry",
    "enzymes",
    "inhibition",
    "kinetics",
    "lineweaver-burk",
  ],
};

// ============================================================================
// Biology Simulations
// ============================================================================

export const mitosisStagesSim: SimulationManifest = {
  id: "sim-mitosis-stages",
  name: "Mitosis Stages Sequencer",
  description:
    "Interactive drag-and-drop activity to sequence the stages of mitosis.",
  domain: "biology",
  version: 1,
  status: "published",

  linkedLearningUnits: ["lu-biology-mitosis-001"],
  claimRefs: ["C1", "C2"],

  canvas: {
    width: 800,
    height: 500,
    background: "#1a1a2e",
    gridEnabled: false,
    coordinateSystem: "screen",
  },

  entities: [
    {
      id: "prophase-card",
      type: "draggable_card",
      name: "Prophase",
      position: { x: 100, y: 200 },
      properties: {
        stage: "prophase",
        correctOrder: 1,
        description: "Chromosomes condense, nuclear envelope breaks down",
      },
      visual: {
        shape: "rectangle",
        color: "#8b5cf6",
        size: { width: 150, height: 100 },
      },
    },
    {
      id: "metaphase-card",
      type: "draggable_card",
      name: "Metaphase",
      position: { x: 280, y: 200 },
      properties: {
        stage: "metaphase",
        correctOrder: 2,
        description: "Chromosomes align at the cell equator",
      },
      visual: {
        shape: "rectangle",
        color: "#06b6d4",
        size: { width: 150, height: 100 },
      },
    },
    {
      id: "anaphase-card",
      type: "draggable_card",
      name: "Anaphase",
      position: { x: 460, y: 200 },
      properties: {
        stage: "anaphase",
        correctOrder: 3,
        description: "Sister chromatids separate and move to poles",
      },
      visual: {
        shape: "rectangle",
        color: "#22c55e",
        size: { width: 150, height: 100 },
      },
    },
    {
      id: "telophase-card",
      type: "draggable_card",
      name: "Telophase",
      position: { x: 640, y: 200 },
      properties: {
        stage: "telophase",
        correctOrder: 4,
        description: "Nuclear envelope reforms, chromosomes decondense",
      },
      visual: {
        shape: "rectangle",
        color: "#f97316",
        size: { width: 150, height: 100 },
      },
    },
    {
      id: "sequence-slots",
      type: "drop_zone",
      name: "Sequence Slots",
      position: { x: 400, y: 400 },
      properties: {
        slots: 4,
      },
      visual: {
        shape: "rectangle",
        color: "#374151",
        size: { width: 680, height: 80 },
      },
    },
  ],

  constraints: [],

  parameters: [
    {
      id: "show-descriptions",
      name: "showDescriptions",
      type: "boolean",
      default: true,
      label: "Show Stage Descriptions",
    },
    {
      id: "shuffle-cards",
      name: "shuffleCards",
      type: "boolean",
      default: true,
      label: "Shuffle Cards on Start",
    },
  ],

  observables: [
    {
      id: "cards-in-order",
      name: "cardsInOrder",
      type: "string",
      label: "Current Sequence",
    },
    {
      id: "correct-positions",
      name: "correctPositions",
      type: "number",
      label: "Correct Positions",
    },
    {
      id: "total-moves",
      name: "totalMoves",
      type: "number",
      label: "Total Moves Made",
    },
  ],

  successCriteria: [
    {
      type: "state_reached",
      events: ["all_cards_correct"],
    },
  ],

  scaffolds: {
    hints: [
      "Which stage comes first? What happens to chromosomes initially?",
      'Metaphase - think "M" for "Middle". Chromosomes line up in the middle.',
      "The sequence follows P-MAT: Prophase, Metaphase, Anaphase, Telophase.",
    ],
    unlockAfterAttempts: 2,
  },

  createdAt: "2024-01-20T11:00:00Z",
  updatedAt: "2024-04-15T09:30:00Z",
  createdBy: "author-bio-001",
  tenantId: "tenant-default",

  thumbnailUrl: "/assets/thumbnails/mitosis-stages.png",
  tags: ["biology", "cell-division", "mitosis", "sequencing"],
};

// ============================================================================
// Computer Science Simulations
// ============================================================================

export const sortingVisualizerSim: SimulationManifest = {
  id: "sim-sorting-visualizer",
  name: "Sorting Algorithm Visualizer",
  description:
    "Step through sorting algorithms to understand how they work and compare their efficiency.",
  domain: "cs",
  version: 1,
  status: "published",

  linkedLearningUnits: ["lu-cs-sorting-001"],
  claimRefs: ["C1", "C3"],

  canvas: {
    width: 900,
    height: 500,
    background: "#0f172a",
    gridEnabled: false,
    coordinateSystem: "screen",
  },

  entities: [
    {
      id: "bar-0",
      type: "bar",
      name: "Element 0",
      position: { x: 100, y: 400 },
      properties: { value: 5, index: 0 },
      visual: {
        shape: "rectangle",
        color: "#3b82f6",
        size: { width: 50, height: 100 },
      },
    },
    {
      id: "bar-1",
      type: "bar",
      name: "Element 1",
      position: { x: 170, y: 400 },
      properties: { value: 2, index: 1 },
      visual: {
        shape: "rectangle",
        color: "#3b82f6",
        size: { width: 50, height: 40 },
      },
    },
    {
      id: "bar-2",
      type: "bar",
      name: "Element 2",
      position: { x: 240, y: 400 },
      properties: { value: 8, index: 2 },
      visual: {
        shape: "rectangle",
        color: "#3b82f6",
        size: { width: 50, height: 160 },
      },
    },
    {
      id: "bar-3",
      type: "bar",
      name: "Element 3",
      position: { x: 310, y: 400 },
      properties: { value: 1, index: 3 },
      visual: {
        shape: "rectangle",
        color: "#3b82f6",
        size: { width: 50, height: 20 },
      },
    },
    {
      id: "bar-4",
      type: "bar",
      name: "Element 4",
      position: { x: 380, y: 400 },
      properties: { value: 9, index: 4 },
      visual: {
        shape: "rectangle",
        color: "#3b82f6",
        size: { width: 50, height: 180 },
      },
    },
  ],

  constraints: [],

  parameters: [
    {
      id: "algorithm",
      name: "algorithm",
      type: "enum",
      default: "bubble",
      enumValues: ["bubble", "insertion", "selection", "merge", "quick"],
      label: "Sorting Algorithm",
    },
    {
      id: "array-size",
      name: "arraySize",
      type: "number",
      default: 5,
      min: 3,
      max: 20,
      step: 1,
      label: "Array Size",
    },
    {
      id: "animation-speed",
      name: "animationSpeed",
      type: "number",
      default: 500,
      min: 100,
      max: 2000,
      step: 100,
      label: "Animation Speed",
      unit: "ms",
    },
  ],

  observables: [
    {
      id: "comparisons",
      name: "comparisons",
      type: "number",
      label: "Comparisons Made",
    },
    {
      id: "swaps",
      name: "swaps",
      type: "number",
      label: "Swaps Made",
    },
    {
      id: "current-step",
      name: "currentStep",
      type: "number",
      label: "Current Step",
    },
    {
      id: "is-sorted",
      name: "isSorted",
      type: "boolean",
      label: "Array Sorted",
    },
  ],

  successCriteria: [
    {
      type: "state_reached",
      events: ["array_sorted"],
    },
  ],

  scaffolds: {
    hints: [
      "Bubble sort compares adjacent elements and swaps if needed.",
      "Watch how many times the algorithm has to loop through the array.",
      'Click "Step" to advance one comparison at a time.',
    ],
    unlockAfterAttempts: 2,
  },

  createdAt: "2024-02-25T14:00:00Z",
  updatedAt: "2024-05-01T10:15:00Z",
  createdBy: "author-cs-001",
  tenantId: "tenant-default",

  thumbnailUrl: "/assets/thumbnails/sorting-visualizer.png",
  tags: ["algorithms", "sorting", "complexity", "visualization"],
};

// ============================================================================
// Draft Simulations
// ============================================================================

export const pendulumDraftSim: SimulationManifest = {
  id: "sim-pendulum-draft",
  name: "Simple Pendulum",
  description: "Explore how pendulum period depends on length but not mass.",
  domain: "physics",
  version: 1,
  status: "draft",

  linkedLearningUnits: ["lu-physics-pendulum-draft"],
  claimRefs: ["C1"],

  canvas: {
    width: 600,
    height: 500,
    background: "#1a1a2e",
    gridEnabled: false,
    coordinateSystem: "cartesian",
  },

  entities: [
    {
      id: "pivot",
      type: "static_body",
      name: "Pivot Point",
      position: { x: 300, y: 450 },
      properties: {},
      visual: {
        shape: "circle",
        color: "#6b7280",
        size: { width: 20, height: 20 },
      },
    },
    {
      id: "bob",
      type: "pendulum_bob",
      name: "Pendulum Bob",
      position: { x: 400, y: 300 },
      properties: { mass: 1, length: 2 },
      visual: {
        shape: "circle",
        color: "#8b5cf6",
        size: { width: 40, height: 40 },
      },
    },
  ],

  constraints: [
    {
      id: "pendulum-constraint",
      type: "fixed_point",
      entities: ["pivot", "bob"],
    },
    {
      id: "gravity",
      type: "gravity",
      entities: ["bob"],
      properties: { acceleration: 9.81 },
    },
  ],

  parameters: [
    {
      id: "bob-mass",
      name: "bobMass",
      type: "number",
      default: 1,
      min: 0.5,
      max: 10,
      step: 0.5,
      label: "Bob Mass",
      unit: "kg",
    },
    {
      id: "string-length",
      name: "stringLength",
      type: "number",
      default: 2,
      min: 0.5,
      max: 5,
      step: 0.25,
      label: "String Length",
      unit: "m",
    },
  ],

  observables: [
    {
      id: "period",
      name: "period",
      type: "number",
      unit: "s",
      label: "Period (T)",
    },
    {
      id: "frequency",
      name: "frequency",
      type: "number",
      unit: "Hz",
      label: "Frequency",
    },
  ],

  createdAt: "2024-06-01T09:00:00Z",
  updatedAt: "2024-06-01T09:00:00Z",
  createdBy: "author-physics-001",
  tenantId: "tenant-default",

  tags: ["physics", "pendulum", "oscillation"],
};

// ============================================================================
// Export All Simulations
// ============================================================================

export const allSimulationManifests: SimulationBlueprintSeed[] = [
  fallingObjectsVacuumSim,
  projectileMotionSim,
  enzymeKineticsSim,
  mitosisStagesSim,
  sortingVisualizerSim,
  pendulumDraftSim,
];

export const publishedSimulations = allSimulationManifests.filter(
  (s) => s.status === "published",
);
export const draftSimulations = allSimulationManifests.filter(
  (s) => s.status === "draft",
);

// ============================================================================
// Simulation Statistics (Mock Analytics)
// ============================================================================

export interface SimulationStats {
  simId: string;
  totalSessions: number;
  avgSessionDuration: number; // minutes
  completionRate: number;
  avgAttemptsToSuccess: number;
  popularParameters: Record<string, number>;
  rating: number;
  ratingCount: number;
}

export const simulationStats: Record<string, SimulationStats> = {
  "sim-falling-objects-vacuum": {
    simId: "sim-falling-objects-vacuum",
    totalSessions: 1847,
    avgSessionDuration: 8,
    completionRate: 0.91,
    avgAttemptsToSuccess: 2.3,
    popularParameters: { airResistance: 0 },
    rating: 4.8,
    ratingCount: 423,
  },
  "sim-projectile-motion": {
    simId: "sim-projectile-motion",
    totalSessions: 1234,
    avgSessionDuration: 12,
    completionRate: 0.85,
    avgAttemptsToSuccess: 3.7,
    popularParameters: { launchAngle: 45, launchVelocity: 25 },
    rating: 4.7,
    ratingCount: 298,
  },
  "sim-enzyme-kinetics": {
    simId: "sim-enzyme-kinetics",
    totalSessions: 567,
    avgSessionDuration: 15,
    completionRate: 0.72,
    avgAttemptsToSuccess: 4.2,
    popularParameters: { inhibitorType: "competitive" },
    rating: 4.5,
    ratingCount: 112,
  },
  "sim-mitosis-stages": {
    simId: "sim-mitosis-stages",
    totalSessions: 2456,
    avgSessionDuration: 5,
    completionRate: 0.96,
    avgAttemptsToSuccess: 1.4,
    popularParameters: {},
    rating: 4.9,
    ratingCount: 567,
  },
  "sim-sorting-visualizer": {
    simId: "sim-sorting-visualizer",
    totalSessions: 892,
    avgSessionDuration: 10,
    completionRate: 0.88,
    avgAttemptsToSuccess: 2.1,
    popularParameters: { algorithm: "bubble" },
    rating: 4.6,
    ratingCount: 201,
  },
};

// ============================================================================
// Entity Library (for Simulation Builder)
// ============================================================================

export interface EntityTemplate {
  id: string;
  name: string;
  category: "physics" | "chemistry" | "biology" | "cs" | "ui" | "general";
  type: string;
  defaultProperties: Record<string, unknown>;
  visual: {
    shape: "circle" | "rectangle" | "sprite";
    color: string;
    size: { width: number; height: number };
  };
  description: string;
}

export const entityTemplates: EntityTemplate[] = [
  // Physics entities
  {
    id: "tpl-rigid-body",
    name: "Rigid Body",
    category: "physics",
    type: "rigid_body",
    defaultProperties: { mass: 1, radius: 20 },
    visual: {
      shape: "circle",
      color: "#3b82f6",
      size: { width: 40, height: 40 },
    },
    description: "A physical object affected by forces and collisions",
  },
  {
    id: "tpl-static-body",
    name: "Static Body",
    category: "physics",
    type: "static_body",
    defaultProperties: { width: 100, height: 20 },
    visual: {
      shape: "rectangle",
      color: "#4b5563",
      size: { width: 100, height: 20 },
    },
    description: "An immovable object for platforms, walls, or boundaries",
  },
  {
    id: "tpl-projectile",
    name: "Projectile",
    category: "physics",
    type: "projectile",
    defaultProperties: { mass: 1, velocity: 20, angle: 45 },
    visual: {
      shape: "circle",
      color: "#f97316",
      size: { width: 30, height: 30 },
    },
    description: "An object that can be launched with initial velocity",
  },
  {
    id: "tpl-spring",
    name: "Spring",
    category: "physics",
    type: "spring",
    defaultProperties: { stiffness: 100, damping: 0.5, restLength: 50 },
    visual: {
      shape: "sprite",
      color: "#22c55e",
      size: { width: 80, height: 20 },
    },
    description: "An elastic connector between two objects",
  },

  // Chemistry entities
  {
    id: "tpl-molecule",
    name: "Molecule",
    category: "chemistry",
    type: "molecule",
    defaultProperties: { formula: "H2O", concentration: 1 },
    visual: {
      shape: "circle",
      color: "#6366f1",
      size: { width: 30, height: 30 },
    },
    description: "A chemical molecule or compound",
  },
  {
    id: "tpl-enzyme",
    name: "Enzyme",
    category: "chemistry",
    type: "enzyme",
    defaultProperties: { Vmax: 100, Km: 10 },
    visual: {
      shape: "sprite",
      color: "#8b5cf6",
      size: { width: 60, height: 60 },
    },
    description: "A biological catalyst with kinetic properties",
  },
  {
    id: "tpl-reaction-vessel",
    name: "Reaction Vessel",
    category: "chemistry",
    type: "container",
    defaultProperties: { volume: 1, temperature: 25 },
    visual: {
      shape: "rectangle",
      color: "#374151",
      size: { width: 150, height: 200 },
    },
    description: "A container for chemical reactions",
  },

  // Biology entities
  {
    id: "tpl-cell",
    name: "Cell",
    category: "biology",
    type: "cell",
    defaultProperties: { stage: "interphase", chromosomeCount: 46 },
    visual: {
      shape: "circle",
      color: "#ec4899",
      size: { width: 80, height: 80 },
    },
    description: "A biological cell that can undergo division",
  },
  {
    id: "tpl-chromosome",
    name: "Chromosome",
    category: "biology",
    type: "chromosome",
    defaultProperties: { condensed: false, separated: false },
    visual: {
      shape: "sprite",
      color: "#a855f7",
      size: { width: 20, height: 40 },
    },
    description: "A condensed DNA structure visible during cell division",
  },

  // CS entities
  {
    id: "tpl-array-bar",
    name: "Array Element",
    category: "cs",
    type: "bar",
    defaultProperties: { value: 5, index: 0 },
    visual: {
      shape: "rectangle",
      color: "#3b82f6",
      size: { width: 40, height: 100 },
    },
    description: "A visual bar representing an array element for sorting",
  },
  {
    id: "tpl-tree-node",
    name: "Tree Node",
    category: "cs",
    type: "tree_node",
    defaultProperties: { value: 0, left: null, right: null },
    visual: {
      shape: "circle",
      color: "#10b981",
      size: { width: 50, height: 50 },
    },
    description: "A node in a binary tree structure",
  },

  // UI entities
  {
    id: "tpl-draggable-card",
    name: "Draggable Card",
    category: "ui",
    type: "draggable_card",
    defaultProperties: { label: "Card", correctOrder: 0 },
    visual: {
      shape: "rectangle",
      color: "#8b5cf6",
      size: { width: 120, height: 80 },
    },
    description: "A card that can be dragged and dropped",
  },
  {
    id: "tpl-drop-zone",
    name: "Drop Zone",
    category: "ui",
    type: "drop_zone",
    defaultProperties: { acceptTypes: [], slots: 1 },
    visual: {
      shape: "rectangle",
      color: "#374151",
      size: { width: 150, height: 100 },
    },
    description: "An area where cards or objects can be dropped",
  },
  {
    id: "tpl-target",
    name: "Target",
    category: "ui",
    type: "sensor",
    defaultProperties: { radius: 30 },
    visual: {
      shape: "circle",
      color: "#22c55e",
      size: { width: 60, height: 60 },
    },
    description: "A target area for hitting or reaching objectives",
  },
  {
    id: "tpl-graph",
    name: "Graph/Plot",
    category: "ui",
    type: "graph",
    defaultProperties: { xAxis: "x", yAxis: "y", series: [] },
    visual: {
      shape: "rectangle",
      color: "#1f2937",
      size: { width: 300, height: 200 },
    },
    description: "A 2D graph or plot for displaying data",
  },

  // General
  {
    id: "tpl-text-label",
    name: "Text Label",
    category: "general",
    type: "text",
    defaultProperties: { text: "Label", fontSize: 16 },
    visual: {
      shape: "rectangle",
      color: "transparent",
      size: { width: 100, height: 30 },
    },
    description: "A text label for annotations",
  },
  {
    id: "tpl-timer",
    name: "Timer",
    category: "general",
    type: "timer",
    defaultProperties: { duration: 60, autoStart: false },
    visual: {
      shape: "rectangle",
      color: "#f59e0b",
      size: { width: 80, height: 40 },
    },
    description: "A countdown or stopwatch timer",
  },
];

// ============================================================================
// Constraint Templates (for Simulation Builder)
// ============================================================================

export interface ConstraintTemplate {
  id: string;
  name: string;
  type: SimulationConstraint["type"];
  category: "physics" | "connection" | "collision";
  defaultProperties: Record<string, unknown>;
  description: string;
  requiredEntityCount: number;
}

export const constraintTemplates: ConstraintTemplate[] = [
  {
    id: "cstr-gravity",
    name: "Gravity",
    type: "gravity",
    category: "physics",
    defaultProperties: { acceleration: 9.81, direction: { x: 0, y: -1 } },
    description: "Apply gravitational force to objects",
    requiredEntityCount: 1,
  },
  {
    id: "cstr-air-resistance",
    name: "Air Resistance",
    type: "air_resistance",
    category: "physics",
    defaultProperties: { enabled: true, densityMultiplier: 1.0 },
    description: "Apply drag force based on velocity and object properties",
    requiredEntityCount: 1,
  },
  {
    id: "cstr-spring",
    name: "Spring Connection",
    type: "spring",
    category: "connection",
    defaultProperties: { stiffness: 100, damping: 0.5, restLength: 50 },
    description: "Connect two objects with an elastic spring",
    requiredEntityCount: 2,
  },
  {
    id: "cstr-fixed-point",
    name: "Fixed Point",
    type: "fixed_point",
    category: "connection",
    defaultProperties: {},
    description: "Fix an object to a pivot point (for pendulums)",
    requiredEntityCount: 2,
  },
  {
    id: "cstr-collision",
    name: "Collision",
    type: "collision",
    category: "collision",
    defaultProperties: { restitution: 0.5 },
    description: "Enable collision detection between objects",
    requiredEntityCount: 2,
  },
];
