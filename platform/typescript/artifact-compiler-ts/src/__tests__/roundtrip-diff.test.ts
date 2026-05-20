/**
 * @fileoverview Tests for round-trip diff report generation.
 */

import { describe, expect, it } from "vitest";
import { compileReact } from "../compile/react.js";
import { decompileTsx } from "../decompile/tsx.js";
import { buildRoundTripDiffReport } from "../diff/roundtrip-diff.js";

const SOURCE = `
import type { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): ReactElement {
  return <button>{props.label}</button>;
}
`.trim();

describe("buildRoundTripDiffReport", () => {
  it("marks generated files as semantically equivalent when re-import preserves model shape", () => {
    const decompiled = decompileTsx({
      label: "diff-test",
      modelId: "diff-model",
      files: [{ relativePath: "src/Button.tsx", content: SOURCE }],
    });
    const compiled = compileReact(decompiled.model);
    const reimported = decompileTsx({
      label: "diff-test-reimport",
      modelId: "diff-model",
      files: compiled.emittedFiles.map((file) => ({
        relativePath: file.relativePath,
        content: file.content,
      })),
    });

    const report = buildRoundTripDiffReport({
      reportId: "diff-report",
      model: decompiled.model,
      originalSources: [{ relativePath: "src/Button.tsx", content: SOURCE }],
      generatedSources: compiled.emittedFiles,
      reimportedModel: reimported.model,
      fidelity: compiled.overallFidelity,
    });

    expect(report.diffs).toHaveLength(1);
    expect(report.diffs[0]?.semanticallyEquivalent).toBe(true);
    expect(report.diffs[0]?.hunks.length).toBeGreaterThan(0);
    expect(report.isLossless).toBe(false);
  });

  it("marks missing re-imported nodes as not semantically equivalent", () => {
    const decompiled = decompileTsx({
      label: "diff-test",
      modelId: "diff-model",
      files: [{ relativePath: "src/Button.tsx", content: SOURCE }],
    });

    const report = buildRoundTripDiffReport({
      reportId: "diff-report",
      model: decompiled.model,
      originalSources: [{ relativePath: "src/Button.tsx", content: SOURCE }],
      generatedSources: [{ relativePath: "src/Button.tsx", content: "export const Broken = 1;" }],
      reimportedModel: { ...decompiled.model, nodes: {} },
    });

    expect(report.diffs[0]?.semanticallyEquivalent).toBe(false);
  });
});
