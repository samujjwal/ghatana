/**
 * KernelProviderHealthMatrix - aggregate health matrix for all Kernel providers.
 *
 * This contract defines the health matrix that aggregates health status from
 * all registered Kernel providers (registry, source, events, artifacts, approvals,
 * health, provenance, memory, runtime-truth, gates, telemetry, environment, secrets).
 *
 * @doc.type module
 * @doc.purpose Aggregate health matrix for Kernel provider health monitoring
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// ProviderCapability
// ---------------------------------------------------------------------------

/**
 * Capabilities that a provider can advertise.
 */
export const PROVIDER_CAPABILITIES = [
  "registry-read",
  "registry-write",
  "source-acquisition",
  "lifecycle-events",
  "artifact-storage",
  "artifact-fingerprinting",
  "approval-workflows",
  "health-snapshots",
  "provenance-tracking",
  "memory-storage",
  "runtime-truth",
  "gate-evaluation",
  "telemetry-emission",
  "environment-provisioning",
  "secrets-management",
] as const;

export type ProviderCapability = (typeof PROVIDER_CAPABILITIES)[number];

/**
 * Provider capability declaration with negotiation metadata.
 */
export interface ProviderCapabilityDeclaration {
  /**
   * Capability identifier.
   */
  readonly capability: ProviderCapability;

  /**
   * Whether this capability is available in the current environment.
   */
  readonly available: boolean;

  /**
   * Whether this capability is required for the provider to function.
   */
  readonly required: boolean;

  /**
   * Optional version or level of the capability.
   */
  readonly version?: string | undefined;

  /**
   * Optional constraints or limitations of this capability.
   */
  readonly constraints?: string[] | undefined;
}

// ---------------------------------------------------------------------------
// ProviderHealthStatus
// ---------------------------------------------------------------------------

/**
 * Individual provider health status.
 */
export type ProviderHealthStatus = "healthy" | "degraded" | "unhealthy" | "unknown";

const PROVIDER_HEALTH_STATUSES = [
  "healthy",
  "degraded",
  "unhealthy",
  "unknown",
] as const satisfies readonly ProviderHealthStatus[];

export const ProviderHealthStatusSchema = z.enum(PROVIDER_HEALTH_STATUSES);

/**
 * Health status of a single provider.
 */
export interface ProviderHealthEntry {
  /**
   * Provider identifier.
   */
  readonly providerId: string;

  /**
   * Provider kind (registry, source, events, artifacts, etc.).
   */
  readonly providerKind: string;

  /**
   * Current health status.
   */
  readonly status: ProviderHealthStatus;

  /**
   * ISO timestamp when this status was last checked.
   */
  readonly checkedAt: string;

  /**
   * Human-readable message describing the status.
   */
  readonly message: string;

  /**
   * Capabilities declared by this provider.
   */
  readonly capabilities: ProviderCapabilityDeclaration[];

  /**
   * Optional error details if status is unhealthy or degraded.
   */
  readonly error?: string | undefined;

  /**
   * Optional latency in milliseconds for the health check.
   */
  readonly latencyMs?: number | undefined;
}

// ---------------------------------------------------------------------------
// KernelProviderHealthMatrix
// ---------------------------------------------------------------------------

/**
 * Aggregate health matrix for all Kernel providers.
 */
export interface KernelProviderHealthMatrixContract {
  /**
   * Schema version for contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * ISO timestamp when this matrix was generated.
   */
  readonly generatedAt: string;

  /**
   * Overall health status across all providers.
   */
  readonly overallStatus: ProviderHealthStatus;

  /**
   * Total number of providers in the matrix.
   */
  readonly totalProviders: number;

  /**
   * Number of healthy providers.
   */
  readonly healthyProviders: number;

  /**
   * Number of degraded providers.
   */
  readonly degradedProviders: number;

  /**
   * Number of unhealthy providers.
   */
  readonly unhealthyProviders: number;

  /**
   * Number of providers with unknown status.
   */
  readonly unknownProviders: number;

  /**
   * Individual provider health entries.
   */
  readonly providers: ProviderHealthEntry[];

  /**
   * Provider mode (bootstrap or platform).
   */
  readonly providerMode: "bootstrap" | "platform";

  /**
   * Required capabilities that are missing or unhealthy.
   */
  readonly missingCapabilities: ProviderCapability[];

  /**
   * Optional correlation identifier for tracing.
   */
  readonly correlationId?: string | undefined;
}

// ---------------------------------------------------------------------------
// Schemas
// ---------------------------------------------------------------------------

export const ProviderCapabilityDeclarationSchema = z.object({
  capability: z.enum(PROVIDER_CAPABILITIES),
  available: z.boolean(),
  required: z.boolean(),
  version: z.string().optional(),
  constraints: z.array(z.string()).optional(),
}).strict();

export const ProviderHealthEntrySchema = z.object({
  providerId: z.string().min(1),
  providerKind: z.string().min(1),
  status: ProviderHealthStatusSchema,
  checkedAt: z.string().datetime(),
  message: z.string().min(1),
  capabilities: z.array(ProviderCapabilityDeclarationSchema),
  error: z.string().optional(),
  latencyMs: z.number().int().nonnegative().optional(),
}).strict();

export const KernelProviderHealthMatrixSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  generatedAt: z.string().datetime(),
  overallStatus: ProviderHealthStatusSchema,
  totalProviders: z.number().int().nonnegative(),
  healthyProviders: z.number().int().nonnegative(),
  degradedProviders: z.number().int().nonnegative(),
  unhealthyProviders: z.number().int().nonnegative(),
  unknownProviders: z.number().int().nonnegative(),
  providers: z.array(ProviderHealthEntrySchema),
  providerMode: z.enum(["bootstrap", "platform"]),
  missingCapabilities: z.array(z.enum(PROVIDER_CAPABILITIES)),
  correlationId: z.string().optional(),
}).strict();

export type KernelProviderHealthMatrix = z.infer<typeof KernelProviderHealthMatrixSchema>;

export const KernelProviderHealthMatrixContractSchema =
  KernelProviderHealthMatrixSchema;

/**
 * Parse a KernelProviderHealthMatrix from unknown input.
 */
export function parseKernelProviderHealthMatrix(
  input: unknown
): KernelProviderHealthMatrix {
  return KernelProviderHealthMatrixSchema.parse(input);
}

/**
 * Type guard to check if a value is a valid KernelProviderHealthMatrix.
 */
export function isKernelProviderHealthMatrix(
  value: unknown
): value is KernelProviderHealthMatrix {
  try {
    KernelProviderHealthMatrixSchema.parse(value);
    return true;
  } catch {
    return false;
  }
}

export function validateProviderHealthStatus(
  value: unknown
): value is ProviderHealthStatus {
  return ProviderHealthStatusSchema.safeParse(value).success;
}

export function validateKernelProviderHealthMatrixContract(
  value: unknown
): value is KernelProviderHealthMatrixContract {
  return KernelProviderHealthMatrixContractSchema.safeParse(value).success;
}
