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

export * from "./PluginKind";
export * from "./KernelPlugin";
export * from "./PluginRef";
export * from "./KernelPluginCapability";
export * from "./KernelPluginBinding";
export * from "./KernelPluginHealthSnapshot";
export * from "./KernelPluginLifecycleHook";
export * from "./KernelPluginGateResult";
