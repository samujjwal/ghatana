/**
 * Credential Service
 *
 * Implements storage and retrieval of credentials using Learning Events,
 * and calculation of learning progress from assessment data.
 */

import type { Prisma, PrismaClient } from "@tutorputor/core/db";
import {
  createCredential,
  isCredentialValid,
  type Credential,
  type CredentialFilter,
  type IssueCredentialDTO,
} from "./models/credential";
import type {
  LearningProgress,
  DomainProgress,
} from "./rules/simulation-achievement-rules";

const CREDENTIAL_EVENT_TYPE = "CREDENTIAL_ISSUED";

type LearningEventRecord = {
  payload: unknown;
};

type CredentialEventPayload = Partial<Credential> & { id?: string };
type CredentialEvidenceRequirement = {
  tenantId: string;
  userId: string;
  moduleId: string;
  minimumMastery?: number;
  minimumAssessmentScore?: number;
};

type CredentialEligibility = {
  eligible: boolean;
  reasons: string[];
  evidence: {
    moduleId: string;
    masteredClaims: Array<{
      claimId: string;
      masteryProbability: number;
      evidenceCount: number;
    }>;
    assessmentAttempts: Array<{
      assessmentAttemptId: string;
      scorePercent: number;
      status: string;
    }>;
    unresolvedVivaRequirement: boolean;
  };
};

type VivaEventPayload = {
  moduleId?: string;
  status?: string;
};

function toJsonValue(value: unknown): Prisma.InputJsonValue {
  return JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue;
}

export class CredentialService {
  constructor(private prisma: PrismaClient) {}

  // ===========================================================================
  // Credential Repository Implementation (using LearningEvent)
  // ===========================================================================

  async create(credential: Credential): Promise<Credential> {
    await this.prisma.learningEvent.create({
      data: {
        tenantId: credential.tenantId,
        userId: credential.userId,
        eventType: CREDENTIAL_EVENT_TYPE,
        payload: toJsonValue(credential),
        timestamp: credential.issuedAt,
      },
    });
    return credential;
  }

  async findById(id: string): Promise<Credential | null> {
    // Postgres: payload->>'id' = id; SQLite: JSON_EXTRACT(payload,'$.id') = id
    const isPostgres =
      process.env.DATABASE_URL?.startsWith("postgresql") ?? false;

    let events: LearningEventRecord[];
    if (isPostgres) {
      events = await this.prisma.$queryRaw`
        SELECT * FROM "LearningEvent"
        WHERE "eventType" = ${CREDENTIAL_EVENT_TYPE}
          AND payload->>'id' = ${id}
        LIMIT 1`;
    } else {
      events = await this.prisma.$queryRaw`
        SELECT * FROM LearningEvent
        WHERE eventType = ${CREDENTIAL_EVENT_TYPE}
          AND JSON_EXTRACT(payload, '$.id') = ${id}
        LIMIT 1`;
    }

    if (events.length === 0) {
      return null;
    }

    const event = events[0];
    if (!event) {
      return null;
    }

    const payload =
      typeof event.payload === "string"
        ? (JSON.parse(event.payload) as CredentialEventPayload)
        : (event.payload as CredentialEventPayload);

    if (!payload.id) {
      return null;
    }

    return this.mergeCredentialUpdates(payload as Credential);
  }

  async findByUser(
    userId: string,
    filter?: CredentialFilter,
  ): Promise<Credential[]> {
    const events = await this.prisma.learningEvent.findMany({
      where: {
        userId,
        eventType: CREDENTIAL_EVENT_TYPE,
      },
      orderBy: { timestamp: "desc" },
      take: filter?.limit || 100,
    });

    let credentials = events
      .map((event) => event.payload as unknown as Credential)
      .filter((credential) => Boolean(credential && credential.id));

    if (filter) {
      if (filter.type) {
        credentials = credentials.filter(
          (credential) => credential.type === filter.type,
        );
      }
      if (filter.status) {
        credentials = credentials.filter(
          (credential) => credential.status === filter.status,
        );
      }
      if (filter.category) {
        credentials = credentials.filter(
          (credential) => credential.metadata.category === filter.category,
        );
      }
      if (filter.simulationId) {
        credentials = credentials.filter(
          (credential) =>
            credential.achievement?.simulationId === filter.simulationId,
        );
      }
      if (filter.tenantId) {
        credentials = credentials.filter(
          (credential) => credential.tenantId === filter.tenantId,
        );
      }
    }

    return credentials;
  }

  async hasCredential(userId: string, ruleId: string): Promise<boolean> {
    const credentials = await this.findByUser(userId);
    return credentials.some(
      (credential) => credential.metadata.customData?.ruleId === ruleId,
    );
  }

  async update(
    id: string,
    updates: Partial<Credential>,
  ): Promise<Credential | null> {
    const existing = await this.findById(id);
    if (!existing) {
      return null;
    }

    await this.prisma.learningEvent.create({
      data: {
        tenantId: existing.tenantId,
        userId: existing.userId,
        eventType: "CREDENTIAL_UPDATED",
        payload: toJsonValue({ id, ...updates }),
        timestamp: new Date(),
      },
    });

    return { ...existing, ...updates };
  }

  async evaluateEvidenceEligibility(
    requirement: CredentialEvidenceRequirement,
  ): Promise<CredentialEligibility> {
    const minimumMastery = requirement.minimumMastery ?? 0.8;
    const minimumAssessmentScore = requirement.minimumAssessmentScore ?? 80;

    const [masteries, attempts, vivaEvents] = await Promise.all([
      this.prisma.learnerMastery.findMany({
        where: {
          tenantId: requirement.tenantId,
          userId: requirement.userId,
          concept: {
            moduleId: requirement.moduleId,
          },
        },
        select: {
          conceptId: true,
          masteryProbability: true,
          evidenceCount: true,
        },
      }),
      this.prisma.assessmentAttempt.findMany({
        where: {
          tenantId: requirement.tenantId,
          userId: requirement.userId,
          scorePercent: { not: null },
          status: { in: ["SUBMITTED", "GRADED"] },
          assessment: {
            moduleId: requirement.moduleId,
          },
        },
        select: {
          id: true,
          scorePercent: true,
          status: true,
        },
      }),
      this.prisma.learningEvent.findMany({
        where: {
          tenantId: requirement.tenantId,
          userId: requirement.userId,
          eventType: {
            in: [
              "MICRO_VIVA_REQUIRED",
              "MICRO_VIVA_FAILED",
              "MICRO_VIVA_PASSED",
              "MICRO_VIVA_RESOLVED",
            ],
          },
        },
        orderBy: { timestamp: "asc" },
      }),
    ]);

    const masteredClaims = masteries
      .filter(
        (mastery) =>
          mastery.masteryProbability >= minimumMastery &&
          mastery.evidenceCount > 0,
      )
      .map((mastery) => ({
        claimId: mastery.conceptId,
        masteryProbability: mastery.masteryProbability,
        evidenceCount: mastery.evidenceCount,
      }));

    const qualifyingAttempts = attempts
      .filter(
        (attempt) =>
          typeof attempt.scorePercent === "number" &&
          attempt.scorePercent >= minimumAssessmentScore,
      )
      .map((attempt) => ({
        assessmentAttemptId: attempt.id,
        scorePercent: attempt.scorePercent ?? 0,
        status: String(attempt.status),
      }));

    const unresolvedVivaRequirement =
      getLatestVivaState(vivaEvents, requirement.moduleId) === "unresolved";

    const reasons: string[] = [];
    if (masteredClaims.length === 0) {
      reasons.push("No mastered claim evidence meets the credential threshold");
    }
    if (qualifyingAttempts.length === 0) {
      reasons.push("No valid assessment evidence meets the credential threshold");
    }
    if (unresolvedVivaRequirement) {
      reasons.push("A micro-viva requirement is unresolved");
    }

    return {
      eligible: reasons.length === 0,
      reasons,
      evidence: {
        moduleId: requirement.moduleId,
        masteredClaims,
        assessmentAttempts: qualifyingAttempts,
        unresolvedVivaRequirement,
      },
    };
  }

  async issueFromEvidence(
    dto: Omit<IssueCredentialDTO, "tenantId"> & { tenantId: string; moduleId: string },
  ): Promise<Credential> {
    const eligibility = await this.evaluateEvidenceEligibility({
      tenantId: dto.tenantId,
      userId: dto.userId,
      moduleId: dto.moduleId,
    });

    if (!eligibility.eligible) {
      throw credentialError(
        422,
        "CREDENTIAL_EVIDENCE_INSUFFICIENT",
        eligibility.reasons.join("; "),
      );
    }

    const credential = createCredential({
      ...dto,
      metadata: {
        ...dto.metadata,
        customData: {
          ...dto.metadata.customData,
          evidence: eligibility.evidence,
          issuedFrom: "mastery_evidence",
        },
      },
    });

    return this.create(credential);
  }

  async verifyCredential(id: string): Promise<{
    credential: Credential | null;
    valid: boolean;
    verificationUrl?: string;
    revoked: boolean;
  }> {
    const credential = await this.findById(id);
    if (!credential) {
      return {
        credential: null,
        valid: false,
        revoked: false,
      };
    }

    return {
      credential,
      valid: isCredentialValid(credential),
      verificationUrl: credential.verification.verificationUrl,
      revoked: credential.status === "revoked",
    };
  }

  async revokeCredential(id: string, reason: string): Promise<Credential | null> {
    return this.update(id, {
      status: "revoked",
      revokedAt: new Date(),
      metadata: {
        ...(await this.findById(id))?.metadata,
        customData: {
          ...((await this.findById(id))?.metadata.customData ?? {}),
          revocationReason: reason,
        },
      },
    });
  }

  async reissueCredential(id: string): Promise<Credential | null> {
    const existing = await this.findById(id);
    if (!existing) {
      return null;
    }

    const evidence = existing.metadata.customData?.evidence as
      | { moduleId?: string }
      | undefined;
    if (!evidence?.moduleId) {
      throw credentialError(
        422,
        "CREDENTIAL_REISSUE_EVIDENCE_REQUIRED",
        "Credential does not include module mastery evidence for reissue",
      );
    }

    return this.issueFromEvidence({
      type: existing.type,
      userId: existing.userId,
      tenantId: existing.tenantId,
      moduleId: evidence.moduleId,
      name: existing.name,
      description: existing.description,
      metadata: {
        ...existing.metadata,
        customData: {
          ...existing.metadata.customData,
          reissuedFrom: existing.id,
        },
      },
      ...(existing.imageUrl ? { imageUrl: existing.imageUrl } : {}),
      ...(existing.achievement ? { achievement: existing.achievement } : {}),
      ...(existing.skill ? { skill: existing.skill } : {}),
      ...(existing.certificate ? { certificate: existing.certificate } : {}),
      ...(existing.expiresAt ? { expiresAt: existing.expiresAt } : {}),
    });
  }

  private async mergeCredentialUpdates(base: Credential): Promise<Credential> {
    const updateEvents = await this.prisma.learningEvent.findMany({
      where: {
        userId: base.userId,
        eventType: "CREDENTIAL_UPDATED",
      },
      orderBy: { timestamp: "asc" },
    });

    let merged = { ...base };
    for (const event of updateEvents) {
      const delta =
        typeof event.payload === "string"
          ? (JSON.parse(event.payload) as CredentialEventPayload)
          : (event.payload as CredentialEventPayload);

      if (delta.id === base.id) {
        merged = { ...merged, ...delta };
      }
    }

    return merged;
  }

  async count(_filter?: CredentialFilter): Promise<number> {
    return 0;
  }

  // ===========================================================================
  // Progress Repository Implementation
  // ===========================================================================

  async getProgress(
    userId: string,
    tenantId: string,
  ): Promise<LearningProgress | null> {
    const attempts = await this.prisma.assessmentAttempt.findMany({
      where: {
        userId,
        tenantId,
        assessment: {
          type: "SIMULATION",
        },
        status: { in: ["SUBMITTED", "GRADED"] },
      },
      include: {
        assessment: {
          select: {
            title: true,
            module: {
              select: {
                domain: true,
              },
            },
          },
        },
      },
    });

    const domainProgress = new Map<string, DomainProgress>();
    let totalTime = 0;
    let perfectScores = 0;
    let streak = 0;

    for (const attempt of attempts) {
      const score = attempt.scorePercent || 0;
      if (score >= 100) {
        perfectScores++;
      }

      const domainKey = String(attempt.assessment.module.domain);
      let progress = domainProgress.get(domainKey);

      if (!progress) {
        progress = {
          domain: domainKey,
          simulationsCompleted: 0,
          totalSimulations: 0,
          averageScore: 0,
          skillLevel: "beginner",
          lastCompletedAt: new Date(0),
        };
        domainProgress.set(domainKey, progress);
      }

      progress.simulationsCompleted++;
      progress.averageScore =
        (progress.averageScore * (progress.simulationsCompleted - 1) + score) /
        progress.simulationsCompleted;

      if (
        attempt.submittedAt &&
        attempt.submittedAt > progress.lastCompletedAt
      ) {
        progress.lastCompletedAt = attempt.submittedAt;
      }
    }

    return {
      userId,
      tenantId,
      currentStreak: streak,
      longestStreak: streak,
      lastActivityDate: new Date(),
      simulationsCompleted: attempts.length,
      domainPacksCompleted: 0,
      totalTimeSpent: totalTime,
      averageScore:
        attempts.length > 0
          ? attempts.reduce(
              (sum, attempt) => sum + (attempt.scorePercent || 0),
              0,
            ) / attempts.length
          : 0,
      perfectScoresCount: perfectScores,
      firstAttemptPassCount: 0,
      domainProgress,
    };
  }
}

function getLatestVivaState(
  events: Array<{ eventType: string; payload: unknown }>,
  moduleId: string,
): "resolved" | "unresolved" | "none" {
  const relevantEvents = events.filter((event) => {
    const payload = normalizeVivaPayload(event.payload);
    return !payload.moduleId || payload.moduleId === moduleId;
  });

  const latest = relevantEvents.at(-1);
  if (!latest) {
    return "none";
  }

  if (
    latest.eventType === "MICRO_VIVA_PASSED" ||
    latest.eventType === "MICRO_VIVA_RESOLVED"
  ) {
    return "resolved";
  }

  return "unresolved";
}

function normalizeVivaPayload(payload: unknown): VivaEventPayload {
  if (typeof payload === "string") {
    try {
      return JSON.parse(payload) as VivaEventPayload;
    } catch {
      return {};
    }
  }

  if (payload && typeof payload === "object") {
    return payload as VivaEventPayload;
  }

  return {};
}

function credentialError(statusCode: number, code: string, message: string): Error & {
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
