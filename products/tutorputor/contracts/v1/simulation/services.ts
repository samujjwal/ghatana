/**
 * Simulation Engine Service Interfaces
 * 
 * @doc.type module
 * @doc.purpose Define service contracts for simulation authoring, runtime, and kernels
 * @doc.layer contracts
 * @doc.pattern ServiceInterface
 */

import type { TenantId, UserId, ModuleId } from "../types";
import type {
  SimulationId,
  SimulationManifest,
  SimulationStep,
  SimKeyframe,
  SimulationDomain,
  SimulationRunRequest,
  SimulationRunResult,
  GenerateManifestRequest,
  GenerateManifestResult,
  RefineManifestRequest,
  SuggestParametersRequest,
  SuggestParametersResult,
  ManifestValidationResult,
  SimulationAnalyticsEvent,
  SimulationSessionId
} from "./types";

// =============================================================================
// Simulation Author Service (AI-Powered Manifest Generation)
// =============================================================================

/**
 * Service for AI-powered simulation manifest authoring.
 * 
 * @doc.type interface
 * @doc.purpose Generate and refine simulation manifests from natural language
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SimulationAuthorService {
  /**
   * Generate a simulation manifest from a natural language prompt.
   * Uses domain-specific prompt packs and few-shot examples.
   */
  generateManifest(
    request: GenerateManifestRequest
  ): Promise<GenerateManifestResult>;

  /**
   * Refine an existing manifest based on feedback or additional requirements.
   */
  refineManifest(
    request: RefineManifestRequest
  ): Promise<GenerateManifestResult>;

  /**
   * Suggest parameters for a given domain and context.
   * Useful for physics values, reaction conditions, PK parameters, etc.
   */
  suggestParameters(
    request: SuggestParametersRequest
  ): Promise<SuggestParametersResult>;

  /**
   * Validate a manifest against the USP schema and domain rules.
   */
  validateManifest(
    manifest: SimulationManifest
  ): Promise<ManifestValidationResult>;
}

// =============================================================================
// Simulation Kernel Service (Domain-Specific Execution Engines)
// =============================================================================

/**
 * Low-level stateful kernel interface for simulation execution.
 * Used by the SDK for step-by-step execution.
 * 
 * @doc.type interface
 * @doc.purpose Low-level stateful kernel for step execution
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface SimKernelService {
  /**
   * The domain this kernel handles.
   */
  readonly domain: SimulationDomain;

  /**
   * Initialize the kernel with a manifest.
   */
  initialize(manifest: SimulationManifest): void;

  /**
   * Execute one simulation step.
   */
  step(): void;

  /**
   * Interpolate state at fractional step position (0-1).
   */
  interpolate(t: number): Partial<SimKeyframe>;

  /**
   * Serialize the current kernel state.
   */
  serialize(): string;

  /**
   * Deserialize the kernel state.
   */
  deserialize(state: string): void;

  /**
   * Reset the kernel to initial state.
   */
  reset(): void;

  /**
   * Get kernel analytics/metrics.
   */
  getAnalytics(): Record<string, unknown>;

  /**
   * Execute a simulation and generate keyframes.
   */
  run(request: SimulationRunRequest): Promise<SimulationRunResult>;

  /**
   * Validate that this kernel can execute the given manifest.
   */
  canExecute(manifest: SimulationManifest): boolean;

  /**
   * Get kernel health status.
   */
  checkHealth(): Promise<boolean>;
}

// =============================================================================
// Simulation Runtime Service (Orchestration)
// =============================================================================

/**
 * Service for managing simulation sessions and playback.
 * 
 * @doc.type interface
 * @doc.purpose Orchestrate simulation sessions and playback
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SimRuntimeService {
  createSession(manifest: SimulationManifest): Promise<SimulationSessionId>;
  stepForward(sessionId: SimulationSessionId): Promise<SimKeyframe>;
  stepBackward(sessionId: SimulationSessionId): Promise<SimKeyframe>;
  seekToStep(sessionId: SimulationSessionId, stepIndex: number): Promise<SimKeyframe>;
  terminateSession(sessionId: SimulationSessionId): Promise<void>;
  getSessionState(sessionId: SimulationSessionId): Promise<any>;
}

/**
 * Base kernel interface - all domain kernels implement this.
 * 
 * @doc.type interface
 * @doc.purpose Execute simulation steps and generate keyframes
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface SimulationKernel extends SimKernelService { }

/**
 * Discrete algorithm kernel for sorting, searching, graph algorithms.
 * 
 * @doc.type interface
 * @doc.purpose Execute discrete algorithm simulations (bubble sort, DFS, etc.)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface DiscreteKernel extends SimulationKernel {
  readonly domain: "CS_DISCRETE";

  /**
   * Generate keyframes with easing interpolation for smooth animations.
   */
  interpolateKeyframes(
    keyframes: SimKeyframe[],
    targetFps: number
  ): SimKeyframe[];
}

/**
 * Physics kernel using Rapier WASM for rigid body simulation.
 * 
 * @doc.type interface
 * @doc.purpose Execute physics simulations (projectiles, springs, collisions)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface PhysicsKernel extends SimulationKernel {
  readonly domain: "PHYSICS";

  /**
   * Set physics world parameters.
   */
  configureWorld(config: {
    gravity?: { x: number; y: number };
    timeStep?: number;
    velocityIterations?: number;
    positionIterations?: number;
  }): void;

  /**
   * Run simulation with deterministic seed.
   */
  runDeterministic(
    request: SimulationRunRequest,
    seed: number
  ): Promise<SimulationRunResult>;
}

/**
 * System dynamics kernel for economics and feedback loops.
 * 
 * @doc.type interface
 * @doc.purpose Execute system dynamics simulations (stocks, flows, feedback)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface SystemDynamicsKernel extends SimulationKernel {
  readonly domain: "ECONOMICS";

  /**
   * Run with specified integration method.
   */
  runWithIntegration(
    request: SimulationRunRequest,
    method: "euler" | "rk4"
  ): Promise<SimulationRunResult>;

  /**
   * Validate conservation laws (mass, energy, etc.).
   */
  validateConservation(result: SimulationRunResult): {
    valid: boolean;
    violations: Array<{ law: string; deviation: number }>;
  };
}

/**
 * Chemistry kernel for molecular reactions and structure visualization.
 * 
 * @doc.type interface
 * @doc.purpose Execute chemistry simulations (reactions, molecular dynamics)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface ChemistryKernel extends SimulationKernel {
  readonly domain: "CHEMISTRY";

  /**
   * Parse SMILES notation and generate entity positions.
   */
  parseSMILES(smiles: string): Promise<{
    atoms: Array<{ element: string; x: number; y: number; charge?: number }>;
    bonds: Array<{ from: number; to: number; order: number }>;
  }>;

  /**
   * Validate chemical reaction (mass balance, charge balance, etc.).
   */
  validateReaction(
    reactants: string[],
    products: string[]
  ): {
    valid: boolean;
    massBalanced: boolean;
    chargeBalanced: boolean;
    errors: string[];
  };
}

/**
 * Biology kernel for cellular and molecular biology simulations.
 * 
 * @doc.type interface
 * @doc.purpose Execute biology simulations (transcription, metabolism, cell division)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface BiologyKernel extends SimulationKernel {
  readonly domain: "BIOLOGY";

  /**
   * Simulate diffusion using Fick's law.
   */
  simulateDiffusion(
    compartments: Array<{ id: string; concentration: number; volume: number }>,
    permeability: number,
    timeStep: number
  ): Array<{ id: string; concentration: number }>;

  /**
   * Simulate enzyme kinetics (Michaelis-Menten).
   */
  simulateEnzymeKinetics(
    substrate: number,
    enzyme: number,
    km: number,
    vmax: number,
    time: number
  ): { substrateRemaining: number; productFormed: number };
}

/**
 * Medicine kernel for PK/PD and epidemiology simulations.
 * 
 * @doc.type interface
 * @doc.purpose Execute medicine simulations (drug kinetics, disease spread)
 * @doc.layer product
 * @doc.pattern Kernel
 */
export interface MedicineKernel extends SimulationKernel {
  readonly domain: "MEDICINE";

  /**
   * Run one-compartment PK model.
   */
  runOneCompartmentPK(
    dose: number,
    volume: number,
    ke: number,
    timePoints: number[]
  ): Array<{ time: number; concentration: number }>;

  /**
   * Run two-compartment PK model.
   */
  runTwoCompartmentPK(
    dose: number,
    v1: number,
    v2: number,
    k12: number,
    k21: number,
    ke: number,
    timePoints: number[]
  ): Array<{ time: number; c1: number; c2: number }>;

  /**
   * Run Emax PD model.
   */
  runEmaxPD(
    concentrations: number[],
    emax: number,
    ec50: number,
    hill?: number
  ): Array<{ concentration: number; effect: number }>;

  /**
   * Run SIR/SEIR epidemiology model.
   */
  runEpidemiologyModel(
    model: "sir" | "seir",
    population: number,
    initialInfected: number,
    beta: number,
    gamma: number,
    days: number,
    sigma?: number
  ): Array<{
    day: number;
    susceptible: number;
    exposed?: number;
    infected: number;
    recovered: number;
  }>;
}

// =============================================================================
// Kernel Registry
// =============================================================================

/**
 * Registry for simulation kernels.
 * 
 * @doc.type interface
 * @doc.purpose Central registry for all domain-specific kernels
 * @doc.layer product
 * @doc.pattern Registry
 */
export interface KernelRegistry {
  /**
   * Register a kernel for a domain.
   */
  register(kernel: SimulationKernel): void;

  /**
   * Get kernel for a domain.
   */
  get(domain: SimulationDomain): SimulationKernel | undefined;

  /**
   * Check if a kernel is registered for domain.
   */
  has(domain: SimulationDomain): boolean;

  /**
   * List all registered domains.
   */
  listDomains(): SimulationDomain[];
}

// =============================================================================
// Simulation Runtime Service (Orchestration)
// =============================================================================

/**
 * Orchestration service for simulation execution.
 * 
 * @doc.type interface
 * @doc.purpose Coordinate manifest fetching, kernel execution, caching, and analytics
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SimulationRuntimeService {
  /**
   * Run a simulation by ID, using cached keyframes if available.
   */
  runSimulation(args: {
    tenantId: TenantId;
    userId: UserId;
    simulationId: SimulationId;
    useCache?: boolean;
  }): Promise<SimulationRunResult>;

  /**
   * Run a simulation from a manifest directly.
   */
  runFromManifest(args: {
    tenantId: TenantId;
    userId: UserId;
    manifest: SimulationManifest;
    useCache?: boolean;
  }): Promise<SimulationRunResult>;

  /**
   * Get cached keyframes for a simulation.
   */
  getCachedKeyframes(
    simulationId: SimulationId
  ): Promise<SimKeyframe[] | null>;

  /**
   * Invalidate cache for a simulation.
   */
  invalidateCache(simulationId: SimulationId): Promise<void>;

  /**
   * Get simulation manifest by ID.
   */
  getManifest(args: {
    tenantId: TenantId;
    simulationId: SimulationId;
  }): Promise<SimulationManifest | null>;

  /**
   * Save or update a simulation manifest.
   */
  saveManifest(args: {
    tenantId: TenantId;
    userId: UserId;
    manifest: SimulationManifest;
  }): Promise<SimulationManifest>;

  /**
   * List simulations for a tenant.
   */
  listSimulations(args: {
    tenantId: TenantId;
    moduleId?: ModuleId;
    domain?: SimulationDomain;
    cursor?: string;
    limit?: number;
  }): Promise<{
    items: Array<{
      id: SimulationId;
      title: string;
      domain: SimulationDomain;
      updatedAt: string;
    }>;
    nextCursor?: string;
  }>;

  /**
   * Record a playback analytics event.
   */
  recordAnalyticsEvent(event: SimulationAnalyticsEvent): Promise<void>;
}

// =============================================================================
// Simulation Renderer Service (Frontend Rendering Coordination)
// =============================================================================

/**
 * Playback state for the simulation player.
 */
export interface PlaybackState {
  simulationId: SimulationId;
  currentStep: number;
  totalSteps: number;
  isPlaying: boolean;
  speed: number;
  currentKeyframe: SimKeyframe | null;
}

/**
 * Renderer service interface for frontend coordination.
 * 
 * @doc.type interface
 * @doc.purpose Coordinate keyframe rendering and playback state
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SimulationRendererService {
  /**
   * Initialize renderer with keyframes.
   */
  initialize(
    keyframes: SimKeyframe[],
    config: {
      canvasWidth: number;
      canvasHeight: number;
      targetFps?: number;
    }
  ): void;

  /**
   * Start playback.
   */
  play(): void;

  /**
   * Pause playback.
   */
  pause(): void;

  /**
   * Seek to a specific step.
   */
  seek(stepIndex: number): void;

  /**
   * Set playback speed (0.25 to 4.0).
   */
  setSpeed(speed: number): void;

  /**
   * Get current playback state.
   */
  getState(): PlaybackState;

  /**
   * Dispose renderer and free resources.
   */
  dispose(): void;
}

// =============================================================================
// Simulation CMS Service (Content Management Integration)
// =============================================================================

/**
 * CMS integration for simulation blocks.
 * 
 * @doc.type interface
 * @doc.purpose Manage simulation blocks within CMS content
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SimulationCMSService {
  /**
   * Create a new simulation block for a module.
   */
  createSimulationBlock(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId: ModuleId;
    title: string;
    domain: SimulationDomain;
    prompt?: string;
  }): Promise<{
    blockId: string;
    simulationId: SimulationId;
    manifest?: SimulationManifest;
  }>;

  /**
   * Update simulation block manifest.
   */
  updateSimulationBlock(args: {
    tenantId: TenantId;
    userId: UserId;
    blockId: string;
    manifest: SimulationManifest;
  }): Promise<void>;

  /**
   * Publish simulation block.
   */
  publishSimulationBlock(args: {
    tenantId: TenantId;
    userId: UserId;
    blockId: string;
  }): Promise<void>;

  /**
   * Preview simulation block (generate keyframes without publishing).
   */
  previewSimulationBlock(args: {
    tenantId: TenantId;
    userId: UserId;
    blockId: string;
  }): Promise<SimulationRunResult>;

  /**
   * Get simulation block by ID.
   */
  getSimulationBlock(args: {
    tenantId: TenantId;
    blockId: string;
  }): Promise<{
    blockId: string;
    simulationId: SimulationId;
    manifest: SimulationManifest;
    published: boolean;
  } | null>;
}

// All interfaces are already exported with export interface/type declarations above
