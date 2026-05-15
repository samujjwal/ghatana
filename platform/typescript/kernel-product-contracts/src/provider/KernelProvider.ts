/**
 * KernelProvider - base provider interface for all Kernel providers.
 *
 * @doc.type interface
 * @doc.purpose Base provider interface for Kernel provider implementations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

/**
 * Base interface for all Kernel providers.
 * All specific provider interfaces should extend this base interface.
 */
export interface KernelProvider {
  /**
   * Provider identifier.
   */
  readonly providerId: string;

  /**
   * Provider version.
   */
  readonly version: string;

  /**
   * Provider capabilities.
   */
  readonly capabilities: readonly string[];

  /**
   * Provider backing store type.
   * Indicates the underlying storage mechanism for the provider.
   */
  readonly backingStore: "file" | "data-cloud" | "external";
}
