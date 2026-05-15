/**
 * RegistryProvider - interface for reading product metadata and lifecycle configuration.
 *
 * @doc.type interface
 * @doc.purpose Registry provider interface for ProductUnit metadata
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider.js";
import type { ProductUnit } from "../product-unit/ProductUnit.js";
import type { ProductUnitIntent } from "../product-unit/ProductUnitIntent.js";

/**
 * Registry provider for reading product metadata and lifecycle configuration.
 */
export interface RegistryProvider extends KernelProvider {
  /**
   * Gets a ProductUnit by its identifier.
   */
  getProductUnit(productUnitId: string): Promise<ProductUnit | null>;

  /**
   * Lists all ProductUnits.
   */
  listProductUnits(): Promise<readonly ProductUnit[]>;

  /**
   * Lists ProductUnits by kind.
   */
  listProductUnitsByKind(kind: string): Promise<readonly ProductUnit[]>;

  /**
   * Validates ProductUnit configuration.
   */
  validateProductUnit(productUnit: ProductUnit): Promise<{
    valid: boolean;
    errors: readonly string[];
  }>;
}

/**
 * Options for applying a ProductUnitIntent.
 */
export interface ProductUnitIntentApplyOptions {
  /**
   * Whether to allow writes to the registry.
   */
  readonly allowWrite: boolean;
}

/**
 * Result of previewing a ProductUnitIntent application.
 */
export interface ProductUnitIntentPreviewResult {
  /**
   * Whether the intent validation passed.
   */
  readonly valid: boolean;

  /**
   * Correlation identifier for tracing.
   */
  readonly correlationId: string;

  /**
   * Validation errors.
   */
  readonly errors: readonly string[];

  /**
   * Validation warnings.
   */
  readonly warnings: readonly string[];

  /**
   * The intent operation type.
   */
  readonly operation: ProductUnitIntent["intentType"];

  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Registry path.
   */
  readonly registryPath: string;

  /**
   * Registry entry before the change.
   */
  readonly before: unknown;

  /**
   * Registry entry after the change.
   */
  readonly after: unknown;

  /**
   * Diff of changes.
   */
  readonly diff: readonly string[];
}

/**
 * Result of applying a ProductUnitIntent.
 */
export interface ProductUnitIntentApplyResult extends ProductUnitIntentPreviewResult {
  /**
   * Whether the intent was actually applied.
   */
  readonly applied: boolean;
}

/**
 * Optional interface for registry providers that support ProductUnitIntent application.
 */
export interface ProductUnitIntentCapableRegistryProvider extends RegistryProvider {
  /**
   * Preview the application of a ProductUnitIntent without mutating the registry.
   */
  previewApplyProductUnitIntent(
    intent: ProductUnitIntent
  ): Promise<ProductUnitIntentPreviewResult>;

  /**
   * Apply a ProductUnitIntent to the registry.
   */
  applyProductUnitIntent(
    intent: ProductUnitIntent,
    options: ProductUnitIntentApplyOptions
  ): Promise<ProductUnitIntentApplyResult>;
}

/**
 * Type guard to check if a registry provider supports ProductUnitIntent application.
 */
export function isProductUnitIntentCapableRegistryProvider(
  value: unknown
): value is ProductUnitIntentCapableRegistryProvider {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const provider = value as Record<string, unknown>;
  return (
    typeof provider.previewApplyProductUnitIntent === "function" &&
    typeof provider.applyProductUnitIntent === "function"
  );
}
