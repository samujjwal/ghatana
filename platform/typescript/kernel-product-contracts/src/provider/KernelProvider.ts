/**
 * KernelProvider - base provider interface for all Kernel providers.
 *
 * @doc.type interface
 * @doc.purpose Base provider interface for Kernel provider implementations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

/**
 * Base interface for all Kernel providers.
 * All specific provider interfaces should extend this base interface.
 */
export interface KernelProvider {
  /**
   * Provider identifier.
   */
  readonly providerId: string;

  /**
   * Provider version.
   */
  readonly version: string;

  /**
   * Provider capabilities.
   */
  readonly capabilities: readonly string[];

  /**
   * Provider backing store type.
   * Indicates the underlying storage mechanism for the provider.
   */
  readonly backingStore: "file" | "data-cloud" | "external";
}

export const KERNEL_PROVIDER_CLASSIFICATIONS = [
  "bootstrap-generic",
  "generic-http-client",
  "data-cloud-bridge",
  "anti-pattern",
] as const;

export type KernelProviderClassification =
  (typeof KERNEL_PROVIDER_CLASSIFICATIONS)[number];

export type KernelProviderCapability =
  | "registry"
  | "source"
  | "toolchain"
  | "artifact"
  | "deployment"
  | "environment"
  | "secrets"
  | "telemetry"
  | "approval"
  | "health"
  | "gate"
  | "lifecycle-event"
  | "provenance"
  | "memory"
  | "runtime-truth"
  | "policy-evidence";

export interface ProviderModeCapabilityRequirement {
  readonly providerId: string;
  readonly mode: "bootstrap" | "platform";
  readonly capabilities: readonly KernelProviderCapability[];
  readonly requiredForPhases: readonly string[];
  readonly failClosed: boolean;
  readonly healthCheckRequired: boolean;
  readonly classification?: KernelProviderClassification | undefined;
}
