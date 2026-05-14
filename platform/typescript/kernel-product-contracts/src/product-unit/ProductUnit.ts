/**
 * ProductUnit - the fundamental abstraction for any governable, deployable artifact in Kernel.
 *
 * A ProductUnit represents a complete product or system that can undergo lifecycle operations.
 * It is product-neutral and can represent monorepo products, external repositories, services,
 * web apps, mobile apps, SDKs, plugins, domain packs, data pipelines, and agent runtimes.
 *
 * @doc.type interface
 * @doc.purpose Core ProductUnit abstraction for lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern AggregateRoot
 */

import type { ProductUnitKind } from "./ProductUnitKind";
import type { ProductUnitSurface } from "./ProductUnitSurface";

// Re-export ProductUnitSurface for convenience
export type { ProductUnitSurface };

/**
 * Reference to a provider implementation.
 */
export interface ProviderRef {
  /**
   * Provider identifier (e.g., "ghatana-file-registry", "github", "aws-codepipeline").
   */
  readonly providerId: string;

  /**
   * Optional provider-specific configuration.
   */
  readonly config?: Record<string, unknown>;
}

/**
 * Lifecycle status of a ProductUnit.
 */
export type LifecycleStatus = "disabled" | "planned" | "partial" | "enabled";

/**
 * ProductUnit conformance requirements.
 */
export interface ProductUnitConformance {
  /**
   * Required conformance checks.
   */
  readonly requiredChecks: readonly string[];

  /**
   * Conformance level (e.g., "basic", "standard", "strict").
   */
  readonly level: string;

  /**
   * Exemptions from specific checks.
   */
  readonly exemptions?: readonly string[];
}

/**
 * ProductUnit governance configuration.
 */
export interface ProductUnitGovernance {
  /**
   * Required approval gates.
   */
  readonly approvalGates?: readonly string[];

  /**
   * Required verification gates.
   */
  readonly verificationGates?: readonly string[];

  /**
   * Security requirements.
   */
  readonly securityRequirements?: readonly string[];

  /**
   * Privacy requirements.
   */
  readonly privacyRequirements?: readonly string[];
}

/**
 * Represents a complete ProductUnit that can undergo lifecycle operations.
 */
export interface ProductUnit {
  /**
   * Schema version for ProductUnit contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Unique identifier for the ProductUnit.
   */
  readonly id: string;

  /**
   * Human-readable name of the ProductUnit.
   */
  readonly name: string;

  /**
   * Kind of ProductUnit (determines governance and lifecycle treatment).
   */
  readonly kind: ProductUnitKind;

  /**
   * Owner or team responsible for the ProductUnit.
   */
  readonly owner?: string;

  /**
   * Reference to the registry provider for this ProductUnit.
   */
  readonly registryProviderRef: ProviderRef;

  /**
   * Reference to the source provider for this ProductUnit.
   */
  readonly sourceProviderRef: ProviderRef;

  /**
   * Deployable surfaces within this ProductUnit.
   */
  readonly surfaces: readonly ProductUnitSurface[];

  /**
   * Lifecycle profile name (e.g., "standard-web-api-product").
   */
  readonly lifecycleProfile?: string;

  /**
   * Current lifecycle execution status.
   */
  readonly lifecycleStatus?: LifecycleStatus;

  /**
   * Conformance requirements for this ProductUnit.
   */
  readonly conformance?: ProductUnitConformance;

  /**
   * Governance configuration for this ProductUnit.
   */
  readonly governance?: ProductUnitGovernance;

  /**
   * Additional metadata for the ProductUnit.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Type guard to check if an object is a valid ProductUnit.
 */
export function isProductUnit(value: unknown): value is ProductUnit {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const pu = value as Record<string, unknown>;

  return (
    pu.schemaVersion === "1.0.0" &&
    typeof pu.id === "string" &&
    typeof pu.name === "string" &&
    typeof pu.kind === "string" &&
    typeof pu.registryProviderRef === "object" &&
    pu.registryProviderRef !== null &&
    typeof pu.sourceProviderRef === "object" &&
    pu.sourceProviderRef !== null &&
    Array.isArray(pu.surfaces)
  );
}

/**
 * Creates a minimal ProductUnit for testing or stub purposes.
 */
export function createMinimalProductUnit(
  id: string,
  name: string,
  kind: ProductUnitKind
): ProductUnit {
  return {
    schemaVersion: "1.0.0",
    id,
    name,
    kind,
    registryProviderRef: { providerId: "ghatana-file-registry" },
    sourceProviderRef: { providerId: "ghatana-file-registry" },
    surfaces: [],
  };
}
