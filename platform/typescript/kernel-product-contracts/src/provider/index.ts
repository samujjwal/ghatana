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

export * from "./ProviderRef";
export * from "./KernelProvider";
export * from "./RegistryProvider";
export * from "./SourceProvider";
export * from "./ToolchainProvider";
export * from "./ArtifactProvider";
export * from "./DeploymentProvider";
export * from "./EnvironmentProvider";
export * from "./SecretsProvider";
export * from "./TelemetryProvider";
export * from "./ApprovalProvider";
export * from "./HealthProvider";
export * from "./GateProvider";
