export type ConsentUseCase =
  | "ai_tutor"
  | "learning_telemetry"
  | "voice_image"
  | "social"
  | "personalization";

export interface ConsentState {
  useCase: ConsentUseCase;
  granted: boolean;
  revoked: boolean;
  parentalConsentGranted?: boolean;
  learnerAge?: number;
}

export interface ConsentDecision {
  allowed: boolean;
  reason:
    | "allowed"
    | "missing_consent"
    | "revoked_consent"
    | "parental_consent_required"
    | "telemetry_opt_out";
}

export class ConsentPolicyError extends Error {
  constructor(
    message: string,
    public readonly decision: ConsentDecision,
  ) {
    super(message);
    this.name = "ConsentPolicyError";
  }
}

export function evaluateConsent(state: ConsentState): ConsentDecision {
  if (typeof state.learnerAge === "number" && state.learnerAge < 13 && !state.parentalConsentGranted) {
    return { allowed: false, reason: "parental_consent_required" };
  }

  if (state.revoked) {
    return {
      allowed: false,
      reason: state.useCase === "learning_telemetry" ? "telemetry_opt_out" : "revoked_consent",
    };
  }

  if (!state.granted) {
    return { allowed: false, reason: "missing_consent" };
  }

  return { allowed: true, reason: "allowed" };
}

export function assertConsentAllowed(state: ConsentState): void {
  const decision = evaluateConsent(state);
  if (!decision.allowed) {
    throw new ConsentPolicyError(
      `Consent denied for ${state.useCase}: ${decision.reason}`,
      decision,
    );
  }
}
