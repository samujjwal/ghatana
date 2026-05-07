/**
 * @doc.type test
 * @doc.purpose Unit tests for the learning service — dashboard, enrollments, progress tracking
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import { createLearningService, clampProgress } from "../service";
import { NotFoundError, ValidationError } from "../../../core/errors";
import type { TutorPrismaClient } from "@tutorputor/core/db";

// ---------------------------------------------------------------------------
// Prisma mock factory
// ---------------------------------------------------------------------------
function makePrisma(
  overrides: Partial<TutorPrismaClient> = {},
): TutorPrismaClient {
  return {
    user: { findFirst: vi.fn() },
    enrollment: {
      findMany: vi.fn(),
      findFirst: vi.fn(),
      upsert: vi.fn(),
      update: vi.fn(),
    },
    module: { findFirst: vi.fn(), findMany: vi.fn() },
    learningEvent: { findMany: vi.fn() },
    $transaction: vi.fn(),
    $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
    ...overrides,
  } as unknown as TutorPrismaClient;
}

const TENANT = "tenant-1" as any;
const USER = "user-1" as any;

function makeUser() {
  return {
    id: USER,
    email: "student@test.com",
    displayName: "Student One",
    role: "STUDENT",
  };
}

function makeEnrollment(partial: Record<string, unknown> = {}) {
  return {
    id: "enr-1",
    userId: USER,
    moduleId: "mod-1",
    status: "IN_PROGRESS",
    progressPercent: 50,
    startedAt: new Date(),
    completedAt: null,
    timeSpentSeconds: 300,
    ...partial,
  };
}

describe("createLearningService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: ReturnType<typeof createLearningService>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createLearningService(prisma);
  });

  // -------------------------------------------------------------------------
  // getDashboard
  // -------------------------------------------------------------------------
  describe("getDashboard", () => {
    it("throws NotFoundError when user does not exist", async () => {
      vi.mocked(prisma.$transaction).mockResolvedValue([null, [], []]);

      await expect(service.getDashboard(TENANT, USER)).rejects.toThrow(
        NotFoundError,
      );
    });

    it("throws NotFoundError with USER_ID code=NOT_FOUND", async () => {
      vi.mocked(prisma.$transaction).mockResolvedValue([null, [], []]);

      let caught: NotFoundError | undefined;
      try {
        await service.getDashboard(TENANT, USER);
      } catch (err) {
        caught = err as NotFoundError;
      }
      expect(caught).toBeInstanceOf(NotFoundError);
      expect(caught?.code).toBe("NOT_FOUND");
      expect(caught?.statusCode).toBe(404);
    });

    it("returns dashboard data when user exists", async () => {
      const user = makeUser();
      const enrollment = makeEnrollment({
        module: { slug: "intro-math", title: "Intro to Math" },
      });
      const module = {
        id: "mod-1",
        slug: "intro-math",
        title: "Intro to Math",
        domain: "MATHEMATICS",
        difficulty: "BEGINNER",
        estimatedTimeMinutes: 60,
        status: "PUBLISHED",
        tenantId: TENANT,
        tags: [],
        enrollments: [{ progressPercent: 50 }],
      };

      vi.mocked(prisma.$transaction).mockResolvedValue([
        user,
        [enrollment],
        [module],
        [],
      ]);

      const dashboard = await service.getDashboard(TENANT, USER);

      expect(dashboard.user.id).toBe(USER);
      expect(dashboard.user.email).toBe("student@test.com");
      expect(dashboard.currentEnrollments).toHaveLength(1);
      expect(dashboard.currentEnrollments[0]?.moduleSlug).toBe("intro-math");
      expect(dashboard.currentEnrollments[0]?.moduleTitle).toBe("Intro to Math");
      expect(dashboard.recommendedModules).toHaveLength(1);
      expect(dashboard.recommendedModules[0]?.slug).toBe("intro-math");
    });

    it("builds actionable mastery state from assessment, simulation, hint, process, and offline telemetry", async () => {
      const user = makeUser();
      const enrollment = makeEnrollment({
        module: { slug: "intro-motion", title: "Motion Evidence" },
      });
      const module = {
        id: "mod-1",
        slug: "intro-motion",
        title: "Motion Evidence",
        domain: "PHYSICS",
        difficulty: "BEGINNER",
        estimatedTimeMinutes: 30,
        status: "PUBLISHED",
        tenantId: TENANT,
        tags: [],
        enrollments: [{ progressPercent: 40 }],
      };
      const telemetry = [
        {
          eventType: "assess.answer",
          timestamp: new Date("2026-05-06T10:00:00.000Z"),
          payload: {
            object: {
              claimId: "claim-motion",
              evidenceId: "evidence-prediction",
            },
            result: {
              correct: false,
              confidence: "high",
              misconceptions: ["Velocity and acceleration are being conflated."],
            },
          },
        },
        {
          eventType: "sim.capture",
          timestamp: new Date("2026-05-06T10:05:00.000Z"),
          payload: {
            object: {
              runId: "run-1",
              captureId: "capture-1",
              claimId: "claim-motion",
              evidenceId: "evidence-sim",
            },
            result: {
              validEvidence: false,
              processFeatures: { processScore: 0.35 },
            },
          },
        },
        {
          eventType: "sim.control.change",
          timestamp: new Date("2026-05-06T10:06:00.000Z"),
          payload: {
            object: { claimId: "claim-motion" },
            result: { processFeatures: { processScore: 0.5 } },
          },
        },
        {
          eventType: "assist.hint",
          timestamp: new Date("2026-05-06T10:07:00.000Z"),
          payload: {
            object: { claimId: "claim-motion", hintId: "hint-1" },
            result: { accepted: true },
          },
        },
        {
          eventType: "offline.sync.completed",
          timestamp: new Date("2026-05-06T10:08:00.000Z"),
          payload: {
            offline: true,
            pendingItems: 0,
            lastSyncedAt: "2026-05-06T10:08:00.000Z",
          },
        },
      ];

      vi.mocked(prisma.$transaction).mockResolvedValue([
        user,
        [enrollment],
        [module],
        telemetry,
      ]);

      const dashboard = await service.getDashboard(TENANT, USER);

      expect(dashboard.currentClaimMastery?.[0]).toEqual(
        expect.objectContaining({
          claimId: "claim-motion",
          evidenceCount: 4,
          status: "developing",
        }),
      );
      expect(dashboard.currentClaimMastery?.[0]?.masteryScore).toBeLessThan(0.6);
      expect(dashboard.nextBestLesson).toEqual(
        expect.objectContaining({
          moduleSlug: "intro-motion",
          targetClaimId: "claim-motion",
        }),
      );
      expect(dashboard.unresolvedMisconceptions).toEqual([
        expect.objectContaining({
          description: "Velocity and acceleration are being conflated.",
          sourceEventType: "assess.answer",
        }),
      ]);
      expect(dashboard.simulationAttemptsNeedingReview).toEqual([
        expect.objectContaining({
          runId: "run-1",
          captureId: "capture-1",
          reason: "Simulation capture did not produce valid evidence.",
        }),
      ]);
      expect(dashboard.overdueSpacedRepetitionItems?.[0]).toEqual(
        expect.objectContaining({ claimId: "claim-motion" }),
      );
      expect(dashboard.recommendedRemediation?.map((item) => item.title)).toEqual(
        expect.arrayContaining([
          "Resolve misconception",
          "Practice targeted evidence task",
        ]),
      );
      expect(dashboard.offlineResumeState).toEqual(
        expect.objectContaining({
          pendingItems: 0,
          lastSyncedAt: "2026-05-06T10:08:00.000Z",
        }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // updateProgress
  // -------------------------------------------------------------------------
  describe("updateProgress", () => {
    it("throws NotFoundError when enrollment does not exist", async () => {
      vi.mocked(prisma.enrollment.findFirst).mockResolvedValue(null);

      await expect(
        service.updateProgress({
          tenantId: TENANT,
          userId: USER,
          enrollmentId: "enr-missing" as never,
          progressPercent: 75,
          timeSpentSecondsDelta: 60,
        }),
      ).rejects.toThrow(NotFoundError);
    });

    it("updates progress and returns mapped enrollment", async () => {
      const existing = makeEnrollment();
      const updated = {
        ...existing,
        progressPercent: 75,
        timeSpentSeconds: 360,
      };

      vi.mocked(prisma.enrollment.findFirst).mockResolvedValue(
        existing as never,
      );
      vi.mocked(prisma.enrollment.update).mockResolvedValue(updated as never);

      const result = await service.updateProgress({
        tenantId: TENANT,
        userId: USER,
        enrollmentId: "enr-1" as never,
        progressPercent: 75,
        timeSpentSecondsDelta: 60,
      });

      expect(result.progressPercent).toBe(75);
      expect(result.timeSpentSeconds).toBe(360);
      expect(result.status).toBe("IN_PROGRESS");
    });

    it("marks enrollment as COMPLETED when progress reaches 100", async () => {
      const existing = makeEnrollment({ progressPercent: 90 });
      const completed = {
        ...existing,
        progressPercent: 100,
        status: "COMPLETED",
        completedAt: new Date(),
      };

      vi.mocked(prisma.enrollment.findFirst).mockResolvedValue(
        existing as never,
      );
      vi.mocked(prisma.enrollment.update).mockResolvedValue(completed as never);

      const result = await service.updateProgress({
        tenantId: TENANT,
        userId: USER,
        enrollmentId: "enr-1" as never,
        progressPercent: 100,
        timeSpentSecondsDelta: 0,
      });

      expect(result.status).toBe("COMPLETED");
    });

    it("rejects progress jumps that violate learner pacing constraints", async () => {
      const existing = makeEnrollment({ progressPercent: 20 });

      vi.mocked(prisma.enrollment.findFirst).mockResolvedValue(
        existing as never,
      );

      await expect(
        service.updateProgress({
          tenantId: TENANT,
          userId: USER,
          enrollmentId: "enr-1" as never,
          progressPercent: 80,
          timeSpentSecondsDelta: 120,
          constraints: {
            preferredPacing: "GUIDED",
            preferredSessionMinutes: 30,
            adjustedDifficulty: "beginner",
          },
        }),
      ).rejects.toThrow(ValidationError);

      expect(prisma.enrollment.update).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // enrollInModule
  // -------------------------------------------------------------------------
  describe("enrollInModule", () => {
    it("throws NotFoundError when module does not exist", async () => {
      vi.mocked(prisma.module.findFirst).mockResolvedValue(null);

      await expect(
        service.enrollInModule(TENANT, USER, "mod-missing" as never),
      ).rejects.toThrow(NotFoundError);
    });

    it("upserts and returns enrollment", async () => {
      vi.mocked(prisma.module.findFirst).mockResolvedValue({
        id: "mod-1",
      } as never);
      const enrollment = makeEnrollment();
      vi.mocked(prisma.enrollment.upsert).mockResolvedValue(
        enrollment as never,
      );

      const result = await service.enrollInModule(
        TENANT,
        USER,
        "mod-1" as never,
      );

      expect(result.moduleId).toBe("mod-1");
      expect(result.status).toBe("IN_PROGRESS");
    });
  });

  // -------------------------------------------------------------------------
  // checkHealth
  // -------------------------------------------------------------------------
  describe("checkHealth", () => {
    it("returns true when DB query succeeds", async () => {
      const result = await service.checkHealth();
      expect(result).toBe(true);
    });
  });
});

// ---------------------------------------------------------------------------
// clampProgress helper (exported)
// ---------------------------------------------------------------------------
describe("clampProgress", () => {
  it("never lowers progress below current value", () => {
    expect(clampProgress(80, 50)).toBe(80);
  });

  it("advances progress when new value is higher", () => {
    expect(clampProgress(50, 80)).toBe(80);
  });

  it("clamps at 100 maximum", () => {
    expect(clampProgress(90, 110)).toBe(100);
  });

  it("returns 0 for a fresh enrollment", () => {
    expect(clampProgress(0, 0)).toBe(0);
  });
});
