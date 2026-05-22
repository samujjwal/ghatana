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
    expect(result.findings).toContainEqual(expect.objectContaining({
      code: "TS2304",
      category: "typescript",
      severity: "error",
      sourceRef: expect.objectContaining({
        file: expect.objectContaining({ relativePath: "src/Broken.tsx" }),
      }),
    }));
  });
});
