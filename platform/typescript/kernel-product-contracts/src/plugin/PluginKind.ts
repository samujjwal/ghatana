/**
 * PluginKind - kind of plugin in the Kernel lifecycle.
 *
 * @doc.type type
 * @doc.purpose Plugin kind enumeration
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Plugin kind - determines when the plugin executes in the lifecycle.
 */
export type PluginKind =
  | "pre-phase"
  | "post-phase"
  | "pre-gate"
  | "post-gate"
  | "pre-deployment"
  | "post-deployment"
  | "platform-plugin"
  | "product-plugin";
