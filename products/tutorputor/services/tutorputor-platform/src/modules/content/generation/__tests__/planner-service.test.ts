/**
 * Generation Planner Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify generation request lifecycle and planning logic
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { GenerationPlannerService } from "../planner-service";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------

function makePrisma() {
  const txProxy = {
    generationJob: {
      create: vi.fn().mockImplementation((args: any) => ({
        id: `job-${Math.random().toString(36).slice(2, 8)}`,
        ...args.data,
        progress: 0,
        outputAssetId: null,
        outputData: null,
        diagnostics: null,
        errorMessage: null,
        retryCount: 0,
        maxRetries: 3,
        startedAt: null,
        completedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
    },
    generationRequest: {
      update: vi.fn().mockImplementation((args: any) => ({
        ...args.data,
        id: args.where.id,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
    },
  };

  return {
    generationRequest: {
      create: vi.fn().mockImplementation((args: any) => ({
        id: "req-1",
        ...args.data,
        status: args.data.status ?? "DRAFT",
        riskLevel: "LOW",
        reviewPath: "HUMAN_REVIEW",
        totalJobs: 0,
        completedJobs: 0,
        failedJobs: 0,
        plannedAt: null,
        startedAt: null,
        completedAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
      update: vi.fn().mockImplementation((args: any) => ({
        id: args.where.id,
        tenantId: "tenant-1",
        title: "Test Request",
        domain: "math",
        requestedBy: "author-1",
        status: args.data.status ?? "DRAFT",
        riskLevel: args.data.riskLevel ?? "LOW",
        reviewPath: args.data.reviewPath ?? "HUMAN_REVIEW",
        totalJobs: 0,
        completedJobs: 0,
        failedJobs: 0,
        ...args.data,
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
    },
    generationJob: {
      create: vi.fn(),
      updateMany: vi.fn().mockResolvedValue({ count: 0 }),
    },
    $transaction: vi.fn().mockImplementation(async (fn: any) => {
      return fn(txProxy);
    }),
    _txProxy: txProxy,
  };
}

function makeRequest(overrides: Record<string, any> = {}) {
  return {
    id: "req-1",
    tenantId: "tenant-1",
    title: "Newton's Laws Explainer",
    description: null,
    domain: "physics",
    conceptId: "concept-motion",
    targetGrades: ["9", "10"],
    requestedBy: "author-1",
    status: "DRAFT",
    plannedAssets: null,
    artifactNeeds: null,
    riskLevel: "LOW",
    riskFactors: null,
    reviewPath: "HUMAN_REVIEW",
    estimatedCost: null,
    totalJobs: 0,
    completedJobs: 0,
    failedJobs: 0,
    plannedAt: null,
    startedAt: null,
    completedAt: null,
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("GenerationPlannerService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: GenerationPlannerService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new GenerationPlannerService(prisma as any);
  });

  // =========================================================================
  // createRequest
  // =========================================================================

  describe("createRequest", () => {
    it("should create a DRAFT generation request", async () => {
      const result = await service.createRequest({
        tenantId: "tenant-1",
        title: "Newton's Laws",
        domain: "physics",
        requestedBy: "author-1",
      });

      expect(prisma.generationRequest.create).toHaveBeenCalledOnce();
      expect(result.status).toBe("draft");
      expect(result.tenantId).toBe("tenant-1");
      expect(result.title).toBe("Newton's Laws");
      expect(result.domain).toBe("physics");
    });

    it("should pass optional fields to persistence", async () => {
      await service.createRequest({
        tenantId: "tenant-1",
        title: "Fractions",
        description: "Learn about fractions",
        domain: "math",
        conceptId: "concept-fractions",
        targetGrades: ["5", "6"],
        requestedBy: "author-2",
      });

      const callData = prisma.generationRequest.create.mock.calls[0][0].data;
      expect(callData.description).toBe("Learn about fractions");
      expect(callData.conceptId).toBe("concept-fractions");
      expect(callData.targetGrades).toEqual(["5", "6"]);
    });

    it("should default optional fields to null", async () => {
      await service.createRequest({
        tenantId: "tenant-1",
        title: "Basics",
        domain: "math",
        requestedBy: "author-1",
      });

      const callData = prisma.generationRequest.create.mock.calls[0][0].data;
      expect(callData.description).toBeNull();
      expect(callData.conceptId).toBeNull();
      expect(callData.targetGrades).toBeNull();
    });
  });

  // =========================================================================
  // getRequest
  // =========================================================================

  describe("getRequest", () => {
    it("should return null when request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      const result = await service.getRequest("tenant-1", "nonexistent");
      expect(result).toBeNull();
    });

    it("should return request with jobs", async () => {
      const req = makeRequest({ jobs: [] });
      prisma.generationRequest.findFirst.mockResolvedValue(req);

      const result = await service.getRequest("tenant-1", "req-1");

      expect(result).not.toBeNull();
      expect(result!.id).toBe("req-1");
      expect(result!.jobs).toEqual([]);
    });

    it("should map jobs correctly", async () => {
      const req = makeRequest({
        jobs: [
          {
            id: "job-1",
            requestId: "req-1",
            jobType: "CLAIM",
            targetRef: "req-1/claim",
            inputPrompt: "Generate claim",
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
          },
        ],
      });
      prisma.generationRequest.findFirst.mockResolvedValue(req);

      const result = await service.getRequest("tenant-1", "req-1");
      expect(result!.jobs).toHaveLength(1);
      expect(result!.jobs[0].jobType).toBe("claim");
      expect(result!.jobs[0].status).toBe("pending");
    });
  });

  // =========================================================================
  // listRequests
  // =========================================================================

  describe("listRequests", () => {
    it("should return items and total", async () => {
      prisma.generationRequest.findMany.mockResolvedValue([
        makeRequest(),
        makeRequest({ id: "req-2" }),
      ]);
      prisma.generationRequest.count.mockResolvedValue(2);

      const result = await service.listRequests("tenant-1");
      expect(result.items).toHaveLength(2);
      expect(result.total).toBe(2);
    });

    it("should filter by status", async () => {
      prisma.generationRequest.findMany.mockResolvedValue([]);
      prisma.generationRequest.count.mockResolvedValue(0);

      await service.listRequests("tenant-1", { status: "planned" });

      const where = prisma.generationRequest.findMany.mock.calls[0][0].where;
      expect(where.status).toBe("PLANNED");
    });

    it("should apply pagination", async () => {
      prisma.generationRequest.findMany.mockResolvedValue([]);
      prisma.generationRequest.count.mockResolvedValue(0);

      await service.listRequests("tenant-1", { limit: 5, offset: 10 });

      const args = prisma.generationRequest.findMany.mock.calls[0][0];
      expect(args.take).toBe(5);
      expect(args.skip).toBe(10);
    });
  });

  // =========================================================================
  // planRequest — Core Planning Logic
  // =========================================================================

  describe("planRequest", () => {
    it("should fail if request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.planRequest("tenant-1", "nonexistent"),
      ).rejects.toThrow("not found");
    });

    it("should fail if request is not in DRAFT status", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "PLANNED" }),
      );

      await expect(service.planRequest("tenant-1", "req-1")).rejects.toThrow(
        "Must be DRAFT",
      );
    });

    it("should produce standard artifacts for non-visual domain", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      // Standard set: claim, explainer, worked_example, assessment + evaluation
      expect(result.plannedAssets).toHaveLength(5);
      const types = result.plannedAssets.map((a) => a.jobType);
      expect(types).toContain("claim");
      expect(types).toContain("explainer");
      expect(types).toContain("worked_example");
      expect(types).toContain("assessment");
      expect(types).toContain("evaluation");
      expect(types).not.toContain("simulation");
      expect(types).not.toContain("animation");
    });

    it("should produce extended artifacts for visual domain (physics)", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "physics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      // Standard + simulation + animation + evaluation = 7
      expect(result.plannedAssets).toHaveLength(7);
      const types = result.plannedAssets.map((a) => a.jobType);
      expect(types).toContain("simulation");
      expect(types).toContain("animation");
    });

    it("should produce extended artifacts for visual domain (chemistry)", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "chemistry" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      const types = result.plannedAssets.map((a) => a.jobType);
      expect(types).toContain("simulation");
      expect(types).toContain("animation");
    });

    it("should set evaluation as last job with dependencies on all others", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      const evalJob = result.plannedAssets.find(
        (a) => a.jobType === "evaluation",
      );

      expect(evalJob).toBeDefined();
      expect(evalJob!.dependsOn).toBeDefined();
      expect(evalJob!.dependsOn!.length).toBe(result.plannedAssets.length - 1);
    });

    it("should compute artifact needs correctly", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "physics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      expect(result.artifactNeeds.claim).toBe(1);
      expect(result.artifactNeeds.explainer).toBe(1);
      expect(result.artifactNeeds.simulation).toBe(1);
      expect(result.artifactNeeds.animation).toBe(1);
      expect(result.artifactNeeds.evaluation).toBe(1);
    });

    it("should persist jobs in a transaction", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      await service.planRequest("tenant-1", "req-1");

      expect(prisma.$transaction).toHaveBeenCalledOnce();
      // 5 jobs created for math (non-visual): claim, explainer, worked_example, assessment, evaluation
      expect(prisma._txProxy.generationJob.create).toHaveBeenCalledTimes(5);
    });

    it("should transition status from DRAFT → PLANNING → PLANNED", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      await service.planRequest("tenant-1", "req-1");

      // First update: PLANNING
      expect(prisma.generationRequest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: { status: "PLANNING" },
        }),
      );

      // Second update (in transaction): PLANNED
      const txUpdate = prisma._txProxy.generationRequest.update;
      expect(txUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: "PLANNED",
          }),
        }),
      );
    });

    it("should include cost estimation", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      expect(result.estimatedCost.totalTokens).toBeGreaterThan(0);
      expect(result.estimatedCost.llmCalls).toBe(5);
      expect(result.estimatedCost.embeddingCalls).toBe(4); // All except evaluation
      expect(result.estimatedCost.estimatedDurationMs).toBeGreaterThan(0);
    });

    it("should set totalJobs to planned asset count", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.totalJobs).toBe(5);
    });
  });

  // =========================================================================
  // Risk Assessment
  // =========================================================================

  describe("risk assessment", () => {
    it("should return LOW risk for standard educational content", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math", title: "Addition and Subtraction" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.riskLevel).toBe("low");
      expect(result.riskFactors).toHaveLength(0);
    });

    it("should return HIGH risk for health-related domain", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "health", title: "Nutrition Basics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.riskLevel).toBe("high");
      expect(result.riskFactors.some((f: string) => f.includes("health"))).toBe(
        true,
      );
    });

    it("should return CRITICAL risk for multiple high-risk keywords", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          domain: "medical",
          title: "Chemical Safety Procedures",
        }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.riskLevel).toBe("critical");
    });

    it("should return MEDIUM risk for sensitive keywords", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          domain: "history",
          title: "Controversial Historical Events",
        }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.riskLevel).toBe("medium");
      expect(
        result.riskFactors.some((f: string) => f.includes("sensitive")),
      ).toBe(true);
    });

    it("should flag young learner grades as additional risk factor", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          domain: "science",
          title: "Basic Biology",
          targetGrades: ["kindergarten", "1st"],
        }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(
        result.riskFactors.some((f: string) => f.includes("young learners")),
      ).toBe(true);
    });
  });

  // =========================================================================
  // Review Path
  // =========================================================================

  describe("review path", () => {
    it("should route LOW risk to auto_publish", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math", title: "Algebra Basics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.reviewPath).toBe("auto_publish");
    });

    it("should route MEDIUM risk to human_review", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({
          domain: "history",
          title: "Controversial Figures",
        }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.reviewPath).toBe("human_review");
    });

    it("should route HIGH risk to expert_review", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "health", title: "Medical Topics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      expect(result.reviewPath).toBe("expert_review");
    });
  });

  // =========================================================================
  // cancelRequest
  // =========================================================================

  describe("cancelRequest", () => {
    it("should cancel a DRAFT request", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "DRAFT" }),
      );
      prisma.generationRequest.update.mockResolvedValue(
        makeRequest({ status: "CANCELLED" }),
      );

      const result = await service.cancelRequest("tenant-1", "req-1");
      expect(result.status).toBe("cancelled");
    });

    it("should cancel a PLANNED request", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "PLANNED" }),
      );
      prisma.generationRequest.update.mockResolvedValue(
        makeRequest({ status: "CANCELLED" }),
      );

      const result = await service.cancelRequest("tenant-1", "req-1");
      expect(result.status).toBe("cancelled");
    });

    it("should cancel pending/running jobs when cancelling", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "EXECUTING" }),
      );
      prisma.generationRequest.update.mockResolvedValue(
        makeRequest({ status: "CANCELLED" }),
      );

      await service.cancelRequest("tenant-1", "req-1");

      expect(prisma.generationJob.updateMany).toHaveBeenCalledWith({
        where: {
          requestId: "req-1",
          status: { in: ["PENDING", "RUNNING"] },
        },
        data: { status: "CANCELLED" },
      });
    });

    it("should fail if request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.cancelRequest("tenant-1", "nonexistent"),
      ).rejects.toThrow("not found");
    });

    it("should fail if request is already COMPLETED", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "COMPLETED" }),
      );

      await expect(service.cancelRequest("tenant-1", "req-1")).rejects.toThrow(
        "terminal status",
      );
    });

    it("should fail if request is already CANCELLED", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "CANCELLED" }),
      );

      await expect(service.cancelRequest("tenant-1", "req-1")).rejects.toThrow(
        "terminal status",
      );
    });

    it("should fail if request is already FAILED", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "FAILED" }),
      );

      await expect(service.cancelRequest("tenant-1", "req-1")).rejects.toThrow(
        "terminal status",
      );
    });
  });

  // =========================================================================
  // Planned Asset Descriptors
  // =========================================================================

  describe("planned asset descriptors", () => {
    it("should include targetRef with request id prefix", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      for (const asset of result.plannedAssets) {
        expect(asset.targetRef).toMatch(/^req-1\//);
      }
    });

    it("should include description for each planned asset", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      for (const asset of result.plannedAssets) {
        expect(asset.description).toBeTruthy();
        expect(asset.description.length).toBeGreaterThan(10);
      }
    });

    it("should include estimated tokens for each planned asset", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "math" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");

      for (const asset of result.plannedAssets) {
        expect(asset.estimatedTokens).toBeGreaterThan(0);
      }
    });

    it("should set dependencies for simulation/animation on claim", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ domain: "physics" }),
      );

      const result = await service.planRequest("tenant-1", "req-1");
      const sim = result.plannedAssets.find((a) => a.jobType === "simulation");
      const anim = result.plannedAssets.find((a) => a.jobType === "animation");

      expect(sim!.dependsOn).toContain("req-1/claim");
      expect(anim!.dependsOn).toContain("req-1/claim");
    });
  });
});
