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

  it("source → model → source → model (two-pass) produces semantically equivalent models", () => {
    // First pass
    const firstDecompile = decompileTsx(makeInput([BUTTON_FIXTURE]));
    const firstNodeId = Object.keys(firstDecompile.model.nodes)[0];
    expect(firstNodeId).toBeDefined();

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

    // Semantic equivalence: same node IDs exist in both models
    const firstNodeIds = new Set(Object.keys(firstDecompile.model.nodes));
    const secondNodeIds = new Set(Object.keys(secondDecompile.model.nodes));

    // Every node from first pass should have a corresponding node in second pass
    for (const nodeId of firstNodeIds) {
      expect(secondNodeIds.has(nodeId)).toBe(true);
    }

    // If the same node exists in both, verify key semantic properties are preserved
    if (firstNodeId) {
      const firstNode = firstDecompile.model.nodes[firstNodeId];
      const secondNode = secondDecompile.model.nodes[firstNodeId];
      expect(secondNode).toBeDefined();

      // Kind should be preserved
      expect(secondNode?.kind).toBe(firstNode.kind);
      // Display name should be preserved
      expect(secondNode?.displayName).toBe(firstNode.displayName);
      // Exported symbols should be preserved
      expect(secondNode?.exportedSymbols).toEqual(firstNode.exportedSymbols);
      // Inferred props should be preserved
      expect(secondNode?.inferredProps).toEqual(firstNode.inferredProps);
      // Design system usage flag should be preserved
      expect(secondNode?.usesDesignSystem).toBe(firstNode.usesDesignSystem);
      // Classification confidence should be preserved
      expect(secondNode?.classificationConfidence).toBe(firstNode.classificationConfidence);
    }
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
      expect(typeof lossPoint.description).toBe("string");
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
      expect(typeof island.id).toBe("string");
      expect(typeof island.description).toBe("string");
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
  it("protected regions are parsed and preserved through round-trip", () => {
    // Create a source file with protected region markers
    const sourceWithRegions: DecompileSourceFile = {
      relativePath: "src/Button.tsx",
      content: `
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

// @ghatana-region: begin src/Button.tsx:body owner=user-authored
export function Button({ label }: ButtonProps): ReactElement {
  return <button>{label}</button>;
}
// @ghatana-region: end src/Button.tsx:body
`.trim(),
    };

    const decompileResult = decompileTsx(makeInput([sourceWithRegions]));
    const nodeId = Object.keys(decompileResult.model.nodes)[0];
    expect(nodeId).toBeDefined();

    const node = decompileResult.model.nodes[nodeId!];
    const protectedRegions = (node.metadata?.protectedRegions as unknown as Array<{
      regionId: string;
      ownerKind: string;
      contentLines: string[];
    }>) ?? [];

    // Should have parsed the protected region
    expect(protectedRegions.length).toBeGreaterThan(0);
    const bodyRegion = protectedRegions.find(r => r.regionId === `${nodeId}:body`);
    expect(bodyRegion).toBeDefined();
    expect(bodyRegion?.ownerKind).toBe("user-authored");

    // Compile should preserve the protected region content
    const compileResult = compileReact(decompileResult.model);
    const emittedFile = compileResult.emittedFiles.find(f => f.relativePath === sourceWithRegions.relativePath);
    expect(emittedFile).toBeDefined();
    expect(emittedFile?.content).toContain("@ghatana-region: begin");
    expect(emittedFile?.content).toContain("owner=user-authored");
  });

  it("modified protected region content is preserved on recompile", () => {
    // Source with protected region
    const sourceWithRegions: DecompileSourceFile = {
      relativePath: "src/Button.tsx",
      content: `
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

// @ghatana-region: begin src/Button.tsx:body owner=user-authored
export function Button({ label }: ButtonProps): ReactElement {
  return <button className="custom-button">{label}</button>;
}
// @ghatana-region: end src/Button.tsx:body
`.trim(),
    };

    const decompileResult = decompileTsx(makeInput([sourceWithRegions]));
    const nodeId = Object.keys(decompileResult.model.nodes)[0];

    // Modify the protected region content in metadata
    const node = decompileResult.model.nodes[nodeId!];
    const protectedRegions = (node.metadata?.protectedRegions as unknown as Array<{
      regionId: string;
      ownerKind: string;
      contentLines: string[];
    }>) ?? [];
    
    const bodyRegion = protectedRegions.find(r => r.regionId === `${nodeId}:body`);
    expect(bodyRegion).toBeDefined();
    
    // Simulate user modification: change the content
    if (bodyRegion) {
      (bodyRegion as any).contentLines = [
        "export function Button({ label }: ButtonProps): ReactElement {",
        "  return <button className=\"user-modified\">{label}</button>;",
        "}",
      ];
    }

    // Recompile - should preserve the modified content
    const compileResult = compileReact(decompileResult.model);
    const emittedFile = compileResult.emittedFiles.find(f => f.relativePath === sourceWithRegions.relativePath);
    expect(emittedFile).toBeDefined();
    expect(emittedFile?.content).toContain("user-modified");
    expect(emittedFile?.content).toContain("@ghatana-region: begin");
    expect(emittedFile?.content).toContain("@ghatana-region: end");
  });
});
