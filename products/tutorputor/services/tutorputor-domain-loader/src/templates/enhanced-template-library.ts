/**
 * Expanded Template Library for Content Generation
 *
 * Provides specialized templates for niche topics and advanced concepts
 * across all domains with intelligent selection and governance.
 *
 * @doc.type module
 * @doc.purpose Expanded template library for specialized content generation
 * @doc.layer product
 * @doc.pattern TemplateLibrary
 */

import type {
  SimulationDomain,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";

/**
 * A loosely-typed manifest blueprint used by template authors.
 * Templates are blueprints/sketches — not finished SimulationManifest objects.
 * The manifest generator converts this into a proper SimulationManifest at
 * generation time (applying branded ids, strict action shapes, etc.).
 *
 * @doc.type alias
 * @doc.purpose Flexible template manifest that decouples authoring from strict runtime types
 * @doc.layer product
 * @doc.pattern ValueObject
 */
export type TemplateManifestBlueprint = {
  id?: string;
  domain?: SimulationDomain;
  title?: string;
  description?: string;
  canvas?: Record<string, unknown>;
  playback?: Record<string, unknown>;
  initialEntities?: Array<Record<string, unknown>>;
  steps?: Array<Record<string, unknown>>;
  accessibility?: Record<string, unknown>;
  [key: string]: unknown;
};

/**
 * Enhanced template metadata
 */
export interface EnhancedTemplateMetadata {
  /** Template identifier */
  id: string;
  /** Template name */
  name: string;
  /** Template description */
  description: string;
  /** Domain */
  domain: SimulationDomain;
  /** Difficulty level */
  difficulty: "beginner" | "intermediate" | "advanced" | "expert";
  /** Grade levels */
  gradeLevels: string[];
  /** Estimated completion time */
  estimatedTime: number;
  /** Prerequisites */
  prerequisites: string[];
  /** Learning objectives */
  learningObjectives: string[];
  /** Keywords for matching */
  keywords: string[];
  /** Content types included */
  contentTypes: {
    simulation: boolean;
    examples: boolean;
    animation: boolean;
  };
  /** Template version */
  version: string;
  /** Author information */
  author: {
    name: string;
    organization?: string;
    email?: string;
  };
  /** Quality metrics */
  quality: {
    confidenceScore: number;
    usageCount: number;
    successRate: number;
    averageRating: number;
  };
  /** Governance status */
  governance: {
    status: "draft" | "review" | "approved" | "deprecated";
    reviewedBy?: string;
    reviewedAt?: string;
    nextReviewDate?: string;
  };
  /** Template tags */
  tags: string[];
}

/**
 * Specialized template for Physics - Quantum Mechanics
 */
export const QUANTUM_MECHANICS_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "physics-quantum-mechanics-wave-function",
  name: "Quantum Wave Function Visualization",
  description:
    "Interactive visualization of quantum wave functions and probability distributions",
  domain: "PHYSICS",
  difficulty: "advanced",
  gradeLevels: ["11-12", "undergraduate"],
  estimatedTime: 1200, // 20 minutes
  prerequisites: ["Basic calculus", "Wave mechanics", "Probability theory"],
  learningObjectives: [
    "Understand wave function probability distributions",
    "Explore quantum superposition principles",
    "Visualize quantum tunneling effects",
    "Analyze measurement and collapse",
  ],
  keywords: [
    "quantum",
    "wave function",
    "probability",
    "superposition",
    "tunneling",
    "measurement",
  ],
  contentTypes: {
    simulation: true,
    examples: true,
    animation: true,
  },
  version: "1.0.0",
  author: {
    name: "Dr. Quantum Physics Team",
    organization: "TutorPutor Physics Division",
  },
  quality: {
    confidenceScore: 0.92,
    usageCount: 145,
    successRate: 96,
    averageRating: 4.7,
  },
  governance: {
    status: "approved",
    reviewedBy: "physics-review-board",
    reviewedAt: "2024-01-15T10:00:00Z",
    nextReviewDate: "2024-07-15T10:00:00Z",
  },
  tags: ["quantum-physics", "advanced", "visualization", "interactive"],
  manifest: {
    id: "quantum-wave-function",
    domain: "PHYSICS",
    title: "Quantum Wave Function Explorer",
    description: "Interactive exploration of quantum mechanical wave functions",
    canvas: {
      width: 1200,
      height: 800,
      backgroundColor: "#0a0a0a",
    },
    playback: {
      defaultSpeed: 1.0,
      allowSpeedChange: true,
      allowScrubbing: true,
      autoPlay: false,
      loop: true,
    },
    initialEntities: [
      {
        id: "wave-function",
        type: "quantumWave",
        label: "Wave Function ψ(x)",
        parameters: {
          amplitude: 1.0,
          wavelength: 2.0,
          phase: 0,
          probabilityDensity: true,
        },
        visual: {
          color: "#00ff88",
          opacity: 0.8,
          lineWidth: 3,
        },
      },
      {
        id: "probability-distribution",
        type: "probabilityDensity",
        label: "|ψ(x)|²",
        parameters: {
          normalization: true,
          fillArea: true,
        },
        visual: {
          color: "#ff8800",
          opacity: 0.6,
          fillColor: "#ff8800",
        },
      },
      {
        id: "potential-barrier",
        type: "potentialBarrier",
        label: "Potential Barrier V(x)",
        parameters: {
          height: 5.0,
          width: 0.5,
          position: 5.0,
        },
        visual: {
          color: "#ff0088",
          opacity: 0.9,
        },
      },
    ],
    steps: [
      {
        id: "initial-state",
        timestamp: 0,
        actions: [
          {
            type: "SET_PARAMETER",
            entityId: "wave-function",
            parameter: "amplitude",
            value: 1.0,
          },
          {
            type: "SET_PARAMETER",
            entityId: "probability-distribution",
            parameter: "normalization",
            value: true,
          },
        ],
        narration:
          "Initial quantum wave function with normalized probability distribution",
      },
      {
        id: "superposition",
        timestamp: 2000,
        actions: [
          {
            type: "SET_PARAMETER",
            entityId: "wave-function",
            parameter: "phase",
            value: Math.PI / 4,
          },
          {
            type: "CREATE_ENTITY",
            entity: {
              id: "second-wave",
              type: "quantumWave",
              label: "Second Wave Component",
              parameters: {
                amplitude: 0.7,
                wavelength: 2.0,
                phase: Math.PI / 2,
              },
              visual: {
                color: "#0088ff",
                opacity: 0.6,
              },
            },
          },
        ],
        narration: "Creating quantum superposition of two wave states",
      },
      {
        id: "tunneling",
        timestamp: 4000,
        actions: [
          {
            type: "SET_PARAMETER",
            entityId: "potential-barrier",
            parameter: "height",
            value: 3.0,
          },
          {
            type: "APPLY_EFFECT",
            entityId: "wave-function",
            effect: "tunneling",
            parameters: {
              barrierId: "potential-barrier",
              transmissionProbability: 0.3,
            },
          },
        ],
        narration: "Demonstrating quantum tunneling through potential barrier",
      },
      {
        id: "measurement",
        timestamp: 6000,
        actions: [
          {
            type: "COLLAPSE_WAVE_FUNCTION",
            entityId: "wave-function",
            position: 6.5,
            certainty: 0.95,
          },
          {
            type: "SET_PARAMETER",
            entityId: "probability-distribution",
            parameter: "fillArea",
            value: false,
          },
        ],
        narration: "Wave function collapse upon measurement",
      },
    ],
  },
};

/**
 * Specialized template for Chemistry - Organic Reaction Mechanisms
 */
export const ORGANIC_REACTION_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "chemistry-organic-sn2-reaction",
  name: "SN2 Reaction Mechanism",
  description:
    "Step-by-step visualization of SN2 nucleophilic substitution reactions",
  domain: "CHEMISTRY",
  difficulty: "intermediate",
  gradeLevels: ["10-12", "undergraduate"],
  estimatedTime: 900, // 15 minutes
  prerequisites: [
    "Basic organic chemistry",
    "Electronegativity",
    "Molecular geometry",
  ],
  learningObjectives: [
    "Understand SN2 reaction mechanism",
    "Identify nucleophiles and electrophiles",
    "Analyze stereochemistry changes",
    "Explore reaction energy profile",
  ],
  keywords: [
    "SN2",
    "nucleophilic substitution",
    "organic chemistry",
    "stereochemistry",
    "mechanism",
  ],
  contentTypes: {
    simulation: true,
    examples: true,
    animation: true,
  },
  version: "1.1.0",
  author: {
    name: "Organic Chemistry Education Team",
  },
  quality: {
    confidenceScore: 0.89,
    usageCount: 89,
    successRate: 94,
    averageRating: 4.5,
  },
  governance: {
    status: "approved",
    reviewedBy: "chemistry-review-board",
    reviewedAt: "2024-02-01T14:30:00Z",
    nextReviewDate: "2024-08-01T14:30:00Z",
  },
  tags: ["organic-chemistry", "reaction-mechanism", "SN2", "stereochemistry"],
  manifest: {
    id: "organic-sn2-reaction",
    domain: "CHEMISTRY",
    title: "SN2 Nucleophilic Substitution",
    description: "Interactive SN2 reaction mechanism with stereochemistry",
    canvas: {
      width: 1000,
      height: 700,
      backgroundColor: "#f8f8f8",
    },
    playback: {
      defaultSpeed: 1.0,
      allowSpeedChange: true,
      allowScrubbing: true,
      autoPlay: false,
      loop: false,
    },
    initialEntities: [
      {
        id: "substrate",
        type: "organicMolecule",
        label: "Alkyl Halide Substrate",
        structure: {
          centralAtom: "C",
          substituents: ["H", "H", "H", "Br"],
          geometry: "tetrahedral",
        },
        position: { x: 300, y: 350 },
        visual: {
          color: "#333333",
          bondColor: "#666666",
          atomSize: 20,
        },
      },
      {
        id: "nucleophile",
        type: "chemicalSpecies",
        label: "OH⁻ Nucleophile",
        charge: -1,
        composition: ["O", "H"],
        position: { x: 100, y: 350 },
        visual: {
          color: "#ff0000",
          size: 25,
        },
      },
      {
        id: "leaving-group",
        type: "chemicalSpecies",
        label: "Br⁻ Leaving Group",
        charge: -1,
        composition: ["Br"],
        visual: {
          color: "#8b4513",
          size: 30,
        },
      },
      {
        id: "energy-diagram",
        type: "energyProfile",
        label: "Reaction Energy Profile",
        position: { x: 700, y: 100 },
        dimensions: { width: 250, height: 200 },
        visual: {
          color: "#0066cc",
          gridColor: "#cccccc",
        },
      },
    ],
    steps: [
      {
        id: "reactants",
        timestamp: 0,
        actions: [
          {
            type: "SET_POSITION",
            entityId: "nucleophile",
            position: { x: 100, y: 350 },
          },
          {
            type: "SHOW_LABEL",
            entityId: "substrate",
            label: "CH₃Br (Substrate)",
          },
        ],
        narration: "Initial state with nucleophile approaching substrate",
      },
      {
        id: "approach",
        timestamp: 2000,
        actions: [
          {
            type: "MOVE_ENTITY",
            entityId: "nucleophile",
            position: { x: 200, y: 350 },
            duration: 2000,
          },
          {
            type: "HIGHLIGHT_BOND",
            entityId: "substrate",
            bond: "C-Br",
            color: "#ff6600",
          },
        ],
        narration: "Nucleophile approaches from backside, C-Br bond weakens",
      },
      {
        id: "transition-state",
        timestamp: 4000,
        actions: [
          {
            type: "SET_TRANSITION_STATE",
            entityId: "substrate",
            geometry: "trigonal_bipyramidal",
          },
          {
            type: "UPDATE_ENERGY_DIAGRAM",
            entityId: "energy-diagram",
            point: { x: 125, y: 50 },
            label: "Transition State",
          },
        ],
        narration: "Transition state with pentavalent carbon",
      },
      {
        id: "products",
        timestamp: 6000,
        actions: [
          {
            type: "BREAK_BOND",
            entityId: "substrate",
            bond: "C-Br",
          },
          {
            type: "FORM_BOND",
            entityId: "substrate",
            atoms: ["C", "O"],
          },
          {
            type: "MOVE_ENTITY",
            entityId: "leaving-group",
            position: { x: 500, y: 350 },
            duration: 1000,
          },
        ],
        narration: "Products formed: CH₃OH and Br⁻",
      },
    ],
  },
};

/**
 * Specialized template for Biology - CRISPR Gene Editing
 */
export const CRISPR_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "biology-crispr-gene-editing",
  name: "CRISPR-Cas9 Gene Editing",
  description: "Interactive simulation of CRISPR-Cas9 gene editing mechanism",
  domain: "BIOLOGY",
  difficulty: "advanced",
  gradeLevels: ["11-12", "undergraduate", "graduate"],
  estimatedTime: 1500, // 25 minutes
  prerequisites: [
    "Molecular biology",
    "DNA structure",
    "Gene expression",
    "Protein synthesis",
  ],
  learningObjectives: [
    "Understand CRISPR-Cas9 mechanism",
    "Explore guide RNA design",
    "Analyze DNA cleavage and repair",
    "Investigate off-target effects",
  ],
  keywords: [
    "CRISPR",
    "Cas9",
    "gene editing",
    "DNA",
    "RNA",
    "genetic engineering",
  ],
  contentTypes: {
    simulation: true,
    examples: true,
    animation: true,
  },
  version: "1.2.0",
  author: {
    name: "Molecular Biology Education Team",
    organization: "TutorPutor Bio Division",
  },
  quality: {
    confidenceScore: 0.94,
    usageCount: 203,
    successRate: 97,
    averageRating: 4.8,
  },
  governance: {
    status: "approved",
    reviewedBy: "biology-review-board",
    reviewedAt: "2024-01-20T09:00:00Z",
    nextReviewDate: "2024-07-20T09:00:00Z",
  },
  tags: ["molecular-biology", "genetic-engineering", "CRISPR", "biotechnology"],
  manifest: {
    id: "crispr-cas9-simulation",
    domain: "BIOLOGY",
    title: "CRISPR-Cas9 Gene Editing Mechanism",
    description: "Interactive simulation of CRISPR-Cas9 gene editing process",
    canvas: {
      width: 1400,
      height: 900,
      backgroundColor: "#ffffff",
    },
    playback: {
      defaultSpeed: 1.0,
      allowSpeedChange: true,
      allowScrubbing: true,
      autoPlay: false,
      loop: false,
    },
    initialEntities: [
      {
        id: "dna-double-helix",
        type: "dnaStructure",
        label: "Target DNA Sequence",
        sequence: "ATGCGATCGTAGCTAGCTAG",
        position: { x: 200, y: 300 },
        dimensions: { width: 800, height: 300 },
        visual: {
          strandColors: { sense: "#2196F3", antisense: "#F44336" },
          baseColors: {
            A: "#4CAF50",
            T: "#FF9800",
            G: "#9C27B0",
            C: "#00BCD4",
          },
        },
      },
      {
        id: "cas9-protein",
        type: "protein",
        label: "Cas9 Nuclease",
        position: { x: 100, y: 450 },
        visual: {
          color: "#795548",
          size: 80,
          structure: "detailed",
        },
      },
      {
        id: "guide-rna",
        type: "rnaMolecule",
        label: "Guide RNA (gRNA)",
        sequence: "CGATCGTAGC",
        position: { x: 100, y: 350 },
        visual: {
          color: "#4CAF50",
          secondaryStructure: true,
        },
      },
      {
        id: "pam-sequence",
        type: "dnaSequence",
        label: "PAM Sequence (NGG)",
        sequence: "AGG",
        position: { x: 600, y: 300 },
        visual: {
          highlightColor: "#FFD700",
          bold: true,
        },
      },
      {
        id: "repair-pathways",
        type: "processDiagram",
        label: "DNA Repair Pathways",
        position: { x: 1100, y: 200 },
        dimensions: { width: 250, height: 400 },
        visual: {
          pathwayColors: {
            nhej: "#FF5722",
            hdr: "#3F51B5",
          },
        },
      },
    ],
    steps: [
      {
        id: "initial-binding",
        timestamp: 0,
        actions: [
          {
            type: "MOVE_ENTITY",
            entityId: "cas9-protein",
            position: { x: 300, y: 450 },
            duration: 2000,
          },
          {
            type: "MOVE_ENTITY",
            entityId: "guide-rna",
            position: { x: 300, y: 350 },
            duration: 2000,
          },
        ],
        narration: "Cas9 protein with guide RNA approaches target DNA",
      },
      {
        id: "pam-recognition",
        timestamp: 3000,
        actions: [
          {
            type: "HIGHLIGHT_SEQUENCE",
            entityId: "pam-sequence",
            color: "#FFD700",
            duration: 2000,
          },
          {
            type: "BIND_TO_PAM",
            entityId: "cas9-protein",
            target: "pam-sequence",
          },
        ],
        narration: "Cas9 recognizes and binds to PAM sequence",
      },
      {
        id: "dna-unwinding",
        timestamp: 6000,
        actions: [
          {
            type: "UNWIND_DNA",
            entityId: "dna-double-helix",
            region: { start: 8, end: 18 },
            duration: 3000,
          },
          {
            type: "HYBRIDIZE_RNA",
            entityId: "guide-rna",
            target: "dna-double-helix",
            region: { start: 8, end: 18 },
          },
        ],
        narration: "DNA unwinds and guide RNA hybridizes with target sequence",
      },
      {
        id: "dna-cleavage",
        timestamp: 10000,
        actions: [
          {
            type: "CLEAVE_DNA",
            entityId: "cas9-protein",
            target: "dna-double-helix",
            positions: [10, 13],
            duration: 2000,
          },
          {
            action: "CREATE_BREAK",
            entityId: "dna-double-helix",
            position: 11.5,
            breakType: "double_strand_break",
          },
        ],
        narration: "Cas9 creates double-strand break at target site",
      },
      {
        id: "repair-initiation",
        timestamp: 13000,
        actions: [
          {
            type: "ACTIVATE_PATHWAY",
            entityId: "repair-pathways",
            pathway: "nhej",
            probability: 0.7,
          },
          {
            type: "ACTIVATE_PATHWAY",
            entityId: "repair-pathways",
            pathway: "hdr",
            probability: 0.3,
          },
        ],
        narration: "Cell initiates DNA repair pathways (NHEJ or HDR)",
      },
    ],
  },
};

// =============================================================================
// Mathematics Template – Differential Calculus Visualizer
// =============================================================================

export const CALCULUS_DERIVATIVES_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "mathematics-calculus-derivatives",
  name: "Differential Calculus Visualizer",
  description:
    "Interactive exploration of derivatives, tangent lines, and rate-of-change for common function families",
  domain: "MATHEMATICS",
  difficulty: "intermediate",
  gradeLevels: ["10-12", "undergraduate"],
  estimatedTime: 900, // 15 minutes
  prerequisites: ["Algebra", "Functions and graphs", "Limits (conceptual)"],
  learningObjectives: [
    "Define the derivative as an instantaneous rate of change",
    "Graphically interpret the slope of a tangent line",
    "Apply power, product, and chain rules interactively",
    "Connect f(x), f'(x), and f''(x) through dynamic graphs",
  ],
  keywords: [
    "calculus",
    "derivative",
    "tangent line",
    "rate of change",
    "differentiation",
    "concavity",
    "critical points",
  ],
  contentTypes: { simulation: true, examples: true, animation: true },
  version: "1.0.0",
  author: { name: "TutorPutor Math Team", organization: "TutorPutor Mathematics Division" },
  quality: { confidenceScore: 0.9, usageCount: 0, successRate: 0, averageRating: 0 },
  governance: {
    status: "approved",
    reviewedBy: "mathematics-review-board",
    reviewedAt: "2025-01-01T00:00:00Z",
    nextReviewDate: "2025-07-01T00:00:00Z",
  },
  tags: ["calculus", "mathematics", "intermediate", "visualization"],
  manifest: {
    id: "calculus-derivatives",
    domain: "MATHEMATICS",
    title: "Differential Calculus Visualizer",
    description: "Dynamic graphing environment for exploring derivatives and rate of change",
    canvas: { width: 1200, height: 800, backgroundColor: "#f8f9fa" },
    playback: { defaultSpeed: 1.0, allowSpeedChange: true, allowScrubbing: true, autoPlay: false },
    entities: [
      {
        id: "function-curve",
        type: "MATHEMATICAL_FUNCTION",
        initialState: {
          expression: "x^2",
          color: "#3b82f6",
          domain: [-5, 5],
        },
      },
      {
        id: "tangent-line",
        type: "DERIVED_ELEMENT",
        initialState: { x0: 1, color: "#ef4444", visible: true },
      },
    ],
    events: [
      {
        id: "drag-tangent-point",
        timestamp: 0,
        actions: [{ type: "ENABLE_DRAG", entityId: "tangent-line", axis: "x" }],
        narration: "Drag the tangent point along the curve to observe how the slope changes",
      },
    ],
  },
};

// =============================================================================
// Physics Template – Electromagnetic Field Visualization
// =============================================================================

export const ELECTROMAGNETIC_FIELDS_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "physics-electromagnetic-fields",
  name: "Electromagnetic Field Lines Simulator",
  description:
    "Visualize electric and magnetic field lines for various charge and current configurations",
  domain: "PHYSICS",
  difficulty: "intermediate",
  gradeLevels: ["11-12", "undergraduate"],
  estimatedTime: 1080, // 18 minutes
  prerequisites: ["Coulomb's Law", "Basic vectors", "Ohm's Law"],
  learningObjectives: [
    "Draw and interpret electric field lines for point charges",
    "Apply superposition to multi-charge systems",
    "Understand the relationship between field strength and line density",
    "Explore magnetic fields produced by current-carrying wires",
  ],
  keywords: [
    "electromagnetism",
    "electric field",
    "magnetic field",
    "field lines",
    "Coulomb",
    "Gauss",
    "superposition",
  ],
  contentTypes: { simulation: true, examples: true, animation: false },
  version: "1.0.0",
  author: { name: "TutorPutor Physics Team", organization: "TutorPutor Physics Division" },
  quality: { confidenceScore: 0.88, usageCount: 0, successRate: 0, averageRating: 0 },
  governance: {
    status: "approved",
    reviewedBy: "physics-review-board",
    reviewedAt: "2025-01-01T00:00:00Z",
    nextReviewDate: "2025-07-01T00:00:00Z",
  },
  tags: ["physics", "electromagnetism", "intermediate", "field-visualization"],
  manifest: {
    id: "electromagnetic-fields",
    domain: "PHYSICS",
    title: "Electromagnetic Field Lines Simulator",
    description: "Interactive simulation of electric and magnetic fields",
    canvas: { width: 1200, height: 800, backgroundColor: "#0d1117" },
    playback: { defaultSpeed: 1.0, allowSpeedChange: true, allowScrubbing: false, autoPlay: true },
    entities: [
      {
        id: "positive-charge",
        type: "POINT_CHARGE",
        initialState: { charge: 1, x: 300, y: 400, color: "#ef4444" },
      },
      {
        id: "negative-charge",
        type: "POINT_CHARGE",
        initialState: { charge: -1, x: 900, y: 400, color: "#3b82f6" },
      },
      {
        id: "field-lines",
        type: "FIELD_LINE_RENDERER",
        initialState: { numLines: 16, color: "#facc15" },
      },
    ],
    events: [
      {
        id: "render-initial-field",
        timestamp: 0,
        actions: [{ type: "RENDER_FIELD_LINES", entityId: "field-lines", sources: ["positive-charge", "negative-charge"] }],
        narration: "Field lines run from the positive to the negative charge — observe the density near each source",
      },
    ],
  },
};

// =============================================================================
// Chemistry Template – Acid-Base Titration
// =============================================================================

export const ACID_BASE_TITRATION_TEMPLATE: EnhancedTemplateMetadata & {
  manifest: TemplateManifestBlueprint;
} = {
  id: "chemistry-acid-base-titration",
  name: "Acid-Base Titration Simulator",
  description:
    "Step-by-step titration of a weak acid with a strong base, plotting the pH curve in real time",
  domain: "CHEMISTRY",
  difficulty: "intermediate",
  gradeLevels: ["10-12", "undergraduate"],
  estimatedTime: 900, // 15 minutes
  prerequisites: ["pH scale", "Acid-base theory", "Stoichiometry basics"],
  learningObjectives: [
    "Understand the equivalence point concept",
    "Interpret a pH titration curve",
    "Identify buffer regions in weak-acid titrations",
    "Select an appropriate indicator for a given titration",
  ],
  keywords: [
    "titration",
    "pH",
    "acid",
    "base",
    "equivalence point",
    "buffer",
    "indicator",
    "neutralization",
  ],
  contentTypes: { simulation: true, examples: true, animation: false },
  version: "1.0.0",
  author: { name: "TutorPutor Chemistry Team", organization: "TutorPutor Chemistry Division" },
  quality: { confidenceScore: 0.91, usageCount: 0, successRate: 0, averageRating: 0 },
  governance: {
    status: "approved",
    reviewedBy: "chemistry-review-board",
    reviewedAt: "2025-01-01T00:00:00Z",
    nextReviewDate: "2025-07-01T00:00:00Z",
  },
  tags: ["chemistry", "titration", "acid-base", "intermediate", "laboratory"],
  manifest: {
    id: "acid-base-titration",
    domain: "CHEMISTRY",
    title: "Acid-Base Titration Simulator",
    description: "Real-time pH curve plotting during weak-acid / strong-base titration",
    canvas: { width: 1200, height: 800, backgroundColor: "#ffffff" },
    playback: { defaultSpeed: 1.0, allowSpeedChange: true, allowScrubbing: true, autoPlay: false },
    entities: [
      { id: "burette", type: "LABORATORY_EQUIPMENT", initialState: { reagent: "NaOH", concentration: 0.1, volume_mL: 50 } },
      { id: "flask", type: "LABORATORY_EQUIPMENT", initialState: { reagent: "CH3COOH", concentration: 0.1, volume_mL: 25, indicator: "phenolphthalein" } },
      { id: "ph-curve", type: "REAL_TIME_GRAPH", initialState: { xAxis: "Volume NaOH added (mL)", yAxis: "pH", color: "#6366f1" } },
    ],
    events: [
      {
        id: "start-titration",
        timestamp: 0,
        actions: [{ type: "ENABLE_DRAG", entityId: "burette", axis: "y" }],
        narration: "Open the burette stopcock to add NaOH drop-by-drop and watch the pH curve evolve",
      },
      {
        id: "near-equivalence",
        timestamp: 8000,
        actions: [{ type: "HIGHLIGHT_REGION", entityId: "ph-curve", regionLabel: "Equivalence Point" }],
        narration: "Observe the steep pH jump at the equivalence point — this signals complete neutralization",
      },
    ],
  },
};

/**
 * Template Library Manager
 */
export class EnhancedTemplateLibrary {
  private templates: Map<
    string,
    EnhancedTemplateMetadata & { manifest: TemplateManifestBlueprint }
  >;
  private domainIndex: Map<SimulationDomain, string[]>;
  private keywordIndex: Map<string, string[]>;
  private difficultyIndex: Map<string, string[]>;

  constructor() {
    this.templates = new Map();
    this.domainIndex = new Map();
    this.keywordIndex = new Map();
    this.difficultyIndex = new Map();
    this.initializeTemplates();
  }

  /**
   * Get template by ID
   */
  getTemplate(
    id: string,
  ):
    | (EnhancedTemplateMetadata & { manifest: TemplateManifestBlueprint })
    | undefined {
    return this.templates.get(id);
  }

  /**
   * Get all templates in the library.
   */
  getTemplates(): (EnhancedTemplateMetadata & { manifest: TemplateManifestBlueprint })[] {
    return Array.from(this.templates.values());
  }

  /**
   * Search templates by criteria
   */
  searchTemplates(
    criteria: TemplateSearchCriteria,
  ): (EnhancedTemplateMetadata & { manifest: TemplateManifestBlueprint })[] {
    let results: (EnhancedTemplateMetadata & {
      manifest: TemplateManifestBlueprint;
    })[] = [];

    // Start with all templates
    results = Array.from(this.templates.values());

    // Filter by domain
    if (criteria.domain) {
      results = results.filter(
        (template) => template.domain === criteria.domain,
      );
    }

    // Filter by difficulty
    if (criteria.difficulty) {
      results = results.filter(
        (template) => template.difficulty === criteria.difficulty,
      );
    }

    // Filter by grade level
    if (criteria.gradeLevel) {
      results = results.filter((template) =>
        template.gradeLevels.some((grade) =>
          grade.includes(criteria.gradeLevel!),
        ),
      );
    }

    // Filter by keywords
    if (criteria.keywords && criteria.keywords.length > 0) {
      results = results.filter((template) =>
        criteria.keywords!.some((keyword) =>
          template.keywords.some((templateKeyword) =>
            templateKeyword.toLowerCase().includes(keyword.toLowerCase()),
          ),
        ),
      );
    }

    // Filter by content types
    if (criteria.contentTypes) {
      results = results.filter((template) =>
        Object.entries(criteria.contentTypes!).every(([type, required]) =>
          required
            ? template.contentTypes[type as keyof typeof template.contentTypes]
            : true,
        ),
      );
    }

    // Filter by governance status
    if (criteria.governanceStatus) {
      results = results.filter(
        (template) => template.governance.status === criteria.governanceStatus,
      );
    }

    // Sort by quality score
    results.sort(
      (a, b) => b.quality.confidenceScore - a.quality.confidenceScore,
    );

    return results;
  }

  /**
   * Get recommended templates for a concept
   */
  getRecommendedTemplates(
    concept: ConceptDescription,
  ): (EnhancedTemplateMetadata & { manifest: TemplateManifestBlueprint })[] {
    const criteria: TemplateSearchCriteria = {
      domain: concept.domain,
      keywords: [...concept.keywords, ...concept.topics],
      gradeLevel: concept.gradeLevel,
      difficulty: concept.difficulty,
      contentTypes: concept.requiredContentTypes,
    };

    return this.searchTemplates(criteria).slice(0, 5); // Return top 5 recommendations
  }

  /**
   * Add new template
   */
  addTemplate(
    template: EnhancedTemplateMetadata & {
      manifest: TemplateManifestBlueprint;
    },
  ): void {
    this.templates.set(template.id, template);
    this.updateIndexes(template);
  }

  /**
   * Update template
   */
  updateTemplate(
    id: string,
    updates: Partial<EnhancedTemplateMetadata>,
  ): boolean {
    const template = this.templates.get(id);
    if (!template) return false;

    const updatedTemplate = { ...template, ...updates };
    this.templates.set(id, updatedTemplate);
    this.updateIndexes(updatedTemplate);

    return true;
  }

  /**
   * Get template usage statistics
   */
  getUsageStatistics(): TemplateUsageStatistics {
    const stats: TemplateUsageStatistics = {
      totalTemplates: this.templates.size,
      domainDistribution: {
        CS_DISCRETE: 0,
        PHYSICS: 0,
        ECONOMICS: 0,
        CHEMISTRY: 0,
        BIOLOGY: 0,
        MEDICINE: 0,
        ENGINEERING: 0,
        MATHEMATICS: 0,
      },
      difficultyDistribution: {},
      averageQualityScore: 0,
      mostUsedTemplates: [],
      recentlyAdded: [],
    };

    let totalQualityScore = 0;

    this.templates.forEach((template) => {
      // Domain distribution
      const domain = template.domain;
      stats.domainDistribution[domain] =
        (stats.domainDistribution[domain] || 0) + 1;

      // Difficulty distribution
      const difficulty = template.difficulty;
      stats.difficultyDistribution[difficulty] =
        (stats.difficultyDistribution[difficulty] || 0) + 1;

      // Quality score
      totalQualityScore += template.quality.confidenceScore;
    });

    stats.averageQualityScore = totalQualityScore / this.templates.size;

    // Most used templates
    stats.mostUsedTemplates = Array.from(this.templates.values())
      .sort((a, b) => b.quality.usageCount - a.quality.usageCount)
      .slice(0, 10)
      .map((template) => ({
        id: template.id,
        name: template.name,
        usageCount: template.quality.usageCount,
        successRate: template.quality.successRate,
      }));

    return stats;
  }

  /**
   * Initialize templates with built-in templates
   */
  private initializeTemplates(): void {
    // Add specialized templates
    this.addTemplate(QUANTUM_MECHANICS_TEMPLATE);
    this.addTemplate(ORGANIC_REACTION_TEMPLATE);
    this.addTemplate(CRISPR_TEMPLATE);
    this.addTemplate(CALCULUS_DERIVATIVES_TEMPLATE);
    this.addTemplate(ELECTROMAGNETIC_FIELDS_TEMPLATE);
    this.addTemplate(ACID_BASE_TITRATION_TEMPLATE);
  }

  /**
   * Update search indexes
   */
  private updateIndexes(
    template: EnhancedTemplateMetadata & {
      manifest: TemplateManifestBlueprint;
    },
  ): void {
    // Update domain index
    if (!this.domainIndex.has(template.domain)) {
      this.domainIndex.set(template.domain, []);
    }
    this.domainIndex.get(template.domain)!.push(template.id);

    // Update keyword index
    template.keywords.forEach((keyword) => {
      if (!this.keywordIndex.has(keyword)) {
        this.keywordIndex.set(keyword, []);
      }
      this.keywordIndex.get(keyword)!.push(template.id);
    });

    // Update difficulty index
    if (!this.difficultyIndex.has(template.difficulty)) {
      this.difficultyIndex.set(template.difficulty, []);
    }
    this.difficultyIndex.get(template.difficulty)!.push(template.id);
  }
}

/**
 * Template search criteria
 */
export interface TemplateSearchCriteria {
  domain?: SimulationDomain;
  difficulty?: "beginner" | "intermediate" | "advanced" | "expert";
  gradeLevel?: string;
  keywords?: string[];
  contentTypes?: {
    simulation?: boolean;
    examples?: boolean;
    animation?: boolean;
  };
  governanceStatus?: "draft" | "review" | "approved" | "deprecated";
}

/**
 * Concept description for template recommendation
 */
export interface ConceptDescription {
  domain: SimulationDomain;
  topics: string[];
  keywords: string[];
  gradeLevel: string;
  difficulty: "beginner" | "intermediate" | "advanced" | "expert";
  requiredContentTypes: {
    simulation?: boolean;
    examples?: boolean;
    animation?: boolean;
  };
}

/**
 * Template usage statistics
 */
export interface TemplateUsageStatistics {
  totalTemplates: number;
  domainDistribution: Record<SimulationDomain, number>;
  difficultyDistribution: Record<string, number>;
  averageQualityScore: number;
  mostUsedTemplates: Array<{
    id: string;
    name: string;
    usageCount: number;
    successRate: number;
  }>;
  recentlyAdded: Array<{
    id: string;
    name: string;
    addedDate: string;
  }>;
}

/**
 * Global template library instance
 */
export const templateLibrary = new EnhancedTemplateLibrary();
