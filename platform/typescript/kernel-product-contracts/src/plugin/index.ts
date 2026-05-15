/**
 * Plugin contracts for Kernel operations.
 *
 * This module exports plugin-related contracts that Kernel uses for
 * plugin-based extensibility and governance.
 *
 * @doc.type module
 * @doc.purpose Plugin contracts for Kernel operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Module
 */

export * from "./PluginKind.js";
export * from "./KernelPlugin.js";
export * from "./PluginRef.js";
export * from "./KernelPluginCapability.js";
export * from "./KernelPluginBinding.js";
export * from "./KernelPluginHealthSnapshot.js";
export * from "./KernelPluginLifecycleHook.js";
export * from "./KernelPluginGateResult.js";
