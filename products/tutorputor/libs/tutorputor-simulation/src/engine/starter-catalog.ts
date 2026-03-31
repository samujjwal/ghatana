/**
 * Simulation Starter Catalog
 *
 * Typed, production-safe starter manifests for simulation authoring flows.
 * This avoids coupling the live platform routes to the older auto/examples
 * modules that still carry unrelated contract debt.
 *
 * @doc.type module
 * @doc.purpose Provide curated starter simulations for authoring and export flows
 * @doc.layer product
 * @doc.pattern Catalog
 */

import type {
  SimEntity,
  SimEntityId,
  SimulationDomain,
  SimulationId,
  SimulationManifest,
  SimulationStep,
  SimStepId,
} from "@tutorputor/contracts/v1/simulation";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";

export type SimulationStarterDifficulty =
  | "beginner"
  | "intermediate"
  | "advanced";

export interface SimulationStarter {
  id: string;
  name: string;
  summary: string;
  domain: SimulationDomain;
  difficulty: SimulationStarterDifficulty;
  tags: string[];
  estimatedMinutes: number;
  audience: "k12" | "undergraduate" | "graduate" | "professional";
  legacyPresetIds: string[];
  manifest: SimulationManifest;
}

export interface ListSimulationStartersInput {
  domain?: SimulationDomain;
  difficulty?: SimulationStarterDifficulty;
  query?: string;
  tag?: string;
  audience?: SimulationStarter["audience"];
}

export interface SimulationStarterCatalogSummary {
  total: number;
  byDomain: Record<SimulationDomain, number>;
  byDifficulty: Record<SimulationStarterDifficulty, number>;
  byAudience: Record<SimulationStarter["audience"], number>;
  legacyPresetCoverage: number;
}

interface SimulationStarterRecord {
  id: string;
  name: string;
  summary: string;
  domain: SimulationDomain;
  difficulty: SimulationStarterDifficulty;
  tags: string[];
  estimatedMinutes: number;
  manifest: SimulationManifest;
}

function simulationId(value: string): SimulationId {
  return value as SimulationId;
}

function entityId(value: string): SimEntityId {
  return value as SimEntityId;
}

function stepId(value: string): SimStepId {
  return value as SimStepId;
}

function userId(value: string): UserId {
  return value as UserId;
}

function tenantId(value: string): TenantId {
  return value as TenantId;
}

function makeEntity(
  id: string,
  type: string,
  x: number,
  y: number,
  label?: string,
): SimEntity {
  return {
    id: entityId(id),
    type,
    x,
    y,
    ...(label ? { label } : {}),
  };
}

function makeStep(
  id: string,
  orderIndex: number,
  title: string,
  description: string,
  duration: number,
  annotation: string,
): SimulationStep {
  return {
    id: stepId(id),
    orderIndex,
    title,
    description,
    duration,
    actions: [
      {
        action: "ANNOTATE",
        text: annotation,
      },
    ],
  };
}

function makeManifest(input: {
  id: string;
  title: string;
  description: string;
  domain: SimulationDomain;
  entities: SimEntity[];
  steps: SimulationStep[];
}): SimulationManifest {
  const timestamp = "2026-03-30T12:00:00.000Z";

  return {
    id: simulationId(input.id),
    version: "1.0.0",
    title: input.title,
    description: input.description,
    domain: input.domain,
    authorId: userId("starter-catalog"),
    tenantId: tenantId("system"),
    canvas: {
      width: 1280,
      height: 720,
      backgroundColor: "#f8fafc",
    },
    playback: {
      defaultSpeed: 1,
      loop: false,
      autoPlay: false,
    },
    initialEntities: input.entities,
    steps: input.steps,
    accessibility: {
      screenReaderNarration: true,
      reducedMotion: false,
      highContrast: true,
    },
    createdAt: timestamp,
    updatedAt: timestamp,
    schemaVersion: "1.0.0",
  };
}

export const simulationStarters: SimulationStarterRecord[] = [
  {
    id: "starter-newton-cart",
    name: "Newton Cart Push",
    summary: "Introductory rigid-body motion with force and inertia cues.",
    domain: "PHYSICS",
    difficulty: "beginner",
    tags: ["forces", "motion", "inertia"],
    estimatedMinutes: 8,
    manifest: makeManifest({
      id: "starter-newton-cart",
      title: "Newton Cart Push",
      description: "Observe how a cart responds before, during, and after a push.",
      domain: "PHYSICS",
      entities: [
        makeEntity("cart", "rigidBody", 220, 360, "Cart"),
        makeEntity("track", "boundary", 640, 420, "Track"),
        makeEntity("force-arrow", "vector", 140, 320, "Applied force"),
      ],
      steps: [
        makeStep("step-observe", 0, "Observe", "The cart starts at rest.", 1200, "No net force yet."),
        makeStep("step-push", 1, "Push", "A force is applied to the cart.", 1800, "Acceleration increases while force is applied."),
        makeStep("step-glide", 2, "Glide", "The cart continues moving.", 2200, "Motion persists after the push stops."),
      ],
    }),
  },
  {
    id: "starter-equilibrium-shift",
    name: "Equilibrium Shift",
    summary: "Reversible chemistry starter focused on concentration changes.",
    domain: "CHEMISTRY",
    difficulty: "intermediate",
    tags: ["equilibrium", "concentration", "reversible-reactions"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-equilibrium-shift",
      title: "Equilibrium Shift",
      description: "Visualize how a reversible reaction responds to a perturbation.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("reactant-a", "molecule", 260, 260, "Reactant A"),
        makeEntity("reactant-b", "molecule", 260, 420, "Reactant B"),
        makeEntity("product-c", "molecule", 860, 340, "Product C"),
      ],
      steps: [
        makeStep("step-start", 0, "Initial mix", "Reactants dominate the system.", 1400, "The reaction begins from the reactant side."),
        makeStep("step-balance", 1, "Dynamic balance", "Forward and reverse processes balance.", 1800, "Equilibrium is dynamic, not static."),
        makeStep("step-shift", 2, "Add reactant", "The system shifts toward product formation.", 2200, "Le Chatelier's principle predicts the shift."),
      ],
    }),
  },
  {
    id: "starter-membrane-transport",
    name: "Membrane Transport",
    summary: "Cell transport starter covering passive and active movement.",
    domain: "BIOLOGY",
    difficulty: "beginner",
    tags: ["cells", "transport", "diffusion"],
    estimatedMinutes: 9,
    manifest: makeManifest({
      id: "starter-membrane-transport",
      title: "Membrane Transport",
      description: "Compare passive diffusion with pump-mediated transport.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("membrane", "compartment", 640, 340, "Membrane"),
        makeEntity("solute-left", "particle", 380, 320, "Solute"),
        makeEntity("solute-right", "particle", 900, 360, "Solute"),
        makeEntity("pump", "enzyme", 640, 500, "Pump protein"),
      ],
      steps: [
        makeStep("step-gradient", 0, "Gradient", "A concentration gradient is established.", 1500, "Diffusion moves down the gradient."),
        makeStep("step-channel", 1, "Facilitated", "Transport proteins ease movement.", 1700, "Channels increase transport rate."),
        makeStep("step-active", 2, "Active transport", "A pump moves material uphill.", 2200, "Active transport requires energy."),
      ],
    }),
  },
  {
    id: "starter-mitosis-phases",
    name: "Mitosis Phases",
    summary: "Track chromosomes, spindle fibers, and checkpoints across mitosis.",
    domain: "BIOLOGY",
    difficulty: "intermediate",
    tags: ["mitosis", "cell-cycle", "chromosomes"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-mitosis-phases",
      title: "Mitosis Phases",
      description: "Follow a dividing cell from prophase through cytokinesis.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("chromosomes", "genetic-material", 520, 280, "Chromosomes"),
        makeEntity("spindle", "fiber-network", 640, 360, "Spindle fibers"),
        makeEntity("cell-membrane", "boundary", 640, 360, "Cell membrane"),
        makeEntity("checkpoint", "signal", 940, 180, "Checkpoint"),
      ],
      steps: [
        makeStep("step-prophase", 0, "Prophase", "Chromosomes condense and spindle fibers form.", 1600, "DNA becomes visible as chromosomes prepare for separation."),
        makeStep("step-metaphase", 1, "Metaphase", "Chromosomes align at the metaphase plate.", 1700, "Alignment helps ensure each daughter cell receives a full copy."),
        makeStep("step-anaphase", 2, "Anaphase", "Sister chromatids separate toward opposite poles.", 1800, "Spindle fibers shorten to pull chromatids apart."),
        makeStep("step-cytokinesis", 3, "Cytokinesis", "The membrane pinches into two daughter cells.", 1900, "Mitosis ends when two genetically similar cells form."),
      ],
    }),
  },
  {
    id: "starter-photosynthesis-cycle",
    name: "Photosynthesis Cycle",
    summary: "Visualize light capture, ATP production, and carbon fixation.",
    domain: "BIOLOGY",
    difficulty: "intermediate",
    tags: ["photosynthesis", "chloroplast", "energy"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-photosynthesis-cycle",
      title: "Photosynthesis Cycle",
      description: "Trace energy flow from photons to glucose production.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("chloroplast", "organelle", 620, 320, "Chloroplast"),
        makeEntity("photon", "particle", 260, 180, "Light"),
        makeEntity("water", "molecule", 280, 500, "Water"),
        makeEntity("glucose", "molecule", 980, 280, "Glucose"),
      ],
      steps: [
        makeStep("step-light", 0, "Capture light", "Pigments absorb incoming photons.", 1500, "Light energy powers the reaction center."),
        makeStep("step-split", 1, "Split water", "Water molecules donate electrons and release oxygen.", 1800, "Photolysis supports electron transport and produces oxygen."),
        makeStep("step-atp", 2, "Build ATP", "Electron transport drives ATP and NADPH production.", 1900, "Stored chemical energy fuels the Calvin cycle."),
        makeStep("step-fix", 3, "Fix carbon", "Carbon dioxide is converted into sugar precursors.", 2100, "Carbon fixation links energy capture to biomass growth."),
      ],
    }),
  },
  {
    id: "starter-dna-replication",
    name: "DNA Replication",
    summary: "Semi-conservative replication with enzymes on leading and lagging strands.",
    domain: "BIOLOGY",
    difficulty: "advanced",
    tags: ["dna", "replication", "enzymes"],
    estimatedMinutes: 13,
    manifest: makeManifest({
      id: "starter-dna-replication",
      title: "DNA Replication",
      description: "Model helicase, polymerase, and primer activity during replication.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("double-helix", "genetic-material", 520, 340, "DNA helix"),
        makeEntity("helicase", "enzyme", 420, 260, "Helicase"),
        makeEntity("polymerase", "enzyme", 760, 260, "Polymerase"),
        makeEntity("primer", "molecule", 760, 460, "RNA primer"),
      ],
      steps: [
        makeStep("step-unzip", 0, "Unzip helix", "Helicase opens the replication fork.", 1500, "Breaking hydrogen bonds separates the template strands."),
        makeStep("step-prime", 1, "Lay primers", "Short primers mark where synthesis can begin.", 1600, "DNA polymerase needs an existing 3' end."),
        makeStep("step-extend", 2, "Extend strands", "Polymerase adds complementary nucleotides.", 1800, "Leading and lagging strands replicate with different continuity."),
        makeStep("step-finish", 3, "Seal fragments", "Fragments are joined into continuous daughter strands.", 1700, "Replication produces two semi-conservative DNA molecules."),
      ],
    }),
  },
  {
    id: "starter-natural-selection",
    name: "Natural Selection",
    summary: "Show variation, survival pressure, and trait frequency shifts over generations.",
    domain: "BIOLOGY",
    difficulty: "beginner",
    tags: ["evolution", "selection", "populations"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-natural-selection",
      title: "Natural Selection",
      description: "Compare trait survival under changing environmental pressure.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("population", "population", 340, 360, "Population"),
        makeEntity("predator", "agent", 720, 220, "Predator"),
        makeEntity("environment", "boundary", 980, 420, "Environment"),
        makeEntity("trait-meter", "signal", 960, 150, "Trait frequency"),
      ],
      steps: [
        makeStep("step-variation", 0, "Variation", "The population begins with mixed traits.", 1400, "Selection acts on existing variation."),
        makeStep("step-pressure", 1, "Selection pressure", "Predation and environment favor some traits.", 1700, "Traits that improve survival become more common."),
        makeStep("step-reproduce", 2, "Reproduce", "Survivors pass traits to the next generation.", 1800, "Inheritance shifts the population distribution."),
        makeStep("step-adapt", 3, "Adaptation", "A once-rare trait becomes common.", 1800, "Adaptation emerges across generations, not in a single organism."),
      ],
    }),
  },
  {
    id: "starter-ecosystem-dynamics",
    name: "Ecosystem Dynamics",
    summary: "Explore food-web feedback, carrying capacity, and recovery after disruption.",
    domain: "BIOLOGY",
    difficulty: "advanced",
    tags: ["ecosystems", "food-web", "population-cycles"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-ecosystem-dynamics",
      title: "Ecosystem Dynamics",
      description: "Observe producer, consumer, and decomposer interactions over time.",
      domain: "BIOLOGY",
      entities: [
        makeEntity("producers", "population", 240, 420, "Producers"),
        makeEntity("herbivores", "population", 620, 320, "Herbivores"),
        makeEntity("predators", "population", 920, 220, "Predators"),
        makeEntity("decomposers", "population", 900, 520, "Decomposers"),
      ],
      steps: [
        makeStep("step-balance", 0, "Baseline web", "Energy moves through a balanced food web.", 1500, "Stable ecosystems depend on linked population feedback."),
        makeStep("step-bloom", 1, "Resource pulse", "Producer growth triggers herbivore expansion.", 1800, "More primary productivity supports higher trophic levels."),
        makeStep("step-collapse", 2, "Predator overshoot", "Predator growth contributes to prey decline.", 1900, "Delayed feedback can destabilize populations."),
        makeStep("step-recover", 3, "Recovery", "Decomposition returns nutrients and populations rebound.", 2100, "Recovery depends on nutrient cycling and carrying capacity."),
      ],
    }),
  },
  {
    id: "starter-ideal-gas-law",
    name: "Ideal Gas Law",
    summary: "Relate pressure, volume, and temperature inside a variable container.",
    domain: "CHEMISTRY",
    difficulty: "beginner",
    tags: ["gas-laws", "pressure", "temperature"],
    estimatedMinutes: 9,
    manifest: makeManifest({
      id: "starter-ideal-gas-law",
      title: "Ideal Gas Law",
      description: "Manipulate piston volume and heat to observe pressure changes.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("piston", "container", 620, 320, "Piston"),
        makeEntity("gas", "particle-cloud", 620, 320, "Gas molecules"),
        makeEntity("thermometer", "sensor", 980, 180, "Temperature"),
        makeEntity("gauge", "sensor", 980, 420, "Pressure"),
      ],
      steps: [
        makeStep("step-baseline-gas", 0, "Baseline", "The gas starts at standard conditions.", 1300, "Pressure depends on particle collisions with the walls."),
        makeStep("step-compress", 1, "Compress", "Volume decreases while particle count stays fixed.", 1600, "Less space means more frequent wall collisions."),
        makeStep("step-heat", 2, "Heat", "Temperature increases and particles move faster.", 1800, "Higher kinetic energy raises pressure when volume is fixed."),
        makeStep("step-release", 3, "Release", "The piston expands and pressure falls back.", 1700, "Gas variables co-vary through PV=nRT."),
      ],
    }),
  },
  {
    id: "starter-reaction-kinetics",
    name: "Reaction Kinetics",
    summary: "Compare uncatalyzed and catalyzed pathways with changing activation barriers.",
    domain: "CHEMISTRY",
    difficulty: "advanced",
    tags: ["kinetics", "activation-energy", "catalyst"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-reaction-kinetics",
      title: "Reaction Kinetics",
      description: "Track collision success and activation energy during a reaction.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("reactants", "molecule-cloud", 300, 340, "Reactants"),
        makeEntity("transition-state", "signal", 660, 180, "Activation barrier"),
        makeEntity("catalyst", "enzyme", 680, 460, "Catalyst"),
        makeEntity("products", "molecule-cloud", 980, 340, "Products"),
      ],
      steps: [
        makeStep("step-collide", 0, "Collisions", "Particles collide with mixed energies.", 1500, "Only sufficiently energetic collisions react."),
        makeStep("step-barrier", 1, "Barrier", "Most reactants fail to cross the activation hill.", 1700, "Activation energy limits reaction rate."),
        makeStep("step-catalyst", 2, "Catalyze", "A catalyst lowers the effective barrier.", 1800, "Catalysts speed rate without changing equilibrium."),
        makeStep("step-rate", 3, "Rate change", "Products form more quickly with the alternate path.", 1900, "A faster pathway increases successful reaction frequency."),
      ],
    }),
  },
  {
    id: "starter-electrochemical-cell",
    name: "Electrochemical Cell",
    summary: "Visualize oxidation, reduction, and voltage across a galvanic cell.",
    domain: "CHEMISTRY",
    difficulty: "intermediate",
    tags: ["electrochemistry", "redox", "voltage"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-electrochemical-cell",
      title: "Electrochemical Cell",
      description: "Connect anode, cathode, and salt bridge to build a voltaic cell.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("anode", "electrode", 280, 320, "Anode"),
        makeEntity("cathode", "electrode", 920, 320, "Cathode"),
        makeEntity("salt-bridge", "bridge", 620, 180, "Salt bridge"),
        makeEntity("voltmeter", "sensor", 620, 500, "Voltmeter"),
      ],
      steps: [
        makeStep("step-oxidize", 0, "Oxidation", "The anode releases electrons into the circuit.", 1500, "Oxidation occurs where electrons are produced."),
        makeStep("step-flow", 1, "Electron flow", "Electrons travel through the external wire.", 1700, "The voltmeter measures the potential difference driving flow."),
        makeStep("step-balance-cell", 2, "Ion balance", "The salt bridge restores charge balance.", 1800, "Ion migration keeps each half-cell electrically neutral."),
        makeStep("step-reduce", 3, "Reduction", "The cathode gains electrons and plates material.", 1800, "Reduction occurs where electrons are consumed."),
      ],
    }),
  },
  {
    id: "starter-molecular-geometry",
    name: "Molecular Geometry",
    summary: "Use electron domains to predict shape and bond-angle changes.",
    domain: "CHEMISTRY",
    difficulty: "intermediate",
    tags: ["vsepr", "geometry", "bond-angles"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-molecular-geometry",
      title: "Molecular Geometry",
      description: "Compare electron-pair repulsion and the resulting molecular shapes.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("central-atom", "atom", 620, 320, "Central atom"),
        makeEntity("bonding-pairs", "electron-domain", 880, 220, "Bonding domains"),
        makeEntity("lone-pairs", "electron-domain", 420, 220, "Lone pairs"),
        makeEntity("angle-meter", "sensor", 980, 460, "Bond angle"),
      ],
      steps: [
        makeStep("step-domains", 0, "Count domains", "Electron groups surround the central atom.", 1400, "Geometry starts from all electron domains, not just bonds."),
        makeStep("step-arrange", 1, "Minimize repulsion", "Domains spread out in three dimensions.", 1700, "Electron pairs maximize separation to lower repulsion."),
        makeStep("step-lone-pair", 2, "Add lone pairs", "Lone pairs compress the bond angle.", 1800, "Nonbonding electrons repel more strongly than bonding pairs."),
        makeStep("step-shape", 3, "Predict shape", "The visible geometry is identified from bonded atoms.", 1700, "Molecular shape depends on which electron domains are bonded."),
      ],
    }),
  },
  {
    id: "starter-buffer-titration",
    name: "Buffer and Titration",
    summary: "Track pH change, equivalence point, and buffer capacity during titration.",
    domain: "CHEMISTRY",
    difficulty: "advanced",
    tags: ["acid-base", "titration", "buffers"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-buffer-titration",
      title: "Buffer and Titration",
      description: "Add titrant gradually and observe the pH response curve.",
      domain: "CHEMISTRY",
      entities: [
        makeEntity("beaker", "container", 620, 420, "Buffer solution"),
        makeEntity("burette", "delivery-system", 620, 140, "Titrant"),
        makeEntity("ph-meter", "sensor", 980, 260, "pH meter"),
        makeEntity("curve", "graph", 980, 500, "Titration curve"),
      ],
      steps: [
        makeStep("step-buffer", 0, "Buffer region", "The solution initially resists pH change.", 1500, "Buffers neutralize added acid or base within a working range."),
        makeStep("step-add", 1, "Add titrant", "The burette delivers titrant in measured increments.", 1700, "Each addition changes the acid-base ratio."),
        makeStep("step-equivalence", 2, "Equivalence", "The curve steepens near the equivalence point.", 1800, "Buffer capacity is exhausted as one species is consumed."),
        makeStep("step-beyond", 3, "Beyond equivalence", "Extra titrant dominates the pH.", 1800, "Past equivalence, the added reagent controls the solution chemistry."),
      ],
    }),
  },
  {
    id: "starter-dose-response",
    name: "Dose Response",
    summary: "Medication starter for absorption, distribution, and response timing.",
    domain: "MEDICINE",
    difficulty: "intermediate",
    tags: ["pharmacology", "pkpd", "dose-response"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-dose-response",
      title: "Dose Response",
      description: "Follow a medication dose through absorption and response phases.",
      domain: "MEDICINE",
      entities: [
        makeEntity("dose", "dose", 220, 520, "Dose"),
        makeEntity("plasma", "compartment", 620, 280, "Plasma"),
        makeEntity("target-tissue", "compartment", 960, 360, "Target tissue"),
        makeEntity("response-curve", "signal", 1050, 150, "Response"),
      ],
      steps: [
        makeStep("step-administer", 0, "Administer", "The dose enters the system.", 1200, "Administration starts the PK profile."),
        makeStep("step-absorb", 1, "Absorb", "Drug enters the bloodstream.", 1800, "Concentration rises during absorption."),
        makeStep("step-response", 2, "Respond", "Target tissue effect appears.", 2400, "Clinical response trails concentration."),
      ],
    }),
  },
  {
    id: "starter-cardiac-cycle",
    name: "Cardiac Cycle",
    summary: "Connect valve timing, chamber pressure, and ventricular volume.",
    domain: "MEDICINE",
    difficulty: "advanced",
    tags: ["cardiology", "pressure-volume", "ecg"],
    estimatedMinutes: 13,
    manifest: makeManifest({
      id: "starter-cardiac-cycle",
      title: "Cardiac Cycle",
      description: "Follow blood flow and pressure-volume changes during one heartbeat.",
      domain: "MEDICINE",
      entities: [
        makeEntity("atria", "chamber", 360, 220, "Atria"),
        makeEntity("ventricle", "chamber", 640, 420, "Left ventricle"),
        makeEntity("valves", "valve-system", 520, 320, "Valves"),
        makeEntity("ecg", "signal", 980, 180, "ECG trace"),
      ],
      steps: [
        makeStep("step-fill", 0, "Ventricular filling", "Blood enters the ventricle while AV valves are open.", 1500, "Filling increases preload before systole."),
        makeStep("step-contract", 1, "Isovolumetric contraction", "Pressure rises before the semilunar valve opens.", 1800, "All valves closed means volume is fixed while pressure climbs."),
        makeStep("step-eject", 2, "Ejection", "Blood is expelled into the arterial system.", 1900, "Pressure-volume loops peak during active ventricular ejection."),
        makeStep("step-relax", 3, "Isovolumetric relaxation", "Pressure falls and the ventricle resets.", 1800, "Relaxation ends when ventricular pressure drops below atrial pressure."),
      ],
    }),
  },
  {
    id: "starter-action-potential",
    name: "Neuronal Action Potential",
    summary: "Model sodium and potassium channel timing across depolarization and repolarization.",
    domain: "MEDICINE",
    difficulty: "advanced",
    tags: ["neuroscience", "ion-channels", "membrane-potential"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-action-potential",
      title: "Neuronal Action Potential",
      description: "Observe threshold crossing and ion-channel dynamics in a neuron.",
      domain: "MEDICINE",
      entities: [
        makeEntity("membrane", "boundary", 620, 340, "Membrane"),
        makeEntity("na-channel", "ion-channel", 440, 240, "Sodium channel"),
        makeEntity("k-channel", "ion-channel", 800, 240, "Potassium channel"),
        makeEntity("voltage-trace", "signal", 980, 420, "Membrane potential"),
      ],
      steps: [
        makeStep("step-resting", 0, "Resting state", "The membrane begins near resting potential.", 1400, "Leak currents and pumps maintain the resting gradient."),
        makeStep("step-threshold", 1, "Threshold", "A stimulus opens enough sodium channels to trigger depolarization.", 1700, "Action potentials are all-or-none once threshold is crossed."),
        makeStep("step-repolarize", 2, "Repolarization", "Potassium conductance increases as sodium channels inactivate.", 1800, "Potassium efflux returns the membrane toward negative voltage."),
        makeStep("step-refractory", 3, "Recovery", "The neuron passes through a refractory period.", 1800, "Refractory timing shapes firing frequency and directionality."),
      ],
    }),
  },
  {
    id: "starter-lung-mechanics",
    name: "Pulmonary Mechanics",
    summary: "Explore compliance, airway resistance, and gas exchange during ventilation.",
    domain: "MEDICINE",
    difficulty: "intermediate",
    tags: ["respiratory", "compliance", "ventilation"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-lung-mechanics",
      title: "Pulmonary Mechanics",
      description: "Relate diaphragm motion, airway resistance, and alveolar pressure.",
      domain: "MEDICINE",
      entities: [
        makeEntity("alveoli", "compartment", 760, 300, "Alveoli"),
        makeEntity("airway", "tube", 520, 320, "Airway"),
        makeEntity("diaphragm", "actuator", 620, 560, "Diaphragm"),
        makeEntity("spirometer", "sensor", 980, 180, "Spirometry"),
      ],
      steps: [
        makeStep("step-inhale", 0, "Inspiration", "The diaphragm contracts and thoracic volume expands.", 1500, "Lower intrathoracic pressure draws air inward."),
        makeStep("step-fill-alveoli", 1, "Alveolar filling", "Air distributes into compliant alveoli.", 1700, "Compliance determines how easily the lung expands."),
        makeStep("step-resistance", 2, "Resistance challenge", "Narrowed airways reduce flow for the same effort.", 1800, "Higher resistance shifts work toward moving air through the tubes."),
        makeStep("step-exchange", 3, "Gas exchange", "Oxygen uptake and carbon dioxide release occur at the alveolus.", 1800, "Ventilation supports diffusion across the alveolar membrane."),
      ],
    }),
  },
  {
    id: "starter-pharmacokinetics",
    name: "Pharmacokinetics Compartments",
    summary: "Connect absorption, distribution, metabolism, and elimination across compartments.",
    domain: "MEDICINE",
    difficulty: "intermediate",
    tags: ["pharmacokinetics", "compartments", "clearance"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-pharmacokinetics",
      title: "Pharmacokinetics Compartments",
      description: "Track a drug through absorption, distribution, metabolism, and elimination.",
      domain: "MEDICINE",
      entities: [
        makeEntity("gut", "compartment", 240, 420, "Absorption site"),
        makeEntity("central", "compartment", 620, 280, "Central compartment"),
        makeEntity("peripheral", "compartment", 900, 420, "Peripheral tissue"),
        makeEntity("liver", "enzyme-system", 620, 520, "Metabolism"),
      ],
      steps: [
        makeStep("step-absorption-site", 0, "Absorption", "Drug enters circulation from the administration site.", 1500, "Bioavailability depends on how much reaches the bloodstream."),
        makeStep("step-distribute", 1, "Distribution", "Drug moves between central and tissue compartments.", 1800, "Distribution reflects perfusion and tissue affinity."),
        makeStep("step-metabolize", 2, "Metabolism", "The liver transforms a fraction of the active compound.", 1800, "Metabolism can activate, deactivate, or prepare drugs for clearance."),
        makeStep("step-clear", 3, "Elimination", "Drug concentration falls as the body clears it.", 1800, "Clearance and volume of distribution shape half-life."),
      ],
    }),
  },
  {
    id: "starter-epidemiology-sir",
    name: "SIR Epidemiology Model",
    summary: "Compare transmission, recovery, and intervention effects in a disease outbreak.",
    domain: "MEDICINE",
    difficulty: "beginner",
    tags: ["epidemiology", "sir", "public-health"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-epidemiology-sir",
      title: "SIR Epidemiology Model",
      description: "Track susceptible, infected, and recovered populations over time.",
      domain: "MEDICINE",
      entities: [
        makeEntity("susceptible", "population", 260, 280, "Susceptible"),
        makeEntity("infected", "population", 620, 280, "Infected"),
        makeEntity("recovered", "population", 960, 280, "Recovered"),
        makeEntity("intervention", "policy", 620, 520, "Intervention"),
      ],
      steps: [
        makeStep("step-seed", 0, "Seed outbreak", "A small infected group enters a susceptible population.", 1400, "Early transmission can accelerate quickly when susceptibility is high."),
        makeStep("step-spread", 1, "Spread", "Contacts move people from susceptible to infected.", 1700, "Transmission rate drives the steepness of the epidemic curve."),
        makeStep("step-recover", 2, "Recover", "Infected individuals recover and leave the infectious pool.", 1800, "Recovery lowers the number of actively transmitting people."),
        makeStep("step-intervene", 3, "Intervene", "Public-health measures flatten the infection curve.", 1800, "Reducing effective contacts lowers reproduction number and peak burden."),
      ],
    }),
  },
  {
    id: "starter-market-shock",
    name: "Market Shock",
    summary: "Supply and demand starter with stock and flow dynamics.",
    domain: "ECONOMICS",
    difficulty: "intermediate",
    tags: ["markets", "supply-demand", "system-dynamics"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-market-shock",
      title: "Market Shock",
      description: "Track price and inventory after a sudden demand change.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("inventory", "stock", 300, 320, "Inventory"),
        makeEntity("demand", "flow", 620, 240, "Demand"),
        makeEntity("supply", "flow", 620, 420, "Supply"),
        makeEntity("price", "agent", 980, 320, "Price signal"),
      ],
      steps: [
        makeStep("step-baseline", 0, "Baseline", "Supply and demand start balanced.", 1400, "Stable systems oscillate around equilibrium."),
        makeStep("step-shock", 1, "Demand shock", "Demand increases abruptly.", 1800, "Price pressure rises as inventory tightens."),
        makeStep("step-adjust", 2, "Adjustment", "Supply responds with a lag.", 2200, "Delayed feedback drives overshoot risk."),
      ],
    }),
  },
  {
    id: "starter-supply-demand-dynamics",
    name: "Supply and Demand Dynamics",
    summary: "Compare curve shifts, new equilibrium, and surplus pressure in a market.",
    domain: "ECONOMICS",
    difficulty: "beginner",
    tags: ["supply-demand", "equilibrium", "price"],
    estimatedMinutes: 9,
    manifest: makeManifest({
      id: "starter-supply-demand-dynamics",
      title: "Supply and Demand Dynamics",
      description: "Introduce baseline equilibrium and the effect of curve shifts.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("demand-curve", "curve", 460, 300, "Demand"),
        makeEntity("supply-curve", "curve", 780, 300, "Supply"),
        makeEntity("equilibrium-point", "node", 640, 340, "Equilibrium"),
        makeEntity("price-axis", "axis", 1020, 360, "Price"),
      ],
      steps: [
        makeStep("step-equilibrium", 0, "Initial equilibrium", "Supply and demand intersect at a stable price.", 1400, "Market equilibrium balances quantity supplied and demanded."),
        makeStep("step-demand-shift", 1, "Demand shift", "Demand increases and the equilibrium moves upward.", 1700, "Higher willingness to buy raises equilibrium price and quantity."),
        makeStep("step-supply-shift", 2, "Supply shift", "Supply improves and moderates the price increase.", 1800, "A rightward supply shift lowers price pressure."),
        makeStep("step-compare-equilibria", 3, "Compare equilibria", "Different shocks produce different new balances.", 1700, "Market outcomes depend on which curve moves and by how much."),
      ],
    }),
  },
  {
    id: "starter-market-structures",
    name: "Market Structure Comparison",
    summary: "Contrast pricing power, output, and profit across market structures.",
    domain: "ECONOMICS",
    difficulty: "intermediate",
    tags: ["competition", "monopoly", "firms"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-market-structures",
      title: "Market Structure Comparison",
      description: "Observe how firms behave under competition, oligopoly, and monopoly.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("market-curve", "curve", 360, 260, "Market demand"),
        makeEntity("firm-curve", "curve", 760, 260, "Firm cost"),
        makeEntity("price-marker", "signal", 620, 420, "Price"),
        makeEntity("profit-area", "region", 980, 420, "Profit"),
      ],
      steps: [
        makeStep("step-competitive", 0, "Perfect competition", "Firms act as price takers.", 1500, "Competitive firms produce where price meets marginal cost."),
        makeStep("step-oligopoly", 1, "Oligopoly", "Firms begin reacting strategically to rivals.", 1800, "Interdependence changes output and pricing decisions."),
        makeStep("step-monopoly", 2, "Monopoly", "A single firm restricts output relative to competition.", 1900, "Market power can increase price above marginal cost."),
        makeStep("step-compare-welfare", 3, "Compare welfare", "Consumer and producer outcomes differ by structure.", 1800, "Pricing power shifts surplus and efficiency."),
      ],
    }),
  },
  {
    id: "starter-keynesian-cross",
    name: "Keynesian Cross",
    summary: "Trace planned expenditure, multiplier effects, and equilibrium output.",
    domain: "ECONOMICS",
    difficulty: "intermediate",
    tags: ["macroeconomics", "aggregate-demand", "multiplier"],
    estimatedMinutes: 10,
    manifest: makeManifest({
      id: "starter-keynesian-cross",
      title: "Keynesian Cross",
      description: "Visualize equilibrium output where planned spending equals production.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("consumption", "flow", 320, 320, "Consumption"),
        makeEntity("investment", "flow", 560, 220, "Investment"),
        makeEntity("government", "flow", 560, 420, "Government"),
        makeEntity("equilibrium-output", "signal", 960, 320, "Equilibrium output"),
      ],
      steps: [
        makeStep("step-spending", 0, "Planned spending", "Aggregate expenditure is assembled from spending components.", 1400, "Total demand combines household, firm, and government spending."),
        makeStep("step-intersection", 1, "Intersection", "The expenditure line meets the 45-degree output line.", 1700, "Equilibrium output occurs where desired spending matches production."),
        makeStep("step-multiplier", 2, "Multiplier", "A change in autonomous spending shifts the line upward.", 1800, "Secondary rounds of spending magnify the initial shift."),
        makeStep("step-gap", 3, "Output gap", "The economy can run below or above full-employment output.", 1700, "Policy aims to close recessionary or inflationary gaps."),
      ],
    }),
  },
  {
    id: "starter-monetary-policy",
    name: "Monetary Policy Transmission",
    summary: "Link policy rates to investment, credit conditions, and aggregate demand.",
    domain: "ECONOMICS",
    difficulty: "advanced",
    tags: ["monetary-policy", "interest-rates", "aggregate-demand"],
    estimatedMinutes: 12,
    manifest: makeManifest({
      id: "starter-monetary-policy",
      title: "Monetary Policy Transmission",
      description: "Show how central-bank actions propagate through the macroeconomy.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("central-bank", "policy-node", 260, 220, "Central bank"),
        makeEntity("money-market", "market", 560, 220, "Money market"),
        makeEntity("investment", "flow", 760, 420, "Investment"),
        makeEntity("aggregate-demand", "curve", 980, 260, "Aggregate demand"),
      ],
      steps: [
        makeStep("step-rate-cut", 0, "Policy move", "The central bank changes the policy rate.", 1500, "Short-term rates anchor broader financial conditions."),
        makeStep("step-credit", 1, "Credit channel", "Borrowing costs and lending conditions shift.", 1700, "Lower rates generally support borrowing and risk-taking."),
        makeStep("step-investment-demand", 2, "Investment response", "Firms and households adjust spending plans.", 1800, "Investment and durable consumption are interest-sensitive."),
        makeStep("step-output-prices", 3, "Macro impact", "Aggregate demand and inflation pressure respond.", 1800, "Transmission is indirect and occurs with lags."),
      ],
    }),
  },
  {
    id: "starter-game-theory",
    name: "Game Theory Basics",
    summary: "Compare dominant strategies, payoff matrices, and Nash equilibrium outcomes.",
    domain: "ECONOMICS",
    difficulty: "advanced",
    tags: ["game-theory", "nash", "strategic-interaction"],
    estimatedMinutes: 11,
    manifest: makeManifest({
      id: "starter-game-theory",
      title: "Game Theory Basics",
      description: "Explore strategic choices using a payoff matrix and response logic.",
      domain: "ECONOMICS",
      entities: [
        makeEntity("player-a", "agent", 320, 260, "Player A"),
        makeEntity("player-b", "agent", 920, 260, "Player B"),
        makeEntity("payoff-matrix", "matrix", 620, 360, "Payoff matrix"),
        makeEntity("best-response", "signal", 620, 540, "Best response"),
      ],
      steps: [
        makeStep("step-options", 0, "Choose strategies", "Each player selects from a small set of actions.", 1400, "Game theory starts by defining actions and payoffs."),
        makeStep("step-payoffs", 1, "Compare payoffs", "Outcomes differ depending on the joint strategy choice.", 1700, "A best response depends on what the other player does."),
        makeStep("step-dominant", 2, "Dominant strategy", "One strategy may outperform another regardless of the opponent.", 1800, "Dominant strategies simplify prediction when they exist."),
        makeStep("step-nash", 3, "Nash equilibrium", "A stable outcome appears where no player wants to deviate alone.", 1800, "Equilibrium captures mutual best responses, not necessarily fairness."),
      ],
    }),
  },
  {
    id: "starter-derivative-tangent",
    name: "Derivative Tangent",
    summary: "Calculus starter for slope intuition and local linearity.",
    domain: "MATHEMATICS",
    difficulty: "beginner",
    tags: ["calculus", "derivatives", "graphs"],
    estimatedMinutes: 7,
    manifest: makeManifest({
      id: "starter-derivative-tangent",
      title: "Derivative Tangent",
      description: "Inspect the slope of a curve at a chosen point.",
      domain: "MATHEMATICS",
      entities: [
        makeEntity("curve", "graph", 540, 340, "Curve"),
        makeEntity("point", "node", 620, 320, "Point on curve"),
        makeEntity("tangent", "edge", 760, 300, "Tangent line"),
      ],
      steps: [
        makeStep("step-point", 0, "Point selection", "Choose a point on the graph.", 1200, "A derivative is always local."),
        makeStep("step-slope", 1, "Slope estimate", "A tangent line appears.", 1600, "The tangent line approximates the curve nearby."),
        makeStep("step-compare", 2, "Compare points", "Slope changes across the curve.", 1800, "Different points yield different derivatives."),
      ],
    }),
  },
  {
    id: "starter-binary-search",
    name: "Binary Search Walkthrough",
    summary: "Discrete CS starter for divide-and-conquer search reasoning.",
    domain: "CS_DISCRETE",
    difficulty: "beginner",
    tags: ["algorithms", "search", "divide-and-conquer"],
    estimatedMinutes: 8,
    manifest: makeManifest({
      id: "starter-binary-search",
      title: "Binary Search Walkthrough",
      description: "Track low, high, and midpoint pointers in a sorted array.",
      domain: "CS_DISCRETE",
      entities: [
        makeEntity("array", "node", 620, 260, "Sorted array"),
        makeEntity("low", "pointer", 360, 420, "Low"),
        makeEntity("mid", "pointer", 620, 420, "Mid"),
        makeEntity("high", "pointer", 880, 420, "High"),
      ],
      steps: [
        makeStep("step-init", 0, "Initialize", "Low and high cover the full range.", 1200, "Binary search starts with the full interval."),
        makeStep("step-mid", 1, "Pick midpoint", "The midpoint partitions the array.", 1500, "Each comparison halves the search space."),
        makeStep("step-narrow", 2, "Narrow range", "Only one half remains active.", 1800, "Repeated halving drives logarithmic growth."),
      ],
    }),
  },
  {
    id: "starter-series-circuit",
    name: "Series Circuit Basics",
    summary: "Engineering starter for current flow and component roles.",
    domain: "ENGINEERING",
    difficulty: "beginner",
    tags: ["circuits", "current", "voltage"],
    estimatedMinutes: 9,
    manifest: makeManifest({
      id: "starter-series-circuit",
      title: "Series Circuit Basics",
      description: "Introduce a closed circuit with a source, load, and measurement point.",
      domain: "ENGINEERING",
      entities: [
        makeEntity("source", "node", 240, 360, "Voltage source"),
        makeEntity("wire-top", "edge", 520, 220, "Wire"),
        makeEntity("bulb", "node", 820, 360, "Lamp"),
        makeEntity("ammeter", "pointer", 520, 500, "Ammeter"),
      ],
      steps: [
        makeStep("step-open", 0, "Open circuit", "Current has no complete path.", 1200, "No closed path means no sustained current."),
        makeStep("step-close", 1, "Close loop", "The circuit is completed.", 1800, "Charge now flows through the load."),
        makeStep("step-measure", 2, "Measure current", "The meter shows steady current.", 2000, "Series elements share the same current."),
      ],
    }),
  },
];

const STARTER_AUDIENCE_MAP: Record<string, SimulationStarter["audience"]> = {
  "starter-newton-cart": "k12",
  "starter-equilibrium-shift": "undergraduate",
  "starter-membrane-transport": "k12",
  "starter-mitosis-phases": "undergraduate",
  "starter-photosynthesis-cycle": "undergraduate",
  "starter-dna-replication": "undergraduate",
  "starter-natural-selection": "k12",
  "starter-ecosystem-dynamics": "k12",
  "starter-ideal-gas-law": "undergraduate",
  "starter-reaction-kinetics": "undergraduate",
  "starter-electrochemical-cell": "undergraduate",
  "starter-molecular-geometry": "undergraduate",
  "starter-buffer-titration": "graduate",
  "starter-dose-response": "professional",
  "starter-cardiac-cycle": "professional",
  "starter-action-potential": "professional",
  "starter-lung-mechanics": "professional",
  "starter-pharmacokinetics": "professional",
  "starter-epidemiology-sir": "graduate",
  "starter-market-shock": "undergraduate",
  "starter-supply-demand-dynamics": "undergraduate",
  "starter-market-structures": "undergraduate",
  "starter-keynesian-cross": "undergraduate",
  "starter-monetary-policy": "graduate",
  "starter-game-theory": "graduate",
  "starter-derivative-tangent": "undergraduate",
  "starter-binary-search": "k12",
  "starter-series-circuit": "undergraduate",
};

const STARTER_LEGACY_PRESET_IDS: Record<string, string[]> = {
  "starter-newton-cart": ["preset-newton-first"],
  "starter-equilibrium-shift": ["preset-chemical-equilibrium"],
  "starter-membrane-transport": ["preset-cell-membrane"],
  "starter-mitosis-phases": ["preset-mitosis"],
  "starter-photosynthesis-cycle": ["preset-photosynthesis"],
  "starter-dna-replication": ["preset-dna-replication"],
  "starter-natural-selection": ["preset-natural-selection"],
  "starter-ecosystem-dynamics": ["preset-ecosystem-dynamics"],
  "starter-ideal-gas-law": ["preset-gas-laws"],
  "starter-reaction-kinetics": ["preset-kinetics"],
  "starter-electrochemical-cell": ["preset-electrochemistry"],
  "starter-molecular-geometry": ["preset-molecular-geometry"],
  "starter-cardiac-cycle": ["preset-cardiac-cycle"],
  "starter-action-potential": ["preset-action-potential"],
  "starter-lung-mechanics": ["preset-lung-mechanics"],
  "starter-pharmacokinetics": ["preset-pharmacokinetics"],
  "starter-epidemiology-sir": ["preset-epidemiology-sir"],
  "starter-supply-demand-dynamics": ["preset-supply-demand"],
  "starter-market-structures": ["preset-market-structures"],
  "starter-keynesian-cross": ["preset-keynesian-cross"],
  "starter-monetary-policy": ["preset-monetary-policy", "preset- monetary-policy"],
  "starter-game-theory": ["preset-game-theory"],
  "starter-binary-search": ["preset-binary-search"],
  "starter-series-circuit": ["preset-series-circuit"],
};

function hydrateStarter(starter: SimulationStarterRecord): SimulationStarter {
  return {
    ...structuredClone(starter),
    audience: STARTER_AUDIENCE_MAP[starter.id] ?? "undergraduate",
    legacyPresetIds: [...(STARTER_LEGACY_PRESET_IDS[starter.id] ?? [])],
  };
}

export function listSimulationStarters(
  input: ListSimulationStartersInput = {},
): SimulationStarter[] {
  const normalizedQuery = input.query?.trim().toLowerCase();
  const normalizedTag = input.tag?.trim().toLowerCase();

  return simulationStarters
    .map(hydrateStarter)
    .filter((starter) =>
      input.domain ? starter.domain === input.domain : true,
    )
    .filter((starter) =>
      input.difficulty ? starter.difficulty === input.difficulty : true,
    )
    .filter((starter) =>
      normalizedTag
        ? starter.tags.some((tag) => tag.toLowerCase() === normalizedTag)
        : true,
    )
    .filter((starter) =>
      input.audience ? starter.audience === input.audience : true,
    )
    .filter((starter) => {
      if (!normalizedQuery) {
        return true;
      }

      const haystack = [
        starter.name,
        starter.summary,
        ...starter.tags,
        starter.manifest.title,
        starter.manifest.description ?? "",
      ]
        .join(" ")
        .toLowerCase();

      return haystack.includes(normalizedQuery);
    });
}

export function getSimulationStarterById(
  starterId: string,
): SimulationStarter | null {
  const starter = simulationStarters.find((item) => item.id === starterId);
  return starter ? hydrateStarter(starter) : null;
}

export function getSimulationStarterByLegacyPresetId(
  legacyPresetId: string,
): SimulationStarter | null {
  const normalizedId = legacyPresetId.trim().toLowerCase();
  const starter = simulationStarters.find((item) =>
    (STARTER_LEGACY_PRESET_IDS[item.id] ?? []).some(
      (alias) => alias.toLowerCase() === normalizedId,
    ),
  );
  return starter ? hydrateStarter(starter) : null;
}

export function resolveSimulationStarter(
  starterRef: string,
): {
  starter: SimulationStarter;
  matchedBy: "starter_id" | "legacy_preset";
} | null {
  const byId = getSimulationStarterById(starterRef);
  if (byId) {
    return { starter: byId, matchedBy: "starter_id" };
  }

  const byLegacyPreset = getSimulationStarterByLegacyPresetId(starterRef);
  if (byLegacyPreset) {
    return { starter: byLegacyPreset, matchedBy: "legacy_preset" };
  }

  return null;
}

export function getSimulationStarterCatalogSummary(): SimulationStarterCatalogSummary {
  const starters = simulationStarters.map(hydrateStarter);
  const byDomain = {
    PHYSICS: 0,
    CHEMISTRY: 0,
    BIOLOGY: 0,
    MEDICINE: 0,
    ECONOMICS: 0,
    CS_DISCRETE: 0,
    ENGINEERING: 0,
    MATHEMATICS: 0,
  } satisfies Record<SimulationDomain, number>;
  const byDifficulty = {
    beginner: 0,
    intermediate: 0,
    advanced: 0,
  } satisfies Record<SimulationStarterDifficulty, number>;
  const byAudience = {
    k12: 0,
    undergraduate: 0,
    graduate: 0,
    professional: 0,
  } satisfies Record<SimulationStarter["audience"], number>;

  for (const starter of starters) {
    byDomain[starter.domain]++;
    byDifficulty[starter.difficulty]++;
    byAudience[starter.audience]++;
  }

  return {
    total: starters.length,
    byDomain,
    byDifficulty,
    byAudience,
    legacyPresetCoverage: starters.reduce(
      (count, starter) => count + starter.legacyPresetIds.length,
      0,
    ),
  };
}
