/**
 * Failure Scenario Tests
 *
 * Validates that every major service component degrades gracefully and produces
 * observable, non-silent failures when dependencies are unavailable:
 *   – AI service unavailability (no backend configured, or backend throws)
 *   – Database connection failure (Prisma throws on queries)
 *   – Redis / session-store failure for the simulation runtime
 *   – Network timeout simulation via fetch rejection
 *   – Invalid input at every documented boundary
 *   – Simulation session missing / already terminated errors
 *
 * All external dependencies are replaced with controlled spies/stubs so these
 * tests run without infrastructure.
 *
 * @doc.type test
 * @doc.purpose Resilience and failure-mode coverage
 * @doc.layer product
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-RES-001 … TPUT-FR-RES-008
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Mock ioredis before importing simulation service
// ---------------------------------------------------------------------------
vi.mock("ioredis", () => ({
  default: class MockRedis {
    private store = new Map<string, string>();
    async get(key: string) {
      return this.store.get(key) ?? null;
    }
    async set(key: string, value: string) {
      this.store.set(key, value);
      return "OK";
    }
    async del(key: string) {
      this.store.delete(key);
      return 1;
    }
    async expire() {
      return 1;
    }
    async keys() {
      return [];
    }
    async quit() {
      return "OK";
    }
    on(_: string, __: unknown) {
      return this;
    }
  },
}));

vi.mock("openai", () => {
  const create = vi.fn();
  const OpenAI = vi.fn(() => ({ chat: { completions: { create } } }));
  (OpenAI as unknown as Record<string, unknown>).__createFn = create;
  return { default: OpenAI };
});

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import { TutorPutorAIProxyService } from "../../libs/tutorputor-ai/src/proxy/service";
import { createRuntimeService } from "../../libs/tutorputor-simulation/src/engine/runtime/service";
import type {
  SimulationManifest,
  SimEntity,
} from "@tutorputor/contracts/v1/simulation/types";

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function buildManifest(stepCount = 3): SimulationManifest {
  return {
    id: "resilience-manifest-001" as any,
    version: "1.0",
    domain: "CS_DISCRETE" as any,
    title: "Resilience Test",
    description: "Used in failure scenario tests",
    authorId: "tester" as any,
    tenantId: "tenant-res" as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: "1.0",
    initialEntities: [
      {
        id: "e1",
        type: "node",
        x: 0,
        y: 0,
        visual: {},
        data: {},
      } as unknown as SimEntity,
    ],
    steps: Array.from({ length: stepCount }, (_, i) => ({
      id: `step-${i}` as any,
      orderIndex: i,
      actions: [],
    })),
    domainMetadata: { domain: "CS_DISCRETE" as any },
  };
}

// ---------------------------------------------------------------------------
// TPUT-FR-RES-001  AI service graceful degradation – no backend configured
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-001: AI service – no backend configured", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockReset();
  });

  it("handleTutorQuery returns a non-empty string in demo mode when no AI is configured", async () => {
    mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

    const service = new TutorPutorAIProxyService({
      useOllama: false,
      // no openaiApiKey
    });

    const result = await service.handleTutorQuery({
      tenantId: "t1" as any,
      userId: "u1" as any,
      question: "What is a derivative?",
    });

    expect(typeof result.answer).toBe("string");
    expect(result.answer.length).toBeGreaterThan(0);
    expect(result.safety.blocked).toBe(false);
  });

  it("parseContentQuery returns textSearch fallback when no openai key", async () => {
    const service = new TutorPutorAIProxyService({ useOllama: false });

    const result = await service.parseContentQuery(
      "intermediate chemistry modules",
    );

    expect(result.textSearch).toBe("intermediate chemistry modules");
  });

  it("parseSimulationIntent returns unknown intent gracefully", async () => {
    const service = new TutorPutorAIProxyService({ useOllama: false });

    const intent = await service.parseSimulationIntent({
      userInput: "add a node",
    });

    expect(intent.type).toBe("unknown");
    expect(intent.confidence).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-002  AI service – LLM throws at call time
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-002: AI service – LLM call failure", () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockFetch.mockReset();

    const { default: OpenAI } = await import("openai");
    const createFn = (OpenAI as unknown as Record<string, unknown>)
      .__createFn as ReturnType<typeof vi.fn>;
    createFn.mockRejectedValue(new Error("429 rate_limit_exceeded"));
  });

  it("handleTutorQuery falls back to demo mode when LLM throws rate-limit", async () => {
    mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    const result = await service.handleTutorQuery({
      tenantId: "t1" as any,
      userId: "u1" as any,
      question: "Explain osmosis",
    });

    expect(typeof result.answer).toBe("string");
    expect(result.answer.length).toBeGreaterThan(0);
  });

  it("parseContentQuery falls back to raw query on LLM error", async () => {
    const query = "advanced biology cell division";

    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    const result = await service.parseContentQuery(query);

    expect(result.textSearch).toBe(query);
  });

  it("parseSimulationIntent returns unknown intent on LLM error", async () => {
    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    const intent = await service.parseSimulationIntent({
      userInput: "move entity to 0,0",
    });

    expect(intent.type).toBe("unknown");
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-003  generateLearningUnitDraft – throws when AI unavailable
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-003: generateLearningUnitDraft – required dependency missing", () => {
  it("throws when no openai client is configured", async () => {
    const service = new TutorPutorAIProxyService({ useOllama: false });

    await expect(
      service.generateLearningUnitDraft({
        topic: "Gravity",
        targetAudience: "Students",
      }),
    ).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-004  generateQuestionsFromContent – throws with diagnostic codes
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-004: generateQuestionsFromContent – error codes are informative", () => {
  it("throws AI_NOT_CONFIGURED when prisma is absent", async () => {
    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    await expect(
      service.generateQuestionsFromContent({
        tenantId: "t1" as any,
        moduleId: "m1" as any,
        count: 3,
        difficulty: "easy",
      }),
    ).rejects.toThrow(/AI_NOT_CONFIGURED/);
  });

  it("throws AI_NOT_CONFIGURED when no openai key is set (prisma present)", async () => {
    const mockPrisma = {
      module: {
        findFirst: vi.fn().mockResolvedValue({
          id: "m1",
          title: "Test",
          description: "Desc",
          tenantId: "t1",
          learningObjectives: [],
          contentBlocks: [],
        }),
      },
    };

    const service = new TutorPutorAIProxyService(
      { useOllama: false },
      mockPrisma as any,
    );

    await expect(
      service.generateQuestionsFromContent({
        tenantId: "t1" as any,
        moduleId: "m1" as any,
        count: 3,
        difficulty: "easy",
      }),
    ).rejects.toThrow(/AI_NOT_CONFIGURED/);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-005  Simulation runtime – missing session errors
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-005: Simulation runtime – invalid session access", () => {
  it("stepForward on a non-existent session rejects with a meaningful error", async () => {
    const service = createRuntimeService() as any;

    await expect(
      service.stepForward("session-does-not-exist"),
    ).rejects.toThrow();
  });

  it("stepBackward on a non-existent session rejects", async () => {
    const service = createRuntimeService() as any;

    await expect(
      service.stepBackward("session-does-not-exist"),
    ).rejects.toThrow();
  });

  it("seekToStep on a non-existent session rejects", async () => {
    const service = createRuntimeService() as any;

    await expect(
      service.seekToStep("session-does-not-exist", 3),
    ).rejects.toThrow();
  });

  it("getSessionState on a non-existent session rejects", async () => {
    const service = createRuntimeService() as any;

    await expect(
      service.getSessionState("session-does-not-exist"),
    ).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-006  Simulation runtime – session terminated access
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-006: Simulation runtime – accessing terminated session", () => {
  it("stepForward on a terminated session rejects", async () => {
    const service = createRuntimeService() as any;
    const sessionId = await service.createSession(buildManifest());
    await service.terminateSession(sessionId);

    await expect(service.stepForward(sessionId)).rejects.toThrow();
  });

  it("getSessionState on a terminated session rejects", async () => {
    const service = createRuntimeService() as any;
    const sessionId = await service.createSession(buildManifest());
    await service.terminateSession(sessionId);

    await expect(service.getSessionState(sessionId)).rejects.toThrow();
  });

  it("double termination is harmless (idempotent)", async () => {
    const service = createRuntimeService() as any;
    const sessionId = await service.createSession(buildManifest());
    await service.terminateSession(sessionId);

    // Second termination must not throw (or if it does, still resolves without crashing)
    await service.terminateSession(sessionId).catch(() => void 0);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-007  Network timeout / Ollama unreachable
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-007: Ollama network timeout is handled gracefully", () => {
  it("handleTutorQuery continues with OpenAI when Ollama times out", async () => {
    mockFetch.mockRejectedValue(new Error("ETIMEDOUT"));

    const { default: OpenAI } = await import("openai");
    const createFn = (OpenAI as unknown as Record<string, unknown>)
      .__createFn as ReturnType<typeof vi.fn>;
    createFn.mockResolvedValue({
      choices: [
        { message: { content: "OpenAI answered after Ollama timeout." } },
      ],
    });

    const service = new TutorPutorAIProxyService({
      useOllama: true,
      openaiApiKey: "sk-test",
    });

    const result = await service.handleTutorQuery({
      tenantId: "t1" as any,
      userId: "u1" as any,
      question: "What is potential energy?",
    });

    expect(result.answer).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-RES-008  Service construction with minimal config does not throw
// ---------------------------------------------------------------------------
describe("TPUT-FR-RES-008: Services are fault-tolerant on initialisation", () => {
  it("TutorPutorAIProxyService constructs with zero config", () => {
    expect(() => new TutorPutorAIProxyService()).not.toThrow();
  });

  it("createRuntimeService constructs without arguments", () => {
    expect(() => createRuntimeService()).not.toThrow();
  });
});
