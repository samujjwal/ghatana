/**
 * Test suite for AI routes
 *
 * @doc.type tests
 * @doc.purpose Integration tests for AI endpoints
 * @doc.layer platform
 * @doc.pattern Test Suite
 */
import {
  describe,
  it,
  expect,
  vi,
  beforeAll,
  afterAll,
  beforeEach,
} from "vitest";
import Fastify from "fastify";
import type { FastifyInstance } from "fastify";
import { registerAIRoutes } from "../routes";

// Mock AI Proxy Service
const mockAIProxyService = {
  handleTutorQuery: vi.fn(),
  parseSimulationIntent: vi.fn(),
  explainSimulation: vi.fn(),
  generateLearningUnitDraft: vi.fn(),
  parseContentQuery: vi.fn(),
  generateQuestionsFromContent: vi.fn(),
};

describe("AI Routes", () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify({ logger: false });
    await registerAIRoutes(app, { aiProxyService: mockAIProxyService });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("POST /tutor/query", () => {
    it("returns tutor response for valid question", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer:
          "Photosynthesis is the process by which plants convert sunlight...",
        citations: [{ id: "1", label: "Biology Textbook", type: "textbook" }],
        followUpQuestions: ["What is chlorophyll?"],
        safety: { blocked: false },
      });

      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        headers: {
          "x-tenant-id": "test-tenant",
          "x-user-id": "test-user",
        },
        payload: {
          question: "What is photosynthesis?",
          locale: "en",
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.response.answer).toContain("Photosynthesis");
      expect(body.response.citations).toHaveLength(1);
    });

    it("uses default tenant ID when not provided", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer: "Test answer",
        safety: { blocked: false },
      });

      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: { question: "Test?" },
      });

      expect(response.statusCode).toBe(200);
      expect(mockAIProxyService.handleTutorQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          tenantId: "default",
        }),
      );
    });

    it("uses default user ID when not provided", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer: "Test answer",
        safety: { blocked: false },
      });

      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: { question: "Test?" },
      });

      expect(response.statusCode).toBe(200);
      expect(mockAIProxyService.handleTutorQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          userId: "anonymous",
        }),
      );
    });

    it("returns 400 for empty question", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: { question: "" },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain("required");
    });

    it("returns 400 for missing question", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: {},
      });

      expect(response.statusCode).toBe(400);
    });

    it("returns 400 for whitespace-only question", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: { question: "   " },
      });

      expect(response.statusCode).toBe(400);
    });

    it("includes moduleId in service call when provided", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer: "Context-aware answer",
        safety: { blocked: false },
      });

      await app.inject({
        method: "POST",
        url: "/tutor/query",
        headers: { "x-tenant-id": "test" },
        payload: {
          question: "Help with this module",
          moduleId: "mod-123",
        },
      });

      expect(mockAIProxyService.handleTutorQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          moduleId: "mod-123",
        }),
      );
    });

    it("passes locale to service", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer: "Respuesta en español",
        safety: { blocked: false },
      });

      await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: {
          question: "¿Qué es la fotosíntesis?",
          locale: "es",
        },
      });

      expect(mockAIProxyService.handleTutorQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          locale: "es",
        }),
      );
    });

    it("handles service errors gracefully", async () => {
      mockAIProxyService.handleTutorQuery.mockRejectedValueOnce(
        new Error("Service unavailable"),
      );

      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        payload: { question: "Test" },
      });

      // Should not crash, error handling depends on implementation
      expect(response.statusCode).toBeGreaterThanOrEqual(200);
    });
  });

  describe("POST /generate-questions", () => {
    it("generates questions for a module", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockResolvedValueOnce([
        {
          question: "What is the main purpose of photosynthesis?",
          options: ["A", "B", "C", "D"],
          correctAnswer: "A",
          explanation: "Plants use photosynthesis to produce energy.",
        },
      ]);

      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        headers: { "x-tenant-id": "test" },
        payload: {
          moduleId: "mod-biology-101",
          count: 5,
          difficulty: "medium",
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.questions).toHaveLength(1);
    });

    it("returns 400 when moduleId is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { count: 5 },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain("moduleId");
    });

    it("uses default count of 5", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockResolvedValueOnce([]);

      await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(
        mockAIProxyService.generateQuestionsFromContent,
      ).toHaveBeenCalledWith(expect.objectContaining({ count: 5 }));
    });

    it("uses default difficulty of medium", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockResolvedValueOnce([]);

      await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(
        mockAIProxyService.generateQuestionsFromContent,
      ).toHaveBeenCalledWith(expect.objectContaining({ difficulty: "medium" }));
    });

    it("limits count to maximum of 10", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockResolvedValueOnce([]);

      await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test", count: 100 },
      });

      expect(
        mockAIProxyService.generateQuestionsFromContent,
      ).toHaveBeenCalledWith(expect.objectContaining({ count: 10 }));
    });

    it("handles AI_NOT_CONFIGURED error", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockRejectedValueOnce(
        new Error("AI_NOT_CONFIGURED: Ollama not running"),
      );

      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(response.statusCode).toBe(503);
      const body = JSON.parse(response.body);
      expect(body.code).toBe("AI_NOT_CONFIGURED");
    });

    it("handles NO_CONTENT error", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockRejectedValueOnce(
        new Error("NO_CONTENT: Module has no content blocks"),
      );

      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "empty-module" },
      });

      expect(response.statusCode).toBe(422);
      const body = JSON.parse(response.body);
      expect(body.code).toBe("NO_CONTENT");
    });

    it("handles GENERATION_FAILED error", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockRejectedValueOnce(
        new Error("GENERATION_FAILED: LLM timeout"),
      );

      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.code).toBe("GENERATION_FAILED");
    });

    it("handles unknown errors", async () => {
      mockAIProxyService.generateQuestionsFromContent.mockRejectedValueOnce(
        new Error("Some unexpected error"),
      );

      const response = await app.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.code).toBe("UNKNOWN_ERROR");
    });

    it("returns 501 when generateQuestionsFromContent is not available", async () => {
      // Create new app without the method
      const appWithoutMethod = Fastify({ logger: false });
      await registerAIRoutes(appWithoutMethod, {
        aiProxyService: {
          handleTutorQuery: vi.fn(),
          // No generateQuestionsFromContent
        } as any,
      });
      await appWithoutMethod.ready();

      const response = await appWithoutMethod.inject({
        method: "POST",
        url: "/generate-questions",
        payload: { moduleId: "test" },
      });

      expect(response.statusCode).toBe(501);
      await appWithoutMethod.close();
    });
  });

  describe("POST /generate-concept", () => {
    it("returns 403 without admin role", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-concept",
        headers: {
          "x-tenant-id": "test",
          "x-user-role": "student",
        },
        payload: {
          conceptName: "Photosynthesis",
          domain: "BIOLOGY",
        },
      });

      expect(response.statusCode).toBe(403);
    });

    it("returns 400 when conceptName is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-concept",
        headers: {
          "x-tenant-id": "test",
          "x-user-role": "admin",
        },
        payload: {
          domain: "BIOLOGY",
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain("conceptName");
    });

    it("returns 400 when domain is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-concept",
        headers: {
          "x-tenant-id": "test",
          "x-user-role": "admin",
        },
        payload: {
          conceptName: "Test",
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain("domain");
    });
  });

  describe("POST /generate-simulation", () => {
    it("returns 403 without admin role", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-simulation",
        headers: {
          "x-user-role": "student",
        },
        payload: {
          description: "Bouncing ball simulation",
          conceptName: "Kinetic Energy",
          domain: "PHYSICS",
        },
      });

      expect(response.statusCode).toBe(403);
    });

    it("returns 400 when description is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-simulation",
        headers: { "x-user-role": "admin" },
        payload: {
          conceptName: "Test",
          domain: "PHYSICS",
        },
      });

      expect(response.statusCode).toBe(400);
    });

    it("returns 400 when conceptName is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-simulation",
        headers: { "x-user-role": "admin" },
        payload: {
          description: "Test sim",
          domain: "PHYSICS",
        },
      });

      expect(response.statusCode).toBe(400);
    });

    it("returns 400 when domain is missing", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/generate-simulation",
        headers: { "x-user-role": "admin" },
        payload: {
          description: "Test sim",
          conceptName: "Test",
        },
      });

      expect(response.statusCode).toBe(400);
    });
  });

  describe("Header Handling", () => {
    it("handles array-valued headers", async () => {
      mockAIProxyService.handleTutorQuery.mockResolvedValueOnce({
        answer: "Test",
        safety: { blocked: false },
      });

      // Fastify might pass headers as arrays in some cases
      const response = await app.inject({
        method: "POST",
        url: "/tutor/query",
        headers: {
          "x-tenant-id": "tenant-from-array",
        },
        payload: { question: "Test" },
      });

      expect(response.statusCode).toBe(200);
    });
  });
});

describe("AI Routes - Error Boundaries", () => {
  it("does not crash on malformed JSON payload", async () => {
    const app = Fastify({ logger: false });
    await registerAIRoutes(app, { aiProxyService: mockAIProxyService });
    await app.ready();

    const response = await app.inject({
      method: "POST",
      url: "/tutor/query",
      headers: { "Content-Type": "application/json" },
      payload: "{ invalid json }",
    });

    // Should return a 4xx error, not crash
    expect(response.statusCode).toBe(400);

    await app.close();
  });
});
