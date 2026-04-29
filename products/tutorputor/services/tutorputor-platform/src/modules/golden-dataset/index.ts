/**
 * Golden Dataset Regression Tests Module
 *
 * Baseline generated artifacts and detect regressions in content quality.
 *
 * @doc.type module
 * @doc.purpose Golden dataset regression testing module
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { GoldenDatasetService } from "./GoldenDatasetService.js";
import { z } from "zod";

const addGoldenEntrySchema = z.object({
  moduleId: z.string(),
  inputType: z.enum(["question", "concept", "simulation"]),
  input: z.string(),
  expectedOutput: z.string(),
  qualityMetrics: z.string(), // JSON string of { clarity, accuracy, completeness }
});

export const goldenDatasetModule: FastifyPluginAsync = async (app) => {
  const goldenService = new GoldenDatasetService(app.prisma as any);
  app.decorate("goldenService", goldenService);

  // POST /api/v1/golden-dataset/entries - Add a golden dataset entry
  app.post("/api/v1/golden-dataset/entries", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = addGoldenEntrySchema.parse(request.body);
    const entry = await goldenService.addGoldenEntry(body);

    return reply.send({ success: true, entry });
  });

  // POST /api/v1/golden-dataset/test/:moduleId - Run regression test for a module
  app.post("/api/v1/golden-dataset/test/:moduleId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { moduleId } = request.params as { moduleId: string };
    const results = await goldenService.runRegressionTest(moduleId);

    return reply.send({ results });
  });

  // GET /api/v1/golden-dataset/history/:moduleId - Get regression test history
  app.get("/api/v1/golden-dataset/history/:moduleId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { moduleId } = request.params as { moduleId: string };
    const limit = parseInt((request.query as { limit?: string }).limit || "50", 10);
    const history = await goldenService.getRegressionTestHistory(moduleId, limit);

    return reply.send({ history });
  });

  // GET /api/v1/golden-dataset/stats/:moduleId - Get regression statistics
  app.get("/api/v1/golden-dataset/stats/:moduleId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { moduleId } = request.params as { moduleId: string };
    const stats = await goldenService.getRegressionStats(moduleId);

    return reply.send(stats);
  });

  app.log.info("✅ Golden dataset module registered");
};
