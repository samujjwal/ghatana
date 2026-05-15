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
  readonly sourceRef?: string;

  /**
   * Current implementation status of the surface.
   */
  readonly implementationStatus: ImplementationStatus;

  /**
   * Runtime environment for the surface (e.g., "nodejs", "java", "python").
   */
  readonly runtime?: string;

  /**
   * Package path for the surface (e.g., in a monorepo).
   */
  readonly packagePath?: string;

  /**
   * Gradle module identifier for the surface (if applicable).
   */
  readonly gradleModule?: string;

  /**
   * Adapter hint for lifecycle operations (e.g., "gradle-java-service", "pnpm-vite-react").
   */
  readonly adapterHint?: string;
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
