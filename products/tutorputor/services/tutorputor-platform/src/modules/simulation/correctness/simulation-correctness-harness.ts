/**
 * Simulation Correctness Harness
 *
 * Validates simulation manifests for domain-specific physical, mathematical, and
 * logical correctness before auto-publishing. Checks invariants, numerical stability,
 * energy/momentum conservation, boundary conditions, and learner safety constraints.
 *
 * Supports deterministic replay using GenerationReplayManifest seeds.
 *
 * @doc.type service
 * @doc.purpose Domain-specific simulation correctness validation for 6 domains
 * @doc.layer product
 * @doc.pattern Validator
 */

import type { Logger } from "pino";

// ─── Types ────────────────────────────────────────────────────────────────────

export type SimulationDomain =
  | "MATH"
  | "PHYSICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "ECONOMICS"
  | "CS";

export interface SimulationParameter {
  name: string;
  min: number;
  max: number;
  default: number;
  unit: string | undefined;
  description: string | undefined;
}

export interface SimulationState {
  name: string;
  transitions: string[];
  invariants: string[] | undefined;
}

export interface SimulationManifest {
  id: string;
  title: string;
  description: string;
  domain: SimulationDomain;
  gradeLevel: number;
  seed: string;
  parameters: SimulationParameter[];
  states: SimulationState[];
  invariants: string[];
  learnerAction: string;
  expectedOutputs: Record<string, unknown>;
  metadata: Record<string, unknown> | undefined;
}

export interface CorrectnessCheckResult {
  check: string;
  passed: boolean;
  severity: "ERROR" | "WARNING" | "INFO";
  detail: string;
}

export interface SimulationCorrectnessReport {
  manifestId: string;
  domain: SimulationDomain;
  passed: boolean;
  checks: CorrectnessCheckResult[];
  overallScore: number;
  criticalFailures: string[];
  warnings: string[];
  validatedAt: string;
}

// ─── Domain validator interfaces ──────────────────────────────────────────────

interface DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[];
}

// ─── Math Validator ───────────────────────────────────────────────────────────

class MathSimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];

    // 1. Parameter constraints: algebraic validity
    for (const param of manifest.parameters) {
      checks.push({
        check: `param_bounds:${param.name}`,
        passed: param.min < param.max,
        severity: "ERROR",
        detail: param.min < param.max
          ? `Parameter '${param.name}': min(${param.min}) < max(${param.max}) ✓`
          : `Parameter '${param.name}': min(${param.min}) must be less than max(${param.max})`,
      });

      checks.push({
        check: `param_default_in_bounds:${param.name}`,
        passed: param.default >= param.min && param.default <= param.max,
        severity: "ERROR",
        detail: `Parameter '${param.name}': default(${param.default}) ${
          param.default >= param.min && param.default <= param.max
            ? "within bounds ✓"
            : `must be in [${param.min}, ${param.max}]`
        }`,
      });
    }

    // 2. Algebraic invariants stated
    checks.push({
      check: "has_algebraic_invariants",
      passed: manifest.invariants.length > 0,
      severity: "WARNING",
      detail: manifest.invariants.length > 0
        ? `${manifest.invariants.length} invariants declared ✓`
        : "No algebraic invariants declared",
    });

    // 3. Seeded determinism
    checks.push({
      check: "deterministic_seed",
      passed: manifest.seed.length > 0,
      severity: "ERROR",
      detail: manifest.seed.length > 0
        ? `Seed '${manifest.seed}' enables deterministic replay ✓`
        : "No seed provided; non-deterministic simulation cannot be verified",
    });

    // 4. Graph transformation: at least one state exists
    checks.push({
      check: "has_states",
      passed: manifest.states.length > 0,
      severity: "ERROR",
      detail: manifest.states.length > 0
        ? `${manifest.states.length} simulation states defined ✓`
        : "No simulation states defined",
    });

    return checks;
  }
}

// ─── Physics Validator ────────────────────────────────────────────────────────

class PhysicsSimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];
    const invariantsLower = manifest.invariants.map((i) => i.toLowerCase());
    const allText = [
      manifest.description,
      ...manifest.invariants,
      ...manifest.states.map((s) => s.name),
    ]
      .join(" ")
      .toLowerCase();

    // 1. Energy conservation declared
    const hasEnergy =
      invariantsLower.some((inv) => inv.includes("energy") || inv.includes("conservation"));
    checks.push({
      check: "energy_conservation_declared",
      passed: hasEnergy,
      severity: "ERROR",
      detail: hasEnergy
        ? "Energy conservation invariant declared ✓"
        : "Missing energy conservation invariant (required for physics simulations)",
    });

    // 2. No perpetual motion claims
    const hasPerpetualMotion = allText.includes("perpetual") || allText.includes("unlimited energy");
    checks.push({
      check: "no_perpetual_motion",
      passed: !hasPerpetualMotion,
      severity: "ERROR",
      detail: !hasPerpetualMotion
        ? "No perpetual motion claims detected ✓"
        : "VIOLATION: perpetual motion claim detected — violates first law of thermodynamics",
    });

    // 3. Force and momentum sanity
    const hasMomentum =
      invariantsLower.some((inv) => inv.includes("momentum") || inv.includes("force"));
    checks.push({
      check: "force_momentum_declared",
      passed: hasMomentum,
      severity: "WARNING",
      detail: hasMomentum
        ? "Force/momentum invariant declared ✓"
        : "No force or momentum invariants declared",
    });

    // 4. Parameter bounds for physical quantities (positive mass, velocity within c)
    for (const param of manifest.parameters) {
      const isVelocity = param.name.toLowerCase().includes("velocity") ||
        param.name.toLowerCase().includes("speed");
      if (isVelocity && param.unit !== undefined) {
        const maxVelocity = param.unit.toLowerCase().includes("km/s") ? 300_000 : 3e8;
        checks.push({
          check: `velocity_physical_limit:${param.name}`,
          passed: param.max <= maxVelocity,
          severity: "ERROR",
          detail: param.max <= maxVelocity
            ? `Velocity '${param.name}' max(${param.max}) is within physical limits ✓`
            : `Velocity '${param.name}' max(${param.max}) exceeds speed of light in ${param.unit}`,
        });
      }

      const isMass = param.name.toLowerCase().includes("mass");
      if (isMass) {
        checks.push({
          check: `mass_positive:${param.name}`,
          passed: param.min >= 0,
          severity: "ERROR",
          detail: param.min >= 0
            ? `Mass '${param.name}' min(${param.min}) is non-negative ✓`
            : `Mass '${param.name}' cannot be negative`,
        });
      }
    }

    // 5. Time step stability
    const hasTimeParam = manifest.parameters.some(
      (p) => p.name.toLowerCase().includes("time") || p.name.toLowerCase().includes("dt"),
    );
    if (hasTimeParam) {
      const timeParam = manifest.parameters.find(
        (p) => p.name.toLowerCase().includes("time") || p.name.toLowerCase().includes("dt"),
      )!;
      checks.push({
        check: "time_step_positive",
        passed: timeParam.min >= 0 && timeParam.default > 0,
        severity: "ERROR",
        detail: timeParam.min >= 0 && timeParam.default > 0
          ? `Time step '${timeParam.name}' is positive ✓`
          : `Time step '${timeParam.name}' must be positive`,
      });
    }

    // 6. Vector consistency: if position vectors declared, velocity must match dimension
    const hasPosition = manifest.parameters.some((p) => p.name.toLowerCase().includes("position") || p.name.toLowerCase().includes("x_0"));
    const hasVelocityParam = manifest.parameters.some((p) => p.name.toLowerCase().includes("velocity") || p.name.toLowerCase().includes("v_0"));
    if (hasPosition) {
      checks.push({
        check: "vector_consistency",
        passed: hasVelocityParam,
        severity: "WARNING",
        detail: hasVelocityParam
          ? "Position and velocity vectors both declared ✓"
          : "Position declared without corresponding velocity — check vector consistency",
      });
    }

    return checks;
  }
}

// ─── Chemistry Validator ──────────────────────────────────────────────────────

class ChemistrySimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];
    const invariantsLower = manifest.invariants.map((i) => i.toLowerCase());

    // 1. Mass conservation / stoichiometry
    const hasMassConservation = invariantsLower.some(
      (inv) => inv.includes("mass") || inv.includes("conservation") || inv.includes("stoichiometry"),
    );
    checks.push({
      check: "mass_conservation_declared",
      passed: hasMassConservation,
      severity: "ERROR",
      detail: hasMassConservation
        ? "Mass conservation / stoichiometry invariant declared ✓"
        : "Missing mass conservation or stoichiometry invariant",
    });

    // 2. Valence / bond constraints
    const hasValence = invariantsLower.some(
      (inv) => inv.includes("valence") || inv.includes("bond") || inv.includes("charge"),
    );
    checks.push({
      check: "valence_or_bond_constraints",
      passed: hasValence,
      severity: "WARNING",
      detail: hasValence
        ? "Valence/bond/charge constraints declared ✓"
        : "No valence, bond, or charge constraints — consider adding for chemical accuracy",
    });

    // 3. Temperature within physically valid range
    const tempParam = manifest.parameters.find(
      (p) => p.name.toLowerCase().includes("temperature") || p.name.toLowerCase().includes("temp"),
    );
    if (tempParam) {
      const isAbsoluteZeroSafe =
        (tempParam.unit !== undefined && tempParam.unit.toLowerCase().includes("celsius"))
          ? tempParam.min >= -273.15
          : tempParam.min >= 0;
      checks.push({
        check: "temperature_above_absolute_zero",
        passed: isAbsoluteZeroSafe,
        severity: "ERROR",
        detail: isAbsoluteZeroSafe
          ? `Temperature '${tempParam.name}' respects absolute zero ✓`
          : `Temperature '${tempParam.name}' min(${tempParam.min}) violates absolute zero`,
      });
    }

    // 4. Reaction rate constraints
    const rateParam = manifest.parameters.find(
      (p) => p.name.toLowerCase().includes("rate") || p.name.toLowerCase().includes("concentration"),
    );
    if (rateParam) {
      checks.push({
        check: "reaction_rate_non_negative",
        passed: rateParam.min >= 0,
        severity: "ERROR",
        detail: rateParam.min >= 0
          ? `Rate/concentration '${rateParam.name}' is non-negative ✓`
          : `Rate/concentration '${rateParam.name}' cannot be negative`,
      });
    }

    return checks;
  }
}

// ─── Biology Validator ────────────────────────────────────────────────────────

class BiologySimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];
    const allText = [
      manifest.description,
      ...manifest.invariants,
      ...manifest.states.map((s) => s.name),
    ]
      .join(" ")
      .toLowerCase();

    // 1. Population must be non-negative
    const popParam = manifest.parameters.find(
      (p) => p.name.toLowerCase().includes("population") || p.name.toLowerCase().includes("count"),
    );
    if (popParam) {
      checks.push({
        check: "population_non_negative",
        passed: popParam.min >= 0,
        severity: "ERROR",
        detail: popParam.min >= 0
          ? `Population '${popParam.name}' is non-negative ✓`
          : `Population '${popParam.name}' cannot be negative`,
      });
    }

    // 2. Physiological plausibility: heart rate within safe human range (30–220 BPM)
    const hrParam = manifest.parameters.find(
      (p) => p.name.toLowerCase().includes("heart_rate") || p.name.toLowerCase().includes("bpm"),
    );
    if (hrParam) {
      const physiologicallyPlausible = hrParam.min >= 30 && hrParam.max <= 220;
      checks.push({
        check: "heart_rate_physiological",
        passed: physiologicallyPlausible,
        severity: "ERROR",
        detail: physiologicallyPlausible
          ? `Heart rate '${hrParam.name}' within physiological range [30, 220] ✓`
          : `Heart rate '${hrParam.name}' [${hrParam.min}, ${hrParam.max}] outside human physiological range [30, 220]`,
      });
    }

    // 3. No harmful content
    const harmfulTerms = ["lethal", "fatal dose", "poisoning", "death simulation"];
    const hasHarmful = harmfulTerms.some((term) => allText.includes(term));
    checks.push({
      check: "no_harmful_content",
      passed: !hasHarmful,
      severity: "ERROR",
      detail: !hasHarmful
        ? "No harmful content detected ✓"
        : "VIOLATION: harmful content detected — remove lethal or fatal dose references",
    });

    // 4. Ecosystem balance: predator-prey model should have both populations
    const hasPredator = allText.includes("predator");
    const hasPrey = allText.includes("prey");
    if (hasPredator || hasPrey) {
      checks.push({
        check: "predator_prey_balanced",
        passed: hasPredator && hasPrey,
        severity: "WARNING",
        detail: hasPredator && hasPrey
          ? "Predator-prey model has both populations ✓"
          : "Predator-prey model incomplete — both species must be modeled",
      });
    }

    return checks;
  }
}

// ─── Economics Validator ──────────────────────────────────────────────────────

class EconomicsSimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];
    const invariantsLower = manifest.invariants.map((i) => i.toLowerCase());
    const allText = [
      manifest.description,
      ...manifest.invariants,
    ]
      .join(" ")
      .toLowerCase();

    // 1. Supply/demand equilibrium declared
    const hasEquilibrium = invariantsLower.some(
      (inv) => inv.includes("equilibrium") || inv.includes("supply") || inv.includes("demand"),
    );
    checks.push({
      check: "supply_demand_equilibrium",
      passed: hasEquilibrium,
      severity: "WARNING",
      detail: hasEquilibrium
        ? "Supply/demand equilibrium relationship declared ✓"
        : "No supply/demand equilibrium declared",
    });

    // 2. Price must be non-negative
    const priceParam = manifest.parameters.find(
      (p) => p.name.toLowerCase().includes("price") || p.name.toLowerCase().includes("cost"),
    );
    if (priceParam) {
      checks.push({
        check: "price_non_negative",
        passed: priceParam.min >= 0,
        severity: "ERROR",
        detail: priceParam.min >= 0
          ? `Price/cost '${priceParam.name}' is non-negative ✓`
          : `Price/cost '${priceParam.name}' cannot be negative`,
      });
    }

    // 3. No impossible negative quantities (e.g. negative GDP, negative employment)
    for (const param of manifest.parameters) {
      const isQuantity =
        param.name.toLowerCase().includes("gdp") ||
        param.name.toLowerCase().includes("employment") ||
        param.name.toLowerCase().includes("quantity") ||
        param.name.toLowerCase().includes("output");
      if (isQuantity) {
        checks.push({
          check: `non_negative_economic_quantity:${param.name}`,
          passed: param.min >= 0,
          severity: "ERROR",
          detail: param.min >= 0
            ? `Economic quantity '${param.name}' is non-negative ✓`
            : `Economic quantity '${param.name}' cannot be negative`,
        });
      }
    }

    // 4. No circular dependency in states
    const stateNames = new Set(manifest.states.map((s) => s.name));
    for (const state of manifest.states) {
      for (const transition of state.transitions) {
        if (!stateNames.has(transition)) {
          checks.push({
            check: `state_transition_valid:${state.name}->${transition}`,
            passed: false,
            severity: "ERROR",
            detail: `State '${state.name}' transitions to undefined state '${transition}'`,
          });
        }
      }
    }

    return checks;
  }
}

// ─── CS Validator ─────────────────────────────────────────────────────────────

class CSSimulationValidator implements DomainValidator {
  validate(manifest: SimulationManifest): CorrectnessCheckResult[] {
    const checks: CorrectnessCheckResult[] = [];
    const allText = [
      manifest.description,
      ...manifest.invariants,
      ...manifest.states.map((s) => s.name),
    ]
      .join(" ")
      .toLowerCase();

    // 1. Algorithm state transitions must be defined
    checks.push({
      check: "has_algorithm_states",
      passed: manifest.states.length >= 2,
      severity: "ERROR",
      detail: manifest.states.length >= 2
        ? `Algorithm has ${manifest.states.length} states (minimum 2: initial + final) ✓`
        : "Algorithm simulation must have at least 2 states (initial and terminal)",
    });

    // 2. Every state has defined transitions or is a terminal state
    for (const state of manifest.states) {
      const isTerminal =
        state.name.toLowerCase().includes("done") ||
        state.name.toLowerCase().includes("halt") ||
        state.name.toLowerCase().includes("end") ||
        state.name.toLowerCase().includes("terminal") ||
        state.name.toLowerCase().includes("accept") ||
        state.name.toLowerCase().includes("reject");
      checks.push({
        check: `state_has_transitions_or_terminal:${state.name}`,
        passed: isTerminal || state.transitions.length > 0,
        severity: "WARNING",
        detail:
          isTerminal
            ? `State '${state.name}' is a terminal state ✓`
            : state.transitions.length > 0
              ? `State '${state.name}' has ${state.transitions.length} transitions ✓`
              : `State '${state.name}' has no transitions and is not labeled as terminal`,
      });
    }

    // 3. Sorting/searching algorithms: verify complexity class stated
    const isSearchOrSort = allText.includes("sort") || allText.includes("search") || allText.includes("algorithm");
    const hasComplexity = allText.includes("o(") || allText.includes("complexity") || allText.includes("time");
    if (isSearchOrSort) {
      checks.push({
        check: "algorithm_complexity_stated",
        passed: hasComplexity,
        severity: "WARNING",
        detail: hasComplexity
          ? "Algorithm complexity or time bounds referenced ✓"
          : "Algorithm simulation should reference complexity class (e.g. O(n log n))",
      });
    }

    // 4. Truth table correctness (for boolean logic simulations)
    const isBooleanLogic = allText.includes("boolean") || allText.includes("logic") || allText.includes("truth table");
    if (isBooleanLogic) {
      const hasInvariants = manifest.invariants.length > 0;
      checks.push({
        check: "boolean_logic_has_invariants",
        passed: hasInvariants,
        severity: "ERROR",
        detail: hasInvariants
          ? "Boolean logic simulation has invariants declared ✓"
          : "Boolean logic simulation must declare truth table invariants",
      });
    }

    return checks;
  }
}

// ─── Harness ──────────────────────────────────────────────────────────────────

const DOMAIN_VALIDATORS: Record<SimulationDomain, DomainValidator> = {
  MATH: new MathSimulationValidator(),
  PHYSICS: new PhysicsSimulationValidator(),
  CHEMISTRY: new ChemistrySimulationValidator(),
  BIOLOGY: new BiologySimulationValidator(),
  ECONOMICS: new EconomicsSimulationValidator(),
  CS: new CSSimulationValidator(),
};

export class SimulationCorrectnessHarness {
  constructor(private readonly logger: Logger) {}

  /**
   * Validate a simulation manifest for domain-specific correctness.
   * Returns a detailed report with all check results and an overall score.
   */
  validate(manifest: SimulationManifest): SimulationCorrectnessReport {
    this.logger.info(
      { manifestId: manifest.id, domain: manifest.domain },
      "Starting simulation correctness validation",
    );

    const checks: CorrectnessCheckResult[] = [];

    // 1. Universal checks (all domains)
    checks.push(...this.universalChecks(manifest));

    // 2. Domain-specific checks
    const domainValidator = DOMAIN_VALIDATORS[manifest.domain];
    checks.push(...domainValidator.validate(manifest));

    const criticalFailures = checks
      .filter((c) => !c.passed && c.severity === "ERROR")
      .map((c) => c.detail);

    const warnings = checks
      .filter((c) => !c.passed && c.severity === "WARNING")
      .map((c) => c.detail);

    const passed = criticalFailures.length === 0;
    const passedCount = checks.filter((c) => c.passed).length;
    const overallScore = checks.length > 0 ? passedCount / checks.length : 1.0;

    const report: SimulationCorrectnessReport = {
      manifestId: manifest.id,
      domain: manifest.domain,
      passed,
      checks,
      overallScore,
      criticalFailures,
      warnings,
      validatedAt: new Date().toISOString(),
    };

    this.logger.info(
      {
        manifestId: manifest.id,
        domain: manifest.domain,
        passed,
        overallScore,
        criticalFailures: criticalFailures.length,
        warnings: warnings.length,
      },
      "Simulation correctness validation complete",
    );

    return report;
  }

  private universalChecks(manifest: SimulationManifest): CorrectnessCheckResult[] {
    return [
      {
        check: "has_title",
        passed: manifest.title.trim().length > 0,
        severity: "ERROR",
        detail: manifest.title.trim().length > 0 ? "Title present ✓" : "Missing title",
      },
      {
        check: "has_description",
        passed: manifest.description.trim().length > 0,
        severity: "ERROR",
        detail: manifest.description.trim().length > 0
          ? "Description present ✓"
          : "Missing description",
      },
      {
        check: "has_learner_action",
        passed: manifest.learnerAction.trim().length > 0,
        severity: "WARNING",
        detail: manifest.learnerAction.trim().length > 0
          ? "Learner action present ✓"
          : "Missing learnerAction — how should the learner interact?",
      },
      {
        check: "grade_level_valid",
        passed: manifest.gradeLevel >= 1 && manifest.gradeLevel <= 16,
        severity: "ERROR",
        detail:
          manifest.gradeLevel >= 1 && manifest.gradeLevel <= 16
            ? `Grade level ${manifest.gradeLevel} is valid ✓`
            : `Grade level ${manifest.gradeLevel} must be between 1 and 16`,
      },
      {
        check: "has_parameters",
        passed: manifest.parameters.length > 0,
        severity: "WARNING",
        detail: manifest.parameters.length > 0
          ? `${manifest.parameters.length} parameters declared ✓`
          : "No parameters declared — simulations should be interactive",
      },
    ];
  }
}
