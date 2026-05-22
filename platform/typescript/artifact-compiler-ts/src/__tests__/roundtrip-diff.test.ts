/**
 * @fileoverview Tests for round-trip diff report generation.
 */

import { describe, expect, it } from "vitest";
import { compileReact } from "../compile/react.js";
import { decompileTsx } from "../decompile/tsx.js";
import {
  buildRoundTripDiffReport,
  createNotRunValidationPipelineResult,
} from "../diff/roundtrip-diff.js";

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
    expect(report.paritySections?.map((section) => section.kind)).toEqual([
      'ast-semantic',
      'import-graph',
      'component',
      'api',
      'design-token',
      'validation',
    ]);
    expect(report.paritySections?.find((section) => section.kind === 'component')?.status).toBe('passed');
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

  it('marks import-shape changes as not semantically equivalent', () => {
    const decompiled = decompileTsx({
      label: 'diff-test-import-shape',
      modelId: 'diff-model-import-shape',
      files: [{ relativePath: 'src/Button.tsx', content: SOURCE }],
    });

    const generated = `
import { ReactElement } from "react";

export interface ButtonProps {
  readonly label: string;
}

export function Button(props: ButtonProps): ReactElement {
  return <button>{props.label}</button>;
}
`.trim();

    const report = buildRoundTripDiffReport({
      reportId: 'diff-report-import-shape',
      model: decompiled.model,
      originalSources: [{ relativePath: 'src/Button.tsx', content: SOURCE }],
      generatedSources: [{ relativePath: 'src/Button.tsx', content: generated }],
    });

    expect(report.diffs[0]?.semanticallyEquivalent).toBe(false);
  });

  it('marks JSX event-handler changes as not semantically equivalent', () => {
    const original = `
      export function Clickable(props: { readonly onPress: () => void }) {
        return <button onClick={props.onPress}>Go</button>;
      }
    `;
    const decompiled = decompileTsx({
      label: 'diff-test-event-shape',
      modelId: 'diff-model-event-shape',
      files: [
        {
          relativePath: 'src/Clickable.tsx',
          content: original,
        },
      ],
    });

    const generated = `
      export function Clickable(props: { readonly onPress: () => void }) {
        return <button onMouseEnter={props.onPress}>Go</button>;
      }
    `;

    const report = buildRoundTripDiffReport({
      reportId: 'diff-report-event-shape',
      model: decompiled.model,
      originalSources: [{ relativePath: 'src/Clickable.tsx', content: original }],
      generatedSources: [{ relativePath: 'src/Clickable.tsx', content: generated }],
    });

    expect(report.diffs[0]?.semanticallyEquivalent).toBe(false);
  });

  it('marks style token and data-binding changes as not semantically equivalent', () => {
    const original = `
      export function Card(props: { readonly title: string; readonly detail: string }) {
        return <section className="card token-primary">{props.title}:{props.detail}</section>;
      }
    `;
    const decompiled = decompileTsx({
      label: 'diff-test-style-shape',
      modelId: 'diff-model-style-shape',
      files: [{ relativePath: 'src/Card.tsx', content: original }],
    });

    const generated = `
      export function Card(props: { readonly title: string; readonly detail: string }) {
        return <section className="card token-secondary">{props.title}</section>;
      }
    `;

    const report = buildRoundTripDiffReport({
      reportId: 'diff-report-style-shape',
      model: decompiled.model,
      originalSources: [{ relativePath: 'src/Card.tsx', content: original }],
      generatedSources: [{ relativePath: 'src/Card.tsx', content: generated }],
    });

    expect(report.diffs[0]?.semanticallyEquivalent).toBe(false);
  });

  it('includes generated artifact validation results in the round-trip report', () => {
    const decompiled = decompileTsx({
      label: 'diff-test-validation',
      modelId: 'diff-model-validation',
      files: [{ relativePath: 'src/Button.tsx', content: SOURCE }],
    });
    const compiled = compileReact(decompiled.model);
    const validation = createNotRunValidationPipelineResult({
      targetId: decompiled.model.modelId,
      reason: 'Validation runner was not invoked in this test.',
    });

    const report = buildRoundTripDiffReport({
      reportId: 'diff-report-validation',
      model: decompiled.model,
      originalSources: [{ relativePath: 'src/Button.tsx', content: SOURCE }],
      generatedSources: compiled.emittedFiles,
      validation,
    });

    expect(report.validation).toEqual(validation);
    expect(report.isLossless).toBe(false);
    expect(report.validation?.findings[0]?.code).toBe('validation/not-run');
    expect(report.paritySections?.find((section) => section.kind === 'validation')).toMatchObject({
      status: 'warning',
      findings: [expect.stringContaining('validation/not-run')],
    });
  });

  it('surfaces design-token parity drift as a structured warning section', () => {
    const original = `
      export function Card() {
        return <section className="card token-primary">Card</section>;
      }
    `;
    const decompiled = decompileTsx({
      label: 'diff-test-token-section',
      modelId: 'diff-model-token-section',
      files: [{ relativePath: 'src/Card.tsx', content: original }],
    });

    const report = buildRoundTripDiffReport({
      reportId: 'diff-report-token-section',
      model: decompiled.model,
      originalSources: [{ relativePath: 'src/Card.tsx', content: original }],
      generatedSources: [{
        relativePath: 'src/Card.tsx',
        content: `
          export function Card() {
            return <section className="card token-secondary">Card</section>;
          }
        `,
      }],
    });

    expect(report.paritySections?.find((section) => section.kind === 'design-token')).toMatchObject({
      status: 'warning',
      findings: [
        'Design token reference "token-primary" is missing from generated sources.',
        'Design token reference "token-secondary" was introduced in generated sources.',
      ],
    });
  });
});
