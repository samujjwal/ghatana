/**
 * Test suite for OllamaAIProxyService
 *
 * @doc.type tests
 * @doc.purpose Unit tests for AI proxy service
 * @doc.layer platform
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { OllamaAIProxyService } from "../OllamaAIProxyService";
import type {
  TenantId,
  UserId,
  ModuleId,
} from "@tutorputor/contracts/v1/types";

// Mock global fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe("OllamaAIProxyService", () => {
  let service: OllamaAIProxyService;

  beforeEach(() => {
    mockFetch.mockClear();
    service = new OllamaAIProxyService("http://localhost:3300");
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("constructor", () => {
    it("uses default base URL when none provided", () => {
      const defaultService = new OllamaAIProxyService();
      // Service should be created without error
      expect(defaultService).toBeDefined();
    });

    it("accepts custom base URL", () => {
      const customService = new OllamaAIProxyService("http://custom:8080");
      expect(customService).toBeDefined();
    });
  });

  describe("handleTutorQuery", () => {
    const defaultArgs = {
      tenantId: "test-tenant" as TenantId,
      userId: "test-user" as UserId,
      question: "What is photosynthesis?",
      locale: "en",
    };

    it("sends correct request to Ollama", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Photosynthesis is the process...",
            citations: [],
            followUpQuestions: [],
          }),
      });

      await service.handleTutorQuery(defaultArgs);

      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:3300/api/generate",
        expect.objectContaining({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: expect.stringContaining("photosynthesis"),
        }),
      );
    });

    it("includes Ollama generation fields in request body", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      await service.handleTutorQuery(defaultArgs);

      const callArgs = mockFetch.mock.calls[0];
      const requestBody = JSON.parse(callArgs[1].body);

      expect(requestBody).toEqual(
        expect.objectContaining({
          model: expect.any(String),
          system: expect.any(String),
          prompt: expect.any(String),
          stream: false,
        }),
      );
    });

    it("accepts moduleId when provided", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      await service.handleTutorQuery({
        ...defaultArgs,
        moduleId: "module-123" as ModuleId,
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("omits locale instruction when locale is not provided", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      await service.handleTutorQuery({
        tenantId: "test-tenant" as TenantId,
        userId: "test-user" as UserId,
        question: "Test question",
      });

      const callArgs = mockFetch.mock.calls[0];
      const requestBody = JSON.parse(callArgs[1].body);

      expect(requestBody.prompt).not.toContain("Respond in");
    });

    it("returns answer from response field", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "This is the AI response",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toBe("This is the AI response");
    });

    it("falls back when response field is missing (content field only)", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            content: "Content field response",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });

    it("falls back when response field is missing (answer field only)", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            answer: "Answer field response",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });

    it("falls back when response field is missing (custom field)", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            customField: "Some data",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });

    it("extracts citations from markdown links in response text", async () => {
      const citations = [
        { id: "1", label: "Source 1", type: "textbook" },
        { id: "2", label: "Source 2", type: "video" },
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Answer with source [Source 1](1) and [Source 2](2)",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.citations).toEqual([
        { type: "content_block", id: "1", label: "Source 1" },
        { type: "content_block", id: "2", label: "Source 2" },
      ]);
    });

    it("extracts follow-up questions from response text", async () => {
      const followUpQuestions = [
        "What are the stages?",
        "How does light affect it?",
      ];

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response:
              "Answer\n\nFollow-up Questions:\n1. What are the stages?\n2. How does light affect it?",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.followUpQuestions).toEqual(followUpQuestions);
    });

    it("returns empty arrays when citations/followUpQuestions not provided", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Simple answer",
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.citations).toEqual([]);
      expect(result.followUpQuestions).toEqual([]);
    });

    it("returns safety.blocked as false for successful responses", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.safety).toEqual({ blocked: false });
    });

    it("handles HTTP error status gracefully", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        statusText: "Internal Server Error",
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
      expect(result.safety).toEqual({ blocked: false });
    });

    it("handles network error gracefully", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Network error"));

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
      expect(result.safety).toEqual({ blocked: false });
    });

    it("handles JSON parse error gracefully", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.reject(new Error("JSON parse error")),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });

    it("handles timeout gracefully", async () => {
      mockFetch.mockImplementationOnce(
        () =>
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error("Timeout")), 100),
          ),
      );

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });
  });

  describe("parseSimulationIntent", () => {
    it("returns unknown intent for any input", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "simulate a ball bouncing",
        context: "physics",
      });

      expect(result.type).toBe("unknown");
      expect(result.confidence).toBe(0);
      expect(result.originalInput).toBe("simulate a ball bouncing");
    });

    it("normalizes input to lowercase", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "CREATE Physics Simulation",
      });

      expect(result.normalizedInput).toBe("create physics simulation");
    });

    it("trims whitespace from input", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "  test input  ",
      });

      expect(result.normalizedInput).toBe("test input");
    });
  });

  describe("explainSimulation", () => {
    it("returns graceful fallback explanation on errors", async () => {
      const result = await service.explainSimulation({
        manifest: { id: "sim-1" },
        query: "How does this work?",
      });

      expect(result).toContain("unable to explain");
    });
  });

  describe("generateLearningUnitDraft", () => {
    it("returns default draft structure on errors", async () => {
      const result = await service.generateLearningUnitDraft({
        topic: "Photosynthesis",
        targetAudience: "High school students",
        learningObjectives: ["Understand the process"],
      });

      expect(result).toEqual({
        title: "Photosynthesis",
        description: "Learning unit about Photosynthesis for High school students",
        sections: expect.any(Array),
      });
    });
  });

  describe("parseContentQuery", () => {
    it("returns parsed query structure", async () => {
      const result = await service.parseContentQuery(
        "physics beginner tutorials",
      );

      expect(result).toEqual({
        domain: "physics",
        difficulty: "beginner",
        tags: ["physics"],
        textSearch: "tutorials",
      });
    });
  });
});

describe("OllamaAIProxyService - Edge Cases", () => {
  let service: OllamaAIProxyService;

  beforeEach(() => {
    mockFetch.mockClear();
    service = new OllamaAIProxyService();
  });

  describe("Special Characters", () => {
    it("handles questions with special characters", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "What is 2 + 2? Is it <4> or (4)?",
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("handles questions with unicode characters", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Test" }),
      });

      await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "¿Qué es la fotosíntesis? 日本語テスト",
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("handles empty question gracefully", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Please ask a question" }),
      });

      const result = await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "",
      });

      expect(result.answer).toBeDefined();
    });
  });

  describe("Long Content", () => {
    it("handles very long questions", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Summarized answer" }),
      });

      const longQuestion = "Test ".repeat(1000);

      await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: longQuestion,
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("handles very long responses", async () => {
      const longResponse = "This is a detailed explanation. ".repeat(500);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: longResponse }),
      });

      const result = await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "Explain everything about photosynthesis",
      });

      expect(result.answer).toBe(longResponse.trim());
    });
  });

  describe("Concurrent Requests", () => {
    it("handles multiple concurrent requests", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Answer 1" }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Answer 2" }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Answer 3" }),
        });

      const results = await Promise.all([
        service.handleTutorQuery({
          tenantId: "t1" as TenantId,
          userId: "u1" as UserId,
          question: "Q1",
        }),
        service.handleTutorQuery({
          tenantId: "t2" as TenantId,
          userId: "u2" as UserId,
          question: "Q2",
        }),
        service.handleTutorQuery({
          tenantId: "t3" as TenantId,
          userId: "u3" as UserId,
          question: "Q3",
        }),
      ]);

      expect(results).toHaveLength(3);
      expect(mockFetch).toHaveBeenCalledTimes(3);
    });
  });

  describe("Response Format Variations", () => {
    it("handles null values in response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Answer",
            citations: null,
            followUpQuestions: null,
          }),
      });

      const result = await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "Test",
      });

      expect(result.citations).toEqual([]);
      expect(result.followUpQuestions).toEqual([]);
    });

    it("handles undefined values in response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Answer",
          }),
      });

      const result = await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "Test",
      });

      expect(result.citations).toEqual([]);
      expect(result.followUpQuestions).toEqual([]);
    });
  });
});
