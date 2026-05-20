/**
 * @fileoverview Tests for fidelity scorer and residual island detector.
 */

import { describe, it, expect } from "vitest";
import {
  aggregateFidelityReports,
  fidelityGate,
  scoreArtifactNode,
  FIDELITY_THRESHOLDS,
} from "../fidelity/scorer.js";
import { detectResidualIslands } from "../residual/residual-islands.js";
import {
  computeFidelityReport,
  createLogicalArtifactModel,
  type ArtifactNode,
} from "@ghatana/artifact-contracts";
import * as ts from "typescript";

// ============================================================================
// FIDELITY SCORER TESTS
// ============================================================================

describe("fidelityGate", () => {
  it("returns 'clean' for scores at or above CLEAN threshold", () => {
    const report = computeFidelityReport([], "scope");
    expect(fidelityGate(report)).toBe("clean");
  });

  it("returns 'review-recommended' for scores between REVIEW_RECOMMENDED and CLEAN", () => {
    // Create a report with a score of 0.8
    const lossPoints = [
      {
        code: "test-loss",
        description: "test",
        severity: "info" as const,
        confidenceImpact: 0.15,
      },
    ];
    const report = computeFidelityReport(lossPoints, "scope");
    expect(report.score).toBe(0.85);
    expect(fidelityGate(report)).toBe("review-recommended");
  });

  it("returns 'blocked' for scores below BLOCKED threshold", () => {
    const lossPoints = [
      { code: "a", description: "a", severity: "critical" as const, confidenceImpact: 0.6 },
    ];
    const report = computeFidelityReport(lossPoints, "scope");
    expect(fidelityGate(report)).toBe("blocked");
  });

  it("uses correct threshold values", () => {
    expect(FIDELITY_THRESHOLDS.CLEAN).toBe(0.95);
    expect(FIDELITY_THRESHOLDS.REVIEW_RECOMMENDED).toBe(0.75);
    expect(FIDELITY_THRESHOLDS.BLOCKED).toBe(0.5);
  });
});

describe("aggregateFidelityReports", () => {
  it("returns perfect report for empty map", () => {
    const result = aggregateFidelityReports(new Map(), "pipeline-1");
    expect(result.score).toBe(1);
    expect(result.canRoundTrip).toBe(true);
  });

  it("computes mean score across reports", () => {
    const r1 = computeFidelityReport(
      [{ code: "a", description: "a", severity: "warning" as const, confidenceImpact: 0.2 }],
      "n1",
    );
    const r2 = computeFidelityReport([], "n2");
    const map = new Map([
      ["n1", r1],
      ["n2", r2],
    ]);
    const result = aggregateFidelityReports(map, "pipeline");
    // Mean of 0.8 and 1.0 = 0.9
    expect(result.score).toBeCloseTo(0.9, 5);
  });

  it("collects all loss points from all reports", () => {
    const r1 = computeFidelityReport(
      [{ code: "lp1", description: "lp1", severity: "info" as const, confidenceImpact: 0.1 }],
      "n1",
    );
    const r2 = computeFidelityReport(
      [{ code: "lp2", description: "lp2", severity: "info" as const, confidenceImpact: 0.05 }],
      "n2",
    );
    const result = aggregateFidelityReports(new Map([["n1", r1], ["n2", r2]]), "p");
    expect(result.lossPoints.length).toBe(2);
  });
});

describe("scoreArtifactNode", () => {
  it("returns perfect score for a fully inferred component node", () => {
    const report = scoreArtifactNode(
      "n1",
      "component",
      ["Button"],
      { label: "string" },
      true,
      1,
    );
    expect(report.score).toBe(1);
    expect(report.canRoundTrip).toBe(true);
  });

  it("deducts for missing exported symbols", () => {
    const report = scoreArtifactNode("n2", "component", [], { label: "string" }, true, 1);
    expect(report.score).toBeLessThan(1);
    const lp = report.lossPoints.find((lp) => lp.code === "no-exported-symbols");
    expect(lp).toBeDefined();
  });

  it("deducts for missing inferred props on component", () => {
    const report = scoreArtifactNode("n3", "component", ["MyComp"], {}, true, 1);
    expect(report.score).toBeLessThan(1);
    const lp = report.lossPoints.find((lp) => lp.code === "no-inferred-props");
    expect(lp).toBeDefined();
  });

  it("deducts for low classification confidence", () => {
    const report = scoreArtifactNode("n4", "component", ["MyComp"], { x: "string" }, true, 0.45);
    const lp = report.lossPoints.find((lp) => lp.code === "low-classification-confidence");
    expect(lp).toBeDefined();
    expect(lp?.severity).toBe("critical");
  });

  it("does not deduct for no-design-system-usage on non-component kinds", () => {
    const report = scoreArtifactNode("n5", "utility", ["fn"], {}, false, 1);
    const lp = report.lossPoints.find((lp) => lp.code === "no-design-system-usage");
    expect(lp).toBeUndefined();
  });
});

// ============================================================================
// RESIDUAL ISLAND DETECTOR TESTS
// ============================================================================

describe("detectResidualIslands", () => {
  it("returns empty report for a clean model with no parsed files", () => {
    const model = createLogicalArtifactModel("m1", "Clean");
    const report = detectResidualIslands(model);

    expect(report.islands).toHaveLength(0);
    expect(report.totalCount).toBe(0);
    expect(report.blockingCount).toBe(0);
    expect(report.canCompileWithResiduals).toBe(true);
  });

  it("flags low-confidence nodes in the model", () => {
    const baseModel = createLogicalArtifactModel("m2", "LowConf");
    const node: ArtifactNode = {
      id: "n1",
      displayName: "Unknown",
      kind: "unknown",
      exportedSymbols: [],
      inferredProps: {},
      usesDesignSystem: false,
      classificationConfidence: 0.4, // below 0.7 threshold
      metadata: {},
    };
    const model = { ...baseModel, nodes: { n1: node } };
    const report = detectResidualIslands(model);

    expect(report.totalCount).toBeGreaterThan(0);
    const island = report.islands.find((i) => i.id.includes("low-confidence"));
    expect(island).toBeDefined();
    expect(island?.severity).toBe("blocking"); // 0.4 < 0.5
  });

  it("detects eval() in parsed source files", () => {
    const model = createLogicalArtifactModel("m3", "Eval");
    const sourceFile = ts.createSourceFile(
      "eval-usage.ts",
      "eval('alert(1)');",
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TS,
    );
    const report = detectResidualIslands(model, {
      parsedFiles: [{ relativePath: "eval-usage.ts", sourceFile }],
    });

    const evalIsland = report.islands.find((i) => i.kind === "runtime-dynamic");
    expect(evalIsland).toBeDefined();
    expect(evalIsland?.severity).toBe("blocking");
  });

  it("detects CSS-in-JS patterns in parsed source files", () => {
    const model = createLogicalArtifactModel("m4", "CSS-in-JS");
    const sourceFile = ts.createSourceFile(
      "styled.tsx",
      'import styled from "styled-components";\nconst Box = styled.div`color: red;`;',
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TSX,
    );
    const report = detectResidualIslands(model, {
      parsedFiles: [{ relativePath: "styled.tsx", sourceFile }],
    });

    const cssIsland = report.islands.find((i) => i.kind === "css-in-js-pattern");
    expect(cssIsland).toBeDefined();
    expect(cssIsland?.severity).toBe("advisory");
  });

  it("canCompileWithResiduals is true when there are no blocking islands", () => {
    const baseModel = createLogicalArtifactModel("m5", "Advisory");
    const node: ArtifactNode = {
      id: "n1",
      displayName: "Advisory",
      kind: "component",
      exportedSymbols: ["Advisory"],
      inferredProps: {},
      usesDesignSystem: false,
      classificationConfidence: 0.75, // advisory range
      metadata: {},
    };
    const model = { ...baseModel, nodes: { n1: node } };
    const report = detectResidualIslands(model);

    expect(report.canCompileWithResiduals).toBe(true);
    expect(report.blockingCount).toBe(0);
  });
});
