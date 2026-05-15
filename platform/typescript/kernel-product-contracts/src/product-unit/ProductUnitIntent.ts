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

export type { ProductUnitDraft, ProductUnitScope };

/**
 * The type of producer that created the ProductUnitIntent.
 */
export type ProducerType = "yappc" | "api" | "cli" | "manual" | "external";

export type ProductUnitIntentType = "create" | "update" | "promote-candidate";

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
  readonly phases?: readonly ProductLifecyclePhase[];
}

export interface ProductUnitGovernanceHints {
  readonly privacyLevel?: ProductUnitPrivacyLevel;
  readonly evidencePrivacyClassification?: ProductUnitPrivacyLevel;
  readonly regulatedDomain?: string;
  readonly requiresHumanApproval?: boolean;
  readonly requiredPolicyPacks?: readonly string[];
  readonly dataSensitivity?: ProductUnitDataSensitivity;
  readonly retentionPolicyId?: string;
  readonly retentionDays?: number;
}

export interface IntentProvenance {
  readonly sourceSystem: ProducerType;
  readonly sourceArtifactRefs: readonly string[];
  readonly createdBy: string;
  readonly createdAt: string;
  readonly evidenceRefs?: readonly string[];
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
  readonly requestedLifecycle?: RequestedLifecycle;

  /**
   * Optional governance hints for Kernel gate/provider selection.
   */
  readonly governanceHints?: ProductUnitGovernanceHints;

  /**
   * Optional provenance information. Must not contain raw secrets.
   */
  readonly provenance?: IntentProvenance;
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
  })
  .strict();

export const IntentProvenanceSchema = z
  .object({
    sourceSystem: z.enum(PRODUCER_TYPES),
    sourceArtifactRefs: z.array(z.string().trim().min(1)),
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
  })
  .strict()
  .superRefine((intent, context) => {
    if (hasSecretLikeField(intent)) {
      context.addIssue({
        code: "custom",
        message: "ProductUnitIntent must not include raw secret-like fields",
      });
    }
  });

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

export function validateProductUnitIntent(
  value: unknown
): ProductUnitIntentValidationResult {
  if (typeof value !== "object" || value === null) {
    return { valid: false, errors: ["ProductUnitIntent must be an object"] };
  }

  const parsed = ProductUnitIntentSchema.safeParse(value);
  const errors = parsed.success
    ? []
    : parsed.error.issues.map((issue: z.ZodIssue) =>
        formatProductUnitIntentIssue(issue)
      );

  return { valid: errors.length === 0, errors };
}

/**
 * Type guard to check if an object is a valid ProductUnitIntent.
 */
export function isProductUnitIntent(value: unknown): value is ProductUnitIntent {
  return validateProductUnitIntent(value).valid;
}
