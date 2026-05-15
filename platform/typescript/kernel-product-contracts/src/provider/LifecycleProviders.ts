/**
 * Lifecycle provider contracts for bootstrap and platform Kernel modes.
 *
 * @doc.type module
 * @doc.purpose Provider mode and lifecycle truth provider contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import type { KernelLifecycleEvent } from "../events/KernelLifecycleEvent";
import type { ProductLifecyclePhase } from "../lifecycle/ProductLifecyclePhase";
import type { ApprovalDecision, ApprovalRequest } from "./ApprovalProvider.js";
import type { GateProvider } from "./GateProvider.js";
import type { KernelProvider } from "./KernelProvider.js";
import type { RegistryProvider } from "./RegistryProvider.js";
import type { SourceProvider } from "./SourceProvider.js";

export const KERNEL_PROVIDER_MODES = ["bootstrap", "platform"] as const;

export type KernelProviderMode = (typeof KERNEL_PROVIDER_MODES)[number];

export interface LifecycleProviderWriteOptions {
  readonly required: boolean;
  readonly correlationId: string;
  readonly privacyClassification?: LifecycleEvidencePrivacyClassification;
  readonly retention?: LifecycleRetentionMetadata;
}

export interface LifecycleProviderResult {
  readonly success: boolean;
  readonly ref?: string;
  readonly error?: string;
}

export interface LifecycleProviderQuery {
  readonly productUnitId: string;
  readonly runId?: string;
  readonly correlationId?: string;
  readonly limit?: number;
  readonly cursor?: string;
}

export interface LifecycleArtifactManifestRef {
  readonly productUnitId: string;
  readonly runId: string;
  readonly manifestPath: string;
  readonly artifactCount: number;
  readonly correlationId?: string;
  readonly digestStatus?: "complete" | "partial" | "missing";
}

export interface LifecycleHealthSnapshotRef {
  readonly productUnitId: string;
  readonly runId: string;
  readonly status: string;
  readonly snapshotPath: string;
  readonly snapshotAt?: string;
  readonly correlationId?: string;
  readonly reasonCode?: string;
  readonly privacyClassification?: LifecycleEvidencePrivacyClassification;
  readonly retention?: LifecycleRetentionMetadata;
}

export type LifecycleEvidencePrivacyClassification =
  | "public"
  | "internal"
  | "confidential"
  | "restricted";

export interface LifecycleRetentionMetadata {
  readonly policyId: string;
  readonly retentionDays: number;
  readonly expiresAt?: string;
  readonly legalHold?: boolean;
}

export interface LifecycleProvenanceRecord {
  readonly provenanceId: string;
  readonly productUnitId: string;
  readonly runId: string;
  readonly source: string;
  readonly evidenceRefs: readonly string[];
  readonly correlationId?: string;
  readonly privacyClassification?: LifecycleEvidencePrivacyClassification;
  readonly retention?: LifecycleRetentionMetadata;
  readonly recordedAt: string;
}

export interface LifecycleMemoryRecord {
  readonly memoryId: string;
  readonly productUnitId: string;
  readonly runId: string;
  readonly kind: string;
  readonly contentRef: string;
  readonly privacyClassification?: LifecycleEvidencePrivacyClassification;
  readonly retention?: LifecycleRetentionMetadata;
  readonly recordedAt: string;
}

export interface LifecycleRuntimeTruthSnapshot {
  readonly productUnitId: string;
  readonly runId: string;
  readonly phase: ProductLifecyclePhase;
  readonly status: string;
  readonly observedAt: string;
  readonly evidenceRefs: readonly string[];
  readonly correlationId?: string;
  readonly providerMode?: KernelProviderMode;
  readonly privacyClassification?: LifecycleEvidencePrivacyClassification;
  readonly retention?: LifecycleRetentionMetadata;
}

export interface LifecycleEventProvider extends KernelProvider {
  appendEvent(
    event: KernelLifecycleEvent,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  listEvents(query: LifecycleProviderQuery): Promise<readonly KernelLifecycleEvent[]>;
}

export interface LifecycleArtifactProvider extends KernelProvider {
  recordArtifactManifest(
    manifest: LifecycleArtifactManifestRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  listArtifactManifests(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleArtifactManifestRef[]>;
}

export interface LifecycleHealthProvider extends KernelProvider {
  recordHealthSnapshot(
    snapshot: LifecycleHealthSnapshotRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  getLatestHealthSnapshot(
    productUnitId: string
  ): Promise<LifecycleHealthSnapshotRef | null>;
}

export interface LifecycleApprovalProvider extends KernelProvider {
  requestLifecycleApproval(
    request: ApprovalRequest,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  decideLifecycleApproval(
    decision: ApprovalDecision,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;
}

export interface LifecycleProvenanceProvider extends KernelProvider {
  recordProvenance(
    record: LifecycleProvenanceRecord,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  listProvenance(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleProvenanceRecord[]>;
}

export interface LifecycleMemoryProvider extends KernelProvider {
  recordMemory(
    record: LifecycleMemoryRecord,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  listMemory(query: LifecycleProviderQuery): Promise<readonly LifecycleMemoryRecord[]>;
}

export interface LifecycleRuntimeTruthProvider extends KernelProvider {
  recordRuntimeTruth(
    snapshot: LifecycleRuntimeTruthSnapshot,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult>;

  getRuntimeTruth(
    productUnitId: string
  ): Promise<LifecycleRuntimeTruthSnapshot | null>;
}

export interface KernelLifecycleProviderContext {
  readonly mode: KernelProviderMode;
  readonly registryProvider?: RegistryProvider;
  readonly sourceProvider?: SourceProvider;
  readonly events?: LifecycleEventProvider;
  readonly artifacts?: LifecycleArtifactProvider;
  readonly health?: LifecycleHealthProvider;
  readonly approvals?: LifecycleApprovalProvider;
  readonly gates?: Record<string, GateProvider>;
  readonly provenance?: LifecycleProvenanceProvider;
  readonly memory?: LifecycleMemoryProvider;
  readonly runtimeTruth?: LifecycleRuntimeTruthProvider;
}

export const KernelProviderModeRequirements = {
  bootstrap: [
    "events",
    "artifacts",
    "health",
    "approvals",
    "provenance",
    "runtimeTruth",
  ],
  platform: [
    "events",
    "artifacts",
    "health",
    "approvals",
    "provenance",
    "memory",
    "runtimeTruth",
  ],
} as const satisfies Record<
  KernelProviderMode,
  readonly (keyof Omit<
    KernelLifecycleProviderContext,
    "mode" | "gates" | "registryProvider" | "sourceProvider"
  >)[]
>;

export type KernelLifecycleProviderName =
  (typeof KernelProviderModeRequirements)[KernelProviderMode][number];

export type KernelLifecycleProviderContextReasonCode =
  | "missing-provider"
  | "invalid-provider-mode";

export interface KernelLifecycleProviderContextValidationResult {
  readonly valid: boolean;
  readonly missingProviders: readonly KernelLifecycleProviderName[];
  readonly mode?: KernelProviderMode;
  readonly reasonCodes: readonly KernelLifecycleProviderContextReasonCode[];
}

export function isKernelProviderMode(value: unknown): value is KernelProviderMode {
  return (
    typeof value === "string" &&
    KERNEL_PROVIDER_MODES.includes(value as KernelProviderMode)
  );
}

export function validateKernelLifecycleProviderContext(
  context: KernelLifecycleProviderContext
): KernelLifecycleProviderContextValidationResult {
  if (!isKernelProviderMode(context.mode)) {
    return {
      valid: false,
      missingProviders: [],
      reasonCodes: ["invalid-provider-mode"],
    };
  }

  const requiredProviders = KernelProviderModeRequirements[context.mode];
  const missingProviders = requiredProviders.filter(
    (providerName) => context[providerName] === undefined
  );

  return {
    valid: missingProviders.length === 0,
    missingProviders,
    mode: context.mode,
    reasonCodes: missingProviders.length > 0 ? ["missing-provider"] : [],
  };
}

export function requireLifecycleProvider<TProvider extends KernelProvider>(
  context: KernelLifecycleProviderContext,
  providerName: keyof Omit<KernelLifecycleProviderContext, "mode">
): TProvider {
  const provider = context[providerName];
  if (provider === undefined) {
    throw new Error(
      `Kernel ${context.mode} mode requires lifecycle provider: ${String(providerName)}`
    );
  }
  return provider as unknown as TProvider;
}

export function requireLifecycleProviderSet(
  context: KernelLifecycleProviderContext,
  providerNames: readonly KernelLifecycleProviderName[]
): void {
  const missingProviders = providerNames.filter(
    (providerName) => context[providerName] === undefined
  );
  if (missingProviders.length > 0) {
    throw new Error(
      `Kernel ${context.mode} mode requires lifecycle providers: ${missingProviders.join(", ")}`
    );
  }
}
