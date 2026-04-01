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
  TenantId,
  UserId,
  ModuleId,
  AssessmentId,
  Difficulty,
} from "@tutorputor/contracts/v1/types";
import type { Prisma, TutorPrismaClient } from "@tutorputor/core/db";

import { aiClient } from "../../clients/ai-client";
import { createStandaloneLogger } from '@tutorputor/core/logger';
import { createLearnerProfileService } from "./learner-profile-service.js";
import { IRTCalibrationService } from "../assessment/irt/service.js";
import { MisconceptionDatabase } from "../assessment/misconceptions/database.js";
import {
  MisconceptionDetector,
  type MisconceptionSignal,
} from "../assessment/misconceptions/detector.js";
import {
  createSimulationAssessmentIntegration,
  scoreSimulationAssessmentResponse,
} from "../assessment/simulation-integration/service.js";

const logger = createStandaloneLogger({ component: 'AssessmentService' });

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

// Model ID for the deterministic fallback path. Configurable so different
// environments (dev / staging / prod) can report the correct model name in
// AssessmentGenerationResult.  Defaults to a stable production identifier.
const DEFAULT_GENERATOR_MODEL =
  process.env['ASSESSMENT_MODEL_ID'] ?? 'tutorputor-assessment-v1';
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
  const simulationAssessmentIntegration =
    createSimulationAssessmentIntegration(prisma);
  return {
    async listAssessments({ tenantId, moduleId, status, cursor, limit = 20 }) {
      const take = Math.min(limit, 50);
      const where: Prisma.AssessmentWhereInput = { tenantId };
      if (moduleId) {
        where.moduleId = moduleId;
      }
      if (status) {
        where.status = status;
      }

      const records = await prisma.assessment.findMany({
        where,
        take: take + 1,
        orderBy: { createdAt: "desc" },
        ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
        include: { items: false, objectives: false },
      });

      const hasMore = records.length > take;
      const trimmed = records.slice(0, take);
      return {
        items: trimmed.map(mapAssessmentSummary),
        nextCursor: hasMore
          ? (trimmed[trimmed.length - 1]?.id as AssessmentId)
          : null,
      };
    },

    async getAssessment({ tenantId, assessmentId, userId, includeDraft }) {
      void userId;
      void includeDraft;

      const record = await prisma.assessment.findFirst({
        where: { id: assessmentId, tenantId },
        include: { items: true, objectives: true },
      });

      if (!record) {
        throw notFoundError("Assessment not found");
      }

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
      const assessment = await prisma.assessment.findFirst({
        where: { id: assessmentId, tenantId },
        include: { items: true, objectives: true },
      });
      if (!assessment) {
        throw notFoundError("Assessment not found");
      }
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
      const attempt = await prisma.assessmentAttempt.findFirst({
        where: { id: attemptId, tenantId, userId },
        include: {
          assessment: {
            include: { items: true, objectives: true },
          },
        },
      });

      if (!attempt) {
        throw notFoundError("Attempt not found for this user.");
      }

      const grading = gradeAttempt(attempt.assessment.items, responses);
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
          objectiveLabels: objectiveCandidates.map((objective) => objective.label),
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
      message: 'AI Assessment generation failed, falling back to deterministic',
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

function mapAssessmentSummary(record: Record<string, unknown>): AssessmentSummary {
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
    ...(record.submittedAt ? { submittedAt: record.submittedAt.toISOString() } : {}),
    ...(record.gradedAt ? { gradedAt: record.gradedAt.toISOString() } : {}),
    ...(record.timeSpentSeconds !== null && record.timeSpentSeconds !== undefined
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

function gradeAttempt(
  items: Array<{ id: string; points: number; [key: string]: unknown }>,
  responses: AssessmentAttempt["responses"],
): { scorePercent: number; feedback: AssessmentFeedback[] } {
  const normalizedResponses = responses ?? {};
  let totalPoints = 0;
  let earnedPoints = 0;
  const feedback: AssessmentFeedback[] = [];

  for (const item of items) {
    totalPoints += item.points;
    const response =
      normalizedResponses[item.id as keyof typeof normalizedResponses];
    const analysis = evaluateResponse(item, response);
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
  choiceSeed?: Array<{ label: string; isCorrect?: boolean; rationale?: string }>;
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
          ...(choice.isCorrect !== undefined ? { isCorrect: choice.isCorrect } : {}),
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
              misconceptionTarget?.distractor ?? "Common but incorrect interpretation.",
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

function parseItemMetadata(value: AssessmentItem["metadata"]): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

function parseIRTParameters(value: unknown) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const irt = value as Record<string, unknown>;
    return {
      discrimination: typeof irt.discrimination === "number" ? irt.discrimination : 1,
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

function mapThetaToDifficulty(theta: number, requested: Difficulty): Difficulty {
  if (requested === "ADVANCED" || requested === "INTRO") {
    return requested;
  }
  if (theta <= -0.75) return "INTRO";
  if (theta >= 0.75) return "ADVANCED";
  return "INTERMEDIATE";
}

function evaluateResponse(
  item: any,
  response: any,
): { earnedPoints: number; feedback: AssessmentFeedback } {
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

    const scorePercent = isCorrect ? 100 : 0;
    return {
      earnedPoints: isCorrect ? item.points : 0,
      feedback: {
        itemId: item.id as AssessmentItem["id"],
        scorePercent,
        needsReview: !isCorrect,
        comments: isCorrect
          ? "Correct selection."
          : "Review the concept and try again.",
      },
    };
  }

  if (
    item.itemType === "simulation_interaction" &&
    response.type === "simulation_interaction"
  ) {
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
      response,
    });
  }

  // For short answer / free response types we flag for instructor review
  return {
    earnedPoints: 0,
    feedback: {
      ...defaultFeedback,
      comments: "Requires instructor review for qualitative scoring.",
    },
  };
}

function notFoundError(message: string): DomainError {
  return createDomainError("ASSESSMENT_NOT_FOUND", 404, message);
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
