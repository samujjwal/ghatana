/**
 * Simulation Correctness Harness Tests
 *
 * Verifies domain-specific correctness checks for all 6 domains:
 * MATH, PHYSICS, CHEMISTRY, BIOLOGY, ECONOMICS, CS
 *
 * @doc.type test
 * @doc.purpose Prove simulation correctness validation logic for all domains
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, expect, it, vi } from "vitest";
import {
  SimulationCorrectnessHarness,
  type SimulationManifest,
  type SimulationDomain,
} from "../simulation-correctness-harness.js";

// ─── Helpers ──────────────────────────────────────────────────────────────────

const logger = {
  info: vi.fn(),
  warn: vi.fn(),
  error: vi.fn(),
  debug: vi.fn(),
  fatal: vi.fn(),
  trace: vi.fn(),
  child: vi.fn().mockReturnThis(),
};

const harness = new SimulationCorrectnessHarness(logger as never);

function makeManifest(
  domain: SimulationDomain,
  overrides: Partial<SimulationManifest> = {},
): SimulationManifest {
  return {
    id: `sim-${domain.toLowerCase()}-test`,
    title: `${domain} Simulation Test`,
    description: `A simulation testing ${domain} concepts.`,
    domain,
    gradeLevel: 10,
    seed: "test-seed-42",
    parameters: [
      { name: "x", min: 0, max: 10, default: 5, unit: undefined, description: undefined },
    ],
    states: [
      { name: "initial", transitions: ["running"], invariants: undefined },
      { name: "running", transitions: ["done"], invariants: undefined },
      { name: "done", transitions: [], invariants: undefined },
    ],
    invariants: ["result >= 0"],
    learnerAction: "Adjust parameter x and observe the output.",
    expectedOutputs: {},
    metadata: undefined,
    ...overrides,
  };
}

// ─── Universal checks ─────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - Universal Checks", () => {
  it("passes a valid well-formed manifest", () => {
    const report = harness.validate(makeManifest("MATH"));
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when title is empty", () => {
    const report = harness.validate(makeManifest("MATH", { title: "" }));
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("title"))).toBe(true);
    expect(report.passed).toBe(false);
  });

  it("fails when description is empty", () => {
    const report = harness.validate(makeManifest("MATH", { description: "" }));
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("description"))).toBe(true);
    expect(report.passed).toBe(false);
  });

  it("fails when grade level is out of range (0)", () => {
    const report = harness.validate(makeManifest("MATH", { gradeLevel: 0 }));
    expect(report.criticalFailures.some((f) => f.includes("Grade level"))).toBe(true);
    expect(report.passed).toBe(false);
  });

  it("warns when no learner action defined", () => {
    const report = harness.validate(makeManifest("MATH", { learnerAction: "" }));
    expect(report.warnings.some((w) => w.toLowerCase().includes("learneraction"))).toBe(true);
  });

  it("overall score is between 0 and 1", () => {
    const report = harness.validate(makeManifest("MATH"));
    expect(report.overallScore).toBeGreaterThanOrEqual(0);
    expect(report.overallScore).toBeLessThanOrEqual(1);
  });
});

// ─── MATH domain ─────────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - MATH", () => {
  it("passes a valid quadratic simulation", () => {
    const manifest = makeManifest("MATH", {
      title: "Quadratic Function Explorer",
      description: "Explore how changing a, b, c affect the roots of ax² + bx + c = 0.",
      parameters: [
        { name: "a", min: -10, max: 10, default: 1, unit: undefined, description: "Leading coefficient" },
        { name: "b", min: -10, max: 10, default: 0, unit: undefined, description: "Linear coefficient" },
        { name: "c", min: -10, max: 10, default: -1, unit: undefined, description: "Constant term" },
      ],
      invariants: ["discriminant = b^2 - 4ac determines number of real roots"],
    });
    const report = harness.validate(manifest);
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when parameter min >= max", () => {
    const manifest = makeManifest("MATH", {
      parameters: [
        { name: "x", min: 10, max: 5, default: 7, unit: undefined, description: undefined },
      ],
    });
    const report = harness.validate(manifest);
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.includes("x") && f.includes("min"))).toBe(true);
  });

  it("fails when default is out of parameter bounds", () => {
    const manifest = makeManifest("MATH", {
      parameters: [
        { name: "coefficient", min: 0, max: 10, default: 15, unit: undefined, description: undefined },
      ],
    });
    const report = harness.validate(manifest);
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.includes("default(15)"))).toBe(true);
  });

  it("warns when no algebraic invariants declared", () => {
    const manifest = makeManifest("MATH", { invariants: [] });
    const report = harness.validate(manifest);
    expect(report.warnings.some((w) => w.toLowerCase().includes("invariant"))).toBe(true);
  });
});

// ─── PHYSICS domain ───────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - PHYSICS", () => {
  function makePhysicsManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
    return makeManifest("PHYSICS", {
      title: "Projectile Motion Simulator",
      description: "Simulate a projectile launched at angle θ with initial velocity v₀.",
      parameters: [
        { name: "velocity_v0", min: 0, max: 100, default: 20, unit: "m/s", description: "Initial velocity" },
        { name: "angle", min: 0, max: 90, default: 45, unit: "degrees", description: "Launch angle" },
        { name: "mass_m", min: 0, max: 100, default: 1, unit: "kg", description: "Projectile mass" },
      ],
      invariants: [
        "energy_conservation: KE + PE = constant at each time step",
        "momentum at peak: horizontal momentum = m * v0 * cos(θ)",
      ],
      ...overrides,
    });
  }

  it("passes a valid physics simulation with energy conservation declared", () => {
    const report = harness.validate(makePhysicsManifest());
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when energy conservation invariant is absent", () => {
    const report = harness.validate(
      makePhysicsManifest({ invariants: ["angle must be between 0 and 90 degrees"] }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("energy"))).toBe(true);
  });

  it("fails when perpetual motion claim is present in description", () => {
    const report = harness.validate(
      makePhysicsManifest({
        description: "This perpetual motion machine generates unlimited energy using magnetic bearings.",
        invariants: ["energy conservation holds"],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.includes("perpetual motion"))).toBe(true);
  });

  it("fails when mass is negative", () => {
    const report = harness.validate(
      makePhysicsManifest({
        parameters: [
          { name: "mass_m", min: -5, max: 100, default: 1, unit: "kg", description: "Mass" },
          { name: "velocity_v0", min: 0, max: 100, default: 20, unit: "m/s", description: "Velocity" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("mass"))).toBe(true);
  });

  it("warns when force/momentum invariants absent", () => {
    const report = harness.validate(
      makePhysicsManifest({ invariants: ["energy conservation holds"] }),
    );
    expect(report.warnings.some((w) => w.toLowerCase().includes("momentum") || w.toLowerCase().includes("force"))).toBe(true);
  });
});

// ─── CHEMISTRY domain ─────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - CHEMISTRY", () => {
  function makeChemManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
    return makeManifest("CHEMISTRY", {
      title: "Acid-Base Titration",
      description: "Simulate the neutralisation of HCl with NaOH.",
      parameters: [
        { name: "concentration_acid", min: 0, max: 1, default: 0.1, unit: "mol/L", description: "Acid concentration" },
        { name: "volume_base", min: 0, max: 100, default: 10, unit: "mL", description: "Volume of base added" },
        { name: "temperature", min: 0, max: 100, default: 25, unit: "Celsius", description: "Reaction temperature" },
      ],
      invariants: [
        "mass_conservation: moles of H⁺ = moles of OH⁻ at equivalence point",
        "charge_balance: sum of positive charges = sum of negative charges",
        "valence rules apply: H has valence 1, O has valence 2",
      ],
      ...overrides,
    });
  }

  it("passes a valid chemistry simulation", () => {
    const report = harness.validate(makeChemManifest());
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when mass conservation not declared", () => {
    const report = harness.validate(makeChemManifest({ invariants: ["valence rules apply"] }));
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("mass conservation"))).toBe(true);
  });

  it("fails when temperature violates absolute zero in Celsius", () => {
    const report = harness.validate(
      makeChemManifest({
        parameters: [
          { name: "temperature", min: -300, max: 100, default: 25, unit: "Celsius", description: "Temperature" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("absolute zero"))).toBe(true);
  });

  it("fails when reaction rate (concentration) is negative", () => {
    const report = harness.validate(
      makeChemManifest({
        parameters: [
          { name: "concentration_acid", min: -1, max: 1, default: 0.1, unit: "mol/L", description: "Concentration" },
          { name: "temperature", min: 0, max: 100, default: 25, unit: "Celsius", description: "Temperature" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("negative"))).toBe(true);
  });
});

// ─── BIOLOGY domain ───────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - BIOLOGY", () => {
  function makeBioManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
    return makeManifest("BIOLOGY", {
      title: "Predator-Prey Population Dynamics",
      description: "Simulate Lotka-Volterra predator-prey dynamics between foxes and rabbits.",
      parameters: [
        { name: "population_rabbit", min: 0, max: 1000, default: 100, unit: "count", description: "Rabbit population" },
        { name: "population_fox", min: 0, max: 200, default: 20, unit: "count", description: "Fox population" },
        { name: "growth_rate", min: 0, max: 2, default: 0.5, unit: "per year", description: "Rabbit growth rate" },
      ],
      invariants: ["populations are non-negative", "total biomass is conserved over time"],
      description: "Simulate predator-prey relationships. Predator population rises as prey increases.",
      ...overrides,
    });
  }

  it("passes a valid biology simulation", () => {
    const report = harness.validate(makeBioManifest());
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when population minimum is negative", () => {
    const report = harness.validate(
      makeBioManifest({
        parameters: [
          { name: "population_rabbit", min: -10, max: 1000, default: 100, unit: "count", description: "Rabbit population" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("population"))).toBe(true);
  });

  it("fails when harmful content is detected", () => {
    const report = harness.validate(
      makeBioManifest({
        description: "Simulate fatal dose poisoning response in humans.",
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("harmful"))).toBe(true);
  });

  it("fails when heart rate is outside physiological range", () => {
    const report = harness.validate(
      makeBioManifest({
        parameters: [
          { name: "heart_rate_bpm", min: 0, max: 500, default: 72, unit: "BPM", description: "Heart rate" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.includes("heart rate") || f.toLowerCase().includes("physiological"))).toBe(true);
  });
});

// ─── ECONOMICS domain ─────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - ECONOMICS", () => {
  function makeEconManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
    return makeManifest("ECONOMICS", {
      title: "Supply and Demand Market Simulator",
      description: "Simulate how price and quantity reach equilibrium in a competitive market.",
      parameters: [
        { name: "price", min: 0, max: 1000, default: 10, unit: "USD", description: "Market price" },
        { name: "quantity_supplied", min: 0, max: 10000, default: 100, unit: "units", description: "Quantity supplied" },
        { name: "quantity_demanded", min: 0, max: 10000, default: 100, unit: "units", description: "Quantity demanded" },
      ],
      invariants: [
        "equilibrium: at market clearing price, supply equals demand",
        "law of supply: higher price → more quantity supplied",
        "law of demand: higher price → less quantity demanded",
      ],
      states: [
        { name: "surplus", transitions: ["equilibrium"], invariants: undefined },
        { name: "equilibrium", transitions: ["surplus", "shortage"], invariants: undefined },
        { name: "shortage", transitions: ["equilibrium"], invariants: undefined },
      ],
      ...overrides,
    });
  }

  it("passes a valid economics simulation", () => {
    const report = harness.validate(makeEconManifest());
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when price is negative", () => {
    const report = harness.validate(
      makeEconManifest({
        parameters: [
          { name: "price", min: -100, max: 1000, default: 10, unit: "USD", description: "Price" },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("price"))).toBe(true);
  });

  it("fails when state transitions reference undefined state", () => {
    const report = harness.validate(
      makeEconManifest({
        states: [
          { name: "start", transitions: ["nonexistent_state"], invariants: undefined },
          { name: "end", transitions: [], invariants: undefined },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.includes("undefined state"))).toBe(true);
  });

  it("warns when no supply/demand equilibrium declared", () => {
    const report = harness.validate(makeEconManifest({ invariants: ["prices are positive"] }));
    expect(report.warnings.some((w) => w.toLowerCase().includes("equilibrium") || w.toLowerCase().includes("supply"))).toBe(true);
  });
});

// ─── CS domain ────────────────────────────────────────────────────────────────

describe("SimulationCorrectnessHarness - CS", () => {
  function makeCSManifest(overrides: Partial<SimulationManifest> = {}): SimulationManifest {
    return makeManifest("CS", {
      title: "Binary Search Algorithm Visualizer",
      description:
        "Visualize binary search algorithm on a sorted array. O(log n) complexity.",
      parameters: [
        { name: "array_size", min: 1, max: 100, default: 16, unit: undefined, description: "Size of sorted array" },
        { name: "target", min: 0, max: 99, default: 42, unit: undefined, description: "Target value to find" },
      ],
      invariants: [
        "array must be sorted before search begins",
        "search space halves at each step: O(log n) complexity",
        "target is found iff present in array",
      ],
      states: [
        { name: "init", transitions: ["searching"], invariants: undefined },
        { name: "searching", transitions: ["found", "not_found"], invariants: undefined },
        { name: "found", transitions: [], invariants: undefined },
        { name: "not_found", transitions: [], invariants: undefined },
      ],
      ...overrides,
    });
  }

  it("passes a valid CS simulation", () => {
    const report = harness.validate(makeCSManifest());
    expect(report.criticalFailures).toHaveLength(0);
    expect(report.passed).toBe(true);
  });

  it("fails when fewer than 2 states defined", () => {
    const report = harness.validate(
      makeCSManifest({
        states: [
          { name: "init", transitions: [], invariants: undefined },
        ],
      }),
    );
    expect(report.passed).toBe(false);
    expect(report.criticalFailures.some((f) => f.toLowerCase().includes("states"))).toBe(true);
  });

  it("warns when algorithm complexity is not stated", () => {
    const report = harness.validate(
      makeCSManifest({
        description: "Sort an array using bubble sort algorithm.",
        invariants: ["array is sorted at completion"],
      }),
    );
    expect(report.warnings.some((w) => w.toLowerCase().includes("complexity"))).toBe(true);
  });

  it("warns when a non-terminal state has no transitions", () => {
    const report = harness.validate(
      makeCSManifest({
        states: [
          { name: "init", transitions: ["processing"], invariants: undefined },
          { name: "processing", transitions: [], invariants: undefined }, // not terminal name
          { name: "done", transitions: [], invariants: undefined },
        ],
      }),
    );
    expect(report.warnings.some((w) => w.includes("processing") && w.includes("terminal"))).toBe(true);
  });
});

// ─── Cross-domain score behavior ──────────────────────────────────────────────

describe("SimulationCorrectnessHarness - Score Behavior", () => {
  it("a fully correct simulation scores higher than one with missing invariants", () => {
    const good = harness.validate(makeManifest("MATH", {
      invariants: ["output >= 0", "output is bounded by max(x)"],
    }));
    const poor = harness.validate(makeManifest("MATH", { invariants: [] }));
    expect(good.overallScore).toBeGreaterThanOrEqual(poor.overallScore);
  });

  it("validatedAt timestamp is an ISO string", () => {
    const report = harness.validate(makeManifest("CS"));
    expect(() => new Date(report.validatedAt)).not.toThrow();
    expect(new Date(report.validatedAt).toISOString()).toBe(report.validatedAt);
  });

  it("all 6 domains can be validated without throwing", () => {
    const domains: SimulationDomain[] = ["MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY", "ECONOMICS", "CS"];
    for (const domain of domains) {
      const report = harness.validate(makeManifest(domain, {
        invariants: domain === "PHYSICS"
          ? ["energy conservation holds", "momentum conservation holds"]
          : ["result is bounded"],
      }));
      expect(report.manifestId).toBeTruthy();
      expect(typeof report.overallScore).toBe("number");
    }
  });
});
