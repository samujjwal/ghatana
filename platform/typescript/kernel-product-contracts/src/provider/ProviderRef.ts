/**
 * ProviderRef - reference to a provider implementation.
 *
 * @doc.type interface
 * @doc.purpose Provider reference for ProductUnit configuration
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

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
