/**
 * @fileoverview Tests for generated artifact validation.
 */

import { describe, expect, it } from "vitest";
import { validateGeneratedArtifacts } from "../validate/generated-artifacts.js";

describe("validateGeneratedArtifacts", () => {
  it("passes generated TSX with valid syntax and basic React types", () => {
    const result = validateGeneratedArtifacts({
      targetId: "model-valid",
      now: () => new Date("2026-01-01T00:00:00.000Z"),
      generatedSources: [
        {
          relativePath: "src/Button.tsx",
          content: [
            'import type { ReactElement } from "react";',
            "",
            "export interface ButtonProps {",
            "  readonly label: string;",
            "}",
            "",
            "export function Button(props: ButtonProps): ReactElement {",
            "  return <button>{props.label}</button>;",
            "}",
          ].join("\n"),
        },
      ],
    });

    expect(result.passed).toBe(true);
    expect(result.errorCount).toBe(0);
    expect(result.validatedAt).toBe("2026-01-01T00:00:00.000Z");
  });

  it("reports TypeScript syntax/type diagnostics with source locations", () => {
    const result = validateGeneratedArtifacts({
      targetId: "model-invalid",
      generatedSources: [
        {
          relativePath: "src/Broken.tsx",
          content: [
            'import type { ReactElement } from "react";',
            "",
            "export function Broken(): ReactElement {",
            "  return <button>{missingLabel}</button>;",
            "}",
          ].join("\n"),
        },
      ],
    });

    expect(result.passed).toBe(false);
    expect(result.errorCount).toBeGreaterThan(0);
    expect(result.findings).toContainEqual(
      expect.objectContaining({
        code: "TS2304",
        category: "typescript",
        severity: "error",
        sourceRef: expect.objectContaining({
          file: expect.objectContaining({ relativePath: "src/Broken.tsx" }),
        }),
      }),
    );
  });

  it("merges stage-level CI findings into the validation result", () => {
    const result = validateGeneratedArtifacts({
      targetId: "model-stage-failures",
      generatedSources: [
        {
          relativePath: "src/Okay.tsx",
          content: [
            'import type { ReactElement } from "react";',
            "",
            "export function Okay(): ReactElement {",
            "  return <button>okay</button>;",
            "}",
          ].join("\n"),
        },
      ],
      stageResults: [
        {
          stageId: "lint",
          passed: false,
          summary: "lint stage failed with one violation",
        },
        {
          stageId: "build",
          passed: true,
          summary: "build output emitted to dist",
        },
      ],
    });

    expect(result.passed).toBe(false);
    expect(result.errorCount).toBe(1);
    expect(result.infoCount).toBe(1);
    expect(result.findings).toContainEqual(
      expect.objectContaining({
        code: "stage/lint",
        severity: "error",
        category: "eslint",
      }),
    );
    expect(result.findings).toContainEqual(
      expect.objectContaining({
        code: "stage/build",
        severity: "info",
        category: "build",
      }),
    );
  });

  it("validates cross-file relative imports in generated workspaces", () => {
    const result = validateGeneratedArtifacts({
      targetId: "model-cross-file",
      generatedSources: [
        {
          relativePath: "src/App.tsx",
          content: [
            'import type { ReactElement } from "react";',
            'import { Button } from "./components/Button";',
            "",
            "export function App(): ReactElement {",
            '  return <Button label="Save" />;',
            "}",
          ].join("\n"),
        },
        {
          relativePath: "src/components/Button.tsx",
          content: [
            'import type { ReactElement } from "react";',
            "",
            "export interface ButtonProps { readonly label: string; }",
            "export function Button(props: ButtonProps): ReactElement {",
            "  return <button>{props.label}</button>;",
            "}",
          ].join("\n"),
        },
      ],
    });

    expect(result.passed).toBe(true);
    expect(result.errorCount).toBe(0);
  });

  it("fails generated workspaces with missing relative modules and prop mismatches", () => {
    const result = validateGeneratedArtifacts({
      targetId: "model-cross-file-invalid",
      generatedSources: [
        {
          relativePath: "src/App.tsx",
          content: [
            'import type { ReactElement } from "react";',
            'import { Button } from "./components/Button";',
            'import { Missing } from "./Missing";',
            "",
            "export function App(): ReactElement {",
            "  return <Button count={1}><Missing /></Button>;",
            "}",
          ].join("\n"),
        },
        {
          relativePath: "src/components/Button.tsx",
          content: [
            'import type { ReactElement, ReactNode } from "react";',
            "",
            "export interface ButtonProps { readonly label: string; readonly children?: ReactNode; }",
            "export function Button(props: ButtonProps): ReactElement {",
            "  return <button>{props.label}{props.children}</button>;",
            "}",
          ].join("\n"),
        },
      ],
    });

    expect(result.passed).toBe(false);
    expect(result.findings).toContainEqual(
      expect.objectContaining({
        code: "TS2307",
        severity: "error",
        sourceRef: expect.objectContaining({
          file: expect.objectContaining({ relativePath: "src/App.tsx" }),
        }),
      }),
    );
    expect(result.findings).toContainEqual(
      expect.objectContaining({
        code: "TS2322",
        severity: "error",
        sourceRef: expect.objectContaining({
          file: expect.objectContaining({ relativePath: "src/App.tsx" }),
        }),
      }),
    );
  });
});
