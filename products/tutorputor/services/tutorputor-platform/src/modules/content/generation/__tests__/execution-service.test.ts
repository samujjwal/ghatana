/**
 * Generation Execution Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify generation execution lifecycle and job status tracking
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  GenerationExecutionService,
  type JobExecutionResult,
} from "../execution-service";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------

function makeJob(overrides: Record<string, any> = {}) {
  return {
    id: "job-1",
    requestId: "req-1",
    jobType: "CLAIM",
    targetRef: "req-1/claim",
    inputPrompt: null,
    parameters: {},
    status: "PENDING",
    progress: 0,
    outputAssetId: null,
    outputData: null,
    diagnostics: null,
    errorMessage: null,
    retryCount: 0,
    maxRetries: 3,
    startedAt: null,
    completedAt: null,
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    ...overrides,
  };
}

function makeRequest(overrides: Record<string, any> = {}) {
  return {
    id: "req-1",
    tenantId: "tenant-1",
    title: "Test Request",
    description: null,
    domain: "physics",
    conceptId: null,
    targetGrades: null,
    requestedBy: "author-1",
    status: "PLANNED",
    plannedAssets: null,
    artifactNeeds: null,
    riskLevel: "LOW",
    riskFactors: null,
    reviewPath: "HUMAN_REVIEW",
    estimatedCost: null,
    totalJobs: 3,
    completedJobs: 0,
    failedJobs: 0,
    plannedAt: new Date("2025-06-01"),
    startedAt: null,
    completedAt: null,
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    jobs: [
      makeJob({ id: "job-1", jobType: "CLAIM" }),
      makeJob({ id: "job-2", jobType: "EXPLAINER" }),
      makeJob({ id: "job-3", jobType: "ASSESSMENT" }),
    ],
    ...overrides,
  };
}

function makePrisma() {
  const txProxy = {
    generationRequest: {
      update: vi.fn().mockResolvedValue(makeRequest({ status: "EXECUTING" })),
    },
    generationJob: {
      updateMany: vi.fn().mockResolvedValue({ count: 3 }),
    },
  };

  return {
    generationRequest: {
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockImplementation((args: any) => ({
        ...makeRequest(),
        ...args.data,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
      count: vi.fn().mockResolvedValue(0),
    },
    generationJob: {
      update: vi.fn().mockImplementation((args: any) => ({
        ...makeJob(),
        ...args.data,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
      updateMany: vi.fn().mockResolvedValue({ count: 0 }),
    },
    $transaction: vi.fn().mockImplementation(async (fn: any) => {
      return fn(txProxy);
    }),
    _txProxy: txProxy,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("GenerationExecutionService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: GenerationExecutionService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new GenerationExecutionService(prisma as any);
  });

  // =========================================================================
  // startExecution
  // =========================================================================

  describe("startExecution", () => {
    it("should fail if request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.startExecution("tenant-1", "nonexistent"),
      ).rejects.toThrow("not found");
    });

    it("should fail if request is not PLANNED", async () => {
      prisma.generationRequest.findFirst
        .mockResolvedValueOnce(makeRequest({ status: "DRAFT" }))
        .mockResolvedValueOnce(makeRequest({ status: "DRAFT" }));

      await expect(service.startExecution("tenant-1", "req-1")).rejects.toThrow(
        "Must be PLANNED",
      );
    });

    it("should transition request to EXECUTING", async () => {
      const planned = makeRequest({ status: "PLANNED" });
      const executing = makeRequest({
        status: "EXECUTING",
        jobs: planned.jobs.map((j: any) => ({ ...j, status: "RUNNING" })),
      });

      prisma.generationRequest.findFirst
        .mockResolvedValueOnce(planned) // First call: check status
        .mockResolvedValueOnce(executing); // Second call: re-fetch

      const result = await service.startExecution("tenant-1", "req-1");

      expect(prisma.$transaction).toHaveBeenCalledOnce();
      expect(result.status).toBe("executing");
    });

    it("should mark all PENDING jobs as RUNNING", async () => {
      const planned = makeRequest({ status: "PLANNED" });
      const executing = makeRequest({ status: "EXECUTING" });

      prisma.generationRequest.findFirst
        .mockResolvedValueOnce(planned)
        .mockResolvedValueOnce(executing);

      await service.startExecution("tenant-1", "req-1");

      expect(prisma._txProxy.generationJob.updateMany).toHaveBeenCalledWith({
        where: { requestId: "req-1", status: "PENDING" },
        data: expect.objectContaining({ status: "RUNNING" }),
      });
    });

    it("should set startedAt on the request", async () => {
      const planned = makeRequest({ status: "PLANNED" });
      const executing = makeRequest({ status: "EXECUTING" });

      prisma.generationRequest.findFirst
        .mockResolvedValueOnce(planned)
        .mockResolvedValueOnce(executing);

      await service.startExecution("tenant-1", "req-1");

      expect(prisma._txProxy.generationRequest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: "EXECUTING",
            startedAt: expect.any(Date),
          }),
        }),
      );
    });
  });

  // =========================================================================
  // recordJobResult
  // =========================================================================

  describe("recordJobResult", () => {
    it("should update job as COMPLETED on success", async () => {
      // maybeCompleteRequest needs to find the request
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [
            makeJob({ status: "COMPLETED" }),
            makeJob({ id: "job-2", status: "RUNNING" }),
          ],
        }),
      );

      const result: JobExecutionResult = {
        jobId: "job-1",
        status: "completed",
        outputData: { claims: [] },
        diagnostics: { durationMs: 100 },
        durationMs: 100,
      };

      await service.recordJobResult("req-1", result);

      expect(prisma.generationJob.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "job-1" },
          data: expect.objectContaining({
            status: "COMPLETED",
            progress: 100,
          }),
        }),
      );
    });

    it("should update job as FAILED on failure", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [makeJob({ status: "RUNNING" })],
        }),
      );

      const result: JobExecutionResult = {
        jobId: "job-1",
        status: "failed",
        errorMessage: "LLM timeout",
        durationMs: 5000,
      };

      await service.recordJobResult("req-1", result);

      expect(prisma.generationJob.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: "FAILED",
            errorMessage: "LLM timeout",
          }),
        }),
      );
    });

    it("should increment completedJobs counter on success", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [makeJob({ status: "RUNNING" })],
        }),
      );

      await service.recordJobResult("req-1", {
        jobId: "job-1",
        status: "completed",
        durationMs: 100,
      });

      expect(prisma.generationRequest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "req-1" },
          data: { completedJobs: { increment: 1 } },
        }),
      );
    });

    it("should increment failedJobs counter on failure", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [makeJob({ status: "RUNNING" })],
        }),
      );

      await service.recordJobResult("req-1", {
        jobId: "job-1",
        status: "failed",
        errorMessage: "Error",
        durationMs: 100,
      });

      expect(prisma.generationRequest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: { failedJobs: { increment: 1 } },
        }),
      );
    });

    it("should complete request when all jobs are done (all success)", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [
            makeJob({ id: "job-1", status: "COMPLETED" }),
            makeJob({ id: "job-2", status: "COMPLETED" }),
            makeJob({ id: "job-3", status: "COMPLETED" }),
          ],
        }),
      );

      await service.recordJobResult("req-1", {
        jobId: "job-3",
        status: "completed",
        durationMs: 100,
      });

      // Should call update twice: once for counter, once for completion
      const updateCalls = prisma.generationRequest.update.mock.calls;
      const completionCall = updateCalls.find(
        (c: any) => c[0].data.status === "COMPLETED",
      );
      expect(completionCall).toBeDefined();
    });

    it("should set request to FAILED when some jobs failed", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [
            makeJob({ id: "job-1", status: "COMPLETED" }),
            makeJob({ id: "job-2", status: "FAILED" }),
            makeJob({ id: "job-3", status: "COMPLETED" }),
          ],
        }),
      );

      await service.recordJobResult("req-1", {
        jobId: "job-3",
        status: "completed",
        durationMs: 100,
      });

      const updateCalls = prisma.generationRequest.update.mock.calls;
      const completionCall = updateCalls.find(
        (c: any) => c[0].data.status === "FAILED",
      );
      expect(completionCall).toBeDefined();
    });

    it("should not complete request when some jobs still running", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "EXECUTING",
          jobs: [
            makeJob({ id: "job-1", status: "COMPLETED" }),
            makeJob({ id: "job-2", status: "RUNNING" }),
            makeJob({ id: "job-3", status: "PENDING" }),
          ],
        }),
      );

      await service.recordJobResult("req-1", {
        jobId: "job-1",
        status: "completed",
        durationMs: 100,
      });

      const updateCalls = prisma.generationRequest.update.mock.calls;
      const completionCall = updateCalls.find(
        (c: any) =>
          c[0].data.status === "COMPLETED" || c[0].data.status === "FAILED",
      );
      expect(completionCall).toBeUndefined();
    });
  });

  // =========================================================================
  // recordBatchResults
  // =========================================================================

  describe("recordBatchResults", () => {
    it("should process all results and return summary", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "COMPLETED",
          totalJobs: 3,
          completedJobs: 3,
          failedJobs: 0,
        }),
      );

      const results: JobExecutionResult[] = [
        { jobId: "job-1", status: "completed", durationMs: 100 },
        { jobId: "job-2", status: "completed", durationMs: 200 },
        { jobId: "job-3", status: "completed", durationMs: 150 },
      ];

      const summary = await service.recordBatchResults("req-1", results);

      expect(summary.requestId).toBe("req-1");
      expect(summary.totalJobs).toBe(3);
      expect(summary.completedJobs).toBe(3);
      expect(summary.failedJobs).toBe(0);
      expect(summary.totalDurationMs).toBeGreaterThanOrEqual(0);
    });

    it("should handle mixed success/failure results", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          status: "FAILED",
          totalJobs: 2,
          completedJobs: 1,
          failedJobs: 1,
        }),
      );

      const results: JobExecutionResult[] = [
        { jobId: "job-1", status: "completed", durationMs: 100 },
        {
          jobId: "job-2",
          status: "failed",
          errorMessage: "LLM error",
          durationMs: 200,
        },
      ];

      const summary = await service.recordBatchResults("req-1", results);

      expect(summary.completedJobs).toBe(1);
      expect(summary.failedJobs).toBe(1);
    });
  });
});
