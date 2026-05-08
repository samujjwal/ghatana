/**
 * Assessment Service (Migrated)
 *
 * Quiz generation, grading, and rubric management.
 * Migrated from tutorputor-assessment/src/service.ts
 *
 * @doc.type service
 * @doc.purpose Assessment lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */

import type { AssessmentService } from "@tutorputor/contracts/v1/services";
import type {
  Assessment,
  AssessmentSummary,
  AssessmentGenerationInput,
  AssessmentGenerationResult,
  AssessmentItem,
  AssessmentItemChoice,
  AssessmentAttempt,
  AssessmentFeedback,
  AssessmentAttemptId,
  AssessmentResponse,
  TenantId,
  UserId,
  ModuleId,
  AssessmentId,
  Difficulty,
} from "@tutorputor/contracts/v1/types";
import type { Prisma, TutorPrismaClient } from "@tutorputor/core/db";
import { paginate } from "@tutorputor/core/db/helpers/pagination";
import { TenantAccessValidator } from "@tutorputor/core/auth/tenant-access-validator";

import { aiClient } from "../../clients/ai-client";
import { createStandaloneLogger } from "@tutorputor/core/logger";
import { createLearnerProfileService } from "./learner-profile-service.js";
import { IRTCalibrationService } from "../assessment/irt/service.js";
import { MisconceptionDatabase } from "../assessment/misconceptions/database.js";
import { MisconceptionDetector } from "../assessment/misconceptions/detector.js";
import {
  createSimulationAssessmentIntegration,
  scoreSimulationAssessmentResponse,
} from "../assessment/simulation-integration/service.js";
import { AIGradingService } from "../assessment/ai-grading/AIGradingService.js";

const logger = createStandaloneLogger({ component: "AssessmentService" });

// Initialize AI grading service
const aiGradingService = new AIGradingService();

// =============================================================================
// Types
// =============================================================================

export type HealthAwareAssessmentService = AssessmentService & {
  checkHealth: () => Promise<boolean>;
};

type AssessmentRecord = Prisma.AssessmentGetPayload<{
  include: {
    items: true;
    objectives: true;
  };
}>;

type AssessmentAttemptRecord = Prisma.AssessmentAttemptGetPayload<{
  include: {
    assessment: {
      include: {
        items: true;
        objectives: true;
      };
    };
  };
}>;

interface DomainError extends Error {
  code: string;
  statusCode: number;
  details?: Record<string, unknown>;
}

// Item record from Prisma database - loosely typed since it comes from various Prisma queries
type AssessmentItemRecord = {
  id: string;
  points: number;
  itemType?: string;
  choices?: unknown;
  metadata?: unknown;
  [key: string]: unknown;
};

// Model ID for the deterministic fallback path. Configurable so different
// environments (dev / staging / prod) can report the correct model name in
// AssessmentGenerationResult.  Defaults to a stable production identifier.
const DEFAULT_GENERATOR_MODEL =
  process.env["ASSESSMENT_MODEL_ID"] ?? "tutorputor-assessment-v1";
const MAX_GENERATED_ITEMS = 10;
const irtService = new IRTCalibrationService();
const misconceptionDatabase = new MisconceptionDatabase();
const misconceptionDetector = new MisconceptionDetector(misconceptionDatabase);

// =============================================================================
// Implementation
// =============================================================================

export function createAssessmentService(
  prisma: TutorPrismaClient,
): HealthAwareAssessmentService {
  const learnerProfileService = createLearnerProfileService(prisma);
  const tenantAccessValidator = new TenantAccessValidator(prisma);
  const simulationAssessmentIntegration =
    createSimulationAssessmentIntegration(prisma);
  return {
    async listAssessments({ tenantId, moduleId, status, cursor, limit = 20 }) {
      const where: Prisma.AssessmentWhereInput = { tenantId };
      if (moduleId) {
        where.moduleId = moduleId;
      }
      if (status) {
        where.status = status;
      }

      const paginationArgs = {
        take: Math.min(limit, 50),
        ...(cursor ? { cursor } : {}),
      };
      const records = await paginate(prisma.assessment, where, paginationArgs, {
        orderField: "createdAt",
      });

      return {
        items: records.items.map(mapAssessmentSummary),
        nextCursor: (records.nextCursor as AssessmentId | undefined) ?? null,
      };
    },

    async getAssessment({ tenantId, assessmentId, userId, includeDraft }) {
      void userId;
      void includeDraft;

      const record = await tenantAccessValidator.validateEntityAccess(
        "Assessment",
        (args) =>
          prisma.assessment.findFirst({
            ...args,
            include: { items: true, objectives: true },
          }),
        assessmentId,
        { tenantId },
      );

      return mapAssessment(record);
    },

    async generateAssessmentItems(args) {
      const generated = await generateItems(
        prisma,
        learnerProfileService,
        simulationAssessmentIntegration,
        args,
      );
      // Logic assumes draft creation happens here or in caller.
      // Source code had draft creation:
      await prisma.assessmentDraft.create({
        data: {
          tenantId: args.tenantId,
          moduleId: args.moduleId,
          createdBy: args.userId,
          payload: generated.items as unknown as Prisma.InputJsonValue,
        },
      });
      return generated;
    },

    async startAttempt({ tenantId, assessmentId, userId }) {
      const assessment = await tenantAccessValidator.validateEntityAccess(
        "Assessment",
        (args) =>
          prisma.assessment.findFirst({
            ...args,
            include: { items: true, objectives: true },
          }),
        assessmentId,
        { tenantId },
      );
      if (assessment.status !== "PUBLISHED") {
        throw validationError("Assessment is not published yet.");
      }

      const attempt = await prisma.assessmentAttempt.create({
        data: {
          tenantId,
          assessmentId,
          userId,
          status: "IN_PROGRESS",
        },
        include: {
          assessment: {
            include: { items: true, objectives: true },
          },
        },
      });

      return mapAttempt(attempt);
    },

    async submitAttempt({ tenantId, attemptId, userId, responses }) {
      const attempt = await tenantAccessValidator.validateEntityAccess(
        "Assessment attempt",
        (args) =>
          prisma.assessmentAttempt.findFirst({
            ...args,
            include: {
              assessment: {
                include: { items: true, objectives: true },
              },
            },
          }),
        attemptId,
        { tenantId, userId },
      );

      const grading = await gradeAttempt(
        attempt.assessment.items,
        responses,
        tenantId,
        attempt.assessment.id,
      );
      await syncLearnerSignals({
        prisma,
        learnerProfileService,
        tenantId,
        userId,
        domain: attempt.assessment.moduleId,
        items: attempt.assessment.items.map(mapAssessmentItem),
        responses,
        feedback: grading.feedback,
      });

      const updated = await prisma.assessmentAttempt.update({
        where: { id: attemptId },
        data: {
          status: "GRADED",
          responses,
          scorePercent: grading.scorePercent,
          feedback: grading.feedback as unknown as Prisma.InputJsonValue,
          submittedAt: new Date(),
          gradedAt: new Date(),
          // Confidence-Based Marking (TODO 13)
          averageConfidence: calculateAverageConfidence(responses),
          confidenceBreakdown: calculateConfidenceBreakdown(responses) as Prisma.InputJsonValue,
        },
        include: {
          assessment: {
            include: { items: true, objectives: true },
          },
        },
      });

      return mapAttempt(updated);
    },

    async checkHealth() {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    },
  };
}

// =============================================================================
// Helper Functions
// =============================================================================

async function generateItems(
  prisma: TutorPrismaClient,
  learnerProfileService: ReturnType<typeof createLearnerProfileService>,
  simulationAssessmentIntegration: ReturnType<
    typeof createSimulationAssessmentIntegration
  >,
  args: AssessmentGenerationInput & { userId: UserId },
): Promise<AssessmentGenerationResult> {
  const module = await prisma.module.findFirst({
    where: { id: args.moduleId, tenantId: args.tenantId },
    include: { learningObjectives: true },
  });

  if (!module) {
    throw validationError("Module not found for assessment generation.");
  }

  const objectiveCandidates =
    args.objectiveIds.length > 0
      ? module.learningObjectives.filter((objective) =>
          args.objectiveIds.includes(objective.id.toString()),
        )
      : module.learningObjectives;

  const count = Math.min(Math.max(args.count, 1), MAX_GENERATED_ITEMS);
  const personalization =
    await learnerProfileService.getPersonalizationSnapshot(
      args.tenantId,
      args.userId,
      module.title,
    );
  const targetTheta = irtService.estimateThetaFromMastery(
    personalization.masterySummary.averageMastery,
  );
  const targetDifficulty = mapThetaToDifficulty(targetTheta, args.difficulty);
  const misconceptionTargets = misconceptionDatabase.findByDomainAndTopic(
    module.domain,
    module.title,
  );
  const simulationItemTarget = Math.min(2, Math.max(0, Math.floor(count / 3)));
  const simulationItems =
    simulationItemTarget > 0
      ? await simulationAssessmentIntegration.createModuleAssessmentItems({
          tenantId: args.tenantId,
          moduleId: args.moduleId,
          count: simulationItemTarget,
          difficulty: targetDifficulty,
          objectiveLabels: objectiveCandidates.map(
            (objective) => objective.label,
          ),
        })
      : [];

  // Attempt AI Generation
  try {
    const aiResponse = await aiClient.generateAssessmentItems({
      topic: module.title,
      objectives: objectiveCandidates.map((o) => o.label),
      difficulty: targetDifficulty,
      count: count,
      learner_level: personalization.adjustedDifficulty,
    });

    if (aiResponse && aiResponse.items && aiResponse.items.length > 0) {
      const items = rankAdaptiveItems(
        aiResponse.items.map((aiItem, index) => {
          const adaptiveParams = {
            source: "ai-service",
            moduleSlug: module.slug,
            moduleTitle: module.title,
            itemIndex: index,
            difficulty: targetDifficulty,
            prompt: aiItem.prompt,
            points: aiItem.points || 10,
            choiceSeed: (aiItem.choices ?? []).map((c) => ({
              label: c.label,
              isCorrect: c.is_correct,
              rationale: c.rationale,
            })),
            modelAnswer: aiItem.correct_answer_explanation,
            misconceptionTargets,
          } satisfies Omit<
            Parameters<typeof buildAdaptiveItem>[0],
            "objective"
          >;

          const objective =
            objectiveCandidates[index % objectiveCandidates.length] ??
            objectiveCandidates[0];

          return objective
            ? buildAdaptiveItem({ ...adaptiveParams, objective })
            : buildAdaptiveItem(adaptiveParams);
        }),
        targetTheta,
        count,
      );

      return {
        items: mergeAdaptiveItems(items, simulationItems, targetTheta, count),
        model: "tutorputor-ai-v1",
        ...(simulationItems.length > 0
          ? { warnings: ["Included simulation-based assessment items."] }
          : {}),
      };
    }
  } catch (error) {
    logger.warn({
      message: "AI Assessment generation failed, falling back to deterministic",
      error: error instanceof Error ? error.message : String(error),
      moduleId: module.id,
      difficulty: args.difficulty,
    });
  }

  // Fallback to deterministic
  const items = buildDeterministicItems({
    moduleTitle: module.title,
    moduleSlug: module.slug,
    objectives: objectiveCandidates.map((objective) => ({
      label: objective.label,
      taxonomyLevel: objective.taxonomyLevel,
    })),
    count,
    difficulty: targetDifficulty,
    misconceptionTargets,
    targetTheta,
  });

  return {
    items: mergeAdaptiveItems(items, simulationItems, targetTheta, count),
    warnings: [
      "AI service unavailable, using backup generator.",
      ...(simulationItems.length > 0
        ? ["Included simulation-based assessment items."]
        : []),
    ],
    model: DEFAULT_GENERATOR_MODEL,
  };
}

function buildDeterministicItems(params: {
  moduleTitle: string;
  moduleSlug: string;
  objectives: Array<{ label: string; taxonomyLevel: string }>;
  count: number;
  difficulty: Difficulty;
  misconceptionTargets: Array<{
    id: string;
    distractor: string;
    explanation: string;
    prerequisiteConceptId?: string;
  }>;
  targetTheta: number;
}): AssessmentItem[] {
  const {
    moduleTitle,
    moduleSlug,
    objectives,
    count,
    difficulty,
    misconceptionTargets,
    targetTheta,
  } = params;
  const baseObjectives =
    objectives.length > 0
      ? objectives
      : [{ label: moduleTitle, taxonomyLevel: "understand" }];
  const items = rankAdaptiveItems(
    baseObjectives.map((objective, index) =>
      buildAdaptiveItem({
        source: "deterministic-generator",
        moduleSlug,
        moduleTitle,
        itemIndex: index,
        objective,
        difficulty,
        prompt: `(${difficulty}) ${objective.label} – pick the best summary.`,
        points: 10,
        misconceptionTargets,
      }),
    ),
    targetTheta,
    count,
  );

  if (items.length >= count) {
    return items;
  }

  for (let index = items.length; index < count; index++) {
    const objective = baseObjectives[index % baseObjectives.length]!;
    items.push(
      buildAdaptiveItem({
        source: "deterministic-generator",
        moduleSlug,
        moduleTitle,
        itemIndex: index,
        objective,
        difficulty,
        prompt: `(${difficulty}) ${objective.label} – pick the best summary.`,
        points: 10,
        misconceptionTargets,
      }),
    );
  }

  return items;
}

function mapAssessmentSummary(
  record: Record<string, unknown>,
): AssessmentSummary {
  return {
    id: record.id as AssessmentSummary["id"],
    moduleId: record.moduleId as ModuleId,
    title: String(record.title ?? ""),
    type: record.type as AssessmentSummary["type"],
    status: record.status as AssessmentSummary["status"],
    version: Number(record.version ?? 1),
    passingScore: Number(record.passingScore ?? 0),
    attemptsAllowed: Number(record.attemptsAllowed ?? 1),
    timeLimitMinutes:
      record.timeLimitMinutes == null ? null : Number(record.timeLimitMinutes),
  };
}

function mapAssessment(record: AssessmentRecord): Assessment {
  return {
    ...mapAssessmentSummary(record),
    createdBy: record.createdBy as UserId,
    updatedBy: record.updatedBy as UserId,
    createdAt: record.createdAt.toISOString(),
    updatedAt: record.updatedAt.toISOString(),
    objectives: record.objectives.map((objective) => ({
      id: `${record.id}-${objective.id}`,
      label: objective.label,
      taxonomyLevel:
        objective.taxonomyLevel as Assessment["objectives"][number]["taxonomyLevel"],
    })),
    items: record.items
      .slice()
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .map(mapAssessmentItem),
  };
}

function mapAssessmentItem(item: Record<string, unknown>): AssessmentItem {
  const choices = parseChoices(item.choices);

  return {
    id: item.id as AssessmentItem["id"],
    type: item.itemType as AssessmentItem["type"],
    prompt: String(item.prompt ?? ""),
    points: Number(item.points ?? 0),
    ...(item.stimulus ? { stimulus: String(item.stimulus) } : {}),
    ...(choices ? { choices } : {}),
    ...(item.modelAnswer ? { modelAnswer: String(item.modelAnswer) } : {}),
    ...(item.rubric ? { rubric: String(item.rubric) } : {}),
    ...(item.metadata
      ? { metadata: item.metadata as Record<string, unknown> }
      : {}),
  };
}

function mapAttempt(record: AssessmentAttemptRecord): AssessmentAttempt {
  const feedback = parseFeedback(record.feedback);

  return {
    id: record.id as AssessmentAttemptId,
    assessmentId: record.assessmentId as AssessmentId,
    tenantId: record.tenantId as TenantId,
    userId: record.userId as UserId,
    status: record.status as AssessmentAttempt["status"],
    responses: (record.responses as AssessmentAttempt["responses"]) ?? {},
    startedAt: record.startedAt.toISOString(),
    ...(record.scorePercent !== null && record.scorePercent !== undefined
      ? { scorePercent: record.scorePercent }
      : {}),
    ...(feedback ? { feedback } : {}),
    ...(record.submittedAt
      ? { submittedAt: record.submittedAt.toISOString() }
      : {}),
    ...(record.gradedAt ? { gradedAt: record.gradedAt.toISOString() } : {}),
    ...(record.timeSpentSeconds !== null &&
    record.timeSpentSeconds !== undefined
      ? { timeSpentSeconds: record.timeSpentSeconds }
      : {}),
  };
}

function parseChoices(value: unknown): AssessmentItemChoice[] | undefined {
  if (!value) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value as AssessmentItemChoice[];
  }
  return undefined;
}

function parseFeedback(value: unknown): AssessmentFeedback[] | undefined {
  if (!value) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value as AssessmentFeedback[];
  }
  return undefined;
}

async function gradeAttempt(
  items: Array<{ id: string; points: number; [key: string]: unknown }>,
  responses: AssessmentAttempt["responses"],
  tenantId: string,
  assessmentId: string,
): Promise<{ scorePercent: number; feedback: AssessmentFeedback[] }> {
  const normalizedResponses = responses ?? {};
  let totalPoints = 0;
  let earnedPoints = 0;
  const feedback: AssessmentFeedback[] = [];

  for (const item of items) {
    totalPoints += item.points;
    const response =
      normalizedResponses[item.id as keyof typeof normalizedResponses];
    
    // Use AI grading for open-ended responses, keep structured grading for MCQ
    const analysis = await evaluateResponse(item, response, tenantId, assessmentId);
    earnedPoints += analysis.earnedPoints;
    feedback.push(analysis.feedback);
  }

  const scorePercent =
    totalPoints === 0 ? 0 : Math.round((earnedPoints / totalPoints) * 100);

  return { scorePercent, feedback };
}

function buildAdaptiveItem(params: {
  source: string;
  moduleSlug: string;
  moduleTitle: string;
  itemIndex: number;
  objective?: { label: string; taxonomyLevel: string };
  difficulty: string;
  prompt: string;
  points: number;
  choiceSeed?: Array<{
    label: string;
    isCorrect?: boolean;
    rationale?: string;
  }>;
  modelAnswer?: string;
  misconceptionTargets: Array<{
    id: string;
    distractor: string;
    explanation: string;
    prerequisiteConceptId?: string;
  }>;
}): AssessmentItem {
  const {
    source,
    moduleSlug,
    moduleTitle,
    itemIndex,
    objective,
    difficulty,
    prompt,
    points,
    choiceSeed = [],
    modelAnswer,
    misconceptionTargets,
  } = params;
  const id = `${moduleSlug}-gen-${itemIndex}`;
  const correctChoiceId = `${id}-choice-correct`;
  const misconceptionTarget =
    misconceptionTargets[itemIndex % Math.max(misconceptionTargets.length, 1)];
  const objectiveLabel = objective?.label ?? moduleTitle;
  const taxonomyLevel = objective?.taxonomyLevel ?? "understand";
  const irt = irtService.calibrateForDifficulty(difficulty, taxonomyLevel);

  const choices: AssessmentItemChoice[] =
    choiceSeed.length > 0
      ? choiceSeed.map((choice, choiceIndex) => ({
          id: `${id}-choice-${choiceIndex}`,
          label: choice.label,
          ...(choice.isCorrect !== undefined
            ? { isCorrect: choice.isCorrect }
            : {}),
          ...(choice.rationale ? { rationale: choice.rationale } : {}),
        }))
      : [
          {
            id: `${id}-choice-a`,
            label: `Unrelated fact about ${moduleTitle}`,
          },
          {
            id: correctChoiceId,
            label: `Key concept: ${objectiveLabel}`,
            isCorrect: true,
            rationale: "Matches the stated objective.",
          },
          {
            id: `${id}-choice-b`,
            label:
              misconceptionTarget?.distractor ??
              "Common but incorrect interpretation.",
            ...(misconceptionTarget
              ? { rationale: misconceptionTarget.explanation }
              : {}),
          },
        ];

  if (!choices.some((choice) => choice.isCorrect)) {
    choices.unshift({
      id: correctChoiceId,
      label: `Key concept: ${objectiveLabel}`,
      isCorrect: true,
      rationale: "Matches the stated objective.",
    });
  }

  return {
    id: id as AssessmentItem["id"],
    type: "multiple_choice_single",
    prompt,
    stimulus: `Objective: ${objectiveLabel}`,
    points,
    choices,
    ...(modelAnswer ? { modelAnswer } : {}),
    metadata: {
      generated: true,
      source,
      taxonomyLevel,
      objectiveLabel,
      topic: moduleTitle,
      conceptId: slugifyConceptId(objectiveLabel),
      irt,
      misconceptionId: misconceptionTarget?.id,
      prerequisiteConceptId: misconceptionTarget?.prerequisiteConceptId,
    },
  };
}

function rankAdaptiveItems(
  items: AssessmentItem[],
  targetTheta: number,
  count: number,
): AssessmentItem[] {
  const ranked = irtService.selectNextItems(
    items.map((item) => {
      const metadata = parseItemMetadata(item.metadata);
      const irt = parseIRTParameters(metadata.irt);
      return { item, irt };
    }),
    targetTheta,
    Math.min(count, items.length),
  );

  return ranked.items;
}

function mergeAdaptiveItems(
  baseItems: AssessmentItem[],
  simulationItems: AssessmentItem[],
  targetTheta: number,
  count: number,
): AssessmentItem[] {
  if (simulationItems.length === 0) {
    return rankAdaptiveItems(baseItems, targetTheta, count);
  }

  const rankedBase = rankAdaptiveItems(
    baseItems,
    targetTheta,
    Math.max(0, count - simulationItems.length),
  );
  const rankedSimulation = rankAdaptiveItems(
    simulationItems,
    targetTheta,
    Math.min(simulationItems.length, Math.max(1, Math.ceil(count / 3))),
  );
  return [...rankedBase, ...rankedSimulation].slice(0, count);
}

async function syncLearnerSignals(args: {
  prisma: TutorPrismaClient;
  learnerProfileService: ReturnType<typeof createLearnerProfileService>;
  tenantId: TenantId;
  userId: UserId;
  domain: string;
  items: AssessmentItem[];
  responses: AssessmentAttempt["responses"];
  feedback: AssessmentFeedback[];
}) {
  const {
    learnerProfileService,
    tenantId,
    userId,
    domain,
    items,
    responses,
    feedback,
  } = args;

  for (const item of items) {
    const itemFeedback = feedback.find((entry) => entry.itemId === item.id);
    const metadata = parseItemMetadata(item.metadata);
    const conceptId = String(metadata.conceptId ?? item.id);
    const isCorrect = (itemFeedback?.scorePercent ?? 0) >= 100;

    await learnerProfileService.updateMastery(tenantId, userId, {
      conceptId,
      correct: isCorrect,
      confidence: Math.max((itemFeedback?.scorePercent ?? 0) / 100, 0.2),
      attempts: 1,
    });
  }

  const signals = misconceptionDetector.detectFromAttempt({
    domain: mapAssessmentDomain(domain),
    items,
    responses,
    feedback,
  });

  for (const signal of signals) {
    await learnerProfileService.recordKnowledgeGap(tenantId, userId, {
      conceptId: signal.conceptId,
      prerequisiteId:
        signal.prerequisiteConceptId ?? `${signal.conceptId}-foundation`,
      severity: signal.confidence >= 0.85 ? "HIGH" : "MEDIUM",
      detectedBy: "ASSESSMENT",
      evidence: {
        misconceptionId: signal.misconceptionId,
        explanation: signal.explanation,
        confidence: signal.confidence,
      },
    });
  }
}

function mapAssessmentDomain(moduleId: string): string {
  const normalized = moduleId.toLowerCase();
  if (normalized.includes("math")) return "MATH";
  if (normalized.includes("tech") || normalized.includes("code")) return "TECH";
  return "SCIENCE";
}

function parseItemMetadata(
  value: AssessmentItem["metadata"],
): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

function parseIRTParameters(value: unknown) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const irt = value as Record<string, unknown>;
    return {
      discrimination:
        typeof irt.discrimination === "number" ? irt.discrimination : 1,
      difficulty: typeof irt.difficulty === "number" ? irt.difficulty : 0,
      guessing: typeof irt.guessing === "number" ? irt.guessing : 0.2,
    };
  }

  return irtService.calibrateForDifficulty("INTERMEDIATE", "understand");
}

function slugifyConceptId(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function mapThetaToDifficulty(
  theta: number,
  requested: Difficulty,
): Difficulty {
  if (requested === "ADVANCED" || requested === "INTRO") {
    return requested;
  }
  if (theta <= -0.75) return "INTRO";
  if (theta >= 0.75) return "ADVANCED";
  return "INTERMEDIATE";
}

async function evaluateResponse(
  item: AssessmentItemRecord,
  response: AssessmentResponse | undefined,
  tenantId: string,
  assessmentId: string,
): Promise<{ earnedPoints: number; feedback: AssessmentFeedback }> {
  const defaultFeedback: AssessmentFeedback = {
    itemId: item.id as AssessmentItem["id"],
    scorePercent: 0,
    needsReview: true,
  };

  if (!response) {
    return {
      earnedPoints: 0,
      feedback: { ...defaultFeedback, comments: "No response provided." },
    };
  }

  const choices = parseChoices(item.choices) ?? [];

  // Handle multiple choice questions
  if (
    item.itemType === "multiple_choice_single" &&
    response.type === "multiple_choice"
  ) {
    const correctIds = choices
      .filter((choice) => choice.isCorrect)
      .map((choice) => choice.id);
    const isCorrect =
      response.selectedChoiceIds.length === 1 &&
      correctIds.includes(response.selectedChoiceIds[0]!);

    // Adjust scoring based on confidence
    let scorePercent = isCorrect ? 100 : 0;
    let needsReview = !isCorrect;
    let comments = isCorrect
      ? "Correct selection."
      : "Review the concept and try again.";

    // Confidence-based marking
    if (response.confidence) {
      if (isCorrect) {
        // High confidence correct answers get full credit
        if (response.confidence === "high") {
          comments = "Correct! You demonstrated strong understanding.";
        } else if (response.confidence === "low") {
          comments = "Correct, but consider building more confidence in this area.";
          scorePercent = 90; // Slightly reduced for low confidence correct answers
        }
      } else {
        // High confidence incorrect answers indicate misconception
        if (response.confidence === "high") {
          comments = "Incorrect. Your high confidence suggests a misconception - review this concept carefully.";
          needsReview = true;
        } else if (response.confidence === "low") {
          comments = "Incorrect. Good that you recognized uncertainty - review the concept.";
          scorePercent = 10; // Partial credit for recognizing uncertainty
        }
      }
    }

    return {
      earnedPoints: (scorePercent / 100) * item.points,
      feedback: {
        itemId: item.id as AssessmentItem["id"],
        scorePercent,
        needsReview,
        comments,
      },
    };
  }

  // Handle simulation interaction - use type assertion since we've already checked response is defined
  if (item.itemType === "simulation_interaction" && "trace" in response) {
    const simulationResponse = response as Extract<
      AssessmentResponse,
      { type: "simulation_interaction" }
    >;
    return scoreSimulationAssessmentResponse({
      item: {
        id: item.id,
        points: item.points,
        ...(item.metadata &&
        typeof item.metadata === "object" &&
        !Array.isArray(item.metadata)
          ? { metadata: item.metadata as Record<string, unknown> }
          : {}),
      },
      response: simulationResponse,
    });
  }

  // For short answer / free response types, use AI grading
  if (item.itemType === "short_answer" && response.type === "free_response") {
    try {
      const freeResponse = response as Extract<AssessmentResponse, { type: "free_response" }>;
      const context: {
        domain?: string;
        difficulty?: string;
        learningObjectives?: string[];
      } = {};
      if (item.moduleId) context.domain = item.moduleId as string;
      if (item.difficulty) context.difficulty = item.difficulty as string;

      const aiResult = await aiGradingService.gradeOpenEndedResponse({
        tenantId,
        assessmentId,
        itemId: item.id as string,
        questionPrompt: item.prompt as string,
        studentResponse: freeResponse.text,
        context,
      });

      // Adjust scoring based on confidence
      let scorePercent = aiResult.scorePercent;
      let needsReview = aiResult.needsReview;
      let comments = aiResult.feedback.comments;

      if (freeResponse.confidence) {
        if (scorePercent >= 70) {
          // High confidence good answers
          if (freeResponse.confidence === "high") {
            comments += " Excellent confidence in your understanding.";
          } else if (freeResponse.confidence === "low") {
            comments += " You answered correctly despite low confidence - trust your knowledge!";
            scorePercent = Math.min(100, scorePercent + 5); // Bonus for correct answer with low confidence
          }
        } else {
          // Low confidence poor answers
          if (freeResponse.confidence === "low") {
            comments += " Your low confidence was appropriate - keep studying this area.";
          } else if (freeResponse.confidence === "high") {
            comments += " Your high confidence on this answer suggests a misconception - review carefully.";
            needsReview = true;
          }
        }
      }

      return {
        earnedPoints: (scorePercent / 100) * item.points,
        feedback: {
          itemId: item.id as AssessmentItem["id"],
          scorePercent,
          needsReview,
          comments,
        },
      };
    } catch (error) {
      // Fallback to manual review if AI grading fails
      return {
        earnedPoints: 0,
        feedback: {
          ...defaultFeedback,
          comments: "AI grading failed. Requires instructor review.",
        },
      };
    }
  }

  // For other response types, flag for instructor review
  return {
    earnedPoints: 0,
    feedback: {
      ...defaultFeedback,
      comments: "Requires instructor review for qualitative scoring.",
    },
  };
}

function validationError(message: string): DomainError {
  return createDomainError("ASSESSMENT_VALIDATION_ERROR", 400, message);
}

function createDomainError(
  code: string,
  statusCode: number,
  message: string,
): DomainError {
  const error = new Error(message) as DomainError;
  error.code = code;
  error.statusCode = statusCode;
  return error;
}

// =============================================================================
// Confidence-Based Marking Helpers (TODO 13)
// =============================================================================

function calculateAverageConfidence(responses: Record<string, unknown>): number | null {
  if (!responses || typeof responses !== "object") {
    return null;
  }

  const confidences: number[] = [];
  
  for (const [itemId, response] of Object.entries(responses)) {
    const responseObj = response as Record<string, unknown>;
    const confidence = responseObj.confidence;
    
    // Handle string confidence values (low/medium/high)
    if (typeof confidence === "string") {
      const confidenceMap: Record<string, number> = {
        low: 0.33,
        medium: 0.66,
        high: 1.0,
      };
      confidences.push(confidenceMap[confidence.toLowerCase()] ?? 0.5);
    } 
    // Handle numeric confidence values (0.0 - 1.0)
    else if (typeof confidence === "number") {
      confidences.push(Math.max(0, Math.min(1, confidence)));
    }
  }

  if (confidences.length === 0) {
    return null;
  }

  return confidences.reduce((sum, conf) => sum + conf, 0) / confidences.length;
}

function calculateConfidenceBreakdown(responses: Record<string, unknown>): Record<string, number> {
  const breakdown: Record<string, number> = {};
  
  if (!responses || typeof responses !== "object") {
    return breakdown;
  }

  for (const [itemId, response] of Object.entries(responses)) {
    const responseObj = response as Record<string, unknown>;
    const confidence = responseObj.confidence;
    
    if (typeof confidence === "string") {
      const confidenceMap: Record<string, number> = {
        low: 0.33,
        medium: 0.66,
        high: 1.0,
      };
      breakdown[itemId] = confidenceMap[confidence.toLowerCase()] ?? 0.5;
    } 
    else if (typeof confidence === "number") {
      breakdown[itemId] = Math.max(0, Math.min(1, confidence));
    }
  }

  return breakdown;
}
