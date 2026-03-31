/**
 * Learning Service (Migrated)
 *
 * Core learning business logic: Dashboard, Enrollments, Progress Tracking.
 *
 * Migrated from tutorputor-learning/src/service.ts
 *
 * @doc.type service
 * @doc.purpose Core learning progression logic
 * @doc.layer product
 * @doc.pattern Service
 */

import type { LearningService } from "@tutorputor/contracts/v1/services";
import type {
  DashboardSummary,
  Enrollment,
  ModuleSummary,
  ModuleId,
  EnrollmentId,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { NotFoundError } from "../../core/errors";

// =============================================================================
// Types
// =============================================================================

export type HealthAwareLearningService = Omit<
  LearningService,
  "updateProgress"
> & {
  updateProgress: (args: {
    tenantId: TenantId;
    userId: UserId;
    enrollmentId: EnrollmentId;
    progressPercent: number;
    timeSpentSecondsDelta: number;
  }) => Promise<Enrollment>;
  checkHealth: () => Promise<boolean>;
};

type ModuleTag = {
  label: string;
  [key: string]: unknown;
};

type EnrollmentRecord = {
  id: string;
  userId: string;
  moduleId: string;
  status: Enrollment["status"];
  progressPercent: number;
  startedAt: Date | null;
  completedAt: Date | null;
  timeSpentSeconds: number;
};

type ModuleWithSummaries = {
  id: string;
  slug: string;
  title: string;
  domain: string;
  difficulty: string;
  estimatedTimeMinutes: number;
  status: string;
  tenantId: string;
  tags: ModuleTag[];
  enrollments: Array<{ progressPercent: number }>;
};

// =============================================================================
// Implementation
// =============================================================================

export function createLearningService(
  prisma: TutorPrismaClient,
): HealthAwareLearningService {
  return {
    async getDashboard(tenantId, userId) {
      const [user, enrollments, modules] = await prisma.$transaction([
        prisma.user.findFirst({
          where: { tenantId, id: userId },
          select: { id: true, email: true, displayName: true, role: true },
        }),
        prisma.enrollment.findMany({
          where: { tenantId, userId },
          orderBy: { updatedAt: "desc" },
        }),
        prisma.module.findMany({
          where: { tenantId, status: "PUBLISHED" },
          orderBy: { title: "asc" },
          take: 8,
          include: {
            tags: true,
            enrollments: {
              where: { tenantId, userId },
            },
          },
        }),
      ]);

      if (!user) {
        throw new NotFoundError("User", userId);
      }

      return {
        user: {
          id: user.id as UserId,
          email: user.email,
          displayName: user.displayName,
          role: user.role as any,
        },
        currentEnrollments: enrollments.map(mapEnrollment),
        recommendedModules: modules.map((module) => mapModuleSummary(module)),
      };
    },

    async enrollInModule(tenantId, userId, moduleId) {
      await assertModuleExists(prisma, tenantId, moduleId);
      const now = new Date();
      const enrollment = await prisma.enrollment.upsert({
        where: {
          tenantId_userId_moduleId: {
            tenantId,
            userId,
            moduleId,
          },
        },
        update: {
          status: "IN_PROGRESS",
        },
        create: {
          tenantId,
          userId,
          moduleId,
          status: "IN_PROGRESS",
          progressPercent: 0,
          startedAt: now,
          timeSpentSeconds: 0,
        },
      });

      return mapEnrollment(enrollment);
    },

    async updateProgress({
      tenantId,
      userId,
      enrollmentId,
      progressPercent,
      timeSpentSecondsDelta,
    }) {
      const enrollment = await prisma.enrollment.findFirst({
        where: { id: enrollmentId, tenantId, userId },
      });

      if (!enrollment) {
        throw new NotFoundError("Enrollment", enrollmentId);
      }

      const nextProgress = clampProgress(
        enrollment.progressPercent,
        progressPercent,
      );
      const totalTime = Math.max(
        0,
        enrollment.timeSpentSeconds + timeSpentSecondsDelta,
      );
      const isComplete = nextProgress >= 100;
      const completedAt =
        isComplete && !enrollment.completedAt
          ? new Date()
          : enrollment.completedAt;

      const updated = await prisma.enrollment.update({
        where: { id: enrollmentId },
        data: {
          progressPercent: nextProgress,
          status: isComplete ? "COMPLETED" : "IN_PROGRESS",
          timeSpentSeconds: totalTime,
          startedAt: enrollment.startedAt ?? new Date(),
          completedAt,
        },
      });

      return mapEnrollment(updated);
    },

    async checkHealth() {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    },
  };
}

// =============================================================================
// Helpers
// =============================================================================

async function assertModuleExists(
  prisma: TutorPrismaClient,
  tenantId: TenantId,
  moduleId: ModuleId,
) {
  const module = await prisma.module.findFirst({
    where: { tenantId, id: moduleId },
  });
  if (!module) {
    throw new NotFoundError("Module", moduleId);
  }
}

export function clampProgress(current: number, requested: number) {
  return Math.max(current, Math.min(requested, 100));
}

function mapEnrollment(record: EnrollmentRecord): Enrollment {
  return {
    id: record.id as Enrollment["id"],
    userId: record.userId as UserId,
    moduleId: record.moduleId as ModuleId,
    status: record.status,
    progressPercent: record.progressPercent,
    ...(record.startedAt ? { startedAt: record.startedAt.toISOString() } : {}),
    ...(record.completedAt ? { completedAt: record.completedAt.toISOString() } : {}),
    timeSpentSeconds: record.timeSpentSeconds,
  };
}

function mapModuleSummary(module: ModuleWithSummaries): ModuleSummary {
  const enrollment = module.enrollments?.[0];
  return {
    id: module.id as ModuleSummary["id"],
    slug: module.slug,
    title: module.title,
    domain: module.domain as ModuleSummary["domain"],
    difficulty: module.difficulty as ModuleSummary["difficulty"],
    estimatedTimeMinutes: module.estimatedTimeMinutes,
    tags: module.tags.map((tag) => tag.label),
    status: "PUBLISHED",
    ...(typeof enrollment?.progressPercent === "number"
      ? { progressPercent: enrollment.progressPercent }
      : {}),
  };
}
