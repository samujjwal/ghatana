/**
 * Simulation Context Deriver Unit Tests
 *
 * Tests for the simulation context derivation logic that enables
 * AI tutor integration with simulations.
 *
 * @doc.type test
 * @doc.purpose Unit tests for simulation context derivation
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, beforeEach } from "vitest";
import {
  deriveSimulationContext,
  summarizeEntities,
  summarizeParameters,
  summarizeUserActions,
  deriveMetrics,
  getDomainSpecificContext,
  type SimulationTutorContext,
  type EntitySummary,
  type ParameterSummary,
  type DerivedMetric,
} from "../simulation-context-deriver";

// =============================================================================
// Mock Data
// =============================================================================

const createMockPhysicsManifest = () => ({
  id: "physics-sim-1",
  title: "Projectile Motion",
  description: "Simulate projectile motion with varying initial conditions",
  domain: "PHYSICS" as const,
  version: "1.0.0",
  initialEntities: [
    {
      id: "projectile-1",
      type: "particle",
      label: "Ball",
      properties: { mass: 1.0, radius: 0.1 },
      position: { x: 0, y: 0, z: 0 },
    },
    {
      id: "ground",
      type: "surface",
      label: "Ground",
      properties: { friction: 0.1 },
    },
  ],
  steps: [
    {
      id: "step-1",
      title: "Launch",
      duration: 1000,
      actions: [
        { action: "APPLY_FORCE", entityId: "projectile-1", force: { x: 10, y: 15, z: 0 } },
      ],
    },
  ],
  parameters: [
    { name: "initialVelocity", value: 20, unit: "m/s", range: { min: 0, max: 100 } },
    { name: "angle", value: 45, unit: "degrees", range: { min: 0, max: 90 } },
    { name: "gravity", value: 9.8, unit: "m/s²", range: { min: 0, max: 20 } },
  ],
});

const createMockCSManifest = () => ({
  id: "cs-sim-1",
  title: "Bubble Sort",
  description: "Visualize bubble sort algorithm",
  domain: "CS_DISCRETE" as const,
  version: "1.0.0",
  initialEntities: [
    { id: "array", type: "array", label: "Numbers", data: [5, 3, 8, 1, 9, 2] },
  ],
  steps: [
    {
      id: "step-1",
      title: "Compare and Swap",
      duration: 500,
      actions: [
        { action: "COMPARE", i: 0, j: 1 },
        { action: "SWAP", i: 0, j: 1 },
      ],
    },
  ],
});

const createMockChemistryManifest = () => ({
  id: "chem-sim-1",
  title: "Acid-Base Reaction",
  description: "Simulate acid-base neutralization",
  domain: "CHEMISTRY" as const,
  version: "1.0.0",
  initialEntities: [
    { id: "hcl", type: "molecule", label: "HCl", properties: { concentration: 0.1, volume: 100 } },
    { id: "naoh", type: "molecule", label: "NaOH", properties: { concentration: 0.1, volume: 0 } },
  ],
  parameters: [
    { name: "temperature", value: 25, unit: "°C" },
    { name: "pH", value: 1.0, unit: "" },
  ],
});

const createMockEconomicsManifest = () => ({
  id: "econ-sim-1",
  title: "Supply and Demand",
  description: "Explore market equilibrium",
  domain: "ECONOMICS" as const,
  version: "1.0.0",
  initialEntities: [
    { id: "supply", type: "curve", label: "Supply Curve" },
    { id: "demand", type: "curve", label: "Demand Curve" },
  ],
  parameters: [
    { name: "supplyElasticity", value: 1.5, unit: "" },
    { name: "demandElasticity", value: -1.2, unit: "" },
    { name: "equilibriumPrice", value: 50, unit: "$" },
  ],
});

const createMockBioMedManifest = () => ({
  id: "biomed-sim-1",
  title: "Drug Pharmacokinetics",
  description: "Model drug absorption and elimination",
  domain: "MEDICINE" as const,
  version: "1.0.0",
  initialEntities: [
    { id: "plasma", type: "compartment", label: "Plasma", properties: { volume: 5 } },
    { id: "tissue", type: "compartment", label: "Tissue", properties: { volume: 35 } },
    { id: "drug", type: "substance", label: "Drug", properties: { dose: 100, halfLife: 4 } },
  ],
  parameters: [
    { name: "dose", value: 100, unit: "mg" },
    { name: "absorptionRate", value: 0.5, unit: "h⁻¹" },
    { name: "eliminationRate", value: 0.1, unit: "h⁻¹" },
  ],
});

// =============================================================================
// Test Suites
// =============================================================================

describe("SimulationContextDeriver", () => {
  describe("deriveSimulationContext", () => {
    it("should derive context from physics simulation", async () => {
      const manifest = createMockPhysicsManifest();
      const currentKeyframe = {
        stepIndex: 0,
        entities: manifest.initialEntities,
        metrics: { kineticEnergy: 200, height: 5 },
      };
      const userActions = [
        { type: "CHANGE_PARAMETER", parameter: "angle", from: 45, to: 60 },
      ];

      const context = await deriveSimulationContext(
        manifest,
        currentKeyframe,
        userActions
      );

      expect(context).toBeDefined();
      expect(context.simulationSummary).toContain("Projectile Motion");
      expect(context.entities).toHaveLength(2);
      expect(context.domainContext.domain).toBe("PHYSICS");
    });

    it("should derive context from CS simulation", async () => {
      const manifest = createMockCSManifest();
      const currentKeyframe = {
        stepIndex: 0,
        entities: manifest.initialEntities,
        metrics: { comparisons: 3, swaps: 2 },
      };

      const context = await deriveSimulationContext(manifest, currentKeyframe, []);

      expect(context).toBeDefined();
      expect(context.simulationSummary).toContain("Bubble Sort");
      expect(context.domainContext.domain).toBe("CS_DISCRETE");
    });

    it("should include current step information", async () => {
      const manifest = createMockPhysicsManifest();
      const currentKeyframe = {
        stepIndex: 0,
        entities: manifest.initialEntities,
        metrics: {},
      };

      const context = await deriveSimulationContext(manifest, currentKeyframe, []);

      expect(context.currentStep).toBeDefined();
      expect(context.currentStep?.index).toBe(0);
      expect(context.currentStep?.title).toBe("Launch");
    });
  });

  describe("summarizeEntities", () => {
    it("should summarize physics entities correctly", () => {
      const manifest = createMockPhysicsManifest();
      const summaries = summarizeEntities(
        manifest.initialEntities,
        "PHYSICS"
      );

      expect(summaries).toHaveLength(2);

      const projectile = summaries.find((e) => e.id === "projectile-1");
      expect(projectile).toBeDefined();
      expect(projectile?.type).toBe("particle");
      expect(projectile?.role).toBe("subject");
    });

    it("should summarize CS entities correctly", () => {
      const manifest = createMockCSManifest();
      const summaries = summarizeEntities(
        manifest.initialEntities,
        "CS_DISCRETE"
      );

      expect(summaries).toHaveLength(1);
      expect(summaries[0].type).toBe("array");
    });

    it("should summarize chemistry entities correctly", () => {
      const manifest = createMockChemistryManifest();
      const summaries = summarizeEntities(
        manifest.initialEntities,
        "CHEMISTRY"
      );

      expect(summaries).toHaveLength(2);
      const hcl = summaries.find((e) => e.id === "hcl");
      expect(hcl?.role).toBe("reactant");
    });

    it("should summarize biomed entities correctly", () => {
      const manifest = createMockBioMedManifest();
      const summaries = summarizeEntities(
        manifest.initialEntities,
        "MEDICINE"
      );

      expect(summaries).toHaveLength(3);
      const plasma = summaries.find((e) => e.id === "plasma");
      expect(plasma?.role).toBe("compartment");
    });
  });

  describe("summarizeParameters", () => {
    it("should summarize parameters with significance", () => {
      const manifest = createMockPhysicsManifest();
      const currentValues = { initialVelocity: 25, angle: 60, gravity: 9.8 };
      const previousValues = { initialVelocity: 20, angle: 45, gravity: 9.8 };

      const summaries = summarizeParameters(
        manifest.parameters ?? [],
        currentValues,
        previousValues
      );

      expect(summaries).toHaveLength(3);

      const angleSummary = summaries.find((p) => p.name === "angle");
      expect(angleSummary?.currentValue).toBe(60);
      expect(angleSummary?.previousValue).toBe(45);
      expect(angleSummary?.delta).toBe(15);
      expect(angleSummary?.significance).toBe("high"); // Large change
    });

    it("should mark unchanged parameters as low significance", () => {
      const manifest = createMockPhysicsManifest();
      const currentValues = { gravity: 9.8 };
      const previousValues = { gravity: 9.8 };

      const summaries = summarizeParameters(
        manifest.parameters ?? [],
        currentValues,
        previousValues
      );

      const gravitySummary = summaries.find((p) => p.name === "gravity");
      expect(gravitySummary?.significance).toBe("low");
    });
  });

  describe("summarizeUserActions", () => {
    it("should create action summaries with descriptions", () => {
      const rawActions = [
        { type: "CHANGE_PARAMETER", parameter: "angle", from: 45, to: 60, timestamp: 1000 },
        { type: "PLAY", timestamp: 2000 },
        { type: "PAUSE", timestamp: 3000 },
        { type: "RESTART", timestamp: 4000 },
      ];

      const summaries = summarizeUserActions(rawActions);

      expect(summaries).toHaveLength(4);
      expect(summaries[0].description).toContain("angle");
      expect(summaries[0].description).toContain("45");
      expect(summaries[0].description).toContain("60");
    });

    it("should handle empty actions", () => {
      const summaries = summarizeUserActions([]);
      expect(summaries).toHaveLength(0);
    });
  });

  describe("deriveMetrics", () => {
    it("should derive physics metrics correctly", () => {
      const keyframe = {
        metrics: { kineticEnergy: 200, potentialEnergy: 50, height: 5, velocity: 20 },
      };

      const metrics = deriveMetrics(keyframe, "PHYSICS");

      expect(metrics.length).toBeGreaterThan(0);

      const energyMetric = metrics.find((m) => m.name.includes("Energy"));
      expect(energyMetric).toBeDefined();
    });

    it("should derive CS metrics correctly", () => {
      const keyframe = {
        metrics: { comparisons: 15, swaps: 8, inversions: 3 },
      };

      const metrics = deriveMetrics(keyframe, "CS_DISCRETE");

      const comparisonMetric = metrics.find((m) => m.name.includes("Comparison"));
      expect(comparisonMetric).toBeDefined();
      expect(comparisonMetric?.value).toBe(15);
    });

    it("should derive economics metrics correctly", () => {
      const keyframe = {
        metrics: { consumerSurplus: 1000, producerSurplus: 800, totalSurplus: 1800 },
      };

      const metrics = deriveMetrics(keyframe, "ECONOMICS");

      const surplusMetric = metrics.find((m) => m.name.includes("Surplus"));
      expect(surplusMetric).toBeDefined();
    });

    it("should derive biomed metrics correctly", () => {
      const keyframe = {
        metrics: { plasmaConcentration: 5.2, AUC: 120, Cmax: 8.5, Tmax: 2 },
      };

      const metrics = deriveMetrics(keyframe, "MEDICINE");

      const aucMetric = metrics.find((m) => m.name === "AUC");
      expect(aucMetric).toBeDefined();
      expect(aucMetric?.value).toBe(120);
    });
  });

  describe("getDomainSpecificContext", () => {
    it("should provide physics-specific context", () => {
      const manifest = createMockPhysicsManifest();
      const context = getDomainSpecificContext(manifest, "PHYSICS");

      expect(context.domain).toBe("PHYSICS");
      expect(context.concepts).toContain("motion");
      expect(context.vocabulary).toBeDefined();
    });

    it("should provide CS-specific context", () => {
      const manifest = createMockCSManifest();
      const context = getDomainSpecificContext(manifest, "CS_DISCRETE");

      expect(context.domain).toBe("CS_DISCRETE");
      expect(context.concepts).toContain("algorithm");
    });

    it("should provide chemistry-specific context", () => {
      const manifest = createMockChemistryManifest();
      const context = getDomainSpecificContext(manifest, "CHEMISTRY");

      expect(context.domain).toBe("CHEMISTRY");
      expect(context.concepts).toContain("reaction");
    });

    it("should provide economics-specific context", () => {
      const manifest = createMockEconomicsManifest();
      const context = getDomainSpecificContext(manifest, "ECONOMICS");

      expect(context.domain).toBe("ECONOMICS");
      expect(context.concepts).toContain("equilibrium");
    });

    it("should provide biomed-specific context", () => {
      const manifest = createMockBioMedManifest();
      const context = getDomainSpecificContext(manifest, "MEDICINE");

      expect(context.domain).toBe("MEDICINE");
      expect(context.concepts).toContain("pharmacokinetics");
    });
  });

  describe("Edge Cases", () => {
    it("should handle missing parameters gracefully", async () => {
      const manifest = {
        ...createMockPhysicsManifest(),
        parameters: undefined,
      };

      const context = await deriveSimulationContext(manifest, { stepIndex: 0, entities: [], metrics: {} }, []);

      expect(context.parameters).toEqual([]);
    });

    it("should handle empty entities", async () => {
      const manifest = {
        ...createMockPhysicsManifest(),
        initialEntities: [],
      };

      const context = await deriveSimulationContext(manifest, { stepIndex: 0, entities: [], metrics: {} }, []);

      expect(context.entities).toEqual([]);
    });

    it("should handle unknown domain", () => {
      const context = getDomainSpecificContext({} as never, "UNKNOWN" as never);

      expect(context.domain).toBe("UNKNOWN");
      expect(context.concepts).toEqual([]);
    });
  });
});
