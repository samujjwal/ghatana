/**
 * AI Proxy Service - Standalone Server
 * 
 * Starts a Fastify server on port 3300 that provides AI tutor endpoints.
 * 
 * @doc.type server
 * @doc.purpose Standalone AI Proxy service with HTTP endpoints
 * @doc.layer product
 * @doc.pattern Server
 */

import Fastify from "fastify";
import { createAIProxyService } from "./service";

const PORT = parseInt(process.env.AI_PROXY_PORT || "3300", 10);
const HOST = process.env.AI_PROXY_HOST || "127.0.0.1";

async function start() {
  try {
    // Create Fastify instance
    const fastify = Fastify({
      logger: {
        level: process.env.LOG_LEVEL || "info",
      },
    });

    // Create AI Proxy Service
    const aiService = createAIProxyService({
      openaiApiKey: process.env.OPENAI_API_KEY,
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      useOllama: process.env.USE_OLLAMA === "true",
      ollamaBaseUrl: process.env.OLLAMA_BASE_URL || "http://localhost:11434",
      ollamaModel: process.env.OLLAMA_MODEL || "mistral",
    });

    // Health check endpoint
    fastify.get("/health", async (request, reply) => {
      const health = await aiService.getHealthStatus();
      return {
        status: "ok",
        timestamp: new Date().toISOString(),
        service: "ai-proxy",
        ai: health,
      };
    });

    // Root endpoint
    fastify.get("/", async (request, reply) => {
      return {
        service: "TutorPutor AI Proxy",
        version: "0.1.0",
        endpoints: {
          health: "/health",
          docs: "/docs",
        },
      };
    });

    // Documentation endpoint
    fastify.get("/docs", async (request, reply) => {
      return {
        service: "TutorPutor AI Proxy Service",
        description: "Provides AI-powered tutoring endpoints with RAG support",
        baseUrl: `http://${HOST}:${PORT}`,
        endpoints: [
          {
            path: "GET /health",
            description: "Health check endpoint",
            response: "{ status: string; ai: object }",
          },
          {
            path: "GET /",
            description: "Service info endpoint",
            response: "{ service: string; version: string; endpoints: object }",
          },
        ],
        configuration: {
          AI_PROXY_PORT: PORT,
          AI_PROXY_HOST: HOST,
          USE_OLLAMA: process.env.USE_OLLAMA || "false",
          OPENAI_API_KEY: process.env.OPENAI_API_KEY ? "configured" : "not set",
          OLLAMA_MODEL: process.env.OLLAMA_MODEL || "mistral",
        },
      };
    });

    // AI Generate endpoint - called by API Gateway
    fastify.post<{ Body: { tenantId: string; userId: string; question: string; locale?: string; moduleId?: string } }>("/api/ai/generate", async (request, reply) => {
      const { tenantId, userId, question, locale, moduleId } = request.body;
      
      if (!question) {
        return reply.code(400).send({ error: "question is required" });
      }

      try {
        const response = await aiService.handleTutorQuery({
          tenantId: tenantId as any,
          userId: userId as any,
          question,
          locale,
          moduleId: moduleId as any,
        });
        
        return {
          response: response.answer,
          status: "success",
          timestamp: new Date().toISOString(),
          data: response,
        };
      } catch (error) {
        fastify.log.error("Error in /api/ai/generate:", error);
        return reply.code(500).send({
          error: "Failed to generate AI response",
          message: error instanceof Error ? error.message : String(error),
        });
      }
    });

    // Start server
    await fastify.listen({ port: PORT, host: HOST });

    console.log(`\n${"=".repeat(60)}`);
    console.log(`✅ AI Proxy Service started successfully`);
    console.log(`${"=".repeat(60)}`);
    console.log(`Service URL: http://${HOST}:${PORT}`);
    console.log(`Health Check: http://${HOST}:${PORT}/health`);
    console.log(`Documentation: http://${HOST}:${PORT}/docs`);
    console.log(`${"=".repeat(60)}\n`);
  } catch (error) {
    console.error("Failed to start AI Proxy Service:", error);
    process.exit(1);
  }
}

// Start the server
start().catch((error) => {
  console.error("Unexpected error:", error);
  process.exit(1);
});
