import { beforeEach, describe, expect, it, vi } from "vitest";
import { GenerationQualityLoopService } from "../quality-loop-service";

function makeRequest(overrides: Record<string, unknown> = {}) {
  return {
    id: "req-1",
    reviewPath: "AUTO_PUBLISH",
    jobs: [],
    ...overrides,
  };
}

function makeEvaluationRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "eval-1",
    tenantId: "tenant-1",
    assetId: "asset-1",
    generationJobId: "job-1",
    generationRequestId: "req-1",
    coherenceScore: 0.9,
    completenessScore: 0.9,
    safetyScore: 1,
    accessibilityScore: 0.95,
    manifestValidityScore: 1,
    overallScore: 0.93,
    status: "PASSED",
    recommendation: "AUTO_PUBLISH",
    issues: [],
    diagnostics: { jobType: "simulation", jobId: "job-1" },
    errorMessage: null,
    createdAt: new Date("2025-06-01T00:00:00Z"),
    updatedAt: new Date("2025-06-01T00:00:00Z"),
    ...overrides,
  };
}

function makePrisma() {
  return {
    generationRequest: {
      findFirst: vi.fn().mockResolvedValue(makeRequest()),
    },
    evaluationRecord: {
      create: vi.fn().mockImplementation((args: any) =>
        Promise.resolve(
          makeEvaluationRow({
            assetId: args.data.assetId,
            generationJobId: args.data.generationJobId,
            generationRequestId: args.data.generationRequestId,
            overallScore: args.data.overallScore,
            recommendation: args.data.recommendation,
            status: args.data.status,
            issues: args.data.issues ?? [],
            diagnostics: args.data.diagnostics,
          }),
        ),
      ),
      deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
      findMany: vi.fn().mockResolvedValue([makeEvaluationRow()]),
      findFirst: vi.fn().mockResolvedValue(null),
    },
    regenerationCandidate: {
      findFirst: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({
        id: "cand-1",
        tenantId: "tenant-1",
        assetId: "asset-1",
        assetType: "simulation",
        trigger: "LOW_EVALUATION_SCORE",
        severity: "MEDIUM",
        reason: "Evaluation requires manual review before release",
        evidence: {},
        priority: 65,
        status: "OPEN",
        generationRequestId: null,
        resolvedBy: null,
        resolvedAt: null,
        createdAt: new Date("2025-06-01T00:00:00Z"),
        updatedAt: new Date("2025-06-01T00:00:00Z"),
      }),
      findMany: vi.fn().mockResolvedValue([]),
      update: vi.fn(),
    },
    generationReviewDecision: {
      findFirst: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({
        id: "dec-1",
        tenantId: "tenant-1",
        requestId: "req-1",
        status: "PENDING",
        reviewedBy: null,
        decisionNote: "Awaiting reviewer",
        regenerateJobIds: null,
        reviewedAt: null,
        createdAt: new Date("2025-06-01T00:00:00Z"),
        updatedAt: new Date("2025-06-01T00:00:00Z"),
      }),
    },
    contentAsset: {
      update: vi.fn().mockResolvedValue({ id: "asset-1" }),
      findFirst: vi.fn().mockResolvedValue({
        id: "asset-1",
        tenantId: "tenant-1",
        assetType: "SIMULATION",
        manifestData: { id: "manifest-1" },
      }),
    },
    artifactManifest: {
      findMany: vi.fn().mockResolvedValue([
        {
          id: "manifest-1",
          assetId: "asset-1",
          manifestType: "simulation",
          status: "PUBLISHED",
          manifest: {},
          generatedBy: "SYSTEM",
          createdBy: "user-1",
          version: "1.0.0",
          createdAt: new Date("2025-06-01T00:00:00Z"),
          updatedAt: new Date("2025-06-01T00:00:00Z"),
        },
      ]),
    },
  };
}

describe("GenerationQualityLoopService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: GenerationQualityLoopService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new GenerationQualityLoopService(prisma as never);
  });

  function mockEvaluationOutcome(input: {
    recommendation: "auto_publish" | "manual_review" | "block";
    overallScore: number;
    records: ReturnType<typeof makeEvaluationRow>[];
  }) {
    vi.spyOn((service as any).evaluationService, "evaluateGenerationRequest").mockResolvedValue({
      evaluationId: "eval-scorecard-1",
      generationRequestId: "req-1",
      overallScore: input.overallScore,
      recommendation: input.recommendation,
      dimensions: {
        coherence: input.overallScore,
        completeness: input.overallScore,
        safety: 1,
        accessibility: 0.95,
        manifestValidity: 1,
      },
      issues: input.records.flatMap((record) => record.issues ?? []),
      blockedReasons: input.records
        .flatMap((record) => record.issues ?? [])
        .filter((issue: any) => issue.severity === "error")
        .map((issue: any) => issue.message),
    });
    vi.spyOn((service as any).evaluationService, "getEvaluationsByRequest").mockResolvedValue(
      input.records.map((row) => ({
        id: row.id,
        tenantId: row.tenantId,
        assetId: row.assetId ?? undefined,
        generationJobId: row.generationJobId ?? undefined,
        generationRequestId: row.generationRequestId ?? undefined,
        coherenceScore: row.coherenceScore ?? undefined,
        completenessScore: row.completenessScore ?? undefined,
        safetyScore: row.safetyScore ?? undefined,
        accessibilityScore: row.accessibilityScore ?? undefined,
        manifestValidityScore: row.manifestValidityScore ?? undefined,
        overallScore: row.overallScore ?? undefined,
        status: String(row.status).toLowerCase() as
          | "pending"
          | "running"
          | "passed"
          | "failed"
          | "skipped",
        recommendation: String(row.recommendation).toLowerCase() as
          | "auto_publish"
          | "manual_review"
          | "block",
        issues: (row.issues as any[]) ?? undefined,
        diagnostics: (row.diagnostics as Record<string, unknown>) ?? undefined,
        errorMessage: undefined,
        createdAt: row.createdAt.toISOString(),
        updatedAt: row.updatedAt.toISOString(),
      })),
    );
  }

  it("auto-publishes passing auto-publish requests with eligible assets", async () => {
    mockEvaluationOutcome({
      recommendation: "auto_publish",
      overallScore: 0.93,
      records: [
        makeEvaluationRow({ assetId: "asset-1", recommendation: "AUTO_PUBLISH" }),
        makeEvaluationRow({
          id: "eval-2",
          assetId: "asset-2",
          generationJobId: "job-2",
          recommendation: "AUTO_PUBLISH",
        }),
      ],
    });
    prisma.evaluationRecord.findMany.mockResolvedValue([
      makeEvaluationRow({ assetId: "asset-1", recommendation: "AUTO_PUBLISH" }),
      makeEvaluationRow({
        id: "eval-2",
        assetId: "asset-2",
        generationJobId: "job-2",
        recommendation: "AUTO_PUBLISH",
      }),
    ]);
    prisma.contentAsset.findFirst
      .mockResolvedValueOnce({
        id: "asset-1",
        tenantId: "tenant-1",
        assetType: "SIMULATION",
        manifestData: { id: "manifest-1" },
      })
      .mockResolvedValueOnce({
        id: "asset-2",
        tenantId: "tenant-1",
        assetType: "ANIMATION",
        manifestData: { id: "manifest-2" },
      });

    const summary = await service.processRequestOutcome("tenant-1", "req-1");

    expect(summary.autoPublished).toBe(false);
    expect(summary.nextAction).toBe("ready_for_publish");
    expect(summary.publishedAssetIds).toEqual([]);
    expect(prisma.contentAsset.update).not.toHaveBeenCalled();
  });

  it("creates regeneration candidates and pending review when evaluation requires intervention", async () => {
    prisma.generationRequest.findFirst.mockResolvedValue(
      makeRequest({ reviewPath: "HUMAN_REVIEW" }),
    );
    mockEvaluationOutcome({
      recommendation: "manual_review",
      overallScore: 0.68,
      records: [
        makeEvaluationRow({
          recommendation: "MANUAL_REVIEW",
          issues: [
            {
              dimension: "coherence",
              severity: "warning",
              message: "Weak alignment",
            },
          ],
        }),
      ],
    });

    const summary = await service.processRequestOutcome("tenant-1", "req-1", {
      autoPublish: false,
    });

    expect(summary.nextAction).toBe("ready_for_manual_review");
    expect(prisma.regenerationCandidate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          assetId: "asset-1",
          trigger: "LOW_EVALUATION_SCORE",
        }),
      }),
    );
    expect(prisma.generationReviewDecision.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          status: "PENDING",
        }),
      }),
    );
  });

  it("flags regeneration when evaluation blocks release", async () => {
    mockEvaluationOutcome({
      recommendation: "block",
      overallScore: 0.21,
      records: [
        makeEvaluationRow({
          recommendation: "BLOCK",
          status: "FAILED",
          issues: [
            {
              dimension: "safety",
              severity: "error",
              message: "Unsafe content",
            },
          ],
        }),
      ],
    });

    const summary = await service.processRequestOutcome("tenant-1", "req-1", {
      autoPublish: false,
    });

    expect(summary.nextAction).toBe("regeneration_required");
    expect(summary.blockedAssetIds).toEqual(["asset-1"]);
    expect(prisma.regenerationCandidate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          trigger: "SAFETY_CONCERN",
          severity: "CRITICAL",
        }),
      }),
    );
  });
});
