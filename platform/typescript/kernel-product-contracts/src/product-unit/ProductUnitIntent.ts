/**
 * ProductUnitIntent - public contract for requesting ProductUnit creation or update.
 *
 * This interface allows YAPPC and other creators to request ProductUnit creation/update
 * without mutating Kernel internals. It provides a clean boundary between product creators
 * and the Kernel platform.
 *
 * @doc.type interface
 * @doc.purpose Public contract for ProductUnit creation/update requests
 * @doc.layer kernel-product-contracts
 * @doc.pattern Command
 */

import type { ProductUnitKind } from "./ProductUnitKind";
import { isProductUnitKind } from "./ProductUnitKind";
import type { ProductUnitSurface } from "./ProductUnitSurface";
import {
  isImplementationStatus,
  isProductUnitSurfaceType,
} from "./ProductUnitSurface";

/**
 * The type of producer that created the ProductUnitIntent.
 */
export type ProducerType = "yappc" | "api" | "cli" | "manual" | "external";

/**
 * A draft ProductUnit for creation or update.
 */
export interface ProductUnitDraft {
  /**
   * Unique identifier for the ProductUnit.
   */
  readonly id: string;

  /**
   * Human-readable name of the ProductUnit.
   */
  readonly name: string;

  /**
   * Kind of ProductUnit.
   */
  readonly kind: ProductUnitKind;

  /**
   * Owner or team responsible for the ProductUnit.
   */
  readonly owner?: string;

  /**
   * Deployable surfaces within this ProductUnit.
   */
  readonly surfaces: readonly ProductUnitSurface[];

  /**
   * Lifecycle profile name.
   */
  readonly lifecycleProfile?: string;

  /**
   * Additional metadata for the ProductUnit.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Target providers for the ProductUnit.
 */
export interface TargetProviders {
  /**
   * Registry provider identifier.
   */
  readonly registryProvider: string;

  /**
   * Source provider identifier.
   */
  readonly sourceProvider: string;
}

/**
 * Producer information for the intent.
 */
export interface Producer {
  /**
   * Producer identifier.
   */
  readonly id: string;

  /**
   * Type of producer.
   */
  readonly type: ProducerType;

  /**
   * Correlation identifier propagated from the producing system.
   */
  readonly correlationId: string;
}

/**
 * Requested lifecycle configuration.
 */
export interface RequestedLifecycle {
  /**
   * Lifecycle profile name.
   */
  readonly profile: string;

  /**
   * Whether to enable lifecycle execution.
   */
  readonly enableExecution: boolean;
}

/**
 * Intent to create or update a ProductUnit.
 *
 * This is the public contract for YAPPC and other creators to request ProductUnit
 * operations without mutating Kernel internals.
 */
export interface ProductUnitIntent {
  /**
   * Schema version for ProductUnitIntent contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Unique identifier for this intent.
   */
  readonly intentId: string;

  /**
   * Producer that created this intent.
   */
  readonly producer: Producer;

  /**
   * Target providers for the ProductUnit.
   */
  readonly target: TargetProviders;

  /**
   * Draft ProductUnit to create or update.
   */
  readonly productUnit: ProductUnitDraft;

  /**
   * Optional requested lifecycle configuration.
   */
  readonly requestedLifecycle?: RequestedLifecycle;

  /**
   * Optional governance hints for Kernel gate/provider selection.
   */
  readonly governanceHints?: Record<string, unknown>;

  /**
   * Optional provenance information. Must not contain raw secrets.
   */
  readonly provenance?: Record<string, unknown>;
}

const PRODUCER_TYPES: readonly ProducerType[] = [
  "yappc",
  "api",
  "cli",
  "manual",
  "external",
];

const SECRET_KEY_PATTERN = /(secret|password|token|api[-_]?key|credential)/i;

export interface ProductUnitIntentValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

function hasSecretLikeField(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some((item) => hasSecretLikeField(item));
  }
  if (typeof value !== "object" || value === null) {
    return false;
  }

  return Object.entries(value as Record<string, unknown>).some(([key, nested]) => {
    if (SECRET_KEY_PATTERN.test(key)) {
      return true;
    }
    return hasSecretLikeField(nested);
  });
}

export function validateProductUnitIntent(
  value: unknown
): ProductUnitIntentValidationResult {
  const errors: string[] = [];

  if (typeof value !== "object" || value === null) {
    return { valid: false, errors: ["ProductUnitIntent must be an object"] };
  }

  const intent = value as Record<string, unknown>;
  const producer = intent.producer as Record<string, unknown> | undefined;
  const target = intent.target as Record<string, unknown> | undefined;
  const productUnit = intent.productUnit as Record<string, unknown> | undefined;

  if (intent.schemaVersion !== "1.0.0") {
    errors.push('schemaVersion must be "1.0.0"');
  }
  if (typeof intent.intentId !== "string" || intent.intentId.trim().length === 0) {
    errors.push("intentId must be a non-empty string");
  }
  if (typeof producer !== "object" || producer === null) {
    errors.push("producer must be an object");
  } else {
    if (typeof producer.id !== "string" || producer.id.trim().length === 0) {
      errors.push("producer.id must be a non-empty string");
    }
    if (!PRODUCER_TYPES.includes(producer.type as ProducerType)) {
      errors.push("producer.type is not supported");
    }
    if (
      typeof producer.correlationId !== "string" ||
      producer.correlationId.trim().length === 0
    ) {
      errors.push("producer.correlationId must be a non-empty string");
    }
  }
  if (typeof target !== "object" || target === null) {
    errors.push("target must be an object");
  } else {
    if (
      typeof target.registryProvider !== "string" ||
      target.registryProvider.trim().length === 0
    ) {
      errors.push("target.registryProvider must be a non-empty string");
    }
    if (
      typeof target.sourceProvider !== "string" ||
      target.sourceProvider.trim().length === 0
    ) {
      errors.push("target.sourceProvider must be a non-empty string");
    }
  }
  if (typeof productUnit !== "object" || productUnit === null) {
    errors.push("productUnit must be an object");
  } else {
    if (!isProductUnitKind(productUnit.kind)) {
      errors.push("productUnit.kind is not a known ProductUnit kind");
    }
    if (!Array.isArray(productUnit.surfaces) || productUnit.surfaces.length === 0) {
      errors.push("productUnit.surfaces must contain at least one surface");
    } else {
      productUnit.surfaces.forEach((surface, index) => {
        if (typeof surface !== "object" || surface === null) {
          errors.push(`productUnit.surfaces[${index}] must be an object`);
          return;
        }
        const surfaceRecord = surface as Record<string, unknown>;
        if (!isProductUnitSurfaceType(surfaceRecord.type)) {
          errors.push(`productUnit.surfaces[${index}].type is not supported`);
        }
        if (!isImplementationStatus(surfaceRecord.implementationStatus)) {
          errors.push(
            `productUnit.surfaces[${index}].implementationStatus is not supported`
          );
        }
      });
    }
  }
  if (hasSecretLikeField(value)) {
    errors.push("ProductUnitIntent must not include raw secret-like fields");
  }

  return { valid: errors.length === 0, errors };
}

/**
 * Type guard to check if an object is a valid ProductUnitIntent.
 */
export function isProductUnitIntent(value: unknown): value is ProductUnitIntent {
  return validateProductUnitIntent(value).valid;
}
