/**
 * Simulation Determinism Tests
 *
 * Validates that simulations produce deterministic outputs given the same seed.
 * This ensures replayability and auditability of simulation results.
 *
 * @doc.type test
 * @doc.purpose Validate simulation determinism and replayability
 * @doc.layer product
 * @doc.pattern GoldenMaster
 */

import { describe, it, expect } from "vitest";
import {
  SimulationCorrectnessHarness,
  type SimulationManifest,
  SimulationDomain,
} from "../simulation-correctness-harness.js";

describe("Simulation Determinism Tests", () => {
  describe("Seed Generation and Validation", () => {
    it("generates cryptographically secure seeds", () => {
      const seed1 = SimulationCorrectnessHarness.generateDeterministicSeed();
      const seed2 = SimulationCorrectnessHarness.generateDeterministicSeed();

      expect(seed1).toHaveLength(32);
      expect(seed2).toHaveLength(32);
      expect(seed1).not.toBe(seed2); // Different seeds each time
    });

    it("validates strong seed format", () => {
      const seed = SimulationCorrectnessHarness.generateDeterministicSeed();
      const isValid = SimulationCorrectnessHarness.validateSeed(seed);

      expect(isValid).toBe(true);
    });

    it("rejects weak seed format", () => {
      const weakSeed = "abc123";
      const isValid = SimulationCorrectnessHarness.validateSeed(weakSeed);

      expect(isValid).toBe(false);
    });

    it("rejects non-hex seed", () => {
      const invalidSeed = "ghijklmnopqrstuvwxyz123456";
      const isValid = SimulationCorrectnessHarness.validateSeed(invalidSeed);

      expect(isValid).toBe(false);
    });
  });

  describe("Determinism Validation", () => {
    it("requires seed for deterministic replay", () => {
      const manifest: SimulationManifest = {
        id: "test-1",
        title: "Test Simulation",
        description: "A test simulation",
        domain: "MATH" as SimulationDomain,
        gradeLevel: 9,
        seed: "", // Empty seed
        parameters: [
          {
            name: "x",
            min: 0,
            max: 10,
            default: 5,
            unit: undefined,
            description: undefined,
          },
        ],
        states: [
          {
            name: "initial",
            transitions: ["final"],
            invariants: ["x >= 0"],
          },
          {
            name: "final",
            transitions: [],
            invariants: ["x <= 10"],
          },
        ],
        invariants: ["x >= 0", "x <= 10"],
        learnerAction: "Adjust the value of x",
        expectedOutputs: { result: 5 },
        metadata: undefined,
        accessibility: {
          keyboardNavigable: true,
          screenReaderCompatible: true,
          colorContrastCompliant: true,
          reducedMotionSupport: true,
          captionsAvailable: true,
          alternativeTextProvided: true,
        },
      };

      const harness = new SimulationCorrectnessHarness({ info: () => {}, error: () => {}, warn: () => {} } as any);
      const report = harness.validate(manifest);

      const seedCheck = report.checks.find((c) => c.check === "deterministic_seed");
      expect(seedCheck).toBeDefined();
      expect(seedCheck?.passed).toBe(false);
      expect(seedCheck?.severity).toBe("ERROR");
    });

    it("accepts valid seed for deterministic replay", () => {
      const manifest: SimulationManifest = {
        id: "test-2",
        title: "Test Simulation",
        description: "A test simulation",
        domain: "MATH" as SimulationDomain,
        gradeLevel: 9,
        seed: SimulationCorrectnessHarness.generateDeterministicSeed(),
        parameters: [
          {
            name: "x",
            min: 0,
            max: 10,
            default: 5,
            unit: undefined,
            description: undefined,
          },
        ],
        states: [
          {
            name: "initial",
            transitions: ["final"],
            invariants: ["x >= 0"],
          },
          {
            name: "final",
            transitions: [],
            invariants: ["x <= 10"],
          },
        ],
        invariants: ["x >= 0", "x <= 10"],
        learnerAction: "Adjust the value of x",
        expectedOutputs: { result: 5 },
        metadata: undefined,
        accessibility: {
          keyboardNavigable: true,
          screenReaderCompatible: true,
          colorContrastCompliant: true,
          reducedMotionSupport: true,
          captionsAvailable: true,
          alternativeTextProvided: true,
        },
      };

      const harness = new SimulationCorrectnessHarness({ info: () => {}, error: () => {}, warn: () => {} } as any);
      const report = harness.validate(manifest);

      const seedCheck = report.checks.find((c) => c.check === "deterministic_seed");
      expect(seedCheck).toBeDefined();
      expect(seedCheck?.passed).toBe(true);
    });
  });

  describe("Replayability", () => {
    it("same seed produces same validation result", () => {
      const seed = SimulationCorrectnessHarness.generateDeterministicSeed();

      const manifest: SimulationManifest = {
        id: "test-3",
        title: "Test Simulation",
        description: "A test simulation",
        domain: "MATH" as SimulationDomain,
        gradeLevel: 9,
        seed,
        parameters: [
          {
            name: "x",
            min: 0,
            max: 10,
            default: 5,
            unit: undefined,
            description: undefined,
          },
        ],
        states: [
          {
            name: "initial",
            transitions: ["final"],
            invariants: ["x >= 0"],
          },
          {
            name: "final",
            transitions: [],
            invariants: ["x <= 10"],
          },
        ],
        invariants: ["x >= 0", "x <= 10"],
        learnerAction: "Adjust the value of x",
        expectedOutputs: { result: 5 },
        metadata: undefined,
        accessibility: {
          keyboardNavigable: true,
          screenReaderCompatible: true,
          colorContrastCompliant: true,
          reducedMotionSupport: true,
          captionsAvailable: true,
          alternativeTextProvided: true,
        },
      };

      const harness = new SimulationCorrectnessHarness({ info: () => {}, error: () => {}, warn: () => {} } as any);
      const report1 = harness.validate(manifest);
      const report2 = harness.validate(manifest);

      expect(report1.overallScore).toBe(report2.overallScore);
      expect(report1.passed).toBe(report2.passed);
    });
  });

  describe("Accessibility Integration", () => {
    it("requires accessibility metadata for production", () => {
      const manifest: SimulationManifest = {
        id: "test-4",
        title: "Test Simulation",
        description: "A test simulation",
        domain: "MATH" as SimulationDomain,
        gradeLevel: 9,
        seed: SimulationCorrectnessHarness.generateDeterministicSeed(),
        parameters: [
          {
            name: "x",
            min: 0,
            max: 10,
            default: 5,
            unit: undefined,
            description: undefined,
          },
        ],
        states: [
          {
            name: "initial",
            transitions: ["final"],
            invariants: ["x >= 0"],
          },
          {
            name: "final",
            transitions: [],
            invariants: ["x <= 10"],
          },
        ],
        invariants: ["x >= 0", "x <= 10"],
        learnerAction: "Adjust the value of x",
        expectedOutputs: { result: 5 },
        metadata: undefined,
        accessibility: {
          keyboardNavigable: true,
          screenReaderCompatible: true,
          colorContrastCompliant: true,
          reducedMotionSupport: true,
          captionsAvailable: true,
          alternativeTextProvided: true,
        },
      };

      const harness = new SimulationCorrectnessHarness({ info: () => {}, error: () => {}, warn: () => {} } as any);
      const report = harness.validate(manifest);

      const keyboardCheck = report.checks.find((c) => c.check === "keyboard_navigable");
      const screenReaderCheck = report.checks.find((c) => c.check === "screen_reader_compatible");
      const contrastCheck = report.checks.find((c) => c.check === "color_contrast_compliant");

      expect(keyboardCheck?.passed).toBe(true);
      expect(screenReaderCheck?.passed).toBe(true);
      expect(contrastCheck?.passed).toBe(true);
    });
  });
});
