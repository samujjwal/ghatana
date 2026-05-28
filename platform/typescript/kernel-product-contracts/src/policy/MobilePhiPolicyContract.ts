/**
 * K-004: Kernel mobile PHI policy and release validation contract.
 * Products use this to gate encrypted PHI cache, session binding, field purge,
 * logout/revocation clearing, and emergency biometric/server authorization.
 */

import { z } from "zod";

export const PhiEncryptionAlgorithms = ["AES-GCM", "AES-CBC", "custom"] as const;
export const PhiKeyDerivationMethods = ["PBKDF2", "scrypt", "argon2", "custom"] as const;
export const PhiKeyStorageModes = ["secure-enclave", "keychain", "encrypted-preferences"] as const;
export const PhiFieldClassifications = ["restricted", "sensitive", "standard"] as const;
export const MobilePhiActions = ["read", "write", "cache", "export", "display"] as const;
export const MobilePhiReleaseGateStatuses = ["passed", "failed"] as const;

export const PhiStoragePolicySchema = z
  .object({
    encryptionRequired: z.boolean(),
    encryptionAlgorithm: z.enum(PhiEncryptionAlgorithms),
    keyDerivationMethod: z.enum(PhiKeyDerivationMethods),
    keyStorage: z.enum(PhiKeyStorageModes),
    biometricRequired: z.boolean(),
    biometricFallbackAllowed: z.boolean(),
  })
  .strict();

export const PhiCachePolicySchema = z
  .object({
    ttlMinutes: z.number().int().positive(),
    sessionBinding: z.boolean(),
    clearOnRevoke: z.boolean(),
    clearOnLogout: z.boolean(),
    clearOnSessionExpiry: z.boolean(),
    clearOnRoleChange: z.boolean(),
    clearOnPersonaChange: z.boolean(),
  })
  .strict();

export const PhiFieldPolicySchema = z
  .object({
    fieldName: z.string().trim().min(1),
    classification: z.enum(PhiFieldClassifications),
    cacheAllowed: z.boolean(),
    exportAllowed: z.boolean(),
    displayAllowed: z.boolean(),
    requiresExplicitConsent: z.boolean(),
  })
  .strict();

export const MobilePhiPolicySchema = z
  .object({
    storage: PhiStoragePolicySchema,
    cache: PhiCachePolicySchema,
    fieldPolicies: z.array(PhiFieldPolicySchema),
    emergencyOverride: z
      .object({
        allowed: z.boolean(),
        requiresBiometric: z.boolean(),
        requiresServerAuthorization: z.boolean().default(true),
        auditRequired: z.boolean(),
      })
      .strict(),
  })
  .strict();

export const MobilePhiPolicyCheckRequestSchema = z
  .object({
    principalId: z.string().trim().min(1),
    sessionId: z.string().trim().min(1),
    action: z.enum(MobilePhiActions),
    fieldNames: z.array(z.string().trim().min(1)).optional(),
    emergencyMode: z.boolean().optional(),
  })
  .strict();

export const MobilePhiReleaseValidationInputSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    policy: MobilePhiPolicySchema,
    evidence: z
      .object({
        encryptedStorageVerified: z.boolean(),
        sessionBindingVerified: z.boolean(),
        restrictedFieldPurgeVerified: z.boolean(),
        logoutClearVerified: z.boolean(),
        consentRevokeClearVerified: z.boolean(),
        sessionExpiryClearVerified: z.boolean(),
        rolePersonaChangeClearVerified: z.boolean(),
        emergencyBiometricGateVerified: z.boolean(),
        emergencyServerAuthorizationVerified: z.boolean(),
      })
      .strict(),
  })
  .strict();

export type PhiStoragePolicy = z.infer<typeof PhiStoragePolicySchema>;
export type PhiCachePolicy = z.infer<typeof PhiCachePolicySchema>;
export type PhiFieldPolicy = z.infer<typeof PhiFieldPolicySchema>;
export type MobilePhiPolicy = z.infer<typeof MobilePhiPolicySchema>;
export type MobilePhiPolicyCheckRequest = z.infer<typeof MobilePhiPolicyCheckRequestSchema>;
export type MobilePhiReleaseValidationInput = z.infer<typeof MobilePhiReleaseValidationInputSchema>;

export type MobilePhiPolicyCheckResult = {
  allowed: boolean;
  reasonCode: string;
  requirements: {
    biometricRequired: boolean;
    explicitConsentRequired: boolean;
    auditRequired: boolean;
    serverAuthorizationRequired: boolean;
  };
  fieldResults?: Array<{
    fieldName: string;
    allowed: boolean;
    reason: string;
  }>;
};

export type MobilePhiReleaseValidationResult = {
  status: (typeof MobilePhiReleaseGateStatuses)[number];
  productUnitId: string;
  failedChecks: string[];
};

export function createDefaultPhiStoragePolicy(): PhiStoragePolicy {
  return {
    encryptionRequired: true,
    encryptionAlgorithm: "AES-GCM",
    keyDerivationMethod: "PBKDF2",
    keyStorage: "secure-enclave",
    biometricRequired: true,
    biometricFallbackAllowed: false,
  };
}

export function createDefaultPhiCachePolicy(): PhiCachePolicy {
  return {
    ttlMinutes: 30,
    sessionBinding: true,
    clearOnRevoke: true,
    clearOnLogout: true,
    clearOnSessionExpiry: true,
    clearOnRoleChange: true,
    clearOnPersonaChange: true,
  };
}

export function createDefaultMobilePhiPolicy(fieldPolicies: PhiFieldPolicy[] = []): MobilePhiPolicy {
  return {
    storage: createDefaultPhiStoragePolicy(),
    cache: createDefaultPhiCachePolicy(),
    fieldPolicies,
    emergencyOverride: {
      allowed: true,
      requiresBiometric: true,
      requiresServerAuthorization: true,
      auditRequired: true,
    },
  };
}

export function validatePhiStoragePolicy(policy: PhiStoragePolicy): boolean {
  const parsed = PhiStoragePolicySchema.safeParse(policy);
  return parsed.success && parsed.data.encryptionRequired;
}

export function validatePhiCachePolicy(policy: PhiCachePolicy): boolean {
  const parsed = PhiCachePolicySchema.safeParse(policy);
  if (!parsed.success) {
    return false;
  }

  return (
    parsed.data.sessionBinding &&
    parsed.data.clearOnRevoke &&
    parsed.data.clearOnLogout &&
    parsed.data.clearOnSessionExpiry &&
    parsed.data.clearOnRoleChange &&
    parsed.data.clearOnPersonaChange
  );
}

export function evaluateMobilePhiPolicyCheck(
  policy: MobilePhiPolicy,
  request: MobilePhiPolicyCheckRequest,
): MobilePhiPolicyCheckResult {
  const parsedPolicy = MobilePhiPolicySchema.parse(policy);
  const parsedRequest = MobilePhiPolicyCheckRequestSchema.parse(request);

  if (parsedRequest.emergencyMode === true && !parsedPolicy.emergencyOverride.allowed) {
    return {
      allowed: false,
      reasonCode: "emergency-override-disabled",
      requirements: {
        biometricRequired: false,
        explicitConsentRequired: false,
        auditRequired: false,
        serverAuthorizationRequired: false,
      },
    };
  }

  const requestedFields = parsedRequest.fieldNames ?? [];
  const fieldResults = requestedFields.map((fieldName) => {
    const fieldPolicy = parsedPolicy.fieldPolicies.find((policyEntry) => policyEntry.fieldName === fieldName);
    if (fieldPolicy === undefined) {
      return {
        fieldName,
        allowed: false,
        reason: "field-policy-missing",
      };
    }

    const allowed = isFieldActionAllowed(fieldPolicy, parsedRequest.action);
    return {
      fieldName,
      allowed,
      reason: allowed ? "allowed" : `${parsedRequest.action}-not-allowed`,
    };
  });

  const explicitConsentRequired = requestedFields.some((fieldName) => {
    const fieldPolicy = parsedPolicy.fieldPolicies.find((policyEntry) => policyEntry.fieldName === fieldName);
    return fieldPolicy?.requiresExplicitConsent === true;
  });
  const blockedField = fieldResults.find((result) => !result.allowed);

  return {
    allowed: blockedField === undefined,
    reasonCode: blockedField === undefined ? "allowed" : blockedField.reason,
    requirements: {
      biometricRequired:
        parsedPolicy.storage.biometricRequired ||
        (parsedRequest.emergencyMode === true && parsedPolicy.emergencyOverride.requiresBiometric),
      explicitConsentRequired,
      auditRequired: parsedRequest.emergencyMode === true && parsedPolicy.emergencyOverride.auditRequired,
      serverAuthorizationRequired:
        parsedRequest.emergencyMode === true && parsedPolicy.emergencyOverride.requiresServerAuthorization,
    },
    ...(fieldResults.length > 0 ? { fieldResults } : {}),
  };
}

export function validateMobilePhiReleaseGate(
  input: MobilePhiReleaseValidationInput,
): MobilePhiReleaseValidationResult {
  const parsedInput = MobilePhiReleaseValidationInputSchema.parse(input);
  const failedChecks: string[] = [];

  if (!validatePhiStoragePolicy(parsedInput.policy.storage)) failedChecks.push("encrypted-storage-policy");
  if (!validatePhiCachePolicy(parsedInput.policy.cache)) failedChecks.push("cache-clearing-policy");
  if (!parsedInput.evidence.encryptedStorageVerified) failedChecks.push("encrypted-storage-evidence");
  if (!parsedInput.evidence.sessionBindingVerified) failedChecks.push("session-binding-evidence");
  if (!parsedInput.evidence.restrictedFieldPurgeVerified) failedChecks.push("restricted-field-purge-evidence");
  if (!parsedInput.evidence.logoutClearVerified) failedChecks.push("logout-clear-evidence");
  if (!parsedInput.evidence.consentRevokeClearVerified) failedChecks.push("consent-revoke-clear-evidence");
  if (!parsedInput.evidence.sessionExpiryClearVerified) failedChecks.push("session-expiry-clear-evidence");
  if (!parsedInput.evidence.rolePersonaChangeClearVerified) failedChecks.push("role-persona-clear-evidence");

  if (parsedInput.policy.emergencyOverride.allowed) {
    if (!parsedInput.policy.emergencyOverride.requiresBiometric) failedChecks.push("emergency-biometric-policy");
    if (!parsedInput.policy.emergencyOverride.requiresServerAuthorization) {
      failedChecks.push("emergency-server-authorization-policy");
    }
    if (!parsedInput.policy.emergencyOverride.auditRequired) failedChecks.push("emergency-audit-policy");
    if (!parsedInput.evidence.emergencyBiometricGateVerified) failedChecks.push("emergency-biometric-evidence");
    if (!parsedInput.evidence.emergencyServerAuthorizationVerified) {
      failedChecks.push("emergency-server-authorization-evidence");
    }
  }

  return {
    status: failedChecks.length === 0 ? "passed" : "failed",
    productUnitId: parsedInput.productUnitId,
    failedChecks,
  };
}

function isFieldActionAllowed(fieldPolicy: PhiFieldPolicy, action: MobilePhiPolicyCheckRequest["action"]): boolean {
  if (action === "cache") return fieldPolicy.cacheAllowed;
  if (action === "export") return fieldPolicy.exportAllowed;
  if (action === "display") return fieldPolicy.displayAllowed;
  return true;
}
