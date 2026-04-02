import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  PrismaClient: class {},
}));

import { createAssessmentService } from "./assessment-service.js";
import { aiClient } from "../../clients/ai-client.js";
import type { TutorPrismaClient } from "@tutorputor/core/db";

function makePrisma(
  overrides: Partial<TutorPrismaClient> = {},
): TutorPrismaClient {
  return {
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
    learnerProfile: {
      findUnique: vi.fn(),
      create: vi.fn(),
    },
    learnerMastery: {
      findMany: vi.fn(),
    },
    knowledgeGap: {
      findMany: vi.fn(),
    },
    $transaction: vi.fn(),
    $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
    ...overrides,
  } as unknown as TutorPrismaClient;
}

describe("createAssessmentService", () => {
  let prisma: TutorPrismaClient;
  let service: ReturnType<typeof createAssessmentService>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createAssessmentService(prisma);
  });

  it("uses cursor pagination and returns the next cursor from the trimmed page", async () => {
    vi.mocked(prisma.assessment.findMany).mockResolvedValue([
      {
        id: "assessment-3",
        moduleId: "module-1",
        title: "Assessment 3",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 30,
      },
      {
        id: "assessment-2",
        moduleId: "module-1",
        title: "Assessment 2",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 30,
      },
      {
        id: "assessment-1",
        moduleId: "module-1",
        title: "Assessment 1",
        type: "QUIZ",
        status: "PUBLISHED",
        version: 1,
        passingScore: 70,
        attemptsAllowed: 2,
        timeLimitMinutes: 30,
      },
    ] as never);

    const result = await service.listAssessments({
      tenantId: "tenant-1" as never,
      moduleId: "module-1" as never,
      limit: 2,
      cursor: "assessment-4" as never,
    });

    expect(prisma.assessment.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          tenantId: "tenant-1",
          moduleId: "module-1",
        },
        take: 3,
        cursor: { id: "assessment-4" },
        skip: 1,
        orderBy: { createdAt: "desc" },
      }),
    );
    expect(result.items).toHaveLength(2);
    expect(result.nextCursor).toBe("assessment-2");
  });

  it("scopes assessment fetches to the tenant before mapping the response", async () => {
    vi.mocked(prisma.assessment.findFirst).mockResolvedValue({
      id: "assessment-1",
      tenantId: "tenant-1",
      moduleId: "module-1",
      title: "Kinematics Quiz",
      type: "QUIZ",
      status: "PUBLISHED",
      version: 2,
      passingScore: 80,
      attemptsAllowed: 3,
      timeLimitMinutes: 20,
      createdBy: "user-1",
      updatedBy: "user-1",
      createdAt: new Date("2026-03-01T00:00:00.000Z"),
      updatedAt: new Date("2026-03-02T00:00:00.000Z"),
      objectives: [
        {
          id: 1,
          label: "Explain acceleration",
          taxonomyLevel: "understand",
        },
      ],
      items: [],
    } as never);

    const result = await service.getAssessment({
      tenantId: "tenant-1" as never,
      assessmentId: "assessment-1" as never,
      userId: "user-1" as never,
    });

    expect(prisma.assessment.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          id: "assessment-1",
          tenantId: "tenant-1",
        },
      }),
    );
    expect(result.id).toBe("assessment-1");
    expect(result.objectives[0]?.label).toBe("Explain acceleration");
  });

  it("raises not found when the tenant-scoped assessment lookup misses", async () => {
    vi.mocked(prisma.assessment.findFirst).mockResolvedValue(null);

    await expect(
      service.getAssessment({
        tenantId: "tenant-1" as never,
        assessmentId: "missing" as never,
        userId: "user-1" as never,
      }),
    ).rejects.toMatchObject({
      statusCode: 404,
    });
  });

  it("falls back to deterministic assessment generation when the AI client fails", async () => {
    vi.mocked(prisma.module.findFirst).mockResolvedValue({
      id: "module-1",
      tenantId: "tenant-1",
      slug: "intro-physics",
      title: "Intro Physics",
      domain: "SCIENCE",
      learningObjectives: [
        {
          id: "objective-1",
          label: "Explain net force",
          taxonomyLevel: "understand",
        },
      ],
    } as never);
    vi.mocked(prisma.learnerProfile.findUnique).mockResolvedValue({
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
    } as never);
    vi.mocked(prisma.$transaction).mockResolvedValue([[], []] as never);
    vi.spyOn(aiClient, "generateAssessmentItems").mockRejectedValueOnce(
      new Error("grpc unavailable"),
    );

    const result = await service.generateAssessmentItems({
      tenantId: "tenant-1" as never,
      userId: "user-1" as never,
      moduleId: "module-1" as never,
      count: 2,
      difficulty: "INTERMEDIATE" as never,
      objectiveIds: [],
    });

    expect(result.model).toBe("tutorputor-assessment-v1");
    expect(result.items).toHaveLength(2);
    expect(result.warnings).toContain(
      "AI service unavailable, using backup generator.",
    );
    expect(prisma.assessmentDraft.create).toHaveBeenCalledTimes(1);
  });

  it("adapts intermediate generation requests down to INTRO for low mastery learners", async () => {
    vi.mocked(prisma.module.findFirst).mockResolvedValue({
      id: "module-1",
      tenantId: "tenant-1",
      slug: "intro-physics",
      title: "Intro Physics",
      domain: "SCIENCE",
      learningObjectives: [
        {
          id: "objective-1",
          label: "Explain net force",
          taxonomyLevel: "understand",
        },
      ],
    } as never);
    vi.mocked(prisma.learnerProfile.findUnique).mockResolvedValue({
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
    } as never);
    vi.mocked(prisma.$transaction).mockResolvedValue([
      [{ conceptId: "force", masteryProbability: 0.1 }],
      [],
    ] as never);
    vi.spyOn(aiClient, "generateAssessmentItems").mockResolvedValueOnce({
      items: [
        {
          type: "multiple_choice_single",
          prompt: "What is force?",
          points: 10,
          choices: [
            { id: "a", label: "A push", is_correct: true },
            { id: "b", label: "A color", is_correct: false },
          ],
        },
      ],
      model: "test-model",
    } as never);

    await service.generateAssessmentItems({
      tenantId: "tenant-1" as never,
      userId: "user-1" as never,
      moduleId: "module-1" as never,
      count: 1,
      difficulty: "INTERMEDIATE" as never,
      objectiveIds: [],
    });

    expect(aiClient.generateAssessmentItems).toHaveBeenCalledWith(
      expect.objectContaining({
        difficulty: "INTRO",
      }),
    );
  });

  it("preserves explicit ADVANCED difficulty requests even for low mastery learners", async () => {
    vi.mocked(prisma.module.findFirst).mockResolvedValue({
      id: "module-1",
      tenantId: "tenant-1",
      slug: "intro-physics",
      title: "Intro Physics",
      domain: "SCIENCE",
      learningObjectives: [
        {
          id: "objective-1",
          label: "Explain net force",
          taxonomyLevel: "understand",
        },
      ],
    } as never);
    vi.mocked(prisma.learnerProfile.findUnique).mockResolvedValue({
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
    } as never);
    vi.mocked(prisma.$transaction).mockResolvedValue([
      [{ conceptId: "force", masteryProbability: 0.1 }],
      [],
    ] as never);
    vi.spyOn(aiClient, "generateAssessmentItems").mockResolvedValueOnce({
      items: [
        {
          type: "multiple_choice_single",
          prompt: "What is inertia?",
          points: 10,
          choices: [
            { id: "a", label: "Resistance to change", is_correct: true },
            { id: "b", label: "Heat energy", is_correct: false },
          ],
        },
      ],
      model: "test-model",
    } as never);

    await service.generateAssessmentItems({
      tenantId: "tenant-1" as never,
      userId: "user-1" as never,
      moduleId: "module-1" as never,
      count: 1,
      difficulty: "ADVANCED" as never,
      objectiveIds: [],
    });

    expect(aiClient.generateAssessmentItems).toHaveBeenCalledWith(
      expect.objectContaining({
        difficulty: "ADVANCED",
      }),
    );
  });
});
