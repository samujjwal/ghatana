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

import type { AssessmentService } from "@ghatana/tutorputor-contracts/v1/services";
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
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

// =============================================================================
// Types
// =============================================================================

export type HealthAwareAssessmentService = AssessmentService & {
  checkHealth: () => Promise<boolean>;
};

type AssessmentRecord = any;
type AssessmentAttemptRecord = any;

interface DomainError extends Error {
  code: string;
  statusCode: number;
  details?: Record<string, unknown>;
}

const DEFAULT_GENERATOR_MODEL = "tutorputor-ai-stub";
const MAX_GENERATED_ITEMS = 10;

import { aiClient } from "../../clients/ai-client";

// =============================================================================
// Implementation
// =============================================================================

export function createAssessmentService(
  prisma: TutorPrismaClient,
): HealthAwareAssessmentService {
  return {
    async listAssessments({ tenantId, moduleId, status, cursor, limit = 20 }) {
      const take = Math.min(limit, 50);
      const where: any = { tenantId };
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
          ? (records[records.length - 1]?.id as AssessmentId)
          : undefined,
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
      const generated = await generateItems(prisma, args);
      // Logic assumes draft creation happens here or in caller.
      // Source code had draft creation:
      await prisma.assessmentDraft.create({
        data: {
          tenantId: args.tenantId,
          moduleId: args.moduleId,
          createdBy: args.userId,
          payload: generated.items as any,
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

      const updated = await prisma.assessmentAttempt.update({
        where: { id: attemptId },
        data: {
          status: "GRADED",
          responses,
          scorePercent: grading.scorePercent,
          feedback: grading.feedback as any,
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
      ? module.learningObjectives.filter((objective: any) =>
        args.objectiveIds.includes(objective.id.toString()),
      )
      : module.learningObjectives;

  const count = Math.min(Math.max(args.count, 1), MAX_GENERATED_ITEMS);

  // Attempt AI Generation
  try {
    const aiResponse = await aiClient.generateAssessmentItems({
      topic: module.title,
      objectives: objectiveCandidates.map((o: any) => o.label),
      difficulty: args.difficulty,
      count: count,
      learner_level: "intermediate", // Could be dynamic based on user profile
    });

    if (aiResponse && aiResponse.items && aiResponse.items.length > 0) {
      const items = aiResponse.items.map((aiItem: any, index: number) => {
        const id = `${module.slug}-ai-${index}`;
        return {
          id: id as AssessmentItem["id"],
          type: "multiple_choice_single", // Force simplified type for now if needed, or map aiItem.type
          prompt: aiItem.prompt,
          stimulus: `Objective: ${objectiveCandidates[index % objectiveCandidates.length]?.label ?? module.title}`,
          points: aiItem.points || 10,
          choices: aiItem.choices.map((c: any) => ({
            id: c.id || `${id}-choice-${Math.random()}`,
            label: c.label,
            isCorrect: c.is_correct,
            rationale: c.rationale,
          })),
          modelAnswer: aiItem.correct_answer_explanation,
          metadata: {
            generated: true,
            source: "ai-service",
          },
        };
      });

      return {
        items,
        model: "tutorputor-ai-v1",
      };
    }
  } catch (error) {
    console.warn(
      "AI Assessment generation failed, falling back to deterministic:",
      error,
    );
  }

  // Fallback to deterministic
  const items = buildDeterministicItems({
    moduleTitle: module.title,
    moduleSlug: module.slug,
    objectives: objectiveCandidates.map((objective: any) => ({
      label: objective.label,
      taxonomyLevel: objective.taxonomyLevel,
    })),
    count,
    difficulty: args.difficulty,
  });

  return {
    items,
    warnings: ["AI service unavailable, using backup generator."],
    model: DEFAULT_GENERATOR_MODEL,
  };
}

function buildDeterministicItems(params: {
  moduleTitle: string;
  moduleSlug: string;
  objectives: Array<{ label: string; taxonomyLevel: string }>;
  count: number;
  difficulty: Difficulty;
}): AssessmentItem[] {
  const { moduleTitle, moduleSlug, objectives, count, difficulty } = params;
  const baseObjectives =
    objectives.length > 0
      ? objectives
      : [{ label: moduleTitle, taxonomyLevel: "understand" }];
  const items: AssessmentItem[] = [];

  for (let index = 0; index < count; index++) {
    const objective = baseObjectives[index % baseObjectives.length]!;
    const id = `${moduleSlug}-gen-${index}`;
    const correctChoiceId = `${id}-choice-correct`;

    items.push({
      id: id as AssessmentItem["id"],
      type: "multiple_choice_single",
      prompt: `(${difficulty}) ${objective.label} – pick the best summary.`,
      stimulus: `Objective: ${objective.label}`,
      points: 10,
      choices: [
        {
          id: `${id}-choice-a`,
          label: `Unrelated fact about ${moduleTitle}`,
        },
        {
          id: correctChoiceId,
          label: `Key concept: ${objective.label}`,
          isCorrect: true,
          rationale: "Matches the stated objective.",
        },
        {
          id: `${id}-choice-c`,
          label: "Placeholder distractor",
        },
      ],
      metadata: {
        generated: true,
        taxonomyLevel: objective.taxonomyLevel,
      },
    });
  }

  return items;
}

function mapAssessmentSummary(record: any): AssessmentSummary {
  return {
    id: record.id as AssessmentSummary["id"],
    moduleId: record.moduleId as ModuleId,
    title: record.title,
    type: record.type as AssessmentSummary["type"],
    status: record.status as AssessmentSummary["status"],
    version: record.version,
    passingScore: record.passingScore,
    attemptsAllowed: record.attemptsAllowed,
    timeLimitMinutes: record.timeLimitMinutes,
  };
}

function mapAssessment(record: AssessmentRecord): Assessment {
  return {
    ...mapAssessmentSummary(record),
    createdBy: record.createdBy as UserId,
    updatedBy: record.updatedBy as UserId,
    createdAt: record.createdAt.toISOString(),
    updatedAt: record.updatedAt.toISOString(),
    objectives: record.objectives.map((objective: any) => ({
      id: `${record.id}-${objective.id}`,
      label: objective.label,
      taxonomyLevel:
        objective.taxonomyLevel as Assessment["objectives"][number]["taxonomyLevel"],
    })),
    items: record.items
      .slice()
      .sort((a: any, b: any) => a.orderIndex - b.orderIndex)
      .map(mapAssessmentItem),
  };
}

function mapAssessmentItem(item: any): AssessmentItem {
  return {
    id: item.id as AssessmentItem["id"],
    type: item.itemType as AssessmentItem["type"],
    prompt: item.prompt,
    stimulus: item.stimulus ?? undefined,
    choices: parseChoices(item.choices),
    modelAnswer: item.modelAnswer ?? undefined,
    rubric: item.rubric ?? undefined,
    points: item.points,
    metadata: item.metadata ?? undefined,
  };
}

function mapAttempt(record: AssessmentAttemptRecord): AssessmentAttempt {
  return {
    id: record.id as AssessmentAttemptId,
    assessmentId: record.assessmentId as AssessmentId,
    tenantId: record.tenantId as TenantId,
    userId: record.userId as UserId,
    status: record.status as AssessmentAttempt["status"],
    responses: (record.responses as AssessmentAttempt["responses"]) ?? {},
    scorePercent: record.scorePercent ?? undefined,
    feedback: parseFeedback(record.feedback),
    startedAt: record.startedAt.toISOString(),
    submittedAt: record.submittedAt?.toISOString(),
    gradedAt: record.gradedAt?.toISOString(),
    timeSpentSeconds: record.timeSpentSeconds ?? undefined,
  };
}

function parseChoices(value: any): AssessmentItemChoice[] | undefined {
  if (!value) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value as AssessmentItemChoice[];
  }
  return undefined;
}

function parseFeedback(value: any): AssessmentFeedback[] | undefined {
  if (!value) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value as AssessmentFeedback[];
  }
  return undefined;
}

function gradeAttempt(
  items: any[],
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
