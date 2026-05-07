import { describe, expect, it } from "vitest";
import {
  ConsentPolicyError,
  assertConsentAllowed,
  evaluateConsent,
  type ConsentUseCase,
} from "../consentPolicy";

describe("consent policy", () => {
  it("blocks under-13 learners until parental consent is granted", () => {
    expect(evaluateConsent({
      useCase: "ai_tutor",
      granted: true,
      revoked: false,
      learnerAge: 12,
      parentalConsentGranted: false,
    })).toMatchObject({ allowed: false, reason: "parental_consent_required" });

    expect(evaluateConsent({
      useCase: "ai_tutor",
      granted: true,
      revoked: false,
      learnerAge: 12,
      parentalConsentGranted: true,
    })).toMatchObject({ allowed: true });
  });

  it("blocks AI, voice/image, social, and personalization when consent is missing or revoked", () => {
    const useCases: ConsentUseCase[] = ["ai_tutor", "voice_image", "social", "personalization"];
    for (const useCase of useCases) {
      expect(evaluateConsent({ useCase, granted: false, revoked: false })).toMatchObject({
        allowed: false,
        reason: "missing_consent",
      });
      expect(evaluateConsent({ useCase, granted: true, revoked: true })).toMatchObject({
        allowed: false,
        reason: "revoked_consent",
      });
    }
  });

  it("honors telemetry opt-out immediately", () => {
    expect(evaluateConsent({
      useCase: "learning_telemetry",
      granted: true,
      revoked: true,
    })).toMatchObject({ allowed: false, reason: "telemetry_opt_out" });
  });

  it("throws a typed error for runtime enforcement", () => {
    expect(() => assertConsentAllowed({
      useCase: "personalization",
      granted: false,
      revoked: false,
    })).toThrow(ConsentPolicyError);
  });
});
