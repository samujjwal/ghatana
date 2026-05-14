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
import type { ProductUnitSurface } from "./ProductUnitSurface";

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
}

/**
 * Type guard to check if an object is a valid ProductUnitIntent.
 */
export function isProductUnitIntent(value: unknown): value is ProductUnitIntent {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const intent = value as Record<string, unknown>;

  return (
    intent.schemaVersion === "1.0.0" &&
    typeof intent.intentId === "string" &&
    typeof intent.producer === "object" &&
    intent.producer !== null &&
    typeof intent.target === "object" &&
    intent.target !== null &&
    typeof intent.productUnit === "object" &&
    intent.productUnit !== null
  );
}
