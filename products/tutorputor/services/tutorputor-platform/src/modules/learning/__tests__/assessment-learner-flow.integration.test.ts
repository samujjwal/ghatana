import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TutorPrismaClient } from "@tutorputor/core/db";

import { createAssessmentService } from "../assessment-service.js";

function makePrisma(
  overrides: Partial<TutorPrismaClient> = {},
): TutorPrismaClient {
  const learnerMastery = {
    findMany: vi.fn(),
    findUnique: vi.fn(),
    upsert: vi.fn(),
  };
  const learnerProfile = {
    findUnique: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  };
  const knowledgeGap = {
    findMany: vi.fn(),
    findFirst: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  };

  const prisma = {
    assessment: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
    },
    assessmentAttempt: {
      create: vi.fn(),
      findFirst: vi.fn(),
      update: vi.fn(),
    },
    assessmentDraft: {
      create: vi.fn(),
    },
    module: {
      findFirst: vi.fn(),
    },
    user: {
      findFirst: vi.fn(),
    },
    learnerProfile,
    learnerMastery,
    knowledgeGap,
    preferenceChange: {
      create: vi.fn(),
    },
    $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
    $transaction: vi.fn(async (input: unknown) => {
      if (typeof input === "function") {
        const tx = {
          learnerMastery,
          learnerProfile,
          knowledgeGap,
          preferenceChange: {
            create: vi.fn(),
          },
        };
        return (input as (txArg: typeof tx) => Promise<unknown>)(tx);
      }

      return input;
    }),
    ...overrides,
  } as unknown as TutorPrismaClient;

  return prisma;
}

const TENANT_ID = "tenant-1" as never;
const USER_ID = "user-1" as never;

function makeLearnerProfile() {
  return {
    id: "profile-1",
    tenantId: "tenant-1",
    userId: "user-1",
    preferredDifficulty: "MEDIUM",
    preferredModality: "MIXED",
    preferredPacing: "ADAPTIVE",
    preferredSessionMinutes: 30,
    notificationFrequency: "daily",
    visualLearningScore: 0.25,
    auditoryLearningScore: 0.25,
    kinestheticLearningScore: 0.25,
    readingLearningScore: 0.25,
    avgSessionMinutes: 30,
    preferredTimeOfDay: null,
    streakDays: 0,
    lastActiveAt: null,
  };
}

/**
 * @doc.type test
 * @doc.purpose Integration placeholders for learner assessment E2E flow
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
describe("Learner assessment flow integration", () => {
  let prisma: TutorPrismaClient;
  let service: ReturnType<typeof createAssessmentService>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createAssessmentService(prisma);

    vi.mocked(prisma.user.findFirst).mockResolvedValue({ id: "user-1" } as never);
    vi.mocked(prisma.learnerProfile.findUnique).mockResolvedValue(
      makeLearnerProfile() as never,
    );
    vi.mocked(prisma.learnerMastery.findUnique).mockResolvedValue(null);
    vi.mocked(prisma.knowledgeGap.findFirst).mockResolvedValue(null);
    vi.mocked(prisma.knowledgeGap.create).mockResolvedValue({ id: "gap-1" } as never);
  });

  it("creates an attempt and returns typed attempt payload", async () => {
    vi.mocked(prisma.assessment.findFirst).mockResolvedValue({
      id: "assessment-1",
      tenantId: "tenant-1",
      moduleId: "physics-newton-module",
      title: "Newton Quiz",
      type: "QUIZ",
      status: "PUBLISHED",
      version: 1,
      passingScore: 70,
      attemptsAllowed: 2,
      timeLimitMinutes: 20,
      createdBy: "teacher-1",
      updatedBy: "teacher-1",
      createdAt: new Date("2026-03-01T00:00:00.000Z"),
      updatedAt: new Date("2026-03-01T00:00:00.000Z"),
      objectives: [],
      items: [],
    } as never);

    vi.mocked(prisma.assessmentAttempt.create).mockResolvedValue({
      id: "attempt-1",
      assessmentId: "assessment-1",
      tenantId: "tenant-1",
      userId: "user-1",
      status: "IN_PROGRESS",
      responses: {},
      scorePercent: null,
      feedback: null,
      startedAt: new Date("2026-03-01T10:00:00.000Z"),
      submittedAt: null,
      gradedAt: null,
      timeSpentSeconds: null,
      assessment: {
        id: "assessment-1",
        moduleId: "physics-newton-module",
        title: "Newton Quiz",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 20,
        createdBy: "teacher-1",
        updatedBy: "teacher-1",
        createdAt: new Date("2026-03-01T00:00:00.000Z"),
        updatedAt: new Date("2026-03-01T00:00:00.000Z"),
        objectives: [],
        items: [],
      },
    } as never);

    const result = await service.startAttempt({
      tenantId: TENANT_ID,
      assessmentId: "assessment-1" as never,
      userId: USER_ID,
    });

    expect(result.id).toBe("attempt-1");
    expect(result.status).toBe("IN_PROGRESS");
    expect(result.responses).toEqual({});
  });

  it("submits attempt, grades feedback, updates mastery, and records knowledge gaps", async () => {
    vi.mocked(prisma.assessmentAttempt.findFirst).mockResolvedValue({
      id: "attempt-1",
      assessmentId: "assessment-1",
      tenantId: "tenant-1",
      userId: "user-1",
      status: "IN_PROGRESS",
      responses: {},
      scorePercent: null,
      feedback: null,
      startedAt: new Date("2026-03-01T10:00:00.000Z"),
      submittedAt: null,
      gradedAt: null,
      timeSpentSeconds: null,
      assessment: {
        id: "assessment-1",
        moduleId: "physics-newton-module",
        title: "Newton Quiz",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 20,
        createdBy: "teacher-1",
        updatedBy: "teacher-1",
        createdAt: new Date("2026-03-01T00:00:00.000Z"),
        updatedAt: new Date("2026-03-01T00:00:00.000Z"),
        objectives: [],
        items: [
          {
            id: "item-1",
            itemType: "multiple_choice_single",
            prompt: "What keeps motion constant?",
            points: 10,
            choices: [
              {
                id: "choice-a",
                label: "Objects must keep experiencing force to remain in motion.",
                isCorrect: false,
              },
              {
                id: "choice-b",
                label: "Net force is zero for constant velocity.",
                isCorrect: true,
              },
            ],
            metadata: {
              topic: "newton force motion",
              conceptId: "force-motion",
            },
          },
        ],
      },
    } as never);

    vi.mocked(prisma.assessmentAttempt.update).mockResolvedValue({
      id: "attempt-1",
      assessmentId: "assessment-1",
      tenantId: "tenant-1",
      userId: "user-1",
      status: "GRADED",
      responses: {
        "item-1": {
          type: "multiple_choice",
          selectedChoiceIds: ["choice-a"],
        },
      },
      scorePercent: 0,
      feedback: [
        {
          itemId: "item-1",
          scorePercent: 0,
          needsReview: true,
          comments: "Incorrect choice selected.",
        },
      ],
      startedAt: new Date("2026-03-01T10:00:00.000Z"),
      submittedAt: new Date("2026-03-01T10:05:00.000Z"),
      gradedAt: new Date("2026-03-01T10:05:00.000Z"),
      timeSpentSeconds: 300,
      assessment: {
        id: "assessment-1",
        moduleId: "physics-newton-module",
        title: "Newton Quiz",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 20,
        createdBy: "teacher-1",
        updatedBy: "teacher-1",
        createdAt: new Date("2026-03-01T00:00:00.000Z"),
        updatedAt: new Date("2026-03-01T00:00:00.000Z"),
        objectives: [],
        items: [],
      },
    } as never);

    const result = await service.submitAttempt({
      tenantId: TENANT_ID,
      attemptId: "attempt-1" as never,
      userId: USER_ID,
      responses: {
        "item-1": {
          type: "multiple_choice",
          selectedChoiceIds: ["choice-a"],
        },
      } as never,
    });

    expect(result.status).toBe("GRADED");
    expect(result.scorePercent).toBe(0);
    expect(result.feedback).toHaveLength(1);
    expect(prisma.learnerMastery.upsert).toHaveBeenCalled();
    expect(prisma.knowledgeGap.create).toHaveBeenCalled();
  });

  it("enforces tenant and user scoped lookup when submitting attempts", async () => {
    vi.mocked(prisma.assessmentAttempt.findFirst).mockResolvedValue(null);

    await expect(
      service.submitAttempt({
        tenantId: TENANT_ID,
        attemptId: "attempt-2" as never,
        userId: USER_ID,
        responses: {} as never,
      }),
    ).rejects.toMatchObject({
      statusCode: 404,
    });

    expect(prisma.assessmentAttempt.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          id: "attempt-2",
          tenantId: "tenant-1",
          userId: "user-1",
        }),
      }),
    );
  });
});
