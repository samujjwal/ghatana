/**
 * Performance Benchmark Tests
 *
 * Validates that core operations in the TutorPutor product complete within
 * documented latency budgets when run in an in-process environment.
 *
 * Strategy:
 *   - All external I/O dependencies (Redis, OpenAI, Ollama) are replaced by
 *     lightweight in-process stubs so only the CPU-bound logic is measured.
 *   - `performance.now()` captures wall-clock elapsed time per operation.
 *   - Budgets are generous to avoid flakiness on CI runners.
 *
 * @doc.type test
 * @doc.purpose Performance latency-budget enforcement for core operations
 * @doc.layer product
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-PERF-001 … TPUT-FR-PERF-007
 */

import { describe, it, expect, vi, beforeAll } from "vitest";

// ---------------------------------------------------------------------------
// Mock ioredis – zero-latency in-process store
// ---------------------------------------------------------------------------
vi.mock("ioredis", () => ({
  default: class FastMockRedis {
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

// ---------------------------------------------------------------------------
// Mock openai – near-zero latency stub
// ---------------------------------------------------------------------------
vi.mock("openai", () => {
  const create = vi.fn().mockResolvedValue({
    choices: [
      {
        message: {
          content: JSON.stringify({ answer: "ok", followUpQuestions: [] }),
        },
      },
    ],
  });
  return {
    default: vi.fn(() => ({ chat: { completions: { create } } })),
  };
});

const mockFetch = vi.fn().mockResolvedValue({
  ok: true,
  json: async () => ({ models: [{ name: "llama3.2" }] }),
  text: async () => '{"message":{"content":"ok"}}\n',
});
vi.stubGlobal("fetch", mockFetch);

import { TutorPutorAIProxyService } from "../../libs/tutorputor-ai/src/proxy/service";
import { createRuntimeService } from "../../libs/tutorputor-simulation/src/engine/runtime/service";
import type {
  SimulationManifest,
  SimEntity,
} from "@tutorputor/contracts/v1/simulation/types";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

function buildManifest(
  stepCount: number,
  id = "perf-manifest",
): SimulationManifest {
  return {
    id: id as any,
    version: "1.0",
    domain: "CS_DISCRETE" as any,
    title: `Perf Manifest (${stepCount} steps)`,
    description: "Used in performance benchmarks",
    authorId: "bench" as any,
    tenantId: "tenant-perf" as any,
    canvas: { width: 1920, height: 1080 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: "1.0",
    initialEntities: [
      {
        id: "e1",
        type: "node",
        x: 100,
        y: 100,
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

/** Returns elapsed milliseconds for `fn`. */
async function measureMs(fn: () => Promise<unknown>): Promise<number> {
  const start = performance.now();
  await fn();
  return performance.now() - start;
}

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-001  Session creation latency
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-001: createSession latency", () => {
  /** Budget: 50 ms per session on an idle process. */
  const BUDGET_MS = 50;

  it("creates a session from a 20-step manifest within the latency budget", async () => {
    const service = createRuntimeService() as any;
    const manifest = buildManifest(20, "perf-create-20");

    const elapsed = await measureMs(() => service.createSession(manifest));

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });

  it("creates a session from a 100-step manifest within the latency budget", async () => {
    const service = createRuntimeService() as any;
    const manifest = buildManifest(100, "perf-create-100");

    const elapsed = await measureMs(() => service.createSession(manifest));

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-002  Sequential step navigation latency
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-002: stepForward / stepBackward latency", () => {
  /** Budget per step: 10 ms. */
  const BUDGET_PER_STEP_MS = 10;
  const STEP_COUNT = 50;

  it("navigates 50 steps forward within individual per-step budget", async () => {
    const service = createRuntimeService() as any;
    const sessionId = await service.createSession(buildManifest(STEP_COUNT));

    for (let i = 0; i < STEP_COUNT - 1; i++) {
      const elapsed = await measureMs(() => service.stepForward(sessionId));
      expect(elapsed).toBeLessThan(BUDGET_PER_STEP_MS);
    }
  });

  it("navigates 50 steps backward within individual per-step budget", async () => {
    const service = createRuntimeService() as any;
    const sessionId = await service.createSession(buildManifest(STEP_COUNT));

    // Advance to end first
    for (let i = 0; i < STEP_COUNT - 1; i++) {
      await service.stepForward(sessionId);
    }

    for (let i = 0; i < STEP_COUNT - 1; i++) {
      const elapsed = await measureMs(() => service.stepBackward(sessionId));
      expect(elapsed).toBeLessThan(BUDGET_PER_STEP_MS);
    }
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-003  seekToStep latency
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-003: seekToStep latency", () => {
  /** Budget: 20 ms for any seek regardless of distance. */
  const BUDGET_MS = 20;

  it("seeks to an arbitrary step within the latency budget", async () => {
    const service = createRuntimeService() as any;
    const manifest = buildManifest(100, "perf-seek");
    const sessionId = await service.createSession(manifest);

    const elapsed = await measureMs(() => service.seekToStep(sessionId, 75));

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-004  Concurrent session creation throughput
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-004: concurrent session creation", () => {
  /**
   * 10 sessions must all be created within 500 ms wall-clock time.
   * This validates that session creation is not serialised on a global lock.
   */
  const SESSION_COUNT = 10;
  const WALL_CLOCK_BUDGET_MS = 500;

  it("creates 10 sessions concurrently within wall-clock budget", async () => {
    const service = createRuntimeService() as any;

    const start = performance.now();
    const ids = await Promise.all(
      Array.from({ length: SESSION_COUNT }, (_, i) =>
        service.createSession(buildManifest(20, `perf-concurrent-${i}`)),
      ),
    );
    const elapsed = performance.now() - start;

    expect(ids).toHaveLength(SESSION_COUNT);
    expect(new Set(ids).size).toBe(SESSION_COUNT); // all IDs are unique
    expect(elapsed).toBeLessThan(WALL_CLOCK_BUDGET_MS);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-005  AI proxy health check latency
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-005: AI proxy health check latency", () => {
  /** Budget: 100 ms even when Ollama responds slowly (mock is instant here). */
  const BUDGET_MS = 100;

  it("getHealthStatus resolves within the latency budget", async () => {
    const service = new TutorPutorAIProxyService({
      useOllama: true,
      ollamaBaseUrl: "http://localhost:11434",
      openaiApiKey: "sk-test",
    });

    const elapsed = await measureMs(() => service.getHealthStatus());

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-006  handleTutorQuery latency (stub LLM)
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-006: handleTutorQuery latency with stub LLM", () => {
  /** Budget: 200 ms when LLM is a stub. */
  const BUDGET_MS = 200;

  it("resolves within latency budget using OpenAI stub", async () => {
    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    const elapsed = await measureMs(() =>
      service.handleTutorQuery({
        tenantId: "t1" as any,
        userId: "u1" as any,
        question: "Why is the sky blue?",
      }),
    );

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-PERF-007  parseSimulationIntent latency
// ---------------------------------------------------------------------------
describe("TPUT-FR-PERF-007: parseSimulationIntent latency", () => {
  /** Budget: 100 ms including LLM stub overhead. */
  const BUDGET_MS = 100;

  it("resolves within latency budget", async () => {
    const service = new TutorPutorAIProxyService({
      openaiApiKey: "sk-test",
      useOllama: false,
    });

    const elapsed = await measureMs(() =>
      service.parseSimulationIntent({ userInput: "connect node a to node b" }),
    );

    expect(elapsed).toBeLessThan(BUDGET_MS);
  });
});
