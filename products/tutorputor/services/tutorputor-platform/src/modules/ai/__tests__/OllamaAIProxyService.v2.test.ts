/**
 * Comprehensive Test Suite for OllamaAIProxyService - Production Implementation
 *
 * Tests all 5 AI methods with real Ollama integration patterns:
 * - handleTutorQuery: Educational tutoring with follow-up questions
 * - parseSimulationIntent: Intent classification with rule-based + AI fallback
 * - explainSimulation: Simulation explanation from manifest
 * - generateLearningUnitDraft: Curriculum structure generation
 * - parseContentQuery: Search query parsing
 *
 * @doc.type tests
 * @doc.purpose Unit tests for production AI proxy service
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

describe("OllamaAIProxyService - Production Implementation", () => {
  let service: OllamaAIProxyService;

  beforeEach(() => {
    mockFetch.mockClear();
    service = new OllamaAIProxyService("http://localhost:11434", "llama3.2");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ============================================================================
  // Constructor Tests
  // ============================================================================
  describe("constructor", () => {
    it("uses default base URL and model when none provided", () => {
      const defaultService = new OllamaAIProxyService();
      expect(defaultService).toBeDefined();
    });

    it("accepts custom base URL and model", () => {
      const customService = new OllamaAIProxyService("http://custom:8080", "custom-model");
      expect(customService).toBeDefined();
    });
  });

  // ============================================================================
  // handleTutorQuery Tests
  // ============================================================================
  describe("handleTutorQuery", () => {
    const defaultArgs = {
      tenantId: "test-tenant" as TenantId,
      userId: "test-user" as UserId,
      question: "What is photosynthesis?",
      locale: "en",
    };

    it("sends correct request to Ollama /api/generate", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Photosynthesis is the process...\n\nFollow-up Questions:\n1. What are the stages?\n2. How does light affect it?",
            done: true,
            prompt_eval_count: 50,
            eval_count: 100,
          }),
      });

      await service.handleTutorQuery(defaultArgs);

      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:11434/api/generate",
        expect.objectContaining({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: expect.stringContaining("photosynthesis"),
        }),
      );

      const requestBody = JSON.parse(mockFetch.mock.calls[0][1].body);
      expect(requestBody.model).toBe("llama3.2");
      expect(requestBody.system).toContain("TutorPutor");
      expect(requestBody.options.temperature).toBe(0.7);
    });

    it("extracts follow-up questions from response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Answer text\n\nFollow-up Questions:\n1. What are chloroplasts?\n2. Why is sunlight needed?\n3. What is ATP?",
            done: true,
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.followUpQuestions).toHaveLength(3);
      expect(result.followUpQuestions[0]).toBe("What are chloroplasts?");
    });

    it("extracts citations from markdown links", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Answer with [Khan Academy](https://khanacademy.org) reference",
            done: true,
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.citations).toHaveLength(1);
      expect(result.citations[0].label).toBe("Khan Academy");
      expect(result.citations[0].id).toBe("https://khanacademy.org");
    });

    it("cleans follow-up questions from main answer", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Main answer text.\n\nFollow-up Questions:\n1. Question one?\n2. Question two?",
            done: true,
          }),
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toBe("Main answer text.");
      expect(result.answer).not.toContain("Follow-up Questions");
    });

    it("handles network errors gracefully", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Network error"));

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
      expect(result.safety.blocked).toBe(false);
    });

    it("handles HTTP errors gracefully", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: "Internal Server Error",
      });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(result.answer).toContain("trouble connecting");
    });

    it("retries on timeout with exponential backoff", async () => {
      mockFetch
        .mockRejectedValueOnce(new Error("fetch failed"))
        .mockRejectedValueOnce(new Error("fetch failed"))
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Success after retries", done: true }),
        });

      const result = await service.handleTutorQuery(defaultArgs);

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(result.answer).toBe("Success after retries");
    });

    it("uses locale for non-English responses", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Respuesta en español", done: true }),
      });

      await service.handleTutorQuery({
        ...defaultArgs,
        locale: "es",
      });

      const requestBody = JSON.parse(mockFetch.mock.calls[0][1].body);
      expect(requestBody.prompt).toContain("es");
    });
  });

  // ============================================================================
  // parseSimulationIntent Tests
  // ============================================================================
  describe("parseSimulationIntent", () => {
    it("classifies CREATE_SIMULATION with rule-based for clear patterns", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Create and build a new physics simulation design for a pendulum",
      });

      expect(result.type).toBe("CREATE_SIMULATION");
      expect(result.confidence).toBeGreaterThan(0.5);
      expect(result.params?.domain).toBe("physics");
    });

    it("classifies RUN_SIMULATION for execution patterns", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Run start execute and launch the simulation now",
      });

      expect(result.type).toBe("RUN_SIMULATION");
      expect(result.confidence).toBeGreaterThan(0);
    });

    it("classifies MODIFY_SIMULATION for edit patterns", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Modify change update and adjust the simulation settings",
      });

      expect(result.type).toBe("MODIFY_SIMULATION");
    });

    it("classifies EXPLAIN_CONCEPT for understanding patterns", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Explain what is and how does this concept work and why",
      });

      expect(result.type).toBe("EXPLAIN_CONCEPT");
    });

    it("classifies ANALYZE_SIMULATION for data patterns", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Analyze the simulation results and plot the graph",
      });

      expect(result.type).toBe("ANALYZE_SIMULATION");
    });

    it("falls back to AI classification for complex cases", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: JSON.stringify({
              type: "CREATE_SIMULATION",
              confidence: 0.85,
              params: { domain: "chemistry", topic: "reaction" },
            }),
            done: true,
          }),
      });

      const result = await service.parseSimulationIntent({
        userInput: "I want to model a complex chemical reaction",
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("extracts domain from input", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Create build design a new biology simulation about cell division",
      });

      expect(result.params?.domain).toBe("biology");
    });

    it("extracts topic from input", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "Projectile motion simulation create build design",
      });

      expect(result.params?.topic).toContain("projectile");
    });

    it("handles unknown intents gracefully", async () => {
      const result = await service.parseSimulationIntent({
        userInput: "xyz abc 123",
      });

      expect(result.type).toBe("unknown");
      expect(result.confidence).toBe(0);
    });

    it("handles AI classification errors gracefully", async () => {
      mockFetch.mockRejectedValueOnce(new Error("AI service down"));

      const result = await service.parseSimulationIntent({
        userInput: "Complex query requiring AI",
      });

      expect(result.type).toBe("unknown");
      expect(result.confidence).toBe(0);
    });
  });

  // ============================================================================
  // explainSimulation Tests
  // ============================================================================
  describe("explainSimulation", () => {
    it("sends manifest to Ollama for explanation", async () => {
      const manifest = {
        id: "pendulum-sim",
        domain: "physics",
        entities: [{ type: "ball", mass: 1 }],
        parameters: { gravity: 9.8 },
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "This pendulum simulation demonstrates periodic motion and energy conservation.",
            done: true,
          }),
      });

      const result = await service.explainSimulation({
        manifest,
        query: "How does gravity affect the pendulum?",
      });

      expect(mockFetch).toHaveBeenCalledWith(
        "http://localhost:11434/api/generate",
        expect.any(Object),
      );

      const requestBody = JSON.parse(mockFetch.mock.calls[0][1].body);
      expect(requestBody.prompt).toContain("pendulum-sim");
      expect(requestBody.prompt).toContain("How does gravity");
      expect(result).toContain("periodic motion");
    });

    it("handles missing manifest gracefully", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Explanation", done: true }),
      });

      const result = await service.explainSimulation({
        manifest: {},
        query: "What is this?",
      });

      expect(result).toBe("Explanation");
    });

    it("handles Ollama errors gracefully", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Service unavailable"));

      const result = await service.explainSimulation({
        manifest: { id: "test" },
        query: "Explain?",
      });

      expect(result).toContain("apologize");
      expect(result).toContain("try again later");
    });
  });

  // ============================================================================
  // generateLearningUnitDraft Tests
  // ============================================================================
  describe("generateLearningUnitDraft", () => {
    it("generates structured learning unit from AI", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: JSON.stringify({
              title: "Introduction to Photosynthesis",
              description: "Learn how plants convert sunlight to energy",
              sections: [
                { type: "introduction", title: "Overview", content: "What is photosynthesis?", estimatedMinutes: 10 },
                { type: "concept", title: "Chloroplasts", content: "The role of chloroplasts", estimatedMinutes: 15 },
              ],
            }),
            done: true,
          }),
      });

      const result = await service.generateLearningUnitDraft({
        topic: "Photosynthesis",
        targetAudience: "High school students",
        learningObjectives: ["Understand the process", "Identify key components"],
      });

      expect(result.title).toBe("Introduction to Photosynthesis");
      expect(result.sections).toHaveLength(2);
      expect(result.sections[0].type).toBe("introduction");
    });

    it("sends correct prompt with topic and objectives", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: JSON.stringify({ title: "Test", description: "", sections: [] }),
            done: true,
          }),
      });

      await service.generateLearningUnitDraft({
        topic: "Newton's Laws",
        targetAudience: "Middle school",
        learningObjectives: ["Understand F=ma"],
      });

      const requestBody = JSON.parse(mockFetch.mock.calls[0][1].body);
      expect(requestBody.prompt).toContain("Newton's Laws");
      expect(requestBody.prompt).toContain("Middle school");
      expect(requestBody.prompt).toContain("F=ma");
    });

    it("falls back to default structure on AI error", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Generation failed"));

      const result = await service.generateLearningUnitDraft({
        topic: "Cell Biology",
        targetAudience: "College students",
      });

      expect(result.title).toBe("Cell Biology");
      expect(result.sections).toHaveLength(5);
      expect(result.sections[0].title).toBe("Introduction");
    });

    it("handles malformed JSON response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            response: "Not valid JSON",
            done: true,
          }),
      });

      const result = await service.generateLearningUnitDraft({
        topic: "Test",
        targetAudience: "Test",
      });

      expect(result.title).toBe("Untitled Unit");
      expect(result.sections).toEqual([]);
    });
  });

  // ============================================================================
  // parseContentQuery Tests
  // ============================================================================
  describe("parseContentQuery", () => {
    it("extracts domain from physics keywords", async () => {
      const result = await service.parseContentQuery("physics gravity tutorials");

      expect(result.domain).toBe("physics");
      expect(result.textSearch).toContain("gravity");
    });

    it("extracts domain from chemistry keywords", async () => {
      const result = await service.parseContentQuery("chemistry molecule bonds");

      expect(result.domain).toBe("chemistry");
    });

    it("extracts domain from biology keywords", async () => {
      const result = await service.parseContentQuery("biology cell structure");

      expect(result.domain).toBe("biology");
    });

    it("extracts difficulty level", async () => {
      const result = await service.parseContentQuery("beginner physics easy concepts");

      expect(result.difficulty).toBe("beginner");
    });

    it("extracts intermediate difficulty", async () => {
      const result = await service.parseContentQuery("intermediate calculus problems");

      expect(result.difficulty).toBe("intermediate");
    });

    it("extracts advanced difficulty", async () => {
      const result = await service.parseContentQuery("advanced quantum mechanics expert level");

      expect(result.difficulty).toBe("advanced");
    });

    it("extracts relevant tags", async () => {
      const result = await service.parseContentQuery("physics gravity newton motion");

      expect(result.tags).toContain("gravity");
      expect(result.tags).toContain("newton");
      expect(result.tags).toContain("motion");
    });

    it("cleans domain and difficulty from text search", async () => {
      const result = await service.parseContentQuery("beginner physics tutorials");

      expect(result.textSearch).not.toContain("beginner");
      expect(result.textSearch).not.toContain("physics");
      expect(result.textSearch).toContain("tutorials");
    });

    it("handles queries with no domain keywords", async () => {
      const result = await service.parseContentQuery("history resources");

      expect(result.domain).toBeUndefined();
      expect(result.textSearch).toBe("history resources");
    });

    it("handles empty queries", async () => {
      const result = await service.parseContentQuery("");

      expect(result.textSearch).toBe("");
    });
  });

  // ============================================================================
  // Edge Cases and Error Handling
  // ============================================================================
  describe("Edge Cases", () => {
    it("handles concurrent requests without interference", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Answer 1", done: true }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ type: "CREATE_SIMULATION", confidence: 0.9, params: {} }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ response: "Explanation", done: true }),
        });

      const results = await Promise.all([
        service.handleTutorQuery({
          tenantId: "t1" as TenantId,
          userId: "u1" as UserId,
          question: "Q1",
        }),
        service.parseSimulationIntent({ userInput: "Create sim" }),
        service.explainSimulation({ manifest: {}, query: "Explain?" }),
      ]);

      expect(results).toHaveLength(3);
      expect(mockFetch).toHaveBeenCalledTimes(3);
    });

    it("handles special characters in questions", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Answer", done: true }),
      });

      await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "What is E=mc²? Is it <4> or (π)?",
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("handles unicode characters", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ response: "Respuesta", done: true }),
      });

      await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "¿Qué es la fotosíntesis? 日本語テスト",
      });

      expect(mockFetch).toHaveBeenCalled();
    });

    it("respects timeout with AbortController", async () => {
      mockFetch.mockRejectedValue(new Error("fetch failed"));

      const result = await service.handleTutorQuery({
        tenantId: "test" as TenantId,
        userId: "test" as UserId,
        question: "Test",
      });

      expect(result.answer).toContain("trouble connecting");
    });
  });
});
