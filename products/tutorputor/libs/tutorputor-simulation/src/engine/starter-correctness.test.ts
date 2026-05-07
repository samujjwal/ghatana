import { describe, expect, it } from "vitest";
import {
  exportSimulationStarterPackage,
  getSimulationStarterById,
} from "./starter-packaging";
import {
  evaluateStarterGoldenOutput,
  isWithinTolerance,
} from "./starter-correctness";

const goldenStarterIds = [
  "starter-derivative-tangent",
  "starter-newton-cart",
  "starter-membrane-transport",
  "starter-dose-response",
  "starter-binary-search",
  "starter-supply-demand-dynamics",
  "starter-ohms-law",
];

describe("starter simulation semantic correctness", () => {
  it("matches deterministic seeded golden outputs across required domains", () => {
    for (const starterId of goldenStarterIds) {
      const starter = getSimulationStarterById(starterId);
      expect(starter, starterId).not.toBeNull();

      const result = evaluateStarterGoldenOutput(starter!.manifest);

      expect(result.seed, starterId).toBe(42);
      expect(result.outputValue, starterId).toBe(result.expectedValue);
      expect(isWithinTolerance(result), starterId).toBe(true);
      expect(result.failureStateIds, starterId).toEqual([]);
    }
  });

  it("clamps out-of-range controls and reports recoverable failure states", () => {
    const starter = getSimulationStarterById("starter-newton-cart");
    expect(starter).not.toBeNull();

    const result = evaluateStarterGoldenOutput(starter!.manifest, 999);

    expect(result.clamped).toBe(true);
    expect(result.clampedValue).toBe(10);
    expect(result.failureStateIds).toContain("out-of-bounds");
  });

  it("exports canonical starter manifests without dropping runtime and accessibility metadata", () => {
    const webxr = exportSimulationStarterPackage({
      starterRef: "starter-ohms-law",
      format: "webxr",
    });
    const unity = exportSimulationStarterPackage({
      starterRef: "starter-ohms-law",
      format: "unity",
    });

    expect(webxr?.packageData.format).toBe("webxr");
    expect(unity?.packageData.format).toBe("unity");
    expect(webxr?.manifest.canonical?.claimLinks).toHaveLength(1);
    expect(webxr?.manifest.accessibility?.altText).toContain("Ohm's Law");
    expect(unity?.manifest.canonical?.telemetryEvents.map((event) => event.eventType)).toEqual(
      expect.arrayContaining(["sim.start", "sim.capture", "sim.complete"]),
    );
  });
});
