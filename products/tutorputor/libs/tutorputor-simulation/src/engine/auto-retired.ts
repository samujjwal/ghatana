/**
 * Auto Retired - Legacy Preset Bag
 *
 * This module contains the legacy auto-runtime preset bag that has been
 * extracted from the main engine exports. These presets are maintained
 * for backward compatibility during the migration period but should not
 * be used for new development.
 *
 * The curated starter catalog in starter-catalog.ts should be used
 * for all new simulation authoring workflows.
 *
 * @deprecated Use starter-catalog.ts instead
 * @doc.type module
 * @doc.purpose Legacy preset storage for backward compatibility only
 * @doc.layer legacy
 */

import type {
  SimEntityBase,
  PhysicsConfig,
} from "@tutorputor/contracts/v1/simulation";
import { getSimulationStarterByLegacyPresetId as _getStarterByLegacyId } from "./starter-catalog";

// Local type definitions to avoid circular dependencies
/** @deprecated */
interface SimulationTemplate {
  id: string;
  name: string;
  description: string;
  domain: string;
  difficulty: string;
  entities: Array<{
    id: string;
    type: string;
    x: number;
    y: number;
    properties?: Record<string, unknown>;
    appearance?: Record<string, unknown>;
  }>;
  config: {
    gravity: number;
    timeScale: number;
    paused: boolean;
  };
  parameters: Array<{
    id: string;
    name: string;
    type: string;
    defaultValue: number;
    min: number;
    max: number;
    step: number;
    description: string;
  }>;
  learningObjectives: string[];
}

/** @deprecated */
interface SimulationParameter {
  id: string;
  name: string;
  type: string;
  defaultValue: number;
  min: number;
  max: number;
  step: number;
  description: string;
}

// =============================================================================
// Legacy Types (deprecated)
// =============================================================================

/** @deprecated Use SimulationStarter from starter-catalog instead */
export interface AutoSimulationRequest {
  description: string;
  domain: "physics" | "chemistry" | "biology" | "medicine" | "cs" | "math";
  learningObjective?: string;
  difficulty?: "beginner" | "intermediate" | "advanced";
  entityCount?: number;
  concepts?: string[];
  audience?: "k12" | "undergraduate" | "graduate" | "professional";
  duration?: number;
}

/** @deprecated Use SimulationStarter from starter-catalog instead */
export interface AutoSimulationResult {
  manifest: {
    id: string;
    type?: string;
    title: string;
    description?: string;
    entities?: SimEntityBase[];
    steps?: Array<{
      id: string;
      title?: string;
      description?: string;
      duration?: number;
      stateChanges?: Record<string, unknown>;
    }>;
  };
  template: SimulationTemplate;
  explanation: string;
  narration?: string;
  educational: {
    concepts: string[];
    prerequisites: string[];
    followUpQuestions: string[];
    commonMisconceptions: string[];
  };
  confidence: number;
}

/** @deprecated Use SimulationStarter from starter-catalog instead */
export interface SimulationPreset {
  id: string;
  name: string;
  description: string;
  domain: string;
  manifest: {
    type?: string;
    title?: string;
    entities?: Array<{
      id: string;
      type?: string;
      x: number;
      y: number;
      properties?: Record<string, unknown>;
      appearance?: Record<string, unknown>;
    }>;
    steps?: Array<{
      id: string;
      title?: string;
      description?: string;
      duration?: number;
      stateChanges?: Record<string, unknown>;
    }>;
  };
  educationalNotes: string;
}

// =============================================================================
// Legacy Auto Runtime Status
// =============================================================================

export type LegacyAutoRuntimeStatus =
  | "governed_starter_available"
  | "legacy_compatibility_only";

export interface LegacyAutoRuntimePresetSummary {
  id: string;
  name: string;
  description: string;
  domain: string;
  retirementStatus: LegacyAutoRuntimeStatus;
  starterId?: string;
  audience?: "k12" | "undergraduate" | "graduate" | "professional";
}

export interface LegacyAutoRuntimeSummary {
  totalPresets: number;
  governedStarterAvailable: number;
  compatibilityOnly: number;
  byDomain: Record<
    string,
    {
      total: number;
      governedStarterAvailable: number;
      compatibilityOnly: number;
    }
  >;
  items: LegacyAutoRuntimePresetSummary[];
}

// =============================================================================
// Legacy Preset Bag (extracted from auto/index.ts)
// =============================================================================

/** @deprecated These presets are maintained for compatibility only */
export const SimulationPresets: SimulationPreset[] = [
  // PHYSICS PRESETS (1-20)
  {
    id: "preset-newton-first",
    name: "Newton's First Law (Inertia)",
    description:
      "Object at rest stays at rest, object in motion stays in motion",
    domain: "physics",
    manifest: {
      type: "physics",
      title: "Newton's First Law",
      entities: [
        {
          id: "object",
          type: "dynamic-body",
          x: 200,
          y: 300,
          properties: { mass: 2, radius: 25 },
          appearance: { fillColor: "#4ecdc4", strokeColor: "#333" },
        },
        {
          id: "friction-surface",
          type: "boundary",
          x: 400,
          y: 350,
          properties: { width: 800, height: 10, friction: 0 },
          appearance: { fillColor: "#666" },
        },
        {
          id: "pusher",
          type: "force-field",
          x: 150,
          y: 300,
          properties: { force: 50, direction: 0, duration: 500 },
          appearance: { fillColor: "#ff6b6b" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Object at Rest",
          description: "The object remains stationary until acted upon",
          duration: 2000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Applied Force",
          description: "External force is applied",
          duration: 500,
          stateChanges: { "pusher.active": true },
        },
        {
          id: "step-3",
          title: "Continued Motion",
          description: "Object continues moving with constant velocity",
          duration: 3000,
          stateChanges: { "pusher.active": false },
        },
      ],
    },
    educationalNotes:
      "Demonstrates that objects maintain their state of motion unless acted upon by external forces",
  },
  {
    id: "preset-newton-second",
    name: "Newton's Second Law (F=ma)",
    description: "Relationship between force, mass, and acceleration",
    domain: "physics",
    manifest: {
      type: "physics",
      title: "Newton's Second Law",
      entities: [
        {
          id: "light-cart",
          type: "dynamic-body",
          x: 200,
          y: 300,
          properties: { mass: 1, radius: 20 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "heavy-cart",
          type: "dynamic-body",
          x: 200,
          y: 400,
          properties: { mass: 3, radius: 30 },
          appearance: { fillColor: "#ff6b6b" },
        },
        {
          id: "force-applier",
          type: "force-field",
          x: 150,
          y: 350,
          properties: { force: 30, direction: 0 },
          appearance: { fillColor: "#ffd93d" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Same Force Applied",
          description: "Equal force applied to different masses",
          duration: 3000,
          stateChanges: { "force-applier.active": true },
        },
        {
          id: "step-2",
          title: "Different Accelerations",
          description: "Light cart accelerates faster than heavy cart",
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes:
      "Shows that acceleration is inversely proportional to mass when force is constant",
  },
  {
    id: "preset-conservation-energy",
    name: "Conservation of Energy",
    description: "Energy transforms between potential and kinetic forms",
    domain: "physics",
    manifest: {
      type: "physics",
      title: "Conservation of Mechanical Energy",
      entities: [
        {
          id: "ball",
          type: "dynamic-body",
          x: 100,
          y: 100,
          properties: { mass: 1, radius: 15 },
          appearance: { fillColor: "#ff6b6b" },
        },
        {
          id: "track",
          type: "boundary",
          x: 400,
          y: 400,
          properties: { width: 800, height: 10, shape: "curved" },
          appearance: { fillColor: "#666" },
        },
        {
          id: "energy-meter",
          type: "sensor",
          x: 750,
          y: 50,
          properties: { type: "energy-monitor" },
          appearance: { fillColor: "#4ecdc4" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Maximum Potential Energy",
          description: "Ball at highest point with maximum PE",
          duration: 1000,
          stateChanges: { "energy-meter.pe": 100, "energy-meter.ke": 0 },
        },
        {
          id: "step-2",
          title: "Rolling Down",
          description: "PE converts to KE as ball descends",
          duration: 2000,
          stateChanges: {},
        },
        {
          id: "step-3",
          title: "Maximum Kinetic Energy",
          description: "At lowest point, maximum velocity and KE",
          duration: 500,
          stateChanges: { "energy-meter.pe": 0, "energy-meter.ke": 100 },
        },
        {
          id: "step-4",
          title: "Climbing Up",
          description: "KE converts back to PE",
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes:
      "Demonstrates energy transformation while total mechanical energy remains constant",
  },
  {
    id: "preset-momentum-conservation",
    name: "Conservation of Momentum",
    description: "Total momentum remains constant in isolated system",
    domain: "physics",
    manifest: {
      type: "physics",
      title: "Momentum Conservation",
      entities: [
        {
          id: "cart-1",
          type: "dynamic-body",
          x: 200,
          y: 300,
          properties: { mass: 2, radius: 25, vx: 5 },
          appearance: { fillColor: "#ff6b6b" },
        },
        {
          id: "cart-2",
          type: "dynamic-body",
          x: 600,
          y: 300,
          properties: { mass: 2, radius: 25, vx: -3 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "momentum-display",
          type: "sensor",
          x: 400,
          y: 50,
          properties: { type: "momentum-monitor" },
          appearance: { fillColor: "#ffd93d" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Before Collision",
          description: "Carts approach with different momenta",
          duration: 1500,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Collision",
          description: "Elastic collision occurs",
          duration: 200,
          stateChanges: {},
        },
        {
          id: "step-3",
          title: "After Collision",
          description: "Momentum conserved, velocities exchanged",
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes:
      "Shows that total momentum before and after collision remains the same",
  },
  {
    id: "preset-wave-interference",
    name: "Wave Interference",
    description: "Constructive and destructive interference patterns",
    domain: "physics",
    manifest: {
      type: "physics",
      title: "Wave Interference",
      entities: [
        {
          id: "source-1",
          type: "wave-source",
          x: 300,
          y: 200,
          properties: { frequency: 2, amplitude: 20, phase: 0 },
          appearance: { fillColor: "#ff6b6b" },
        },
        {
          id: "source-2",
          type: "wave-source",
          x: 500,
          y: 200,
          properties: { frequency: 2, amplitude: 20, phase: 0 },
          appearance: { fillColor: "#4ecdc4" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Waves Emanate",
          description: "Circular waves spread from both sources",
          duration: 2000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Constructive Interference",
          description: "Waves meet in phase, amplitude doubles",
          duration: 2000,
          stateChanges: {},
        },
        {
          id: "step-3",
          title: "Destructive Interference",
          description: "Waves meet out of phase, cancel out",
          duration: 2000,
          stateChanges: { "source-2.phase": 180 },
        },
      ],
    },
    educationalNotes:
      "Demonstrates principle of superposition and interference patterns",
  },
  // CHEMISTRY PRESETS (21-40)
  {
    id: "preset-atomic-structure",
    name: "Atomic Structure",
    description: "Bohr model with electron shells and energy levels",
    domain: "chemistry",
    manifest: {
      type: "chemistry",
      title: "Atomic Structure",
      entities: [
        {
          id: "nucleus",
          type: "molecule",
          x: 400,
          y: 300,
          properties: { radius: 30, protons: 6, neutrons: 6 },
          appearance: { fillColor: "#ff6b6b", strokeColor: "#333" },
        },
        {
          id: "electron-1",
          type: "molecule",
          x: 400,
          y: 200,
          properties: { radius: 8, charge: -1 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "electron-2",
          type: "molecule",
          x: 400,
          y: 400,
          properties: { radius: 8, charge: -1 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "shell-1",
          type: "field",
          x: 400,
          y: 300,
          properties: { radius: 100, energy: -13.6 },
          appearance: { strokeColor: "#666", strokeWidth: 1 },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Ground State",
          description: "Electrons in lowest energy level",
          duration: 2000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Photon Absorption",
          description: "Electron absorbs energy and jumps to higher level",
          duration: 1000,
          stateChanges: { "electron-1.shell": 2, "electron-1.y": 150 },
        },
        {
          id: "step-3",
          title: "Photon Emission",
          description: "Electron drops back, emitting photon",
          duration: 1000,
          stateChanges: { "electron-1.shell": 1, "electron-1.y": 200 },
        },
      ],
    },
    educationalNotes:
      "Visualizes electron energy levels and quantum transitions",
  },
  {
    id: "preset-chemical-equilibrium",
    name: "Chemical Equilibrium",
    description: "Dynamic equilibrium in reversible reactions",
    domain: "chemistry",
    manifest: {
      type: "chemistry",
      title: "Reversible Reaction Equilibrium",
      entities: [
        {
          id: "reactant-a",
          type: "molecule",
          x: 200,
          y: 300,
          properties: { type: "A", count: 10 },
          appearance: { fillColor: "#ff6b6b" },
        },
        {
          id: "reactant-b",
          type: "molecule",
          x: 200,
          y: 350,
          properties: { type: "B", count: 10 },
          appearance: { fillColor: "#ffd93d" },
        },
        {
          id: "product-c",
          type: "molecule",
          x: 600,
          y: 325,
          properties: { type: "C", count: 0 },
          appearance: { fillColor: "#4ecdc4" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Initial State",
          description: "Only reactants present",
          duration: 1000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Forward Reaction",
          description: "A + B → C begins",
          duration: 3000,
          stateChanges: {
            "reactant-a.count": 6,
            "reactant-b.count": 6,
            "product-c.count": 4,
          },
        },
        {
          id: "step-3",
          title: "Reverse Reaction",
          description: "C → A + B begins as C accumulates",
          duration: 3000,
          stateChanges: {
            "reactant-a.count": 4,
            "reactant-b.count": 4,
            "product-c.count": 6,
          },
        },
        {
          id: "step-4",
          title: "Equilibrium",
          description: "Forward and reverse rates equal",
          duration: 2000,
          stateChanges: {},
        },
      ],
    },
    educationalNotes: "Demonstrates that equilibrium is dynamic, not static",
  },
  {
    id: "preset-gas-laws",
    name: "Gas Laws (Boyle & Charles)",
    description: "Relationship between pressure, volume, and temperature",
    domain: "chemistry",
    manifest: {
      type: "chemistry",
      title: "Ideal Gas Behavior",
      entities: [
        {
          id: "container",
          type: "boundary",
          x: 400,
          y: 300,
          properties: { width: 400, height: 300, movable: true },
          appearance: { strokeColor: "#666", strokeWidth: 3 },
        },
        {
          id: "gas-particle",
          type: "molecule",
          x: 350,
          y: 250,
          properties: { count: 50, velocity: 2 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "pressure-gauge",
          type: "sensor",
          x: 600,
          y: 150,
          properties: { value: 1.0, unit: "atm" },
          appearance: { fillColor: "#ffd93d" },
        },
        {
          id: "temperature-gauge",
          type: "sensor",
          x: 200,
          y: 150,
          properties: { value: 300, unit: "K" },
          appearance: { fillColor: "#ff6b6b" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Initial State",
          description: "Container at standard conditions",
          duration: 1000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Boyle's Law",
          description: "Compressing container increases pressure",
          duration: 2000,
          stateChanges: { "container.width": 200, "pressure-gauge.value": 2.0 },
        },
        {
          id: "step-3",
          title: "Charles's Law",
          description: "Heating gas increases volume",
          duration: 2000,
          stateChanges: {
            "temperature-gauge.value": 600,
            "container.width": 400,
            "gas-particle.velocity": 4,
          },
        },
      ],
    },
    educationalNotes: "Shows PV=nRT relationships through visual changes",
  },
  // BIOLOGY PRESETS (41-60)
  {
    id: "preset-cell-membrane",
    name: "Cell Membrane Transport",
    description: "Diffusion, osmosis, and active transport",
    domain: "biology",
    manifest: {
      type: "biology",
      title: "Membrane Transport Mechanisms",
      entities: [
        {
          id: "membrane",
          type: "boundary",
          x: 400,
          y: 300,
          properties: { width: 10, height: 400, permeable: true },
          appearance: { fillColor: "#9b59b6" },
        },
        {
          id: "channel-protein",
          type: "molecule",
          x: 400,
          y: 200,
          properties: { type: "channel", state: "open" },
          appearance: { fillColor: "#e74c3c" },
        },
        {
          id: "pump-protein",
          type: "molecule",
          x: 400,
          y: 400,
          properties: { type: "pump", requiresATP: true },
          appearance: { fillColor: "#f39c12" },
        },
        {
          id: "molecule-a",
          type: "molecule",
          x: 300,
          y: 250,
          properties: { concentration: 10 },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "molecule-b",
          type: "molecule",
          x: 500,
          y: 350,
          properties: { concentration: 2 },
          appearance: { fillColor: "#4ecdc4" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Simple Diffusion",
          description: "Small molecules pass through membrane",
          duration: 2000,
          stateChanges: { "molecule-a.x": 500, "molecule-a.concentration": 6 },
        },
        {
          id: "step-2",
          title: "Facilitated Diffusion",
          description: "Channel protein assists transport",
          duration: 2000,
          stateChanges: { "channel-protein.state": "active" },
        },
        {
          id: "step-3",
          title: "Active Transport",
          description: "Pump moves molecules against gradient",
          duration: 2000,
          stateChanges: {
            "pump-protein.active": true,
            "molecule-b.x": 300,
            "molecule-b.concentration": 6,
          },
        },
      ],
    },
    educationalNotes: "Compares passive and active transport mechanisms",
  },
  {
    id: "preset-photosynthesis",
    name: "Photosynthesis Process",
    description: "Light-dependent and independent reactions",
    domain: "biology",
    manifest: {
      type: "biology",
      title: "Photosynthesis",
      entities: [
        {
          id: "chloroplast",
          type: "cell",
          x: 400,
          y: 300,
          properties: { type: "organelle", size: 200 },
          appearance: { fillColor: "#2ecc71" },
        },
        {
          id: "thylakoid",
          type: "cell",
          x: 350,
          y: 250,
          properties: { type: "membrane", reaction: "light-dependent" },
          appearance: { fillColor: "#27ae60" },
        },
        {
          id: "stroma",
          type: "field",
          x: 450,
          y: 350,
          properties: { type: "matrix", reaction: "calvin-cycle" },
          appearance: { fillColor: "#229954" },
        },
        {
          id: "sunlight",
          type: "field",
          x: 200,
          y: 100,
          properties: { type: "energy", wavelength: "visible" },
          appearance: { fillColor: "#ffd93d" },
        },
        {
          id: "co2",
          type: "molecule",
          x: 600,
          y: 150,
          properties: { type: "gas" },
          appearance: { fillColor: "#95a5a6" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Light Absorption",
          description: "Photons excite electrons in chlorophyll",
          duration: 2000,
          stateChanges: {
            "sunlight.intensity": "high",
            "thylakoid.excited": true,
          },
        },
        {
          id: "step-2",
          title: "Water Splitting",
          description: "Photolysis produces O2, H+, and electrons",
          duration: 2000,
          stateChanges: { "thylakoid.o2-released": true },
        },
        {
          id: "step-3",
          title: "ATP and NADPH Production",
          description: "Energy carriers created",
          duration: 1500,
          stateChanges: { "stroma.atp": 3, "stroma.nadph": 2 },
        },
        {
          id: "step-4",
          title: "Calvin Cycle",
          description: "CO2 fixed into glucose",
          duration: 3000,
          stateChanges: { "co2.consumed": true, "stroma.glucose": 1 },
        },
      ],
    },
    educationalNotes: "Complete visualization of photosynthesis stages",
  },
  {
    id: "preset-dna-replication",
    name: "DNA Replication",
    description: "Semi-conservative replication process",
    domain: "biology",
    manifest: {
      type: "biology",
      title: "DNA Replication",
      entities: [
        {
          id: "dna-strand",
          type: "molecule",
          x: 400,
          y: 300,
          properties: { type: "dna", structure: "double-helix" },
          appearance: { strokeColor: "#e74c3c", strokeWidth: 4 },
        },
        {
          id: "helicase",
          type: "molecule",
          x: 350,
          y: 300,
          properties: { type: "enzyme", function: "unzip" },
          appearance: { fillColor: "#9b59b6" },
        },
        {
          id: "polymerase",
          type: "molecule",
          x: 450,
          y: 250,
          properties: { type: "enzyme", function: "synthesize" },
          appearance: { fillColor: "#3498db" },
        },
        {
          id: "nucleotide",
          type: "molecule",
          x: 500,
          y: 200,
          properties: { type: "base", available: 20 },
          appearance: { fillColor: "#f39c12" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Initiation",
          description: "Helicase unwinds DNA at origin",
          duration: 2000,
          stateChanges: { "helicase.active": true, "dna-strand.open": true },
        },
        {
          id: "step-2",
          title: "Elongation",
          description: "Polymerase adds complementary bases",
          duration: 3000,
          stateChanges: {
            "polymerase.active": true,
            "nucleotide.available": 5,
            "dna-strand.new-strand": 50,
          },
        },
        {
          id: "step-3",
          title: "Termination",
          description: "Replication complete, two identical strands",
          duration: 1500,
          stateChanges: { "dna-strand.complete": true, "dna-strand.count": 2 },
        },
      ],
    },
    educationalNotes: "Shows semi-conservative nature of DNA replication",
  },
  // CS PRESETS (61-80)
  {
    id: "preset-binary-search",
    name: "Binary Search Algorithm",
    description: "Efficient search in sorted arrays",
    domain: "cs",
    manifest: {
      type: "discrete",
      title: "Binary Search Visualization",
      entities: [
        {
          id: "array",
          type: "array",
          x: 400,
          y: 200,
          properties: { size: 16, sorted: true },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "target",
          type: "variable",
          x: 400,
          y: 400,
          properties: { value: 42, found: false },
          appearance: { fillColor: "#ffd93d" },
        },
        {
          id: "pointer-low",
          type: "pointer",
          x: 200,
          y: 280,
          properties: { index: 0, label: "low" },
          appearance: { fillColor: "#2ecc71" },
        },
        {
          id: "pointer-high",
          type: "pointer",
          x: 600,
          y: 280,
          properties: { index: 15, label: "high" },
          appearance: { fillColor: "#e74c3c" },
        },
        {
          id: "pointer-mid",
          type: "pointer",
          x: 400,
          y: 280,
          properties: { index: 7, label: "mid" },
          appearance: { fillColor: "#f39c12" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Initialize",
          description: "Set low=0, high=n-1",
          duration: 1000,
          stateChanges: {},
        },
        {
          id: "step-2",
          title: "Calculate Mid",
          description: "mid = (low + high) / 2",
          duration: 1500,
          stateChanges: { "pointer-mid.active": true },
        },
        {
          id: "step-3",
          title: "Compare",
          description: "Compare target with array[mid]",
          duration: 1000,
          stateChanges: { "array.index-7.highlight": true },
        },
        {
          id: "step-4",
          title: "Adjust Range",
          description: "Update low or high based on comparison",
          duration: 1500,
          stateChanges: { "pointer-low.index": 8, "pointer-mid.index": 11 },
        },
        {
          id: "step-5",
          title: "Found!",
          description: "Target found at index",
          duration: 1000,
          stateChanges: {
            "target.found": true,
            "array.index-11.highlight": "success",
          },
        },
      ],
    },
    educationalNotes: "Demonstrates O(log n) complexity through range halving",
  },
  {
    id: "preset-dijkstra",
    name: "Dijkstra's Shortest Path",
    description: "Finding shortest paths in weighted graphs",
    domain: "cs",
    manifest: {
      type: "discrete",
      title: "Dijkstra Algorithm",
      entities: [
        {
          id: "graph",
          type: "graph",
          x: 400,
          y: 300,
          properties: { nodes: 6, edges: 9, weighted: true },
          appearance: { strokeColor: "#666" },
        },
        {
          id: "source",
          type: "node",
          x: 200,
          y: 300,
          properties: { id: "A", distance: 0 },
          appearance: { fillColor: "#2ecc71" },
        },
        {
          id: "target",
          type: "node",
          x: 600,
          y: 300,
          properties: { id: "F", distance: Infinity },
          appearance: { fillColor: "#e74c3c" },
        },
        {
          id: "priority-queue",
          type: "structure",
          x: 700,
          y: 100,
          properties: { type: "min-heap", elements: ["A"] },
          appearance: { fillColor: "#9b59b6" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Initialize",
          description: "Set source distance to 0, others to infinity",
          duration: 1500,
          stateChanges: {
            "source.distance": 0,
            "priority-queue.elements": ["A"],
          },
        },
        {
          id: "step-2",
          title: "Extract Min",
          description: "Remove node with minimum distance from queue",
          duration: 1000,
          stateChanges: {
            "priority-queue.elements": ["B", "C"],
            "source.visited": true,
          },
        },
        {
          id: "step-3",
          title: "Relax Edges",
          description: "Update distances to neighbors",
          duration: 2000,
          stateChanges: { "node-B.distance": 4, "node-C.distance": 2 },
        },
        {
          id: "step-4",
          title: "Build Path",
          description: "Shortest path constructed",
          duration: 2000,
          stateChanges: {
            "target.distance": 8,
            "path.highlight": true,
            "path.nodes": ["A", "C", "E", "F"],
          },
        },
      ],
    },
    educationalNotes: "Shows greedy approach and edge relaxation",
  },
  {
    id: "preset-binary-tree",
    name: "Binary Tree Traversal",
    description: "In-order, pre-order, and post-order traversals",
    domain: "cs",
    manifest: {
      type: "discrete",
      title: "Tree Traversal",
      entities: [
        {
          id: "tree",
          type: "tree",
          x: 400,
          y: 300,
          properties: { type: "binary", height: 3 },
          appearance: { strokeColor: "#666" },
        },
        {
          id: "root",
          type: "node",
          x: 400,
          y: 100,
          properties: { value: 1, visited: false },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "node-2",
          type: "node",
          x: 300,
          y: 200,
          properties: { value: 2, visited: false },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "node-3",
          type: "node",
          x: 500,
          y: 200,
          properties: { value: 3, visited: false },
          appearance: { fillColor: "#4ecdc4" },
        },
        {
          id: "current",
          type: "pointer",
          x: 400,
          y: 100,
          properties: { target: "root", label: "current" },
          appearance: { fillColor: "#f39c12" },
        },
        {
          id: "stack",
          type: "structure",
          x: 700,
          y: 200,
          properties: { type: "stack", elements: [] },
          appearance: { fillColor: "#9b59b6" },
        },
      ],
      steps: [
        {
          id: "step-1",
          title: "Visit Root",
          description: "Start at root node",
          duration: 1000,
          stateChanges: {
            "current.target": "root",
            "root.visited": true,
            "root.highlight": "order-1",
          },
        },
        {
          id: "step-2",
          title: "Go Left",
          description: "Traverse to left child",
          duration: 1000,
          stateChanges: {
            "current.target": "node-2",
            "stack.elements": ["root"],
          },
        },
        {
          id: "step-3",
          title: "Visit Left",
          description: "Visit left subtree node",
          duration: 1000,
          stateChanges: {
            "node-2.visited": true,
            "node-2.highlight": "order-2",
          },
        },
        {
          id: "step-4",
          title: "Pop Stack",
          description: "Return to parent",
          duration: 1000,
          stateChanges: { "current.target": "root", "stack.elements": [] },
        },
        {
          id: "step-5",
          title: "Go Right",
          description: "Traverse to right child",
          duration: 1000,
          stateChanges: { "current.target": "node-3" },
        },
      ],
    },
    educationalNotes: "Shows recursive nature of tree traversal",
  },
];

// =============================================================================
// Legacy Helper Functions (for compatibility only)
// =============================================================================

/** @deprecated Use listSimulationStarters from starter-catalog instead */
export function getSimulationPresetById(id: string): SimulationPreset | null {
  const normalizedId = id.trim().toLowerCase();
  const preset = SimulationPresets.find(
    (candidate) => candidate.id.toLowerCase() === normalizedId,
  );
  return preset ? structuredClone(preset) : null;
}

/** @deprecated Use listSimulationStarters from starter-catalog instead */
export function listLegacyRuntimePresetSummaries(
  input: {
    domain?: AutoSimulationRequest["domain"];
    status?: LegacyAutoRuntimeStatus;
    query?: string;
  } = {},
): LegacyAutoRuntimePresetSummary[] {
  // Use top-level import to avoid dynamic require() in ESM context
  const getSimulationStarterByLegacyPresetId = _getStarterByLegacyId;

  const query = input.query?.trim().toLowerCase();
  return SimulationPresets.map((preset): LegacyAutoRuntimePresetSummary => {
    const starter = getSimulationStarterByLegacyPresetId(preset.id);
    return {
      id: preset.id,
      name: preset.name,
      description: preset.description,
      domain: preset.domain,
      retirementStatus: starter
        ? "governed_starter_available"
        : "legacy_compatibility_only",
      ...(starter ? { starterId: starter.id, audience: starter.audience } : {}),
    };
  })
    .filter((preset) => (input.domain ? preset.domain === input.domain : true))
    .filter((preset) =>
      input.status ? preset.retirementStatus === input.status : true,
    )
    .filter((preset) =>
      query
        ? `${preset.name} ${preset.description} ${preset.domain}`
            .toLowerCase()
            .includes(query)
        : true,
    );
}

/** @deprecated Use getSimulationStarterCatalogSummary from starter-catalog instead */
export function getLegacyAutoRuntimeSummary(): LegacyAutoRuntimeSummary {
  const items = listLegacyRuntimePresetSummaries();
  const byDomain = items.reduce<
    Record<
      string,
      {
        total: number;
        governedStarterAvailable: number;
        compatibilityOnly: number;
      }
    >
  >((acc, item) => {
    const bucket = acc[item.domain] ?? {
      total: 0,
      governedStarterAvailable: 0,
      compatibilityOnly: 0,
    };
    bucket.total += 1;
    if (item.retirementStatus === "governed_starter_available") {
      bucket.governedStarterAvailable += 1;
    } else {
      bucket.compatibilityOnly += 1;
    }
    acc[item.domain] = bucket;
    return acc;
  }, {});

  return {
    totalPresets: items.length,
    governedStarterAvailable: items.filter(
      (item) => item.retirementStatus === "governed_starter_available",
    ).length,
    compatibilityOnly: items.filter(
      (item) => item.retirementStatus === "legacy_compatibility_only",
    ).length,
    byDomain,
    items,
  };
}
