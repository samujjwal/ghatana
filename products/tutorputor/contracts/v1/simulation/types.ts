/**
 * Universal Simulation Protocol (USP) - Core Types
 *
 * @doc.type module
 * @doc.purpose Define core simulation manifest, entity, step, and keyframe types
 * @doc.layer contracts
 * @doc.pattern Schema
 */

import type { TenantId, UserId, ModuleId } from "../types";

// =============================================================================
// Branded IDs for Type Safety
// =============================================================================

export type SimulationId = string & { readonly __simulationId: unique symbol };
export type SimEntityId = string & { readonly __simEntityId: unique symbol };
export type SimStepId = string & { readonly __simStepId: unique symbol };
export type SimulationSessionId = string & {
  readonly __simulationSessionId: unique symbol;
};

// =============================================================================
// Simulation Domain Types
// =============================================================================

/**
 * Supported simulation domains across the platform.
 */
export type SimulationDomain =
  | "CS_DISCRETE" // Algorithms, data structures
  | "PHYSICS" // Mechanics, waves, thermodynamics
  | "ECONOMICS" // System dynamics, markets
  | "CHEMISTRY" // Reactions, molecular structures
  | "BIOLOGY" // Cellular, molecular biology
  | "MEDICINE" // PK/PD, epidemiology
  | "ENGINEERING" // Circuits, mechanics
  | "MATHEMATICS"; // Geometric, calculus visualizations

/**
 * Easing functions for smooth animations.
 */
export type EasingFunction =
  | "linear"
  | "easeIn"
  | "easeOut"
  | "easeInOut"
  | "easeInQuad"
  | "easeOutQuad"
  | "easeInOutQuad"
  | "easeInCubic"
  | "easeOutCubic"
  | "easeInOutCubic"
  | "easeInElastic"
  | "easeOutElastic"
  | "easeInBounce"
  | "easeOutBounce"
  | "spring";

// =============================================================================
// Entity System (USP Core)
// =============================================================================

/**
 * Base entity type - all simulation entities extend this.
 * Provides position, visual properties, and metadata.
 */
export interface SimEntityBase {
  id: SimEntityId;
  type: string;
  label?: string;
  x: number;
  y: number;
  z?: number;
  width?: number;
  height?: number;
  rotation?: number;
  scale?: number;
  opacity?: number;
  color?: string;
  strokeColor?: string;
  strokeWidth?: number;
  visible?: boolean;
  layer?: number;
  metadata?: Record<string, unknown>;
}

/**
 * Discrete algorithm entities (nodes, edges, pointers).
 */
export interface DiscreteNodeEntity extends SimEntityBase {
  type: "node";
  value: string | number;
  highlighted?: boolean;
  visited?: boolean;
  comparing?: boolean;
  sorted?: boolean;
  shape?: "rect" | "circle" | "diamond" | "hexagon";
}

export interface DiscreteEdgeEntity extends SimEntityBase {
  type: "edge";
  sourceId: SimEntityId;
  targetId: SimEntityId;
  directed?: boolean;
  weight?: number;
  curved?: boolean;
  animated?: boolean;
}

export interface DiscretePointerEntity extends SimEntityBase {
  type: "pointer";
  targetId: SimEntityId;
  pointerLabel?: string;
  style?: "arrow" | "bracket" | "underline";
}

/**
 * Physics simulation entities.
 */
export interface PhysicsBodyEntity extends SimEntityBase {
  type: "rigidBody";
  mass: number;
  velocityX?: number;
  velocityY?: number;
  /** Legacy velocity object (stories convenience). */
  velocity?: { x: number; y: number };
  accelerationX?: number;
  accelerationY?: number;
  friction?: number;
  restitution?: number;
  fixed?: boolean;
  /** Alias for fixed (pinned bodies don't move). */
  pinned?: boolean;
  shape?: "circle" | "rect" | "rectangle" | "polygon";
  /** Body width for rect shapes (legacy). */
  width?: number;
  /** Body height for rect shapes (legacy). */
  height?: number;
  vertices?: Array<{ x: number; y: number }>;
  /** Visual highlight state. */
  highlighted?: boolean;
}

export interface PhysicsSpringEntity extends SimEntityBase {
  type: "spring";
  anchorId?: SimEntityId;
  attachId?: SimEntityId;
  /** Legacy alias for anchorId. */
  body1Id?: SimEntityId;
  /** Legacy alias for attachId. */
  body2Id?: SimEntityId;
  stiffness: number;
  damping?: number;
  restLength: number;
}

export interface PhysicsVectorEntity extends SimEntityBase {
  type: "vector";
  attachId?: SimEntityId;
  /** Legacy alias for attachId. */
  attachedToId?: SimEntityId;
  magnitude?: number;
  angle?: number;
  /** Legacy: x-component of vector (pixels). */
  dx?: number;
  /** Legacy: y-component of vector (pixels). */
  dy?: number;
  vectorType?: "velocity" | "acceleration" | "force" | "displacement";
}

export interface PhysicsParticleEntity extends SimEntityBase {
  type: "particle";
  velocityX?: number;
  velocityY?: number;
  /** Legacy velocity object (stories convenience). */
  velocity?: { x: number; y: number };
  lifetime?: number;
  age?: number;
  /** Visual radius in world units. */
  size?: number;
}

/**
 * Economics/System Dynamics entities.
 */
export interface EconStockEntity extends SimEntityBase {
  type: "stock";
  value: number;
  minValue?: number;
  maxValue?: number;
  units?: string;
}

export interface EconFlowEntity extends SimEntityBase {
  type: "flow";
  sourceId: SimEntityId;
  targetId: SimEntityId;
  rate: number;
  equation?: string;
  delay?: number;
}

export interface EconAgentEntity extends SimEntityBase {
  type: "agent";
  state: Record<string, unknown>;
  agentType?: string;
  behavior?: string;
}

/**
 * Chemistry entities.
 */
export interface ChemAtomEntity extends SimEntityBase {
  type: "atom";
  element: string;
  charge?: number;
  isotope?: number;
  hybridization?: "sp" | "sp2" | "sp3" | "sp3d" | "sp3d2";
  /** Visual highlight state for selection/hover rendering. */
  highlighted?: boolean;
}

export interface ChemBondEntity extends SimEntityBase {
  type: "bond";
  atom1Id: SimEntityId;
  atom2Id: SimEntityId;
  bondOrder?: 1 | 2 | 3 | 1.5;
  bondType?:
    | "covalent"
    | "ionic"
    | "hydrogen"
    | "metallic"
    | "single"
    | "double"
    | "triple"
    | "aromatic";
  stereochemistry?: "up" | "down" | "none";
  /** Visual highlight state. */
  highlighted?: boolean;
}

export interface ChemMoleculeEntity extends SimEntityBase {
  type: "molecule";
  smiles?: string;
  formula?: string;
  name?: string;
  atomIds: SimEntityId[];
  bondIds?: SimEntityId[];
}

export interface ChemReactionArrowEntity extends SimEntityBase {
  type: "reactionArrow";
  arrowType?: "forward" | "reverse" | "equilibrium" | "resonance";
  /** Alternative to arrowType for stories/rendering convenience. */
  arrowStyle?: "forward" | "reverse" | "equilibrium" | "resonance";
  conditions?: string[];
  catalyst?: string;
  /** Start position (alternative to x for multi-point arrows). */
  startX?: number;
  startY?: number;
  endX?: number;
  endY?: number;
}

export interface ChemEnergyProfileEntity extends SimEntityBase {
  type: "energyProfile";
  points: Array<{ x: number; y: number; label?: string }>;
  activationEnergy?: number;
  deltaH?: number;
}

/**
 * Biology entities.
 */
export interface BioCellEntity extends SimEntityBase {
  type: "cell";
  cellType?: "prokaryote" | "eukaryote" | "plant" | "animal";
  organelles?: SimEntityId[];
  membranePermeability?: number;
  /** Rendering radius in world units (alias for visual size). */
  radius?: number;
  /** Visual highlight state. */
  highlighted?: boolean;
}

export interface BioOrganelleEntity extends SimEntityBase {
  type: "organelle";
  organelleType:
    | "nucleus"
    | "mitochondria"
    | "ribosome"
    | "er"
    | "golgi"
    | "lysosome"
    | "chloroplast"
    | "vacuole";
  activity?: number;
  /** ID of the parent cell entity that contains this organelle. */
  containedInId?: SimEntityId;
}

export interface BioCompartmentEntity extends SimEntityBase {
  type: "compartment";
  volume?: number;
  concentration?: Record<string, number>;
  permeability?: Record<string, number>;
  /** Compartment classification for rendering. */
  compartmentType?: string;
}

export interface BioEnzymeEntity extends SimEntityBase {
  type: "enzyme";
  name: string;
  kcat?: number;
  km?: number;
  active?: boolean;
  substrate?: string;
  product?: string;
}

export interface BioSignalEntity extends SimEntityBase {
  type: "signal";
  signalType:
    | "hormone"
    | "neurotransmitter"
    | "cytokine"
    | "ion"
    | "receptor"
    | "secondMessenger"
    | "ligand";
  concentration?: number;
  receptor?: SimEntityId;
  /** Display name for the signal molecule. */
  name?: string;
  /** Whether the signal is currently active. */
  active?: boolean;
}

export interface BioGeneEntity extends SimEntityBase {
  type: "gene";
  /** Gene name or symbol (e.g. 'BRCA1'). */
  name?: string;
  sequence?: string;
  promoterActive?: boolean;
  expressionLevel?: number;
  product?: string;
  /** Visual highlight state. */
  highlighted?: boolean;
}

/**
 * Medicine/PK-PD entities.
 */
export interface MedCompartmentEntity extends SimEntityBase {
  type: "pkCompartment";
  compartmentType: "central" | "peripheral" | "effect";
  volume: number;
  concentration: number;
  ke?: number; // elimination rate constant
  k12?: number; // transfer rate to peripheral
  k21?: number; // transfer rate from peripheral
  /** Visual highlight state. */
  highlighted?: boolean;
}

export interface MedDoseEntity extends SimEntityBase {
  type: "dose";
  amount: number;
  route: "iv" | "oral" | "im" | "sc" | "topical" | "subq";
  bioavailability?: number;
  absorptionRate?: number;
  /** Time of administration (hours). */
  time?: number;
  /** Visual highlight state. */
  highlighted?: boolean;
}

export interface MedInfectionAgentEntity extends SimEntityBase {
  type: "infectionAgent";
  agentType: "virus" | "bacteria" | "parasite" | "fungus";
  population?: number;
  /** Legacy alias for population. */
  load?: number;
  /** Common name for the agent strain/species. */
  name?: string;
  reproductionRate?: number;
  infectivity?: number;
  /** Visual highlight state. */
  highlighted?: boolean;
}

/**
 * Union of all entity types for type-safe handling.
 */
export type SimEntity =
  | SimEntityBase
  | DiscreteNodeEntity
  | DiscreteEdgeEntity
  | DiscretePointerEntity
  | PhysicsBodyEntity
  | PhysicsSpringEntity
  | PhysicsVectorEntity
  | PhysicsParticleEntity
  | EconStockEntity
  | EconFlowEntity
  | EconAgentEntity
  | ChemAtomEntity
  | ChemBondEntity
  | ChemMoleculeEntity
  | ChemReactionArrowEntity
  | ChemEnergyProfileEntity
  | BioCellEntity
  | BioOrganelleEntity
  | BioCompartmentEntity
  | BioEnzymeEntity
  | BioSignalEntity
  | BioGeneEntity
  | MedCompartmentEntity
  | MedDoseEntity
  | MedInfectionAgentEntity;

// =============================================================================
// Action System (Simulation Steps)
// =============================================================================

/**
 * Base action interface - all step actions extend this.
 */
export interface SimActionBase {
  action: string;
  targetId?: SimEntityId;
  duration?: number;
  easing?: EasingFunction;
  delay?: number;
}

/**
 * Discrete algorithm actions.
 */
export interface CreateEntityAction extends SimActionBase {
  action: "CREATE_ENTITY";
  entity: SimEntity;
}

export interface RemoveEntityAction extends SimActionBase {
  action: "REMOVE_ENTITY";
  targetId: SimEntityId;
}

export interface MoveEntityAction extends SimActionBase {
  action: "MOVE";
  targetId: SimEntityId;
  toX: number;
  toY: number;
  toZ?: number;
}

export interface HighlightAction extends SimActionBase {
  action: "HIGHLIGHT";
  targetIds: SimEntityId[];
  style?: "primary" | "secondary" | "error" | "success" | "warning";
}

export interface CompareAction extends SimActionBase {
  action: "COMPARE";
  leftId: SimEntityId;
  rightId: SimEntityId;
  result?: "less" | "equal" | "greater";
}

export interface SwapAction extends SimActionBase {
  action: "SWAP";
  id1: SimEntityId;
  id2: SimEntityId;
}

export interface SetValueAction extends SimActionBase {
  action: "SET_VALUE";
  targetId: SimEntityId;
  value: unknown;
  property?: string;
}

export interface AnnotateAction extends SimActionBase {
  action: "ANNOTATE";
  targetId?: SimEntityId;
  text: string;
  position?: "top" | "bottom" | "left" | "right" | "center";
}

/**
 * Physics actions.
 */
export interface SetInitialVelocityAction extends SimActionBase {
  action: "SET_INITIAL_VELOCITY";
  targetId: SimEntityId;
  vx: number;
  vy: number;
}

export interface ApplyForceAction extends SimActionBase {
  action: "APPLY_FORCE";
  targetId: SimEntityId;
  fx: number;
  fy: number;
  impulse?: boolean;
}

export interface ConnectSpringAction extends SimActionBase {
  action: "CONNECT_SPRING";
  body1Id: SimEntityId;
  body2Id: SimEntityId;
  stiffness: number;
  damping: number;
  restLength?: number;
}

export interface ReleaseAction extends SimActionBase {
  action: "RELEASE";
  targetId: SimEntityId;
}

export interface SetGravityAction extends SimActionBase {
  action: "SET_GRAVITY";
  gx: number;
  gy: number;
}

/**
 * Economics actions.
 */
export interface UpdateFlowRateAction extends SimActionBase {
  action: "UPDATE_FLOW_RATE";
  targetId: SimEntityId;
  rate: number;
}

export interface SetStockValueAction extends SimActionBase {
  action: "SET_STOCK_VALUE";
  targetId: SimEntityId;
  value: number;
}

export interface SpawnAgentAction extends SimActionBase {
  action: "SPAWN_AGENT";
  entity: EconAgentEntity;
}

export interface DisplayChartAction extends SimActionBase {
  action: "DISPLAY_CHART";
  chartType: "line" | "bar" | "area" | "pie";
  dataSourceIds: SimEntityId[];
  title?: string;
  position: { x: number; y: number; width: number; height: number };
}

/**
 * Chemistry actions.
 */
export interface CreateBondAction extends SimActionBase {
  action: "CREATE_BOND";
  atom1Id: SimEntityId;
  atom2Id: SimEntityId;
  bondOrder: 1 | 2 | 3;
}

export interface BreakBondAction extends SimActionBase {
  action: "BREAK_BOND";
  bondId: SimEntityId;
  homolytic?: boolean;
}

export interface RearrangeAction extends SimActionBase {
  action: "REARRANGE";
  atomIds: SimEntityId[];
  newPositions: Array<{ id: SimEntityId; x: number; y: number }>;
}

export interface HighlightAtomsAction extends SimActionBase {
  action: "HIGHLIGHT_ATOMS";
  atomIds: SimEntityId[];
  style?: "nucleophile" | "electrophile" | "leaving_group" | "active_site";
}

export interface SetReactionConditionsAction extends SimActionBase {
  action: "SET_REACTION_CONDITIONS";
  temperature?: number;
  pressure?: number;
  solvent?: string;
  catalyst?: string;
  ph?: number;
}

export interface DisplayFormulaAction extends SimActionBase {
  action: "DISPLAY_FORMULA";
  formula: string;
  position: { x: number; y: number };
  style?: "molecular" | "structural" | "condensed" | "lewis";
}

export interface ShowEnergyProfileAction extends SimActionBase {
  action: "SHOW_ENERGY_PROFILE";
  profileId: SimEntityId;
  animate?: boolean;
}

/**
 * Biology actions.
 */
export interface DiffuseAction extends SimActionBase {
  action: "DIFFUSE";
  molecule: string;
  fromId: SimEntityId;
  toId: SimEntityId;
  rate: number;
}

export interface TransportAction extends SimActionBase {
  action: "TRANSPORT";
  molecule: string;
  fromId: SimEntityId;
  toId: SimEntityId;
  transporterType: "passive" | "active" | "facilitated";
  atpCost?: number;
}

export interface TranscribeAction extends SimActionBase {
  action: "TRANSCRIBE";
  geneId: SimEntityId;
  mRnaId: SimEntityId;
}

export interface TranslateAction extends SimActionBase {
  action: "TRANSLATE";
  mRnaId: SimEntityId;
  proteinId: SimEntityId;
}

export interface MetaboliseAction extends SimActionBase {
  action: "METABOLISE";
  enzymeId: SimEntityId;
  substrateId: SimEntityId;
  productId: SimEntityId;
  rate?: number;
}

export interface GrowDivideAction extends SimActionBase {
  action: "GROW_DIVIDE";
  cellId: SimEntityId;
  phase: "G1" | "S" | "G2" | "M";
}

/**
 * Medicine/PK-PD actions.
 */
export interface AbsorbAction extends SimActionBase {
  action: "ABSORB";
  doseId: SimEntityId;
  compartmentId: SimEntityId;
  rate: number;
}

export interface EliminateAction extends SimActionBase {
  action: "ELIMINATE";
  compartmentId: SimEntityId;
  rate: number;
  route: "renal" | "hepatic" | "both";
}

export interface SpreadDiseaseAction extends SimActionBase {
  action: "SPREAD_DISEASE";
  agentId: SimEntityId;
  beta: number; // transmission rate
  gamma: number; // recovery rate
}

export interface SignalAction extends SimActionBase {
  action: "SIGNAL";
  signalId: SimEntityId;
  targetId: SimEntityId;
  response: "activate" | "inhibit" | "modulate";
}

/**
 * Union of all action types.
 */
export type SimAction =
  | CreateEntityAction
  | RemoveEntityAction
  | MoveEntityAction
  | HighlightAction
  | CompareAction
  | SwapAction
  | SetValueAction
  | AnnotateAction
  | SetInitialVelocityAction
  | ApplyForceAction
  | ConnectSpringAction
  | ReleaseAction
  | SetGravityAction
  | UpdateFlowRateAction
  | SetStockValueAction
  | SpawnAgentAction
  | DisplayChartAction
  | CreateBondAction
  | BreakBondAction
  | RearrangeAction
  | HighlightAtomsAction
  | SetReactionConditionsAction
  | DisplayFormulaAction
  | ShowEnergyProfileAction
  | DiffuseAction
  | TransportAction
  | TranscribeAction
  | TranslateAction
  | MetaboliseAction
  | GrowDivideAction
  | AbsorbAction
  | EliminateAction
  | SpreadDiseaseAction
  | SignalAction;

// =============================================================================
// Simulation Step (Discrete Time Unit)
// =============================================================================

export interface SimAnnotation {
  id: string;
  text: string;
  position: { x: number; y: number };
  targetId?: SimEntityId;
}

export interface SimAudioCue {
  soundId: string;
  volume?: number;
  loop?: boolean;
}

export interface SimCameraState {
  x: number;
  y: number;
  zoom: number;
}

/**
 * A single step in the simulation sequence.
 * Contains actions to execute and narrative context.
 */
export interface SimulationStep {
  id: SimStepId;
  orderIndex: number;
  title?: string;
  description?: string;
  actions: SimAction[];
  narration?: string;
  tutorContext?: string;
  checkpoint?: boolean;
  breakpoint?: boolean;
  assessmentHook?: {
    questionId?: string;
    prompt?: string;
  };
  duration?: number;
  easing?: string;
  annotations?: SimAnnotation[];
  audio?: SimAudioCue;
  camera?: SimCameraState;
}

// =============================================================================
// Keyframe (Rendered State at a Point in Time)
// =============================================================================

/**
 * A complete snapshot of simulation state at a specific time.
 * Generated by the kernel from manifest steps.
 */
export interface SimKeyframe {
  stepIndex: number;
  timestamp: number;
  entities: SimEntity[];
  annotations: SimAnnotation[];
  audio?: SimAudioCue;
  camera?: SimCameraState;
  charts?: Array<{
    id: string;
    type: "line" | "bar" | "area" | "pie";
    data: Array<{ x: number; y: number; label?: string }>;
    position: { x: number; y: number; width: number; height: number };
  }>;
  metadata?: Record<string, unknown>;
}

// =============================================================================
// Simulation Manifest (Complete Definition)
// =============================================================================

/**
 * Physics-specific metadata for simulations.
 */
export interface PhysicsMetadata {
  gravity?: { x: number; y: number };
  friction?: number;
  restitution?: number;
  timeScale?: number;
  units?: {
    length: "m" | "cm" | "mm" | "km";
    mass: "kg" | "g" | "mg";
    time: "s" | "ms";
  };
}

/**
 * Economics-specific metadata.
 */
export interface EconomicsMetadata {
  timeStep: number;
  integrationMethod: "euler" | "rk4";
  simulationDuration: number;
  initialConditions?: Record<string, number>;
}

/**
 * Chemistry-specific metadata.
 */
export interface ChemistryMetadata {
  reactionType?:
    | "substitution"
    | "elimination"
    | "addition"
    | "oxidation"
    | "reduction"
    | "acid_base"
    | "combustion";
  mechanism?: string;
  conditions?: {
    temperature?: number;
    pressure?: number;
    solvent?: string;
    catalyst?: string;
  };
}

/**
 * Biology-specific metadata.
 */
export interface BiologyMetadata {
  scale:
    | "molecular"
    | "cellular"
    | "tissue"
    | "organ"
    | "organism"
    | "population";
  timeScale?: "ms" | "s" | "min" | "hour" | "day";
  process?: string;
}

/**
 * Medicine-specific metadata.
 */
export interface MedicineMetadata {
  modelType: "one_compartment" | "two_compartment" | "emax" | "sir" | "seir";
  drugName?: string;
  halfLife?: number;
  therapeuticRange?: { min: number; max: number };
  dosing?: {
    amount: number;
    interval: number;
    route: string;
  };
}

/**
 * Domain-specific metadata union.
 */
export type DomainMetadata =
  | { domain: "PHYSICS"; physics: PhysicsMetadata }
  | { domain: "ECONOMICS"; economics: EconomicsMetadata }
  | { domain: "CHEMISTRY"; chemistry: ChemistryMetadata }
  | { domain: "BIOLOGY"; biology: BiologyMetadata }
  | { domain: "MEDICINE"; medicine: MedicineMetadata }
  | { domain: "CS_DISCRETE" }
  | { domain: "ENGINEERING" }
  | { domain: "MATHEMATICS" };

/**
 * Canvas configuration for rendering.
 */
export interface CanvasConfig {
  width: number;
  height: number;
  backgroundColor?: string;
  gridEnabled?: boolean;
  gridSize?: number;
  panEnabled?: boolean;
  zoomEnabled?: boolean;
  minZoom?: number;
  maxZoom?: number;
}

/**
 * Playback configuration.
 */
export interface PlaybackConfig {
  defaultSpeed: number;
  allowSpeedChange?: boolean;
  minSpeed?: number;
  maxSpeed?: number;
  allowScrubbing?: boolean;
  autoPlay?: boolean;
  loop?: boolean;
}

// =============================================================================
// Lifecycle & Governance Types
// =============================================================================

/**
 * Lifecycle tracking for simulation manifests.
 */
export interface SimulationLifecycle {
  status: "draft" | "validated" | "published" | "archived";
  createdBy: "userId" | "ai" | "template";
  validatedAt?: number;
  publishedAt?: number;
}

/**
 * Safety constraints for simulation execution.
 */
export interface SimulationSafety {
  parameterBounds: {
    enforced: boolean;
    maxIterations?: number;
  };
  executionLimits: {
    maxSteps: number;
    maxRuntimeMs: number;
  };
}

/**
 * Deterministic replay configuration.
 */
export interface SimulationReplay {
  deterministic: boolean;
  seedStrategy: "fixed" | "perSession";
}

/**
 * Evidence-Centered Design metadata.
 */
export interface ECDMetadata {
  claims: Array<{
    id: string;
    description: string;
    evidenceIds: string[];
  }>;
  evidence: Array<{
    id: string;
    source:
      | "telemetry.parameterChange"
      | "telemetry.timeOnTask"
      | "grading.stateComparison";
    tolerance?: number;
    requiredForClaim: string[];
  }>;
  tasks: Array<{
    id: string;
    type:
      | "prediction"
      | "manipulation"
      | "explanation"
      | "design"
      | "diagnosis";
    claimIds: string[];
  }>;
}

/**
 * Template governance metadata.
 */
export interface TemplateGovernance {
  reviewStatus: "draft" | "submitted" | "approved" | "rejected" | "deprecated";
  reviewerNotes?: string;
  lastValidatedAt?: number;
  approvedBy?: UserId;
  version: string;
  changelog?: string;
}

/**
 * Rendering capabilities for VR/AR forward compatibility.
 */
export interface RenderingCapabilities {
  requiredCapabilities: Array<"2d" | "3d" | "vr" | "ar">;
  optionalCapabilities: Array<"2d" | "3d" | "vr" | "ar">;
}

/**
 * Institutional compliance metadata.
 */
export interface ComplianceMetadata {
  dataRetentionDays: number;
  analyticsConsentRequired: boolean;
  auditLevel: "none" | "basic" | "full";
}

/**
 * The complete Simulation Manifest - the source of truth for any simulation.
 */
export interface SimulationManifest {
  id: SimulationId;
  version: string;
  title: string;
  description?: string;
  domain: SimulationDomain;
  domainMetadata?: DomainMetadata;

  // Author & review
  authorId: UserId;
  tenantId: TenantId;
  needsReview?: boolean;
  reviewNotes?: string;

  // Module association
  moduleId?: ModuleId;
  blockId?: string;

  // Canvas & playback
  canvas: CanvasConfig;
  playback: PlaybackConfig;

  // Initial state
  initialEntities: SimEntity[];

  // Simulation sequence
  steps: SimulationStep[];

  // Accessibility
  accessibility?: {
    altText?: string;
    screenReaderNarration?: boolean;
    reducedMotion?: boolean;
    highContrast?: boolean;
  };

  // NEW: Lifecycle tracking
  lifecycle?: SimulationLifecycle;

  // NEW: Safety constraints
  safety?: SimulationSafety;

  // NEW: Deterministic replay
  replay?: SimulationReplay;

  // NEW: Evidence-Centered Design metadata
  ecd?: ECDMetadata;

  // NEW: Rendering capabilities
  rendering?: RenderingCapabilities;

  // NEW: Compliance metadata
  compliance?: ComplianceMetadata;

  // Timestamps
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;

  // Schema versioning
  schemaVersion: string;
}

// =============================================================================
// Kernel Execution Types
// =============================================================================

/**
 * Request to run a simulation and generate keyframes.
 */
export interface SimulationRunRequest {
  manifest: SimulationManifest;
  startStep?: number;
  endStep?: number;
  samplingRate?: number; // keyframes per second
  seed?: number; // for deterministic physics
}

/**
 * Result from running a simulation.
 */
export interface SimulationRunResult {
  simulationId: SimulationId;
  keyframes: SimKeyframe[];
  totalSteps: number;
  executionTimeMs: number;
  warnings?: string[];
  errors?: string[];
}

/**
 * Validation result for a manifest.
 */
export interface ManifestValidationResult {
  valid: boolean;
  errors: Array<{
    path: string;
    message: string;
    severity: "error" | "warning";
  }>;
  warnings: Array<{
    path: string;
    message: string;
  }>;
}

// =============================================================================
// Parser/Author Types
// =============================================================================

/**
 * Request to generate a manifest from natural language.
 */
export interface GenerateManifestRequest {
  tenantId: TenantId;
  userId: UserId;
  prompt: string;
  domain?: SimulationDomain;
  constraints?: {
    maxSteps?: number;
    maxEntities?: number;
    targetDuration?: number;
  };
  options?: {
    includeAnnotations?: boolean;
    complexity?: "simple" | "medium" | "complex";
  };
}

/**
 * Result from manifest generation.
 */
export interface GenerateManifestResult {
  manifest: SimulationManifest;
  confidence: number;
  needsReview: boolean;
  suggestions?: string[];
  alternativeInterpretations?: string[];
}

/**
 * Request to refine an existing manifest.
 */
export interface RefineManifestRequest {
  tenantId: TenantId;
  userId: UserId;
  manifest: SimulationManifest;
  refinement: string;
  targetSteps?: SimStepId[];
}

/**
 * Request for parameter suggestions.
 */
export interface SuggestParametersRequest {
  tenantId: TenantId;
  domain: SimulationDomain;
  context: string;
  currentParams?: Record<string, unknown>;
}

/**
 * Parameter suggestion result.
 */
export interface SuggestParametersResult {
  suggestions: Array<{
    param: string;
    value: unknown;
    rationale: string;
    confidence: number;
  }>;
}

// =============================================================================
// Cache & Storage Types
// =============================================================================

/**
 * Cached simulation data.
 */
export interface SimulationCache {
  manifestHash: string;
  keyframes: SimKeyframe[];
  generatedAt: string;
  expiresAt: string;
  hitCount: number;
}

/**
 * Simulation analytics event.
 */
export interface SimulationAnalyticsEvent {
  eventType: "PLAY" | "PAUSE" | "SEEK" | "SPEED_CHANGE" | "COMPLETE" | "ERROR";
  simulationId: SimulationId;
  userId: UserId;
  tenantId: TenantId;
  timestamp: string;
  stepIndex?: number;
  metadata?: Record<string, unknown>;
}

// =============================================================================
// Configuration Aliases (for Kernel Compatibility)
// =============================================================================

export type PhysicsConfig = PhysicsMetadata;
export type EconomicsConfig = EconomicsMetadata;
export type ChemistryConfig = ChemistryMetadata;
export type BiologyConfig = BiologyMetadata;
export type MedicineConfig = MedicineMetadata;

// =============================================================================
// Natural Language Intent Types
// =============================================================================

/**
 * Intent types for simulation refinement.
 */
export type IntentType =
  | "add_entity"
  | "remove_entity"
  | "modify_entity"
  | "add_step"
  | "remove_step"
  | "modify_step"
  | "change_speed"
  | "change_visual"
  | "add_annotation"
  | "change_domain_config"
  | "explain"
  | "clarify"
  | "undo"
  | "redo"
  | "unknown";

/**
 * Visual style parameters for intents.
 */
export interface IntentVisualParams {
  color: string;
  size: number;
  opacity: number;
  shape: string;
}

/**
 * Intent parameters.
 */
export interface IntentParams {
  /** Target entity ID or name */
  targetEntity?: string;
  /** Target step ID or index */
  targetStep?: string | number;
  /** New value for modifications */
  newValue?: unknown;
  /** Property to modify */
  property?: string;
  /** Entity type for additions */
  entityType?: string;
  /** Position for entity placement */
  position?: { x: number; y: number; z?: number };
  /** Visual style changes */
  visual?: Partial<IntentVisualParams>;
  /** Duration changes (ms) */
  duration?: number;
  /** Text content (for annotations, labels) */
  text?: string;
  /** Color specification */
  color?: string;
}

/**
 * Parsed intent from natural language.
 */
export interface ParsedIntent {
  /** The classified intent type */
  type: IntentType;
  /** Confidence score (0-1) */
  confidence: number;
  /** Extracted parameters */
  params: IntentParams;
  /** Original user input */
  originalInput: string;
  /** Normalized input */
  normalizedInput: string;
}

// =============================================================================
// Template Types
// =============================================================================

/**
 * Simulation template with governance metadata.
 */
export interface SimulationTemplate {
  id: string;
  tenantId: TenantId;
  authorId: UserId;
  manifest: SimulationManifest;
  domain: SimulationDomain;
  title: string;
  description?: string;
  version: string;
  parentVersionId?: string;
  domainConceptId?: string;
  governance?: TemplateGovernance;
  createdAt: string;
  updatedAt: string;
}
