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
import { registerAIRoutes } from "./routes.js";
import { OllamaAIProxyService } from "./OllamaAIProxyService.js";

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

  // Register AI routes with the service
  await registerAIRoutes(app, { aiProxyService });

  app.log.info("✅ AI module routes registered");
};
