/**
 * Content Generation Benchmark Module
 *
 * Performance baseline for queue processing, gRPC throughput, and LLM latency.
 *
 * @doc.type module
 * @doc.purpose Content generation throughput benchmarking module
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { ContentGenerationBenchmarkService } from "./ContentGenerationBenchmarkService.js";

export const contentGenerationBenchmarkModule: FastifyPluginAsync = async (app) => {
  const benchmarkService = new ContentGenerationBenchmarkService(app.prisma as any);
  app.decorate("contentGenerationBenchmarkService", benchmarkService);

  // POST /api/v1/content-generation-benchmark/run - Run benchmark
  app.post("/api/v1/content-generation-benchmark/run", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const result = await benchmarkService.runBenchmark(tenantId);

    return reply.send(result);
  });

  // POST /api/v1/content-generation-benchmark/:benchmarkId/baseline - Set as baseline
  app.post("/api/v1/content-generation-benchmark/:benchmarkId/baseline", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { benchmarkId } = request.params as { benchmarkId: string };
    await benchmarkService.setAsBaseline(tenantId, benchmarkId);

    return reply.send({ success: true });
  });

  // GET /api/v1/content-generation-benchmark/history - Get benchmark history
  app.get("/api/v1/content-generation-benchmark/history", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const query = request.query as { limit?: string };
    const limit = query.limit ? parseInt(query.limit, 10) : 50;
    const history = await benchmarkService.getBenchmarkHistory(tenantId, limit);

    return reply.send({ history });
  });

  // GET /api/v1/content-generation-benchmark/stats - Get benchmark statistics
  app.get("/api/v1/content-generation-benchmark/stats", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const stats = await benchmarkService.getBenchmarkStats(tenantId);

    return reply.send(stats);
  });

  app.log.info("✅ Content generation benchmark module registered");
};
