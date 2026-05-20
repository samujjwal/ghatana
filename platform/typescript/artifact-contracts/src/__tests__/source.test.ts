/**
 * @test.type unit
 * @test.execution <50ms
 * @test.infra none
 */

import { describe, it, expect } from "vitest";
import {
  SourceSpanSchema,
  SourceFileSchema,
  SourceRefSchema,
} from "../source.js";

describe("SourceSpanSchema", () => {
  it("accepts valid span", () => {
    const result = SourceSpanSchema.safeParse({
      startOffset: 0,
      endOffset: 100,
      startLine: 1,
      startColumn: 1,
    });
    expect(result.success).toBe(true);
  });

  it("accepts minimal span (no optional fields)", () => {
    const result = SourceSpanSchema.safeParse({ startOffset: 0, endOffset: 10 });
    expect(result.success).toBe(true);
  });

  it("rejects negative startOffset", () => {
    const result = SourceSpanSchema.safeParse({ startOffset: -1, endOffset: 10 });
    expect(result.success).toBe(false);
  });
});

describe("SourceFileSchema", () => {
  it("accepts valid source file", () => {
    const result = SourceFileSchema.safeParse({
      relativePath: "src/components/Button.tsx",
      contentType: "text/typescript",
      kind: "component",
      language: "typescript",
    });
    expect(result.success).toBe(true);
  });

  it("rejects empty relativePath", () => {
    const result = SourceFileSchema.safeParse({
      relativePath: "",
      contentType: "text/typescript",
      kind: "component",
    });
    expect(result.success).toBe(false);
  });

  it("rejects invalid kind", () => {
    const result = SourceFileSchema.safeParse({
      relativePath: "src/foo.ts",
      contentType: "text/typescript",
      kind: "invalid-kind",
    });
    expect(result.success).toBe(false);
  });

  it("accepts valid SHA-256 contentHash", () => {
    const result = SourceFileSchema.safeParse({
      relativePath: "src/foo.ts",
      contentType: "text/typescript",
      kind: "utility",
      contentHash: "a".repeat(64),
    });
    expect(result.success).toBe(true);
  });

  it("rejects invalid contentHash (not 64 hex chars)", () => {
    const result = SourceFileSchema.safeParse({
      relativePath: "src/foo.ts",
      contentType: "text/typescript",
      kind: "utility",
      contentHash: "short",
    });
    expect(result.success).toBe(false);
  });
});

describe("SourceRefSchema", () => {
  const validRef = {
    repositoryUri: "https://github.com/ghatana/ghatana",
    commitRef: "abc1234",
    file: {
      relativePath: "src/Button.tsx",
      contentType: "text/typescript",
      kind: "component",
    },
  };

  it("accepts valid source ref", () => {
    expect(SourceRefSchema.safeParse(validRef).success).toBe(true);
  });

  it("accepts source ref with span", () => {
    const result = SourceRefSchema.safeParse({
      ...validRef,
      span: { startOffset: 10, endOffset: 50 },
    });
    expect(result.success).toBe(true);
  });

  it("rejects missing repositoryUri", () => {
    const { repositoryUri: _, ...withoutUri } = validRef;
    expect(SourceRefSchema.safeParse(withoutUri).success).toBe(false);
  });
});
