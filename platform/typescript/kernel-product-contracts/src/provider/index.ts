/**
 * Provider contracts for Kernel lifecycle operations.
 *
 * This module exports all provider interfaces that Kernel uses to abstract
 * operations across different product shapes and ecosystems.
 *
 * @doc.type module
 * @doc.purpose Provider contracts for Kernel lifecycle operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Module
 */

export * from "./ProviderRef.js";
export * from "./KernelProvider.js";
export * from "./RegistryProvider.js";
export * from "./SourceProvider.js";
export * from "./ToolchainProvider.js";
export * from "./ArtifactProvider.js";
export * from "./DeploymentProvider.js";
export * from "./EnvironmentProvider.js";
export * from "./SecretsProvider.js";
export * from "./TelemetryProvider.js";
export * from "./ApprovalProvider.js";
export * from "./HealthProvider.js";
export * from "./GateProvider.js";
export * from "./LifecycleProviders.js";
