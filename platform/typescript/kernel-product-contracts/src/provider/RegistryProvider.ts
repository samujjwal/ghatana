/**
 * RegistryProvider - interface for reading product metadata and lifecycle configuration.
 *
 * @doc.type interface
 * @doc.purpose Registry provider interface for ProductUnit metadata
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";
import type { ProductUnit } from "../product-unit/ProductUnit.js";
import type { ProductUnitIntent } from "../product-unit/ProductUnitIntent.js";
import { ProductUnitIntentTypeSchema } from "../product-unit/ProductUnitIntent.js";

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

export const ProductUnitIntentApplyOptionsSchema = z
  .object({
    allowWrite: z.boolean(),
  })
  .strict();

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

export const ProductUnitIntentPreviewResultSchema = z
  .object({
    valid: z.boolean(),
    correlationId: z.string().trim().min(1),
    errors: z.array(z.string()),
    warnings: z.array(z.string()),
    operation: ProductUnitIntentTypeSchema,
    productUnitId: z.string().trim().min(1),
    registryPath: z.string().trim().min(1),
    before: z.unknown(),
    after: z.unknown(),
    diff: z.array(z.string()),
  })
  .strict();

/**
 * Result of applying a ProductUnitIntent.
 */
export interface ProductUnitIntentApplyResult extends ProductUnitIntentPreviewResult {
  /**
   * Whether the intent was actually applied.
   */
  readonly applied: boolean;
}

export const ProductUnitIntentApplyResultSchema =
  ProductUnitIntentPreviewResultSchema.extend({
    applied: z.boolean(),
  });

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

export function validateProductUnitIntentApplyOptions(
  value: unknown
): value is ProductUnitIntentApplyOptions {
  return ProductUnitIntentApplyOptionsSchema.safeParse(value).success;
}

export function validateProductUnitIntentPreviewResult(
  value: unknown
): value is ProductUnitIntentPreviewResult {
  return ProductUnitIntentPreviewResultSchema.safeParse(value).success;
}

export function validateProductUnitIntentApplyResult(
  value: unknown
): value is ProductUnitIntentApplyResult {
  return ProductUnitIntentApplyResultSchema.safeParse(value).success;
}
