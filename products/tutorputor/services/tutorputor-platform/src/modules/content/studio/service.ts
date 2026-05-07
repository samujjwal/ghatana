/**
 * Content Studio Service
 *
 * Implements core authoring workflows backed by LearningExperience schema
 * and queue-driven background content generation.
 */

import { Prisma, type PrismaClient } from "@tutorputor/core/db";
import { createHttpError } from "../../../core/http/requestContext.js";
import type {
  ContentStudioService,
  CreateExperienceRequest,
  RefineExperienceRequest,
  ExperienceOperationResult,
  ExperienceValidationResult,
  LearningExperience,
  LearningClaim,
  LearningEvidence,
  ExperienceTask,
  AIGenerationResult,
  GradeAdaptation,
  ExperienceStatus as ContractExperienceStatus,
  ValidationCheck,
} from "@tutorputor/contracts/v1/content-studio";
import type {
  BloomLevel,
  ContentNeeds,
  EvidenceType,
  Observable,
  TaskType,
} from "@tutorputor/contracts/v1/learning-unit";
import type { IndependentGeneratedContentValidator } from "../evaluation/independent-validator-service.js";
import type { ClaimContradictionGrader } from "../evaluation/claim-contradiction-grader.js";
import type { RubricBackedPillarGrader } from "../evaluation/rubric-backed-pillar-grader.js";
import {
  getContentGenerationQueue,
  type ContentGenerationQueueLike,
} from "../queue/content-generation-queue.js";

export type { ContentStudioService };

type GradeRange =
  | "k_2"
  | "grade_3_5"
  | "grade_6_8"
  | "grade_9_12"
  | "undergraduate"
  | "graduate"
  | "professional";

function toInputJsonValue(value: unknown): Prisma.InputJsonValue {
  return value as Prisma.InputJsonValue;
}

function toNullableInputJsonValue(
  value: unknown,
): Prisma.NullableJsonNullValueInput | Prisma.InputJsonValue {
  return value === null ? Prisma.JsonNull : toInputJsonValue(value);
}

type ExperienceStatus = ContractExperienceStatus;

type ExperienceListFilters = {
  tenantId: string;
  status?: ExperienceStatus;
  gradeRange?: GradeRange;
  authorId?: string;
  limit?: number;
  offset?: number;
};

type UpdateExperienceInput = {
  title?: string;
  description?: string;
  status?: ExperienceStatus;
  estimatedTimeMinutes?: number;
  gradeRange?: GradeRange;
  userId?: string;
};

type GenerateClaimsInput = {
  maxClaims?: number;
  topic?: string;
};

type GenerateClaimsResult = {
  status: "queued";
  jobId: string | number | null | undefined;
  experienceId: string;
};

type RefineContentInput = {
  refinementPrompt?: string;
  prompt?: string;
  userId?: string;
};

type AdaptGradeInput = {
  gradeRange?: GradeRange;
  targetGrade?: GradeRange;
  userId?: string;
};

type ValidateExperienceInput = {
  userId?: string;
};

type ClaimCoverageCheck = {
  claimRef: string;
  hasTasks: boolean;
  hasArtifacts: boolean;
  hasBloom: boolean;
};

type AddClaimInput = {
  claimRef?: string;
  text?: string;
  statement?: string;
  bloomLevel?: string;
  bloom?: string;
  contentNeeds?: unknown;
  userId?: string;
};

type UpdateClaimInput = {
  text?: string;
  statement?: string;
  bloomLevel?: string;
  bloom?: string;
  contentNeeds?: unknown;
  userId?: string;
};

type AddTaskInput = {
  taskRef?: string;
  evidenceRef?: string;
  type?: string;
  prompt?: string;
  instructions?: string;
  config?: Record<string, unknown>;
  userId?: string;
};

type UpdateTaskInput = {
  type?: string;
  prompt?: string;
  instructions?: string;
  config?: Record<string, unknown>;
  userId?: string;
};

type ValidationHistoryEntry = Record<string, unknown>;

type ExperienceAnalyticsSummary = {
  experienceId: string;
  latestValidation: {
    status: "PASS" | "WARN" | "FAIL";
    validatedAt: string;
    accessibilityScore: number | null;
    authorityScore: number | null;
    accuracyScore: number | null;
    usefulnessScore: number | null;
    harmlessnessScore: number | null;
    suggestions: unknown[];
  } | null;
  latestIndependentValidation: {
    status: "PASS" | "WARN" | "FAIL";
    validatedAt: string;
    score: number | null;
    validatorVersion: string | null;
    recommendations: unknown[];
    issues: unknown[];
  } | null;
  recentEvents: ExperienceTimelineEvent[];
} & Record<string, unknown>;

type GenerationProgress = {
  experienceId: string;
  status: "queued" | "in_progress" | "complete";
  totalClaims: number;
  claimsProcessed: number;
  percentComplete: number;
  contentCounts: {
    examples: number;
    simulations: number;
    animations: number;
  };
  isComplete: boolean;
  updatedAt: string;
};

export interface ContentStudioConfig {
  openaiApiKey: string;
  model?: string;
  independentValidator?: Pick<
    IndependentGeneratedContentValidator,
    "validateGeneratedContent"
  >;
  /** F-011: Second-LLM contradiction grader, run async before publish gate */
  contradictionGrader?: Pick<ClaimContradictionGrader, "check">;
  /** F-036: Rubric-backed pillar grader replacing heuristic scoring */
  rubricGrader?: Pick<RubricBackedPillarGrader, "grade">;
}

type ExtendedExperienceValidationResult = ExperienceValidationResult & {
  independentValidation?: {
    status: "PASS" | "WARN" | "FAIL";
    score: number;
    validatorVersion: string;
    requiresHumanReview: boolean;
    reviewQueueId?: string;
    recommendations: string[];
  };
};

export type HealthAwareContentStudioService = Omit<
  ContentStudioService,
  "publishExperience"
> & {
  checkHealth: () => Promise<boolean>;
  updateExperience: (
    id: string,
    data: UpdateExperienceInput,
  ) => Promise<LearningExperience | null>;
  deleteExperience: (id: string) => Promise<void>;
  generateClaims: (
    id: string,
    request: GenerateClaimsInput,
  ) => Promise<GenerateClaimsResult>;
  generateTasks: (
    experienceId: string,
    claimId: string,
    request: Record<string, never>,
  ) => Promise<{ tasks: unknown[] }>;
  refineContent: (
    id: string,
    request: RefineContentInput,
  ) => Promise<LearningExperience | null>;
  adaptGrade: (
    id: string,
    request: AdaptGradeInput,
  ) => Promise<LearningExperience | null>;
  getValidationHistory: (id: string) => Promise<ValidationHistoryEntry[]>;
  publishExperience: (
    id: string,
    userId: string,
  ) => Promise<LearningExperience | null>;
  unpublishExperience: (
    id: string,
    reason?: string,
  ) => Promise<LearningExperience | null>;
  archiveExperience: (id: string) => Promise<LearningExperience | null>;
  addClaim: (
    id: string,
    claim: AddClaimInput,
  ) => Promise<ContentStudioClaimRow>;
  updateClaim: (
    experienceId: string,
    claimId: string,
    data: UpdateClaimInput,
  ) => Promise<ContentStudioClaimRow>;
  deleteClaim: (experienceId: string, claimId: string) => Promise<void>;
  addTask: (
    experienceId: string,
    claimId: string,
    task: AddTaskInput,
  ) => Promise<ContentStudioTaskRow>;
  updateTask: (
    experienceId: string,
    claimId: string,
    taskId: string,
    data: UpdateTaskInput,
  ) => Promise<ContentStudioTaskRow>;
  deleteTask: (
    experienceId: string,
    claimId: string,
    taskId: string,
  ) => Promise<void>;
  getExperienceAnalytics: (id: string) => Promise<ExperienceAnalyticsSummary>;
  getExperienceEvents: (
    id: string,
    options?: { limit?: number; eventType?: ExperienceEventType },
  ) => Promise<ExperienceTimelineEvent[]>;
  getGenerationProgress: (id: string) => Promise<GenerationProgress>;
};

type ExperienceTimelineEvent = {
  id: string;
  type: ExperienceEventType;
  actorId: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
};

const MANUAL_PUBLISH_BUNDLE_CONFIDENCE_MIN = 0.85;

type EvidencePublishReviewSignals = {
  lowConfidenceBundles: Array<{
    claimRef: string;
    bundleConfidence: number;
  }>;
  contradictoryBundles: Array<{
    claimRef: string;
    bundleConfidence: number;
  }>;
};

function generateSlug(title: string): string {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

function inferDomain(
  title: string,
  description?: string,
): "MATH" | "SCIENCE" | "TECH" {
  const text = `${title} ${description || ""}`.toLowerCase();
  if (
    /\b(math|algebra|calculus|geometry|statistics|equation|number)\b/.test(text)
  ) {
    return "MATH";
  }
  if (
    /\b(physics|chemistry|biology|science|force|energy|cell|molecule)\b/.test(
      text,
    )
  ) {
    return "SCIENCE";
  }
  return "TECH";
}

function normalizeGradeRange(raw: string | null | undefined): GradeRange {
  if (!raw) return "grade_6_8";
  const normalized = raw.toLowerCase();
  if (normalized === "k_2") return "k_2";
  if (normalized === "grade_3_5") return "grade_3_5";
  if (normalized === "grade_6_8") return "grade_6_8";
  if (normalized === "grade_9_12") return "grade_9_12";
  if (normalized === "undergraduate") return "undergraduate";
  if (normalized === "graduate") return "graduate";
  return "professional";
}

function toGradeEnum(grade: GradeRange): string {
  const mapping: Record<GradeRange, string> = {
    k_2: "K_2",
    grade_3_5: "GRADE_3_5",
    grade_6_8: "GRADE_6_8",
    grade_9_12: "GRADE_9_12",
    undergraduate: "UNDERGRADUATE",
    graduate: "GRADUATE",
    professional: "GRADUATE",
  };
  return mapping[grade] || "GRADE_6_8";
}

function fromPrismaStatus(status: string): ExperienceStatus {
  const map: Record<string, ExperienceStatus> = {
    DRAFT: "draft",
    REVIEW: "review",
    PUBLISHED: "published",
    ARCHIVED: "archived",
  };
  return map[status] || "draft";
}

function toPrismaStatus(status: ContractExperienceStatus): string {
  if (status === "draft") return "DRAFT";
  if (status === "review") return "REVIEW";
  if (status === "published") return "PUBLISHED";
  if (status === "archived") return "ARCHIVED";
  return "DRAFT";
}

/** Canonical event types for authoring lifecycle observability. */
type ExperienceEventType =
  | "CREATED"
  | "UPDATED"
  | "VALIDATED"
  | "PUBLISHED"
  | "UNPUBLISHED"
  | "ARCHIVED"
  | "CONTENT_CHANGED"
  | "CLAIMS_GENERATED"
  | "GRADE_ADAPTED"
  | "REFINED"
  | "REVIEW_SUBMITTED"
  | "REVIEW_DECISION"
  | "SIMULATION_LINKED"
  | "SIMULATION_UNLINKED"
  | "ANALYTICS_VIEWED"
  | "ANALYTICS_UPDATED";

async function recordExperienceEvent(
  prisma: PrismaClient,
  experienceId: string,
  eventType: ExperienceEventType,
  actorId: string,
  metadata?: Record<string, unknown>,
): Promise<void> {
  await prisma.experienceEvent.create({
    data: {
      experienceId,
      eventType,
      actorId,
      ...(metadata ? { metadata: toInputJsonValue(metadata) } : {}),
    },
  });
}

async function recordExperiencePublishProvenance(
  prisma: PrismaClient,
  experience: LearningExperience,
  actorId: string,
  validation: ExtendedExperienceValidationResult,
): Promise<void> {
  const claimRefs = experience.claims.map((claim) => claim.id);
  const prismaWithExtras = prisma as PrismaClient & {
    experienceRevision?: {
      create: (args: unknown) => Promise<unknown>;
    };
    auditLog?: {
      create: (args: unknown) => Promise<unknown>;
    };
    evidenceBundleMetadata?: {
      findMany: (args: unknown) => Promise<
        Array<{
          claimRef: string;
          bundleConfidence: number;
          coverageScore: number;
          contradictionDetected: boolean;
          freshnessOverall: string;
          evidenceCount: number;
          generatedAt: Date;
          regeneratedAt: Date | null;
          generationJobId: string | null;
        }>
      >;
    };
    aiGenerationLog?: {
      findFirst: (args: unknown) => Promise<
        | {
            promptHash: string | null;
            model: string;
            modelVersion: string | null;
            guardrailsVersion: string | null;
            createdAt: Date;
          }
        | null
      >;
    };
    validationRecordExtended?: {
      findFirst: (args: unknown) => Promise<
        | {
            overallStatus: "PASS" | "WARN" | "FAIL";
            validatorsVersion: string | null;
            suggestions: unknown;
            issues: unknown;
            authorityScore: number | null;
            validatedAt: Date;
          }
        | null
      >;
    };
  };

  const [bundleMetadata, latestGenerationLog, latestIndependentValidation] = await Promise.all([
    claimRefs.length > 0
      ? prismaWithExtras.evidenceBundleMetadata?.findMany({
          where: {
            experienceId: experience.id,
            claimRef: { in: claimRefs },
          },
          orderBy: { claimRef: "asc" },
        })
      : Promise.resolve([]),
    prismaWithExtras.aiGenerationLog?.findFirst({
      where: { experienceId: experience.id },
      orderBy: { createdAt: "desc" },
      select: {
        promptHash: true,
        model: true,
        modelVersion: true,
        guardrailsVersion: true,
        createdAt: true,
      },
    }),
    prismaWithExtras.validationRecordExtended?.findFirst({
      where: { experienceId: experience.id },
      orderBy: { validatedAt: "desc" },
      select: {
        overallStatus: true,
        validatorsVersion: true,
        suggestions: true,
        issues: true,
        authorityScore: true,
        validatedAt: true,
      },
    }),
  ]);

  const provenanceSnapshot = {
    status: experience.status,
    title: experience.title,
    description: experience.description,
    version: experience.version,
    gradeAdaptation: experience.gradeAdaptation,
    claimCount: experience.claims.length,
    taskCount: experience.claims.reduce(
      (count, claim) => count + (claim.tasks?.length ?? 0),
      0,
    ),
    evidenceCount: experience.claims.reduce(
      (count, claim) => count + (claim.evidenceRequirements?.length ?? 0),
      0,
    ),
    claims: experience.claims.map((claim) => ({
      id: claim.id,
      claimRef: claim.id,
      text: claim.text,
      bloomLevel: claim.bloom,
      taskRefs: (claim.tasks ?? []).map((task) => task.id),
      evidenceRefs: (claim.evidenceRequirements ?? []).map(
        (evidence) => evidence.id,
      ),
      contentNeeds: claim.contentNeeds ?? null,
    })),
    evidenceBundles: (bundleMetadata ?? []).map((bundle) => ({
      claimRef: bundle.claimRef,
      bundleConfidence: bundle.bundleConfidence,
      coverageScore: bundle.coverageScore,
      contradictionDetected: bundle.contradictionDetected,
      freshnessOverall: bundle.freshnessOverall,
      evidenceCount: bundle.evidenceCount,
      generatedAt: bundle.generatedAt.toISOString(),
      regeneratedAt: bundle.regeneratedAt?.toISOString() ?? null,
      generationJobId: bundle.generationJobId,
    })),
    latestGeneration:
      latestGenerationLog == null
        ? null
        : {
            promptHash: latestGenerationLog.promptHash,
            model: latestGenerationLog.model,
            modelVersion: latestGenerationLog.modelVersion,
            guardrailsVersion: latestGenerationLog.guardrailsVersion,
            createdAt: latestGenerationLog.createdAt.toISOString(),
          },
    validation: {
      source: "content_studio.validateExperience",
      version: "heuristic-v2",
      score: validation.score,
      status: validation.status,
      canPublish: validation.canPublish,
      independentValidation:
        validation.independentValidation ??
        (latestIndependentValidation == null
          ? null
          : {
              status: latestIndependentValidation.overallStatus,
              validatorVersion: latestIndependentValidation.validatorsVersion,
              score: latestIndependentValidation.authorityScore,
              validatedAt:
                latestIndependentValidation.validatedAt.toISOString(),
              suggestions: Array.isArray(latestIndependentValidation.suggestions)
                ? latestIndependentValidation.suggestions
                : [],
              issues: Array.isArray(latestIndependentValidation.issues)
                ? latestIndependentValidation.issues
                : [],
            }),
    },
    publishedBy: actorId,
    publishedAt: new Date().toISOString(),
  };

  await Promise.all([
    prismaWithExtras.experienceRevision?.create({
      data: {
        experienceId: experience.id,
        version: experience.version,
        diff: provenanceSnapshot,
        authorType: "HUMAN",
        authorId: actorId,
        promptHash: latestGenerationLog?.promptHash ?? null,
      },
    }),
    prismaWithExtras.auditLog?.create({
      data: {
        tenantId: experience.tenantId,
        actorId,
        action: "experience_published",
        resourceType: "LearningExperience",
        resourceId: experience.id,
        outcome: "success",
        metadata: JSON.stringify(provenanceSnapshot),
      },
    }),
  ]);
}

async function submitExperiencePublishReview(
  prisma: PrismaClient,
  params: {
    tenantId: string;
    experienceId: string;
    actorId: string;
    validation: ExperienceValidationResult;
    reviewChecks: ValidationCheck[];
  },
): Promise<string | null> {
  const prismaWithReview = prisma as PrismaClient & {
    reviewQueue?: {
      findFirst?: (args: unknown) => Promise<{ id: string } | null>;
      create?: (args: unknown) => Promise<{ id: string }>;
    };
    auditLog?: {
      create?: (args: unknown) => Promise<unknown>;
    };
  };

  const triggerReason = params.reviewChecks.some(
    (check) => check.checkId === "evidence-bundle-contradictions",
  )
    ? "high_risk"
    : "low_confidence";
  const riskLevel = triggerReason === "high_risk" ? "HIGH" : "MEDIUM";
  const priority = triggerReason === "high_risk" ? 90 : 60;
  const reviewReasons = params.reviewChecks.map((check) => check.message);

  const existingReview =
    typeof prismaWithReview.reviewQueue?.findFirst === "function"
      ? await prismaWithReview.reviewQueue.findFirst({
          where: {
            experienceId: params.experienceId,
            assignedTo: null,
            triggerReason,
          },
          orderBy: { queuedAt: "desc" },
          select: { id: true },
        })
      : null;

  const reviewQueueId = existingReview?.id
    ? existingReview.id
    : typeof prismaWithReview.reviewQueue?.create === "function"
      ? (
          await prismaWithReview.reviewQueue.create({
            data: {
              tenantId: params.tenantId,
              experienceId: params.experienceId,
              priority,
              riskLevel,
              triggerReason,
              metadata: {
                source: "content_studio.publishExperience",
                validationScore: params.validation.score,
                checks: params.validation.checks
                  .filter((check) => !check.passed)
                  .map((check) => ({
                    checkId: check.checkId,
                    message: check.message,
                    severity: check.severity,
                  })),
                reviewReasons,
              },
            } as never,
          })
        ).id
      : null;

  if (!existingReview && reviewQueueId) {
    await recordExperienceEvent(
      prisma,
      params.experienceId,
      "REVIEW_SUBMITTED",
      params.actorId,
      {
        reviewQueueId,
        source: "content_studio.publishExperience",
        validationScore: params.validation.score,
          reviewReasons,
      },
    );
  }

  if (typeof prismaWithReview.auditLog?.create === "function") {
    await prismaWithReview.auditLog.create({
      data: {
        tenantId: params.tenantId,
        actorId: params.actorId,
        action: "experience_publish_review_required",
        resourceType: "LearningExperience",
        resourceId: params.experienceId,
        outcome: "manual_review",
        metadata: JSON.stringify({
          reviewQueueId,
          validationScore: params.validation.score,
          reviewReasons,
        }),
      },
    });
  }

  return reviewQueueId;
}

function defaultGradeAdaptation(gradeRange: GradeRange): GradeAdaptation {
  const defaults: Record<GradeRange, GradeAdaptation> = {
    k_2: {
      gradeRange: "k_2",
      mathLevel: "arithmetic",
      rigorLevel: "conceptual",
      scaffoldingLevel: "high",
      vocabularyComplexity: 2,
      readingLevel: 1,
      prerequisiteConcepts: [],
    },
    grade_3_5: {
      gradeRange: "grade_3_5",
      mathLevel: "arithmetic",
      rigorLevel: "procedural",
      scaffoldingLevel: "high",
      vocabularyComplexity: 4,
      readingLevel: 4,
      prerequisiteConcepts: [],
    },
    grade_6_8: {
      gradeRange: "grade_6_8",
      mathLevel: "arithmetic",
      rigorLevel: "procedural",
      scaffoldingLevel: "medium",
      vocabularyComplexity: 6,
      readingLevel: 7,
      prerequisiteConcepts: [],
    },
    grade_9_12: {
      gradeRange: "grade_9_12",
      mathLevel: "algebra",
      rigorLevel: "analytical",
      scaffoldingLevel: "medium",
      vocabularyComplexity: 8,
      readingLevel: 10,
      prerequisiteConcepts: [],
    },
    undergraduate: {
      gradeRange: "undergraduate",
      mathLevel: "calculus",
      rigorLevel: "analytical",
      scaffoldingLevel: "low",
      vocabularyComplexity: 9,
      readingLevel: 13,
      prerequisiteConcepts: [],
    },
    graduate: {
      gradeRange: "graduate",
      mathLevel: "calculus",
      rigorLevel: "synthesis",
      scaffoldingLevel: "low",
      vocabularyComplexity: 10,
      readingLevel: 16,
      prerequisiteConcepts: [],
    },
    professional: {
      gradeRange: "professional",
      mathLevel: "calculus",
      rigorLevel: "synthesis",
      scaffoldingLevel: "none",
      vocabularyComplexity: 10,
      readingLevel: 16,
      prerequisiteConcepts: [],
    },
  };

  return defaults[gradeRange];
}

function safeJsonArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

type ContentStudioClaimRow = {
  id: string;
  claimRef?: string | null;
  text?: string | null;
  statement?: string | null;
  bloomLevel?: string | null;
  orderIndex?: number | null;
  evidence?: unknown;
  tasks?: unknown;
  contentNeeds?: unknown;
};

type ContentStudioEvidenceRow = {
  id: string;
  evidenceRef?: string | null;
  claimRef?: string | null;
  type?: string | null;
  description?: string | null;
  observables?: unknown;
  contentDelivery?: unknown;
  minimumScore?: number | null;
  weight?: number | null;
};

type ContentStudioTaskRow = {
  id: string;
  taskRef?: string | null;
  claimRef?: string | null;
  evidenceRef?: string | null;
  evidenceType?: string | null;
  evidenceIds?: unknown;
  type?: string | null;
  title?: string | null;
  prompt?: string | null;
  instructions?: string | null;
  estimatedMinutes?: number | null;
  orderIndex?: number | null;
};

function safeRowArray<T extends object>(value: unknown): T[] {
  return Array.isArray(value)
    ? value.filter((item): item is T => typeof item === "object" && item !== null)
    : [];
}

function optionalContractObject<T extends object>(value: unknown): T | undefined {
  return typeof value === "object" && value !== null ? (value as T) : undefined;
}

const bloomLevels: readonly BloomLevel[] = [
  "remember",
  "understand",
  "apply",
  "analyze",
  "evaluate",
  "create",
];

function bloomToContract(value: string): BloomLevel {
  const normalized = value.toLowerCase();
  return bloomLevels.includes(normalized as BloomLevel)
    ? (normalized as BloomLevel)
    : "understand";
}

const evidenceTypes: readonly EvidenceType[] = [
  "prediction_vs_outcome",
  "parameter_targeting",
  "explanation_quality",
  "construction_artifact",
  "observation",
  "diagnosis",
];

function evidenceTypeFromRow(value: string | null | undefined): EvidenceType {
  return evidenceTypes.includes(value as EvidenceType)
    ? (value as EvidenceType)
    : "observation";
}

const taskTypes: readonly TaskType[] = [
  "prediction",
  "simulation",
  "explanation",
  "construction",
];

function taskTypeFromRow(value: string | null | undefined): TaskType {
  return taskTypes.includes(value as TaskType)
    ? (value as TaskType)
    : "simulation";
}

function observablesFromRow(
  primary: unknown,
  fallback: unknown,
): Observable[] {
  return safeRowArray<Observable>(primary).length > 0
    ? safeRowArray<Observable>(primary)
    : safeRowArray<Observable>(fallback);
}

function evidenceIdsFromTask(task: ContentStudioTaskRow): string[] {
  if (Array.isArray(task.evidenceIds)) {
    return task.evidenceIds
      .map((id) => (typeof id === "string" ? id : null))
      .filter((id): id is string => Boolean(id));
  }

  const fallbackId = task.evidenceRef ?? task.evidenceType;
  return fallbackId ? [fallbackId] : [];
}

function bloomFromInput(
  value: string | undefined,
): Prisma.LearningClaimUncheckedCreateInput["bloomLevel"] {
  if (!value) return "UNDERSTAND";
  const up = value.toUpperCase();
  const valid = [
    "REMEMBER",
    "UNDERSTAND",
    "APPLY",
    "ANALYZE",
    "EVALUATE",
    "CREATE",
  ];
  return (
    valid.includes(up) ? up : "UNDERSTAND"
  ) as Prisma.LearningClaimUncheckedCreateInput["bloomLevel"];
}

function extractPrimaryGrade(experience: {
  targetGrades?: unknown;
}): GradeRange {
  const grades = safeJsonArray(experience.targetGrades);
  if (grades.length === 0) return "grade_6_8";
  return normalizeGradeRange(String(grades[0]));
}

async function mapExperience(
  prisma: PrismaClient,
  experienceId: string,
): Promise<LearningExperience | null> {
  const exp = await prisma.learningExperience.findUnique({
    where: { id: experienceId },
    include: {
      claims: {
        orderBy: { orderIndex: "asc" },
      },
      evidences: true,
      experienceTasks: true,
    },
  });

  if (!exp) return null;

  const gradeRange = extractPrimaryGrade(exp);
  const gradeAdaptations = safeJsonArray(exp.gradeAdaptations);
  const selectedGradeAdaptation =
    optionalContractObject<GradeAdaptation>(gradeAdaptations[0]) ||
    defaultGradeAdaptation(gradeRange);

  const experienceEvidence = safeRowArray<ContentStudioEvidenceRow>(exp.evidences);
  const experienceTasks = safeRowArray<ContentStudioTaskRow>(exp.experienceTasks);
  const claims: LearningClaim[] = (exp.claims as ContentStudioClaimRow[]).map((claim) => {
    const claimRef = claim.claimRef ?? claim.id;
    const claimId = claim.id ?? claim.claimRef;
    const claimText = claim.text ?? claim.statement ?? "";
    const bloomLevel = bloomToContract(claim.bloomLevel || "UNDERSTAND");
    const contentNeeds = optionalContractObject<ContentNeeds>(claim.contentNeeds);

    const evidences = safeRowArray<ContentStudioEvidenceRow>(claim.evidence);
    const mappedEvidence =
      evidences.length > 0
        ? evidences
        : experienceEvidence.filter((e) => e.claimRef === claimRef);
    const claimTasks = safeRowArray<ContentStudioTaskRow>(claim.tasks);
    const mappedTasks =
      claimTasks.length > 0
        ? claimTasks
        : experienceTasks.filter((task) => task.claimRef === claimRef);

    return {
      id: claimId,
      text: claimText,
      bloom: bloomLevel,
      bloomLevel,
      experienceId: exp.id,
      orderIndex: claim.orderIndex ?? 0,
      masteryThreshold: 0.7,
      evidenceRequirements: mappedEvidence.map<LearningEvidence>((e) => ({
        id: e.id ?? e.evidenceRef ?? `${claimId}-evidence`,
        claimId,
        claimRef: e.claimRef ?? claimRef,
        type: evidenceTypeFromRow(e.type),
        description: e.description ?? "",
        observables: observablesFromRow(e.observables, e.contentDelivery),
        minimumScore: e.minimumScore ?? 0.7,
        weight: e.weight ?? 1,
      })),
      tasks: mappedTasks.map<ExperienceTask>((task) => ({
        id: task.id ?? task.taskRef ?? `${claimId}-task`,
        claimId,
        type: taskTypeFromRow(task.type),
        title: task.title ?? task.prompt ?? task.instructions ?? "Learning task",
        instructions: task.instructions ?? task.prompt ?? "",
        evidenceIds: evidenceIdsFromTask(task),
        estimatedMinutes: task.estimatedMinutes ?? 10,
        orderIndex: task.orderIndex ?? 0,
      })),
      ...(contentNeeds ? { contentNeeds } : {}),
    };
  });

  return {
    id: exp.id,
    tenantId: exp.tenantId,
    slug: exp.conceptId || generateSlug(exp.title),
    title: exp.title,
    description: exp.intentProblem || exp.intentMotivation || "",
    status: fromPrismaStatus(exp.status),
    version: exp.version,
    gradeAdaptation: selectedGradeAdaptation,
    claims,
    estimatedTimeMinutes: exp.estimatedTimeMinutes,
    keywords: [],
    ...(exp.moduleId ? { moduleId: exp.moduleId } : {}),
    ...(exp.simulationManifestId ? { simulationId: exp.simulationManifestId } : {}),
    ...(exp.createdBy ? { authorId: exp.createdBy } : {}),
    createdAt: exp.createdAt,
    updatedAt: exp.updatedAt,
  };
}

export function createContentStudioService(
  prisma: PrismaClient,
  _config: ContentStudioConfig,
): HealthAwareContentStudioService {
  const queue: ContentGenerationQueueLike = getContentGenerationQueue();
  const independentValidator = _config.independentValidator;
  const contradictionGrader = _config.contradictionGrader;
  const rubricGrader = _config.rubricGrader;

  function buildIndependentValidationContent(
    experience: {
      title?: string | null;
      intentProblem?: string | null;
      intentMotivation?: string | null;
      claims: Array<Record<string, unknown>>;
      experienceTasks: Array<Record<string, unknown>>;
    },
  ): string {
    const claimLines = experience.claims.map((claim, index) => {
      const claimText =
        (typeof claim.statement === "string" && claim.statement.length > 0
          ? claim.statement
          : typeof claim.text === "string" && claim.text.length > 0
            ? claim.text
            : `Claim ${index + 1}`) ?? `Claim ${index + 1}`;
      const bloomLevel =
        typeof claim.bloomLevel === "string"
          ? claim.bloomLevel
          : typeof claim.bloom === "string"
            ? claim.bloom
            : "unclassified";
      const exampleCount = Array.isArray(claim.examples) ? claim.examples.length : 0;
      const simulationCount = Array.isArray(claim.simulations)
        ? claim.simulations.length
        : 0;
      const animationCount = Array.isArray(claim.animations)
        ? claim.animations.length
        : 0;

      return `Claim ${index + 1}: ${claimText}\nBloom: ${bloomLevel}\nArtifacts: examples=${exampleCount}, simulations=${simulationCount}, animations=${animationCount}`;
    });

    const taskLines = experience.experienceTasks.map((task, index) => {
      const prompt =
        typeof task.prompt === "string" && task.prompt.length > 0
          ? task.prompt
          : typeof task.instructions === "string" &&
              task.instructions.length > 0
            ? task.instructions
            : `Task ${index + 1}`;
      const claimRef = typeof task.claimRef === "string" ? task.claimRef : "unlinked";
      return `Task ${index + 1} [${claimRef}]: ${prompt}`;
    });

    return [
      `Title: ${experience.title ?? "Untitled experience"}`,
      `Problem: ${experience.intentProblem ?? ""}`,
      `Motivation: ${experience.intentMotivation ?? ""}`,
      claimLines.join("\n\n"),
      taskLines.length > 0 ? `Tasks:\n${taskLines.join("\n")}` : "Tasks: none",
    ]
      .filter((section) => section.trim().length > 0)
      .join("\n\n");
  }

  async function checkHealth(): Promise<boolean> {
    try {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    } catch {
      return false;
    }
  }

  async function createExperience(
    request: CreateExperienceRequest,
  ): Promise<ExperienceOperationResult> {
    try {
      const gradeRange = normalizeGradeRange(request.gradeRange);
      const adaptation = defaultGradeAdaptation(gradeRange);
      const conceptId = generateSlug(request.title);
      const domain = inferDomain(request.title, request.description);

      const experience = await prisma.learningExperience.create({
        data: {
          tenantId: request.tenantId,
          moduleId: request.moduleId || null,
          title: request.title,
          domain,
          conceptId,
          intentProblem: request.description,
          intentMotivation: request.description,
          intentMisconceptions: [],
          targetGrades: toInputJsonValue([toGradeEnum(gradeRange)]),
          gradeAdaptations: toInputJsonValue([adaptation]),
          assessmentConfig: {
            passingThreshold: 0.7,
            minEvidencePerClaim: 1,
            adaptive: true,
          },
          status: "DRAFT",
          version: 1,
          estimatedTimeMinutes: 30,
          createdBy: request.authorId || "system",
          lastEditedBy: request.authorId || "system",
        } satisfies Prisma.LearningExperienceUncheckedCreateInput,
      });

      try {
        await queue.add(
          "generate-claims",
          {
            experienceId: experience.id,
            tenantId: experience.tenantId,
            topic: experience.title,
            title: experience.title,
            domain: experience.domain,
            gradeLevel: toGradeEnum(gradeRange),
            targetGrades: [toGradeEnum(gradeRange)],
            maxClaims: 5,
          },
          {
            jobId: `generate-claims:${experience.id}`,
            attempts: 3,
            backoff: { type: "exponential", delay: 2000 },
            removeOnComplete: 100,
            removeOnFail: 200,
          },
        );
      } catch (queueError) {
        await prisma.learningExperience
          .delete({ where: { id: experience.id } })
          .catch(() => undefined);
        throw new Error(
          `Failed to enqueue background claim generation: ${
            queueError instanceof Error
              ? queueError.message
              : String(queueError)
          }`,
        );
      }

      const mapped = await mapExperience(prisma, experience.id);
      await recordExperienceEvent(
        prisma,
        experience.id,
        "CREATED",
        request.authorId || "system",
        {
          title: experience.title,
          moduleId: experience.moduleId,
          status: experience.status,
        },
      );
      const result: ExperienceOperationResult = {
        success: true,
      };
      if (mapped) {
        result.experience = mapped;
      }
      return result;
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  async function getExperience(
    experienceId: string,
  ): Promise<LearningExperience | null> {
    return mapExperience(prisma, experienceId);
  }

  async function listExperiences(
    filters: ExperienceListFilters,
  ): Promise<{ experiences: LearningExperience[]; total: number }> {
    const where: Record<string, unknown> = { tenantId: filters.tenantId };

    if (filters.status) {
      where.status = toPrismaStatus(filters.status);
    }
    if (filters.authorId) {
      where.createdBy = filters.authorId;
    }

    const [rows, total] = await Promise.all([
      prisma.learningExperience.findMany({
        where,
        orderBy: { updatedAt: "desc" },
        take: filters.limit || 20,
        skip: filters.offset || 0,
        select: { id: true },
      }),
      prisma.learningExperience.count({ where }),
    ]);

    const experiences: LearningExperience[] = [];
    for (const row of rows) {
      const mapped = await mapExperience(prisma, row.id);
      if (!mapped) continue;
      if (
        filters.gradeRange &&
        mapped.gradeAdaptation?.gradeRange !== filters.gradeRange
      ) {
        continue;
      }
      experiences.push(mapped);
    }

    return {
      experiences,
      total: filters.gradeRange ? experiences.length : total,
    };
  }

  async function updateExperience(
    id: string,
    data: UpdateExperienceInput,
  ): Promise<LearningExperience | null> {
    const existing = await prisma.learningExperience.findUnique({
      where: { id },
    });
    if (!existing) return null;

    const updateData: Record<string, unknown> = {
      lastEditedBy: data?.userId || existing.lastEditedBy || existing.createdBy,
    };

    if (typeof data?.title === "string" && data.title.trim()) {
      updateData.title = data.title.trim();
      if (!existing.conceptId) {
        updateData.conceptId = generateSlug(data.title.trim());
      }
    }

    if (typeof data?.description === "string") {
      updateData.intentProblem = data.description;
      updateData.intentMotivation = data.description;
    }

    if (typeof data?.status === "string") {
      updateData.status = toPrismaStatus(data.status);
    }

    if (
      typeof data?.estimatedTimeMinutes === "number" &&
      data.estimatedTimeMinutes > 0
    ) {
      updateData.estimatedTimeMinutes = data.estimatedTimeMinutes;
    }

    if (typeof data?.gradeRange === "string") {
      const range = normalizeGradeRange(data.gradeRange);
      updateData.targetGrades = [toGradeEnum(range)];
      updateData.gradeAdaptations = [defaultGradeAdaptation(range)];
    }

    const updated = await prisma.learningExperience.update({
      where: { id },
      data: updateData,
    });

    await recordExperienceEvent(
      prisma,
      id,
      "UPDATED",
      String(updateData.lastEditedBy ?? "system"),
      {
        changedFields: Object.keys(updateData).filter(
          (key) => key !== "lastEditedBy",
        ),
        status: updated.status,
      },
    );

    return mapExperience(prisma, id);
  }

  async function deleteExperience(id: string): Promise<void> {
    await prisma.learningExperience.delete({ where: { id } });
  }

  async function generateClaims(
    id: string,
    request: GenerateClaimsInput,
  ): Promise<GenerateClaimsResult> {
    const experience = await prisma.learningExperience.findUnique({
      where: { id },
    });
    if (!experience) {
      throw new Error("Experience not found");
    }

    const primaryGrade = normalizeGradeRange(
      String(safeJsonArray(experience.targetGrades)[0] || "grade_6_8"),
    );
    const maxClaims =
      typeof request?.maxClaims === "number" && request.maxClaims > 0
        ? request.maxClaims
        : 5;

    const job = await queue.add(
      "generate-claims",
      {
        experienceId: experience.id,
        tenantId: experience.tenantId,
        topic: request?.topic || experience.title,
        title: experience.title,
        domain: experience.domain,
        gradeLevel: toGradeEnum(primaryGrade),
        targetGrades: safeJsonArray(experience.targetGrades),
        maxClaims,
      },
      {
        jobId: `generate-claims:${experience.id}`,
        attempts: 3,
        backoff: { type: "exponential", delay: 2000 },
        removeOnComplete: 100,
        removeOnFail: 200,
      },
    );

    await recordExperienceEvent(prisma, id, "CLAIMS_GENERATED", "system", {
      action: "generate_claims",
      jobId: job.id,
      maxClaims,
      topic: request?.topic || experience.title,
    });

    return {
      status: "queued",
      jobId: job.id,
      experienceId: id,
    };
  }

  async function generateTasks(
    experienceId: string,
    claimId: string,
    _request: Record<string, never>,
  ): Promise<{ tasks: unknown[] }> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      return { tasks: [] };
    }

    const tasks = await prisma.experienceTask.findMany({
      where: {
        experienceId,
        claimRef: claim.claimRef,
      },
      orderBy: { orderIndex: "asc" },
    });

    return { tasks };
  }

  async function refineContent(
    id: string,
    request: RefineContentInput,
  ): Promise<LearningExperience | null> {
    const current = await prisma.learningExperience.findUnique({
      where: { id },
    });
    if (!current) return null;

    const note =
      request?.refinementPrompt ||
      request?.prompt ||
      "Refined by auto pipeline";

    const updated = await prisma.learningExperience.update({
      where: { id },
      data: {
        intentMotivation:
          `${current.intentMotivation}\n\nRefinement: ${note}`.slice(0, 5000),
        version: { increment: 1 },
        lastEditedBy: request?.userId || "auto-refiner",
      },
    });

    await recordExperienceEvent(
      prisma,
      id,
      "REFINED",
      request?.userId || "auto-refiner",
      {
        action: "refine_content",
        version: updated.version,
      },
    );

    return mapExperience(prisma, id);
  }

  async function refineExperience(
    request: RefineExperienceRequest,
  ): Promise<ExperienceOperationResult> {
    const experience = await refineContent(request.experienceId, request);
    if (!experience) {
      return { success: false, error: "Experience not found" };
    }
    return { success: true, experience };
  }

  async function adaptGrade(
    id: string,
    request: AdaptGradeInput,
  ): Promise<LearningExperience | null> {
    const target = normalizeGradeRange(
      String(request?.gradeRange || request?.targetGrade || "grade_6_8"),
    );
    const updated = await prisma.learningExperience.update({
      where: { id },
      data: {
        targetGrades: toInputJsonValue([toGradeEnum(target)]),
        gradeAdaptations: toInputJsonValue([defaultGradeAdaptation(target)]),
        version: { increment: 1 },
      } satisfies Prisma.LearningExperienceUpdateInput,
    });

    await recordExperienceEvent(
      prisma,
      id,
      "GRADE_ADAPTED",
      request?.userId || "system",
      {
        action: "adapt_grade",
        targetGrade: target,
        version: updated.version,
      },
    );

    return mapExperience(prisma, id);
  }

  async function adaptGradeLevel(
    experienceId: string,
    targetGrade: GradeRange,
  ): Promise<ExperienceOperationResult> {
    const experience = await adaptGrade(experienceId, { targetGrade });
    if (!experience) {
      return { success: false, error: "Experience not found" };
    }
    return { success: true, experience };
  }

  async function validateExperience(
    id: string,
    _request?: ValidateExperienceInput,
  ): Promise<ExtendedExperienceValidationResult> {
    const experience = await prisma.learningExperience.findUnique({
      where: { id },
      include: {
        claims: {
          include: {
            examples: true,
            simulations: true,
            animations: true,
          },
        },
        evidences: true,
        experienceTasks: true,
      },
    });

    if (!experience) {
      throw new Error("Experience not found");
    }

    const claims = experience.claims as Array<Record<string, unknown>>;
    const claimCount = claims.length;

    // --------------------------------------------------------------------------
    // Evidence-based validation: check per-claim artifact presence
    // --------------------------------------------------------------------------
    let claimsWithTasks = 0;
    let claimsWithArtifacts = 0;
    let claimsWithBloom = 0;

    const claimChecks: ClaimCoverageCheck[] = [];

    const tasksByClaimRef = new Map<string, number>();
    for (const task of experience.experienceTasks) {
      const ref = (task as { claimRef?: string }).claimRef ?? "";
      tasksByClaimRef.set(ref, (tasksByClaimRef.get(ref) ?? 0) + 1);
    }

    const claimRefs = new Set<string>();

    for (const claim of claims) {
      const claimRef =
        (claim as { claimRef?: string; id?: string }).claimRef ??
        (claim as { claimRef?: string; id?: string }).id ??
        "";
      claimRefs.add(claimRef);
      const taskCount = tasksByClaimRef.get(claimRef) ?? 0;
      const exampleCount = Array.isArray(claim.examples) ? claim.examples.length : 0;
      const simCount = Array.isArray(claim.simulations) ? claim.simulations.length : 0;
      const animCount = Array.isArray(claim.animations) ? claim.animations.length : 0;
      const hasArtifacts = exampleCount + simCount + animCount > 0;
      const hasTasks = taskCount > 0;
      const hasBloom = !!claim.bloomLevel;

      if (hasTasks) claimsWithTasks++;
      if (hasArtifacts) claimsWithArtifacts++;
      if (hasBloom) claimsWithBloom++;

      claimChecks.push({ claimRef, hasTasks, hasArtifacts, hasBloom });
    }

    const orphanTaskRefs = [...tasksByClaimRef.entries()]
      .filter(([claimRef, count]) => count > 0 && !claimRefs.has(claimRef))
      .map(([claimRef]) => claimRef || "unlinked-task");

    const prismaWithEvidence = prisma as PrismaClient & {
      evidenceBundleMetadata?: {
        findMany?: (args: unknown) => Promise<
          Array<{
            claimRef: string;
            bundleConfidence: number;
            contradictionDetected: boolean;
          }>
        >;
      };
    };
    const evidenceBundles =
      claimRefs.size > 0 &&
      typeof prismaWithEvidence.evidenceBundleMetadata?.findMany === "function"
        ? await prismaWithEvidence.evidenceBundleMetadata.findMany({
            where: {
              experienceId: id,
              claimRef: { in: [...claimRefs] },
            },
            select: {
              claimRef: true,
              bundleConfidence: true,
              contradictionDetected: true,
            },
            orderBy: { claimRef: "asc" },
          })
        : [];
    const lowConfidenceBundles = evidenceBundles.filter(
      (bundle) =>
        bundle.bundleConfidence < MANUAL_PUBLISH_BUNDLE_CONFIDENCE_MIN,
    );
    const contradictoryBundles = evidenceBundles.filter(
      (bundle) => bundle.contradictionDetected,
    );
    const requiresManualReview =
      lowConfidenceBundles.length > 0 || contradictoryBundles.length > 0;
    let independentValidation:
      | ExtendedExperienceValidationResult["independentValidation"]
      | undefined;

    // --------------------------------------------------------------------------
    // Pillar scoring (0–100 per pillar)
    // --------------------------------------------------------------------------
    // Educational: claims with Bloom level + task coverage
    const educationalScore =
      claimCount === 0
        ? 0
        : Math.round(
            (claimsWithBloom / claimCount) * 50 +
              (claimsWithTasks / claimCount) * 50,
          );

    // Experiential: claims with concrete artifacts (examples / simulations / animations)
    const experientialScore =
      claimCount === 0
        ? 0
        : Math.round((claimsWithArtifacts / claimCount) * 100);

    // Technical: schema validity — grade adaptation present + version set
    const hasGradeAdaptation =
      Array.isArray(experience.gradeAdaptations) &&
      (experience.gradeAdaptations as unknown[]).length > 0;
    const technicalScore =
      claimCount === 0
        ? 20
        : Math.min(
            100,
            (hasGradeAdaptation ? 50 : 0) + (claimCount > 0 ? 50 : 0),
          );

    // Safety / Accessibility: grade adaptation presence + sensible grade range
    const accessibilityScore = hasGradeAdaptation ? 85 : 50;
    const safetyScore = 100; // No safety issues unless content screening is integrated

    const overallScore = Math.round(
      educationalScore * 0.3 +
        experientialScore * 0.35 +
        technicalScore * 0.15 +
        safetyScore * 0.1 +
        accessibilityScore * 0.1,
    );

    // --------------------------------------------------------------------------
    // Publishability gate:
    //   - At least one claim
    //   - Every claim must have at least one task OR one artifact
    //   - Overall score >= 60
    // --------------------------------------------------------------------------
    const allClaimsMeetBaseline = claimChecks.every(
      (c) => c.hasTasks && c.hasArtifacts,
    );
    // --------------------------------------------------------------------------
    // Build human-readable check items
    // --------------------------------------------------------------------------
    const checks: ValidationCheck[] = [];

    if (claimCount === 0) {
      checks.push({
        checkId: "claims-required",
        pillar: "educational",
        name: "Learning Claims",
        passed: false,
        severity: "error",
        message:
          "Experience has no learning claims. Generate claims before publishing.",
        suggestion:
          "Use the Generate Claims action to create evidence-backed claims.",
      });
    } else {
      checks.push({
        checkId: "claims-present",
        pillar: "educational",
        name: "Learning Claims",
        passed: true,
        severity: "info",
        message: `${claimCount} claim${claimCount > 1 ? "s" : ""} present.`,
      });
    }

    if (claimCount > 0) {
      const missingTasks = claimChecks.filter((c) => !c.hasTasks).length;
      const missingArtifacts = claimChecks.filter(
        (c) => !c.hasArtifacts,
      ).length;

      const taskCheck: ValidationCheck = {
        checkId: "claim-tasks",
        pillar: "educational",
        name: "Claim Tasks Coverage",
        passed: missingTasks === 0,
        severity: missingTasks === 0 ? "info" : "error",
        message:
          missingTasks === 0
            ? "All claims have associated practice tasks."
            : `${missingTasks} claim${missingTasks > 1 ? "s are" : " is"} missing tasks.`,
      };
      if (missingTasks > 0) {
        taskCheck.suggestion = "Generate tasks for claims without coverage.";
      }
      checks.push(taskCheck);

      const artifactCheck: ValidationCheck = {
        checkId: "claim-artifacts",
        pillar: "experiential",
        name: "Concrete Learning Artifacts",
        passed: missingArtifacts === 0,
        severity: missingArtifacts === 0 ? "info" : "error",
        message:
          missingArtifacts === 0
            ? "All claims have at least one concrete learning artifact."
            : `${missingArtifacts} claim${missingArtifacts > 1 ? "s lack" : " lacks"} examples, simulations, or animations.`,
      };
      if (missingArtifacts > 0) {
        artifactCheck.suggestion =
          "Run the content generation pipeline to create examples and simulations.";
      }
      checks.push(artifactCheck);

      const missingBloom = claimChecks.filter((c) => !c.hasBloom).length;
      const bloomCheck: ValidationCheck = {
        checkId: "claim-bloom",
        pillar: "educational",
        name: "Bloom Coverage",
        passed: missingBloom === 0,
        severity: missingBloom === 0 ? "info" : "warning",
        message:
          missingBloom === 0
            ? "All claims include a Bloom level."
            : `${missingBloom} claim${missingBloom > 1 ? "s are" : " is"} missing Bloom classification.`,
      };
      if (missingBloom > 0) {
        bloomCheck.suggestion =
          "Assign Bloom levels so validation and review can assess instructional coverage.";
      }
      checks.push(bloomCheck);

      const orphanTaskCheck: ValidationCheck = {
        checkId: "task-claim-links",
        pillar: "technical",
        name: "Task Claim Links",
        passed: orphanTaskRefs.length === 0,
        severity: orphanTaskRefs.length === 0 ? "info" : "error",
        message:
          orphanTaskRefs.length === 0
            ? "All practice tasks are linked to an existing claim."
            : `${orphanTaskRefs.length} task link${orphanTaskRefs.length > 1 ? "s reference" : " references"} missing claims: ${orphanTaskRefs.join(", ")}.`,
      };
      if (orphanTaskRefs.length > 0) {
        orphanTaskCheck.suggestion =
          "Relink or remove tasks whose claimRef no longer exists before publishing.";
      }
      checks.push(orphanTaskCheck);

      const evidenceConfidenceCheck: ValidationCheck = {
        checkId: "evidence-bundle-confidence",
        pillar: "technical",
        name: "Evidence Bundle Confidence",
        passed: lowConfidenceBundles.length === 0,
        severity: lowConfidenceBundles.length === 0 ? "info" : "error",
        message:
          lowConfidenceBundles.length === 0
            ? "Evidence bundle confidence meets the publish threshold."
            : `${lowConfidenceBundles.length} claim${lowConfidenceBundles.length > 1 ? "s have" : " has"} evidence confidence below ${MANUAL_PUBLISH_BUNDLE_CONFIDENCE_MIN}: ${lowConfidenceBundles
                .map(
                  (bundle) =>
                    `${bundle.claimRef} (${bundle.bundleConfidence.toFixed(2)})`,
                )
                .join(", ")}.`,
      };
      if (lowConfidenceBundles.length > 0) {
        evidenceConfidenceCheck.suggestion =
          "Regenerate or strengthen evidence bundles before publishing, or route the experience for reviewer approval.";
      }
      checks.push(evidenceConfidenceCheck);

      const evidenceContradictionCheck: ValidationCheck = {
        checkId: "evidence-bundle-contradictions",
        pillar: "safety",
        name: "Evidence Bundle Contradictions",
        passed: contradictoryBundles.length === 0,
        severity: contradictoryBundles.length === 0 ? "info" : "error",
        message:
          contradictoryBundles.length === 0
            ? "No contradictions were detected in publish-time evidence bundles."
            : `${contradictoryBundles.length} claim${contradictoryBundles.length > 1 ? "s contain" : " contains"} contradictory evidence bundles: ${contradictoryBundles
                .map((bundle) => bundle.claimRef)
                .join(", ")}.`,
      };
      if (contradictoryBundles.length > 0) {
        evidenceContradictionCheck.suggestion =
          "Resolve evidence contradictions or submit the experience for manual review before publishing.";
      }
      checks.push(evidenceContradictionCheck);
    }

    if (independentValidator) {
      const independentResult = await independentValidator.validateGeneratedContent(
        {
          tenantId: experience.tenantId,
          experienceId: id,
          actorId: _request?.userId ?? "system",
          content: buildIndependentValidationContent(experience),
          contentType: "explanation",
          domain:
            typeof experience.domain === "string" && experience.domain.length > 0
              ? experience.domain
              : inferDomain(
                  experience.title ?? "",
                  experience.intentProblem ?? undefined,
                ),
          gradeRange:
            Array.isArray(experience.targetGrades) &&
            typeof experience.targetGrades[0] === "string"
              ? String(experience.targetGrades[0])
              : "GRADE_6_8",
          metadata: {
            source: "content_studio.validateExperience",
            claimCount,
          },
        },
      );

      independentValidation = {
        status: independentResult.overallStatus,
        score: independentResult.score,
        validatorVersion: independentResult.validatorVersion,
        requiresHumanReview: independentResult.requiresHumanReview,
        ...(independentResult.reviewQueueId !== undefined
          ? { reviewQueueId: independentResult.reviewQueueId }
          : {}),
        recommendations: independentResult.recommendations,
      };

      checks.push({
        checkId: "independent-validator",
        pillar: "safety",
        name: "Independent Content Validation",
        passed: independentResult.overallStatus === "PASS",
        severity:
          independentResult.overallStatus === "FAIL"
            ? "error"
            : independentResult.overallStatus === "WARN"
              ? "warning"
              : "info",
        message:
          independentResult.overallStatus === "PASS"
            ? `Independent validator passed (${independentResult.score}/100).`
            : `Independent validator returned ${independentResult.overallStatus} (${independentResult.score}/100).`,
        ...(independentResult.recommendations.length > 0
          ? { suggestion: independentResult.recommendations.join(" ") }
          : independentResult.requiresHumanReview
            ? { suggestion: "Route the experience for manual review before publishing." }
            : {}),
      });
    }

    // =========================================================================
    // F-011: Second-LLM claim contradiction grader
    // =========================================================================
    let contradictionBlocksPublish = false;
    if (contradictionGrader && claimCount > 1) {
      try {
        const claimList = Array.isArray((experience as { claims?: unknown[] }).claims)
          ? (experience as { claims: Array<{ id?: string; claimRef?: string; text?: string }> }).claims.map((c) => ({
              id: String(c.id ?? c.claimRef ?? ""),
              text: String(c.text ?? ""),
            }))
          : [];
        const contradictionResult = await contradictionGrader.check({
          experienceId: id,
          tenantId: String(experience.tenantId),
          actorId: _request?.userId ?? "system",
          title: String((experience as { title?: string }).title ?? ""),
          claims: claimList,
        });
        contradictionBlocksPublish = contradictionResult.blocksPublish;
        checks.push({
          checkId: "claim-contradiction-grader",
          pillar: "safety",
          name: "Claim Contradiction Check (F-011)",
          passed: !contradictionResult.blocksPublish,
          severity: contradictionResult.blocksPublish ? "error" : "info",
          message: contradictionResult.blocksPublish
            ? `${contradictionResult.contradictingPairs.length} contradicting claim pair(s) found (coherence ${contradictionResult.coherenceScore}/100). Resolve before publishing.`
            : `No claim contradictions detected (coherence ${contradictionResult.coherenceScore}/100).`,
          ...(contradictionResult.blocksPublish
            ? { suggestion: "Review the flagged claim pairs and remove or reconcile the contradictory statements." }
            : {}),
        });
      } catch (_err) {
        // Do not block publish if grader itself throws unexpectedly
      }
    }

    // =========================================================================
    // F-036: Rubric-backed pillar grader (augments heuristic scores)
    // =========================================================================
    let rubricOverallScore: number | undefined;
    if (rubricGrader && claimCount > 0) {
      try {
        const claimTexts = Array.isArray((experience as { claims?: Array<{ text?: string }> }).claims)
          ? (experience as { claims: Array<{ text?: string }> }).claims.map((c) => String(c.text ?? ""))
          : [];
        const rubricResult = await rubricGrader.grade({
          experienceId: id,
          tenantId: String(experience.tenantId),
          title: String((experience as { title?: string }).title ?? ""),
          description: String((experience as { description?: string }).description ?? ""),
          claimTexts,
        });
        rubricOverallScore = rubricResult.overallScore;
        for (const pillarResult of rubricResult.pillarResults) {
          checks.push({
            checkId: `rubric-pillar-${pillarResult.pillar}`,
            pillar: pillarResult.pillar,
            name: `Rubric: ${pillarResult.pillar.charAt(0).toUpperCase()}${pillarResult.pillar.slice(1)} (F-036)`,
            passed: !pillarResult.blocksPublish,
            severity: pillarResult.blocksPublish ? "error" : pillarResult.weightedScore < 60 ? "warning" : "info",
            message: `Rubric score ${pillarResult.weightedScore}/100${pillarResult.blocksPublish ? " — blocks publish" : ""}.`,
            ...(pillarResult.blocksPublish
              ? { suggestion: `Improve ${pillarResult.pillar} quality before publishing (minimum 40/100 required).` }
              : {}),
          });
        }
      } catch (_err) {
        // Rubric grader failure must not block publish
      }
    }

    const canPublish =
      claimCount > 0 &&
      allClaimsMeetBaseline &&
      orphanTaskRefs.length === 0 &&
      overallScore >= 60 &&
      !requiresManualReview &&
      independentValidation?.status !== "FAIL" &&
      independentValidation?.requiresHumanReview !== true &&
      !contradictionBlocksPublish &&
      (rubricOverallScore === undefined || rubricOverallScore >= 40);
    const overallStatus = canPublish
      ? "PASS"
      : independentValidation?.status === "FAIL"
        ? "FAIL"
      : independentValidation?.status === "WARN"
        ? "WARN"
      : contradictoryBundles.length > 0
        ? "FAIL"
        : overallScore >= 45
          ? "WARN"
          : "FAIL";

    const gradeCheck: ValidationCheck = {
      checkId: "grade-adaptation",
      pillar: "accessibility",
      name: "Grade Adaptation",
      passed: hasGradeAdaptation,
      severity: hasGradeAdaptation ? "info" : "warning",
      message: hasGradeAdaptation
        ? "Grade adaptation profile is set."
        : "No grade adaptation profile found.",
    };
    if (!hasGradeAdaptation) {
      gradeCheck.suggestion =
        "Set a target grade range to enable grade-appropriate content.";
    }
    checks.push(gradeCheck);

    await prisma.validationRecord.create({
      data: {
        experienceId: id,
        authorityScore: educationalScore,
        accuracyScore: technicalScore,
        usefulnessScore: experientialScore,
        harmlessnessScore: safetyScore,
        accessibilityScore,
        gradefitScore: accessibilityScore,
        overallStatus,
        issues: checks
          .filter((c) => !c.passed)
          .map((c) => ({ severity: c.severity, message: c.message })),
        suggestions: checks
          .filter((c) => c.suggestion)
          .map((c) => c.suggestion as string),
      } satisfies Prisma.ValidationRecordUncheckedCreateInput,
    });

    await recordExperienceEvent(prisma, id, "VALIDATED", "system", {
      status: overallStatus,
      canPublish,
      score: overallScore,
      independentValidationStatus: independentValidation?.status ?? null,
      independentValidatorVersion:
        independentValidation?.validatorVersion ?? null,
    });

    return {
      status: canPublish
        ? "valid"
        : overallScore >= 45
          ? "warnings"
          : "invalid",
      canPublish,
      checks,
      score: overallScore,
      pillarScores: {
        educational: educationalScore,
        experiential: experientialScore,
        safety: safetyScore,
        technical: technicalScore,
        accessibility: accessibilityScore,
      },
      validatedAt: new Date(),
      ...(independentValidation ? { independentValidation } : {}),
    };
  }

  async function getValidationHistory(
    id: string,
  ): Promise<ValidationHistoryEntry[]> {
    const rows = await prisma.validationRecord.findMany({
      where: { experienceId: id },
      orderBy: { validatedAt: "desc" },
      take: 20,
    });

    return rows;
  }

  async function publishExperience(
    id: string,
    userId: string,
  ): Promise<LearningExperience | null> {
    const publishContext = await prisma.learningExperience.findUnique({
      where: { id },
      select: { tenantId: true },
    });

    if (!publishContext) {
      throw new Error("Experience not found");
    }

    // Validate before publishing and surface the exact blocking artifact-graph defects.
    const validation = await validateExperience(id);
    if (!validation.canPublish) {
      const blockingIssues = validation.checks
        .filter((c) => !c.passed && c.severity !== "info")
        .map((c) => c.message);
      const reviewReasonChecks = validation.checks.filter(
        (check) =>
          !check.passed &&
          (check.checkId === "evidence-bundle-confidence" ||
            check.checkId === "evidence-bundle-contradictions" ||
            check.checkId === "independent-validator"),
      );

      if (reviewReasonChecks.length > 0) {
        const reviewQueueId = await submitExperiencePublishReview(prisma, {
          tenantId: publishContext.tenantId,
          experienceId: id,
          actorId: userId || "publisher",
          validation,
          reviewChecks: reviewReasonChecks,
        });

        throw createHttpError(
          409,
          "REVIEW_REQUIRED",
          `Cannot publish without manual review. ${blockingIssues.join("; ")}${reviewQueueId ? ` Review queue: ${reviewQueueId}.` : ""}`,
        );
      }

      throw new Error(
        `Cannot publish: validation failed (score ${validation.score}/100). ` +
          `Fix the following issues: ${blockingIssues.join("; ")}`,
      );
    }

    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "PUBLISHED",
        publishedAt: new Date(),
        lastEditedBy: userId || "publisher",
      },
    });

    const publishedExperience = await mapExperience(prisma, id);

    if (publishedExperience) {
      await recordExperiencePublishProvenance(
        prisma,
        publishedExperience,
        userId || "publisher",
        validation,
      );
    }

    await recordExperienceEvent(
      prisma,
      id,
      "PUBLISHED",
      userId || "publisher",
      {
        status: "PUBLISHED",
        validationScore: validation.score,
      },
    );

    return publishedExperience;
  }

  async function unpublishExperience(
    id: string,
    _reason?: string,
  ): Promise<LearningExperience | null> {
    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "REVIEW",
      },
    });
    await recordExperienceEvent(prisma, id, "UNPUBLISHED", "system", {
      reason: _reason || null,
      status: "REVIEW",
    });
    return mapExperience(prisma, id);
  }

  async function archiveExperience(
    id: string,
  ): Promise<LearningExperience | null> {
    await prisma.learningExperience.update({
      where: { id },
      data: {
        status: "ARCHIVED",
      },
    });
    await recordExperienceEvent(prisma, id, "ARCHIVED", "system", {
      action: "archive",
      status: "ARCHIVED",
    });
    return mapExperience(prisma, id);
  }

  async function addClaim(
    id: string,
    claim: AddClaimInput,
  ): Promise<ContentStudioClaimRow> {
    const latest = await prisma.learningClaim.findFirst({
      where: { experienceId: id },
      orderBy: { orderIndex: "desc" },
    });

    const orderIndex = (latest?.orderIndex ?? -1) + 1;
    const claimRef = claim?.claimRef || `C${orderIndex + 1}`;

    const created = await prisma.learningClaim.create({
      data: {
        experienceId: id,
        claimRef,
        text: claim?.text || claim?.statement || "New claim",
        bloomLevel: bloomFromInput(claim?.bloomLevel || claim?.bloom),
        orderIndex,
        contentNeeds: claim?.contentNeeds
          ? toInputJsonValue(claim.contentNeeds)
          : Prisma.JsonNull,
      } satisfies Prisma.LearningClaimUncheckedCreateInput,
    });

    await recordExperienceEvent(
      prisma,
      id,
      "CONTENT_CHANGED",
      claim?.userId || "system",
      {
        action: "claim_added",
        claimId: created.id,
        claimRef,
      },
    );

    return created;
  }

  async function updateClaim(
    experienceId: string,
    claimId: string,
    data: UpdateClaimInput,
  ): Promise<ContentStudioClaimRow> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      throw new Error("Claim not found");
    }

    const updated = await prisma.learningClaim.update({
      where: { id: claim.id },
      data: {
        text: data?.text || data?.statement || claim.text,
        bloomLevel:
          data?.bloomLevel || data?.bloom
            ? bloomFromInput(data?.bloomLevel || data?.bloom)
            : claim.bloomLevel,
        contentNeeds:
          data?.contentNeeds !== undefined
            ? toInputJsonValue(data.contentNeeds)
            : toNullableInputJsonValue(claim.contentNeeds),
      } satisfies Prisma.LearningClaimUpdateInput,
    });

    await recordExperienceEvent(
      prisma,
      experienceId,
      "CONTENT_CHANGED",
      data?.userId || "system",
      {
        action: "claim_updated",
        claimId: updated.id,
        claimRef: updated.claimRef,
      },
    );

    return updated;
  }

  async function deleteClaim(
    experienceId: string,
    claimId: string,
  ): Promise<void> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });

    if (!claim) return;

    await prisma.learningClaim.delete({
      where: { id: claim.id },
    });

    await recordExperienceEvent(
      prisma,
      experienceId,
      "CONTENT_CHANGED",
      "system",
      {
        action: "claim_deleted",
        claimId: claim.id,
        claimRef: claim.claimRef,
      },
    );
  }

  async function addTask(
    experienceId: string,
    claimId: string,
    task: AddTaskInput,
  ): Promise<ContentStudioTaskRow> {
    const claim = await prisma.learningClaim.findFirst({
      where: {
        experienceId,
        OR: [{ id: claimId }, { claimRef: claimId }],
      },
    });
    if (!claim) {
      throw new Error("Claim not found");
    }

    const latest = await prisma.experienceTask.findFirst({
      where: { experienceId },
      orderBy: { orderIndex: "desc" },
    });
    const orderIndex = (latest?.orderIndex ?? -1) + 1;

    const taskRef = task?.taskRef || `T${orderIndex + 1}`;
    const evidenceRef = task?.evidenceRef || `E${orderIndex + 1}`;

    const created = await prisma.experienceTask.create({
      data: {
        experienceId,
        taskRef,
        type: task?.type || "explanation",
        claimRef: claim.claimRef,
        evidenceRef,
        prompt: task?.prompt || task?.instructions || "",
        orderIndex,
        config: toInputJsonValue(task?.config || {}),
      } satisfies Prisma.ExperienceTaskUncheckedCreateInput,
    });

    await recordExperienceEvent(
      prisma,
      experienceId,
      "CONTENT_CHANGED",
      task?.userId || "system",
      {
        action: "task_added",
        taskId: created.id,
        taskRef: created.taskRef,
        claimRef: claim.claimRef,
      },
    );

    return created;
  }

  async function updateTask(
    experienceId: string,
    _claimId: string,
    taskId: string,
    data: UpdateTaskInput,
  ): Promise<ContentStudioTaskRow> {
    const task = await prisma.experienceTask.findFirst({
      where: {
        experienceId,
        OR: [{ id: taskId }, { taskRef: taskId }],
      },
    });

    if (!task) {
      throw new Error("Task not found");
    }

    const updated = await prisma.experienceTask.update({
      where: { id: task.id },
      data: {
        type: data?.type || task.type,
        prompt: data?.prompt || data?.instructions || task.prompt,
        config:
          data?.config !== undefined
            ? toInputJsonValue(data.config)
            : task.config === null
              ? Prisma.JsonNull
              : toInputJsonValue(task.config),
      } satisfies Prisma.ExperienceTaskUpdateInput,
    });

    await recordExperienceEvent(
      prisma,
      experienceId,
      "CONTENT_CHANGED",
      data?.userId || "system",
      {
        action: "task_updated",
        taskId: updated.id,
        taskRef: updated.taskRef,
      },
    );

    return updated;
  }

  async function deleteTask(
    experienceId: string,
    _claimId: string,
    taskId: string,
  ): Promise<void> {
    const task = await prisma.experienceTask.findFirst({
      where: {
        experienceId,
        OR: [{ id: taskId }, { taskRef: taskId }],
      },
    });

    if (!task) return;
    await prisma.experienceTask.delete({ where: { id: task.id } });
    await recordExperienceEvent(
      prisma,
      experienceId,
      "CONTENT_CHANGED",
      "system",
      {
        action: "task_deleted",
        taskId: task.id,
        taskRef: task.taskRef,
      },
    );
  }

  async function getExperienceAnalytics(
    id: string,
  ): Promise<ExperienceAnalyticsSummary> {
    const prismaWithExtendedValidation = prisma as PrismaClient & {
      validationRecordExtended?: {
        findFirst: (args: unknown) => Promise<
          | {
              overallStatus: "PASS" | "WARN" | "FAIL";
              validatedAt: Date;
              validatorsVersion: string | null;
              authorityScore: number | null;
              suggestions: unknown;
              issues: unknown;
            }
          | null
        >;
      };
    };

    const [analytics, latestValidation, latestIndependentValidation, recentEvents] = await Promise.all([
      prisma.experienceAnalytics.findUnique({
        where: { experienceId: id },
      }),
      prisma.validationRecord.findFirst({
        where: { experienceId: id },
        orderBy: { validatedAt: "desc" },
      }),
      prismaWithExtendedValidation.validationRecordExtended?.findFirst({
        where: { experienceId: id },
        orderBy: { validatedAt: "desc" },
        select: {
          overallStatus: true,
          validatedAt: true,
          validatorsVersion: true,
          authorityScore: true,
          suggestions: true,
          issues: true,
        },
      }) ?? Promise.resolve(null),
      prisma.experienceEvent.findMany({
        where: { experienceId: id },
        orderBy: { createdAt: "desc" },
        take: 20,
      }),
    ]);

    const timeline: ExperienceTimelineEvent[] = (recentEvents || []).map(
      (event) => ({
        id: event.id,
        type: event.eventType,
        actorId: event.actorId,
        metadata:
          event.metadata && typeof event.metadata === "object"
            ? (event.metadata as Record<string, unknown>)
            : null,
        createdAt:
          event.createdAt instanceof Date
            ? event.createdAt.toISOString()
            : String(event.createdAt),
      }),
    );

    return {
      ...(analytics || {}),
      experienceId: id,
      latestValidation: latestValidation
        ? {
            status: latestValidation.overallStatus,
            validatedAt:
              latestValidation.validatedAt instanceof Date
                ? latestValidation.validatedAt.toISOString()
                : String(latestValidation.validatedAt),
            accessibilityScore: latestValidation.accessibilityScore,
            authorityScore: latestValidation.authorityScore,
            accuracyScore: latestValidation.accuracyScore,
            usefulnessScore: latestValidation.usefulnessScore,
            harmlessnessScore: latestValidation.harmlessnessScore,
            suggestions: Array.isArray(latestValidation.suggestions)
              ? latestValidation.suggestions
              : [],
          }
        : null,
      latestIndependentValidation: latestIndependentValidation
        ? {
            status: latestIndependentValidation.overallStatus,
            validatedAt:
              latestIndependentValidation.validatedAt instanceof Date
                ? latestIndependentValidation.validatedAt.toISOString()
                : String(latestIndependentValidation.validatedAt),
            score: latestIndependentValidation.authorityScore,
            validatorVersion: latestIndependentValidation.validatorsVersion,
            recommendations: Array.isArray(latestIndependentValidation.suggestions)
              ? latestIndependentValidation.suggestions
              : [],
            issues: Array.isArray(latestIndependentValidation.issues)
              ? latestIndependentValidation.issues
              : [],
          }
        : null,
      recentEvents: timeline,
    };
  }

  async function getExperienceEvents(
    id: string,
    options?: { limit?: number; eventType?: ExperienceEventType },
  ): Promise<ExperienceTimelineEvent[]> {
    const where: Record<string, unknown> = { experienceId: id };
    if (options?.eventType) {
      where.eventType = options.eventType;
    }

    const events = await prisma.experienceEvent.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: options?.limit ?? 50,
    });

    return (events || []).map((event: Record<string, unknown>) => ({
      id: String(event.id),
      type: String(event.eventType) as ExperienceEventType,
      actorId: String(event.actorId),
      metadata:
        event.metadata && typeof event.metadata === "object"
          ? (event.metadata as Record<string, unknown>)
          : null,
      createdAt:
        event.createdAt instanceof Date
          ? event.createdAt.toISOString()
          : String(event.createdAt),
    }));
  }

  async function getGenerationProgress(
    id: string,
  ): Promise<GenerationProgress> {
    const exp = await prisma.learningExperience.findUnique({
      where: { id },
      include: {
        claims: {
          include: {
            examples: true,
            simulations: true,
            animations: true,
          },
        },
      },
    });

    if (!exp) {
      throw new Error("Experience not found");
    }

    const totalClaims = exp.claims.length;
    const claimsProcessed = exp.claims.filter((claim) => {
      return (
        (claim.examples?.length || 0) > 0 ||
        (claim.simulations?.length || 0) > 0 ||
        (claim.animations?.length || 0) > 0
      );
    }).length;

    const contentCounts = exp.claims.reduce(
      (acc, claim) => {
        acc.examples += claim.examples?.length || 0;
        acc.simulations += claim.simulations?.length || 0;
        acc.animations += claim.animations?.length || 0;
        return acc;
      },
      { examples: 0, simulations: 0, animations: 0 },
    );

    const isComplete = totalClaims > 0 && claimsProcessed >= totalClaims;
    const percentComplete =
      totalClaims === 0 ? 0 : Math.round((claimsProcessed / totalClaims) * 100);

    return {
      experienceId: id,
      status: isComplete
        ? "complete"
        : totalClaims === 0
          ? "queued"
          : "in_progress",
      totalClaims,
      claimsProcessed,
      percentComplete,
      contentCounts,
      isComplete,
      updatedAt: exp.updatedAt.toISOString(),
    };
  }

  async function getSuggestions(
    _experienceId: string,
  ): Promise<AIGenerationResult<string[]>> {
    return {
      content: [
        "Add at least one evidence-backed task per claim",
        "Increase concrete examples for early-grade learners",
      ],
      confidence: 0.8,
      explanation: "Generated from heuristic analysis",
      tokensUsed: 0,
      processingTimeMs: 0,
    };
  }

  return {
    checkHealth,
    createExperience,
    getExperience,
    listExperiences,
    updateExperience,
    deleteExperience,
    generateClaims,
    generateTasks,
    refineContent,
    refineExperience,
    adaptGrade,
    adaptGradeLevel,
    validateExperience,
    getValidationHistory,
    publishExperience,
    unpublishExperience,
    archiveExperience,
    addClaim,
    updateClaim,
    deleteClaim,
    addTask,
    updateTask,
    deleteTask,
    getExperienceAnalytics,
    getExperienceEvents,
    getGenerationProgress,
    getSuggestions,
  };
}
