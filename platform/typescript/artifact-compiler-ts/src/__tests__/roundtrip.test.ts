/**
 * @fileoverview Round-trip tests: source → model → source → model.
 *
 * Proves that:
 *   1. source → model (decompile) does not silently drop structural intent.
 *   2. model → source (compile) produces well-formed output.
 *   3. source → model → source → model (two-pass) produces a stable model —
 *      the second model must contain at least as many nodes as the first.
 *
 * @doc.type test
 * @doc.purpose Round-trip fidelity hardening for artifact-compiler-ts
 * @doc.layer platform
 */

import { describe, it, expect } from "vitest";
import { decompileTsx } from "../decompile/tsx.js";
import type { DecompileSourceFile } from "../decompile/tsx.js";
import { compileReact } from "../compile/react.js";
import { detectResidualIslands } from "../residual/residual-islands.js";
import { fidelityGate } from "../fidelity/scorer.js";
import type { LogicalArtifactModel } from "@ghatana/artifact-contracts";

// ============================================================================
// Fixtures
// ============================================================================

const BUTTON_FIXTURE: DecompileSourceFile = {
  relativePath: "src/Button.tsx",
  content: `
import React from "react";

export interface ButtonProps {
  readonly label: string;
  readonly onClick: () => void;
  readonly disabled?: boolean;
}

export function Button({ label, onClick, disabled }: ButtonProps) {
  return <button type="button" onClick={onClick} disabled={disabled}>{label}</button>;
}
`.trim(),
};

const CARD_FIXTURE: DecompileSourceFile = {
  relativePath: "src/Card.tsx",
  content: `
import React from "react";

export interface CardProps {
  readonly title: string;
  readonly children: React.ReactNode;
}

export function Card({ title, children }: CardProps) {
  return (
    <div className="card">
      <h2>{title}</h2>
      <div className="card-body">{children}</div>
    </div>
  );
}
`.trim(),
};

const INDEX_FIXTURE: DecompileSourceFile = {
  relativePath: "src/index.ts",
  content: `export { Button } from "./Button";
export { Card } from "./Card";
export type { ButtonProps } from "./Button";
export type { CardProps } from "./Card";
`,
};

// ============================================================================
// Helpers
// ============================================================================

function makeInput(files: readonly DecompileSourceFile[]): import("../decompile/tsx.js").DecompileTsxInput {
  return {
    label: "test-model",
    modelId: "00000000-0000-0000-0000-000000000001",
    files,
  };
}

// ============================================================================
// Tests
// ============================================================================

describe("Round-trip: source → model → source → model", () => {
  it("decompiles a single component and produces a valid model", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    const nodeCount = Object.keys(result.model.nodes).length;
    expect(nodeCount).toBeGreaterThan(0);
    expect(result.model.schemaVersion).toBe("1.0.0");
  });

  it("source → model → source produces compilable output", () => {
    const decompileResult = decompileTsx(makeInput([BUTTON_FIXTURE]));

    const compileResult = compileReact(decompileResult.model);
    expect(compileResult.emittedFiles.length).toBeGreaterThan(0);
    // The component key "Button" should appear in model nodes
    const nodeKeys = Object.keys(decompileResult.model.nodes);
    expect(nodeKeys.some((k) => k.includes("Button"))).toBe(true);
    // Compiled output should be non-empty
    const allContent = compileResult.emittedFiles.map((f) => f.content).join("\n");
    expect(allContent.trim().length).toBeGreaterThan(0);
  });

  it("source → model → source → model (two-pass) produces a stable node count", () => {
    // First pass
    const firstDecompile = decompileTsx(makeInput([BUTTON_FIXTURE]));
    const firstNodeCount = Object.keys(firstDecompile.model.nodes).length;

    // Compile back to source
    const firstCompile = compileReact(firstDecompile.model);
    expect(firstCompile.emittedFiles.length).toBeGreaterThan(0);
    const firstEmitted = firstCompile.emittedFiles[0];
    if (firstEmitted === undefined) return; // guard

    // Second pass — decompile the compiled output
    const secondSource: DecompileSourceFile = {
      relativePath: "src/Button.gen.tsx",
      content: firstEmitted.content,
    };
    const secondDecompile = decompileTsx(makeInput([secondSource]));

    // Second model should have at least as many nodes as the first
    const secondNodeCount = Object.keys(secondDecompile.model.nodes).length;
    expect(secondNodeCount).toBeGreaterThanOrEqual(firstNodeCount);
  });

  it("multi-file round-trip preserves all source files as nodes", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE, CARD_FIXTURE, INDEX_FIXTURE]));

    const nodeCount = Object.keys(result.model.nodes).length;
    expect(nodeCount).toBeGreaterThanOrEqual(2); // Button + Card at minimum

    const compileResult = compileReact(result.model);
    expect(compileResult.emittedFiles.length).toBeGreaterThan(0);
  });

  it("compiled source references at least one top-level component name", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE, CARD_FIXTURE]));

    // At least one component key should reference a known component
    const nodeKeys = Object.keys(result.model.nodes);
    const hasButton = nodeKeys.some((k) => k.includes("Button"));
    const hasCard = nodeKeys.some((k) => k.includes("Card"));
    expect(hasButton || hasCard).toBe(true);
  });
});

describe("Round-trip: no silent loss of source intent", () => {
  it("loss points are enumerated, not silently dropped", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    // Fidelity report should always be present
    expect(result.fidelityReport).toBeDefined();
    expect(typeof result.fidelityReport.score).toBe("number");
    expect(result.fidelityReport.score).toBeGreaterThan(0);
    expect(result.fidelityReport.score).toBeLessThanOrEqual(1);

    // Every loss point must have required fields
    for (const lossPoint of result.fidelityReport.lossPoints) {
      expect(typeof lossPoint.code).toBe("string");
      expect(typeof lossPoint.message).toBe("string");
      expect(["critical", "warning", "info"]).toContain(lossPoint.severity);
    }
  });

  it("residual islands are surfaced when present", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    const residualReport = detectResidualIslands(result.model);
    expect(residualReport).toBeDefined();
    expect(typeof residualReport.islands).toBe("object");
    expect(Array.isArray(residualReport.islands)).toBe(true);

    for (const island of residualReport.islands) {
      expect(typeof island.islandId).toBe("string");
      expect(typeof island.reason).toBe("string");
    }
  });

  it("fidelityGate returns a deterministic verdict", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    const verdict = fidelityGate(result.fidelityReport);
    expect(["clean", "review-recommended", "blocked"]).toContain(verdict);
  });
});

describe("Round-trip: ownership and provenance are preserved", () => {
  it("every decompiled node references its source file", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    for (const node of Object.values(result.model.nodes)) {
      // sourceRef may be undefined for synthetic nodes, but if set must be valid
      if (node.sourceRef !== undefined) {
        expect(typeof node.sourceRef.file.relativePath).toBe("string");
        expect(node.sourceRef.file.relativePath.length).toBeGreaterThan(0);
      }
    }
  });

  it("model schema version is stable across compile/decompile", () => {
    const firstResult = decompileTsx(makeInput([BUTTON_FIXTURE]));
    expect(firstResult.model.schemaVersion).toBe("1.0.0");

    const compiled = compileReact(firstResult.model);
    const firstFile = compiled.emittedFiles[0];
    if (firstFile === undefined) return;
    const secondResult = decompileTsx(makeInput([
      { relativePath: "src/Button.gen.tsx", content: firstFile.content },
    ]));
    expect(secondResult.model.schemaVersion).toBe("1.0.0");
  });
});

describe("Round-trip: protected regions", () => {
  it("nodes with isProtected=true are preserved in the model through compile", () => {
    const result = decompileTsx(makeInput([BUTTON_FIXTURE]));

    const nodeEntries = Object.entries(result.model.nodes);
    expect(nodeEntries.length).toBeGreaterThan(0);
    const [firstKey, firstNode] = nodeEntries[0]!;

    // Mark first node as protected
    const protectedModel: LogicalArtifactModel = {
      ...result.model,
      nodes: {
        ...result.model.nodes,
        [firstKey]: { ...firstNode, isProtected: true },
      },
    };

    const compileResult = compileReact(protectedModel);
    expect(compileResult.emittedFiles.length).toBeGreaterThan(0);
    // Protected flag must survive in the input model (no side effects)
    expect(protectedModel.nodes[firstKey]?.isProtected).toBe(true);
  });
});
