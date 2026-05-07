/**
 * @doc.type tests
 * @doc.purpose Unit tests for content-generation-queue noop and production guards.
 *   Verifies the queue throws in production when disabled, and warns in test mode.
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// ---------------------------------------------------------------------------
// Mock BullMQ so the queue module never opens a real Redis connection.
// ---------------------------------------------------------------------------
vi.mock("bullmq", () => ({
  Queue: class MockQueue {
    close = vi.fn().mockResolvedValue(undefined);
  },
}));

vi.mock("@tutorputor/core/logger", () => ({
  createStandaloneLogger: vi.fn().mockReturnValue({
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  }),
}));

// We re-import the module fresh for each test to get a clean singleton.
const importFresh = async () => {
  // Vitest caches modules; use vi.resetModules() to force a fresh import.
  vi.resetModules();
  return import("../content-generation-queue.js");
};

describe("getContentGenerationQueue", () => {
  const originalEnv = { ...process.env };

  beforeEach(() => {
    // Reset env before each test
    process.env = { ...originalEnv };
  });

  afterEach(() => {
    process.env = originalEnv;
    vi.resetModules();
  });

  describe("test / dev noop mode", () => {
    it("returns a noop queue when NODE_ENV=test", async () => {
      process.env.NODE_ENV = "test";
      delete process.env.CONTENT_QUEUE_DISABLED;

      const { getContentGenerationQueue } = await importFresh();
      const queue = getContentGenerationQueue();

      expect(queue).toBeDefined();
    });

    it("noop add() returns the jobId option when provided", async () => {
      process.env.NODE_ENV = "test";
      delete process.env.CONTENT_QUEUE_DISABLED;

      const { getContentGenerationQueue } = await importFresh();
      const queue = getContentGenerationQueue();

      const result = await queue.add("content-gen", { moduleId: "m1" }, { jobId: "test-job-id" });
      expect(result.id).toBe("test-job-id");
    });

    it("noop add() returns 'noop' id when no jobId option is given", async () => {
      process.env.NODE_ENV = "test";
      delete process.env.CONTENT_QUEUE_DISABLED;

      const { getContentGenerationQueue } = await importFresh();
      const queue = getContentGenerationQueue();

      const result = await queue.add("content-gen", { moduleId: "m1" });
      expect(result.id).toBe("noop");
    });

    it("noop addBulk() returns one entry per job", async () => {
      process.env.NODE_ENV = "test";
      delete process.env.CONTENT_QUEUE_DISABLED;

      const { getContentGenerationQueue } = await importFresh();
      const queue = getContentGenerationQueue();

      const results = await queue.addBulk([
        { name: "gen-1", data: { moduleId: "m1" }, opts: { jobId: "job-1" } },
        { name: "gen-2", data: { moduleId: "m2" }, opts: { jobId: "job-2" } },
      ]);

      expect(results).toHaveLength(2);
      expect(results[0].id).toBe("job-1");
      expect(results[1].id).toBe("job-2");
    });

    it("noop queue is returned when CONTENT_QUEUE_DISABLED=true in non-prod env", async () => {
      process.env.NODE_ENV = "development";
      process.env.CONTENT_QUEUE_DISABLED = "true";

      const { getContentGenerationQueue } = await importFresh();
      // Should not throw in dev
      expect(() => getContentGenerationQueue()).not.toThrow();
    });
  });

  describe("production guard", () => {
    it("throws when CONTENT_QUEUE_DISABLED=true in production", async () => {
      process.env.NODE_ENV = "production";
      process.env.CONTENT_QUEUE_DISABLED = "true";

      const { getContentGenerationQueue } = await importFresh();

      expect(() => getContentGenerationQueue()).toThrow(
        /Content generation queue is disabled.*production environment/,
      );
    });

    it("throw message includes actionable remediation hint", async () => {
      process.env.NODE_ENV = "production";
      process.env.CONTENT_QUEUE_DISABLED = "true";

      const { getContentGenerationQueue } = await importFresh();

      expect(() => getContentGenerationQueue()).toThrow(
        /CONTENT_QUEUE_DISABLED is unset/,
      );
    });
  });
});
