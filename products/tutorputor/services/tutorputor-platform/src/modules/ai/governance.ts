import { assertConsentAllowed } from "../compliance/consentPolicy.js";
import type { PrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { createHash } from "crypto";

// Local type definitions to avoid import issues
type AIUseCase = "tutor_query" | "content_generation" | "intent_parsing" | "simulation_explanation" | "query_parsing";
type AISafetyFilterResult = "passed" | "blocked" | "human_review_required" | "redacted";

/**
 * Typed AI Governance Decision Envelope
 * 
 * Encapsulates the decision-making process for AI interactions with
 * explicit separation of success/failure metadata for auditability.
 */
export interface AIGovernanceDecision {
  /** Whether the interaction was allowed */
  allowed: boolean;
  /** Primary reason for the decision */
  reason: GovernanceReason;
  /** Detailed failure information if not allowed */
  failure?: GovernanceFailure;
  /** Consent state at time of decision */
  consentState: AIInteractionGovernanceMetadata["consentState"];
  /** Safety filter result */
  safetyFilterResult: AISafetyFilterResult;
  /** Timestamp of decision */
  decidedAt: string;
  /** Additional context */
  context?: Record<string, unknown>;
}

/**
 * Reasons for governance decisions
 */
export type GovernanceReason =
  | "consent_granted"
  | "consent_missing"
  | "consent_revoked"
  | "safety_blocked"
  | "human_review_required"
  | "rate_limited"
  | "validation_error"
  | "service_error"
  | "allowed";

/**
 * Detailed failure information for audit trails
 */
export interface GovernanceFailure {
  /** Category of failure */
  category: "consent" | "safety" | "rate_limit" | "validation" | "service";
  /** Specific error code */
  code: string;
  /** Human-readable error message */
  message: string;
  /** Whether this failure is retryable */
  retryable: boolean;
  /** Recommended action for user */
  recommendedAction?: string;
  /** Additional diagnostic information */
  diagnostics?: Record<string, unknown>;
}

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
 * Rule-based safety filter for learner-submitted content.
 *
 * Checks content against categories of unsafe patterns. Returns:
 * - "blocked"              — content matches a clearly unsafe pattern
 * - "human_review_required" — content matches a borderline pattern
 * - "passed"               — no unsafe patterns detected
 *
 * This implementation is the defence-in-depth layer. Integrating an external
 * moderation API (e.g. OpenAI Moderation, AWS Comprehend Moderation) will replace
 * the regex-only checks when the platform AI safety service is available, while
 * keeping this function's signature and contract unchanged.
 */
async function runSafetyFilter(
  content: string,
): Promise<"passed" | "blocked" | "human_review_required" | "redacted"> {
  if (!content || content.trim().length === 0) {
    return "passed";
  }

  // Patterns that should be blocked outright — PII, credentials, self-harm triggers
  const blockedPatterns: RegExp[] = [
    /\bpassword\b/i,
    /\bcredit\s*card\b/i,
    /\b(ssn|social\s*security\s*number)\b/i,
    /\bsocial\s*security\b/i,
    /\b(api[_-]?key|secret[_-]?key|access[_-]?token|bearer\s+[a-z0-9._-]{20,})\b/i,
    /\b(kill|suicide|self[_-]?harm|cut\s+myself)\b/i,
    /\b(bomb|explosive|weapon\s+of\s+mass)\b/i,
    /\b(child\s+abuse|csam)\b/i,
  ];

  // Patterns that warrant human review before proceeding
  const reviewPatterns: RegExp[] = [
    /\b(hate|racist|sexist|discriminat)\b/i,
    /\b(drug|narcotic|controlled\s+substance)\b/i,
    /\b(personal\s+information|private\s+data)\b/i,
    /\b(copyright|plagiari)\b/i,
    /[^a-zA-Z0-9\s.,!?'"()\-–—:;@#$%&*+=[\]{}|<>/\\]{5,}/u, // high density of unusual chars
  ];

  for (const pattern of blockedPatterns) {
    if (pattern.test(content)) {
      console.warn("[SafetyFilter] Blocked — pattern matched", {
        patternSource: pattern.source,
        contentLength: content.length,
      });
      return "blocked";
    }
  }

  for (const pattern of reviewPatterns) {
    if (pattern.test(content)) {
      console.warn("[SafetyFilter] Human review required — borderline pattern matched", {
        patternSource: pattern.source,
        contentLength: content.length,
      });
      return "human_review_required";
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

/**
 * Create a typed AI Governance Decision from governance metadata
 * 
 * This function centralizes decision-making logic and produces a typed
 * decision envelope with explicit failure information for audit trails.
 */
export function createAIGovernanceDecision(
  governance: AIInteractionGovernanceMetadata,
  learner?: { age?: number; parentalConsentGranted?: boolean },
): AIGovernanceDecision {
  const consentRevoked = governance.consentState === "revoked";
  const decidedAt = new Date().toISOString();

  // Check consent state
  if (governance.consentState === "missing") {
    return {
      allowed: false,
      reason: "consent_missing",
      failure: {
        category: "consent",
        code: "AI_CONSENT_MISSING",
        message: "AI interaction blocked because consent is missing",
        retryable: false,
        recommendedAction: "Request user to grant AI processing consent",
      },
      consentState: governance.consentState,
      safetyFilterResult: governance.safetyFilterResult,
      decidedAt,
    };
  }

  if (governance.consentState === "revoked") {
    return {
      allowed: false,
      reason: "consent_revoked",
      failure: {
        category: "consent",
        code: "AI_CONSENT_REVOKED",
        message: "AI interaction blocked because consent was revoked",
        retryable: false,
        recommendedAction: "Contact support to restore consent if this was in error",
      },
      consentState: governance.consentState,
      safetyFilterResult: governance.safetyFilterResult,
      decidedAt,
    };
  }

  // Verify consent policy
  try {
    assertConsentAllowed({
      useCase: "ai_tutor",
      granted: governance.consentState === "granted" || governance.consentState === "not_required",
      revoked: consentRevoked,
      ...(typeof learner?.age === "number" ? { learnerAge: learner.age } : {}),
      ...(typeof learner?.parentalConsentGranted === "boolean"
        ? { parentalConsentGranted: learner.parentalConsentGranted }
        : {}),
    });
  } catch (error) {
    return {
      allowed: false,
      reason: "consent_revoked",
      failure: {
        category: "consent",
        code: "AI_CONSENT_POLICY_VIOLATION",
        message: error instanceof Error ? error.message : "Consent policy violation",
        retryable: false,
        recommendedAction: "Review consent settings",
      },
      consentState: governance.consentState,
      safetyFilterResult: governance.safetyFilterResult,
      decidedAt,
    };
  }

  // Check safety filter
  if (governance.safetyFilterResult === "blocked") {
    return {
      allowed: false,
      reason: "safety_blocked",
      failure: {
        category: "safety",
        code: "AI_SAFETY_BLOCKED",
        message: "AI interaction blocked by safety filter",
        retryable: false,
        recommendedAction: "Modify input content to remove blocked patterns",
        diagnostics: { safetyFilterResult: governance.safetyFilterResult },
      },
      consentState: governance.consentState,
      safetyFilterResult: governance.safetyFilterResult,
      decidedAt,
    };
  }

  if (
    governance.humanReviewRequired ||
    governance.safetyFilterResult === "human_review_required"
  ) {
    return {
      allowed: false,
      reason: "human_review_required",
      failure: {
        category: "safety",
        code: "AI_HUMAN_REVIEW_REQUIRED",
        message: "AI interaction requires human review before release",
        retryable: false,
        recommendedAction: "Submit for human review",
        diagnostics: { humanReviewRequired: governance.humanReviewRequired },
      },
      consentState: governance.consentState,
      safetyFilterResult: governance.safetyFilterResult,
      decidedAt,
    };
  }

  // All checks passed
  return {
    allowed: true,
    reason: "allowed",
    consentState: governance.consentState,
    safetyFilterResult: governance.safetyFilterResult,
    decidedAt,
  };
}

export function assertAIInteractionAllowed(
  governance: AIInteractionGovernanceMetadata,
  learner?: { age?: number; parentalConsentGranted?: boolean },
): void {
  const decision = createAIGovernanceDecision(governance, learner);

  if (!decision.allowed) {
    throw new AIGovernanceError(
      decision.failure?.message || "AI interaction not allowed",
      decision.failure?.code as AIGovernanceError["code"],
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

// Constants for audit payload size limits
const MAX_STRING_LENGTH = 120;
const MAX_ARRAY_ITEMS = 10;
const MAX_OBJECT_DEPTH = 3;
const MAX_TOTAL_PAYLOAD_SIZE_BYTES = 10240; // 10KB

function computeHash(value: string): string {
  return createHash('sha256').update(value).digest('hex').substring(0, 16);
}

function computeSize(value: unknown): number {
  return JSON.stringify(value).length;
}

function keepNonPiiValue(
  value: unknown,
  depth: number = 0,
  currentSize: number = 0,
): { value: unknown; size: number } {
  if (depth > MAX_OBJECT_DEPTH) {
    return { value: "[max_depth_exceeded]", size: 24 };
  }

  if (typeof value === "string") {
    const truncated = value.length > MAX_STRING_LENGTH
      ? `${value.slice(0, MAX_STRING_LENGTH - 3)}...`
      : value;
    return { value: truncated, size: truncated.length };
  }

  if (Array.isArray(value)) {
    const result: unknown[] = [];
    let size = 2; // brackets
    for (let i = 0; i < Math.min(value.length, MAX_ARRAY_ITEMS); i++) {
      const { value: item, size: itemSize } = keepNonPiiValue(value[i], depth + 1);
      result.push(item);
      size += itemSize + 1; // +1 for comma
    }
    if (value.length > MAX_ARRAY_ITEMS) {
      result.push(`...(${value.length - MAX_ARRAY_ITEMS} more items)`);
      size += 30;
    }
    return { value: result, size };
  }

  if (value && typeof value === "object") {
    const { sanitized, size: objSize } = sanitizeNonPiiRecord(
      value as Record<string, unknown>,
      depth + 1,
    );
    return { value: sanitized, size: objSize };
  }

  const strVal = String(value);
  return { value: strVal, size: strVal.length };
}

export function sanitizeNonPiiRecord(
  payload: Record<string, unknown>,
  depth: number = 0,
): { sanitized: Record<string, unknown>; size: number } {
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

  // Keys that should be replaced with hash references instead of full content
  const hashReferenceKeys = new Set([
    "currentSimulationState",
    "recentAttempts",
    "misconceptions",
  ]);

  const sanitized: Record<string, unknown> = {};
  let totalSize = 2; // braces

  for (const [key, value] of Object.entries(payload)) {
    if (blockedKeys.has(key)) {
      sanitized[`${key}Redacted`] = true;
      totalSize += `${key}Redacted`.length + 5; // :true
      continue;
    }

    if (hashReferenceKeys.has(key) && value) {
      const hash = computeHash(JSON.stringify(value));
      sanitized[`${key}Hash`] = hash;
      sanitized[`${key}Present`] = true;
      totalSize += `${key}Hash`.length + hash.length + 10;
      continue;
    }

    const { value: processedValue, size: valueSize } = keepNonPiiValue(value, depth);
    sanitized[key] = processedValue;
    totalSize += key.length + valueSize + 2; // key: value
  }

  return { sanitized, size: totalSize };
}

export function buildAIAuditPayload(args: AIAuditPayloadArgs): {
  requestPayload: string;
  responsePayload?: string;
} {
  const { sanitized: sanitizedRequest, size: requestSize } = sanitizeNonPiiRecord(args.request);
  const requestPayload = JSON.stringify({
    endpoint: args.endpoint,
    useCase: args.useCase,
    governance: {
      ...args.governance,
      containsDirectPii: false,
    },
    request: sanitizedRequest,
  });

  let responsePayload: string | undefined;
  if (args.response) {
    const { sanitized: sanitizedResponse } = sanitizeNonPiiRecord(args.response);
    responsePayload = JSON.stringify({
      endpoint: args.endpoint,
      useCase: args.useCase,
      governance: {
        modelVersion: args.governance.modelVersion,
        promptVersion: args.governance.promptVersion,
        safetyFilterResult: args.governance.safetyFilterResult,
        humanReviewRequired: args.governance.humanReviewRequired,
        containsDirectPii: false,
      },
      response: sanitizedResponse,
    });
  }

  return {
    requestPayload,
    ...(responsePayload ? { responsePayload } : {}),
  };
}
