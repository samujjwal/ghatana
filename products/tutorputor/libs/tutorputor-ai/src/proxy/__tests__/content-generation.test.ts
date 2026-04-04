/**
 * AI Content Generation Tests
 *
 * Validates the observable behaviour of the AI content generation pipeline:
 *   – health-status reporting across backend configurations
 *   – tutor query answering with and without a Prisma / OpenAI backend
 *   – simulation intent parsing and graceful degradation when AI is unavailable
 *   – learning-unit draft generation via mocked LLM
 *   – content-query parsing to structured filters
 *   – question generation from module content
 *
 * All external I/O (OpenAI SDK, fetch/Ollama) is replaced by inline Vitest mocks
 * so these tests run offline without any API credentials.
 *
 * @doc.type test
 * @doc.purpose Requirement-first coverage of the AI content generation pipeline
 * @doc.layer product
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-AI-001 … TPUT-FR-AI-008
 */

import {
  describe,
  it,
  expect,
  beforeEach,
  vi,
  type MockInstance,
} from "vitest";
import {
  TutorPutorAIProxyService,
  type AIProxyServiceConfig,
} from "../service";

// ---------------------------------------------------------------------------
// Mock openai SDK at module level
// ---------------------------------------------------------------------------
vi.mock("openai", () => {
  const createFn = vi.fn();
  const listFn = vi.fn();
  // Use a regular function (not arrow) so it can be called with `new`
  function MockOpenAI(this: Record<string, unknown>, _config: unknown) {
    this["chat"] = { completions: { create: createFn } };
    this["models"] = { list: listFn };
  }
  // Expose the inner mocks so tests can configure return values
  (MockOpenAI as unknown as Record<string, unknown>)["__createFn"] = createFn;
  (MockOpenAI as unknown as Record<string, unknown>)["__listFn"] = listFn;
  return { default: MockOpenAI };
});

// ---------------------------------------------------------------------------
// Mock global fetch (used by Ollama-path and health checks)
// ---------------------------------------------------------------------------
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// ---------------------------------------------------------------------------
// Helper: make an OpenAI-style chat completion response
// ---------------------------------------------------------------------------
function makeChatResponse(content: string): {
  choices: Array<{ message: { content: string } }>;
} {
  return { choices: [{ message: { content } }] };
}

// ---------------------------------------------------------------------------
// Helper: access the private OpenAI createFn mock
// ---------------------------------------------------------------------------
async function getCreateMock(): Promise<MockInstance> {
  const { default: OpenAI } = await import("openai");
  return (OpenAI as unknown as Record<string, unknown>)
    .__createFn as MockInstance;
}

// ---------------------------------------------------------------------------
// Shared nullable Prisma stub (no DB required)
// ---------------------------------------------------------------------------
const nullPrisma = null;

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("TutorPutorAIProxyService – AI Content Generation", () => {
  let createMock: MockInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    mockFetch.mockReset();
    createMock = await getCreateMock();
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-001  Health status reporting
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-001: Health status reflects backend availability", () => {
    it("reports openai as available when an API key is supplied", async () => {
      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const status = await service.getHealthStatus();

      expect(status.openai.available).toBe(true);
      expect(status.activeBackend).toContain("openai");
    });

    it("reports openai as unavailable when no API key is supplied", async () => {
      const service = new TutorPutorAIProxyService({
        useOllama: false,
      });

      const status = await service.getHealthStatus();

      expect(status.openai.available).toBe(false);
    });

    it("reports the correct Ollama base URL in health status", async () => {
      mockFetch.mockResolvedValueOnce({ ok: true });

      const service = new TutorPutorAIProxyService({
        useOllama: true,
        ollamaBaseUrl: "http://ollama-server:11434",
        ollamaModel: "llama3",
      });

      const status = await service.getHealthStatus();

      expect(status.ollama.baseUrl).toBe("http://ollama-server:11434");
      expect(status.ollama.model).toBe("llama3");
    });

    it("reports Ollama as available when the /api/tags endpoint responds ok", async () => {
      mockFetch.mockResolvedValueOnce({ ok: true });

      const service = new TutorPutorAIProxyService({ useOllama: true });

      const status = await service.getHealthStatus();

      expect(status.ollama.available).toBe(true);
      expect(status.activeBackend).toContain("ollama");
    });

    it("reports Ollama as unavailable when /api/tags throws a network error", async () => {
      mockFetch.mockRejectedValueOnce(new Error("ECONNREFUSED"));

      const service = new TutorPutorAIProxyService({ useOllama: true });

      const status = await service.getHealthStatus();

      expect(status.ollama.available).toBe(false);
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-002  Tutor query answering with LLM backend
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-002: Tutor query returns answer and follow-up questions", () => {
    it("returns an answer string from the LLM when OpenAI is configured", async () => {
      createMock.mockResolvedValue(
        makeChatResponse("The derivative of x² is 2x."),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const result = await service.handleTutorQuery({
        tenantId: "tenant-1" as any,
        userId: "user-1" as any,
        question: "What is the derivative of x²?",
      });

      expect(result.answer).toBeTruthy();
      expect(typeof result.answer).toBe("string");
    });

    it("returns follow-up questions as an array of strings", async () => {
      createMock.mockResolvedValue(
        makeChatResponse(
          "Great question!\n1. Try x³\n2. Try e^x\n3. Try sin(x)",
        ),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const result = await service.handleTutorQuery({
        tenantId: "tenant-1" as any,
        userId: "user-1" as any,
        question: "What is a derivative?",
      });

      expect(Array.isArray(result.followUpQuestions)).toBe(true);
    });

    it("falls back to demo mode when no AI backend is configured", async () => {
      // No API key, Ollama disabled – webSearch also returns nothing useful
      mockFetch.mockRejectedValue(new Error("offline"));

      const service = new TutorPutorAIProxyService({ useOllama: false });

      const result = await service.handleTutorQuery({
        tenantId: "tenant-1" as any,
        userId: "user-1" as any,
        question: "Explain Newton's first law",
      });

      // Must still return a response string (demo mode)
      expect(typeof result.answer).toBe("string");
      expect(result.answer.length).toBeGreaterThan(0);
    });

    it("result always contains a safety object", async () => {
      createMock.mockResolvedValue(makeChatResponse("Safe answer."));

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const result = await service.handleTutorQuery({
        tenantId: "tenant-1" as any,
        userId: "user-1" as any,
        question: "What is ATP?",
      });

      expect(result.safety).toBeDefined();
      expect(result.safety.blocked).toBe(false);
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-003  Simulation intent parsing
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-003: Simulation intent parsing with structured output", () => {
    it("returns unknown intent with confidence 0 when no OpenAI client is configured", async () => {
      const service = new TutorPutorAIProxyService({ useOllama: false });

      const intent = await service.parseSimulationIntent({
        userInput: "add a red ball at position 10,10",
      });

      expect(intent.type).toBe("unknown");
      expect(intent.confidence).toBe(0);
      expect(intent.originalInput).toBe("add a red ball at position 10,10");
    });

    it("returns a parsed intent object with the original input preserved", async () => {
      createMock.mockResolvedValue(
        makeChatResponse(
          JSON.stringify({
            type: "add_entity",
            confidence: 0.9,
            params: { entityType: "ball", position: { x: 10, y: 10 } },
          }),
        ),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const intent = await service.parseSimulationIntent({
        userInput: "add a red ball at position 10,10",
      });

      expect(intent.originalInput).toBe("add a red ball at position 10,10");
      expect(intent.normalizedInput).toBe("add a red ball at position 10,10");
    });

    it("normalises input to lowercase regardless of LLM response", async () => {
      createMock.mockResolvedValue(
        makeChatResponse(
          JSON.stringify({
            type: "modify_entity",
            confidence: 0.8,
            params: {},
          }),
        ),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const intent = await service.parseSimulationIntent({
        userInput: "Move NODE_A to 50, 50",
      });

      expect(intent.normalizedInput).toBe("move node_a to 50, 50");
    });

    it("returns unknown intent gracefully when LLM throws", async () => {
      createMock.mockRejectedValue(new Error("quota exceeded"));

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const intent = await service.parseSimulationIntent({
        userInput: "delete all nodes",
      });

      expect(intent.type).toBe("unknown");
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-004  Learning unit draft generation
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-004: Learning unit draft generation from topic prompt", () => {
    it("returns a draft object with a title and contentBlocks when OpenAI succeeds", async () => {
      const draftPayload = {
        title: "Introduction to Derivatives",
        description: "Learn the fundamental concept of derivatives.",
        domain: "MATH",
        difficulty: "INTRO",
        learningObjectives: [
          { text: "Define derivative", bloomLevel: "Remember" },
        ],
        contentBlocks: [
          {
            type: "text",
            content: "A derivative measures the rate of change.",
          },
        ],
        metadata: { claims: [], evidence: [] },
      };

      createMock.mockResolvedValue(
        makeChatResponse(JSON.stringify(draftPayload)),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const draft = (await service.generateLearningUnitDraft({
        topic: "Derivatives in calculus",
        targetAudience: "High school students",
      })) as typeof draftPayload;

      expect(draft.title).toBe("Introduction to Derivatives");
      expect(Array.isArray(draft.contentBlocks)).toBe(true);
      expect(draft.contentBlocks.length).toBeGreaterThan(0);
    });

    it("throws when no OpenAI client is configured", async () => {
      const service = new TutorPutorAIProxyService({ useOllama: false });

      await expect(
        service.generateLearningUnitDraft({
          topic: "Photosynthesis",
          targetAudience: "Middle school",
        }),
      ).rejects.toThrow();
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-005  Content query parsing
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-005: Content query parsing extracts structured filters", () => {
    it("extracts domain and difficulty from a natural language query", async () => {
      createMock.mockResolvedValue(
        makeChatResponse(
          JSON.stringify({
            domain: "SCIENCE",
            difficulty: "INTERMEDIATE",
            tags: ["biology", "cells"],
            textSearch: "cell division",
          }),
        ),
      );

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const parsed = await service.parseContentQuery(
        "Show me intermediate science modules about cell division",
      );

      expect(parsed.domain).toBe("SCIENCE");
      expect(parsed.difficulty).toBe("INTERMEDIATE");
      expect(parsed.textSearch).toBe("cell division");
    });

    it("returns textSearch fallback when no OpenAI client is configured", async () => {
      const service = new TutorPutorAIProxyService({ useOllama: false });

      const parsed = await service.parseContentQuery("cell division biology");

      expect(parsed.textSearch).toBe("cell division biology");
    });

    it("returns textSearch fallback when LLM call fails", async () => {
      createMock.mockRejectedValue(new Error("500 Internal Server Error"));

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const parsed = await service.parseContentQuery(
        "advanced TECH sorting algorithms",
      );

      expect(typeof parsed.textSearch).toBe("string");
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-006  Question generation from content
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-006: Question generation requires Prisma and OpenAI", () => {
    it("throws with AI_NOT_CONFIGURED when Prisma client is absent", async () => {
      const service = new TutorPutorAIProxyService(
        { openaiApiKey: "sk-test-key", useOllama: false },
        undefined, // no prisma
      );

      await expect(
        service.generateQuestionsFromContent({
          tenantId: "tenant-1" as any,
          moduleId: "mod-1" as any,
          count: 3,
          difficulty: "medium",
        }),
      ).rejects.toThrow(/AI_NOT_CONFIGURED/);
    });

    it("throws with AI_NOT_CONFIGURED when no OpenAI key is set", async () => {
      const mockPrisma = {
        module: {
          findFirst: vi.fn().mockResolvedValue({
            id: "mod-1",
            title: "Calculus",
            description: "Intro",
            tenantId: "tenant-1",
            learningObjectives: [],
            contentBlocks: [
              {
                id: "b1",
                blockType: "text",
                payload: { content: "Derivatives..." },
              },
            ],
          }),
        },
      };

      const service = new TutorPutorAIProxyService(
        { useOllama: false },
        mockPrisma as any,
      );

      await expect(
        service.generateQuestionsFromContent({
          tenantId: "tenant-1" as any,
          moduleId: "mod-1" as any,
          count: 3,
          difficulty: "easy",
        }),
      ).rejects.toThrow(/AI_NOT_CONFIGURED/);
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-007  Ollama backend selection and fallback ordering
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-007: Ollama takes priority over OpenAI when enabled", () => {
    it("calls Ollama first when useOllama is true and Ollama is reachable", async () => {
      // Mock Ollama chat completions response (OpenAI-compatible path)
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          choices: [{ message: { content: "Ollama answered." } }],
        }),
      });

      // Also mock OpenAI to verify it is NOT called
      createMock.mockResolvedValue(makeChatResponse("OpenAI answered."));

      const service = new TutorPutorAIProxyService({
        useOllama: true,
        ollamaBaseUrl: "http://localhost:11434",
        ollamaModel: "mistral",
        openaiApiKey: "sk-test-key",
      });

      const result = await service.handleTutorQuery({
        tenantId: "t1" as any,
        userId: "u1" as any,
        question: "What is momentum?",
      });

      // The answer must come from Ollama
      expect(result.answer).toBe("Ollama answered.");
      // OpenAI should not have been called
      expect(createMock).not.toHaveBeenCalled();
    });

    it("falls through to OpenAI when Ollama is unreachable", async () => {
      // Ollama call fails
      mockFetch.mockRejectedValueOnce(new Error("ECONNREFUSED"));

      createMock.mockResolvedValue(makeChatResponse("OpenAI fallback answer."));

      const service = new TutorPutorAIProxyService({
        useOllama: true,
        openaiApiKey: "sk-test-key",
      });

      const result = await service.handleTutorQuery({
        tenantId: "t1" as any,
        userId: "u1" as any,
        question: "Explain entropy",
      });

      // Should fall back to OpenAI
      expect(result.answer).toBeTruthy();
    });
  });

  // -----------------------------------------------------------------------
  // TPUT-FR-AI-008  Service configuration contracts
  // -----------------------------------------------------------------------
  describe("TPUT-FR-AI-008: Service configuration contracts", () => {
    it("constructs without error when no config is provided", () => {
      expect(() => new TutorPutorAIProxyService()).not.toThrow();
    });

    it("uses default model gpt-4o-mini when no model is specified", async () => {
      createMock.mockResolvedValue(makeChatResponse("answer"));

      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        useOllama: false,
      });

      const status = await service.getHealthStatus();
      expect(status.openai.model).toBe("gpt-4o-mini");
    });

    it("respects a custom model name in health status", async () => {
      const service = new TutorPutorAIProxyService({
        openaiApiKey: "sk-test-key",
        model: "gpt-4-turbo",
        useOllama: false,
      });

      const status = await service.getHealthStatus();
      expect(status.openai.model).toBe("gpt-4-turbo");
    });
  });
});
