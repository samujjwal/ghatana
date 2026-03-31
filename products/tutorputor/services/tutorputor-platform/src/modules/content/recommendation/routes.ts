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
import { getTenantId, roleGuard } from "../../../core/http/requestContext.js";
import type { PrismaClient } from "@tutorputor/core/db";
import { RecommendationService } from "./recommendation-service.js";
import { RecommendationEngine } from "./recommendation-engine.js";
import { AssetOutcomeService } from "./asset-outcome-service.js";
import { ExperienceRemediationService } from "./experience-remediation-service.js";

// =============================================================================
// Types
// =============================================================================

type AssetIdParams = {
  assetId: string;
};

type LimitQuery = {
  limit?: string;
};

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
  // GET /assets/:assetId/recommendations — All related assets grouped by type
  // ---------------------------------------------------------------------------
  fastify.get<{ Params: AssetIdParams; Querystring: LimitQuery }>(
    "/assets/:assetId/recommendations",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const limit = request.query.limit
        ? parseInt(request.query.limit, 10)
        : 10;

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
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const limit = request.query.limit ? parseInt(request.query.limit, 10) : 5;

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
      const tenantId = getTenantId(request);
      const { assetId } = request.params;

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
      const tenantId = getTenantId(request);
      const result = await service.recomputeOutcomeAwareEdges(tenantId, {
        ...(request.body?.sourceAssetId
          ? { sourceAssetId: request.body.sourceAssetId }
          : {}),
        ...(request.body?.limit !== undefined
          ? { limit: request.body.limit }
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
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
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
      const tenantId = getTenantId(request);
      const { assetId } = request.params;
      const result = await outcomeService.analyzeAsset(tenantId, assetId, {
        apply: request.body?.apply ?? true,
        recomputeRecommendations:
          request.body?.recomputeRecommendations ?? true,
      });
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/outcome-summary",
    { preHandler: [readGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await outcomeService.analyzeExperienceAssets(
        tenantId,
        request.params.experienceId,
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
      const tenantId = getTenantId(request);
      const result = await outcomeService.analyzeExperienceAssets(
        tenantId,
        request.params.experienceId,
        {
          apply: request.body?.apply ?? true,
          recomputeRecommendations:
            request.body?.recomputeRecommendations ?? true,
        },
      );
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/remediation-summary",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.summarizeExperience(
        tenantId,
        request.params.experienceId,
      );
      return reply.send({ data: result });
    },
  );

  fastify.get<{ Params: { experienceId: string } }>(
    "/experiences/:experienceId/remediation-policy",
    { preHandler: [adminGuard] },
    async (request, reply) => {
      const tenantId = getTenantId(request);
      const result = await remediationService.summarizeExperience(
        tenantId,
        request.params.experienceId,
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
      const tenantId = getTenantId(request);
      const result = await remediationService.rankExperienceInterventions(
        tenantId,
        request.params.experienceId,
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
      const tenantId = getTenantId(request);
      const result = await remediationService.applyRankedExperienceInterventions(
        tenantId,
        request.params.experienceId,
        request.body ?? {},
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
      const tenantId = getTenantId(request);
      const result = await remediationService.rankTenantPortfolioInterventions(
        tenantId,
        request.query ?? {},
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
      const tenantId = getTenantId(request);
      const result = await remediationService.applyTenantPortfolioInterventions(
        tenantId,
        request.body ?? {},
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
      const tenantId = getTenantId(request);
      const result = await remediationService.applyExperienceRemediation(
        tenantId,
        request.params.experienceId,
        request.body ?? {},
      );
      return reply.send({ data: result });
    },
  );
}
