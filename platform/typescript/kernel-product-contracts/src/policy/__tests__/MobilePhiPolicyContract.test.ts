import { describe, expect, it } from "vitest";
import {
  createDefaultMobilePhiPolicy,
  evaluateMobilePhiPolicyCheck,
  validateMobilePhiReleaseGate,
  validatePhiCachePolicy,
  validatePhiStoragePolicy,
  type MobilePhiReleaseValidationInput,
  type PhiFieldPolicy,
} from "../../index.js";

const restrictedFieldPolicy: PhiFieldPolicy = {
  fieldName: "diagnosis",
  classification: "restricted",
  cacheAllowed: false,
  exportAllowed: false,
  displayAllowed: true,
  requiresExplicitConsent: true,
};

const standardFieldPolicy: PhiFieldPolicy = {
  fieldName: "appointmentReminder",
  classification: "standard",
  cacheAllowed: true,
  exportAllowed: true,
  displayAllowed: true,
  requiresExplicitConsent: false,
};

function makeReleaseInput(
  overrides: Partial<MobilePhiReleaseValidationInput> = {},
): MobilePhiReleaseValidationInput {
  return {
    productUnitId: "phr",
    policy: createDefaultMobilePhiPolicy([restrictedFieldPolicy, standardFieldPolicy]),
    evidence: {
      encryptedStorageVerified: true,
      sessionBindingVerified: true,
      restrictedFieldPurgeVerified: true,
      logoutClearVerified: true,
      consentRevokeClearVerified: true,
      sessionExpiryClearVerified: true,
      rolePersonaChangeClearVerified: true,
      emergencyBiometricGateVerified: true,
      emergencyServerAuthorizationVerified: true,
    },
    ...overrides,
  };
}

describe("mobile PHI policy validation", () => {
  it("accepts the default encrypted storage and cache clearing policies", () => {
    const policy = createDefaultMobilePhiPolicy([restrictedFieldPolicy]);

    expect(validatePhiStoragePolicy(policy.storage)).toBe(true);
    expect(validatePhiCachePolicy(policy.cache)).toBe(true);
  });

  it("denies restricted fields that are not allowed for cache or export", () => {
    const policy = createDefaultMobilePhiPolicy([restrictedFieldPolicy, standardFieldPolicy]);

    const result = evaluateMobilePhiPolicyCheck(policy, {
      principalId: "patient-1",
      sessionId: "session-1",
      action: "cache",
      fieldNames: ["diagnosis", "appointmentReminder"],
    });

    expect(result.allowed).toBe(false);
    expect(result.reasonCode).toBe("cache-not-allowed");
    expect(result.requirements.explicitConsentRequired).toBe(true);
    expect(result.fieldResults).toContainEqual({
      fieldName: "diagnosis",
      allowed: false,
      reason: "cache-not-allowed",
    });
  });

  it("requires biometric, server authorization, and audit for emergency PHI access", () => {
    const policy = createDefaultMobilePhiPolicy([restrictedFieldPolicy]);

    const result = evaluateMobilePhiPolicyCheck(policy, {
      principalId: "clinician-1",
      sessionId: "session-2",
      action: "display",
      fieldNames: ["diagnosis"],
      emergencyMode: true,
    });

    expect(result.allowed).toBe(true);
    expect(result.requirements).toMatchObject({
      biometricRequired: true,
      auditRequired: true,
      serverAuthorizationRequired: true,
    });
  });
});

describe("mobile PHI release gate", () => {
  it("passes only when all required mobile privacy evidence is present", () => {
    const result = validateMobilePhiReleaseGate(makeReleaseInput());

    expect(result.status).toBe("passed");
    expect(result.failedChecks).toEqual([]);
  });

  it("fails closed when revocation clearing, session binding, or emergency authorization evidence is missing", () => {
    const result = validateMobilePhiReleaseGate(
      makeReleaseInput({
        evidence: {
          encryptedStorageVerified: true,
          sessionBindingVerified: false,
          restrictedFieldPurgeVerified: true,
          logoutClearVerified: true,
          consentRevokeClearVerified: false,
          sessionExpiryClearVerified: true,
          rolePersonaChangeClearVerified: true,
          emergencyBiometricGateVerified: true,
          emergencyServerAuthorizationVerified: false,
        },
      }),
    );

    expect(result.status).toBe("failed");
    expect(result.failedChecks).toEqual(
      expect.arrayContaining([
        "session-binding-evidence",
        "consent-revoke-clear-evidence",
        "emergency-server-authorization-evidence",
      ]),
    );
  });

  it("fails when a policy allows emergency access without biometric or server authorization", () => {
    const unsafePolicy = createDefaultMobilePhiPolicy([restrictedFieldPolicy]);
    unsafePolicy.emergencyOverride.requiresBiometric = false;
    unsafePolicy.emergencyOverride.requiresServerAuthorization = false;

    const result = validateMobilePhiReleaseGate(makeReleaseInput({ policy: unsafePolicy }));

    expect(result.status).toBe("failed");
    expect(result.failedChecks).toEqual(
      expect.arrayContaining([
        "emergency-biometric-policy",
        "emergency-server-authorization-policy",
      ]),
    );
  });
});
