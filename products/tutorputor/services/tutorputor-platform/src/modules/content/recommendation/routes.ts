/**
 * Recommendation Routes
 *
 * Fastify routes for related assets, prerequisites, and next-step
 * suggestions. Serves the learner-facing discovery rails and admin
 * bootstrap actions.
 *
 * @doc.type module
 * @doc.purpose HTTP routes for recommendation and next-step APIs
 * @doc.layer product
 * @doc.pattern Route
 */

import type { FastifyInstance } from "fastify";
import { getTenantId, getUserId, roleGuard } from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { RecommendationService } from "./recommendation-service.js";
import { RecommendationEngine } from "./recommendation-engine.js";
import { AssetOutcomeService } from "./asset-outcome-service.js";
import { ExperienceRemediationService } from "./experience-remediation-service.js";
import { z } from "zod";

// =============================================================================
// Types
// =============================================================================

type AssetIdParams = {
  assetId: string;
};

type LimitQuery = {
  limit?: string;
};

const assetIdParamsSchema = z.object({
  assetId: z.string().trim().min(1),
});

const experienceIdParamsSchema = z.object({
  experienceId: z.string().trim().min(1),
});

const limitQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).optional(),
});

const recomputeRecommendationsBodySchema = z.object({
  sourceAssetId: z.string().trim().min(1).optional(),
  limit: z.number().int().min(1).max(100).optional(),
});

const outcomeRecomputeBodySchema = z.object({
  apply: z.boolean().optional(),
  recomputeRecommendations: z.boolean().optional(),
});

const remediationRankingApplyBodySchema = z.object({
  limit: z.number().int().min(1).max(100).optional(),
});

const tenantInterventionsQuerySchema = z.object({
  experienceLimit: z.coerce.number().int().min(1).max(100).optional(),
  interventionsPerExperience: z.coerce.number().int().min(1).max(100).optional(),
});

const tenantInterventionsApplyBodySchema = z.object({
  experienceLimit: z.number().int().min(1).max(100).optional(),
  interventionsPerExperience: z.number().int().min(1).max(100).optional(),
  maxActions: z.number().int().min(1).max(100).optional(),
});

const remediationApplyBodySchema = z.object({
  autoPromoteExperiments: z.boolean().optional(),
  recomputeRecommendations: z.boolean().optional(),
});

function sendValidationError(
  reply: { status: (code: number) => { send: (body: unknown) => unknown } },
  error: z.ZodError,
  message: string,
) {
  return reply.status(400).send({
    error: message,
    issues: error.issues,
  });
}

// =============================================================================
// Route Registration
// =============================================================================

export interface RecommendationRouteContext {
  prisma: PrismaClient;
}

export function registerRecommendationRoutes(
  fastify: FastifyInstance,
  context: RecommendationRouteContext,
): void {
  const service = new RecommendationService(context.prisma);
  const engine = new RecommendationEngine(context.prisma, {
    recommendationService: service,
  });
  const outcomeService = new AssetOutcomeService(context.prisma);
  const remediationService = new ExperienceRemediationService(context.prisma);

  const readGuard = roleGuard([
    "admin",
    "content_creator",
    "superadmin",
    "teacher",
    "student",
  ]);
  const adminGuard = roleGuard(["admin", "content_creator", "superadmin"]);

  // ---------------------------------------------------------------------------
  // GET /recommendations/personalized — AI-powered personalized recommendations
  // ---------------------------------------------------------------------------
  fastify.get<{ Querystring: { limit?: string } }>(
    "/recommendations/personalized",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const queryResult = limitQuerySchema.safeParse(request.query ?? {});
      if (!queryResult.success) {
        return sendValidationError(reply, queryResult.error, "Invalid limit query");
      }

      const tenantId = getTenantId(request);
      const userId = getUserId(request);

      if (!userId) {
        return reply.status(401).send({ error: "Authentication required" });
      }

      const limit = queryResult.data.limit ?? 6;

      const result = await service.getPersonalizedRecommendations(tenantId, userId, {
        limit,
        excludeEnrolled: true,
      });
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/recommendations — All related assets grouped by type
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: LimitQuery }>(
    "/assets/:assetId/recommendations",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const queryResult = limitQuerySchema.safeParse(request.query ?? {});
      if (!queryResult.success) {
        return sendValidationError(reply, queryResult.error, "Invalid limit query");
      }

      const tenantId = getTenantId(request);
      const { assetId } = paramsResult.data;
      const limit = queryResult.data.limit ?? 10;

      const result = await engine.getRecommendations({
        tenantId,
        assetId,
        limit,
      });
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/next-steps — Next-step suggestions
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: LimitQuery }>(
    "/assets/:assetId/next-steps",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const queryResult = limitQuerySchema.safeParse(request.query ?? {});
      if (!queryResult.success) {
        return sendValidationError(reply, queryResult.error, "Invalid limit query");
      }

      const tenantId = getTenantId(request);
      const { assetId } = paramsResult.data;
      const limit = queryResult.data.limit ?? 5;

      const suggestions = await engine.getNextSteps({
        tenantId,
        assetId,
        limit,
      });
      return reply.send({ data: suggestions });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /assets/:assetId/bootstrap-edges — Bootstrap recommendation edges
  // ---------------------------------------------------------------------------
  fastify.post<{ Params: AssetIdParams }>(
    "/assets/:assetId/bootstrap-edges",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const tenantId = getTenantId(request);
      const { assetId } = paramsResult.data;

      const result = await service.bootstrapEdges(tenantId, assetId);
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /recommendations/recompute — Outcome-aware recommendation refresh
  // ---------------------------------------------------------------------------
  fastify.post<{
    Body: { sourceAssetId?: string; limit?: number };
  }>(
    "/recommendations/recompute",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const bodyResult = recomputeRecommendationsBodySchema.safeParse(
        request.body ?? {},
      );
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid recompute payload",
        );
      }

      const tenantId = getTenantId(request);
      const result = await service.recomputeOutcomeAwareEdges(tenantId, {
        ...(bodyResult.data.sourceAssetId
          ? { sourceAssetId: bodyResult.data.sourceAssetId }
          : {}),
        ...(bodyResult.data.limit !== undefined
          ? { limit: bodyResult.data.limit }
          : {}),
      });

      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // GET /assets/:assetId/outcome-summary — Analyze learner/reviewer outcomes
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams }>(
    "/assets/:assetId/outcome-summary",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const tenantId = getTenantId(request);
      const { assetId } = paramsResult.data;
      const result = await outcomeService.analyzeAsset(tenantId, assetId);
      return reply.send({ data: result });
    },
  );

  // ---------------------------------------------------------------------------
  // POST /assets/:assetId/outcome-summary/recompute — Persist outcome signals
  // ---------------------------------------------------------------------------
  fastify.post<{
    Params: AssetIdParams;
    Body: { apply?: boolean; recomputeRecommendations?: boolean };
  }>(
    "/assets/:assetId/outcome-summary/recompute",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = assetIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(reply, paramsResult.error, "Invalid asset id");
      }

      const bodyResult = outcomeRecomputeBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid outcome recompute payload",
        );
      }

      const tenantId = getTenantId(request);
      const { assetId } = paramsResult.data;
      const result = await outcomeService.analyzeAsset(tenantId, assetId, {
        apply: bodyResult.data.apply ?? true,
        recomputeRecommendations: bodyResult.data.recomputeRecommendations ?? true,
      });
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/outcome-summary",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const tenantId = getTenantId(request);
      const result = await outcomeService.analyzeExperienceAssets(
        tenantId,
        paramsResult.data.experienceId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.post<{
    Params: { experienceId: string };
    Body: { apply?: boolean; recomputeRecommendations?: boolean };
  }>(
    "/experiences/:experienceId/outcome-summary/recompute",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const bodyResult = outcomeRecomputeBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid outcome recompute payload",
        );
      }

      const tenantId = getTenantId(request);
      const result = await outcomeService.analyzeExperienceAssets(
        tenantId,
        paramsResult.data.experienceId,
        {
          apply: bodyResult.data.apply ?? true,
          recomputeRecommendations: bodyResult.data.recomputeRecommendations ?? true,
        },
      );
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/remediation-summary",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.summarizeExperience(
        tenantId,
        paramsResult.data.experienceId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/remediation-policy",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.summarizeExperience(
        tenantId,
        paramsResult.data.experienceId,
      );
      return reply.send({
        data: {
          experienceId: result.experienceId,
          recommendedActions: result.recommendedActions,
          policyBreakdown: result.policyBreakdown,
        },
      });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/remediation-rankings",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.rankExperienceInterventions(
        tenantId,
        paramsResult.data.experienceId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.post<{
    Params: { experienceId: string };
    Body: { limit?: number };
  }>(
    "/experiences/:experienceId/remediation-rankings/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const bodyResult = remediationRankingApplyBodySchema.safeParse(
        request.body ?? {},
      );
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid remediation ranking payload",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.applyRankedExperienceInterventions(
        tenantId,
        paramsResult.data.experienceId,
        {
          ...(typeof bodyResult.data.limit === "number"
            ? { limit: bodyResult.data.limit }
            : {}),
        },
      );
      return reply.send({ data: result });
    },
  );

  fastify.get(
    "/remediation-policy/tenant",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.summarizeTenantPolicyProfile(
        tenantId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.get(
    "/remediation-policy/tenant/model",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.trainTenantPolicyModel(tenantId);
      return reply.send({ data: result });
    },
  );

  fastify.get(
    "/remediation-policy/tenant/causal-model",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.trainTenantCausalPolicyModel(tenantId);
      return reply.send({ data: result });
    },
  );

  fastify.get(
    "/remediation-policy/tenant/scenarios",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.simulateTenantPolicyScenarios(
        tenantId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.get(
    "/remediation-policy/tenant/portfolio",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.rankTenantRemediationPortfolio(
        tenantId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.get<{
    Querystring: {
      experienceLimit?: number;
      interventionsPerExperience?: number;
    };
  }>(
    "/remediation-policy/tenant/interventions",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const queryResult = tenantInterventionsQuerySchema.safeParse(
        request.query ?? {},
      );
      if (!queryResult.success) {
        return sendValidationError(
          reply,
          queryResult.error,
          "Invalid tenant interventions query",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.rankTenantPortfolioInterventions(
        tenantId,
        {
          ...(typeof queryResult.data.experienceLimit === "number"
            ? { experienceLimit: queryResult.data.experienceLimit }
            : {}),
          ...(typeof queryResult.data.interventionsPerExperience === "number"
            ? {
                interventionsPerExperience:
                  queryResult.data.interventionsPerExperience,
              }
            : {}),
        },
      );
      return reply.send({ data: result });
    },
  );

  fastify.post<{
    Body: {
      experienceLimit?: number;
      interventionsPerExperience?: number;
      maxActions?: number;
    };
  }>(
    "/remediation-policy/tenant/interventions/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const bodyResult = tenantInterventionsApplyBodySchema.safeParse(
        request.body ?? {},
      );
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid tenant intervention apply payload",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.applyTenantPortfolioInterventions(
        tenantId,
        {
          ...(typeof bodyResult.data.experienceLimit === "number"
            ? { experienceLimit: bodyResult.data.experienceLimit }
            : {}),
          ...(typeof bodyResult.data.interventionsPerExperience === "number"
            ? {
                interventionsPerExperience:
                  bodyResult.data.interventionsPerExperience,
              }
            : {}),
          ...(typeof bodyResult.data.maxActions === "number"
            ? { maxActions: bodyResult.data.maxActions }
            : {}),
        },
      );
      return reply.send({ data: result });
    },
  );

  fastify.post<{
    Params: { experienceId: string };
    Body: { autoPromoteExperiments?: boolean; recomputeRecommendations?: boolean };
  }>(
    "/experiences/:experienceId/remediation-summary/apply",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        return sendValidationError(
          reply,
          paramsResult.error,
          "Invalid experience id",
        );
      }

      const bodyResult = remediationApplyBodySchema.safeParse(request.body ?? {});
      if (!bodyResult.success) {
        return sendValidationError(
          reply,
          bodyResult.error,
          "Invalid remediation apply payload",
        );
      }

      const tenantId = getTenantId(request);
      const result = await remediationService.applyExperienceRemediation(
        tenantId,
        paramsResult.data.experienceId,
        {
          ...(typeof bodyResult.data.autoPromoteExperiments === "boolean"
            ? {
                autoPromoteExperiments:
                  bodyResult.data.autoPromoteExperiments,
              }
            : {}),
          ...(typeof bodyResult.data.recomputeRecommendations === "boolean"
            ? {
                recomputeRecommendations:
                  bodyResult.data.recomputeRecommendations,
              }
            : {}),
        },
      );
      return reply.send({ data: result });
    },
  );
}
