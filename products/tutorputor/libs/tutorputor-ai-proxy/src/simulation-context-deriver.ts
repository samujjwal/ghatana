/**
 * Simulation Context Deriver for AI Tutor Integration
 *
 * Summarizes simulation state and user actions into tutor-consumable context.
 * Enables the AI tutor to understand what the learner is doing/seeing
 * in the simulation and provide contextual help.
 *
 * @doc.type module
 * @doc.purpose Derive tutor context from simulation state and actions
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  SimulationManifest,
  SimulationDomain,
  SimEntity,
  SimAction,
  SimulationStep,
  SimKeyframe,
} from "@ghatana/tutorputor-contracts/v1/simulation";

// =============================================================================
// Types
// =============================================================================

/**
 * Derived context for the AI tutor.
 */
export interface SimulationTutorContext {
  /** Summary of the simulation */
  simulationSummary: string;
  /** Current entities in the simulation */
  entities: EntitySummary[];
  /** Parameter values and their changes */
  parameters: ParameterSummary[];
  /** User actions in this session */
  userActions: ActionSummary[];
  /** Derived metrics based on domain */
  metrics: DerivedMetric[];
  /** Current step information */
  currentStep: StepSummary | null;
  /** Learning hints based on state */
  hints: string[];
  /** Domain-specific context */
  domainContext: DomainSpecificContext;
}

/**
 * Summary of a simulation entity.
 */
export interface EntitySummary {
  id: string;
  type: string;
  label: string;
  properties: Record<string, unknown>;
  role: string; // e.g., "subject", "force", "reactant", "compartment"
}

/**
 * Summary of a parameter and its changes.
 */
export interface ParameterSummary {
  name: string;
  currentValue: unknown;
  previousValue?: unknown;
  delta?: number;
  unit?: string;
  range?: { min: number; max: number };
  significance: "low" | "medium" | "high";
}

/**
 * Summary of a user action.
 */
export interface ActionSummary {
  timestamp: number;
  actionType: string;
  description: string;
  targetEntity?: string;
  outcome?: string;
}

/**
 * A derived metric from the simulation state.
 */
export interface DerivedMetric {
  name: string;
  value: number;
  unit?: string;
  interpretation: string;
  isOptimal?: boolean;
}

/**
 * Summary of the current step.
 */
export interface StepSummary {
  index: number;
  title: string;
  objective: string;
  hints: string[];
  isCheckpoint: boolean;
}

/**
 * Domain-specific context.
 */
export type DomainSpecificContext =
  | PhysicsContext
  | ChemistryContext
  | BiologyContext
  | MedicineContext
  | EconomicsContext
  | CSDiscreteContext
  | GenericContext;

export interface PhysicsContext {
  domain: "PHYSICS";
  totalEnergy?: number;
  kineticEnergy?: number;
  potentialEnergy?: number;
  momentum?: { x: number; y: number };
  forces: Array<{ name: string; magnitude: number; direction: number }>;
  conservationStatus: string;
}

export interface ChemistryContext {
  domain: "CHEMISTRY";
  reactionType?: string;
  mechanism?: string;
  bondChanges: Array<{ type: string; atoms: string[]; order: number }>;
  energyProfile?: { current: number; activation: number; deltaH: number };
  stereochemistry?: string;
}

export interface BiologyContext {
  domain: "BIOLOGY";
  scale: string;
  processes: string[];
  concentrations: Record<string, number>;
  pathwayState?: string;
  cellCyclePhase?: string;
}

export interface MedicineContext {
  domain: "MEDICINE";
  drugConcentration?: number;
  compartmentVolumes: Record<string, number>;
  auc?: number;
  halfLife?: number;
  therapeuticWindow?: { min: number; max: number; current: number };
  eliminationRate?: number;
}

export interface EconomicsContext {
  domain: "ECONOMICS";
  stockLevels: Record<string, number>;
  flowRates: Record<string, number>;
  equilibriumStatus: string;
  trends: Array<{ variable: string; direction: "up" | "down" | "stable" }>;
}

export interface CSDiscreteContext {
  domain: "CS_DISCRETE";
  algorithmPhase?: string;
  dataStructureState?: string;
  comparisons: number;
  swaps: number;
  complexity: string;
  invariants: string[];
}

export interface GenericContext {
  domain: "ENGINEERING" | "MATHEMATICS";
  summary: string;
  keyValues: Record<string, unknown>;
}

// =============================================================================
// User Action Tracking
// =============================================================================

/**
 * Tracked user action in the simulation.
 */
export interface TrackedUserAction {
  timestamp: number;
  actionType: string;
  targetEntityId?: string;
  parameters?: Record<string, unknown>;
  simulationState?: Partial<SimKeyframe>;
}

// =============================================================================
// Main Deriver Class
// =============================================================================

/**
 * Derives tutor context from simulation state and user actions.
 *
 * @doc.type class
 * @doc.purpose Convert simulation state to AI tutor context
 * @doc.layer product
 * @doc.pattern Service
 */
export class SimulationContextDeriver {
  private manifest: SimulationManifest;
  private currentKeyframe: SimKeyframe | null = null;
  private userActions: TrackedUserAction[] = [];
  private previousKeyframe: SimKeyframe | null = null;

  constructor(manifest: SimulationManifest) {
    this.manifest = manifest;
  }

  /**
   * Update the current simulation state.
   */
  updateState(keyframe: SimKeyframe): void {
    this.previousKeyframe = this.currentKeyframe;
    this.currentKeyframe = keyframe;
  }

  /**
   * Record a user action.
   */
  recordUserAction(action: TrackedUserAction): void {
    this.userActions.push(action);
    // Keep only last 50 actions
    if (this.userActions.length > 50) {
      this.userActions = this.userActions.slice(-50);
    }
  }

  /**
   * Derive the complete tutor context.
   */
  deriveContext(): SimulationTutorContext {
    const entities = this.deriveEntitySummaries();
    const parameters = this.deriveParameterSummaries();
    const userActions = this.deriveActionSummaries();
    const metrics = this.deriveMetrics();
    const currentStep = this.deriveCurrentStep();
    const domainContext = this.deriveDomainContext();
    const hints = this.deriveHints();

    return {
      simulationSummary: this.deriveSimulationSummary(),
      entities,
      parameters,
      userActions,
      metrics,
      currentStep,
      hints,
      domainContext,
    };
  }

  /**
   * Derive a text summary of the simulation.
   */
  private deriveSimulationSummary(): string {
    const { title, domain, description } = this.manifest;
    const entityCount = this.currentKeyframe?.entities.length ?? this.manifest.initialEntities.length;
    const stepIndex = this.currentKeyframe?.stepIndex ?? 0;
    const totalSteps = this.manifest.steps.length;

    let summary = `"${title}" - ${this.getDomainLabel(domain)} simulation. `;
    if (description) {
      summary += `${description.slice(0, 100)}${description.length > 100 ? "..." : ""} `;
    }
    summary += `Currently showing ${entityCount} entities at step ${stepIndex + 1} of ${totalSteps}.`;

    return summary;
  }

  /**
   * Derive entity summaries.
   */
  private deriveEntitySummaries(): EntitySummary[] {
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;

    return entities.slice(0, 20).map((entity) => ({
      id: entity.id,
      type: entity.type,
      label: entity.label ?? this.generateEntityLabel(entity),
      properties: this.extractRelevantProperties(entity),
      role: this.inferEntityRole(entity),
    }));
  }

  /**
   * Derive parameter summaries.
   */
  private deriveParameterSummaries(): ParameterSummary[] {
    const summaries: ParameterSummary[] = [];
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;
    const prevEntities = this.previousKeyframe?.entities ?? [];

    // Track key parameters based on domain
    for (const entity of entities) {
      const prevEntity = prevEntities.find((e) => e.id === entity.id);
      const params = this.extractDomainParameters(entity, prevEntity);
      summaries.push(...params);
    }

    return summaries.slice(0, 15);
  }

  /**
   * Derive action summaries.
   */
  private deriveActionSummaries(): ActionSummary[] {
    return this.userActions.slice(-10).map((action) => ({
      timestamp: action.timestamp,
      actionType: action.actionType,
      description: this.describeAction(action),
      targetEntity: action.targetEntityId,
      outcome: this.inferActionOutcome(action),
    }));
  }

  /**
   * Derive metrics based on domain.
   */
  private deriveMetrics(): DerivedMetric[] {
    switch (this.manifest.domain) {
      case "PHYSICS":
        return this.derivePhysicsMetrics();
      case "CHEMISTRY":
        return this.deriveChemistryMetrics();
      case "BIOLOGY":
        return this.deriveBiologyMetrics();
      case "MEDICINE":
        return this.deriveMedicineMetrics();
      case "ECONOMICS":
        return this.deriveEconomicsMetrics();
      case "CS_DISCRETE":
        return this.deriveCSMetrics();
      default:
        return [];
    }
  }

  /**
   * Derive current step information.
   */
  private deriveCurrentStep(): StepSummary | null {
    const stepIndex = this.currentKeyframe?.stepIndex ?? 0;
    const step = this.manifest.steps[stepIndex];

    if (!step) return null;

    return {
      index: stepIndex,
      title: step.title ?? `Step ${stepIndex + 1}`,
      objective: step.description ?? this.inferStepObjective(step),
      hints: step.tutorContext ? [step.tutorContext] : [],
      isCheckpoint: step.checkpoint ?? false,
    };
  }

  /**
   * Derive domain-specific context.
   */
  private deriveDomainContext(): DomainSpecificContext {
    switch (this.manifest.domain) {
      case "PHYSICS":
        return this.derivePhysicsContext();
      case "CHEMISTRY":
        return this.deriveChemistryContext();
      case "BIOLOGY":
        return this.deriveBiologyContext();
      case "MEDICINE":
        return this.deriveMedicineContext();
      case "ECONOMICS":
        return this.deriveEconomicsContext();
      case "CS_DISCRETE":
        return this.deriveCSContext();
      default:
        return this.deriveGenericContext();
    }
  }

  /**
   * Derive learning hints.
   */
  private deriveHints(): string[] {
    const hints: string[] = [];
    const step = this.manifest.steps[this.currentKeyframe?.stepIndex ?? 0];

    // Step-specific hints
    if (step?.tutorContext) {
      hints.push(step.tutorContext);
    }

    // Add domain-specific hints
    hints.push(...this.deriveDomainHints());

    // Add interaction hints based on user actions
    hints.push(...this.deriveInteractionHints());

    return hints.slice(0, 5);
  }

  // =============================================================================
  // Domain-Specific Derivers
  // =============================================================================

  private derivePhysicsContext(): PhysicsContext {
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;
    const bodies = entities.filter((e) => e.type === "rigidBody") as Array<{
      mass: number;
      velocityX?: number;
      velocityY?: number;
    }>;

    // Calculate energies
    let kineticEnergy = 0;
    let momentum = { x: 0, y: 0 };

    for (const body of bodies) {
      const vx = body.velocityX ?? 0;
      const vy = body.velocityY ?? 0;
      const mass = body.mass ?? 1;
      kineticEnergy += 0.5 * mass * (vx * vx + vy * vy);
      momentum.x += mass * vx;
      momentum.y += mass * vy;
    }

    return {
      domain: "PHYSICS",
      totalEnergy: kineticEnergy, // Simplified - would need potential energy too
      kineticEnergy,
      momentum,
      forces: this.extractForces(),
      conservationStatus: this.checkConservation(),
    };
  }

  private deriveChemistryContext(): ChemistryContext {
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;
    const bonds = entities.filter((e) => e.type === "bond");
    const atoms = entities.filter((e) => e.type === "atom");

    return {
      domain: "CHEMISTRY",
      reactionType: this.inferReactionType(),
      mechanism: this.inferMechanism(),
      bondChanges: this.detectBondChanges(),
      energyProfile: this.extractEnergyProfile(),
    };
  }

  private deriveBiologyContext(): BiologyContext {
    return {
      domain: "BIOLOGY",
      scale: this.inferBiologyScale(),
      processes: this.identifyBioProcesses(),
      concentrations: this.extractConcentrations(),
    };
  }

  private deriveMedicineContext(): MedicineContext {
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;
    const compartments = entities.filter((e) => e.type === "pkCompartment") as Array<{
      id: string;
      compartmentType: string;
      concentration: number;
      volume: number;
    }>;

    const volumes: Record<string, number> = {};
    let centralConc = 0;

    for (const comp of compartments) {
      volumes[comp.compartmentType] = comp.volume;
      if (comp.compartmentType === "central") {
        centralConc = comp.concentration;
      }
    }

    return {
      domain: "MEDICINE",
      drugConcentration: centralConc,
      compartmentVolumes: volumes,
      therapeuticWindow: this.extractTherapeuticWindow(),
    };
  }

  private deriveEconomicsContext(): EconomicsContext {
    const entities = this.currentKeyframe?.entities ?? this.manifest.initialEntities;
    const stocks = entities.filter((e) => e.type === "stock") as Array<{
      label?: string;
      value: number;
    }>;
    const flows = entities.filter((e) => e.type === "flow") as Array<{
      label?: string;
      rate: number;
    }>;

    const stockLevels: Record<string, number> = {};
    const flowRates: Record<string, number> = {};

    stocks.forEach((s, i) => {
      stockLevels[s.label ?? `Stock ${i + 1}`] = s.value;
    });

    flows.forEach((f, i) => {
      flowRates[f.label ?? `Flow ${i + 1}`] = f.rate;
    });

    return {
      domain: "ECONOMICS",
      stockLevels,
      flowRates,
      equilibriumStatus: this.checkEquilibrium(),
      trends: this.detectTrends(),
    };
  }

  private deriveCSContext(): CSDiscreteContext {
    return {
      domain: "CS_DISCRETE",
      algorithmPhase: this.inferAlgorithmPhase(),
      dataStructureState: this.describeDataStructure(),
      comparisons: this.countComparisons(),
      swaps: this.countSwaps(),
      complexity: this.inferComplexity(),
      invariants: this.checkInvariants(),
    };
  }

  private deriveGenericContext(): GenericContext {
    return {
      domain: this.manifest.domain as "ENGINEERING" | "MATHEMATICS",
      summary: this.manifest.description ?? "",
      keyValues: {},
    };
  }

  // =============================================================================
  // Metric Derivers
  // =============================================================================

  private derivePhysicsMetrics(): DerivedMetric[] {
    const context = this.derivePhysicsContext();
    return [
      {
        name: "Total Kinetic Energy",
        value: context.kineticEnergy ?? 0,
        unit: "J",
        interpretation: "Sum of kinetic energies of all bodies",
      },
      {
        name: "Total Momentum (X)",
        value: context.momentum?.x ?? 0,
        unit: "kg⋅m/s",
        interpretation: "Should be conserved in isolated system",
      },
    ];
  }

  private deriveChemistryMetrics(): DerivedMetric[] {
    const bondChanges = this.detectBondChanges();
    return [
      {
        name: "Bond Changes",
        value: bondChanges.length,
        interpretation: `${bondChanges.length} bond(s) formed or broken`,
      },
    ];
  }

  private deriveBiologyMetrics(): DerivedMetric[] {
    return [];
  }

  private deriveMedicineMetrics(): DerivedMetric[] {
    const context = this.deriveMedicineContext();
    const metrics: DerivedMetric[] = [];

    if (context.drugConcentration !== undefined) {
      const therapeutic = context.therapeuticWindow;
      let interpretation = "Drug concentration in central compartment";
      let isOptimal = true;

      if (therapeutic) {
        if (context.drugConcentration < therapeutic.min) {
          interpretation = "Below therapeutic range - may be ineffective";
          isOptimal = false;
        } else if (context.drugConcentration > therapeutic.max) {
          interpretation = "Above therapeutic range - potential toxicity";
          isOptimal = false;
        } else {
          interpretation = "Within therapeutic range";
        }
      }

      metrics.push({
        name: "Drug Concentration",
        value: context.drugConcentration,
        unit: "mg/L",
        interpretation,
        isOptimal,
      });
    }

    return metrics;
  }

  private deriveEconomicsMetrics(): DerivedMetric[] {
    const context = this.deriveEconomicsContext();
    return [
      {
        name: "Equilibrium Status",
        value: context.equilibriumStatus === "at_equilibrium" ? 1 : 0,
        interpretation: context.equilibriumStatus.replace(/_/g, " "),
      },
    ];
  }

  private deriveCSMetrics(): DerivedMetric[] {
    const context = this.deriveCSContext();
    return [
      {
        name: "Comparisons",
        value: context.comparisons,
        interpretation: "Number of element comparisons",
      },
      {
        name: "Swaps",
        value: context.swaps,
        interpretation: "Number of element swaps",
      },
    ];
  }

  // =============================================================================
  // Helper Methods
  // =============================================================================

  private getDomainLabel(domain: SimulationDomain): string {
    const labels: Record<SimulationDomain, string> = {
      CS_DISCRETE: "Computer Science",
      PHYSICS: "Physics",
      ECONOMICS: "Economics",
      CHEMISTRY: "Chemistry",
      BIOLOGY: "Biology",
      MEDICINE: "Medicine",
      ENGINEERING: "Engineering",
      MATHEMATICS: "Mathematics",
    };
    return labels[domain];
  }

  private generateEntityLabel(entity: SimEntity): string {
    const typeLabels: Record<string, string> = {
      rigidBody: "Body",
      spring: "Spring",
      vector: "Vector",
      atom: "Atom",
      bond: "Bond",
      molecule: "Molecule",
      stock: "Stock",
      flow: "Flow",
      node: "Node",
      edge: "Edge",
      pkCompartment: "Compartment",
      dose: "Dose",
    };
    return typeLabels[entity.type] ?? entity.type;
  }

  private extractRelevantProperties(entity: SimEntity): Record<string, unknown> {
    const props: Record<string, unknown> = {};
    const exclude = ["id", "type", "label", "x", "y", "z", "metadata"];

    for (const [key, value] of Object.entries(entity)) {
      if (!exclude.includes(key) && value !== undefined) {
        props[key] = value;
      }
    }

    return props;
  }

  private inferEntityRole(entity: SimEntity): string {
    switch (entity.type) {
      case "rigidBody":
        return "subject";
      case "spring":
        return "constraint";
      case "vector":
        return "indicator";
      case "atom": {
        const atomEntity = entity as { atomType?: string };
        return atomEntity.atomType === "nucleophile" ? "nucleophile" : "reactant";
      }
      case "pkCompartment":
        return (entity as { compartmentType?: string }).compartmentType ?? "compartment";
      default:
        return "element";
    }
  }

  private extractDomainParameters(
    entity: SimEntity,
    prevEntity?: SimEntity
  ): ParameterSummary[] {
    const params: ParameterSummary[] = [];

    // Physics parameters
    if (entity.type === "rigidBody") {
      const body = entity as { mass: number; velocityX?: number; velocityY?: number };
      const prevBody = prevEntity as { velocityX?: number; velocityY?: number } | undefined;

      params.push({
        name: "Mass",
        currentValue: body.mass,
        unit: "kg",
        significance: "medium",
      });

      if (body.velocityX !== undefined) {
        params.push({
          name: "Velocity X",
          currentValue: body.velocityX,
          previousValue: prevBody?.velocityX,
          delta: prevBody?.velocityX !== undefined ? body.velocityX - prevBody.velocityX : undefined,
          unit: "m/s",
          significance: "high",
        });
      }
    }

    // Add more domain-specific parameters as needed

    return params;
  }

  private describeAction(action: TrackedUserAction): string {
    const descriptions: Record<string, string> = {
      APPLY_FORCE: "Applied force to object",
      SET_GRAVITY: "Changed gravity settings",
      CREATE_BOND: "Created chemical bond",
      BREAK_BOND: "Broke chemical bond",
      ABSORB: "Administered dose",
      SWAP: "Swapped elements",
      COMPARE: "Compared elements",
    };
    return descriptions[action.actionType] ?? `Performed ${action.actionType}`;
  }

  private inferActionOutcome(action: TrackedUserAction): string | undefined {
    // Would need more sophisticated logic in production
    return undefined;
  }

  private deriveDomainHints(): string[] {
    switch (this.manifest.domain) {
      case "PHYSICS":
        return ["Watch the energy conservation as forces are applied."];
      case "CHEMISTRY":
        return ["Observe the electron movement during the reaction."];
      case "MEDICINE":
        return ["Monitor the drug concentration relative to the therapeutic window."];
      case "ECONOMICS":
        return ["Track how changes in flows affect stock levels over time."];
      case "CS_DISCRETE":
        return ["Note the number of comparisons required at each step."];
      default:
        return [];
    }
  }

  private deriveInteractionHints(): string[] {
    const hints: string[] = [];
    const recentActions = this.userActions.slice(-5);

    if (recentActions.length === 0) {
      hints.push("Try interacting with the simulation to see how it responds.");
    }

    return hints;
  }

  // Placeholder implementations for complex derivations
  private extractForces(): Array<{ name: string; magnitude: number; direction: number }> {
    return [];
  }

  private checkConservation(): string {
    return "checking";
  }

  private inferReactionType(): string | undefined {
    return undefined;
  }

  private inferMechanism(): string | undefined {
    return undefined;
  }

  private detectBondChanges(): Array<{ type: string; atoms: string[]; order: number }> {
    return [];
  }

  private extractEnergyProfile(): { current: number; activation: number; deltaH: number } | undefined {
    return undefined;
  }

  private inferBiologyScale(): string {
    return "cellular";
  }

  private identifyBioProcesses(): string[] {
    return [];
  }

  private extractConcentrations(): Record<string, number> {
    return {};
  }

  private extractTherapeuticWindow(): { min: number; max: number; current: number } | undefined {
    return undefined;
  }

  private checkEquilibrium(): string {
    return "checking";
  }

  private detectTrends(): Array<{ variable: string; direction: "up" | "down" | "stable" }> {
    return [];
  }

  private inferAlgorithmPhase(): string | undefined {
    return undefined;
  }

  private describeDataStructure(): string | undefined {
    return undefined;
  }

  private countComparisons(): number {
    return this.userActions.filter((a) => a.actionType === "COMPARE").length;
  }

  private countSwaps(): number {
    return this.userActions.filter((a) => a.actionType === "SWAP").length;
  }

  private inferComplexity(): string {
    return "O(n²)"; // Placeholder
  }

  private checkInvariants(): string[] {
    return [];
  }

  private inferStepObjective(step: SimulationStep): string {
    if (step.actions.length === 0) {
      return "Observe the current state";
    }
    const actionTypes = [...new Set(step.actions.map((a) => a.action))];
    return `Perform: ${actionTypes.join(", ")}`;
  }
}

/**
 * Create a context deriver for a simulation manifest.
 */
export function createSimulationContextDeriver(
  manifest: SimulationManifest
): SimulationContextDeriver {
  return new SimulationContextDeriver(manifest);
}
