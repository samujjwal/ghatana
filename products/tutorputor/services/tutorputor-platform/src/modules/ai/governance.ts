import { assertConsentAllowed } from "../compliance/consentPolicy.js";
import type { PrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";

// Local type definitions to avoid import issues
type AIUseCase = "tutor_query" | "content_generation" | "intent_parsing" | "simulation_explanation" | "query_parsing";
type AISafetyFilterResult = "passed" | "blocked" | "human_review_required" | "redacted";

interface AIInteractionGovernanceMetadata {
  consentState: "granted" | "missing" | "revoked" | "not_required";
  learnerContextScope:
    | "none"
    | "module"
    | "claim"
    | "simulation"
    | "assessment"
    | "course";
  promptVersion: string;
  modelVersion: string;
  retrievedContentIds: string[];
  safetyFilterResult: AISafetyFilterResult;
  latencyMs?: number;
  tokenUsage?: {
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
  };
  costUsd?: number;
  confidence?: number;
  humanReviewRequired: boolean;
  humanReviewReason?: string;
}

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
  tenantId?: TenantId;
  userId?: UserId;
  contentToFilter?: string;
}

/**
 * Fetch actual consent state from database
 */
async function fetchActualConsentState(
  prisma: PrismaClient,
  tenantId: TenantId,
  userId: UserId,
): Promise<"granted" | "missing" | "revoked" | "not_required"> {
  try {
    const userConsent = await prisma.userConsent.findFirst({
      where: {
        userId,
        category: "ai_processing",
        tenantId,
      },
    });

    if (!userConsent) {
      return "missing";
    }

    return userConsent.granted ? "granted" : "revoked";
  } catch (error) {
    // Log error but default to missing to be safe
    console.warn("Failed to fetch consent state, defaulting to missing", error);
    return "missing";
  }
}

/**
 * Run actual safety filter check on content
 */
async function runSafetyFilter(
  content: string,
): Promise<"passed" | "blocked" | "human_review_required" | "redacted"> {
  // Placeholder for actual safety filter implementation
  // TODO: Integrate with real safety filter service
  if (!content || content.length === 0) {
    return "passed";
  }
  
  // Basic heuristic checks (replace with actual safety filter)
  const unsafePatterns = [
    /password/i,
    /credit card/i,
    /ssn/i,
    /social security/i,
  ];
  
  for (const pattern of unsafePatterns) {
    if (pattern.test(content)) {
      return "blocked";
    }
  }
  
  return "passed";
}

export async function buildAIGovernanceMetadata(
  args: BuildAIGovernanceMetadataArgs & { prisma?: PrismaClient },
): Promise<AIInteractionGovernanceMetadata> {
  let consentState = args.consentState ?? "granted";
  let safetyFilterResult = args.safetyFilterResult ?? "passed";

  // Fetch actual consent state if tenantId and userId are provided
  if (args.prisma && args.tenantId && args.userId && !args.consentState) {
    consentState = await fetchActualConsentState(args.prisma, args.tenantId, args.userId);
  }

  // Run safety filter if content is provided
  if (args.contentToFilter && !args.safetyFilterResult) {
    safetyFilterResult = await runSafetyFilter(args.contentToFilter);
  }

  return {
    consentState,
    learnerContextScope: args.learnerContextScope,
    promptVersion: args.promptVersion ?? "tutorputor-ai-v1",
    modelVersion: args.modelVersion ?? "unknown",
    retrievedContentIds: args.retrievedContentIds ?? [],
    safetyFilterResult,
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
  const consentRevoked = governance.consentState === "revoked";

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
    revoked: consentRevoked,
    ...(typeof learner?.age === "number" ? { learnerAge: learner.age } : {}),
    ...(typeof learner?.parentalConsentGranted === "boolean"
      ? { parentalConsentGranted: learner.parentalConsentGranted }
      : {}),
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
