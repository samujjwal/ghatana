/**
 * Credential Routes
 *
 * Fastify routes for issuing and managing credentials.
 */

import { FastifyInstance } from "fastify";
import { CredentialService } from "./service";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../core/http/requestContext.js";
import {
  createCredential,
  getCredentialSummary,
  IssueCredentialDTOSchema,
  CredentialFilterSchema,
  IssueCredentialDTO,
  CredentialFilter,
} from "./models/credential";
import {
  evaluateAchievements,
  SimulationResult,
} from "./rules/simulation-achievement-rules";

export async function credentialRoutes(
  fastify: FastifyInstance,
  options: { service: CredentialService },
) {
  const service = options.service;
  const privilegedCredentialRoles = ["teacher", "admin", "superadmin"];

  // List user's credentials
  fastify.get<{ Params: { userId: string }; Querystring: CredentialFilter }>(
    "/users/:userId/credentials",
    async (request, reply) => {
      const { userId } = request.params;
      const filter = CredentialFilterSchema.parse(request.query);
      const tenantId = getTenantId(request);
      const currentUserId = getUserId(request);

      if (userId !== currentUserId) {
        requireRole(request, privilegedCredentialRoles);
      }

      const credentials = await service.findByUser(userId, {
        ...filter,
        tenantId,
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
    },
  );

  // Issue new credential (manual)
  fastify.post<{ Body: IssueCredentialDTO }>(
    "/credentials",
    async (request, reply) => {
      const dto = IssueCredentialDTOSchema.parse(request.body);
      const tenantId = getTenantId(request);
      void getUserId(request);
      requireRole(request, privilegedCredentialRoles);

      const credentialInput: IssueCredentialDTO = {
        type: dto.type,
        userId: dto.userId,
        tenantId,
        name: dto.name,
        description: dto.description,
        metadata: {
          ...(dto.metadata.category ? { category: dto.metadata.category } : {}),
          ...(typeof dto.metadata.points === "number"
            ? { points: dto.metadata.points }
            : {}),
          ...(dto.metadata.tier ? { tier: dto.metadata.tier } : {}),
          ...(dto.metadata.rarity ? { rarity: dto.metadata.rarity } : {}),
          ...(dto.metadata.tags ? { tags: dto.metadata.tags } : {}),
          ...(dto.metadata.customData
            ? { customData: dto.metadata.customData }
            : {}),
        },
        ...(dto.imageUrl ? { imageUrl: dto.imageUrl } : {}),
        ...(dto.achievement
          ? {
              achievement: {
                ...(dto.achievement.simulationId
                  ? { simulationId: dto.achievement.simulationId }
                  : {}),
                ...(dto.achievement.simulationName
                  ? { simulationName: dto.achievement.simulationName }
                  : {}),
                ...(dto.achievement.domainPackId
                  ? { domainPackId: dto.achievement.domainPackId }
                  : {}),
                ...(typeof dto.achievement.score === "number"
                  ? { score: dto.achievement.score }
                  : {}),
                ...(typeof dto.achievement.maxScore === "number"
                  ? { maxScore: dto.achievement.maxScore }
                  : {}),
                ...(typeof dto.achievement.completionTime === "number"
                  ? { completionTime: dto.achievement.completionTime }
                  : {}),
                ...(typeof dto.achievement.attempts === "number"
                  ? { attempts: dto.achievement.attempts }
                  : {}),
                ...(dto.achievement.criteria
                  ? {
                      criteria: dto.achievement.criteria.map((criterion) => ({
                        id: criterion.id,
                        name: criterion.name,
                        description: criterion.description,
                        met: criterion.met,
                        ...(typeof criterion.value === "number"
                          ? { value: criterion.value }
                          : {}),
                        ...(typeof criterion.threshold === "number"
                          ? { threshold: criterion.threshold }
                          : {}),
                      })),
                    }
                  : {}),
              },
            }
          : {}),
        ...(dto.skill
          ? {
              skill: {
                ...(dto.skill.skillId ? { skillId: dto.skill.skillId } : {}),
                ...(dto.skill.skillName
                  ? { skillName: dto.skill.skillName }
                  : {}),
                ...(dto.skill.level ? { level: dto.skill.level } : {}),
                ...(dto.skill.domain ? { domain: dto.skill.domain } : {}),
                ...(dto.skill.subDomain
                  ? { subDomain: dto.skill.subDomain }
                  : {}),
              },
            }
          : {}),
        ...(dto.certificate
          ? {
              certificate: {
                ...(dto.certificate.courseId
                  ? { courseId: dto.certificate.courseId }
                  : {}),
                ...(dto.certificate.courseName
                  ? { courseName: dto.certificate.courseName }
                  : {}),
                ...(dto.certificate.curriculum
                  ? { curriculum: dto.certificate.curriculum }
                  : {}),
                ...(dto.certificate.completionDate
                  ? { completionDate: dto.certificate.completionDate }
                  : {}),
                ...(dto.certificate.grade
                  ? { grade: dto.certificate.grade }
                  : {}),
                ...(typeof dto.certificate.hoursCompleted === "number"
                  ? { hoursCompleted: dto.certificate.hoursCompleted }
                  : {}),
              },
            }
          : {}),
        ...(dto.expiresAt ? { expiresAt: dto.expiresAt } : {}),
      };

      const credential = createCredential(credentialInput);

      const created = await service.create(credential);

      reply.code(201).send({
        data: created,
      });
    },
  );

  // Evaluate simulation result and issue achievements
  fastify.post<{ Body: SimulationResult }>(
    "/credentials/evaluate",
    async (request, reply) => {
      const result = request.body;
      const tenantId = getTenantId(request);
      const requesterId = getUserId(request);

      if (requesterId !== result.userId) {
        requireRole(request, privilegedCredentialRoles);
      }

      // Get user's learning progress
      const progress = await service.getProgress(result.userId, tenantId);

      if (!progress) {
        // Create empty progress if none found? Or 404.
        // For new user, progress might be 'zero'.
        reply
          .code(404)
          .send({ error: "User progress not found or insufficient data" });
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
              ...(achievement.metadata?.category
                ? { category: achievement.metadata.category as any }
                : {}),
              ...(achievement.metadata?.tier
                ? { tier: achievement.metadata.tier as any }
                : {}),
              ...(achievement.metadata?.rarity
                ? { rarity: achievement.metadata.rarity as any }
                : {}),
              ...(typeof achievement.metadata?.points === "number"
                ? { points: achievement.metadata.points as number }
                : {}),
              ...(achievement.metadata
                ? { customData: achievement.metadata }
                : {}),
            },
            achievement: {
              simulationId: result.simulationId,
              simulationName: result.simulationName,
              ...(result.domainPackId
                ? { domainPackId: result.domainPackId }
                : {}),
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
    },
  );

  // Note: 'getCredential' (by ID), 'verify', 'revoke' are omitted due to storage limitations
  // (Cannot efficiently find by ID in event stream without user context or indexing).
}
