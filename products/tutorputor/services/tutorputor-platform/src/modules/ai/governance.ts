import type {
  AIInteractionGovernanceMetadata,
  AIUseCase,
} from "@tutorputor/contracts/v1/services";
import { assertConsentAllowed } from "../compliance/consentPolicy.js";

export class AIGovernanceError extends Error {
  constructor(
    message: string,
    public readonly code:
      | "AI_CONSENT_MISSING"
      | "AI_CONSENT_REVOKED"
      | "AI_SAFETY_BLOCKED"
      | "AI_HUMAN_REVIEW_REQUIRED",
  ) {
    super(message);
    this.name = "AIGovernanceError";
  }
}

export interface BuildAIGovernanceMetadataArgs {
  consentState?: AIInteractionGovernanceMetadata["consentState"];
  learnerContextScope: AIInteractionGovernanceMetadata["learnerContextScope"];
  promptVersion?: string;
  modelVersion?: string;
  retrievedContentIds?: string[];
  safetyFilterResult?: AIInteractionGovernanceMetadata["safetyFilterResult"];
  latencyMs?: number;
  tokenUsage?: AIInteractionGovernanceMetadata["tokenUsage"];
  costUsd?: number;
  confidence?: number;
  humanReviewRequired?: boolean;
  humanReviewReason?: string;
  learnerAge?: number;
  parentalConsentGranted?: boolean;
}

export function buildAIGovernanceMetadata(
  args: BuildAIGovernanceMetadataArgs,
): AIInteractionGovernanceMetadata {
  return {
    consentState: args.consentState ?? "granted",
    learnerContextScope: args.learnerContextScope,
    promptVersion: args.promptVersion ?? "tutorputor-ai-v1",
    modelVersion: args.modelVersion ?? "unknown",
    retrievedContentIds: args.retrievedContentIds ?? [],
    safetyFilterResult: args.safetyFilterResult ?? "passed",
    ...(typeof args.latencyMs === "number" ? { latencyMs: args.latencyMs } : {}),
    ...(args.tokenUsage ? { tokenUsage: args.tokenUsage } : {}),
    ...(typeof args.costUsd === "number" ? { costUsd: args.costUsd } : {}),
    ...(typeof args.confidence === "number" ? { confidence: args.confidence } : {}),
    humanReviewRequired: args.humanReviewRequired ?? false,
    ...(args.humanReviewReason
      ? { humanReviewReason: args.humanReviewReason }
      : {}),
  };
}

export function assertAIInteractionAllowed(
  governance: AIInteractionGovernanceMetadata,
  learner?: { age?: number; parentalConsentGranted?: boolean },
): void {
  if (governance.consentState === "missing") {
    throw new AIGovernanceError(
      "AI interaction blocked because consent is missing.",
      "AI_CONSENT_MISSING",
    );
  }

  if (governance.consentState === "revoked") {
    throw new AIGovernanceError(
      "AI interaction blocked because consent was revoked.",
      "AI_CONSENT_REVOKED",
    );
  }

  assertConsentAllowed({
    useCase: "ai_tutor",
    granted: governance.consentState === "granted" || governance.consentState === "not_required",
    revoked: governance.consentState === "revoked",
    learnerAge: learner?.age,
    parentalConsentGranted: learner?.parentalConsentGranted,
  });

  if (governance.safetyFilterResult === "blocked") {
    throw new AIGovernanceError(
      "AI interaction blocked by safety filter.",
      "AI_SAFETY_BLOCKED",
    );
  }

  if (
    governance.humanReviewRequired ||
    governance.safetyFilterResult === "human_review_required"
  ) {
    throw new AIGovernanceError(
      "AI interaction requires human review before release.",
      "AI_HUMAN_REVIEW_REQUIRED",
    );
  }
}

export interface AIAuditPayloadArgs {
  endpoint: string;
  useCase: AIUseCase;
  governance: AIInteractionGovernanceMetadata;
  request: Record<string, unknown>;
  response?: Record<string, unknown>;
}

function keepNonPiiValue(value: unknown): unknown {
  if (typeof value === "string") {
    return value.length > 120 ? `${value.slice(0, 117)}...` : value;
  }
  if (Array.isArray(value)) {
    return value.map(keepNonPiiValue);
  }
  if (value && typeof value === "object") {
    return sanitizeNonPiiRecord(value as Record<string, unknown>);
  }
  return value;
}

export function sanitizeNonPiiRecord(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  const blockedKeys = new Set([
    "answer",
    "email",
    "fullName",
    "name",
    "phone",
    "prompt",
    "question",
    "response",
    "studentName",
    "text",
    "transcript",
    "userInput",
    "voice",
  ]);
  const sanitized: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(payload)) {
    if (blockedKeys.has(key)) {
      sanitized[`${key}Redacted`] = true;
      continue;
    }
    sanitized[key] = keepNonPiiValue(value);
  }

  return sanitized;
}

export function buildAIAuditPayload(args: AIAuditPayloadArgs): {
  requestPayload: string;
  responsePayload?: string;
} {
  const requestPayload = JSON.stringify({
    endpoint: args.endpoint,
    useCase: args.useCase,
    governance: {
      ...args.governance,
      containsDirectPii: false,
    },
    request: sanitizeNonPiiRecord(args.request),
  });

  const responsePayload = args.response
    ? JSON.stringify({
        endpoint: args.endpoint,
        useCase: args.useCase,
        governance: {
          modelVersion: args.governance.modelVersion,
          promptVersion: args.governance.promptVersion,
          safetyFilterResult: args.governance.safetyFilterResult,
          humanReviewRequired: args.governance.humanReviewRequired,
          containsDirectPii: false,
        },
        response: sanitizeNonPiiRecord(args.response),
      })
    : undefined;

  return {
    requestPayload,
    ...(responsePayload ? { responsePayload } : {}),
  };
}
