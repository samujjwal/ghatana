import { describe, expect, it } from "vitest";
import { FileBootstrapGateProvider } from "../FileBootstrapGateProvider";

describe("FileBootstrapGateProvider", () => {
  it("passes valid gate evaluations with evidence", async () => {
    const provider = new FileBootstrapGateProvider();

    const result = await provider.evaluateGate({
      gateId: "registry-validation",
      productUnitId: "digital-marketing",
      phase: "validate",
      context: {},
    });

    expect(result.passed).toBe(true);
    expect(result.gateId).toBe("registry-validation");
    expect(result.evidence).toEqual(["bootstrap-gate:registry-validation"]);
  });

  it("fails closed when gate id is empty", async () => {
    const provider = new FileBootstrapGateProvider();

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
});
