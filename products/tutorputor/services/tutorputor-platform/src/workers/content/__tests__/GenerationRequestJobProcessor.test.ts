import { describe, it, expect, vi, beforeEach } from "vitest";
import { GenerationRequestJobProcessor } from "../processors/GenerationRequestJobProcessor";

function makeJob(
  dataOverrides: Record<string, any> = {},
  jobOverrides: Record<string, any> = {},
) {
  return {
    id: "queue-job-1",
    attemptsMade: 0,
    opts: { attempts: 3 },
    data: {
      generationRequestId: "req-1",
      generationJobId: "job-1",
      tenantId: "tenant-1",
      requestedBy: "author-1",
      requestTitle: "Newton's Laws",
      requestDescription: "Understand inertia",
      domain: "physics",
      targetGrades: ["GRADE_9_12"],
      generationJobType: "claim",
      targetRef: "req-1/claim",
      parameters: { dependsOn: [] },
      ...dataOverrides,
    },
    ...jobOverrides,
  } as any;
}

describe("GenerationRequestJobProcessor", () => {
  let grpcClient: any;
  let telemetry: any;
  let executionService: any;
  let dispatcher: any;
  let logger: any;
  let processor: GenerationRequestJobProcessor;

  beforeEach(() => {
    grpcClient = {
      generateClaims: vi.fn().mockResolvedValue({
        claims: [{ claim_ref: "C1", text: "Objects resist motion changes" }],
        metadata: { tokens_used: 1200 },
      }),
      generateExamples: vi.fn(),
      generateSimulation: vi.fn(),
      generateAnimation: vi.fn(),
    };
    telemetry = {
      publishForJob: vi.fn().mockResolvedValue(undefined),
    };
    executionService = {
      recordJobResult: vi.fn().mockResolvedValue(undefined),
      recordBatchResults: vi.fn().mockResolvedValue(undefined),
    };
    dispatcher = {
      collectDependencyFailureResults: vi.fn().mockResolvedValue([]),
      dispatchReadyJobs: vi.fn().mockResolvedValue({
        requestId: "req-1",
        queuedJobs: [],
        blockedJobs: [],
        skippedJobs: [],
      }),
    };
    logger = {
      error: vi.fn(),
    };

    processor = new GenerationRequestJobProcessor(
      grpcClient,
      {} as any,
      logger,
      telemetry,
      executionService,
      dispatcher,
    );
  });

  it("executes claim jobs and persists a completed generation result", async () => {
    await processor.process(makeJob());

    expect(grpcClient.generateClaims).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: "tenant-1",
        topic: "Newton's Laws",
        maxClaims: 1,
      }),
    );
    expect(executionService.recordJobResult).toHaveBeenCalledWith(
      "req-1",
      expect.objectContaining({
        jobId: "job-1",
        status: "completed",
        outputData: expect.objectContaining({
          claims: expect.any(Array),
          count: 1,
        }),
      }),
    );
    expect(dispatcher.dispatchReadyJobs).toHaveBeenCalledWith(
      "tenant-1",
      "req-1",
    );
  });

  it("records a failed result on the final retry", async () => {
    grpcClient.generateClaims.mockRejectedValue(new Error("gRPC unavailable"));

    await expect(
      processor.process(
        makeJob({ generationJobId: "job-2" }, { attemptsMade: 2 }),
      ),
    ).rejects.toThrow("gRPC unavailable");

    expect(executionService.recordJobResult).toHaveBeenCalledWith(
      "req-1",
      expect.objectContaining({
        jobId: "job-2",
        status: "failed",
        errorMessage: "gRPC unavailable",
      }),
    );
  });

  it("materializes explainer jobs into canonical assets before persisting results", async () => {
    grpcClient.generateExamples.mockResolvedValue({
      examples: [
        {
          title: "Inertia in a Car",
          description: "Why passengers move forward when a car stops.",
          content: "Passengers keep moving because of inertia.",
        },
      ],
      metadata: { tokens_used: 800 },
    });

    const prisma = {
      contentAsset: {
        findFirst: vi.fn().mockResolvedValue(null),
        create: vi.fn().mockResolvedValue({
          id: "asset-1",
          currentVersion: 1,
          slug: "asset-1",
        }),
        update: vi.fn(),
      },
      contentBlock: {
        create: vi.fn().mockResolvedValue({ id: "block-1" }),
        deleteMany: vi.fn(),
      },
      artifactManifest: {
        create: vi.fn(),
        deleteMany: vi.fn(),
      },
      contentAssetRevision: {
        create: vi.fn().mockResolvedValue({ id: "rev-1" }),
      },
    };

    processor = new GenerationRequestJobProcessor(
      grpcClient,
      prisma as any,
      logger,
      telemetry,
      executionService,
      dispatcher,
    );

    await processor.process(
      makeJob({ generationJobType: "explainer", generationJobId: "job-3" }),
    );

    expect(prisma.contentAsset.create).toHaveBeenCalled();
    expect(executionService.recordJobResult).toHaveBeenCalledWith(
      "req-1",
      expect.objectContaining({
        jobId: "job-3",
        outputAssetId: "asset-1",
        outputData: expect.objectContaining({
          assetId: "asset-1",
          materialization: expect.objectContaining({
            assetId: "asset-1",
            assetType: "explainer",
          }),
        }),
      }),
    );
  });

  it("runs the quality loop for evaluation jobs", async () => {
    const prisma = {
      generationRequest: {
        findFirst: vi.fn().mockResolvedValue({
          id: "req-1",
          reviewPath: "AUTO_PUBLISH",
          tenantId: "tenant-1",
          jobs: [],
        }),
      },
      evaluationRecord: {
        create: vi.fn(),
        deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
        findMany: vi.fn().mockResolvedValue([]),
        findFirst: vi.fn().mockResolvedValue(null),
      },
      regenerationCandidate: {
        findFirst: vi.fn().mockResolvedValue(null),
        create: vi.fn(),
        findMany: vi.fn().mockResolvedValue([]),
      },
      generationReviewDecision: {
        findFirst: vi.fn().mockResolvedValue(null),
        create: vi.fn(),
      },
      contentAsset: {
        update: vi.fn(),
        findFirst: vi.fn(),
      },
    };

    processor = new GenerationRequestJobProcessor(
      grpcClient,
      prisma as any,
      logger,
      telemetry,
      executionService,
      dispatcher,
    );

    await processor.process(makeJob({ generationJobType: "evaluation" }));

    expect(executionService.recordJobResult).toHaveBeenCalledWith(
      "req-1",
      expect.objectContaining({
        outputData: expect.objectContaining({
          evaluationStatus: expect.any(String),
          qualityLoop: expect.objectContaining({
            requestId: "req-1",
          }),
        }),
      }),
    );
  });

  describe("failure state machine — non-final retry", () => {
    it("does NOT call recordJobResult on a non-final retry attempt (attemptsMade < attempts - 1)", async () => {
      grpcClient.generateClaims.mockRejectedValue(new Error("gRPC timeout"));

      // opts.attempts=3, attemptsMade=0 → isFinalAttempt = (0+1 >= 3) = false
      await expect(
        processor.process(
          makeJob(
            { generationJobId: "job-retry" },
            { attemptsMade: 0, opts: { attempts: 3 } },
          ),
        ),
      ).rejects.toThrow("gRPC timeout");

      // Must NOT record a result on non-final attempts
      expect(executionService.recordJobResult).not.toHaveBeenCalled();
      // Must NOT collect dependency failures prematurely
      expect(dispatcher.collectDependencyFailureResults).not.toHaveBeenCalled();
      // Error must be logged
      expect(logger.error).toHaveBeenCalledWith(
        expect.objectContaining({ generationJobId: "job-retry" }),
        expect.any(String),
      );
    });

    it("re-throws the error on a non-final retry so BullMQ can reschedule", async () => {
      grpcClient.generateClaims.mockRejectedValue(
        new Error("transient failure"),
      );

      await expect(
        processor.process(
          makeJob(
            { generationJobId: "job-transient" },
            { attemptsMade: 1, opts: { attempts: 3 } },
          ),
        ),
      ).rejects.toThrow("transient failure");
    });
  });

  describe("failure state machine — cascade dependency skip", () => {
    it("calls collectDependencyFailureResults and recordBatchResults when downstream jobs are blocked", async () => {
      grpcClient.generateClaims.mockRejectedValue(
        new Error("gRPC unavailable"),
      );

      const blockedResults = [
        {
          jobId: "job-dep-1",
          status: "skipped" as const,
          errorMessage: "upstream failed",
          durationMs: 0,
        },
        {
          jobId: "job-dep-2",
          status: "skipped" as const,
          errorMessage: "upstream failed",
          durationMs: 0,
        },
      ];
      dispatcher.collectDependencyFailureResults.mockResolvedValue(
        blockedResults,
      );

      // Final attempt: attemptsMade=2, attempts=3 → isFinalAttempt = (2+1 >= 3) = true
      await expect(
        processor.process(
          makeJob(
            { generationJobId: "job-upstream" },
            { attemptsMade: 2, opts: { attempts: 3 } },
          ),
        ),
      ).rejects.toThrow("gRPC unavailable");

      expect(dispatcher.collectDependencyFailureResults).toHaveBeenCalledWith(
        "tenant-1",
        "req-1",
      );
      expect(executionService.recordBatchResults).toHaveBeenCalledWith(
        "req-1",
        blockedResults,
      );
    });

    it("does NOT call recordBatchResults when there are no blocked dependents", async () => {
      grpcClient.generateClaims.mockRejectedValue(
        new Error("gRPC unavailable"),
      );
      dispatcher.collectDependencyFailureResults.mockResolvedValue([]);

      await expect(
        processor.process(
          makeJob(
            { generationJobId: "job-no-deps" },
            { attemptsMade: 2, opts: { attempts: 3 } },
          ),
        ),
      ).rejects.toThrow();

      expect(executionService.recordBatchResults).not.toHaveBeenCalled();
    });
  });
});
