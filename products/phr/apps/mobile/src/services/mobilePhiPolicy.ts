import {
  createDefaultMobilePhiPolicy,
  evaluateMobilePhiPolicyCheck,
  type PhiFieldPolicy,
} from "@ghatana/kernel-product-contracts/policy";

const PRODUCT_UNIT_ID = "phr-mobile";
const OFFLINE_CACHE_POLICY_SESSION_ID = "offline-cache-policy";

export const PHR_MOBILE_PHI_FIELD_POLICIES = [
  {
    fieldName: "nationalId",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: false,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "mentalHealth",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "substanceUse",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "substanceAbuseHistory",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "geneticInfo",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "reproductiveHealth",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "hivStatus",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
  {
    fieldName: "psychiatricHistory",
    classification: "restricted",
    cacheAllowed: false,
    exportAllowed: false,
    displayAllowed: true,
    requiresExplicitConsent: true,
  },
] satisfies readonly PhiFieldPolicy[];

export const PHR_MOBILE_PHI_POLICY = createDefaultMobilePhiPolicy([
  ...PHR_MOBILE_PHI_FIELD_POLICIES,
]);

export function shouldRemoveFieldFromMobileCache(fieldName: string): boolean {
  if (!PHR_MOBILE_PHI_FIELD_POLICIES.some((policy) => policy.fieldName === fieldName)) {
    return false;
  }

  const result = evaluateMobilePhiPolicyCheck(PHR_MOBILE_PHI_POLICY, {
    principalId: PRODUCT_UNIT_ID,
    sessionId: OFFLINE_CACHE_POLICY_SESSION_ID,
    action: "cache",
    fieldNames: [fieldName],
  });

  return !result.allowed;
}
