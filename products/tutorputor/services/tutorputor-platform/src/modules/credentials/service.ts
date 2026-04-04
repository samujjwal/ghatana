/**
 * Credential Service
 *
 * Implements storage and retrieval of credentials using Learning Events,
 * and calculation of learning progress from assessment data.
 */

import type { Prisma, PrismaClient } from "@tutorputor/core/db";
import type { Credential, CredentialFilter } from "./models/credential";
import type {
  LearningProgress,
  DomainProgress,
} from "./rules/simulation-achievement-rules";

const CREDENTIAL_EVENT_TYPE = "CREDENTIAL_ISSUED";

type LearningEventRecord = {
  payload: unknown;
};

type CredentialEventPayload = Partial<Credential> & { id?: string };

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
