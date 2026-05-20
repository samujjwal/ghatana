/**
 * ProductUnitIntent - public contract for requesting ProductUnit creation or update.
 *
 * This interface allows YAPPC and other creators to request ProductUnit creation/update
 * without mutating Kernel internals. It provides a clean boundary between product creators
 * and the Kernel platform.
 *
 * @doc.type interface
 * @doc.purpose Public contract for ProductUnit creation/update requests
 * @doc.layer kernel-product-contracts
 * @doc.pattern Command
 */

import { z } from "zod";
import type { ProductLifecyclePhase } from "../lifecycle/ProductLifecyclePhase";
import {
  ProductUnitDraftSchema,
  ProductUnitScopeSchema,
  type ProductUnitDraft,
  type ProductUnitScope,
} from "./ProductUnit.js";
import type { ProductUnitSourceRef } from "./ProductUnitSourceRef.js";
import { ProductUnitSourceRefSchema } from "./ProductUnitSourceRef.js";

export type { ProductUnitDraft, ProductUnitScope };

/**
 * The type of producer that created the ProductUnitIntent.
 */
export type ProducerType = "yappc" | "api" | "cli" | "manual" | "external";

export type ProductUnitIntentType = "create" | "update" | "promote-candidate";
export type ProductUnitIntentApplyMode = "preview" | "apply";
export type ProductUnitIntentStatus = "accepted" | "queued" | "blocked" | "failed";

/**
 * Application status for ProductUnitIntent after Kernel processing.
 */
export type ProductUnitIntentApplicationStatus =
  | "previewed"
  | "queued"
  | "applied"
  | "blocked"
  | "failed";

/**
 * Reason codes for ProductUnitIntent application failures.
 */
export type ProductUnitIntentApplicationReasonCode =
  | "target-provider-mismatch"
  | "missing-apply-permission"
  | "provider-mode-not-available"
  | "registry-apply-failed"
  | "runtime-truth-write-failed"
  | "provenance-write-failed"
  | "event-write-failed"
  | "schema-invalid"
  | "kernel-lifecycle-service-unavailable"
  | "kernel-service-unreachable"
  | "kernel-service-response-invalid"
  | `kernel-service-http-${number}`;

/**
 * Result of applying a ProductUnitIntent through the Kernel.
 */
export interface ProductUnitIntentApplicationResult {
  /**
   * Schema version for contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Unique identifier for the intent that was applied.
   */
  readonly intentId: string;

  /**
   * Application status after Kernel processing.
   */
  readonly status: ProductUnitIntentApplicationStatus;

  /**
   * ProductUnit identifier that was created or updated.
   */
  readonly productUnitId: string;

  /**
   * Correlation identifier for tracing the operation.
   */
  readonly correlationId: string;

  /**
   * Provider mode used for the application.
   */
  readonly providerMode: "bootstrap" | "platform";

  /**
   * Registry provider identifier that handled the application.
   */
  readonly registryProviderId: string;

  /**
   * Source provider identifier that supplied the intent.
   */
  readonly sourceProviderId: string;

  /**
   * Reference to the preview result if in preview mode.
   */
  readonly previewRef?: string | undefined;

  /**
   * Reference to the application result if in apply mode.
   */
  readonly applicationRef?: string | undefined;

  /**
   * References to lifecycle events recorded during application.
   */
  readonly lifecycleEventRefs: readonly string[];

  /**
   * References to provenance records created during application.
   */
  readonly provenanceRefs: readonly string[];

  /**
   * References to runtime truth snapshots recorded during application.
   */
  readonly runtimeTruthRefs: readonly string[];

  /**
   * Reason codes explaining why the application was blocked or failed.
   */
  readonly blockedReasons: readonly string[];

  /**
   * Error messages describing any failures.
   */
  readonly errors: readonly string[];

  /**
   * ISO timestamp when the intent was applied (for successful applies).
   */
  readonly appliedAt?: string | undefined;
}

export type ProductUnitPrivacyLevel = "public" | "internal" | "confidential" | "restricted";

export type ProductUnitDataSensitivity = "none" | "low" | "moderate" | "high" | "regulated";

/**
 * Target providers for the ProductUnit.
 */
export interface TargetProviders {
  /**
   * Registry provider identifier.
   */
  readonly registryProvider: string;

  /**
   * Source provider identifier.
   */
  readonly sourceProvider: string;
}

/**
 * Producer information for the intent.
 */
export interface Producer {
  /**
   * Producer identifier.
   */
  readonly id: string;

  /**
   * Type of producer.
   */
  readonly type: ProducerType;

  /**
   * Correlation identifier propagated from the producing system.
   */
  readonly correlationId: string;
}

/**
 * Requested lifecycle configuration.
 */
export interface RequestedLifecycle {
  /**
   * Lifecycle profile name.
   */
  readonly profile: string;

  /**
   * Whether to enable lifecycle execution.
   */
  readonly enableExecution: boolean;

  /**
   * Requested lifecycle phases.
   */
  readonly phases?: readonly ProductLifecyclePhase[] | undefined;
}

export interface ProductUnitGovernanceHints {
  readonly privacyLevel?: ProductUnitPrivacyLevel | undefined;
  readonly evidencePrivacyClassification?: ProductUnitPrivacyLevel | undefined;
  readonly regulatedDomain?: string | undefined;
  readonly requiresHumanApproval?: boolean | undefined;
  readonly requiredPolicyPacks?: readonly string[] | undefined;
  readonly dataSensitivity?: ProductUnitDataSensitivity | undefined;
  readonly retentionPolicyId?: string | undefined;
  readonly retentionDays?: number | undefined;
  readonly evidenceRequired?: boolean | undefined;
}

export interface IntentProvenance {
  readonly sourceSystem: ProducerType;
  readonly sourceArtifactRefs: readonly string[];
  readonly sourceRefs?: readonly ProductUnitSourceRef[] | undefined;
  readonly createdBy: string;
  readonly createdAt: string;
  readonly evidenceRefs?: readonly string[] | undefined;
}

/**
 * Intent to create or update a ProductUnit.
 *
 * This is the public contract for YAPPC and other creators to request ProductUnit
 * operations without mutating Kernel internals.
 */
export interface ProductUnitIntent {
  /**
   * Schema version for ProductUnitIntent contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Unique identifier for this intent.
   */
  readonly intentId: string;

  /**
   * Intent operation type.
   */
  readonly intentType: ProductUnitIntentType;

  /**
   * Tenant/workspace/project scope for this requested ProductUnit change.
   */
  readonly scope: ProductUnitScope;

  /**
   * Producer that created this intent.
   */
  readonly producer: Producer;

  /**
   * Target providers for the ProductUnit.
   */
  readonly target: TargetProviders;

  /**
   * Draft ProductUnit to create or update.
   */
  readonly productUnit: ProductUnitDraft;

  /**
   * Optional requested lifecycle configuration.
   */
  readonly requestedLifecycle?: RequestedLifecycle | undefined;

  /**
   * Optional governance hints for Kernel gate/provider selection.
   */
  readonly governanceHints?: ProductUnitGovernanceHints | undefined;

  /**
   * Optional provenance information. Must not contain raw secrets.
   */
  readonly provenance?: IntentProvenance | undefined;

  /**
   * Optional semantic artifact evidence refs from YAPPC/Studio handoff.
   */
  readonly semanticArtifactRefs?: readonly string[] | undefined;

  /**
   * Optional risk hotspot evidence refs for generated or imported source.
   */
  readonly riskHotspotRefs?: readonly string[] | undefined;

  /**
   * Optional generated change-set evidence refs.
   */
  readonly generatedChangeSetRefs?: readonly string[] | undefined;
}

const PRODUCER_TYPES: readonly ProducerType[] = [
  "yappc",
  "api",
  "cli",
  "manual",
  "external",
];

const INTENT_TYPES = [
  "create",
  "update",
  "promote-candidate",
] as const satisfies readonly ProductUnitIntentType[];

const APPLICATION_STATUSES = [
  "previewed",
  "queued",
  "applied",
  "blocked",
  "failed",
] as const satisfies readonly ProductUnitIntentApplicationStatus[];

const PRODUCT_LIFECYCLE_PHASES = [
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

const PRIVACY_LEVELS = [
  "public",
  "internal",
  "confidential",
  "restricted",
] as const satisfies readonly ProductUnitPrivacyLevel[];

const DATA_SENSITIVITY_LEVELS = [
  "none",
  "low",
  "moderate",
  "high",
  "regulated",
] as const satisfies readonly ProductUnitDataSensitivity[];

const SECRET_KEY_PATTERN = /(secret|password|token|api[-_]?key|credential)/i;

export interface ProductUnitIntentValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
}

export type ProductUnitIntentValidationReasonCode =
  | "missing-evidence"
  | "missing-source-artifact-refs"
  | "secret-like-field"
  | "invalid-scope"
  | "unsupported-lifecycle-phase"
  | "schema-invalid";

export interface ProductUnitIntentValidationIssue {
  readonly path: string;
  readonly reasonCode: ProductUnitIntentValidationReasonCode;
  readonly message: string;
  readonly severity: "error" | "warning";
}

export interface ProductUnitIntentDetailedValidationResult
  extends ProductUnitIntentValidationResult {
  readonly issues: readonly ProductUnitIntentValidationIssue[];
}

function hasSecretLikeField(value: unknown): boolean {
  if (Array.isArray(value)) {
    return value.some((item) => hasSecretLikeField(item));
  }
  if (typeof value !== "object" || value === null) {
    return false;
  }

  return Object.entries(value as Record<string, unknown>).some(([key, nested]) => {
    if (SECRET_KEY_PATTERN.test(key)) {
      return true;
    }
    return hasSecretLikeField(nested);
  });
}

export const ProducerSchema = z
  .object({
    id: z.string().trim().min(1),
    type: z.enum(PRODUCER_TYPES),
    correlationId: z.string().trim().min(1),
  })
  .strict();

export const TargetProvidersSchema = z
  .object({
    registryProvider: z.string().trim().min(1),
    sourceProvider: z.string().trim().min(1),
  })
  .strict();

export const RequestedLifecycleSchema = z
  .object({
    profile: z.string().trim().min(1),
    enableExecution: z.boolean(),
    phases: z.array(z.enum(PRODUCT_LIFECYCLE_PHASES)).optional(),
  })
  .strict();

export const ProductUnitGovernanceHintsSchema = z
  .object({
    privacyLevel: z.enum(PRIVACY_LEVELS).optional(),
    evidencePrivacyClassification: z.enum(PRIVACY_LEVELS).optional(),
    regulatedDomain: z.string().trim().min(1).optional(),
    requiresHumanApproval: z.boolean().optional(),
    requiredPolicyPacks: z.array(z.string().trim().min(1)).optional(),
    dataSensitivity: z.enum(DATA_SENSITIVITY_LEVELS).optional(),
    retentionPolicyId: z.string().trim().min(1).optional(),
    retentionDays: z.number().int().nonnegative().optional(),
    evidenceRequired: z.boolean().optional(),
  })
  .strict();

export const IntentProvenanceSchema = z
  .object({
    sourceSystem: z.enum(PRODUCER_TYPES),
    sourceArtifactRefs: z.array(z.string().trim().min(1)),
    sourceRefs: z.array(ProductUnitSourceRefSchema).optional(),
    createdBy: z.string().trim().min(1),
    createdAt: z.string().datetime({ offset: true }),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export const ProductUnitIntentSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    intentId: z.string().trim().min(1),
    intentType: z.enum(INTENT_TYPES),
    scope: ProductUnitScopeSchema,
    producer: ProducerSchema,
    target: TargetProvidersSchema,
    productUnit: ProductUnitDraftSchema.extend({
      surfaces: ProductUnitDraftSchema.shape.surfaces.min(1),
    }),
    requestedLifecycle: RequestedLifecycleSchema.optional(),
    governanceHints: ProductUnitGovernanceHintsSchema.optional(),
    provenance: IntentProvenanceSchema.optional(),
    semanticArtifactRefs: z.array(z.string().trim().min(1)).optional(),
    riskHotspotRefs: z.array(z.string().trim().min(1)).optional(),
    generatedChangeSetRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict()
  .superRefine((intent: ProductUnitIntent, context: z.RefinementCtx) => {
    if (hasSecretLikeField(intent)) {
      context.addIssue({
        code: "custom",
        message: "ProductUnitIntent must not include raw secret-like fields",
      });
    }
    if (
      intent.intentType === "promote-candidate" &&
      intent.provenance !== undefined &&
      intent.provenance.sourceArtifactRefs.length === 0
    ) {
      context.addIssue({
        code: "custom",
        path: ["provenance", "sourceArtifactRefs"],
        message: "promote-candidate requires provenance.sourceArtifactRefs",
      });
    }
    if (
      intent.intentType === "promote-candidate" &&
      (intent.provenance?.evidenceRefs === undefined ||
        intent.provenance.evidenceRefs.length === 0)
    ) {
      context.addIssue({
        code: "custom",
        path: ["provenance", "evidenceRefs"],
        message: "promote-candidate requires provenance.evidenceRefs",
      });
    }
  });

export const ProductUnitIntentApplicationResultSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    intentId: z.string().trim().min(1),
    status: z.enum(APPLICATION_STATUSES),
    productUnitId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    providerMode: z.enum(["bootstrap", "platform"]),
    registryProviderId: z.string().trim().min(1),
    sourceProviderId: z.string().trim().min(1),
    previewRef: z.string().trim().min(1).optional(),
    applicationRef: z.string().trim().min(1).optional(),
    lifecycleEventRefs: z.array(z.string().trim().min(1)),
    provenanceRefs: z.array(z.string().trim().min(1)),
    runtimeTruthRefs: z.array(z.string().trim().min(1)),
    blockedReasons: z.array(z.string().trim().min(1)),
    errors: z.array(z.string().trim().min(1)),
    appliedAt: z.string().datetime({ offset: true }).optional(),
  })
  .strict();

function formatProductUnitIntentIssue(issue: z.ZodIssue): string {
  const path = issue.path.join(".");
  if (path === "schemaVersion") {
    return 'schemaVersion must be "1.0.0"';
  }
  if (path === "intentId") {
    return "intentId must be a non-empty string";
  }
  if (path === "intentType") {
    return "intentType is not supported";
  }
  if (path === "scope") {
    return "scope must be an object";
  }
  if (path === "producer") {
    return "producer must be an object";
  }
  if (path === "producer.id") {
    return "producer.id must be a non-empty string";
  }
  if (path === "producer.type") {
    return "producer.type is not supported";
  }
  if (path === "producer.correlationId") {
    return "producer.correlationId must be a non-empty string";
  }
  if (path === "target") {
    return "target must be an object";
  }
  if (path === "target.registryProvider") {
    return "target.registryProvider must be a non-empty string";
  }
  if (path === "target.sourceProvider") {
    return "target.sourceProvider must be a non-empty string";
  }
  if (path === "productUnit") {
    return "productUnit must be an object";
  }
  if (path === "productUnit.kind") {
    return "productUnit.kind is not a known ProductUnit kind";
  }
  if (path === "productUnit.surfaces") {
    return "productUnit.surfaces must contain at least one surface";
  }
  if (/^productUnit\.surfaces\.\d+\.type$/.test(path)) {
    const index = path.split(".")[2];
    return `productUnit.surfaces[${index}].type is not supported`;
  }
  if (/^productUnit\.surfaces\.\d+\.implementationStatus$/.test(path)) {
    const index = path.split(".")[2];
    return `productUnit.surfaces[${index}].implementationStatus is not supported`;
  }
  if (path === "governanceHints.evidencePrivacyClassification") {
    return "governanceHints.evidencePrivacyClassification is invalid";
  }
  if (path === "governanceHints.retentionPolicyId") {
    return "governanceHints.retentionPolicyId must be a non-empty string";
  }
  if (path === "governanceHints.retentionDays") {
    return "governanceHints.retentionDays must be non-negative";
  }
  return issue.message;
}

function productUnitIntentReasonCode(
  path: string,
  message: string
): ProductUnitIntentValidationReasonCode {
  if (path.startsWith("scope")) {
    return "invalid-scope";
  }
  if (path === "requestedLifecycle.phases" || path.includes("phases")) {
    return "unsupported-lifecycle-phase";
  }
  if (path === "provenance.evidenceRefs") {
    return "missing-evidence";
  }
  if (path === "provenance.sourceArtifactRefs") {
    return "missing-source-artifact-refs";
  }
  if (message.includes("secret-like")) {
    return "secret-like-field";
  }
  return "schema-invalid";
}

function toProductUnitIntentIssue(
  path: string,
  reasonCode: ProductUnitIntentValidationReasonCode,
  message: string
): ProductUnitIntentValidationIssue {
  return {
    path,
    reasonCode,
    message,
    severity: "error",
  };
}

export function validateProductUnitIntentDetailed(
  value: unknown
): ProductUnitIntentDetailedValidationResult {
  if (typeof value !== "object" || value === null) {
    const issue = toProductUnitIntentIssue(
      "",
      "schema-invalid",
      "ProductUnitIntent must be an object"
    );
    return { valid: false, errors: [issue.message], issues: [issue] };
  }

  const parsed = ProductUnitIntentSchema.safeParse(value);
  const issues = parsed.success
    ? []
    : parsed.error.issues.map((issue: z.ZodIssue) => {
        const path = issue.path.join(".");
        const message = formatProductUnitIntentIssue(issue);
        return toProductUnitIntentIssue(
          path,
          productUnitIntentReasonCode(path, message),
          message
        );
      });
  const errors = issues.map((issue: ProductUnitIntentValidationIssue) => issue.message);

  return { valid: errors.length === 0, errors, issues };
}

export function validateProductUnitIntent(
  value: unknown
): ProductUnitIntentValidationResult {
  const result = validateProductUnitIntentDetailed(value);
  return { valid: result.valid, errors: result.errors };
}

/**
 * Type guard to check if an object is a valid ProductUnitIntent.
 */
export function isProductUnitIntent(value: unknown): value is ProductUnitIntent {
  return validateProductUnitIntent(value).valid;
}
