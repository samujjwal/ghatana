/**
 * KernelPluginLifecycleHook - represents a lifecycle hook point for plugins.
 *
 * Lifecycle hooks allow plugins to subscribe to and act on specific
 * lifecycle events such as build completion, deployment, or verification.
 *
 * @doc.type type
 * @doc.purpose Plugin lifecycle hook type definition
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * Lifecycle hook points that plugins can subscribe to.
 */
export type KernelPluginLifecycleHook =
  | "onProductRegistered"
  | "onProductBootstrapped"
  | "onProductDevStarted"
  | "onProductValidated"
  | "onProductTested"
  | "onProductBuildStarted"
  | "onProductBuildCompleted"
  | "onProductPackaged"
  | "onProductDeployStarted"
  | "onProductDeployed"
  | "onProductVerified"
  | "onProductPromoted"
  | "onProductRolledBack"
  | "onProductRetired";

/**
 * Type guard to check if a string is a valid KernelPluginLifecycleHook.
 */
export function isKernelPluginLifecycleHook(value: unknown): value is KernelPluginLifecycleHook {
  const validHooks: readonly KernelPluginLifecycleHook[] = [
    "onProductRegistered",
    "onProductBootstrapped",
    "onProductDevStarted",
    "onProductValidated",
    "onProductTested",
    "onProductBuildStarted",
    "onProductBuildCompleted",
    "onProductPackaged",
    "onProductDeployStarted",
    "onProductDeployed",
    "onProductVerified",
    "onProductPromoted",
    "onProductRolledBack",
    "onProductRetired",
  ];
  return typeof value === "string" && validHooks.includes(value as KernelPluginLifecycleHook);
}

/**
 * Gets a human-readable label for a lifecycle hook.
 */
export function getLifecycleHookLabel(hook: KernelPluginLifecycleHook): string {
  const labels: Record<KernelPluginLifecycleHook, string> = {
    onProductRegistered: "Product Registered",
    onProductBootstrapped: "Product Bootstrapped",
    onProductDevStarted: "Dev Started",
    onProductValidated: "Product Validated",
    onProductTested: "Product Tested",
    onProductBuildStarted: "Build Started",
    onProductBuildCompleted: "Build Completed",
    onProductPackaged: "Product Packaged",
    onProductDeployStarted: "Deploy Started",
    onProductDeployed: "Product Deployed",
    onProductVerified: "Product Verified",
    onProductPromoted: "Product Promoted",
    onProductRolledBack: "Product Rolled Back",
    onProductRetired: "Product Retired",
  };
  return labels[hook];
}
