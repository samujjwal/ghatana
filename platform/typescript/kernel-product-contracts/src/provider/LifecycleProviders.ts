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
import { z } from "zod";

export const KERNEL_PROVIDER_MODES = ["bootstrap", "platform"] as const;

export type KernelProviderMode = (typeof KERNEL_PROVIDER_MODES)[number];

export const KernelProviderModeSchema = z.enum(KERNEL_PROVIDER_MODES);

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

export type KernelBridgeProviderMode = KernelProviderMode;

export const KernelBridgeProviderModeSchema = KernelProviderModeSchema;

export type KernelBridgeProviderStatus = "healthy" | "degraded" | "unavailable";

export const KernelBridgeProviderStatusSchema = z.enum([
  "healthy",
  "degraded",
  "unavailable",
]);

export type KernelBridgeProviderErrorCode =
  | "transport"
  | "schema"
  | "tenant-isolation"
  | "unavailable-provider"
  | "retry-exhausted"
  | "duplicate-event"
  | "stale-event";

export const KernelBridgeProviderErrorCodeSchema = z.enum([
  "transport",
  "schema",
  "tenant-isolation",
  "unavailable-provider",
  "retry-exhausted",
  "duplicate-event",
  "stale-event",
]);

export interface KernelBridgeProviderHealthResult {
  readonly providerId: string;
  readonly mode: KernelBridgeProviderMode;
  readonly status: KernelBridgeProviderStatus;
  readonly reason?: string;
  readonly latencyMs?: number;
  readonly lastSuccessAt?: string;
  readonly lastFailureAt?: string;
  readonly evidenceRefs: readonly string[];
}

export interface KernelBridgeProviderError {
  readonly code: KernelBridgeProviderErrorCode;
  readonly providerId: string;
  readonly message: string;
  readonly retryable: boolean;
  readonly correlationId?: string;
  readonly evidenceRefs?: readonly string[];
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

export const LifecycleEvidencePrivacyClassificationSchema = z.enum([
  "public",
  "internal",
  "confidential",
  "restricted",
]);

export interface LifecycleRetentionMetadata {
  readonly policyId: string;
  readonly retentionDays: number;
  readonly expiresAt?: string;
  readonly legalHold?: boolean;
}

export const LifecycleRetentionMetadataSchema = z
  .object({
    policyId: z.string().trim().min(1),
    retentionDays: z.number().int().nonnegative(),
    expiresAt: z.string().datetime({ offset: true }).optional(),
    legalHold: z.boolean().optional(),
  })
  .strict();

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

const LIFECYCLE_PROVIDER_PHASES = [
  "create",
  "bootstrap",
  "dev",
  "validate",
  "test",
  "build",
  "package",
  "release",
  "deploy",
  "verify",
  "promote",
  "rollback",
  "operate",
  "retire",
] as const satisfies readonly ProductLifecyclePhase[];

export const LifecycleProviderWriteOptionsSchema = z
  .object({
    required: z.boolean(),
    correlationId: z.string().trim().min(1),
    privacyClassification: LifecycleEvidencePrivacyClassificationSchema.optional(),
    retention: LifecycleRetentionMetadataSchema.optional(),
  })
  .strict();

export const LifecycleProviderResultSchema = z
  .object({
    success: z.boolean(),
    ref: z.string().trim().min(1).optional(),
    error: z.string().trim().min(1).optional(),
  })
  .strict();

export const LifecycleProviderQuerySchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1).optional(),
    correlationId: z.string().trim().min(1).optional(),
    limit: z.number().int().positive().optional(),
    cursor: z.string().trim().min(1).optional(),
  })
  .strict();

export const LifecycleArtifactManifestRefSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    manifestPath: z.string().trim().min(1),
    artifactCount: z.number().int().nonnegative(),
    correlationId: z.string().trim().min(1).optional(),
    digestStatus: z.enum(["complete", "partial", "missing"]).optional(),
  })
  .strict();

export const LifecycleHealthSnapshotRefSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    status: z.string().trim().min(1),
    snapshotPath: z.string().trim().min(1),
    snapshotAt: z.string().datetime({ offset: true }).optional(),
    correlationId: z.string().trim().min(1).optional(),
    reasonCode: z.string().trim().min(1).optional(),
    privacyClassification: LifecycleEvidencePrivacyClassificationSchema.optional(),
    retention: LifecycleRetentionMetadataSchema.optional(),
  })
  .strict();

export const LifecycleProvenanceRecordSchema = z
  .object({
    provenanceId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    source: z.string().trim().min(1),
    evidenceRefs: z.array(z.string().trim().min(1)),
    correlationId: z.string().trim().min(1).optional(),
    privacyClassification: LifecycleEvidencePrivacyClassificationSchema.optional(),
    retention: LifecycleRetentionMetadataSchema.optional(),
    recordedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecycleMemoryRecordSchema = z
  .object({
    memoryId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    kind: z.string().trim().min(1),
    contentRef: z.string().trim().min(1),
    privacyClassification: LifecycleEvidencePrivacyClassificationSchema.optional(),
    retention: LifecycleRetentionMetadataSchema.optional(),
    recordedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const LifecycleRuntimeTruthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    phase: z.enum(LIFECYCLE_PROVIDER_PHASES),
    status: z.string().trim().min(1),
    observedAt: z.string().datetime({ offset: true }),
    evidenceRefs: z.array(z.string().trim().min(1)),
    correlationId: z.string().trim().min(1).optional(),
    providerMode: KernelProviderModeSchema.optional(),
    privacyClassification: LifecycleEvidencePrivacyClassificationSchema.optional(),
    retention: LifecycleRetentionMetadataSchema.optional(),
  })
  .strict();

export const KernelBridgeProviderHealthResultSchema = z
  .object({
    providerId: z.string().trim().min(1),
    mode: KernelBridgeProviderModeSchema,
    status: KernelBridgeProviderStatusSchema,
    reason: z.string().trim().min(1).optional(),
    latencyMs: z.number().nonnegative().optional(),
    lastSuccessAt: z.string().datetime({ offset: true }).optional(),
    lastFailureAt: z.string().datetime({ offset: true }).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).default([]),
  })
  .strict();

export const KernelBridgeProviderErrorSchema = z
  .object({
    code: KernelBridgeProviderErrorCodeSchema,
    providerId: z.string().trim().min(1),
    message: z.string().trim().min(1),
    retryable: z.boolean(),
    correlationId: z.string().trim().min(1).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export interface LifecycleEventProvider extends KernelProvider {
  appendEvent(
    event: KernelLifecycleEvent,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  listEvents(
    query: LifecycleProviderQuery,
  ): Promise<readonly KernelLifecycleEvent[]>;
}

export interface LifecycleArtifactProvider extends KernelProvider {
  recordArtifactManifest(
    manifest: LifecycleArtifactManifestRef,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  listArtifactManifests(
    query: LifecycleProviderQuery,
  ): Promise<readonly LifecycleArtifactManifestRef[]>;
}

export interface LifecycleHealthProvider extends KernelProvider {
  recordHealthSnapshot(
    snapshot: LifecycleHealthSnapshotRef,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  getLatestHealthSnapshot(
    productUnitId: string,
  ): Promise<LifecycleHealthSnapshotRef | null>;
}

export interface LifecycleApprovalProvider extends KernelProvider {
  requestLifecycleApproval(
    request: ApprovalRequest,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  decideLifecycleApproval(
    decision: ApprovalDecision,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;
}

export interface LifecycleProvenanceProvider extends KernelProvider {
  recordProvenance(
    record: LifecycleProvenanceRecord,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  listProvenance(
    query: LifecycleProviderQuery,
  ): Promise<readonly LifecycleProvenanceRecord[]>;
}

export interface LifecycleMemoryProvider extends KernelProvider {
  recordMemory(
    record: LifecycleMemoryRecord,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  listMemory(
    query: LifecycleProviderQuery,
  ): Promise<readonly LifecycleMemoryRecord[]>;
}

export interface LifecycleRuntimeTruthProvider extends KernelProvider {
  recordRuntimeTruth(
    snapshot: LifecycleRuntimeTruthSnapshot,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;

  getRuntimeTruth(
    productUnitId: string,
  ): Promise<LifecycleRuntimeTruthSnapshot | null>;
}

export type KernelEventProvider = LifecycleEventProvider;
export type KernelArtifactProvider = LifecycleArtifactProvider;
export type KernelHealthProvider = LifecycleHealthProvider;
export type KernelProvenanceProvider = LifecycleProvenanceProvider;
export type KernelRuntimeTruthProvider = LifecycleRuntimeTruthProvider;

export interface KernelTelemetryProvider extends KernelProvider {
  recordTelemetry(
    event: {
      readonly eventId: string;
      readonly eventType: string;
      readonly occurredAt: string;
      readonly correlationId?: string;
      readonly attributes: Readonly<Record<string, unknown>>;
    },
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;
}

export interface KernelPolicyEvidenceProvider extends KernelProvider {
  recordPolicyEvidence(
    evidence: {
      readonly evidenceId: string;
      readonly productUnitId: string;
      readonly runId: string;
      readonly decision: "allowed" | "denied" | "requires-approval";
      readonly policyRefs: readonly string[];
      readonly recordedAt: string;
      readonly correlationId?: string;
    },
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult>;
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
    "memory",
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

export const KernelLifecycleProviderNameSchema = z.enum([
  "events",
  "artifacts",
  "health",
  "approvals",
  "provenance",
  "memory",
  "runtimeTruth",
]);

export type KernelLifecycleProviderContextReasonCode =
  | "missing-provider"
  | "invalid-provider-mode"
  | "invalid-backing-store"
  | "file-backing-allowed";

export const KernelLifecycleProviderContextReasonCodeSchema = z.enum([
  "missing-provider",
  "invalid-provider-mode",
  "invalid-backing-store",
  "file-backing-allowed",
]);

export interface KernelLifecycleProviderContextValidationResult {
  readonly valid: boolean;
  readonly missingProviders: readonly KernelLifecycleProviderName[];
  readonly invalidBackingStores: readonly InvalidBackingStoreError[];
  readonly mode?: KernelProviderMode;
  readonly reasonCodes: readonly KernelLifecycleProviderContextReasonCode[];
}

export interface InvalidBackingStoreError {
  readonly providerName: string;
  readonly backingStore: string;
  readonly reason: string;
}

export const InvalidBackingStoreErrorSchema = z
  .object({
    providerName: z.string().trim().min(1),
    backingStore: z.string().trim().min(1),
    reason: z.string().trim().min(1),
  })
  .strict();

export const KernelLifecycleProviderContextValidationResultSchema = z
  .object({
    valid: z.boolean(),
    missingProviders: z.array(KernelLifecycleProviderNameSchema),
    invalidBackingStores: z.array(InvalidBackingStoreErrorSchema),
    mode: KernelProviderModeSchema.optional(),
    reasonCodes: z.array(KernelLifecycleProviderContextReasonCodeSchema),
  })
  .strict();

function hasFunctions(
  value: unknown,
  functionNames: readonly string[],
): boolean {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const provider = value as Record<string, unknown>;
  return functionNames.every((name) => typeof provider[name] === "function");
}

export const LifecycleEventProviderSchema = z.custom<LifecycleEventProvider>(
  (value) => hasFunctions(value, ["appendEvent", "listEvents"]),
  "LifecycleEventProvider requires event persistence functions",
);

export const LifecycleArtifactProviderSchema =
  z.custom<LifecycleArtifactProvider>(
    (value) =>
      hasFunctions(value, ["recordArtifactManifest", "listArtifactManifests"]),
    "LifecycleArtifactProvider requires artifact manifest functions",
  );

export const LifecycleHealthProviderSchema = z.custom<LifecycleHealthProvider>(
  (value) =>
    hasFunctions(value, ["recordHealthSnapshot", "getLatestHealthSnapshot"]),
  "LifecycleHealthProvider requires health snapshot functions",
);

export const LifecycleApprovalProviderSchema =
  z.custom<LifecycleApprovalProvider>(
    (value) =>
      hasFunctions(value, [
        "requestLifecycleApproval",
        "decideLifecycleApproval",
      ]),
    "LifecycleApprovalProvider requires approval functions",
  );

export const LifecycleProvenanceProviderSchema =
  z.custom<LifecycleProvenanceProvider>(
    (value) => hasFunctions(value, ["recordProvenance", "listProvenance"]),
    "LifecycleProvenanceProvider requires provenance functions",
  );

export const LifecycleMemoryProviderSchema = z.custom<LifecycleMemoryProvider>(
  (value) => hasFunctions(value, ["recordMemory", "listMemory"]),
  "LifecycleMemoryProvider requires memory functions",
);

export const LifecycleRuntimeTruthProviderSchema =
  z.custom<LifecycleRuntimeTruthProvider>(
    (value) => hasFunctions(value, ["recordRuntimeTruth", "getRuntimeTruth"]),
    "LifecycleRuntimeTruthProvider requires runtime truth functions",
  );

export const KernelEventProviderSchema = LifecycleEventProviderSchema;
export const KernelArtifactProviderSchema = LifecycleArtifactProviderSchema;
export const KernelHealthProviderSchema = LifecycleHealthProviderSchema;
export const KernelProvenanceProviderSchema = LifecycleProvenanceProviderSchema;
export const KernelRuntimeTruthProviderSchema =
  LifecycleRuntimeTruthProviderSchema;

export const KernelTelemetryProviderSchema = z.custom<KernelTelemetryProvider>(
  (value) => hasFunctions(value, ["recordTelemetry"]),
  "KernelTelemetryProvider requires telemetry recording function",
);

export const KernelPolicyEvidenceProviderSchema =
  z.custom<KernelPolicyEvidenceProvider>(
    (value) => hasFunctions(value, ["recordPolicyEvidence"]),
    "KernelPolicyEvidenceProvider requires policy evidence function",
  );

export function isKernelProviderMode(
  value: unknown,
): value is KernelProviderMode {
  return KernelProviderModeSchema.safeParse(value).success;
}

export function validateLifecycleProviderWriteOptions(
  value: unknown,
): value is LifecycleProviderWriteOptions {
  return LifecycleProviderWriteOptionsSchema.safeParse(value).success;
}

export function validateLifecycleProviderResult(
  value: unknown,
): value is LifecycleProviderResult {
  return LifecycleProviderResultSchema.safeParse(value).success;
}

export function validateKernelBridgeProviderMode(
  value: unknown,
): value is KernelBridgeProviderMode {
  return KernelBridgeProviderModeSchema.safeParse(value).success;
}

export function validateKernelBridgeProviderStatus(
  value: unknown,
): value is KernelBridgeProviderStatus {
  return KernelBridgeProviderStatusSchema.safeParse(value).success;
}

export function validateKernelBridgeProviderErrorCode(
  value: unknown,
): value is KernelBridgeProviderErrorCode {
  return KernelBridgeProviderErrorCodeSchema.safeParse(value).success;
}

export function validateLifecycleProviderQuery(
  value: unknown,
): value is LifecycleProviderQuery {
  return LifecycleProviderQuerySchema.safeParse(value).success;
}

export function validateLifecycleEvidencePrivacyClassification(
  value: unknown,
): value is LifecycleEvidencePrivacyClassification {
  return LifecycleEvidencePrivacyClassificationSchema.safeParse(value).success;
}

export function validateLifecycleRetentionMetadata(
  value: unknown,
): value is LifecycleRetentionMetadata {
  return LifecycleRetentionMetadataSchema.safeParse(value).success;
}

export function validateLifecycleEventProvider(
  value: unknown,
): value is LifecycleEventProvider {
  return LifecycleEventProviderSchema.safeParse(value).success;
}

export function validateLifecycleArtifactProvider(
  value: unknown,
): value is LifecycleArtifactProvider {
  return LifecycleArtifactProviderSchema.safeParse(value).success;
}

export function validateLifecycleHealthProvider(
  value: unknown,
): value is LifecycleHealthProvider {
  return LifecycleHealthProviderSchema.safeParse(value).success;
}

export function validateLifecycleApprovalProvider(
  value: unknown,
): value is LifecycleApprovalProvider {
  return LifecycleApprovalProviderSchema.safeParse(value).success;
}

export function validateLifecycleProvenanceProvider(
  value: unknown,
): value is LifecycleProvenanceProvider {
  return LifecycleProvenanceProviderSchema.safeParse(value).success;
}

export function validateLifecycleMemoryProvider(
  value: unknown,
): value is LifecycleMemoryProvider {
  return LifecycleMemoryProviderSchema.safeParse(value).success;
}

export function validateLifecycleRuntimeTruthProvider(
  value: unknown,
): value is LifecycleRuntimeTruthProvider {
  return LifecycleRuntimeTruthProviderSchema.safeParse(value).success;
}

export function validateKernelEventProvider(
  value: unknown,
): value is KernelEventProvider {
  return KernelEventProviderSchema.safeParse(value).success;
}

export function validateKernelArtifactProvider(
  value: unknown,
): value is KernelArtifactProvider {
  return KernelArtifactProviderSchema.safeParse(value).success;
}

export function validateKernelHealthProvider(
  value: unknown,
): value is KernelHealthProvider {
  return KernelHealthProviderSchema.safeParse(value).success;
}

export function validateKernelProvenanceProvider(
  value: unknown,
): value is KernelProvenanceProvider {
  return KernelProvenanceProviderSchema.safeParse(value).success;
}

export function validateKernelRuntimeTruthProvider(
  value: unknown,
): value is KernelRuntimeTruthProvider {
  return KernelRuntimeTruthProviderSchema.safeParse(value).success;
}

export function validateKernelTelemetryProvider(
  value: unknown,
): value is KernelTelemetryProvider {
  return KernelTelemetryProviderSchema.safeParse(value).success;
}

export function validateKernelPolicyEvidenceProvider(
  value: unknown,
): value is KernelPolicyEvidenceProvider {
  return KernelPolicyEvidenceProviderSchema.safeParse(value).success;
}

export function validateKernelLifecycleProviderName(
  value: unknown,
): value is KernelLifecycleProviderName {
  return KernelLifecycleProviderNameSchema.safeParse(value).success;
}

export function validateKernelLifecycleProviderContextReasonCode(
  value: unknown,
): value is KernelLifecycleProviderContextReasonCode {
  return KernelLifecycleProviderContextReasonCodeSchema.safeParse(value).success;
}

export function validateKernelLifecycleProviderContextValidationResult(
  value: unknown,
): value is KernelLifecycleProviderContextValidationResult {
  return KernelLifecycleProviderContextValidationResultSchema.safeParse(value)
    .success;
}

export function validateInvalidBackingStoreError(
  value: unknown,
): value is InvalidBackingStoreError {
  return InvalidBackingStoreErrorSchema.safeParse(value).success;
}

export function validateKernelLifecycleProviderContext(
  context: KernelLifecycleProviderContext,
): KernelLifecycleProviderContextValidationResult {
  if (!isKernelProviderMode(context.mode)) {
    return {
      valid: false,
      missingProviders: [],
      invalidBackingStores: [],
      reasonCodes: ["invalid-provider-mode"],
    };
  }

  const requiredProviders = KernelProviderModeRequirements[context.mode];
  const missingProviders = requiredProviders.filter(
    (providerName) => context[providerName] === undefined,
  );

  const backingStoreValidation = validateProviderBackingForMode(context);

  return {
    valid: missingProviders.length === 0 && backingStoreValidation.valid,
    missingProviders,
    invalidBackingStores: backingStoreValidation.invalidBackingStores,
    mode: context.mode,
    reasonCodes: [
      ...(missingProviders.length > 0 ? (["missing-provider"] as const) : []),
      ...(backingStoreValidation.reasonCodes ?? []),
    ],
  };
}

export function validateProviderBackingForMode(
  context: KernelLifecycleProviderContext,
): Pick<
  KernelLifecycleProviderContextValidationResult,
  "valid" | "invalidBackingStores" | "reasonCodes"
> {
  if (context.mode !== "platform") {
    // Bootstrap mode allows file providers
    return { valid: true, invalidBackingStores: [], reasonCodes: [] };
  }

  const invalidBackingStores: InvalidBackingStoreError[] = [];
  const allowedFileBackedProviders: string[] = [];
  const providerEntries: ReadonlyArray<[string, KernelProvider | undefined]> = [
    ["registryProvider", context.registryProvider],
    ["sourceProvider", context.sourceProvider],
    ["events", context.events],
    ["artifacts", context.artifacts],
    ["health", context.health],
    ["approvals", context.approvals],
    ["provenance", context.provenance],
    ["memory", context.memory],
    ["runtimeTruth", context.runtimeTruth],
  ];

  // Platform mode pilot: allow file-backed providers for development
  // In production, this should be enforced to require data-cloud backing
  const allowFileBacking = process.env.GHATANA_PLATFORM_ALLOW_FILE_BACKING === "true";

  for (const [providerName, provider] of providerEntries) {
    if (provider !== undefined && provider.backingStore === "file") {
      if (allowFileBacking) {
        allowedFileBackedProviders.push(providerName);
      } else {
        invalidBackingStores.push({
          providerName,
          backingStore: provider.backingStore,
          reason:
            "Platform mode cannot use file-backed providers. Use data-cloud or external backing store, or set GHATANA_PLATFORM_ALLOW_FILE_BACKING=true for development.",
        });
      }
    }
  }

  return {
    valid: invalidBackingStores.length === 0,
    invalidBackingStores,
    reasonCodes: [
      ...(invalidBackingStores.length > 0 ? (["invalid-backing-store"] as const) : []),
      ...(allowedFileBackedProviders.length > 0 ? (["file-backing-allowed"] as const) : []),
    ],
  };
}

export function requireLifecycleProvider<TProvider extends KernelProvider>(
  context: KernelLifecycleProviderContext,
  providerName: keyof Omit<KernelLifecycleProviderContext, "mode">,
): TProvider {
  const provider = context[providerName];
  if (provider === undefined) {
    throw new Error(
      `Kernel ${context.mode} mode requires lifecycle provider: ${String(providerName)}`,
    );
  }
  return provider as unknown as TProvider;
}

export function requireLifecycleProviderSet(
  context: KernelLifecycleProviderContext,
  providerNames: readonly KernelLifecycleProviderName[],
): void {
  const missingProviders = providerNames.filter(
    (providerName) => context[providerName] === undefined,
  );
  if (missingProviders.length > 0) {
    throw new Error(
      `Kernel ${context.mode} mode requires lifecycle providers: ${missingProviders.join(", ")}`,
    );
  }
}
