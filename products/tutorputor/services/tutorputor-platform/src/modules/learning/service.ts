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
import {
  ClaimMasteryCalculator,
  type EvidenceRecord,
} from "@tutorputor/core/kernel/engine/analytics/ClaimMastery";
import { NotFoundError, ValidationError } from "../../core/errors";

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
    constraints?: ProgressUpdateConstraints;
  }) => Promise<Enrollment>;
  checkHealth: () => Promise<boolean>;
};

export interface ProgressUpdateConstraints {
  preferredPacing: "SELF_PACED" | "GUIDED" | "ADAPTIVE" | "INTENSIVE";
  preferredSessionMinutes: number;
  adjustedDifficulty: "beginner" | "easy" | "medium" | "hard" | "expert";
}

type ModuleTag = {
  label: string;
  [key: string]: unknown;
};

type EnrollmentRecord = {
  id: string;
  userId: string;
  moduleId: string;
  module?: {
    slug: string;
    title: string;
  } | null;
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

type LearningEventRecord = {
  eventType: string;
  payload: unknown;
  timestamp: Date | string | null;
};

type JsonRecord = Record<string, unknown>;

interface ActionableLearnerState {
  currentClaimMastery: Array<{
    claimId: string;
    masteryScore: number;
    evidenceCount: number;
    status: "not_started" | "developing" | "proficient" | "mastered";
    lastEvidenceAt?: string | undefined;
  }>;
  nextBestLesson: {
    moduleId: ModuleId;
    moduleSlug?: string | undefined;
    moduleTitle: string;
    reason: string;
    targetClaimId?: string | undefined;
  } | null;
  unresolvedMisconceptions: Array<{
    claimId?: string | undefined;
    description: string;
    sourceEventType: string;
    observedAt?: string | undefined;
  }>;
  overdueSpacedRepetitionItems: Array<{
    claimId: string;
    dueAt: string;
    reason: string;
  }>;
  simulationAttemptsNeedingReview: Array<{
    runId?: string | undefined;
    captureId?: string | undefined;
    claimId?: string | undefined;
    reason: string;
    capturedAt?: string | undefined;
  }>;
  recommendedRemediation: Array<{
    claimId?: string | undefined;
    moduleId?: ModuleId | undefined;
    title: string;
    reason: string;
  }>;
  offlineResumeState: {
    pendingItems: number;
    lastSyncedAt?: string | undefined;
    resumedAt?: string | undefined;
  } | null;
}

const claimMasteryCalculator = new ClaimMasteryCalculator();

// =============================================================================
// Implementation
// =============================================================================

export function createLearningService(
  prisma: TutorPrismaClient,
): HealthAwareLearningService {
  return {
    async getDashboard(tenantId, userId) {
      const [user, enrollments, modules, learningEvents] = await prisma.$transaction([
        prisma.user.findFirst({
          where: { tenantId, id: userId },
          select: { id: true, email: true, displayName: true, role: true },
        }),
        prisma.enrollment.findMany({
          where: { tenantId, userId },
          orderBy: { updatedAt: "desc" },
          include: {
            module: {
              select: {
                slug: true,
                title: true,
              },
            },
          },
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
        prisma.learningEvent.findMany({
          where: {
            tenantId,
            userId,
            eventType: {
              in: [
                "assess.answer",
                "sim.capture",
                "sim.control.change",
                "assist.hint",
                "offline.sync.completed",
              ],
            },
          },
          orderBy: { timestamp: "desc" },
          take: 100,
        }),
      ]);

      if (!user) {
        throw new NotFoundError("User", userId);
      }

      const currentEnrollments = enrollments.map(mapEnrollment);
      const recommendedModules = modules.map((module) => mapModuleSummary(module));
      const actionableState = buildActionableLearnerState({
        currentEnrollments,
        recommendedModules,
        learningEvents: learningEvents as LearningEventRecord[],
      });

      return {
        user: {
          id: user.id as UserId,
          email: user.email,
          displayName: user.displayName,
          role: user.role as DashboardSummary["user"]["role"],
        },
        currentEnrollments,
        recommendedModules,
        ...actionableState,
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
      constraints,
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
      validateProgressUpdateConstraints({
        currentProgress: enrollment.progressPercent,
        requestedProgress: nextProgress,
        timeSpentSecondsDelta,
        ...(constraints ? { constraints } : {}),
      });
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

function validateProgressUpdateConstraints(args: {
  currentProgress: number;
  requestedProgress: number;
  timeSpentSecondsDelta: number;
  constraints?: ProgressUpdateConstraints;
}) {
  const { currentProgress, requestedProgress, timeSpentSecondsDelta, constraints } = args;
  if (!constraints) {
    return;
  }

  const deltaProgress = Math.max(0, requestedProgress - currentProgress);
  if (deltaProgress === 0) {
    return;
  }

  if (timeSpentSecondsDelta <= 0) {
    throw new ValidationError(
      "Progress updates that advance completion must include time spent.",
    );
  }

  const sessionSecondsPerProgressPoint =
    Math.max(constraints.preferredSessionMinutes, 1) * 0.6;
  const pacingMultiplier = getPacingMultiplier(constraints.preferredPacing);
  const difficultyMultiplier = getDifficultyMultiplier(
    constraints.adjustedDifficulty,
  );
  const minimumRequiredSeconds = Math.ceil(
    deltaProgress * sessionSecondsPerProgressPoint * pacingMultiplier * difficultyMultiplier,
  );

  if (timeSpentSecondsDelta < minimumRequiredSeconds) {
    throw new ValidationError(
      `Progress update advances too quickly for the learner pacing profile; expected at least ${minimumRequiredSeconds} seconds for ${deltaProgress} progress points.`,
    );
  }
}

function getPacingMultiplier(
  preferredPacing: ProgressUpdateConstraints["preferredPacing"],
) {
  switch (preferredPacing) {
    case "SELF_PACED":
      return 0.85;
    case "GUIDED":
      return 1.2;
    case "INTENSIVE":
      return 0.65;
    case "ADAPTIVE":
    default:
      return 1;
  }
}

function getDifficultyMultiplier(
  adjustedDifficulty: ProgressUpdateConstraints["adjustedDifficulty"],
) {
  switch (adjustedDifficulty) {
    case "beginner":
      return 1.25;
    case "easy":
      return 1.1;
    case "hard":
      return 0.9;
    case "expert":
      return 0.8;
    case "medium":
    default:
      return 1;
  }
}

function mapEnrollment(record: EnrollmentRecord): Enrollment {
  return {
    id: record.id as Enrollment["id"],
    userId: record.userId as UserId,
    moduleId: record.moduleId as ModuleId,
    ...(record.module?.slug ? { moduleSlug: record.module.slug } : {}),
    ...(record.module?.title ? { moduleTitle: record.module.title } : {}),
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

function buildActionableLearnerState(args: {
  currentEnrollments: Enrollment[];
  recommendedModules: ModuleSummary[];
  learningEvents: LearningEventRecord[];
}): ActionableLearnerState {
  const evidenceRecords = args.learningEvents
    .flatMap(mapTelemetryEventToEvidence)
    .filter((evidence): evidence is EvidenceRecord => evidence !== null);

  const claimIds = [...new Set(evidenceRecords.map((evidence) => evidence.claimId))];
  const currentClaimMastery = claimIds.map((claimId) => {
    const relevantEvidence = evidenceRecords.filter(
      (evidence) => evidence.claimId === claimId,
    );
    const mastery = claimMasteryCalculator.calculateClaimMastery(
      "dashboard-learner",
      "dashboard",
      claimId,
      evidenceRecords,
    );
    const lastEvidenceAt = findLastEvidenceAt(args.learningEvents, claimId);
    return {
      claimId,
      masteryScore: mastery.masteryScore,
      evidenceCount: relevantEvidence.length,
      status: getMasteryStatus(mastery.masteryScore),
      ...(lastEvidenceAt ? { lastEvidenceAt } : {}),
    };
  });

  const unresolvedMisconceptions = args.learningEvents.flatMap((event) =>
    extractMisconceptions(event),
  );
  const simulationAttemptsNeedingReview = args.learningEvents
    .map(extractSimulationReviewItem)
    .filter((item): item is NonNullable<typeof item> => item !== null);
  const overdueSpacedRepetitionItems = currentClaimMastery
    .filter((claim) => claim.masteryScore < 0.8)
    .map((claim) => ({
      claimId: claim.claimId,
      dueAt: new Date().toISOString(),
      reason: "Claim mastery is below the spaced-repetition threshold.",
    }));
  const recommendedRemediation = [
    ...unresolvedMisconceptions.map((misconception) => ({
      claimId: misconception.claimId,
      title: "Resolve misconception",
      reason: misconception.description,
    })),
    ...currentClaimMastery
      .filter((claim) => claim.masteryScore < 0.6)
      .map((claim) => ({
        claimId: claim.claimId,
        title: "Practice targeted evidence task",
        reason: "Current claim mastery needs remediation.",
      })),
  ];

  const weakestClaim = [...currentClaimMastery].sort(
    (a, b) => a.masteryScore - b.masteryScore,
  )[0];
  const activeEnrollment = args.currentEnrollments.find(
    (enrollment) => enrollment.status !== "COMPLETED",
  );
  const recommendedModule = args.recommendedModules[0];
  const nextBestLesson =
    activeEnrollment || recommendedModule
      ? {
          moduleId: (activeEnrollment?.moduleId ?? recommendedModule!.id) as ModuleId,
          ...(activeEnrollment?.moduleSlug || recommendedModule?.slug
            ? { moduleSlug: activeEnrollment?.moduleSlug ?? recommendedModule?.slug }
            : {}),
          moduleTitle:
            activeEnrollment?.moduleTitle ??
            recommendedModule?.title ??
            "Recommended lesson",
          reason: weakestClaim
            ? `Strengthen ${weakestClaim.claimId} with the next evidence task.`
            : "Continue the next recommended lesson.",
          ...(weakestClaim ? { targetClaimId: weakestClaim.claimId } : {}),
        }
      : null;

  return {
    currentClaimMastery,
    nextBestLesson,
    unresolvedMisconceptions,
    overdueSpacedRepetitionItems,
    simulationAttemptsNeedingReview,
    recommendedRemediation,
    offlineResumeState: extractOfflineResumeState(args.learningEvents),
  };
}

function mapTelemetryEventToEvidence(event: LearningEventRecord): EvidenceRecord | null {
  const payload = asRecord(event.payload);
  const object = asRecord(payload.object);
  const result = asRecord(payload.result);
  const claimId = getString(object.claimId) ?? getString(result.claimId);
  if (!claimId) return null;

  if (event.eventType === "assess.answer") {
    const correct = getBoolean(result.correct);
    const confidence = getConfidence(result.confidence);
    if (correct === undefined || !confidence) return null;
    return {
      evidenceId: getString(object.evidenceId) ?? `${event.eventType}:${claimId}`,
      claimId,
      type: "prediction_vs_outcome",
      correct,
      confidence,
    };
  }

  if (event.eventType === "sim.capture") {
    const processFeatures = asRecord(result.processFeatures);
    const processScore = getNumber(processFeatures.processScore);
    if (processScore !== undefined) {
      return {
        evidenceId: getString(object.evidenceId) ?? `${event.eventType}:${claimId}`,
        claimId,
        type: "construction_artifact",
        rubricScore: processScore,
        maxRubricScore: 1,
      };
    }
    return {
      evidenceId: getString(object.evidenceId) ?? `${event.eventType}:${claimId}`,
      claimId,
      type: "parameter_targeting",
      goalAchieved: getBoolean(result.validEvidence) ?? true,
      attempts: getNumber(processFeatures.attempts) ?? 1,
      rmse: getNumber(processFeatures.rmse) ?? 0,
      tolerance: getNumber(processFeatures.tolerance) ?? 1,
    };
  }

  if (event.eventType === "sim.control.change") {
    const processScore = getNumber(asRecord(result.processFeatures).processScore);
    return {
      evidenceId: `${event.eventType}:${claimId}`,
      claimId,
      type: "construction_artifact",
      rubricScore: processScore ?? 0.5,
      maxRubricScore: 1,
    };
  }

  if (event.eventType === "assist.hint") {
    return {
      evidenceId: getString(object.hintId) ?? `${event.eventType}:${claimId}`,
      claimId,
      type: "construction_artifact",
      rubricScore: 0.4,
      maxRubricScore: 1,
    };
  }

  return null;
}

function extractMisconceptions(event: LearningEventRecord) {
  const payload = asRecord(event.payload);
  const object = asRecord(payload.object);
  const result = asRecord(payload.result);
  const rawMisconceptions = Array.isArray(result.misconceptions)
    ? result.misconceptions
    : [];
  return rawMisconceptions
    .map((item) => getString(item))
    .filter((item): item is string => Boolean(item))
    .map((description) => ({
      ...(getString(object.claimId) ? { claimId: getString(object.claimId) } : {}),
      description,
      sourceEventType: event.eventType,
      ...(event.timestamp ? { observedAt: new Date(event.timestamp).toISOString() } : {}),
    }));
}

function extractSimulationReviewItem(event: LearningEventRecord) {
  if (event.eventType !== "sim.capture") return null;
  const payload = asRecord(event.payload);
  const object = asRecord(payload.object);
  const result = asRecord(payload.result);
  const processScore = getNumber(asRecord(result.processFeatures).processScore);
  const validEvidence = getBoolean(result.validEvidence);
  if (validEvidence !== false && (processScore === undefined || processScore >= 0.6)) {
    return null;
  }
  return {
    ...(getString(object.runId) ? { runId: getString(object.runId) } : {}),
    ...(getString(object.captureId) ? { captureId: getString(object.captureId) } : {}),
    ...(getString(object.claimId) ? { claimId: getString(object.claimId) } : {}),
    reason:
      validEvidence === false
        ? "Simulation capture did not produce valid evidence."
        : "Simulation process score needs instructor review.",
    ...(event.timestamp ? { capturedAt: new Date(event.timestamp).toISOString() } : {}),
  };
}

function extractOfflineResumeState(events: LearningEventRecord[]) {
  const offlineEvents = events.filter(
    (event) =>
      event.eventType === "offline.sync.completed" ||
      asRecord(event.payload).offline === true,
  );
  if (offlineEvents.length === 0) return null;
  const latest = offlineEvents[0];
  if (!latest) return null;
  const payload = asRecord(latest.payload);
  return {
    pendingItems: getNumber(payload.pendingItems) ?? 0,
    ...(getString(payload.lastSyncedAt) ? { lastSyncedAt: getString(payload.lastSyncedAt) } : {}),
    ...(latest.timestamp ? { resumedAt: new Date(latest.timestamp).toISOString() } : {}),
  };
}

function findLastEvidenceAt(events: LearningEventRecord[], claimId: string) {
  const event = events.find((item) => {
    const payload = asRecord(item.payload);
    const object = asRecord(payload.object);
    const result = asRecord(payload.result);
    return getString(object.claimId) === claimId || getString(result.claimId) === claimId;
  });
  return event?.timestamp ? new Date(event.timestamp).toISOString() : undefined;
}

function getMasteryStatus(score: number) {
  if (score >= 0.9) return "mastered" as const;
  if (score >= 0.75) return "proficient" as const;
  if (score > 0) return "developing" as const;
  return "not_started" as const;
}

function asRecord(value: unknown): JsonRecord {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as JsonRecord)
    : {};
}

function getString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

function getBoolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

function getNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function getConfidence(value: unknown) {
  return value === "high" || value === "medium" || value === "low"
    ? value
    : undefined;
}
