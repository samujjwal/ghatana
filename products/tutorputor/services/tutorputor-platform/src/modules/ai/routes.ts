/**
 * AI Routes - HTTP endpoints for AI functionality
 *
 * @doc.type routes
 * @doc.purpose AI HTTP endpoint handlers
 * @doc.layer platform
 * @doc.pattern Routes
 */
import type { FastifyInstance } from "fastify";
import type { AIProxyService } from "@ghatana/tutorputor-contracts/v1/services";
import type { ModuleId } from "@ghatana/tutorputor-contracts/v1/types";
import { AIContentGenerationService } from "./AIContentGenerationService.js";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../utils/request-helpers.js";
import type { AiRegistryClient } from "../../clients/ai-registry.client.js";

interface AIRouteDeps {
  aiProxyService: AIProxyService & {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any;
    generateQuestionsFromContent?: (args: {
      tenantId: string;
      moduleId: string;
      count: number;
      difficulty: "easy" | "medium" | "hard";
    }) => Promise<
      Array<{
        question: string;
        options?: string[];
        correctAnswer: string;
        explanation: string;
      }>
    >;
  };
  /** Optional: Platform AI Registry client for model version discovery */
  aiRegistryClient?: AiRegistryClient | null;
}

export async function registerAIRoutes(
  app: FastifyInstance,
  deps: AIRouteDeps,
): Promise<void> {
  const aiContentService = new AIContentGenerationService(deps.aiProxyService);

  // AI Tutor query endpoint
  app.post("/tutor/query", async (req, reply) => {
    const tenantId = getTenantId(req);
    const userId = getUserId(req);
    const { moduleId, question, locale } = req.body as {
      moduleId?: ModuleId;
      question: string;
      locale?: string;
    };

    if (!question || question.trim().length === 0) {
      return reply.status(400).send({ error: "Question is required" });
    }

    // Resolve active model from platform AI Registry for observability & routing
    let activeModelId: string | undefined;
    if (deps.aiRegistryClient) {
      try {
        const model = await deps.aiRegistryClient.findActiveModel(
          String(tenantId),
          "tutoring-llm",
        );
        if (model) {
          activeModelId = model.id;
          app.log.debug(
            `[AI] Active model resolved: ${model.id} (${model.name})`,
          );
        }
      } catch {
        app.log.warn("[AI] Could not resolve active model from registry");
      }
    }

    app.log.info(
      `[AI] Tutor query from user ${String(userId)}: ${question.substring(0, 50)}...`,
    );

    const response = await deps.aiProxyService.handleTutorQuery({
      tenantId,
      userId,
      moduleId,
      question,
      locale,
    });

    if (activeModelId) {
      void reply.header("x-active-model-id", activeModelId);
    }
    return reply.send({ response });
  });

  // AI-generated questions from module content
  app.post("/generate-questions", async (req, reply) => {
    const tenantId = getTenantId(req);
    const {
      moduleId,
      count = 5,
      difficulty = "medium",
    } = req.body as {
      moduleId: ModuleId;
      count?: number;
      difficulty?: "easy" | "medium" | "hard";
    };

    if (!moduleId) {
      return reply.status(400).send({ error: "moduleId is required" });
    }

    if (!deps.aiProxyService.generateQuestionsFromContent) {
      return reply.status(501).send({
        error: "Question generation not available",
        questions: [],
      });
    }

    try {
      const questions = await deps.aiProxyService.generateQuestionsFromContent({
        tenantId: String(tenantId),
        moduleId: String(moduleId),
        count: Math.min(count, 10),
        difficulty,
      });

      return reply.send({ questions });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);

      // Return structured error codes for client handling
      if (message.startsWith("AI_NOT_CONFIGURED:")) {
        return reply.status(503).send({
          error: "AI service not configured",
          code: "AI_NOT_CONFIGURED",
          details: message.replace("AI_NOT_CONFIGURED: ", ""),
          questions: [],
        });
      }

      if (message.startsWith("NO_CONTENT:")) {
        return reply.status(422).send({
          error: "No content available for question generation",
          code: "NO_CONTENT",
          details: message.replace("NO_CONTENT: ", ""),
          questions: [],
        });
      }

      if (message.startsWith("GENERATION_FAILED:")) {
        return reply.status(500).send({
          error: "Question generation failed",
          code: "GENERATION_FAILED",
          details: message.replace("GENERATION_FAILED: ", ""),
          questions: [],
        });
      }

      // Unknown error
      return reply.status(500).send({
        error: message,
        code: "UNKNOWN_ERROR",
        questions: [],
      });
    }
  });

  // AI-powered concept generation from name
  app.post("/generate-concept", async (req, reply) => {
    try {
      requireRole(req, ["admin"]);
      const tenantId = getTenantId(req);
      const { conceptName, domain } = req.body as {
        conceptName: string;
        domain: string;
      };

      if (!conceptName || !domain) {
        return reply
          .status(400)
          .send({ error: "conceptName and domain are required" });
      }

      app.log.info(`[AI] Generating concept: ${conceptName} (${domain})`);

      const generated = await aiContentService.generateConceptFromName(
        conceptName,
        domain,
        String(tenantId),
      );

      return reply.send({ success: true, concept: generated });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error";

      if (errorMessage.includes("Forbidden")) {
        return reply.status(403).send({ error: errorMessage });
      }

      app.log.error(`[AI] Concept generation failed: ${errorMessage}`);
      return reply.status(500).send({ error: errorMessage });
    }
  });

  // AI-powered simulation manifest generation
  app.post("/generate-simulation", async (req, reply) => {
    try {
      requireRole(req, ["admin"]);
      const tenantId = getTenantId(req);
      const { description, conceptName, domain } = req.body as {
        description: string;
        conceptName: string;
        domain: string;
      };

      if (!description || !conceptName || !domain) {
        return reply.status(400).send({
          error: "description, conceptName, and domain are required",
        });
      }

      app.log.info(
        `[AI] Generating simulation: ${description} for concept ${conceptName}`,
      );

      const generated = await aiContentService.generateSimulationManifest(
        description,
        conceptName,
        domain,
        String(tenantId),
      );

      return reply.send({ success: true, simulation: generated });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error";

      if (errorMessage.includes("Forbidden")) {
        return reply.status(403).send({ error: errorMessage });
      }

      app.log.error(`[AI] Simulation generation failed: ${errorMessage}`);
      return reply.status(500).send({ error: errorMessage });
    }
  });

  app.log.info(
    "[AI] Routes registered: /tutor/query, /generate-questions, /generate-concept, /generate-simulation",
  );
}
