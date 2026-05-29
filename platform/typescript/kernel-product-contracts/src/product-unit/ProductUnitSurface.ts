/**
 * ProductUnitSurface - represents a deployable component within a ProductUnit.
 *
 * A ProductUnit can have multiple surfaces (e.g., backend-api, web, mobile). Each surface
 * has its own build configuration, runtime, and adapter for lifecycle operations.
 *
 * @doc.type interface
 * @doc.purpose Deployable component representation for lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

/**
 * The type of a ProductUnit surface, determining its build and deployment characteristics.
 */
export type ProductUnitSurfaceType =
  | "backend-api"
  | "web"
  | "worker"
  | "operator"
  | "portal"
  | "sdk"
  | "mobile"
  | "mobile-ios"
  | "mobile-android"
  | "cli"
  | "domain-pack"
  | "plugin"
  | "agent-runtime"
  | "data-pipeline";

export const PRODUCT_UNIT_SURFACE_TYPES = [
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
] as const satisfies readonly ProductUnitSurfaceType[];

export const ProductUnitSurfaceTypeSchema = z.enum(PRODUCT_UNIT_SURFACE_TYPES);

/**
 * The implementation status of a ProductUnit surface.
 */
export type ImplementationStatus =
  | "implemented"
  | "planned"
  | "backend-only"
  | "experimental";

export const IMPLEMENTATION_STATUSES = [
  "implemented",
  "planned",
  "backend-only",
  "experimental",
] as const satisfies readonly ImplementationStatus[];

export const ImplementationStatusSchema = z.enum(IMPLEMENTATION_STATUSES);

/**
 * Canonical implementation language identifiers for Kernel-managed surfaces.
 */
export type ProductSurfaceLanguage =
  | "java"
  | "typescript"
  | "javascript"
  | "rust"
  | "python"
  | "swift"
  | "kotlin"
  | "go"
  | "other";

/**
 * Language version constraints for precise toolchain specification.
 * Examples: "21" for Java 21, "3.11" for Python 3.11, "20" for Node.js 20.
 */
export type LanguageVersion = string;

export const LanguageVersionSchema = z.string().trim().min(1);

/**
 * Runtime version constraints for precise environment specification.
 * Examples: "21" for Java JRE 21, "20.10.0" for Node.js 20.10.0, "3.11" for Python 3.11.
 */
export type RuntimeVersion = string;

export const RuntimeVersionSchema = z.string().trim().min(1);

/**
 * Build system version constraints for precise toolchain specification.
 * Examples: "8.5" for Gradle 8.5, "1.75" for Cargo 1.75, "9.0" for pnpm 9.0.
 */
export type BuildSystemVersion = string;

export const BuildSystemVersionSchema = z.string().trim().min(1);

export const PRODUCT_SURFACE_LANGUAGES = [
  "java",
  "typescript",
  "javascript",
  "rust",
  "python",
  "swift",
  "kotlin",
  "go",
  "other",
] as const satisfies readonly ProductSurfaceLanguage[];

export const ProductSurfaceLanguageSchema = z.enum(PRODUCT_SURFACE_LANGUAGES);

/**
 * Canonical build system identifiers used for lifecycle adapter selection.
 */
export type ProductSurfaceBuildSystem =
  | "gradle"
  | "pnpm"
  | "cargo"
  | "pyproject"
  | "xcode"
  | "maven"
  | "docker"
  | "compose"
  | "none"
  | "other";

export const PRODUCT_SURFACE_BUILD_SYSTEMS = [
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
] as const satisfies readonly ProductSurfaceBuildSystem[];

export const ProductSurfaceBuildSystemSchema = z.enum(
  PRODUCT_SURFACE_BUILD_SYSTEMS
);

/**
 * Canonical runtime environment identifiers for Kernel-managed surfaces.
 * These represent the execution environment where the surface runs.
 */
export type ProductSurfaceRuntime =
  | "java-jre"
  | "java-jdk"
  | "nodejs"
  | "nodejs-bun"
  | "python"
  | "python-uv"
  | "rust-native"
  | "rust-wasm"
  | "go"
  | "swift"
  | "kotlin-jvm"
  | "kotlin-native"
  | "docker-container"
  | "docker-compose"
  | "browser"
  | "mobile-ios"
  | "mobile-android"
  | "cli-native"
  | "none"
  | "other";

export const PRODUCT_SURFACE_RUNTIMES = [
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
] as const satisfies readonly ProductSurfaceRuntime[];

export const ProductSurfaceRuntimeSchema = z.enum(PRODUCT_SURFACE_RUNTIMES);

/**
 * Represents a deployable component within a ProductUnit.
 */
export interface ProductUnitSurface {
  /**
   * Unique identifier for the surface within the ProductUnit.
   */
  readonly id: string;

  /**
   * The type of the surface, determining build and deployment characteristics.
   */
  readonly type: ProductUnitSurfaceType;

  /**
   * Optional reference to the source code location (e.g., Git ref, path).
   */
  readonly sourceRef?: string | undefined;

  /**
   * Current implementation status of the surface.
   */
  readonly implementationStatus: ImplementationStatus;

  /**
   * Primary implementation language for this surface.
   * Required for implemented surfaces to enable polyglot adapter selection.
   */
  readonly language?: ProductSurfaceLanguage | undefined;

  /**
   * Optional language version constraint (e.g., "21" for Java 21, "3.11" for Python 3.11).
   * Used by adapters to validate toolchain compatibility and ensure reproducible builds.
   */
  readonly languageVersion?: LanguageVersion | undefined;

  /**
   * Runtime environment for the surface (e.g., "nodejs", "java-jre", "python").
   * Required for implemented surfaces to enable proper lifecycle execution.
   */
  readonly runtime?: ProductSurfaceRuntime | undefined;

  /**
   * Optional runtime version constraint (e.g., "21" for Java JRE 21, "20.10.0" for Node.js 20.10.0).
   * Used by adapters to validate runtime compatibility and ensure consistent execution environments.
   */
  readonly runtimeVersion?: RuntimeVersion | undefined;

  /**
   * Build system used by the lifecycle adapter for this surface.
   * Required for implemented surfaces to enable toolchain adapter selection.
   */
  readonly buildSystem?: ProductSurfaceBuildSystem | undefined;

  /**
   * Optional build system version constraint (e.g., "8.5" for Gradle 8.5, "1.75" for Cargo 1.75).
   * Used by adapters to validate build tool compatibility and ensure reproducible builds.
   */
  readonly buildSystemVersion?: BuildSystemVersion | undefined;

  /**
   * Package path for the surface (e.g., in a monorepo).
   */
  readonly packagePath?: string | undefined;

  /**
   * Gradle module identifier for the surface (if applicable).
   */
  readonly gradleModule?: string | undefined;

  /**
   * Cargo crate directory or Cargo.toml path for Rust surfaces.
   */
  readonly cratePath?: string | undefined;

  /**
   * Explicit Cargo.toml path for Rust surfaces.
   */
  readonly cargoToml?: string | undefined;

  /**
   * Python package directory or pyproject.toml path for Python surfaces.
   */
  readonly pyprojectPath?: string | undefined;

  /**
   * Adapter hint for lifecycle operations (e.g., "gradle-java-service", "pnpm-vite-react").
   */
  readonly adapterHint?: string | undefined;
}

/**
 * Type guard to check if a string is a valid ProductUnitSurfaceType.
 */
export function isProductUnitSurfaceType(
  value: unknown
): value is ProductUnitSurfaceType {
  return typeof value === "string" && PRODUCT_UNIT_SURFACE_TYPES.includes(value as ProductUnitSurfaceType);
}

/**
 * Type guard to check if a string is a valid ImplementationStatus.
 */
export function isImplementationStatus(
  value: unknown
): value is ImplementationStatus {
  return typeof value === "string" && IMPLEMENTATION_STATUSES.includes(value as ImplementationStatus);
}

export function isProductSurfaceLanguage(
  value: unknown
): value is ProductSurfaceLanguage {
  return typeof value === "string" && PRODUCT_SURFACE_LANGUAGES.includes(value as ProductSurfaceLanguage);
}

export function isProductSurfaceBuildSystem(
  value: unknown
): value is ProductSurfaceBuildSystem {
  return typeof value === "string" && PRODUCT_SURFACE_BUILD_SYSTEMS.includes(value as ProductSurfaceBuildSystem);
}

export function isProductSurfaceRuntime(
  value: unknown
): value is ProductSurfaceRuntime {
  return typeof value === "string" && PRODUCT_SURFACE_RUNTIMES.includes(value as ProductSurfaceRuntime);
}

/**
 * Valid combinations of language, runtime, and build system.
 * This validation ensures that polyglot surfaces use compatible toolchains.
 */
export interface LanguageRuntimeBuildSystemTriplet {
  readonly language: ProductSurfaceLanguage;
  readonly runtime: ProductSurfaceRuntime;
  readonly buildSystem: ProductSurfaceBuildSystem;
}

export const LanguageRuntimeBuildSystemTripletSchema = z
  .object({
    language: ProductSurfaceLanguageSchema,
    runtime: ProductSurfaceRuntimeSchema,
    buildSystem: ProductSurfaceBuildSystemSchema,
  })
  .strict()
  .superRefine((triplet, context) => {
    if (
      !isValidLanguageRuntimeBuildSystemCombination(
        triplet.language,
        triplet.runtime,
        triplet.buildSystem
      )
    ) {
      context.addIssue({
        code: "custom",
        message:
          "language/runtime/buildSystem combination is not supported by Kernel lifecycle adapters",
      });
    }
  });

/**
 * Validates that a language/runtime/buildSystem combination is valid.
 * Returns true if the combination is supported by the Kernel.
 */
export function isValidLanguageRuntimeBuildSystemCombination(
  language: ProductSurfaceLanguage,
  runtime: ProductSurfaceRuntime,
  buildSystem: ProductSurfaceBuildSystem
): boolean {
  // Java combinations
  if (language === "java") {
    return (
      (runtime === "java-jre" || runtime === "java-jdk") &&
      (buildSystem === "gradle" || buildSystem === "maven")
    );
  }

  // TypeScript/JavaScript combinations
  if (language === "typescript" || language === "javascript") {
    if (runtime === "nodejs" || runtime === "nodejs-bun") {
      return buildSystem === "pnpm" || buildSystem === "none";
    }
    if (runtime === "browser") {
      return buildSystem === "pnpm" || buildSystem === "none";
    }
    return false;
  }

  // Rust combinations
  if (language === "rust") {
    if (runtime === "rust-native" || runtime === "rust-wasm") {
      return buildSystem === "cargo";
    }
    return false;
  }

  // Python combinations
  if (language === "python") {
    if (runtime === "python" || runtime === "python-uv") {
      return buildSystem === "pyproject";
    }
    return false;
  }

  // Swift combinations
  if (language === "swift") {
    if (runtime === "mobile-ios") {
      return buildSystem === "xcode";
    }
    return false;
  }

  // Kotlin combinations
  if (language === "kotlin") {
    if (runtime === "kotlin-jvm") {
      return buildSystem === "gradle" || buildSystem === "maven";
    }
    if (runtime === "kotlin-native") {
      return buildSystem === "gradle";
    }
    return false;
  }

  // Go combinations
  if (language === "go") {
    if (runtime === "cli-native") {
      return buildSystem === "none";
    }
    return false;
  }

  // Docker-based combinations (language-agnostic)
  if (runtime === "docker-container" || runtime === "docker-compose") {
    return buildSystem === "docker" || buildSystem === "compose";
  }

  // Allow "other" for experimental or custom combinations
  if (language === "other" || runtime === "other" || buildSystem === "other") {
    return true;
  }

  return false;
}

/**
 * Validation result for surface triplet validation.
 */
export interface SurfaceTripletValidationResult {
  readonly valid: boolean;
  readonly error?: string;
}

export const SurfaceTripletValidationResultSchema = z
  .object({
    valid: z.boolean(),
    error: z.string().trim().min(1).optional(),
  })
  .strict();

/**
 * Validates a surface's language/runtime/buildSystem triplet.
 */
export function validateSurfaceTriplet(
  language: ProductSurfaceLanguage,
  runtime: ProductSurfaceRuntime,
  buildSystem: ProductSurfaceBuildSystem
): SurfaceTripletValidationResult {
  if (!isValidLanguageRuntimeBuildSystemCombination(language, runtime, buildSystem)) {
    return {
      valid: false,
      error: `Invalid combination: language="${language}", runtime="${runtime}", buildSystem="${buildSystem}". ` +
        `This combination is not supported by the Kernel lifecycle adapters.`,
    };
  }
  return { valid: true };
}

export function validateLanguageVersion(value: unknown): value is LanguageVersion {
  return LanguageVersionSchema.safeParse(value).success;
}

export function validateRuntimeVersion(value: unknown): value is RuntimeVersion {
  return RuntimeVersionSchema.safeParse(value).success;
}

export function validateBuildSystemVersion(
  value: unknown
): value is BuildSystemVersion {
  return BuildSystemVersionSchema.safeParse(value).success;
}

export function validateLanguageRuntimeBuildSystemTriplet(
  value: unknown
): value is LanguageRuntimeBuildSystemTriplet {
  return LanguageRuntimeBuildSystemTripletSchema.safeParse(value).success;
}

export function validateSurfaceTripletValidationResult(
  value: unknown
): value is SurfaceTripletValidationResult {
  return SurfaceTripletValidationResultSchema.safeParse(value).success;
}
