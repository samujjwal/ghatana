/**
 * Credential Service
 * 
 * Implements storage and retrieval of credentials using Learning Events,
 * and calculation of learning progress from assessment data.
 */

import { PrismaClient } from "@ghatana/tutorputor-db";
import {
    Credential,
    CredentialFilter,
    CredentialStatus,
    CredentialType
} from "./models/credential";
import { LearningProgress, DomainProgress } from "./rules/simulation-achievement-rules";

const CREDENTIAL_EVENT_TYPE = "CREDENTIAL_ISSUED";

export class CredentialService {
    constructor(private prisma: PrismaClient) { }

    // ===========================================================================
    // Credential Repository Implementation (using LearningEvent)
    // ===========================================================================

    async create(credential: Credential): Promise<Credential> {
        await this.prisma.learningEvent.create({
            data: {
                tenantId: credential.tenantId,
                userId: credential.userId,
                eventType: CREDENTIAL_EVENT_TYPE,
                payload: credential as any, // Store full object
                timestamp: credential.issuedAt,
            },
        });
        return credential;
    }

    async findById(id: string): Promise<Credential | null> {
        // Use Prisma raw query to search JSON payload field for matching credential id.
        // Postgres: payload->>'id' = id; SQLite: JSON_EXTRACT(payload,'$.id') = id
        const isPostgres = (this.prisma as any)._engineConfig?.activeProvider === 'postgresql'
            || process.env.DATABASE_URL?.startsWith('postgresql');

        let events: any[];
        if (isPostgres) {
            events = await this.prisma.$queryRaw`
                SELECT * FROM "LearningEvent"
                WHERE "eventType" = ${CREDENTIAL_EVENT_TYPE}
                  AND payload->>'id' = ${id}
                LIMIT 1`;
        } else {
            // SQLite fallback: JSON_EXTRACT
            events = await this.prisma.$queryRaw`
                SELECT * FROM LearningEvent
                WHERE eventType = ${CREDENTIAL_EVENT_TYPE}
                  AND JSON_EXTRACT(payload, '$.id') = ${id}
                LIMIT 1`;
        }

        if (!events || events.length === 0) return null;
        const event = events[0];
        const payload = typeof event.payload === 'string' ? JSON.parse(event.payload) : event.payload;
        if (!payload?.id) return null;

        // Merge with any CREDENTIAL_UPDATED events
        return this.mergeCredentialUpdates(payload);
    }

    async findByUser(userId: string, filter?: CredentialFilter): Promise<Credential[]> {
        const events = await this.prisma.learningEvent.findMany({
            where: {
                userId,
                // tenantId: filter?.tenantId, // Optional in filter
                eventType: CREDENTIAL_EVENT_TYPE,
            },
            orderBy: { timestamp: 'desc' },
            take: filter?.limit || 100, // Limit scan
        });

        // Map and filter in memory
        let credentials = events.map((e: any) => e.payload as unknown as Credential).filter((c: any) => c && c.id);

        if (filter) {
            if (filter.type) credentials = credentials.filter((c: any) => c.type === filter.type);
            if (filter.status) credentials = credentials.filter((c: any) => c.status === filter.status);
            if (filter.category) credentials = credentials.filter((c: any) => c.metadata.category === filter.category);
            if (filter.simulationId) credentials = credentials.filter((c: any) => c.achievement?.simulationId === filter.simulationId);
            if (filter.tenantId) credentials = credentials.filter((c: any) => c.tenantId === filter.tenantId);
        }

        return credentials;
    }

    async hasCredential(userId: string, ruleId: string): Promise<boolean> {
        const creds = await this.findByUser(userId);
        return creds.some(c => c.metadata.customData?.ruleId === ruleId);
    }

    async update(id: string, updates: Partial<Credential>): Promise<Credential | null> {
        // Append-only pattern: insert a CREDENTIAL_UPDATED event containing the delta.
        // On read, mergeCredentialUpdates() replays updates on top of the original.
        const existing = await this.findById(id);
        if (!existing) return null;

        await this.prisma.learningEvent.create({
            data: {
                tenantId: existing.tenantId,
                userId: existing.userId,
                eventType: 'CREDENTIAL_UPDATED',
                payload: { id, ...updates } as any,
                timestamp: new Date(),
            },
        });

        return { ...existing, ...updates };
    }

    private async mergeCredentialUpdates(base: Credential): Promise<Credential> {
        // Fetch any CREDENTIAL_UPDATED events for this credential ID and apply them.
        const updateEvents = await this.prisma.learningEvent.findMany({
            where: {
                userId: base.userId,
                eventType: 'CREDENTIAL_UPDATED',
            },
            orderBy: { timestamp: 'asc' },
        });

        let merged = { ...base };
        for (const evt of updateEvents) {
            const delta = typeof evt.payload === 'string' ? JSON.parse(evt.payload) : evt.payload as any;
            if (delta?.id === base.id) {
                merged = { ...merged, ...delta };
            }
        }
        return merged;
    }

    async count(filter?: CredentialFilter): Promise<number> {
        // Approximate count
        return 0;
    }

    // ===========================================================================
    // Progress Repository Implementation
    // ===========================================================================

    async getProgress(userId: string, tenantId: string): Promise<LearningProgress | null> {
        // Aggregate data from AssessmentAttempts (Simulations)
        // We need 'SIMULATION' type assessments.

        const attempts = await this.prisma.assessmentAttempt.findMany({
            where: {
                userId,
                tenantId,
                assessment: {
                    type: 'SIMULATION'
                },
                status: { in: ['SUBMITTED', 'GRADED'] }
            },
            include: {
                assessment: {
                    select: {
                        title: true,
                        // domain is on Module, not Assessment directly?
                        // Assessment -> Module -> domain
                        module: {
                            select: {
                                domain: true
                            }
                        }
                    }
                }
            }
        });

        const domainProgress = new Map<string, DomainProgress>();
        let totalTime = 0;
        let perfectScores = 0;
        let streak = 0; // Simplified

        // Process attempts to build stats
        for (const attempt of attempts) {
            const score = attempt.scorePercent || 0;
            if (score >= 100) perfectScores++;

            const domain = attempt.assessment.module.domain; // Enum
            const domainKey = String(domain);

            let dProg = domainProgress.get(domainKey);
            if (!dProg) {
                dProg = {
                    domain: domainKey,
                    simulationsCompleted: 0,
                    totalSimulations: 0, // Need to count distinct?
                    averageScore: 0,
                    skillLevel: 'beginner',
                    lastCompletedAt: new Date(0)
                };
                domainProgress.set(domainKey, dProg);
            }

            dProg.simulationsCompleted++;
            dProg.averageScore = ((dProg.averageScore * (dProg.simulationsCompleted - 1)) + score) / dProg.simulationsCompleted;
            if (attempt.submittedAt && attempt.submittedAt > dProg.lastCompletedAt) {
                dProg.lastCompletedAt = attempt.submittedAt;
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
            averageScore: attempts.length > 0 ? attempts.reduce((a: any, b: any) => a + (b.scorePercent || 0), 0) / attempts.length : 0,
            perfectScoresCount: perfectScores,
            firstAttemptPassCount: 0, // Need attempt history per assessment
            domainProgress
        };
    }
}
