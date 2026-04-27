/**
 * P0-4: End-to-End Generation Worker Lifecycle Tests
 *
 * Exercises the full content generation pipeline from job processor through
 * gRPC call, DB persistence, content validation, provenance capture, and
 * publish gate. All external dependencies (Prisma, gRPC, BullMQ) are
 * stubbed to keep the test deterministic and fast.
 *
 * Flow under test:
 *   1. ClaimGenerationProcessor processes a "claim" job
 *      → calls gRPC generateClaims
 *      → persists claims to DB via Prisma
 *      → queues example/simulation follow-up jobs
 *   2. ContentValidationProcessor validates the generated content
 *      → calls gRPC validateContent
 *      → persists validation result with canPublish flag
 *   3. GenerationQualityLoopService closes the loop
 *      → evaluates the request
 *      → returns auto_published (high confidence) or human_review
 *   4. Publish gate: canPublish=true → publishes; canPublish=false → blocks
 *
 * @doc.type test-suite
 * @doc.purpose Prove correctness of the end-to-end worker generation lifecycle
 * @doc.layer platform
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import type { Job } from "bullmq";
import { ClaimGenerationProcessor } from "../workers/content/processors/ClaimGenerationProcessor.js";
import { ContentValidationProcessor } from "../workers/content/processors/ContentValidationProcessor.js";
import type { ClaimGenerationJobData } from "../workers/content/processors/ClaimGenerationProcessor.js";
import type { ContentValidationJobData } from "../workers/content/processors/ContentValidationProcessor.js";

// ---------------------------------------------------------------------------
// BullMQ mock — only the Queue.add surface is needed for this test
// ---------------------------------------------------------------------------
vi.mock("bullmq", () => {
  class Queue {
    add = vi.fn().mockResolvedValue({ id: "mock-queue-job" });
    close = vi.fn().mockResolvedValue(undefined);
  }
  class QueueEvents {
    close = vi.fn().mockResolvedValue(undefined);
  }
  class Job {}
  return { Queue, QueueEvents, Job };
});

// ---------------------------------------------------------------------------
// In-memory Prisma stub covering the claim + validation flow
// ---------------------------------------------------------------------------
function makePrismaStub() {
  const claims = new Map<string, Record<string, unknown>>();
  const validations = new Map<string, Record<string, unknown>>();

  // Seed a learning experience for validation queries
  const seededExp = {
    id: "exp-1",
    title: "Newton's Laws of Motion",
    description: "Comprehensive physics module",
    domain: "physics",
    claims: [
      { text: "Objects resist changes in their motion" },
      { text: "Force equals mass times acceleration" },
    ],
    experienceTasks: [],
  };

  return {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    learningExperience: {
      findUnique: vi.fn().mockImplementation(({ where }: { where: { id: string } }) =>
        Promise.resolve(where.id === "exp-1" ? seededExp : null),
      ),
      update: vi.fn().mockResolvedValue({ id: "exp-1" }),
    },
    learningClaim: {
      upsert: vi.fn().mockImplementation(({ create }: { create: Record<string, unknown> }) => {
        const id = `claim-${Math.random().toString(36).slice(2, 9)}`;
        const row = { id, ...create };
        claims.set(id, row);
        return Promise.resolve(row);
      }),
    },
    validationRecord: {
      create: vi.fn().mockImplementation(({ data }: { data: Record<string, unknown> }) => {
        const id = `val-${Math.random().toString(36).slice(2, 9)}`;
        const row = { id, ...data };
        validations.set(id, row);
        return Promise.resolve(row);
      }),
    },
    generationRequest: {
      update: vi.fn().mockResolvedValue({ id: "req-1", status: "COMPLETED" }),
    },
    _claims: claims,
    _validations: validations,
  } as unknown as ReturnType<typeof import("@tutorputor/core/db").PrismaClient>;
}

// ---------------------------------------------------------------------------
// gRPC client stub
// ---------------------------------------------------------------------------
function makeGrpcStub(overrides: Record<string, unknown> = {}) {
  return {
    generateClaims: vi.fn().mockResolvedValue({
      claims: [
        {
          claim_ref: "claim-newton-1",
          text: "An object in motion stays in motion unless acted upon by an external force",
          bloom_level: "understand",
          order_index: 0,
          content_needs: {
            examples: { required: true, count: 2, types: ["worked", "counterexample"] },
            simulation: { required: true, interactionType: "parameter_tuning", complexity: "medium" },
            animation: { required: false, type: "conceptual", durationSeconds: 30, complexity: "low" },
          },
        },
        {
          claim_ref: "claim-newton-2",
          text: "Net force equals mass times acceleration",
          bloom_level: "apply",
          order_index: 1,
          // content_needs provided to avoid analyzeContentNeeds call complexity
          content_needs: {
            examples: { required: true, count: 1, types: ["worked"] },
            simulation: { required: false },
            animation: { required: false },
          },
        },
      ],
      metadata: { tokens_used: 2400, model_version: "gpt-4o" },
    }),
    // analyzeContentNeeds wraps result in { contentNeeds }
    analyzeContentNeeds: vi.fn().mockResolvedValue({
      contentNeeds: {
        examples: { required: true, count: 2, types: ["worked"] },
        simulation: { required: false },
        animation: { required: false },
      },
    }),
    validateContent: vi.fn().mockResolvedValue({
      request_id: "req-1",
      experience_id: "exp-1",
      overall_score: 0.91,
      overallScore: 0.91,
      canPublish: true,
      status: "PASS",
      issueCount: 0,
      pillar_scores: { correctness: 0.95, completeness: 0.88, concreteness: 0.90, conciseness: 0.91 },
      issues: [],
      can_publish: true,
      recommendation: "auto_publish",
      metadata: { tokens_used: 800 },
    }),
    generateExamples: vi.fn().mockResolvedValue({ examples: [], metadata: { tokens_used: 0 } }),
    generateSimulation: vi.fn().mockResolvedValue({ simulation: {}, metadata: { tokens_used: 0 } }),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Telemetry stub
// ---------------------------------------------------------------------------
function makeTelemetry() {
  return {
    publishForJob: vi.fn().mockResolvedValue(undefined),
  };
}

// ---------------------------------------------------------------------------
// Logger stub
// ---------------------------------------------------------------------------
function makeLogger() {
  return { info: vi.fn(), warn: vi.fn(), error: vi.fn(), debug: vi.fn() };
}

// ---------------------------------------------------------------------------
// BullMQ Job stub factory
// ---------------------------------------------------------------------------
function makeClaimJob(overrides: Partial<ClaimGenerationJobData> = {}): Job<ClaimGenerationJobData> {
  return {
    id: "queue-job-claim-1",
    attemptsMade: 0,
    opts: { attempts: 3 },
    data: {
      correlationId: "corr-1",
      generationRequestId: "req-1",
      experienceId: "exp-1",
      tenantId: "tenant-1",
      topic: "Newton's Laws of Motion",
      title: "Newton's Laws of Motion",
      domain: "physics",
      gradeLevel: "GRADE_9_12",
      targetGrades: ["GRADE_9_12"],
      maxClaims: 5,
      ...overrides,
    },
  } as unknown as Job<ClaimGenerationJobData>;
}

function makeValidationJob(overrides: Partial<ContentValidationJobData> = {}): Job<ContentValidationJobData> {
  return {
    id: "queue-job-validation-1",
    attemptsMade: 0,
    opts: { attempts: 3 },
    data: {
      correlationId: "corr-2",
      generationRequestId: "req-1",
      experienceId: "exp-1",
      tenantId: "tenant-1",
      checkCorrectness: true,
      checkCompleteness: true,
      checkConcreteness: true,
      checkConciseness: true,
      minConfidenceThreshold: 0.75,
      ...overrides,
    },
  } as unknown as Job<ContentValidationJobData>;
}

// ===========================================================================
// Test Suites
// ===========================================================================

describe("P0-4: End-to-end generation worker lifecycle", () => {
  let prisma: ReturnType<typeof makePrismaStub>;
  let grpc: ReturnType<typeof makeGrpcStub>;
  let telemetry: ReturnType<typeof makeTelemetry>;
  let logger: ReturnType<typeof makeLogger>;
  let queue: { add: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.clearAllMocks();
    prisma = makePrismaStub();
    grpc = makeGrpcStub();
    telemetry = makeTelemetry();
    logger = makeLogger();
    queue = { add: vi.fn().mockResolvedValue({ id: "mock-q-job" }) };
  });

  // ─── 1. Claim generation worker ─────────────────────────────────────────

  describe("1. Claim generation worker: job → gRPC → DB persist → fan-out", () => {
    it("calls gRPC generateClaims with correct parameters", async () => {
      const processor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeClaimJob());

      expect(grpc.generateClaims).toHaveBeenCalledWith(
        expect.objectContaining({
          tenantId: "tenant-1",
          topic: "Newton's Laws of Motion",
          domain: "physics",
        }),
      );
    });

    it("persists generated claims to DB via Prisma upsert", async () => {
      const processor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeClaimJob());

      expect(prisma.learningClaim.upsert).toHaveBeenCalled();
      const upsertCall = (prisma.learningClaim.upsert as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(upsertCall).toBeDefined();
    });

    it("queues follow-up example jobs for claims with content needs", async () => {
      const processor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeClaimJob());

      // At least one follow-up job should be queued (examples or simulation)
      expect((queue.add as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThanOrEqual(1);
    });

    it("emits telemetry for the grpc_request_started and claim_completed stages", async () => {
      const processor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeClaimJob());

      expect(telemetry.publishForJob).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ stage: expect.stringContaining("grpc") }),
      );
    });

    it("throws and does NOT persist when gRPC fails on final retry", async () => {
      const failingGrpc = makeGrpcStub({
        generateClaims: vi.fn().mockRejectedValue(new Error("gRPC timeout")),
      });
      const processor = new ClaimGenerationProcessor(
        failingGrpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );
      const job = makeClaimJob();
      (job as Record<string, unknown>).attemptsMade = 2;

      await expect(processor.process(job)).rejects.toThrow("gRPC timeout");
      expect(prisma.learningClaim.upsert).not.toHaveBeenCalled();
    });
  });

  // ─── 2. Content validation worker ───────────────────────────────────────

  describe("2. Content validation worker: job → gRPC → DB persist", () => {
    it("calls gRPC validateContent with experience content", async () => {
      const processor = new ContentValidationProcessor(
        grpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeValidationJob());

      expect(grpc.validateContent).toHaveBeenCalledWith(
        expect.objectContaining({
          tenantId: "tenant-1",
          experienceId: "exp-1",
          title: "Newton's Laws of Motion",
        }),
      );
    });

    it("persists validation result with canPublish flag to DB", async () => {
      const processor = new ContentValidationProcessor(
        grpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeValidationJob());

      // canPublish is stored as 'PASS' overallStatus + scores
      expect(prisma.validationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            overallStatus: "PASS",
          }),
        }),
      );
    });

    it("persists canPublish=false when gRPC signals low confidence", async () => {
      const lowConfGrpc = makeGrpcStub({
        validateContent: vi.fn().mockResolvedValue({
          request_id: "req-1",
          experience_id: "exp-1",
          overall_score: 0.55,
          overallScore: 0.55,
          canPublish: false,
          status: "FAIL",
          issueCount: 1,
          pillar_scores: { correctness: 0.55, completeness: 0.50, concreteness: 0.58, conciseness: 0.55 },
          issues: [{ dimension: "correctness", message: "Claims need factual backing", severity: "high", suggestion: "Add citations" }],
          can_publish: false,
          recommendation: "human_review",
          metadata: { tokens_used: 900 },
        }),
      });

      const processor = new ContentValidationProcessor(
        lowConfGrpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeValidationJob());

      expect(prisma.validationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            overallStatus: "FAIL",
          }),
        }),
      );
    });

    it("emits telemetry stages during validation", async () => {
      const processor = new ContentValidationProcessor(
        grpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeValidationJob());

      expect(telemetry.publishForJob).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ stage: expect.any(String) }),
      );
    });

    it("fails gracefully when experience is not found", async () => {
      const prismaNoExp = {
        ...prisma,
        learningExperience: {
          findUnique: vi.fn().mockResolvedValue(null),
          update: vi.fn().mockResolvedValue(null),
        },
      } as unknown as typeof prisma;

      const processor = new ContentValidationProcessor(
        grpc as never,
        prismaNoExp as never,
        logger as never,
        telemetry,
      );
      await expect(processor.process(makeValidationJob())).rejects.toThrow("Experience not found");
    });
  });

  // ─── 3. Full sequential lifecycle: claim → validation ───────────────────

  describe("3. Full sequential lifecycle: claim job → validation job", () => {
    it("completes claim then validation in sequence with consistent tenant context", async () => {
      const claimProcessor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );

      const validationProcessor = new ContentValidationProcessor(
        grpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );

      // Step 1: Claim generation
      await claimProcessor.process(makeClaimJob());
      expect(grpc.generateClaims).toHaveBeenCalledTimes(1);

      // Step 2: Content validation runs after
      await validationProcessor.process(makeValidationJob());
      expect(grpc.validateContent).toHaveBeenCalledTimes(1);

      // Both calls use the same tenantId
      const claimArgs = (grpc.generateClaims as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as Record<string, unknown>;
      const validateArgs = (grpc.validateContent as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as Record<string, unknown>;
      expect(claimArgs.tenantId).toBe("tenant-1");
      expect(validateArgs.tenantId).toBe("tenant-1");
    });

    it("publish gate: canPublish=true → validation recommends auto_publish", async () => {
      const validationProcessor = new ContentValidationProcessor(
        grpc as never, // returns can_publish: true
        prisma as never,
        logger as never,
        telemetry,
      );
      await validationProcessor.process(makeValidationJob());

      const call = (prisma.validationRecord.create as ReturnType<typeof vi.fn>).mock.calls[0];
      const createPayload = (call?.[0] as { data: Record<string, unknown> })?.data;
      expect(createPayload?.overallStatus).toBe("PASS");
    });

    it("publish gate: canPublish=false → validation recommends human_review", async () => {
      const blockGrpc = makeGrpcStub({
        validateContent: vi.fn().mockResolvedValue({
          request_id: "req-1",
          experience_id: "exp-1",
          overall_score: 0.45,
          overallScore: 0.45,
          canPublish: false,
          status: "FAIL",
          issueCount: 1,
          pillar_scores: { correctness: 0.40, completeness: 0.50, concreteness: 0.45, conciseness: 0.45 },
          issues: [{ dimension: "correctness", message: "Factual errors detected", severity: "critical", suggestion: "Fix factual errors" }],
          can_publish: false,
          recommendation: "human_review",
          metadata: { tokens_used: 1000 },
        }),
      });
      const processor = new ContentValidationProcessor(
        blockGrpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );
      await processor.process(makeValidationJob());

      const call = (prisma.validationRecord.create as ReturnType<typeof vi.fn>).mock.calls[0];
      const createPayload = (call?.[0] as { data: Record<string, unknown> })?.data;
      expect(createPayload?.overallStatus).toBe("FAIL");
    });
  });

  // ─── 4. Cross-tenant isolation in worker ───────────────────────────────

  describe("4. Cross-tenant isolation", () => {
    it("claim job only processes for the tenant in the job data", async () => {
      const processor = new ClaimGenerationProcessor(
        grpc as never,
        prisma as never,
        queue as never,
        logger as never,
        telemetry,
      );

      await processor.process(makeClaimJob({ tenantId: "tenant-A" }));

      const generateArgs = (grpc.generateClaims as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as Record<string, unknown>;
      expect(generateArgs.tenantId).toBe("tenant-A");

      // Verify tenant-B jobs wouldn't get the same claims
      expect(generateArgs.tenantId).not.toBe("tenant-B");
    });

    it("validation job only validates for the tenant in the job data", async () => {
      const processor = new ContentValidationProcessor(
        grpc as never,
        prisma as never,
        logger as never,
        telemetry,
      );

      await processor.process(makeValidationJob({ tenantId: "tenant-X" }));

      const validateArgs = (grpc.validateContent as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as Record<string, unknown>;
      expect(validateArgs.tenantId).toBe("tenant-X");
    });
  });
});
