import { describe, it, expect, vi, beforeEach } from "vitest";
import { GenerationQueueDispatcher } from "../queue-dispatcher";

function makeJob(overrides: Record<string, any> = {}) {
  return {
    id: "job-1",
    requestId: "req-1",
    jobType: "CLAIM",
    status: "PENDING",
    progress: 0,
    retryCount: 0,
    maxRetries: 3,
    targetRef: "req-1/claim",
    inputPrompt: "Generate claim",
    parameters: { dependsOn: [] },
    diagnostics: null,
    outputData: null,
    errorMessage: null,
    createdAt: new Date("2025-06-01T10:00:00Z"),
    updatedAt: new Date("2025-06-01T10:00:00Z"),
    ...overrides,
  };
}

function makeRequest(overrides: Record<string, any> = {}) {
  return {
    id: "req-1",
    tenantId: "tenant-1",
    title: "Newton's Laws",
    description: "Understand motion",
    domain: "physics",
    requestedBy: "author-1",
    requestConfig: { urgent: true },
    status: "EXECUTING",
    riskLevel: "LOW",
    reviewPath: "HUMAN_REVIEW",
    totalJobs: 3,
    completedJobs: 0,
    failedJobs: 0,
    plannedAssets: [],
    artifactNeeds: {},
    createdAt: new Date("2025-06-01T10:00:00Z"),
    updatedAt: new Date("2025-06-01T10:00:00Z"),
    jobs: [
      makeJob(),
      makeJob({
        id: "job-2",
        jobType: "SIMULATION",
        targetRef: "req-1/simulation",
        parameters: { dependsOn: ["req-1/claim"] },
      }),
      makeJob({
        id: "job-3",
        jobType: "EVALUATION",
        targetRef: "req-1/evaluation",
        parameters: {
          dependsOn: ["req-1/claim", "req-1/simulation"],
        },
      }),
    ],
    ...overrides,
  };
}

describe("GenerationQueueDispatcher", () => {
  let prisma: any;
  let queue: { add: ReturnType<typeof vi.fn> };
  let dispatcher: GenerationQueueDispatcher;

  beforeEach(() => {
    prisma = {
      generationRequest: {
        findFirst: vi.fn().mockResolvedValue(makeRequest()),
      },
      generationJob: {
        update: vi.fn().mockResolvedValue(undefined),
      },
    };
    queue = {
      add: vi.fn().mockResolvedValue({ id: "queue-job-1" }),
    };
    dispatcher = new GenerationQueueDispatcher(prisma, queue);
  });

  it("dispatches only dependency-ready jobs", async () => {
    const summary = await dispatcher.dispatchReadyJobs("tenant-1", "req-1");

    expect(queue.add).toHaveBeenCalledTimes(1);
    expect(queue.add).toHaveBeenCalledWith(
      "execute-generation-job",
      expect.objectContaining({
        generationRequestId: "req-1",
        generationJobId: "job-1",
        generationJobType: "claim",
      }),
      expect.objectContaining({
        jobId: "execute-generation-job:req-1:job-1",
      }),
    );
    expect(summary.queuedJobs).toHaveLength(1);
    expect(summary.skippedJobs).toContain("job-2");
    expect(summary.skippedJobs).toContain("job-3");
  });

  it("marks jobs blocked by failed dependencies for fast failure", async () => {
    prisma.generationRequest.findFirst.mockResolvedValue(
      makeRequest({
        jobs: [
          makeJob({
            id: "job-1",
            status: "FAILED",
            targetRef: "req-1/claim",
          }),
          makeJob({
            id: "job-2",
            jobType: "SIMULATION",
            targetRef: "req-1/simulation",
            parameters: { dependsOn: ["req-1/claim"] },
          }),
        ],
      }),
    );

    const results = await dispatcher.collectDependencyFailureResults(
      "tenant-1",
      "req-1",
    );

    expect(results).toEqual([
      expect.objectContaining({
        jobId: "job-2",
        status: "failed",
        errorMessage: expect.stringContaining("req-1/claim"),
      }),
    ]);
  });
});
