/**
 * Credential Routes
 *
 * Fastify routes for issuing and managing credentials.
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { CredentialService } from "./service";
import {
    createCredential,
    isCredentialValid,
    getCredentialSummary,
    IssueCredentialDTOSchema,
    CredentialFilterSchema,
    IssueCredentialDTO,
    CredentialFilter,
} from "./models/credential";
import {
    evaluateAchievements,
    getProgressTowardsAchievements,
    SimulationResult,
} from "./rules/simulation-achievement-rules";

export async function credentialRoutes(fastify: FastifyInstance, options: { service: CredentialService }) {
    const service = options.service;

    // List user's credentials
    fastify.get<{ Params: { userId: string }; Querystring: CredentialFilter }>(
        "/users/:userId/credentials",
        async (request, reply) => {
            const { userId } = request.params;
            const filter = CredentialFilterSchema.parse(request.query);
            // const tenantId = request.headers["x-tenant-id"] as string;

            const credentials = await service.findByUser(userId, {
                ...filter,
                // tenantId,
            });

            const summary = getCredentialSummary(credentials);

            reply.send({
                data: credentials,
                summary,
                // Pagination metadata is approximate as we scan
                pagination: {
                    page: filter.page,
                    limit: filter.limit,
                    total: credentials.length,
                    totalPages: 1,
                },
            });
        }
    );

    // Issue new credential (manual)
    fastify.post<{ Body: IssueCredentialDTO }>(
        "/credentials",
        async (request, reply) => {
            const dto = IssueCredentialDTOSchema.parse(request.body);
            const tenantId = (request.headers["x-tenant-id"] as string) || dto.tenantId;

            const credential = createCredential({
                ...dto,
                tenantId: tenantId,
            });

            const created = await service.create(credential);

            reply.code(201).send({
                data: created,
            });
        }
    );

    // Evaluate simulation result and issue achievements
    fastify.post<{ Body: SimulationResult }>(
        "/credentials/evaluate",
        async (request, reply) => {
            const result = request.body;
            const tenantId = (request.headers["x-tenant-id"] as string) || result.tenantId;

            // Get user's learning progress
            const progress = await service.getProgress(result.userId, tenantId);

            if (!progress) {
                // Create empty progress if none found? Or 404.
                // For new user, progress might be 'zero'.
                reply.code(404).send({ error: "User progress not found or insufficient data" });
                return;
            }

            // Evaluate which achievements were earned
            const achievedResults = evaluateAchievements(result, progress);

            // Filter out already-earned achievements
            const newAchievements = [];
            for (const achievement of achievedResults) {
                const ruleId = achievement.metadata?.ruleId as string;
                const alreadyHas = await service.hasCredential(result.userId, ruleId);

                if (!alreadyHas) {
                    // Create credential for new achievement
                    const credential = createCredential({
                        type: achievement.metadata?.credentialType as any,
                        userId: result.userId,
                        tenantId,
                        name: achievement.credentialName || "",
                        description: achievement.credentialDescription || "",
                        metadata: {
                            category: achievement.metadata?.category as any,
                            tier: achievement.metadata?.tier as any,
                            rarity: achievement.metadata?.rarity as any,
                            points: achievement.metadata?.points as number,
                            customData: achievement.metadata,
                        },
                        achievement: {
                            simulationId: result.simulationId,
                            simulationName: result.simulationName,
                            domainPackId: result.domainPackId,
                            score: result.score,
                            maxScore: result.maxScore,
                            completionTime: result.completionTime,
                            attempts: result.attempts,
                            criteria: achievement.criteria,
                        },
                    });

                    const created = await service.create(credential);
                    newAchievements.push(created);
                }
            }

            reply.send({
                data: {
                    evaluated: achievedResults.length,
                    newAchievements: newAchievements.length,
                    credentials: newAchievements,
                },
            });
        }
    );

    // Note: 'getCredential' (by ID), 'verify', 'revoke' are omitted due to storage limitations
    // (Cannot efficiently find by ID in event stream without user context or indexing).
}
