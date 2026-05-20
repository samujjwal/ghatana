/**
 * @test.type unit
 * @test.execution <50ms
 * @test.infra none
 */

import { describe, it, expect } from "vitest";
import {
  computeFidelityReport,
  createPerfectFidelityReport,
  createResidualIslandReport,
  type LossPoint,
  type ResidualIsland,
} from "../fidelity.js";

describe("computeFidelityReport", () => {
  it("returns perfect score when no loss points", () => {
    const report = computeFidelityReport([]);
    expect(report.score).toBe(1);
    expect(report.canRoundTrip).toBe(true);
    expect(report.lossPoints).toHaveLength(0);
  });

  it("reduces score by confidenceImpact sum", () => {
    const lossPoints: LossPoint[] = [
      {
        code: "BINDINGS_NOT_ENCODED",
        description: "Bindings not encoded",
        severity: "warning",
        confidenceImpact: 0.1,
      },
      {
        code: "STATE_VARIANT_MISSING",
        description: "State variant not generated",
        severity: "warning",
        confidenceImpact: 0.2,
      },
    ];
    const report = computeFidelityReport(lossPoints);
    expect(report.score).toBeCloseTo(0.7, 5);
    expect(report.canRoundTrip).toBe(false);
  });

  it("clamps score to 0 when impact exceeds 1", () => {
    const lossPoints: LossPoint[] = [
      {
        code: "TOTAL_LOSS",
        description: "Cannot model",
        severity: "critical",
        confidenceImpact: 1.5,
      },
    ];
    const report = computeFidelityReport(lossPoints);
    expect(report.score).toBe(0);
  });

  it("sets scopeId and scope from arguments", () => {
    const report = computeFidelityReport([], "my-node", "file");
    expect(report.scopeId).toBe("my-node");
    expect(report.scope).toBe("file");
  });
});

describe("createPerfectFidelityReport", () => {
  it("returns score 1 with no loss points", () => {
    const report = createPerfectFidelityReport("test-id");
    expect(report.score).toBe(1);
    expect(report.canRoundTrip).toBe(true);
    expect(report.lossPoints).toHaveLength(0);
    expect(report.scopeId).toBe("test-id");
  });
});

describe("createResidualIslandReport", () => {
  it("returns empty report for no islands", () => {
    const report = createResidualIslandReport([]);
    expect(report.totalCount).toBe(0);
    expect(report.blockingCount).toBe(0);
    expect(report.canCompileWithResiduals).toBe(true);
  });

  it("counts blocking vs advisory correctly", () => {
    const islands: ResidualIsland[] = [
      {
        id: "r1",
        kind: "unsupported-syntax",
        description: "Cannot parse",
        severity: "blocking",
      },
      {
        id: "r2",
        kind: "imperative-logic",
        description: "Imperative logic",
        severity: "advisory",
      },
    ];
    const report = createResidualIslandReport(islands);
    expect(report.totalCount).toBe(2);
    expect(report.blockingCount).toBe(1);
    expect(report.canCompileWithResiduals).toBe(false);
  });
});
