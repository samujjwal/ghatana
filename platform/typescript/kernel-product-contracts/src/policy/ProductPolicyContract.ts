/**
 * K-003: Kernel product policy abstraction for PHI.
 * Consent/treatment/facility/emergency/FCHV policies are modeled in Kernel, implemented/configured by PHR.
 */

import { z } from "zod";

export const PolicyDecisionSchema = z.enum(['allowed', 'denied']);

export const PolicyReasonCodeSchema = z.enum([
  'PATIENT_OWN_RECORD',
  'PATIENT_OTHER_RECORD',
  'CAREGIVER_GRANTED',
  'CAREGIVER_NO_GRANT',
  'CLINICIAN_TREATMENT_RELATIONSHIP',
  'CLINICIAN_NO_TREATMENT_RELATIONSHIP',
  'ADMIN_AUDIT_PURPOSE',
  'ADMIN_NO_AUDIT_PURPOSE',
  'FCHV_COMMUNITY_ASSIGNED',
  'FCHV_NO_COMMUNITY_ASSIGNMENT',
  'EMERGENCY_APPROVED',
  'EMERGENCY_PENDING',
  'EMERGENCY_DENIED',
  'POLICY_SERVICE_UNAVAILABLE',
  'UNKNOWN_POLICY',
]);

export const PolicyRequirementSchema = z
  .object({
    auditRequired: z.boolean(),
    justificationRequired: z.boolean(),
    notificationRequired: z.boolean(),
    reviewRequired: z.boolean(),
  })
  .strict();

export const ConsentPolicySchema = z
  .object({
    resourceTypes: z.array(z.string().trim().min(1)),
    actions: z.array(z.string().trim().min(1)),
    expiresAt: z.string().trim().datetime().optional(),
    revocable: z.boolean(),
  })
  .strict();

export const TreatmentRelationshipPolicySchema = z
  .object({
    activeRelationship: z.boolean(),
    relationshipType: z.string().trim().min(1).optional(),
    facilityId: z.string().trim().min(1).optional(),
    expiresAt: z.string().trim().datetime().optional(),
  })
  .strict();

export const FacilityScopePolicySchema = z
  .object({
    facilityId: z.string().trim().min(1),
    allowedRoles: z.array(z.string().trim().min(1)),
    scope: z.enum(['full', 'limited', 'read-only']),
  })
  .strict();

export const EmergencyPolicySchema = z
  .object({
    requiresJustification: z.boolean(),
    requiresApproval: z.boolean(),
    approvalTimeoutMinutes: z.number().int().positive().optional(),
    notificationRequired: z.boolean(),
    reviewRequired: z.boolean(),
  })
  .strict();

export const FchvCommunityPolicySchema = z
  .object({
    communityId: z.string().trim().min(1),
    assignedPatients: z.array(z.string().trim().min(1)),
    scope: z.enum(['community', 'village', 'ward']),
  })
  .strict();

const PolicyConfigSchema = z.union([
  ConsentPolicySchema,
  TreatmentRelationshipPolicySchema,
  FacilityScopePolicySchema,
  EmergencyPolicySchema,
  FchvCommunityPolicySchema,
]);

export const ProductPolicySchema = z
  .object({
    id: z.string().trim().min(1),
    type: z.enum(['consent', 'treatment-relationship', 'facility-scope', 'emergency', 'fchv-community']),
    decision: PolicyDecisionSchema,
    reasonCode: PolicyReasonCodeSchema,
    requirements: PolicyRequirementSchema,
    config: PolicyConfigSchema,
  })
  .strict();

export const ProductPolicyEvaluationRequestSchema = z
  .object({
    principalId: z.string().trim().min(1),
    role: z.string().trim().min(1),
    patientId: z.string().trim().min(1),
    action: z.string().trim().min(1),
    resourceType: z.string().trim().min(1).optional(),
    facilityId: z.string().trim().min(1).optional(),
    emergencyContext: z
      .object({
        justification: z.string().trim().min(1).optional(),
        approved: z.boolean().optional(),
      })
      .strict()
      .optional(),
  })
  .strict();

export const ProductPolicyEvaluationResultSchema = z
  .object({
    decision: PolicyDecisionSchema,
    reasonCode: PolicyReasonCodeSchema,
    requirements: PolicyRequirementSchema,
    policies: z.array(ProductPolicySchema),
    correlationId: z.string().trim().min(1).optional(),
  })
  .strict();

export type PolicyDecision = z.infer<typeof PolicyDecisionSchema>;
export type PolicyReasonCode = z.infer<typeof PolicyReasonCodeSchema>;
export type PolicyRequirement = z.infer<typeof PolicyRequirementSchema>;
export type ConsentPolicy = z.infer<typeof ConsentPolicySchema>;
export type TreatmentRelationshipPolicy = z.infer<typeof TreatmentRelationshipPolicySchema>;
export type FacilityScopePolicy = z.infer<typeof FacilityScopePolicySchema>;
export type EmergencyPolicy = z.infer<typeof EmergencyPolicySchema>;
export type FchvCommunityPolicy = z.infer<typeof FchvCommunityPolicySchema>;
export type ProductPolicy = z.infer<typeof ProductPolicySchema>;
export type ProductPolicyEvaluationRequest = z.infer<typeof ProductPolicyEvaluationRequestSchema>;
export type ProductPolicyEvaluationResult = z.infer<typeof ProductPolicyEvaluationResultSchema>;

export function createPolicyDecision(
  decision: PolicyDecision,
  reasonCode: PolicyReasonCode,
  requirements: PolicyRequirement
): ProductPolicyEvaluationResult {
  return {
    decision,
    reasonCode,
    requirements,
    policies: [],
  };
}

export function createDefaultPolicyRequirements(): PolicyRequirement {
  return {
    auditRequired: false,
    justificationRequired: false,
    notificationRequired: false,
    reviewRequired: false,
  };
}

export function isPolicyDecisionAllowed(result: ProductPolicyEvaluationResult): boolean {
  return result.decision === 'allowed';
}

export function isPolicyDecisionDenied(result: ProductPolicyEvaluationResult): boolean {
  return result.decision === 'denied';
}

export function validatePolicyReasonCode(value: unknown): value is PolicyReasonCode {
  return PolicyReasonCodeSchema.safeParse(value).success;
}

export function validatePolicyRequirement(value: unknown): value is PolicyRequirement {
  return PolicyRequirementSchema.safeParse(value).success;
}

export function validateConsentPolicy(value: unknown): value is ConsentPolicy {
  return ConsentPolicySchema.safeParse(value).success;
}

export function validateTreatmentRelationshipPolicy(value: unknown): value is TreatmentRelationshipPolicy {
  return TreatmentRelationshipPolicySchema.safeParse(value).success;
}

export function validateFacilityScopePolicy(value: unknown): value is FacilityScopePolicy {
  return FacilityScopePolicySchema.safeParse(value).success;
}

export function validateEmergencyPolicy(value: unknown): value is EmergencyPolicy {
  return EmergencyPolicySchema.safeParse(value).success;
}

export function validateFchvCommunityPolicy(value: unknown): value is FchvCommunityPolicy {
  return FchvCommunityPolicySchema.safeParse(value).success;
}

export function validateProductPolicy(value: unknown): value is ProductPolicy {
  return ProductPolicySchema.safeParse(value).success;
}

export function validateProductPolicyEvaluationRequest(value: unknown): value is ProductPolicyEvaluationRequest {
  return ProductPolicyEvaluationRequestSchema.safeParse(value).success;
}

export function validateProductPolicyEvaluationResult(value: unknown): value is ProductPolicyEvaluationResult {
  return ProductPolicyEvaluationResultSchema.safeParse(value).success;
}
