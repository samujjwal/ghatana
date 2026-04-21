import { describe, it, expect } from "vitest";
import {
  generateCssVariables,
  getCssVariables,
  createCssVariableMap,
} from "../css";
import { tokens } from "../registry";

describe("CSS variable generation", () => {
  describe("generateCssVariables", () => {
    it("should generate CSS custom properties from tokens", () => {
      const css = generateCssVariables(tokens);
      expect(css).toContain("--gh-");
      expect(css).toContain(":root");
      expect(css).toContain("{");
      expect(css).toContain("}");
    });

    it("should include color tokens", () => {
      const css = generateCssVariables(tokens);
      expect(css).toContain("--gh-colors-palette-primary-500");
    });

    it("should include spacing tokens", () => {
      const css = generateCssVariables(tokens);
      expect(css).toContain("--gh-spacing-xs");
    });

    it("should include typography tokens", () => {
      const css = generateCssVariables(tokens);
      expect(css).toContain("--gh-typography-font-family-base");
    });

    it("should respect custom selector option", () => {
      const css = generateCssVariables(tokens, { selector: ":host" });
      expect(css).toContain(":host {");
      expect(css).not.toContain(":root {");
    });

    it("should include comments when requested", () => {
      const css = generateCssVariables(tokens, { includeComments: true });
      expect(css).toContain("/*");
    });

    it("should respect custom prefix", () => {
      const css = generateCssVariables(tokens, { prefix: "custom" });
      expect(css).toContain("--custom-");
    });
  });

  describe("getCssVariables", () => {
    it("should return CSS string with default tokens", () => {
      const css = getCssVariables();
      expect(css).toContain("--gh-");
      expect(css).toContain(":root");
    });

    it("should accept options parameter", () => {
      const css = getCssVariables({ selector: ":host" });
      expect(css).toContain(":host {");
    });
  });

  describe("createCssVariableMap", () => {
    it("should create a map from token paths to CSS variable names", () => {
      const map = createCssVariableMap(tokens);
      expect(typeof map).toBe("object");
      expect(Object.keys(map).length).toBeGreaterThan(0);
    });

    it("should have --gh- prefix for all keys by default", () => {
      const map = createCssVariableMap(tokens);
      Object.keys(map).forEach((key) => {
        expect(key).toMatch(/^--gh-/);
      });
    });

    it("should respect custom prefix", () => {
      const map = createCssVariableMap(tokens, { prefix: "custom" });
      Object.keys(map).forEach((key) => {
        expect(key).toMatch(/^--custom-/);
      });
    });

    it("should have non-empty string or number values", () => {
      const map = createCssVariableMap(tokens);
      Object.values(map).forEach((value) => {
        expect(["string", "number"]).toContain(typeof value);
        expect(value).toBeTruthy();
      });
    });
  });
});
