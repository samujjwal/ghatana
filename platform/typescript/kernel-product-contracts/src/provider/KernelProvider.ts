/**
 * KernelProvider - base provider interface for all Kernel providers.
 *
 * @doc.type interface
 * @doc.purpose Base provider interface for Kernel provider implementations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";

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

export const KernelProviderClassificationSchema = z.enum(
  KERNEL_PROVIDER_CLASSIFICATIONS
);

export const KERNEL_PROVIDER_CAPABILITIES = [
  "registry",
  "source",
  "toolchain",
  "artifact",
  "deployment",
  "environment",
  "secrets",
  "telemetry",
  "approval",
  "health",
  "gate",
  "lifecycle-event",
  "provenance",
  "memory",
  "runtime-truth",
  "policy-evidence",
] as const;

export type KernelProviderCapability =
  (typeof KERNEL_PROVIDER_CAPABILITIES)[number];

export const KernelProviderCapabilitySchema = z.enum(
  KERNEL_PROVIDER_CAPABILITIES
);

export const KernelProviderSchema = z
  .object({
    providerId: z.string().trim().min(1),
    version: z.string().trim().min(1),
    capabilities: z.array(z.string().trim().min(1)),
    backingStore: z.enum(["file", "data-cloud", "external"]),
  })
  .passthrough();

export interface ProviderModeCapabilityRequirement {
  readonly providerId: string;
  readonly mode: "bootstrap" | "platform";
  readonly capabilities: readonly KernelProviderCapability[];
  readonly requiredForPhases: readonly string[];
  readonly failClosed: boolean;
  readonly healthCheckRequired: boolean;
  readonly classification?: KernelProviderClassification | undefined;
}

export const ProviderModeCapabilityRequirementSchema = z
  .object({
    providerId: z.string().trim().min(1),
    mode: z.enum(["bootstrap", "platform"]),
    capabilities: z.array(KernelProviderCapabilitySchema),
    requiredForPhases: z.array(z.string().trim().min(1)),
    failClosed: z.boolean(),
    healthCheckRequired: z.boolean(),
    classification: KernelProviderClassificationSchema.optional(),
  })
  .strict();

export function validateKernelProvider(value: unknown): value is KernelProvider {
  return KernelProviderSchema.safeParse(value).success;
}

export function validateKernelProviderClassification(
  value: unknown
): value is KernelProviderClassification {
  return KernelProviderClassificationSchema.safeParse(value).success;
}

export function validateKernelProviderCapability(
  value: unknown
): value is KernelProviderCapability {
  return KernelProviderCapabilitySchema.safeParse(value).success;
}

export function validateProviderModeCapabilityRequirement(
  value: unknown
): value is ProviderModeCapabilityRequirement {
  return ProviderModeCapabilityRequirementSchema.safeParse(value).success;
}
