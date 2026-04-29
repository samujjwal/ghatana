/**
 * @doc.type module
 * @doc.purpose Knowledge Base Integration API routes
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance } from "fastify";
import type { KnowledgeBaseServiceImpl } from "./service";
import { z } from "zod";

const verifyFactBodySchema = z.object({
  claim: z.string().min(1),
  domain: z.string().min(1),
  context: z
    .object({
      gradeRange: z.string().min(1).optional(),
      subject: z.string().min(1).optional(),
      relatedConcepts: z.array(z.string().min(1)).optional(),
    })
    .optional(),
});

const conceptSearchQuerySchema = z.object({
  query: z.string().min(1),
  domain: z.string().min(1),
});

const conceptParamsSchema = z.object({
  concept: z.string().min(1),
});

const conceptContextQuerySchema = z.object({
  domain: z.string().min(1),
  gradeRange: z.string().min(1).optional(),
});

const curriculumQuerySchema = z.object({
  domain: z.string().min(1),
});

const validateBodySchema = z.object({
  content: z.string().min(1),
  contentType: z.enum(["claim", "example", "explanation", "task"]),
  domain: z.string().min(1),
  gradeRange: z.string().min(1),
  context: z
    .object({
      learningObjectives: z.array(z.string().min(1)).optional(),
      prerequisites: z.array(z.string().min(1)).optional(),
    })
    .optional(),
});

const batchVerifyBodySchema = z.object({
  claims: z
    .array(
      z.object({
        claim: z.string().min(1),
        domain: z.string().min(1),
      }),
    )
    .min(1),
});

export function registerKnowledgeBaseRoutes(
  fastify: FastifyInstance,
  knowledgeBaseService: KnowledgeBaseServiceImpl,
): void {
  // Verify a factual claim
  fastify.post("/api/knowledge-base/verify-fact", async (request, reply) => {
    const factRequestResult = verifyFactBodySchema.safeParse(request.body);
    if (!factRequestResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid verify-fact payload",
        issues: factRequestResult.error.issues,
      };
    }
    const factRequest = factRequestResult.data;
    const verifyFactRequest = {
      claim: factRequest.claim,
      domain: factRequest.domain,
      ...(factRequest.context
        ? {
            context: {
              ...(factRequest.context.gradeRange
                ? { gradeRange: factRequest.context.gradeRange }
                : {}),
              ...(factRequest.context.subject
                ? { subject: factRequest.context.subject }
                : {}),
              ...(factRequest.context.relatedConcepts
                ? { relatedConcepts: factRequest.context.relatedConcepts }
                : {}),
            },
          }
        : {}),
    };

    try {
      const result = await knowledgeBaseService.verifyFact(verifyFactRequest);
      return { success: true, data: result };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to verify fact" };
    }
  });

  // Search for concepts
  fastify.get("/api/knowledge-base/concepts/search", async (request, reply) => {
    const queryResult = conceptSearchQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid concept search query",
        issues: queryResult.error.issues,
      };
    }
    const { query, domain } = queryResult.data;

    try {
      const concepts = await knowledgeBaseService.searchConcept(query, domain);
      return { success: true, data: concepts };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to search concepts" };
    }
  });

  // Find examples for a concept
  fastify.get(
    "/api/knowledge-base/examples/:concept",
    async (request, reply) => {
      const paramsResult = conceptParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid concept parameter",
          issues: paramsResult.error.issues,
        };
      }
      const queryResult = conceptContextQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid examples query",
          issues: queryResult.error.issues,
        };
      }

      const { concept } = paramsResult.data;
      const { domain, gradeRange } = queryResult.data;

      try {
        const examples = await knowledgeBaseService.findExamples(
          concept,
          domain,
          gradeRange,
        );
        return { success: true, data: examples };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to find examples" };
      }
    },
  );

  // Get curriculum alignment
  fastify.get(
    "/api/knowledge-base/curriculum/:concept",
    async (request, reply) => {
      const paramsResult = conceptParamsSchema.safeParse(request.params);
      if (!paramsResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid concept parameter",
          issues: paramsResult.error.issues,
        };
      }
      const queryResult = curriculumQuerySchema.safeParse(request.query);
      if (!queryResult.success) {
        reply.code(400);
        return {
          success: false,
          error: "Invalid curriculum query",
          issues: queryResult.error.issues,
        };
      }

      const { concept } = paramsResult.data;
      const { domain } = queryResult.data;

      try {
        const standards = await knowledgeBaseService.getCurriculumAlignment(
          concept,
          domain,
        );
        return { success: true, data: standards };
      } catch (error) {
        fastify.log.error(error);
        reply.code(500);
        return { success: false, error: "Failed to get curriculum alignment" };
      }
    },
  );

  // Validate content
  fastify.post("/api/knowledge-base/validate", async (request, reply) => {
    const validationRequestResult = validateBodySchema.safeParse(request.body);
    if (!validationRequestResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid validate payload",
        issues: validationRequestResult.error.issues,
      };
    }
    const validationRequest = validationRequestResult.data;
    const validateRequest = {
      content: validationRequest.content,
      contentType: validationRequest.contentType,
      domain: validationRequest.domain,
      gradeRange: validationRequest.gradeRange,
      ...(validationRequest.context
        ? {
            context: {
              ...(validationRequest.context.learningObjectives
                ? { learningObjectives: validationRequest.context.learningObjectives }
                : {}),
              ...(validationRequest.context.prerequisites
                ? { prerequisites: validationRequest.context.prerequisites }
                : {}),
            },
          }
        : {}),
    };

    try {
      const result =
        await knowledgeBaseService.validateContent(validateRequest);
      return { success: true, data: result };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to validate content" };
    }
  });

  // Batch fact verification
  fastify.post("/api/knowledge-base/batch-verify", async (request, reply) => {
    const claimsResult = batchVerifyBodySchema.safeParse(request.body);
    if (!claimsResult.success) {
      reply.code(400);
      return {
        success: false,
        error: "Invalid batch verify payload",
        issues: claimsResult.error.issues,
      };
    }
    const { claims } = claimsResult.data;

    try {
      const results = [];
      for (const claimRequest of claims) {
        const result = await knowledgeBaseService.verifyFact(claimRequest);
        results.push({ claim: claimRequest.claim, result });
      }
      return { success: true, data: results };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to batch verify facts" };
    }
  });

  // Get knowledge base statistics
  fastify.get("/api/knowledge-base/stats", async (_request, reply) => {
    try {
      const prisma = (knowledgeBaseService as any).prisma;
      
      // Query actual statistics from the database
      const [totalConcepts, totalSources, evidenceBundles] = await Promise.all([
        // Count total concepts from learner mastery
        prisma.learnerMastery.count({
          distinct: ['conceptId'],
        }),
        // Count total sources from evidence bundles
        prisma.evidenceBundleMetadata.count(),
        // Get evidence bundles for confidence calculation
        prisma.evidenceBundleMetadata.findMany({
          select: { bundleConfidence: true },
          take: 1000,
        }),
      ]);

      // Calculate average confidence
      const averageConfidence = evidenceBundles.length > 0
        ? evidenceBundles.reduce((sum: number, bundle: { bundleConfidence: number }) => sum + bundle.bundleConfidence, 0) / evidenceBundles.length
        : 0;

      // Get unique domains from modules
      const modules = await prisma.module.findMany({
        select: { domain: true },
        distinct: ['domain'],
      });
      const domains = modules.map((m: { domain: string }) => m.domain.toLowerCase());

      // Get last updated timestamp from most recent evidence bundle
      const lastUpdated = await prisma.evidenceBundleMetadata.findFirst({
        orderBy: { generatedAt: 'desc' },
        select: { generatedAt: true },
      });

      return {
        success: true,
        data: {
          totalConcepts,
          totalSources,
          averageConfidence,
          lastUpdated: lastUpdated?.generatedAt || new Date(),
          domains,
        },
      };
    } catch (error) {
      fastify.log.error(error);
      reply.code(500);
      return { success: false, error: "Failed to get statistics" };
    }
  });

  // Health check
  fastify.get("/api/knowledge-base/health", async (_request, _reply) => {
    return {
      success: true,
      status: "healthy",
      service: "Knowledge Base Integration",
      timestamp: new Date(),
    };
  });
}
