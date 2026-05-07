/**
 * Credential Routes
 *
 * Fastify routes for issuing and managing credentials.
 */

import type { FastifyPluginAsync } from "fastify";
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
  CredentialMetadata,
  AchievementDetails,
  SkillDetails,
  CertificateDetails,
} from "./models/credential";
import {
  evaluateAchievements,
  SimulationResult,
} from "./rules/simulation-achievement-rules";
import { z } from "zod";

const simulationResultSchema = z.object({
  userId: z.string().min(1),
  simulationId: z.string().min(1),
  simulationName: z.string().min(1),
  score: z.number(),
  maxScore: z.number().positive(),
  completionTime: z.number().nonnegative(),
  attempts: z.number().int().positive(),
  domainPackId: z.string().min(1).optional(),
});

const evidenceIssueSchema = IssueCredentialDTOSchema.omit({ tenantId: true }).extend({
  moduleId: z.string().min(1),
});

const revokeCredentialSchema = z.object({
  reason: z.string().min(1),
});

type ParsedCredentialMetadata = {
  category?: CredentialMetadata["category"] | undefined;
  points?: CredentialMetadata["points"] | undefined;
  tier?: CredentialMetadata["tier"] | undefined;
  rarity?: CredentialMetadata["rarity"] | undefined;
  tags?: CredentialMetadata["tags"] | undefined;
  customData?: CredentialMetadata["customData"] | undefined;
};

function cleanCredentialMetadata(metadata: ParsedCredentialMetadata): Partial<CredentialMetadata> {
  return {
    ...(metadata.category ? { category: metadata.category } : {}),
    ...(typeof metadata.points === "number" ? { points: metadata.points } : {}),
    ...(metadata.tier ? { tier: metadata.tier } : {}),
    ...(metadata.rarity ? { rarity: metadata.rarity } : {}),
    ...(metadata.tags ? { tags: metadata.tags } : {}),
    ...(metadata.customData ? { customData: metadata.customData } : {}),
  };
}

function stripUndefinedFields(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(value).filter(([, fieldValue]) => fieldValue !== undefined),
  );
}

export const credentialRoutes: FastifyPluginAsync<{ service: CredentialService }> = async (
  fastify,
  options,
) => {
  const service = options.service;
  const privilegedCredentialRoles = ["teacher", "admin", "superadmin"];

  // List user's credentials
  fastify.get<{ Params: { userId: string }; Querystring: CredentialFilter }>(
    "/users/:userId/credentials",
    async (request, reply) => {
      const { userId } = request.params;
      const query = request.query as Record<string, unknown>;
      const filterResult = CredentialFilterSchema.safeParse({
        ...query,
        ...(typeof query.page === "string" ? { page: Number(query.page) } : {}),
        ...(typeof query.limit === "string"
          ? { limit: Number(query.limit) }
          : {}),
      });
      if (!filterResult.success) {
        reply.code(400).send({
          error: "Invalid credential filter query",
          issues: filterResult.error.issues,
        });
        return;
      }

      const filter = filterResult.data;
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
      const dtoResult = IssueCredentialDTOSchema.safeParse(request.body);
      if (!dtoResult.success) {
        reply.code(400).send({
          error: "Invalid credential issue payload",
          issues: dtoResult.error.issues,
        });
        return;
      }

      const dto = dtoResult.data;
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

  // Issue credential from demonstrated mastery and assessment evidence.
  fastify.post<{ Body: z.infer<typeof evidenceIssueSchema> }>(
    "/credentials/issue-from-evidence",
    async (request, reply) => {
      const dtoResult = evidenceIssueSchema.safeParse(request.body);
      if (!dtoResult.success) {
        reply.code(400).send({
          error: "Invalid credential evidence payload",
          issues: dtoResult.error.issues,
        });
        return;
      }

      const dto = dtoResult.data;
      const tenantId = getTenantId(request);
      void getUserId(request);
      requireRole(request, privilegedCredentialRoles);

      try {
        const credential = await service.issueFromEvidence({
          type: dto.type,
          userId: dto.userId,
          tenantId,
          moduleId: dto.moduleId,
          name: dto.name,
          description: dto.description,
          metadata: cleanCredentialMetadata(dto.metadata),
          ...(dto.imageUrl ? { imageUrl: dto.imageUrl } : {}),
          ...(dto.achievement
            ? { achievement: stripUndefinedFields(dto.achievement) as Partial<AchievementDetails> }
            : {}),
          ...(dto.skill
            ? { skill: stripUndefinedFields(dto.skill) as Partial<SkillDetails> }
            : {}),
          ...(dto.certificate
            ? { certificate: stripUndefinedFields(dto.certificate) as Partial<CertificateDetails> }
            : {}),
          ...(dto.expiresAt ? { expiresAt: dto.expiresAt } : {}),
        });

        reply.code(201).send({
          data: credential,
        });
      } catch (error) {
        const statusCode =
          typeof (error as { statusCode?: unknown }).statusCode === "number"
            ? (error as { statusCode: number }).statusCode
            : 500;
        const code =
          typeof (error as { code?: unknown }).code === "string"
            ? (error as { code: string }).code
            : "CREDENTIAL_ISSUE_FAILED";
        reply.code(statusCode).send({
          error: {
            code,
            message: error instanceof Error ? error.message : "Credential issue failed",
          },
        });
      }
    },
  );

  fastify.get<{ Params: { credentialId: string } }>(
    "/credentials/:credentialId/verify",
    async (request, reply) => {
      const result = await service.verifyCredential(request.params.credentialId);

      if (!result.credential) {
        reply.code(404).send({
          error: "Credential not found",
        });
        return;
      }

      reply.send({
        data: result,
      });
    },
  );

  fastify.post<{ Params: { credentialId: string }; Body: z.infer<typeof revokeCredentialSchema> }>(
    "/credentials/:credentialId/revoke",
    async (request, reply) => {
      const bodyResult = revokeCredentialSchema.safeParse(request.body);
      if (!bodyResult.success) {
        reply.code(400).send({
          error: "Invalid credential revoke payload",
          issues: bodyResult.error.issues,
        });
        return;
      }

      void getTenantId(request);
      requireRole(request, privilegedCredentialRoles);

      const credential = await service.revokeCredential(
        request.params.credentialId,
        bodyResult.data.reason,
      );

      if (!credential) {
        reply.code(404).send({
          error: "Credential not found",
        });
        return;
      }

      reply.send({
        data: credential,
      });
    },
  );

  fastify.post<{ Params: { credentialId: string } }>(
    "/credentials/:credentialId/reissue",
    async (request, reply) => {
      void getTenantId(request);
      requireRole(request, privilegedCredentialRoles);

      const credential = await service.reissueCredential(request.params.credentialId);

      if (!credential) {
        reply.code(404).send({
          error: "Credential not found",
        });
        return;
      }

      reply.code(201).send({
        data: credential,
      });
    },
  );

  // Evaluate simulation result and issue achievements
  fastify.post<{ Body: SimulationResult }>(
    "/credentials/evaluate",
    async (request, reply) => {
      const resultValidation = simulationResultSchema.safeParse(request.body);
      if (!resultValidation.success) {
        reply.code(400).send({
          error: "Invalid simulation result payload",
          issues: resultValidation.error.issues,
        });
        return;
      }

      const result = resultValidation.data as SimulationResult;
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
        const metadata = achievement.metadata;
        if (!metadata?.ruleId || !metadata.credentialType || !metadata.category) {
          continue;
        }
        const alreadyHas = await service.hasCredential(result.userId, metadata.ruleId);

        if (!alreadyHas) {
          // Create credential for new achievement
          const credential = createCredential({
            type: metadata.credentialType,
            userId: result.userId,
            tenantId,
            name: achievement.credentialName || "",
            description: achievement.credentialDescription || "",
            metadata: {
              ...(metadata.category
                ? { category: metadata.category }
                : {}),
              ...(metadata.tier
                ? { tier: metadata.tier }
                : {}),
              ...(metadata.rarity
                ? { rarity: metadata.rarity }
                : {}),
              ...(typeof metadata.points === "number"
                ? { points: metadata.points }
                : {}),
              ...(metadata
                ? { customData: metadata }
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

  // Credentials are event-sourced through LearningEvent and verified by ID through
  // the credential lookup projection in CredentialService.
};
