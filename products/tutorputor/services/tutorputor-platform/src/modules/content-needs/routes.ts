/**
 * @doc.type module
 * @doc.purpose Content Needs Analyzer API routes
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance } from "fastify";
import type { ContentNeeds } from "@tutorputor/contracts/v1/learning-unit";
import type { ContentNeedsAnalyzer } from "./service";
import type { ContentDriftDetector } from "./drift-detector";
import type { ContentQualityMLPipeline } from "../content/quality-ml/pipeline";
import { z } from "zod";

const analyzeClaimBodySchema = z.object({
  claim: z.object({
    id: z.string().min(1),
    text: z.string().min(1),
    bloomLevel: z.string().min(1),
  }),
  context: z.object({
    domain: z.string().min(1),
    gradeRange: z.string().min(1),
    subject: z.string().min(1),
    topic: z.string().min(1),
    prerequisites: z.array(z.string()),
    learningObjectives: z.array(z.string()),
  }),
});

const experienceIdParamsSchema = z.object({
  experienceId: z.string().min(1),
});

const generateContentParamsSchema = z.object({
  claimId: z.string().min(1),
});

const generateContentBodySchema = z.object({
  needs: z.unknown(),
});

const batchAnalyzeBodySchema = z.object({
  claims: z
    .array(
      z.object({
        id: z.string().min(1),
        text: z.string().min(1),
        bloomLevel: z.string().min(1),
      }),
    )
    .min(1),
  context: z.object({
    domain: z.string().min(1),
    gradeRange: z.string().min(1),
    subject: z.string().min(1),
    topic: z.string().min(1),
    prerequisites: z.array(z.string()),
    learningObjectives: z.array(z.string()),
  }),
});

export function registerContentNeedsRoutes(
  fastify: FastifyInstance,
  contentNeedsAnalyzer: ContentNeedsAnalyzer,
  contentDriftDetector?: ContentDriftDetector,
  qualityPipeline?: ContentQualityMLPipeline,
): void {
  // Analyze content needs for a specific claim
  fastify.post("/analyze-claim", async (request, reply) => {
    const bodyResult = analyzeClaimBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid analyze-claim payload",
        issues: bodyResult.error.issues,
      };
    }
    const { claim, context } = bodyResult.data;

    try {
      const needs = await contentNeedsAnalyzer.analyzeClaimNeeds(
        claim,
        context,
      );
      return { success: true, data: needs };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to analyze claim needs" };
    }
  });

  // Analyze content needs for entire experience
  fastify.post("/analyze-experience/:experienceId", async (request, reply) => {
    const paramsResult = experienceIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid experience id",
        issues: paramsResult.error.issues,
      };
    }
    const { experienceId } = paramsResult.data;

    try {
      const analyses =
        await contentNeedsAnalyzer.analyzeExperienceNeeds(experienceId);
      return { success: true, data: analyses };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to analyze experience needs" };
    }
  });

  // Generate content based on analyzed needs
  fastify.post("/generate-content/:claimId", async (request, reply) => {
    const paramsResult = generateContentParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid claim id",
        issues: paramsResult.error.issues,
      };
    }
    const bodyResult = generateContentBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid generate-content payload",
        issues: bodyResult.error.issues,
      };
    }
    const { claimId } = paramsResult.data;
    const { needs } = bodyResult.data;

    try {
      const content = await contentNeedsAnalyzer.generateContentForClaim(
        claimId,
        needs as ContentNeeds,
      );
      return { success: true, data: content };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to generate content" };
    }
  });

  // Get content needs analysis history
  fastify.get("/experience/:experienceId/history", async (request, reply) => {
    const paramsResult = experienceIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid experience id",
        issues: paramsResult.error.issues,
      };
    }
    const { experienceId } = paramsResult.data;

    try {
      const history =
        await contentNeedsAnalyzer.getAnalysisHistory(experienceId);
      return { success: true, data: history };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to get analysis history" };
    }
  });

  // Batch analyze multiple claims
  fastify.post("/batch-analyze", async (request, reply) => {
    const bodyResult = batchAnalyzeBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid batch-analyze payload",
        issues: bodyResult.error.issues,
      };
    }
    const { claims, context } = bodyResult.data;

    try {
      const results = await Promise.all(
        claims.map(async (claim) => {
          const needs = await contentNeedsAnalyzer.analyzeClaimNeeds(
            claim,
            context,
          );
          return { claimId: claim.id, needs };
        }),
      );
      return { success: true, data: results };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to batch analyze claims" };
    }
  });

  if (contentDriftDetector) {
    fastify.get("/drift/thresholds", async (request, reply) => {
      const tenantId = request.headers["x-tenant-id"] as string;

      try {
        const thresholds = await contentDriftDetector.adjustThresholds(tenantId);
        return { success: true, data: thresholds };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to compute adaptive thresholds" };
      }
    });

    fastify.post("/drift/scan/:experienceId", async (request, reply) => {
      const paramsResult = experienceIdParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid experience id",
          issues: paramsResult.error.issues,
        };
      }
      const { experienceId } = paramsResult.data;
      const tenantId = request.headers["x-tenant-id"] as string;

      try {
        const result = await contentDriftDetector.scanExperienceAdaptive(
          tenantId,
          experienceId,
        );
        return { success: true, data: result };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to scan content drift" };
      }
    });

    if (qualityPipeline) {
      fastify.post("/drift/scan/:experienceId/apply-quality", async (request, reply) => {
        const paramsResult = experienceIdParamsSchema.safeParse(request.params);
        if (!paramsResult.success) {
          reply.code(400);
          return {
            success: false,
            error: "Invalid experience id",
            issues: paramsResult.error.issues,
          };
        }
        const { experienceId } = paramsResult.data;
        const tenantId = request.headers["x-tenant-id"] as string;

        try {
          const result = await contentDriftDetector.scanExperienceAdaptive(
            tenantId,
            experienceId,
          );
          const shouldApplyQuality =
            result.signals.length > 0 ||
            result.insights.length > 0;
          const predictions = shouldApplyQuality
            ? await qualityPipeline.applyPredictionsForExperience(
                tenantId,
                experienceId,
              )
            : [];

          return {
            success: true,
            data: {
              drift: result,
              qualityPredictionsApplied: predictions.length,
              predictions,
            },
          };
        } catch (error) {
          fastify.log.error(error);
          reply.code(500);
          return {
            success: false,
            error: "Failed to scan content drift and apply quality mitigation",
          };
        }
      });
    }
  }
}
