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
import { isProductUnitKind } from "./ProductUnitKind";
import type { ProductUnitSurface } from "./ProductUnitSurface";
import {
  isImplementationStatus,
  isProductUnitSurfaceType,
} from "./ProductUnitSurface";
import type { ProviderRef } from "../provider/ProviderRef";

// Re-export ProductUnitSurface for convenience
export type { ProductUnitSurface };
export type { ProviderRef };

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

export interface ProductUnitValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

function hasProviderId(value: unknown): value is ProviderRef {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const providerRef = value as Record<string, unknown>;
  return (
    typeof providerRef.providerId === "string" &&
    providerRef.providerId.trim().length > 0
  );
}

function validateSurface(value: unknown, index: number): readonly string[] {
  const errors: string[] = [];
  if (typeof value !== "object" || value === null) {
    return [`surfaces[${index}] must be an object`];
  }

  const surface = value as Record<string, unknown>;
  if (typeof surface.id !== "string" || surface.id.trim().length === 0) {
    errors.push(`surfaces[${index}].id must be a non-empty string`);
  }
  if (!isProductUnitSurfaceType(surface.type)) {
    errors.push(`surfaces[${index}].type is not a known ProductUnit surface type`);
  }
  if (!isImplementationStatus(surface.implementationStatus)) {
    errors.push(
      `surfaces[${index}].implementationStatus is not a known implementation status`
    );
  }

  return errors;
}

export function validateProductUnit(value: unknown): ProductUnitValidationResult {
  const errors: string[] = [];

  if (typeof value !== "object" || value === null) {
    return { valid: false, errors: ["ProductUnit must be an object"] };
  }

  const pu = value as Record<string, unknown>;

  if (pu.schemaVersion !== "1.0.0") {
    errors.push('schemaVersion must be "1.0.0"');
  }
  if (typeof pu.id !== "string" || pu.id.trim().length === 0) {
    errors.push("id must be a non-empty string");
  }
  if (typeof pu.name !== "string" || pu.name.trim().length === 0) {
    errors.push("name must be a non-empty string");
  }
  if (!isProductUnitKind(pu.kind)) {
    errors.push("kind is not a known ProductUnit kind");
  }
  if (!hasProviderId(pu.registryProviderRef)) {
    errors.push("registryProviderRef.providerId must be a non-empty string");
  }
  if (!hasProviderId(pu.sourceProviderRef)) {
    errors.push("sourceProviderRef.providerId must be a non-empty string");
  }
  if (!Array.isArray(pu.surfaces)) {
    errors.push("surfaces must be an array");
  } else {
    pu.surfaces.forEach((surface, index) => {
      errors.push(...validateSurface(surface, index));
    });
  }
  if (pu.lifecycleStatus === "enabled" && typeof pu.lifecycleProfile !== "string") {
    errors.push("enabled lifecycle requires lifecycleProfile");
  }

  return { valid: errors.length === 0, errors };
}

/**
 * Type guard to check if an object is a valid ProductUnit.
 */
export function isProductUnit(value: unknown): value is ProductUnit {
  return validateProductUnit(value).valid;
}

/**
 * Creates a valid minimal ProductUnit skeleton for callers that need to seed a ProductUnit.
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
