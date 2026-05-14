/**
 * RegistryProvider - interface for reading product metadata and lifecycle configuration.
 *
 * @doc.type interface
 * @doc.purpose Registry provider interface for ProductUnit metadata
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelProvider } from "./KernelProvider";
import type { ProductUnit } from "../product-unit/ProductUnit";

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
