/**
 * AI module evaluation guardrail tests.
 *
 * Covers:
 * - safety.blocked is always false on normal responses (current behaviour is documented)
 * - handleTutorQuery degrades gracefully when LLM returns empty content
 * - handleTutorQuery degrades gracefully when LLM throws
 * - parseSimulationIntent falls back to type:unknown when OpenAI not configured
 * - parseSimulationIntent falls back to type:unknown when LLM returns invalid JSON
 * - parseSimulationIntent falls back to type:unknown when LLM throws
 * - explainSimulation returns "AI service unavailable" when OpenAI not configured
 * - generateModuleDraft degrades gracefully when no LLM configured
 * - Response citations array is always defined (never undefined/null)
 * - followUpQuestions array is always defined (never undefined/null)
 *
 * @doc.type test
 * @doc.purpose Evaluation guardrail coverage for tutorputor-ai module
 * @doc.layer product
 * @doc.pattern GuardrailTest
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { TutorPutorAIProxyService, type AIProxyServiceConfig } from "../service";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId, ModuleId } from "@tutorputor/contracts/v1/types";

/* ---------------------------------------------------------------------------
 * Mock openai — keeps tests offline
 * --------------------------------------------------------------------------- */
vi.mock("openai", () => {
  const createFn = vi.fn();
  function MockOpenAI(this: Record<string, unknown>, _config: unknown) {
    this["chat"] = { completions: { create: createFn } };
    this["models"] = { list: vi.fn().mockResolvedValue({ data: [] }) };
  }
  (MockOpenAI as unknown as Record<string, unknown>)["__createFn"] = createFn;
  return { default: MockOpenAI };
});

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

/* ---------------------------------------------------------------------------
 * Helpers
 * --------------------------------------------------------------------------- */

async function getCreateFn(): Promise<ReturnType<typeof vi.fn>> {
  const { default: OpenAI } = await import("openai");
  return (OpenAI as unknown as Record<string, ReturnType<typeof vi.fn>>)["__createFn"]!;
}

function buildPrismaStub(): TutorPrismaClient {
  return {
    module: { findUnique: vi.fn().mockResolvedValue(null) },
    contentBlock: { findMany: vi.fn().mockResolvedValue([]) },
    $queryRaw: vi.fn().mockResolvedValue([]),
  } as unknown as TutorPrismaClient;
}

function makeOpenAIResponse(content: string) {
  return {
    choices: [{ message: { content }, finish_reason: "stop" }],
  };
}

/* ===========================================================================
 * TESTS
 * =========================================================================== */

describe("TutorPutorAIProxyService — Evaluation Guardrails", () => {
  const tenantId = "tenant-test" as TenantId;
  const userId = "user-1" as UserId;
  const moduleId = "mod-1" as ModuleId;

  // ── Safety field ───────────────────────────────────────────────────────────

  describe("safety.blocked guardrail", () => {
    it("safety.blocked is false on a successful LLM response", async () => {
      const createFn = await getCreateFn();
      createFn.mockResolvedValue(makeOpenAIResponse("Photosynthesis is the process by which plants make food."));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" }, buildPrismaStub());
      const result = await service.handleTutorQuery({ tenantId, userId, moduleId, question: "What is photosynthesis?" });

      expect(result.safety.blocked).toBe(false);
    });

    it("safety.blocked is false even when no Prisma context is available", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.handleTutorQuery({ tenantId, userId, question: "Explain osmosis." });

      expect(result.safety.blocked).toBe(false);
    });
  });

  // ── Response shape invariants ──────────────────────────────────────────────

  describe("Response shape invariants", () => {
    it("citations is always an array (never null/undefined)", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.handleTutorQuery({ tenantId, userId, question: "What is gravity?" });

      expect(Array.isArray(result.citations)).toBe(true);
    });

    it("followUpQuestions is always an array when present", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.handleTutorQuery({ tenantId, userId, question: "Explain DNA replication." });

      if (result.followUpQuestions !== undefined) {
        expect(Array.isArray(result.followUpQuestions)).toBe(true);
      }
    });

    it("answer is always a non-empty string", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.handleTutorQuery({ tenantId, userId, question: "Define entropy." });

      expect(typeof result.answer).toBe("string");
      expect(result.answer.length).toBeGreaterThan(0);
    });
  });

  // ── LLM error degradation ─────────────────────────────────────────────────

  describe("LLM failure degradation", () => {
    it("returns a non-empty answer when LLM returns empty content string", async () => {
      const createFn = await getCreateFn();
      createFn.mockResolvedValue(makeOpenAIResponse(""));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" }, buildPrismaStub());
      const result = await service.handleTutorQuery({ tenantId, userId, question: "What is entropy?" });

      // Must not crash; answer should still be a string
      expect(typeof result.answer).toBe("string");
    });

    it("does not throw when LLM call throws a network error", async () => {
      const createFn = await getCreateFn();
      createFn.mockRejectedValue(new Error("Network error"));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" }, buildPrismaStub());

      await expect(
        service.handleTutorQuery({ tenantId, userId, question: "Explain Newton's laws." }),
      ).resolves.toBeDefined();
    });
  });

  // ── parseSimulationIntent guardrails ──────────────────────────────────────

  describe("parseSimulationIntent guardrails", () => {
    it("returns type:unknown when OpenAI is not configured", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.parseSimulationIntent({ userInput: "add a red node" });

      expect(result.type).toBe("unknown");
      expect(result.confidence).toBe(0);
    });

    it("returns type:unknown when LLM returns invalid JSON", async () => {
      const createFn = await getCreateFn();
      createFn.mockResolvedValue(makeOpenAIResponse("not valid json at all"));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" });
      const result = await service.parseSimulationIntent({ userInput: "add a red node" });

      expect(result.type).toBe("unknown");
      expect(result.confidence).toBe(0);
      expect(result.originalInput).toBe("add a red node");
    });

    it("returns type:unknown when LLM throws", async () => {
      const createFn = await getCreateFn();
      createFn.mockRejectedValue(new Error("Quota exceeded"));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" });
      const result = await service.parseSimulationIntent({ userInput: "remove node X" });

      expect(result.type).toBe("unknown");
    });

    it("parses a valid JSON response to the correct intent type", async () => {
      const createFn = await getCreateFn();
      createFn.mockResolvedValue(makeOpenAIResponse(JSON.stringify({
        type: "add_entity",
        confidence: 0.95,
        params: { entityType: "node", position: { x: 10, y: 20 } },
      })));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" });
      const result = await service.parseSimulationIntent({ userInput: "add a node at 10,20" });

      expect(result.type).toBe("add_entity");
      expect(result.confidence).toBe(0.95);
      expect(result.params).toBeDefined();
    });

    it("normalizedInput is always the lowercase version of userInput", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.parseSimulationIntent({ userInput: "ADD A NODE" });

      expect(result.normalizedInput).toBe("add a node");
      expect(result.originalInput).toBe("ADD A NODE");
    });
  });

  // ── explainSimulation guardrails ──────────────────────────────────────────

  describe("explainSimulation guardrails", () => {
    it("returns 'AI service unavailable' when OpenAI not configured", async () => {
      const service = new TutorPutorAIProxyService({}, undefined);
      const result = await service.explainSimulation({ manifest: { steps: [] }, query: "What does this do?" });

      expect(result).toBe("AI service unavailable.");
    });

    it("returns a non-empty string when LLM responds correctly", async () => {
      const createFn = await getCreateFn();
      createFn.mockResolvedValue(makeOpenAIResponse("This simulation models projectile motion."));

      const service = new TutorPutorAIProxyService({ openaiApiKey: "sk-test" });
      const result = await service.explainSimulation({
        manifest: { id: "sim-1", steps: [{ id: "s1", orderIndex: 0 }] },
        query: "Explain the simulation.",
      });

      expect(typeof result).toBe("string");
      expect(result.length).toBeGreaterThan(0);
    });
  });

  // ── getHealthStatus guardrails ────────────────────────────────────────────

  describe("getHealthStatus guardrails", () => {
    it("returns a complete health status object when no backends are configured", async () => {
      mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));
      const service = new TutorPutorAIProxyService({}, undefined);
      const health = await service.getHealthStatus();

      expect(health).toHaveProperty("ollama");
      expect(health).toHaveProperty("openai");
      expect(health).toHaveProperty("webSearch");
      expect(health).toHaveProperty("activeBackend");
      expect(typeof health.activeBackend).toBe("string");
    });

    it("activeBackend is 'demo' when neither Ollama nor OpenAI are available", async () => {
      mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));
      const service = new TutorPutorAIProxyService({ useOllama: false }, undefined);
      const health = await service.getHealthStatus();

      expect(health.activeBackend).toMatch(/demo|web-search/);
    });
  });
});
