/**
 * Content Evaluation Module
 *
 * Automated validation of AI-generated educational claims against curriculum/citation corpus.
 *
 * @doc.type module
 * @doc.purpose Content correctness evaluation module
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { ContentCorrectnessEvaluator } from "./ContentCorrectnessEvaluator.js";
import { z } from "zod";

const evaluateContentSchema = z.object({
  content: z.string(),
  moduleId: z.string(),
});

export const contentEvaluationModule: FastifyPluginAsync = async (app) => {
  const evaluator = new ContentCorrectnessEvaluator(app.prisma as any);
  app.decorate("contentEvaluator", evaluator);

  // POST /api/v1/content-evaluation/evaluate - Evaluate content for correctness
  app.post("/api/v1/content-evaluation/evaluate", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = evaluateContentSchema.parse(request.body);
    const results = await evaluator.evaluateContent(body.content, body.moduleId);

    return reply.send({ results });
  });

  // GET /api/v1/content-evaluation/stats/:moduleId - Get evaluation statistics for a module
  app.get("/api/v1/content-evaluation/stats/:moduleId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { moduleId } = request.params as { moduleId: string };
    const stats = await evaluator.getModuleEvaluationStats(moduleId);

    return reply.send(stats);
  });

  app.log.info("✅ Content evaluation module registered");
};
