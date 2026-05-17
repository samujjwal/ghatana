import { describe, expect, it } from "vitest";
import { FileBootstrapGateProvider } from "../FileBootstrapGateProvider";

describe("FileBootstrapGateProvider", () => {
  describe("with no supportedGates configured", () => {
    it("returns NOT_READY for any gate when supportedGates is empty", async () => {
      const provider = new FileBootstrapGateProvider({ supportedGates: [] });

      const result = await provider.evaluateGate({
        gateId: "registry-validation",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(false);
      expect(result.gateId).toBe("registry-validation");
      expect(result.reason).toContain("NOT_READY");
      expect(result.reason).toContain("no concrete provider implementation found");
      expect(result.evidence).toEqual([]);
    });

    it("returns synthetic success for gates in supportedGates list", async () => {
      const provider = new FileBootstrapGateProvider({
        supportedGates: ["registry-validation", "manifest-validation"],
      });

      const result = await provider.evaluateGate({
        gateId: "registry-validation",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(true);
      expect(result.gateId).toBe("registry-validation");
      expect(result.evidence).toEqual(["bootstrap-gate:registry-validation"]);
      expect(result.reason).toContain("synthetic - replace with concrete provider");
    });

    it("returns NOT_READY for gates not in supportedGates list", async () => {
      const provider = new FileBootstrapGateProvider({
        supportedGates: ["registry-validation"],
      });

      const result = await provider.evaluateGate({
        gateId: "manifest-validation",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(false);
      expect(result.gateId).toBe("manifest-validation");
      expect(result.reason).toContain("NOT_READY");
      expect(result.evidence).toEqual([]);
    });
  });

  describe("with supportedGates not configured", () => {
    it("fails closed and returns NOT_READY when supportedGates is not provided", async () => {
      const provider = new FileBootstrapGateProvider();

      const result = await provider.evaluateGate({
        gateId: "registry-validation",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(false);
      expect(result.gateId).toBe("registry-validation");
      expect(result.evidence).toEqual([]);
      expect(result.reason).toContain("NOT_READY");
    });
  });

  describe("edge cases", () => {
    it("fails when gate id is empty", async () => {
      const provider = new FileBootstrapGateProvider({ supportedGates: [] });

      const result = await provider.evaluateGate({
        gateId: "   ",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(false);
      expect(result.evidence).toEqual([]);
      expect(result.reason).toContain("non-empty gateId");
    });

    it("handles whitespace in gate id", async () => {
      const provider = new FileBootstrapGateProvider({
        supportedGates: ["registry-validation"],
      });

      const result = await provider.evaluateGate({
        gateId: "  registry-validation  ",
        productUnitId: "digital-marketing",
        phase: "validate",
        context: {},
      });

      expect(result.passed).toBe(true);
      expect(result.gateId).toBe("registry-validation");
    });
  });
});
