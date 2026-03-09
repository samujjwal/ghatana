/**
 * Prompt Packs for Domain-Specific Simulation Generation
 * 
 * @doc.type module
 * @doc.purpose Provide few-shot examples and prompts for each simulation domain
 * @doc.layer product
 * @doc.pattern Configuration
 */

import type { SimulationDomain } from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Prompt pack for a specific domain.
 */
export interface PromptPack {
  domain: SimulationDomain;
  systemPrompt: string;
  fewShotExamples: Array<{
    userPrompt: string;
    assistantResponse: string;
  }>;
  constraints: string[];
  entityTypes: string[];
  actionTypes: string[];
}

/**
 * CS Discrete prompt pack for algorithms and data structures.
 */
export const CS_DISCRETE_PROMPT_PACK: PromptPack = {
  domain: "CS_DISCRETE",
  systemPrompt: `You are an expert in creating educational algorithm visualizations.
Your task is to generate a SimulationManifest JSON that visualizes algorithms and data structures.

Available entity types:
- node: A data element with value, position, and visual state (highlighted, visited, comparing, sorted)
- edge: A connection between nodes (directed/undirected, weighted)
- pointer: An indicator pointing to a node (i, j, pivot, etc.)

Available actions:
- CREATE_ENTITY: Add a new entity
- REMOVE_ENTITY: Remove an entity
- MOVE: Move an entity to a new position
- HIGHLIGHT: Highlight one or more entities
- COMPARE: Compare two nodes
- SWAP: Swap two nodes
- SET_VALUE: Change an entity's value
- ANNOTATE: Add explanatory text

Guidelines:
1. Break down the algorithm into clear, discrete steps
2. Include narration for each step explaining what's happening
3. Use appropriate easing for smooth animations
4. Mark important steps as checkpoints for assessment hooks
5. Ensure visual clarity with proper spacing and positioning`,

  fewShotExamples: [
    {
      userPrompt: "Create a bubble sort visualization for the array [5, 2, 8, 1, 9]",
      assistantResponse: JSON.stringify({
        id: "sim_bubble_sort_001",
        version: "1.0.0",
        title: "Bubble Sort Visualization",
        description: "Visual demonstration of the bubble sort algorithm",
        domain: "CS_DISCRETE",
        canvas: { width: 800, height: 400, backgroundColor: "#1a1a2e" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          { id: "node_0", type: "node", value: 5, x: 100, y: 200, shape: "rect" },
          { id: "node_1", type: "node", value: 2, x: 200, y: 200, shape: "rect" },
          { id: "node_2", type: "node", value: 8, x: 300, y: 200, shape: "rect" },
          { id: "node_3", type: "node", value: 1, x: 400, y: 200, shape: "rect" },
          { id: "node_4", type: "node", value: 9, x: 500, y: 200, shape: "rect" },
          { id: "ptr_i", type: "pointer", targetId: "node_0", x: 100, y: 280, pointerLabel: "i" },
          { id: "ptr_j", type: "pointer", targetId: "node_1", x: 200, y: 280, pointerLabel: "j" }
        ],
        steps: [
          {
            id: "step_1",
            orderIndex: 0,
            title: "Compare first pair",
            description: "Compare elements at positions 0 and 1",
            narration: "We start by comparing the first two elements: 5 and 2",
            actions: [
              { action: "COMPARE", leftId: "node_0", rightId: "node_1", result: "greater" },
              { action: "HIGHLIGHT", targetIds: ["node_0", "node_1"], style: "primary" }
            ]
          },
          {
            id: "step_2",
            orderIndex: 1,
            title: "Swap elements",
            description: "5 > 2, so we swap them",
            narration: "Since 5 is greater than 2, we swap their positions",
            actions: [
              { action: "SWAP", id1: "node_0", id2: "node_1", duration: 500, easing: "easeInOut" }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    },
    {
      userPrompt: "Visualize DFS traversal on a binary tree",
      assistantResponse: JSON.stringify({
        id: "sim_dfs_tree_001",
        version: "1.0.0",
        title: "Depth-First Search on Binary Tree",
        description: "Pre-order DFS traversal visualization",
        domain: "CS_DISCRETE",
        canvas: { width: 800, height: 500, backgroundColor: "#1a1a2e" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          { id: "node_A", type: "node", value: "A", x: 400, y: 50, shape: "circle" },
          { id: "node_B", type: "node", value: "B", x: 200, y: 150, shape: "circle" },
          { id: "node_C", type: "node", value: "C", x: 600, y: 150, shape: "circle" },
          { id: "edge_AB", type: "edge", sourceId: "node_A", targetId: "node_B", directed: true },
          { id: "edge_AC", type: "edge", sourceId: "node_A", targetId: "node_C", directed: true }
        ],
        steps: [
          {
            id: "step_1",
            orderIndex: 0,
            title: "Visit root",
            narration: "DFS starts at the root node A",
            actions: [
              { action: "HIGHLIGHT", targetIds: ["node_A"], style: "success" },
              { action: "SET_VALUE", targetId: "node_A", property: "visited", value: true }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Maximum 50 steps per simulation",
    "Maximum 100 entities",
    "Node values must be strings or numbers",
    "All entity IDs must be unique"
  ],

  entityTypes: ["node", "edge", "pointer"],
  actionTypes: ["CREATE_ENTITY", "REMOVE_ENTITY", "MOVE", "HIGHLIGHT", "COMPARE", "SWAP", "SET_VALUE", "ANNOTATE"]
};

/**
 * Physics prompt pack for mechanics simulations.
 */
export const PHYSICS_PROMPT_PACK: PromptPack = {
  domain: "PHYSICS",
  systemPrompt: `You are an expert in creating physics simulations for education.
Your task is to generate a SimulationManifest JSON that visualizes physics concepts.

Available entity types:
- rigidBody: A physical object with mass, velocity, and forces
- spring: An elastic connector between bodies
- vector: Visual representation of velocity, acceleration, or force
- particle: Small moving object for effects

Available actions:
- CREATE_ENTITY: Add a new entity
- SET_INITIAL_VELOCITY: Set initial velocity on a body
- APPLY_FORCE: Apply a force to a body
- CONNECT_SPRING: Connect two bodies with a spring
- RELEASE: Release a held body
- SET_GRAVITY: Change gravity settings

Guidelines:
1. Use realistic physics parameters (SI units)
2. Include vector visualizations for forces and velocities
3. Add annotations explaining physical concepts
4. Set appropriate time scales for visibility
5. Include energy or momentum displays where relevant`,

  fewShotExamples: [
    {
      userPrompt: "Simulate projectile motion with initial velocity 20 m/s at 45 degrees",
      assistantResponse: JSON.stringify({
        id: "sim_projectile_001",
        version: "1.0.0",
        title: "Projectile Motion",
        description: "A ball launched at 45 degrees demonstrating parabolic trajectory",
        domain: "PHYSICS",
        domainMetadata: {
          domain: "PHYSICS",
          physics: {
            gravity: { x: 0, y: -9.81 },
            units: { length: "m", mass: "kg", time: "s" }
          }
        },
        canvas: { width: 1000, height: 600, backgroundColor: "#0a0a1a" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          {
            id: "ball",
            type: "rigidBody",
            x: 50,
            y: 500,
            mass: 1,
            velocityX: 14.14,
            velocityY: -14.14,
            shape: "circle",
            width: 20,
            color: "#ff6b6b"
          },
          {
            id: "velocity_vector",
            type: "vector",
            attachId: "ball",
            magnitude: 20,
            angle: -45,
            vectorType: "velocity",
            x: 50,
            y: 500,
            color: "#4ecdc4"
          }
        ],
        steps: [
          {
            id: "step_launch",
            orderIndex: 0,
            title: "Launch",
            narration: "The ball is launched with velocity 20 m/s at 45 degrees",
            actions: [
              { action: "RELEASE", targetId: "ball" },
              { action: "ANNOTATE", text: "v₀ = 20 m/s, θ = 45°", position: "top" }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Use SI units (meters, kilograms, seconds)",
    "Maximum simulation time 60 seconds",
    "Maximum 20 rigid bodies",
    "Gravity should be realistic (-9.81 m/s² on Earth)"
  ],

  entityTypes: ["rigidBody", "spring", "vector", "particle"],
  actionTypes: ["CREATE_ENTITY", "SET_INITIAL_VELOCITY", "APPLY_FORCE", "CONNECT_SPRING", "RELEASE", "SET_GRAVITY"]
};

/**
 * Economics prompt pack for system dynamics.
 */
export const ECONOMICS_PROMPT_PACK: PromptPack = {
  domain: "ECONOMICS",
  systemPrompt: `You are an expert in creating system dynamics simulations for economics education.
Your task is to generate a SimulationManifest JSON that visualizes economic and feedback systems.

Available entity types:
- stock: A container that accumulates quantity over time
- flow: A rate of change between stocks or from/to external sources
- agent: An autonomous entity making decisions

Available actions:
- CREATE_ENTITY: Add a new entity
- UPDATE_FLOW_RATE: Change the rate of a flow
- SET_STOCK_VALUE: Set the value of a stock
- SPAWN_AGENT: Create a new agent
- DISPLAY_CHART: Show a time-series chart

Guidelines:
1. Model stocks and flows clearly
2. Include feedback loops where appropriate
3. Use realistic economic parameters
4. Add charts to visualize dynamics over time
5. Include narration explaining economic concepts`,

  fewShotExamples: [
    {
      userPrompt: "Simulate compound interest with 5% annual rate",
      assistantResponse: JSON.stringify({
        id: "sim_compound_interest_001",
        version: "1.0.0",
        title: "Compound Interest Growth",
        description: "Visualization of compound interest over time",
        domain: "ECONOMICS",
        domainMetadata: {
          domain: "ECONOMICS",
          economics: {
            timeStep: 1,
            integrationMethod: "euler",
            simulationDuration: 20,
            initialConditions: { principal: 1000 }
          }
        },
        canvas: { width: 900, height: 500, backgroundColor: "#1a1a2e" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          { id: "principal", type: "stock", x: 200, y: 250, value: 1000, label: "Principal" },
          { id: "interest_flow", type: "flow", sourceId: "external", targetId: "principal", rate: 0.05, equation: "principal * 0.05" }
        ],
        steps: [
          {
            id: "step_year_1",
            orderIndex: 0,
            title: "Year 1",
            narration: "After one year, interest is added to the principal",
            actions: [
              { action: "SET_STOCK_VALUE", targetId: "principal", value: 1050 },
              { action: "DISPLAY_CHART", chartType: "line", dataSourceIds: ["principal"], title: "Principal Over Time", position: { x: 500, y: 100, width: 350, height: 200 } }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Maximum simulation duration 100 time units",
    "Maximum 20 stocks",
    "Maximum 30 flows",
    "Include at least one visualization chart"
  ],

  entityTypes: ["stock", "flow", "agent"],
  actionTypes: ["CREATE_ENTITY", "UPDATE_FLOW_RATE", "SET_STOCK_VALUE", "SPAWN_AGENT", "DISPLAY_CHART"]
};

/**
 * Chemistry prompt pack.
 */
export const CHEMISTRY_PROMPT_PACK: PromptPack = {
  domain: "CHEMISTRY",
  systemPrompt: `You are an expert in creating chemistry visualizations for education.
Your task is to generate a SimulationManifest JSON that visualizes chemical reactions and molecular structures.

Available entity types:
- atom: An atom with element, charge, and position
- bond: A chemical bond between atoms
- molecule: A group of atoms and bonds
- reactionArrow: Arrow indicating reaction direction
- energyProfile: Reaction energy diagram

Available actions:
- CREATE_ENTITY/REMOVE_ENTITY: Add/remove entities
- CREATE_BOND: Form a bond between atoms
- BREAK_BOND: Break an existing bond
- REARRANGE: Move atoms to new positions
- HIGHLIGHT_ATOMS: Highlight specific atoms
- SET_REACTION_CONDITIONS: Set temperature, pressure, etc.
- DISPLAY_FORMULA: Show chemical formula
- SHOW_ENERGY_PROFILE: Display energy diagram

Guidelines:
1. Use correct chemical notation and structures
2. Ensure mass and charge balance in reactions
3. Show electron flow where appropriate
4. Include reaction conditions
5. Add narration explaining mechanism steps`,

  fewShotExamples: [
    {
      userPrompt: "Visualize SN2 reaction of bromoethane with hydroxide ion",
      assistantResponse: JSON.stringify({
        id: "sim_sn2_reaction_001",
        version: "1.0.0",
        title: "SN2 Reaction Mechanism",
        description: "Bimolecular nucleophilic substitution of bromoethane",
        domain: "CHEMISTRY",
        domainMetadata: {
          domain: "CHEMISTRY",
          chemistry: {
            reactionType: "substitution",
            mechanism: "SN2"
          }
        },
        canvas: { width: 900, height: 400, backgroundColor: "#1a1a2e" },
        playback: { defaultSpeed: 0.5, allowSpeedChange: true },
        initialEntities: [
          { id: "C1", type: "atom", element: "C", x: 300, y: 200, hybridization: "sp3" },
          { id: "Br", type: "atom", element: "Br", x: 400, y: 200, charge: 0 },
          { id: "OH", type: "atom", element: "O", x: 150, y: 200, charge: -1 },
          { id: "bond_C_Br", type: "bond", atom1Id: "C1", atom2Id: "Br", bondOrder: 1 }
        ],
        steps: [
          {
            id: "step_approach",
            orderIndex: 0,
            title: "Nucleophile Approach",
            narration: "The hydroxide ion (nucleophile) approaches the carbon from the opposite side of the leaving group",
            actions: [
              { action: "HIGHLIGHT_ATOMS", atomIds: ["OH"], style: "nucleophile" },
              { action: "MOVE", targetId: "OH", toX: 200, toY: 200, easing: "easeInOut", duration: 1000 }
            ]
          },
          {
            id: "step_transition",
            orderIndex: 1,
            title: "Transition State",
            narration: "In the transition state, the carbon is partially bonded to both the nucleophile and leaving group",
            actions: [
              { action: "CREATE_BOND", atom1Id: "C1", atom2Id: "OH", bondOrder: 1 },
              { action: "ANNOTATE", text: "‡ Transition State", position: "top" }
            ],
            checkpoint: true
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Must balance mass in reactions",
    "Must balance charge in reactions",
    "Use standard atom colors (C=gray, O=red, N=blue, etc.)",
    "Maximum 50 atoms per simulation"
  ],

  entityTypes: ["atom", "bond", "molecule", "reactionArrow", "energyProfile"],
  actionTypes: ["CREATE_ENTITY", "REMOVE_ENTITY", "CREATE_BOND", "BREAK_BOND", "REARRANGE", "HIGHLIGHT_ATOMS", "SET_REACTION_CONDITIONS", "DISPLAY_FORMULA", "SHOW_ENERGY_PROFILE"]
};

/**
 * Biology prompt pack.
 */
export const BIOLOGY_PROMPT_PACK: PromptPack = {
  domain: "BIOLOGY",
  systemPrompt: `You are an expert in creating biology simulations for education.
Your task is to generate a SimulationManifest JSON that visualizes biological processes.

Available entity types:
- cell: A cell with organelles
- organelle: Subcellular structures (nucleus, mitochondria, etc.)
- compartment: A region with concentration
- enzyme: Catalytic protein
- signal: Signaling molecules
- gene: DNA sequence

Available actions:
- CREATE_ENTITY/REMOVE_ENTITY: Add/remove entities
- DIFFUSE: Passive diffusion between compartments
- TRANSPORT: Active or facilitated transport
- TRANSCRIBE: DNA to mRNA
- TRANSLATE: mRNA to protein
- METABOLISE: Enzyme-catalyzed reactions
- GROW_DIVIDE: Cell cycle progression
- SIGNAL: Cell signaling

Guidelines:
1. Use accurate biological terminology
2. Show scale appropriately (molecular vs cellular)
3. Include time scales (ms, s, min, hours)
4. Add narration explaining biological concepts
5. Visualize concentrations and gradients`,

  fewShotExamples: [
    {
      userPrompt: "Visualize transcription and translation of a gene",
      assistantResponse: JSON.stringify({
        id: "sim_central_dogma_001",
        version: "1.0.0",
        title: "Gene Expression: Transcription & Translation",
        description: "The central dogma of molecular biology",
        domain: "BIOLOGY",
        domainMetadata: {
          domain: "BIOLOGY",
          biology: {
            scale: "molecular",
            timeScale: "min",
            process: "gene_expression"
          }
        },
        canvas: { width: 1000, height: 500, backgroundColor: "#0a1a0a" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          { id: "nucleus", type: "organelle", organelleType: "nucleus", x: 200, y: 250, width: 300, height: 200 },
          { id: "gene1", type: "gene", sequence: "ATGCGA", x: 250, y: 250, promoterActive: true }
        ],
        steps: [
          {
            id: "step_transcription",
            orderIndex: 0,
            title: "Transcription Begins",
            narration: "RNA polymerase binds to the promoter and begins transcribing the gene",
            actions: [
              { action: "TRANSCRIBE", geneId: "gene1", mRnaId: "mrna1" }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Use accurate biological scales",
    "Maximum 10 cells per simulation",
    "Maximum 50 molecules per compartment",
    "Include time scale in metadata"
  ],

  entityTypes: ["cell", "organelle", "compartment", "enzyme", "signal", "gene"],
  actionTypes: ["CREATE_ENTITY", "REMOVE_ENTITY", "DIFFUSE", "TRANSPORT", "TRANSCRIBE", "TRANSLATE", "METABOLISE", "GROW_DIVIDE", "SIGNAL"]
};

/**
 * Medicine prompt pack for PK/PD and epidemiology.
 */
export const MEDICINE_PROMPT_PACK: PromptPack = {
  domain: "MEDICINE",
  systemPrompt: `You are an expert in creating pharmacology and epidemiology simulations for education.
Your task is to generate a SimulationManifest JSON that visualizes drug kinetics and disease spread.

Available entity types:
- pkCompartment: Pharmacokinetic compartment (central, peripheral, effect)
- dose: Drug dose with route and bioavailability
- infectionAgent: Pathogen entity

Available actions:
- CREATE_ENTITY/REMOVE_ENTITY: Add/remove entities
- ABSORB: Drug absorption from dose site
- ELIMINATE: Drug elimination (renal, hepatic)
- SPREAD_DISEASE: Infection transmission (SIR/SEIR)
- SIGNAL: Drug-receptor interaction

Guidelines:
1. Use clinically relevant drug parameters
2. Include therapeutic windows where appropriate
3. Show concentration-time curves
4. Add safety warnings for toxic levels
5. Use realistic epidemiological parameters`,

  fewShotExamples: [
    {
      userPrompt: "Simulate oral drug absorption and elimination",
      assistantResponse: JSON.stringify({
        id: "sim_pk_oral_001",
        version: "1.0.0",
        title: "Oral Drug Pharmacokinetics",
        description: "One-compartment PK model with first-order absorption and elimination",
        domain: "MEDICINE",
        domainMetadata: {
          domain: "MEDICINE",
          medicine: {
            modelType: "one_compartment",
            drugName: "Example Drug",
            halfLife: 4,
            therapeuticRange: { min: 10, max: 20 },
            dosing: { amount: 100, interval: 8, route: "oral" }
          }
        },
        canvas: { width: 900, height: 500, backgroundColor: "#1a0a0a" },
        playback: { defaultSpeed: 1, allowSpeedChange: true },
        initialEntities: [
          { id: "dose1", type: "dose", amount: 100, route: "oral", bioavailability: 0.8, x: 100, y: 200 },
          { id: "central", type: "pkCompartment", compartmentType: "central", volume: 50, concentration: 0, ke: 0.173, x: 400, y: 200 }
        ],
        steps: [
          {
            id: "step_absorption",
            orderIndex: 0,
            title: "Drug Absorption",
            narration: "The drug is absorbed from the GI tract into the central compartment",
            actions: [
              { action: "ABSORB", doseId: "dose1", compartmentId: "central", rate: 1.0 },
              { action: "DISPLAY_CHART", chartType: "line", dataSourceIds: ["central"], title: "Plasma Concentration vs Time", position: { x: 550, y: 50, width: 300, height: 200 } }
            ]
          }
        ],
        schemaVersion: "1.0.0"
      }, null, 2)
    }
  ],

  constraints: [
    "Include therapeutic range where applicable",
    "Use realistic PK parameters",
    "Maximum simulation time 72 hours for PK",
    "Include safety warnings for concentrations above toxic threshold"
  ],

  entityTypes: ["pkCompartment", "dose", "infectionAgent"],
  actionTypes: ["CREATE_ENTITY", "REMOVE_ENTITY", "ABSORB", "ELIMINATE", "SPREAD_DISEASE", "SIGNAL"]
};

/**
 * Get prompt pack for a domain.
 */
export function getPromptPack(domain: SimulationDomain): PromptPack | null {
  switch (domain) {
    case "CS_DISCRETE":
      return CS_DISCRETE_PROMPT_PACK;
    case "PHYSICS":
      return PHYSICS_PROMPT_PACK;
    case "ECONOMICS":
      return ECONOMICS_PROMPT_PACK;
    case "CHEMISTRY":
      return CHEMISTRY_PROMPT_PACK;
    case "BIOLOGY":
      return BIOLOGY_PROMPT_PACK;
    case "MEDICINE":
      return MEDICINE_PROMPT_PACK;
    default:
      return null;
  }
}

/**
 * Get all available prompt packs.
 */
export function getAllPromptPacks(): PromptPack[] {
  return [
    CS_DISCRETE_PROMPT_PACK,
    PHYSICS_PROMPT_PACK,
    ECONOMICS_PROMPT_PACK,
    CHEMISTRY_PROMPT_PACK,
    BIOLOGY_PROMPT_PACK,
    MEDICINE_PROMPT_PACK
  ];
}
