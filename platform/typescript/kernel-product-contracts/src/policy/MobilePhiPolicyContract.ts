/**
 * K-004: Kernel mobile PHI policy check.
 * Ensures encrypted storage, clear-on-revoke/logout/session-expiry, biometric gating where required.
 */

export type PhiStoragePolicy = {
  encryptionRequired: boolean;
  encryptionAlgorithm: 'AES-GCM' | 'AES-CBC' | 'custom';
  keyDerivationMethod: 'PBKDF2' | 'scrypt' | 'argon2' | 'custom';
  keyStorage: 'secure-enclave' | 'keychain' | 'encrypted-preferences';
  biometricRequired: boolean;
  biometricFallbackAllowed: boolean;
};

export type PhiCachePolicy = {
  ttlMinutes: number;
  sessionBinding: boolean;
  clearOnRevoke: boolean;
  clearOnLogout: boolean;
  clearOnSessionExpiry: boolean;
  clearOnRoleChange: boolean;
  clearOnPersonaChange: boolean;
};

export type PhiFieldPolicy = {
  fieldName: string;
  classification: 'restricted' | 'sensitive' | 'standard';
  cacheAllowed: boolean;
  exportAllowed: boolean;
  displayAllowed: boolean;
  requiresExplicitConsent: boolean;
};

export type MobilePhiPolicy = {
  storage: PhiStoragePolicy;
  cache: PhiCachePolicy;
  fieldPolicies: PhiFieldPolicy[];
  emergencyOverride: {
    allowed: boolean;
    requiresBiometric: boolean;
    auditRequired: boolean;
  };
};

export type MobilePhiPolicyCheckRequest = {
  principalId: string;
  sessionId: string;
  action: 'read' | 'write' | 'cache' | 'export' | 'display';
  fieldNames?: string[];
  emergencyMode?: boolean;
};

export type MobilePhiPolicyCheckResult = {
  allowed: boolean;
  reasonCode: string;
  requirements: {
    biometricRequired: boolean;
    explicitConsentRequired: boolean;
    auditRequired: boolean;
  };
  fieldResults?: Array<{
    fieldName: string;
    allowed: boolean;
    reason: string;
  }>;
};

export function createDefaultPhiStoragePolicy(): PhiStoragePolicy {
  return {
    encryptionRequired: true,
    encryptionAlgorithm: 'AES-GCM',
    keyDerivationMethod: 'PBKDF2',
    keyStorage: 'secure-enclave',
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

export function validatePhiStoragePolicy(policy: PhiStoragePolicy): boolean {
  if (!policy.encryptionRequired) return false;
  if (!['AES-GCM', 'AES-CBC', 'custom'].includes(policy.encryptionAlgorithm)) return false;
  if (!['PBKDF2', 'scrypt', 'argon2', 'custom'].includes(policy.keyDerivationMethod)) return false;
  if (!['secure-enclave', 'keychain', 'encrypted-preferences'].includes(policy.keyStorage)) return false;
  return true;
}

export function validatePhiCachePolicy(policy: PhiCachePolicy): boolean {
  if (policy.ttlMinutes <= 0) return false;
  if (!policy.sessionBinding) return false;
  if (!policy.clearOnRevoke) return false;
  if (!policy.clearOnLogout) return false;
  if (!policy.clearOnSessionExpiry) return false;
  return true;
}
