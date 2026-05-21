import { describe, expect, it } from "vitest";
import {
  isImplementationStatus,
  isProductSurfaceBuildSystem,
  isProductSurfaceLanguage,
  isProductUnitSurfaceType,
  type ProductSurfaceBuildSystem,
  type ProductSurfaceLanguage,
  type ProductUnitSurfaceType,
} from "../ProductUnitSurface";

describe("ProductUnitSurface", () => {
  it("accepts every canonical surface type", () => {
    const surfaceTypes: readonly ProductUnitSurfaceType[] = [
      "backend-api",
      "web",
      "worker",
      "operator",
      "portal",
      "sdk",
      "mobile",
      "mobile-ios",
      "mobile-android",
      "cli",
      "domain-pack",
      "plugin",
      "agent-runtime",
      "data-pipeline",
    ];

    for (const surfaceType of surfaceTypes) {
      expect(isProductUnitSurfaceType(surfaceType)).toBe(true);
    }
  });

  it("rejects unknown surface types", () => {
    expect(isProductUnitSurfaceType("desktop")).toBe(false);
    expect(isProductUnitSurfaceType("")).toBe(false);
  });

  it("accepts known implementation statuses and rejects unknown values", () => {
    expect(isImplementationStatus("implemented")).toBe(true);
    expect(isImplementationStatus("planned")).toBe(true);
    expect(isImplementationStatus("backend-only")).toBe(true);
    expect(isImplementationStatus("experimental")).toBe(true);
    expect(isImplementationStatus("unknown")).toBe(false);
  });

  it("accepts canonical implementation languages and rejects unknown values", () => {
    const languages: readonly ProductSurfaceLanguage[] = [
      "java",
      "typescript",
      "javascript",
      "rust",
      "python",
      "swift",
      "kotlin",
      "go",
      "other",
    ];

    for (const language of languages) {
      expect(isProductSurfaceLanguage(language)).toBe(true);
    }
    expect(isProductSurfaceLanguage("ruby")).toBe(false);
  });

  it("accepts canonical build systems and rejects unknown values", () => {
    const buildSystems: readonly ProductSurfaceBuildSystem[] = [
      "gradle",
      "pnpm",
      "cargo",
      "pyproject",
      "xcode",
      "maven",
      "docker",
      "compose",
      "none",
      "other",
    ];

    for (const buildSystem of buildSystems) {
      expect(isProductSurfaceBuildSystem(buildSystem)).toBe(true);
    }
    expect(isProductSurfaceBuildSystem("make-it-so")).toBe(false);
  });
});
