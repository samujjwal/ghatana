/**
 * PluginKind - kind of plugin in the Kernel lifecycle.
 *
 * @doc.type type
 * @doc.purpose Plugin kind enumeration
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

export const PLUGIN_KINDS = [
  "pre-phase",
  "post-phase",
  "pre-gate",
  "post-gate",
  "pre-deployment",
  "post-deployment",
  "platform-plugin",
  "product-plugin",
] as const;

/**
 * Plugin kind - determines when the plugin executes in the lifecycle.
 */
export type PluginKind = (typeof PLUGIN_KINDS)[number];

export const PluginKindSchema = z.enum(PLUGIN_KINDS);

export function validatePluginKind(value: unknown): value is PluginKind {
  return PluginKindSchema.safeParse(value).success;
}
