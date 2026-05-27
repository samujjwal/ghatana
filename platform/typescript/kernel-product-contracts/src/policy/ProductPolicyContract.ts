/**
 * K-003: Kernel product policy abstraction for PHI.
 * Consent/treatment/facility/emergency/FCHV policies are modeled in Kernel, implemented/configured by PHR.
 */

export type PolicyDecision = 'allowed' | 'denied';

export type PolicyReasonCode =
  | 'PATIENT_OWN_RECORD'
  | 'PATIENT_OTHER_RECORD'
  | 'CAREGIVER_GRANTED'
  | 'CAREGIVER_NO_GRANT'
  | 'CLINICIAN_TREATMENT_RELATIONSHIP'
  | 'CLINICIAN_NO_TREATMENT_RELATIONSHIP'
  | 'ADMIN_AUDIT_PURPOSE'
  | 'ADMIN_NO_AUDIT_PURPOSE'
  | 'FCHV_COMMUNITY_ASSIGNED'
  | 'FCHV_NO_COMMUNITY_ASSIGNMENT'
  | 'EMERGENCY_APPROVED'
  | 'EMERGENCY_PENDING'
  | 'EMERGENCY_DENIED'
  | 'POLICY_SERVICE_UNAVAILABLE'
  | 'UNKNOWN_POLICY';

export type PolicyRequirement = {
  auditRequired: boolean;
  justificationRequired: boolean;
  notificationRequired: boolean;
  reviewRequired: boolean;
};

export type ConsentPolicy = {
  resourceTypes: string[];
  actions: string[];
  expiresAt?: string;
  revocable: boolean;
};

export type TreatmentRelationshipPolicy = {
  activeRelationship: boolean;
  relationshipType?: string;
  facilityId?: string;
  expiresAt?: string;
};

export type FacilityScopePolicy = {
  facilityId: string;
  allowedRoles: string[];
  scope: 'full' | 'limited' | 'read-only';
};

export type EmergencyPolicy = {
  requiresJustification: boolean;
  requiresApproval: boolean;
  approvalTimeoutMinutes?: number;
  notificationRequired: boolean;
  reviewRequired: boolean;
};

export type FchvCommunityPolicy = {
  communityId: string;
  assignedPatients: string[];
  scope: 'community' | 'village' | 'ward';
};

export type ProductPolicy = {
  id: string;
  type: 'consent' | 'treatment-relationship' | 'facility-scope' | 'emergency' | 'fchv-community';
  decision: PolicyDecision;
  reasonCode: PolicyReasonCode;
  requirements: PolicyRequirement;
  config: ConsentPolicy | TreatmentRelationshipPolicy | FacilityScopePolicy | EmergencyPolicy | FchvCommunityPolicy;
};

export type ProductPolicyEvaluationRequest = {
  principalId: string;
  role: string;
  patientId: string;
  action: string;
  resourceType?: string;
  facilityId?: string;
  emergencyContext?: {
    justification?: string;
    approved?: boolean;
  };
};

export type ProductPolicyEvaluationResult = {
  decision: PolicyDecision;
  reasonCode: PolicyReasonCode;
  requirements: PolicyRequirement;
  policies: ProductPolicy[];
  correlationId?: string;
};

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
