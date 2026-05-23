import { describe, expect, it } from "vitest";
import {
  isImplementationStatus,
  isProductSurfaceBuildSystem,
  isProductSurfaceLanguage,
  isProductSurfaceRuntime,
  isProductUnitSurfaceType,
  isValidLanguageRuntimeBuildSystemCombination,
  validateSurfaceTriplet,
  type ProductSurfaceBuildSystem,
  type ProductSurfaceLanguage,
  type ProductSurfaceRuntime,
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

  it("accepts canonical runtime environments and rejects unknown values", () => {
    const runtimes: readonly ProductSurfaceRuntime[] = [
      "java-jre",
      "java-jdk",
      "nodejs",
      "nodejs-bun",
      "python",
      "python-uv",
      "rust-native",
      "rust-wasm",
      "go",
      "swift",
      "kotlin-jvm",
      "kotlin-native",
      "docker-container",
      "docker-compose",
      "browser",
      "mobile-ios",
      "mobile-android",
      "cli-native",
      "none",
      "other",
    ];

    for (const runtime of runtimes) {
      expect(isProductSurfaceRuntime(runtime)).toBe(true);
    }
    expect(isProductSurfaceRuntime("ruby-runtime")).toBe(false);
  });

  describe("language/runtime/buildSystem combination validation", () => {
    it("accepts valid Java combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "java-jre", "gradle")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "java-jdk", "gradle")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "java-jre", "maven")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "java-jdk", "maven")).toBe(true);
    });

    it("rejects invalid Java combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "nodejs", "gradle")).toBe(false);
      expect(isValidLanguageRuntimeBuildSystemCombination("java", "java-jre", "pnpm")).toBe(false);
    });

    it("accepts valid TypeScript/JavaScript combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("typescript", "nodejs", "pnpm")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("typescript", "nodejs-bun", "pnpm")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("typescript", "browser", "pnpm")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("javascript", "nodejs", "pnpm")).toBe(true);
    });

    it("rejects invalid TypeScript/JavaScript combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("typescript", "java-jre", "pnpm")).toBe(false);
      expect(isValidLanguageRuntimeBuildSystemCombination("typescript", "nodejs", "gradle")).toBe(false);
    });

    it("accepts valid Rust combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("rust", "rust-native", "cargo")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("rust", "rust-wasm", "cargo")).toBe(true);
    });

    it("rejects invalid Rust combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("rust", "rust-native", "gradle")).toBe(false);
      expect(isValidLanguageRuntimeBuildSystemCombination("rust", "nodejs", "cargo")).toBe(false);
    });

    it("accepts valid Python combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("python", "python", "pyproject")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("python", "python-uv", "pyproject")).toBe(true);
    });

    it("rejects invalid Python combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("python", "python", "gradle")).toBe(false);
      expect(isValidLanguageRuntimeBuildSystemCombination("python", "nodejs", "pyproject")).toBe(false);
    });

    it("accepts valid Swift combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("swift", "mobile-ios", "xcode")).toBe(true);
    });

    it("rejects invalid Swift combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("swift", "mobile-android", "xcode")).toBe(false);
    });

    it("accepts valid Kotlin combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("kotlin", "kotlin-jvm", "gradle")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("kotlin", "kotlin-jvm", "maven")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("kotlin", "kotlin-native", "gradle")).toBe(true);
    });

    it("rejects invalid Kotlin combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("kotlin", "kotlin-jvm", "cargo")).toBe(false);
    });

    it("accepts valid Go combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("go", "cli-native", "none")).toBe(true);
    });

    it("rejects invalid Go combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("go", "cli-native", "gradle")).toBe(false);
    });

    it("accepts Docker-based combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("other", "docker-container", "docker")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("other", "docker-compose", "compose")).toBe(true);
    });

    it("accepts 'other' for experimental combinations", () => {
      expect(isValidLanguageRuntimeBuildSystemCombination("other", "other", "other")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("other", "java-jre", "gradle")).toBe(true);
      expect(isValidLanguageRuntimeBuildSystemCombination("other", "rust-native", "other")).toBe(true);
    });

    it("provides detailed validation errors", () => {
      const result = validateSurfaceTriplet("java", "nodejs", "gradle");
      expect(result.valid).toBe(false);
      expect(result.error).toContain("Invalid combination");
      expect(result.error).toContain('language="java"');
      expect(result.error).toContain('runtime="nodejs"');
      expect(result.error).toContain('buildSystem="gradle"');
    });

    it("validates successful combinations", () => {
      const result = validateSurfaceTriplet("java", "java-jre", "gradle");
      expect(result.valid).toBe(true);
      expect(result.error).toBeUndefined();
    });
  });
});
