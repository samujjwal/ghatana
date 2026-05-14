/**
 * KernelPluginCapability - represents a capability that a plugin provides.
 *
 * Capabilities are the atomic units of plugin functionality that can be
 * referenced by lifecycle hooks, gates, and health checks.
 *
 * @doc.type interface
 * @doc.purpose Plugin capability representation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Represents a capability that a plugin provides.
 */
export interface KernelPluginCapability {
  /**
   * Unique identifier for the capability (e.g., "security.sast", "policy.evaluate").
   */
  readonly id: string;

  /**
   * Human-readable name for the capability.
   */
  readonly name: string;

  /**
   * Description of what the capability does.
   */
  readonly description: string;

  /**
   * Category of the capability (e.g., "security", "policy", "observability").
   */
  readonly category: string;

  /**
   * Whether the capability is required for the plugin to function.
   */
  readonly required: boolean;

  /**
   * Input schema for the capability (if applicable).
   */
  readonly inputSchema?: Record<string, unknown>;

  /**
   * Output schema for the capability (if applicable).
   */
  readonly outputSchema?: Record<string, unknown>;
}

/**
 * Type guard to check if a value is a KernelPluginCapability.
 */
export function isKernelPluginCapability(value: unknown): value is KernelPluginCapability {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const capability = value as Record<string, unknown>;
  return (
    typeof capability.id === 'string' &&
    typeof capability.name === 'string' &&
    typeof capability.description === 'string' &&
    typeof capability.category === 'string' &&
    typeof capability.required === 'boolean'
  );
}
