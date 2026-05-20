/**
 * @fileoverview Tests for the TSX decompiler.
 */

import { describe, it, expect } from "vitest";
import { decompileTsx } from "../decompile/tsx.js";
import type { DecompileSourceFile } from "../decompile/tsx.js";

// ============================================================================
// FIXTURES
// ============================================================================

const SIMPLE_COMPONENT: DecompileSourceFile = {
  relativePath: "src/components/Button.tsx",
  content: `
import React from "react";

export interface ButtonProps {
  readonly label: string;
  readonly onClick: () => void;
  readonly disabled?: boolean;
}

export function Button({ label, onClick, disabled }: ButtonProps) {
  return <button onClick={onClick} disabled={disabled}>{label}</button>;
}
`.trim(),
};

const PAGE_FILE: DecompileSourceFile = {
  relativePath: "src/pages/HomePage.tsx",
  content: `
import React from "react";
import { Button } from "../components/Button";

export interface HomePageProps {
  readonly title: string;
}

export function HomePage({ title }: HomePageProps) {
  return (
    <div>
      <h1>{title}</h1>
      <Button label="Click me" onClick={() => {}} />
    </div>
  );
}

export default HomePage;
`.trim(),
};

const UTILITY_FILE: DecompileSourceFile = {
  relativePath: "src/utils/formatters.ts",
  content: `
export function formatCurrency(value: number, currency: string): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
}

export function formatDate(date: Date): string {
  return date.toISOString().split("T")[0] ?? "";
}
`.trim(),
};

const HOOK_FILE: DecompileSourceFile = {
  relativePath: "src/hooks/useCounter.ts",
  content: `
import { useState } from "react";

export function useCounter(initial: number = 0) {
  const [count, setCount] = useState(initial);
  return { count, increment: () => setCount(c => c + 1) };
}
`.trim(),
};

const DS_COMPONENT: DecompileSourceFile = {
  relativePath: "src/components/Card.tsx",
  content: `
import React from "react";
import { Button, Badge } from "@ghatana/design-system";

export interface CardProps {
  readonly title: string;
  readonly backgroundColor: string;
  readonly paddingSize: string;
}

export function Card({ title, backgroundColor, paddingSize }: CardProps) {
  return (
    <div style={{ background: backgroundColor, padding: paddingSize }}>
      <Badge>{title}</Badge>
      <Button label="Open" onClick={() => {}} />
    </div>
  );
}
`.trim(),
};

// ============================================================================
// TESTS
// ============================================================================

describe("decompileTsx", () => {
  it("creates a model with the correct label and modelId", () => {
    const result = decompileTsx({
      label: "test-workspace",
      modelId: "model-001",
      files: [SIMPLE_COMPONENT],
    });

    expect(result.model.label).toBe("test-workspace");
    expect(result.model.modelId).toBe("model-001");
    expect(result.model.schemaVersion).toBe("1.0.0");
  });

  it("classifies a TSX file as component kind", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m1",
      files: [SIMPLE_COMPONENT],
    });

    const nodeId = "src/components/Button.tsx";
    const node = result.model.nodes[nodeId];
    expect(node).toBeDefined();
    expect(node?.kind).toBe("component");
    expect(node?.displayName).toBe("Button");
  });

  it("classifies a page file as page kind", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m2",
      files: [PAGE_FILE],
    });

    const node = result.model.nodes["src/pages/HomePage.tsx"];
    expect(node?.kind).toBe("page");
  });

  it("classifies a utility TS file as utility kind", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m3",
      files: [UTILITY_FILE],
    });

    const node = result.model.nodes["src/utils/formatters.ts"];
    expect(node?.kind).toBe("utility");
  });

  it("classifies a hook file as hook kind", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m4",
      files: [HOOK_FILE],
    });

    const node = result.model.nodes["src/hooks/useCounter.ts"];
    expect(node?.kind).toBe("hook");
  });

  it("extracts exported symbols from a component", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m5",
      files: [SIMPLE_COMPONENT],
    });

    const node = result.model.nodes["src/components/Button.tsx"];
    expect(node?.exportedSymbols).toContain("ButtonProps");
    expect(node?.exportedSymbols).toContain("Button");
  });

  it("infers props from a Props interface", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m6",
      files: [SIMPLE_COMPONENT],
    });

    const node = result.model.nodes["src/components/Button.tsx"];
    expect(node?.inferredProps).toHaveProperty("label");
    expect(node?.inferredProps).toHaveProperty("onClick");
    expect(node?.inferredProps).toHaveProperty("disabled");
  });

  it("detects design-system usage when DS component names are provided", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m7",
      files: [DS_COMPONENT],
      designSystemComponentNames: new Set(["Button", "Badge"]),
    });

    const node = result.model.nodes["src/components/Card.tsx"];
    expect(node?.usesDesignSystem).toBe(true);
  });

  it("does not mark usesDesignSystem when DS names are not provided", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m8",
      files: [DS_COMPONENT],
      // no designSystemComponentNames
    });

    const node = result.model.nodes["src/components/Card.tsx"];
    expect(node?.usesDesignSystem).toBe(false);
  });

  it("produces a perfect fidelity report for simple files", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m9",
      files: [SIMPLE_COMPONENT, UTILITY_FILE],
    });

    expect(result.fidelityReport.score).toBe(1);
    expect(result.fidelityReport.canRoundTrip).toBe(true);
  });

  it("creates intra-model edges for relative imports", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m10",
      files: [SIMPLE_COMPONENT, PAGE_FILE],
    });

    // HomePage imports from ../components/Button — should create an edge
    expect(result.model.edges.length).toBeGreaterThan(0);
  });

  it("produces per-file fidelity reports for each input file", () => {
    const result = decompileTsx({
      label: "test",
      modelId: "m11",
      files: [SIMPLE_COMPONENT, UTILITY_FILE],
    });

    expect(result.perFileFidelity.has("src/components/Button.tsx")).toBe(true);
    expect(result.perFileFidelity.has("src/utils/formatters.ts")).toBe(true);
  });
});
