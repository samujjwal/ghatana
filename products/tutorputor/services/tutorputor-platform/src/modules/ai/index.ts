/**
 * AI Module - Consolidates all AI-related functionality
 *
 * Provides:
 * - /api/v1/ai/tutor/query - AI tutor query endpoint
 * - /api/v1/ai/generate-questions - AI-generated questions
 * - /api/v1/ai/generate-concept - AI-powered concept generation
 * - /api/v1/ai/generate-simulation - AI-powered simulation manifest generation
 *
 * @doc.type module
 * @doc.purpose AI integration endpoints for tutor, content generation
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import type { AIProxyService } from "@tutorputor/contracts/v1/services";
import { registerAIRoutes } from "./routes.js";
import { OllamaAIProxyService } from "./OllamaAIProxyService.js";
import { AIHealthCheckService } from "./AIHealthCheckService.js";
import { AICacheService } from "./AICacheService.js";
import { aiRegistryClient } from "../../clients/ai-registry.client.js";

/**
 * AI module plugin - registers all AI-related routes
 */
export const aiModule: FastifyPluginAsync = async (app) => {
  app.log.info("Initializing AI module...");

  // Create AI Proxy Service instance
  // Uses OLLAMA_BASE_URL or tutorputor-ai-proxy service URL
  const aiProxyBaseUrl =
    process.env.AI_PROXY_URL ||
    process.env.OLLAMA_BASE_URL ||
    "http://localhost:3300";

  const aiProxyService = new OllamaAIProxyService(aiProxyBaseUrl);

  app.log.info(`AI Proxy configured with base URL: ${aiProxyBaseUrl}`);

  // Create AI Health Check Service
  const aiHealthCheckService = new AIHealthCheckService();
  app.decorate('aiHealthCheckService', aiHealthCheckService);

  // Create AI Cache Service
  const aiCacheService = new AICacheService(
    app.redis,
    parseInt(process.env.AI_CACHE_TTL || '3600000', 10)
  );
  app.decorate('aiCacheService', aiCacheService);

  // Register health check endpoint
  app.get('/api/v1/ai/health', async (request, reply) => {
    const healthStatus = aiHealthCheckService.getHealthStatus();
    return reply.send({
      healthy: healthStatus.every(s => s.healthy),
      services: healthStatus,
    });
  });

  // Register cache stats endpoint
  app.get('/api/v1/ai/cache/stats', async (request, reply) => {
    const stats = aiCacheService.getStats();
    return reply.send(stats);
  });

  if (!aiRegistryClient) {
    app.log.warn(
      "AI Registry client not configured (AI_REGISTRY_URL unset) — model discovery disabled",
    );
  }

  // Register AI routes with the service and optional registry client
  await registerAIRoutes(app, {
    aiProxyService: aiProxyService as AIProxyService,
    aiRegistryClient,
  });

  app.log.info("✅ AI module routes registered");
};
