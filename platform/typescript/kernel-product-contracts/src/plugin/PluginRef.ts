/**
 * PluginRef - reference to a plugin implementation.
 *
 * @doc.type interface
 * @doc.purpose Reference to a plugin implementation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

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

export const PluginRefSchema = z
  .object({
    pluginId: z.string().trim().min(1),
    kind: z.string().trim().min(1),
    enabled: z.boolean(),
    contractPath: z.string().trim().min(1),
  })
  .strict();

export function validatePluginRef(value: unknown): value is PluginRef {
  return PluginRefSchema.safeParse(value).success;
}
