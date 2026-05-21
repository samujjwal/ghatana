/**
 * ProductInteractionContract - canonical cross-product interaction contract.
 *
 * Products declare interactions through Kernel contracts instead of importing
 * another product's domain classes, services, or storage.
 *
 * @doc.type module
 * @doc.purpose Cross-product interaction contracts for Kernel ProductUnits
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

export type ProductInteractionMode =
  | "request-response"
  | "event-publish"
  | "event-subscribe"
  | "shared-evidence"
  | "provider-capability";

export type ProductInteractionFailureCode =
  | "product_interaction.provider_not_enabled"
  | "product_interaction.consumer_not_authorized"
  | "product_interaction.contract_missing"
  | "product_interaction.contract_version_mismatch"
  | "product_interaction.consent_missing"
  | "product_interaction.policy_denied"
  | "product_interaction.provider_unhealthy"
  | "product_interaction.timeout"
  | "product_interaction.evidence_write_failed"
  | "product_interaction.degraded_not_allowed";

export type ProductInteractionOutcomeStatus =
  | "allowed"
  | "denied"
  | "blocked"
  | "failed"
  | "degraded"
  | "succeeded";

export interface ProductInteractionPolicy {
  readonly requiresAuth: boolean;
  readonly requiresTenant: boolean;
  readonly requiresConsent?: boolean | undefined;
  readonly piiClassification?: string | undefined;
  readonly tenantScope?: "same-tenant" | "same-workspace" | "cross-tenant-approved" | undefined;
  readonly allowedCallerRoles?: readonly string[] | undefined;
  readonly allowedPurposes?: readonly string[] | undefined;
  readonly allowedLifecyclePhases?: readonly string[] | undefined;
  readonly degradedModeAllowed?: boolean | undefined;
}

export interface ProductInteractionEvidence {
  readonly required: boolean;
  readonly manifestType: "interaction-evidence";
  readonly evidenceRefs?: readonly string[] | undefined;
  readonly retentionPolicyId?: string | undefined;
}

export interface ProductInteractionContract {
  readonly contractId: string;
  readonly schemaVersion: string;
  readonly providerProductId: string;
  readonly consumerProductIds: readonly string[];
  readonly mode: ProductInteractionMode;
  readonly topic?: string | undefined;
  readonly capability?: string | undefined;
  readonly requestSchemaRef?: string | undefined;
  readonly responseSchemaRef?: string | undefined;
  readonly eventSchemaRef?: string | undefined;
  readonly policy: ProductInteractionPolicy;
  readonly evidence: ProductInteractionEvidence;
  readonly required?: boolean | undefined;
}

export interface ProductInteractionDeclaration {
  readonly publishes?: readonly ProductInteractionContract[] | undefined;
  readonly consumes?: readonly ProductInteractionContract[] | undefined;
  readonly provides?: readonly ProductInteractionContract[] | undefined;
}

export interface ProductInteractionEvidenceRecord {
  readonly schemaVersion: "1.0.0";
  readonly evidenceId: string;
  readonly manifestType: "interaction-evidence";
  readonly contractId: string;
  readonly contractVersion: string;
  readonly providerProductId: string;
  readonly consumerProductId: string;
  readonly mode: ProductInteractionMode;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly productUnitId: string;
  readonly runId: string;
  readonly correlationId: string;
  readonly requestedAt: string;
  readonly completedAt?: string | undefined;
  readonly status: ProductInteractionOutcomeStatus;
  readonly reasonCode?: ProductInteractionFailureCode | undefined;
  readonly policyDecision: "allowed" | "denied" | "not-evaluated";
  readonly evidenceRefs: readonly string[];
  readonly provenanceRefs?: readonly string[] | undefined;
}

export const ProductInteractionModeSchema = z.enum([
  "request-response",
  "event-publish",
  "event-subscribe",
  "shared-evidence",
  "provider-capability",
]);

export const ProductInteractionPolicySchema = z
  .object({
    requiresAuth: z.boolean(),
    requiresTenant: z.boolean(),
    requiresConsent: z.boolean().optional(),
    piiClassification: z.string().trim().min(1).optional(),
    tenantScope: z
      .enum(["same-tenant", "same-workspace", "cross-tenant-approved"])
      .optional(),
    allowedCallerRoles: z.array(z.string().trim().min(1)).optional(),
    allowedPurposes: z.array(z.string().trim().min(1)).optional(),
    allowedLifecyclePhases: z.array(z.string().trim().min(1)).optional(),
    degradedModeAllowed: z.boolean().optional(),
  })
  .strict();

export const ProductInteractionEvidenceSchema = z
  .object({
    required: z.boolean(),
    manifestType: z.literal("interaction-evidence"),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
    retentionPolicyId: z.string().trim().min(1).optional(),
  })
  .strict();

export const ProductInteractionContractSchema = z
  .object({
    contractId: z.string().trim().min(1),
    schemaVersion: z.string().trim().min(1),
    providerProductId: z.string().trim().min(1),
    consumerProductIds: z.array(z.string().trim().min(1)).min(1),
    mode: ProductInteractionModeSchema,
    topic: z.string().trim().min(1).optional(),
    capability: z.string().trim().min(1).optional(),
    requestSchemaRef: z.string().trim().min(1).optional(),
    responseSchemaRef: z.string().trim().min(1).optional(),
    eventSchemaRef: z.string().trim().min(1).optional(),
    policy: ProductInteractionPolicySchema,
    evidence: ProductInteractionEvidenceSchema,
    required: z.boolean().optional(),
  })
  .strict()
  .superRefine((contract, context) => {
    if (contract.mode === "request-response") {
      if (contract.requestSchemaRef === undefined) {
        context.addIssue({
          code: "custom",
          path: ["requestSchemaRef"],
          message: "request-response interactions require requestSchemaRef",
        });
      }
      if (contract.responseSchemaRef === undefined) {
        context.addIssue({
          code: "custom",
          path: ["responseSchemaRef"],
          message: "request-response interactions require responseSchemaRef",
        });
      }
    }
    if (
      (contract.mode === "event-publish" || contract.mode === "event-subscribe") &&
      contract.topic === undefined
    ) {
      context.addIssue({
        code: "custom",
        path: ["topic"],
        message: "event interactions require topic",
      });
    }
    if (
      (contract.mode === "event-publish" || contract.mode === "event-subscribe") &&
      contract.eventSchemaRef === undefined
    ) {
      context.addIssue({
        code: "custom",
        path: ["eventSchemaRef"],
        message: "event interactions require eventSchemaRef",
      });
    }
    if (contract.mode === "provider-capability" && contract.capability === undefined) {
      context.addIssue({
        code: "custom",
        path: ["capability"],
        message: "provider-capability interactions require capability",
      });
    }
    if (contract.policy.requiresConsent && contract.policy.piiClassification === undefined) {
      context.addIssue({
        code: "custom",
        path: ["policy", "piiClassification"],
        message: "consent-required interactions require piiClassification",
      });
    }
  });

export const ProductInteractionDeclarationSchema = z
  .object({
    publishes: z.array(ProductInteractionContractSchema).optional(),
    consumes: z.array(ProductInteractionContractSchema).optional(),
    provides: z.array(ProductInteractionContractSchema).optional(),
  })
  .strict();

export const ProductInteractionOutcomeStatusSchema = z.enum([
  "allowed",
  "denied",
  "blocked",
  "failed",
  "degraded",
  "succeeded",
]);

export const ProductInteractionFailureCodeSchema = z.enum([
  "product_interaction.provider_not_enabled",
  "product_interaction.consumer_not_authorized",
  "product_interaction.contract_missing",
  "product_interaction.contract_version_mismatch",
  "product_interaction.consent_missing",
  "product_interaction.policy_denied",
  "product_interaction.provider_unhealthy",
  "product_interaction.timeout",
  "product_interaction.evidence_write_failed",
  "product_interaction.degraded_not_allowed",
]);

export const ProductInteractionEvidenceRecordSchema = z
  .object({
    schemaVersion: z.literal("1.0.0"),
    evidenceId: z.string().trim().min(1),
    manifestType: z.literal("interaction-evidence"),
    contractId: z.string().trim().min(1),
    contractVersion: z.string().trim().min(1),
    providerProductId: z.string().trim().min(1),
    consumerProductId: z.string().trim().min(1),
    mode: ProductInteractionModeSchema,
    tenantId: z.string().trim().min(1),
    workspaceId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    requestedAt: z.string().datetime({ offset: true }),
    completedAt: z.string().datetime({ offset: true }).optional(),
    status: ProductInteractionOutcomeStatusSchema,
    reasonCode: ProductInteractionFailureCodeSchema.optional(),
    policyDecision: z.enum(["allowed", "denied", "not-evaluated"]),
    evidenceRefs: z.array(z.string().trim().min(1)),
    provenanceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .strict()
  .superRefine((record, context) => {
    if (
      (record.status === "denied" ||
        record.status === "blocked" ||
        record.status === "failed") &&
      record.reasonCode === undefined
    ) {
      context.addIssue({
        code: "custom",
        path: ["reasonCode"],
        message: "unsuccessful interaction evidence requires reasonCode",
      });
    }
    if (record.policyDecision === "denied" && record.status === "succeeded") {
      context.addIssue({
        code: "custom",
        path: ["status"],
        message: "denied policy decisions cannot produce succeeded status",
      });
    }
  });

export function parseProductInteractionContract(
  input: unknown,
): ProductInteractionContract {
  return ProductInteractionContractSchema.parse(input);
}

export function isProductInteractionContract(
  input: unknown,
): input is ProductInteractionContract {
  return ProductInteractionContractSchema.safeParse(input).success;
}

export function parseProductInteractionEvidenceRecord(
  input: unknown,
): ProductInteractionEvidenceRecord {
  return ProductInteractionEvidenceRecordSchema.parse(input);
}
