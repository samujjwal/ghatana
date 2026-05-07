import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";

type PrismaModel<T> = {
  findFirst?: (args: unknown) => Promise<T | null>;
  findMany?: (args: unknown) => Promise<T[]>;
};

export type EvidenceGradePrisma = {
  assessmentAttempt?: PrismaModel<AssessmentAttemptEvidence>;
  learnerMastery?: PrismaModel<LearnerMasteryEvidence>;
};

type AssessmentAttemptEvidence = {
  id: string;
  tenantId?: string;
  userId?: string;
  status?: string;
  scorePercent?: number | null;
  gradedAt?: Date | string | null;
  submittedAt?: Date | string | null;
  assessment?: {
    moduleId?: string | null;
  } | null;
};

type LearnerMasteryEvidence = {
  conceptId?: string | null;
  masteryProbability?: number | null;
  evidenceCount?: number | null;
};

export type EvidenceBackedLtiGradeInput = {
  tenantId: TenantId;
  userId: UserId;
  assessmentAttemptId?: string;
  moduleId?: string;
  timestamp?: string;
};

export type EvidenceBackedLtiGrade = {
  userId: string;
  scoreGiven: number;
  scoreMaximum: number;
  activityProgress: "Completed" | "Initialized" | "Started" | "InProgress" | "Submitted";
  gradingProgress: "FullyGraded" | "Pending" | "PendingManual" | "Failed" | "NotReady";
  timestamp: string;
  comment: string;
  evidence: {
    source: "assessment_attempt" | "claim_mastery";
    assessmentAttemptId?: string;
    moduleId?: string;
    claimCount?: number;
    attemptStatus?: string;
  };
};

export async function calculateEvidenceBackedLtiGrade(
  prisma: EvidenceGradePrisma,
  input: EvidenceBackedLtiGradeInput,
): Promise<EvidenceBackedLtiGrade> {
  if (input.assessmentAttemptId) {
    const assessmentAttempt = await prisma.assessmentAttempt?.findFirst?.({
      where: {
        id: input.assessmentAttemptId,
        tenantId: input.tenantId,
        userId: input.userId,
      },
      select: {
        id: true,
        tenantId: true,
        userId: true,
        status: true,
        scorePercent: true,
        gradedAt: true,
        submittedAt: true,
        assessment: {
          select: {
            moduleId: true,
          },
        },
      },
    });

    if (!assessmentAttempt) {
      throw evidenceGradeError(
        404,
        "LTI_EVIDENCE_NOT_FOUND",
        "Assessment evidence was not found for this learner and tenant",
      );
    }

    if (typeof assessmentAttempt.scorePercent !== "number") {
      throw evidenceGradeError(
        422,
        "LTI_EVIDENCE_NOT_READY",
        "Assessment evidence is not graded and cannot be passed back",
      );
    }

    return {
      userId: input.userId,
      scoreGiven: clampPercent(assessmentAttempt.scorePercent),
      scoreMaximum: 100,
      activityProgress: "Completed",
      gradingProgress: "FullyGraded",
      timestamp: input.timestamp ?? dateToIso(assessmentAttempt.gradedAt ?? assessmentAttempt.submittedAt),
      comment: `TutorPutor evidence-backed grade from assessment attempt ${assessmentAttempt.id}`,
      evidence: {
        source: "assessment_attempt",
        assessmentAttemptId: assessmentAttempt.id,
        ...(assessmentAttempt.assessment?.moduleId ?? input.moduleId
          ? { moduleId: assessmentAttempt.assessment?.moduleId ?? input.moduleId }
          : {}),
        ...(assessmentAttempt.status ? { attemptStatus: assessmentAttempt.status } : {}),
      },
    };
  }

  if (input.moduleId) {
    const latestAssessmentAttempt = await prisma.assessmentAttempt?.findFirst?.({
      where: {
        tenantId: input.tenantId,
        userId: input.userId,
        scorePercent: { not: null },
        assessment: {
          moduleId: input.moduleId,
        },
      },
      orderBy: [{ gradedAt: "desc" }, { submittedAt: "desc" }],
      select: {
        id: true,
        tenantId: true,
        userId: true,
        status: true,
        scorePercent: true,
        gradedAt: true,
        submittedAt: true,
        assessment: {
          select: {
            moduleId: true,
          },
        },
      },
    });

    if (latestAssessmentAttempt && typeof latestAssessmentAttempt.scorePercent === "number") {
      return {
        userId: input.userId,
        scoreGiven: clampPercent(latestAssessmentAttempt.scorePercent),
        scoreMaximum: 100,
        activityProgress: "Completed",
        gradingProgress: "FullyGraded",
        timestamp: input.timestamp ?? dateToIso(
          latestAssessmentAttempt.gradedAt ?? latestAssessmentAttempt.submittedAt,
        ),
        comment: `TutorPutor evidence-backed grade from assessment attempt ${latestAssessmentAttempt.id}`,
        evidence: {
          source: "assessment_attempt",
          assessmentAttemptId: latestAssessmentAttempt.id,
          moduleId: input.moduleId,
          ...(latestAssessmentAttempt.status
            ? { attemptStatus: latestAssessmentAttempt.status }
            : {}),
        },
      };
    }

    const masteryRows = await prisma.learnerMastery?.findMany?.({
      where: {
        tenantId: input.tenantId,
        userId: input.userId,
        concept: {
          moduleId: input.moduleId,
        },
      },
      select: {
        conceptId: true,
        masteryProbability: true,
        evidenceCount: true,
      },
    }) ?? [];

    const scoredRows = masteryRows.filter(
      (row) => typeof row.masteryProbability === "number" && (row.evidenceCount ?? 0) > 0,
    );

    if (scoredRows.length === 0) {
      throw evidenceGradeError(
        422,
        "LTI_EVIDENCE_NOT_READY",
        "No claim mastery evidence is available for this module",
      );
    }

    const masteryAverage =
      scoredRows.reduce((sum, row) => sum + (row.masteryProbability ?? 0), 0) /
      scoredRows.length;

    return {
      userId: input.userId,
      scoreGiven: clampPercent(masteryAverage * 100),
      scoreMaximum: 100,
      activityProgress: "Completed",
      gradingProgress: "FullyGraded",
      timestamp: input.timestamp ?? new Date().toISOString(),
      comment: `TutorPutor evidence-backed grade from ${scoredRows.length} mastered claim(s)`,
      evidence: {
        source: "claim_mastery",
        moduleId: input.moduleId,
        claimCount: scoredRows.length,
      },
    };
  }

  throw evidenceGradeError(
    400,
    "LTI_EVIDENCE_REQUIRED",
    "Provide an assessmentAttemptId or moduleId for evidence-backed grade passback",
  );
}

function clampPercent(score: number): number {
  return Number(Math.min(100, Math.max(0, score)).toFixed(2));
}

function dateToIso(value: Date | string | null | undefined): string {
  if (value instanceof Date) {
    return value.toISOString();
  }

  if (typeof value === "string" && value.length > 0) {
    return value;
  }

  return new Date().toISOString();
}

function evidenceGradeError(statusCode: number, code: string, message: string): Error & {
  statusCode: number;
  code: string;
} {
  const error = new Error(message) as Error & {
    statusCode: number;
    code: string;
  };
  error.statusCode = statusCode;
  error.code = code;
  return error;
}
