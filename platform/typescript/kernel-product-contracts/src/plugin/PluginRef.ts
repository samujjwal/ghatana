/**
 * PluginRef - reference to a plugin implementation.
 *
 * @doc.type interface
 * @doc.purpose Reference to a plugin implementation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Plugin reference.
 */
export interface PluginRef {
  /**
   * Plugin identifier.
   */
  readonly pluginId: string;

  /**
   * Plugin kind.
   */
  readonly kind: string;

  /**
   * Whether the plugin is enabled.
   */
  readonly enabled: boolean;

  /**
   * Path to the plugin contract implementation.
   */
  readonly contractPath: string;
}
