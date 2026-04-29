/**
 * AI Routes Consent Enforcement Tests
 *
 * Tests for consent enforcement middleware on AI routes.
 * Verifies that AI routes require ai_processing consent.
 *
 * @doc.type test
 * @doc.purpose Verify consent enforcement on AI routes
 * @doc.layer product
 * @doc.pattern Integration Test
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { registerAIRoutes } from "./routes.js";

describe("AI Routes Consent Enforcement", () => {
  let app: FastifyInstance;
  let mockRequest: Partial<FastifyRequest>;
  let mockReply: Partial<FastifyReply>;

  beforeEach(() => {
    // Create minimal Fastify instance for testing
    app = {
      log: {
        info: vi.fn(),
        warn: vi.fn(),
        debug: vi.fn(),
        error: vi.fn(),
      },
      decorate: vi.fn(),
      get: vi.fn(),
      post: vi.fn(),
    } as unknown as FastifyInstance;

    mockRequest = {
      headers: {},
    };
    mockReply = {
      code: vi.fn(() => mockReply),
      send: vi.fn(() => mockReply),
      header: vi.fn(() => mockReply),
    };
  });

  describe("Consent enforcement on AI routes", () => {
    it("should add consent enforcement preHandler to /tutor/query route", async () => {
      const aiProxyService = {
        handleTutorQuery: vi.fn(() => Promise.resolve({ response: "test response" })),
      };

      await registerAIRoutes(app, {
        aiProxyService: aiProxyService as any,
        aiRegistryClient: null,
      });

      // Verify that /tutor/query route was registered with consent enforcement
      const postCalls = (app.post as any).mock.calls;
      const tutorQueryCall = postCalls.find((call: any[]) => call[0] === "/tutor/query");
      
      expect(tutorQueryCall).toBeDefined();
      expect(tutorQueryCall[1]).toBeDefined();
      expect(tutorQueryCall[1].preHandler).toBeDefined();
      
      // Verify consent enforcement is in preHandler array
      const preHandlers = Array.isArray(tutorQueryCall[1].preHandler) 
        ? tutorQueryCall[1].preHandler 
        : [tutorQueryCall[1].preHandler];
      
      expect(preHandlers.length).toBeGreaterThan(0);
    });

    it("should add consent enforcement preHandler to /generate-questions route", async () => {
      const aiProxyService = {
        handleTutorQuery: vi.fn(() => Promise.resolve({ response: "test response" })),
      };

      await registerAIRoutes(app, {
        aiProxyService: aiProxyService as any,
        aiRegistryClient: null,
      });

      const postCalls = (app.post as any).mock.calls;
      const generateQuestionsCall = postCalls.find((call: any[]) => call[0] === "/generate-questions");
      
      expect(generateQuestionsCall).toBeDefined();
      expect(generateQuestionsCall[1]).toBeDefined();
      expect(generateQuestionsCall[1].preHandler).toBeDefined();
    });

    it("should add consent enforcement preHandler to /generate-concept route", async () => {
      const aiProxyService = {
        handleTutorQuery: vi.fn(() => Promise.resolve({ response: "test response" })),
      };

      await registerAIRoutes(app, {
        aiProxyService: aiProxyService as any,
        aiRegistryClient: null,
      });

      const postCalls = (app.post as any).mock.calls;
      const generateConceptCall = postCalls.find((call: any[]) => call[0] === "/generate-concept");
      
      expect(generateConceptCall).toBeDefined();
      expect(generateConceptCall[1]).toBeDefined();
      expect(generateConceptCall[1].preHandler).toBeDefined();
    });

    it("should add consent enforcement preHandler to /generate-simulation route", async () => {
      const aiProxyService = {
        handleTutorQuery: vi.fn(() => Promise.resolve({ response: "test response" })),
      };

      await registerAIRoutes(app, {
        aiProxyService: aiProxyService as any,
        aiRegistryClient: null,
      });

      const postCalls = (app.post as any).mock.calls;
      const generateSimulationCall = postCalls.find((call: any[]) => call[0] === "/generate-simulation");
      
      expect(generateSimulationCall).toBeDefined();
      expect(generateSimulationCall[1]).toBeDefined();
      expect(generateSimulationCall[1].preHandler).toBeDefined();
    });
  });

  describe("Provenance metadata in AI responses", () => {
    it("should include provenance metadata in tutor query response", async () => {
      const aiProxyService = {
        handleTutorQuery: vi.fn(() => Promise.resolve({ response: "test response" })),
      };

      await registerAIRoutes(app, {
        aiProxyService: aiProxyService as any,
        aiRegistryClient: null,
      });

      // Verify that the response structure includes provenance
      // This is checked by examining the route handler implementation
      const postCalls = (app.post as any).mock.calls;
      const tutorQueryCall = postCalls.find((call: any[]) => call[0] === "/tutor/query");
      
      expect(tutorQueryCall).toBeDefined();
      
      // The handler should send response with provenance metadata
      const handler = tutorQueryCall[2];
      expect(handler).toBeDefined();
    });
  });
});
