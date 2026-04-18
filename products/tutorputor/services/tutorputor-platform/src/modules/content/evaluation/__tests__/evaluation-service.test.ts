/**
 * Evaluation Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify evaluation scoring, persistence, and scorecard aggregation
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { EvaluationService } from "../evaluation-service";
import { goldenEvaluationDatasets } from "../__fixtures__/golden-datasets";

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

function makeJob(overrides: Record<string, any> = {}) {
  return {
    id: "job-1",
    requestId: "req-1",
    jobType: "CLAIM",
    parameters: {},
    outputAssetId: null,
    outputData: {
      claims: [{ text: "Photosynthesis converts light to energy." }],
      count: 1,
    },
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    ...overrides,
  };
}

function makeRequest(jobs: any[] = [makeJob()]) {
  return {
    id: "req-1",
    tenantId: "tenant-1",
    status: "COMPLETED",
    jobs,
  };
}

function makeEvalRow(overrides: Record<string, any> = {}) {
  return {
    id: "eval-1",
    tenantId: "tenant-1",
    assetId: null,
    generationJobId: "job-1",
    generationRequestId: "req-1",
    coherenceScore: 0.9,
    completenessScore: 1.0,
    safetyScore: 1.0,
    accessibilityScore: 0.95,
    manifestValidityScore: 1.0,
    overallScore: 0.9725,
    status: "PASSED",
    recommendation: "AUTO_PUBLISH",
    issues: null,
    diagnostics: { jobType: "claim", jobId: "job-1" },
    errorMessage: null,
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    ...overrides,
  };
}

function makePrisma() {
  return {
    generationRequest: {
      findFirst: vi.fn(),
    },
    evaluationRecord: {
      create: vi.fn().mockImplementation((args: any) =>
        makeEvalRow({
          generationJobId: args.data.generationJobId,
          generationRequestId: args.data.generationRequestId,
          coherenceScore: args.data.coherenceScore,
          completenessScore: args.data.completenessScore,
          safetyScore: args.data.safetyScore,
          accessibilityScore: args.data.accessibilityScore,
          manifestValidityScore: args.data.manifestValidityScore,
          overallScore: args.data.overallScore,
          status: args.data.status,
          recommendation: args.data.recommendation,
          issues: args.data.issues,
          diagnostics: args.data.diagnostics,
        }),
      ),
      deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
    },
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue({ id: "asset-1" }),
    },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("EvaluationService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: EvaluationService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new EvaluationService(prisma as any);
  });

  // =========================================================================
  // evaluateJob
  // =========================================================================

  describe("evaluateJob", () => {
    it("scores a valid CLAIM job with high marks", async () => {
      const job = makeJob({
        jobType: "CLAIM",
        outputData: {
          claims: [{ text: "Water boils at 100°C." }],
          count: 1,
        },
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            coherenceScore: args.data.coherenceScore,
            completenessScore: args.data.completenessScore,
            safetyScore: args.data.safetyScore,
            overallScore: args.data.overallScore,
            status: args.data.status,
            recommendation: args.data.recommendation,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.coherenceScore).toBeGreaterThanOrEqual(0.8);
      expect(result.completenessScore).toBe(1.0);
      expect(result.safetyScore).toBe(1.0);
      expect(result.recommendation).toBe("auto_publish");
    });

    it("flags dangerous language with error-level safety issue", async () => {
      const job = makeJob({
        jobType: "CLAIM",
        outputData: {
          claims: [{ text: "This content promotes violence." }],
          count: 1,
        },
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            safetyScore: args.data.safetyScore,
            recommendation: args.data.recommendation,
            status: args.data.status,
            issues: args.data.issues,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.safetyScore).toBe(0.1);
      expect(result.recommendation).toBe("block");
      expect(
        result.issues?.some(
          (i: { severity: string }) => i.severity === "error",
        ),
      ).toBe(true);
    });

    it("flags missing completeness keys for CLAIM job", async () => {
      const job = makeJob({
        jobType: "CLAIM",
        outputData: {}, // missing "claims" and "count"
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            completenessScore: args.data.completenessScore,
            issues: args.data.issues,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.completenessScore).toBeLessThan(1.0);
    });

    it("scores a SIMULATION job manifest", async () => {
      const job = makeJob({
        jobType: "SIMULATION",
        outputData: {
          simulations: [
            { id: "sim-1", title: "Pendulum Sim" },
            { id: "sim-2", title: "Wave Sim" },
          ],
        },
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            manifestValidityScore: args.data.manifestValidityScore,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.manifestValidityScore).toBe(1.0);
    });

    it("penalises SIMULATION job manifest with missing id", async () => {
      const job = makeJob({
        jobType: "SIMULATION",
        outputData: {
          simulations: [
            { title: "Pendulum Sim" }, // no id
          ],
        },
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            manifestValidityScore: args.data.manifestValidityScore,
            recommendation: args.data.recommendation,
            issues: args.data.issues,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.manifestValidityScore).toBe(0.3);
      expect(result.recommendation).toBe("block");
    });

    it("persists EvaluationRecord to prisma", async () => {
      const job = makeJob();

      await service.evaluateJob("tenant-1", job);

      expect(prisma.evaluationRecord.create).toHaveBeenCalledOnce();
      const args = prisma.evaluationRecord.create.mock.calls[0][0];
      expect(args.data.tenantId).toBe("tenant-1");
      expect(args.data.generationJobId).toBe("job-1");
    });

    it("persists asset-linked evaluation metadata onto the content asset", async () => {
      const job = makeJob({
        outputAssetId: "asset-1",
      });

      await service.evaluateJob("tenant-1", job);

      expect(prisma.evaluationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            assetId: "asset-1",
          }),
        }),
      );
      expect(prisma.contentAsset.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "asset-1" },
          data: expect.objectContaining({
            qualityScore: expect.any(Number),
            reviewState: expect.any(String),
          }),
        }),
      );
    });

    it("blocks a regenerated artifact that regresses below the approved baseline", async () => {
      const job = makeJob({
        id: "job-regression",
        jobType: "CLAIM",
        outputAssetId: "asset-1",
        parameters: {
          promptHash: "prompt-regenerated-v2",
        },
        outputData: {
          claims: [{ text: "Photosynthesis is important." }],
          count: 1,
        },
      });

      prisma.contentAsset.findFirst.mockResolvedValue({
        id: "asset-1",
        currentVersion: 3,
        promptHash: "prompt-approved-v1",
        qualityScore: 0.93,
        reviewState: JSON.stringify({
          status: "approved",
          recommendation: "auto_publish",
        }),
        searchableText:
          "Approved baseline artifact with richer explanation and stronger evidence coverage.",
        revisions: [
          {
            version: 3,
            snapshot: {
              title: "Photosynthesis",
              blocks: [
                {
                  title: "Explainer",
                  body:
                    "Detailed explanation with worked example, evidence references, and task framing.",
                },
              ],
            },
            qualityScore: 0.93,
          },
        ],
      });
      prisma.evaluationRecord.findFirst.mockResolvedValueOnce(
        makeEvalRow({
          id: "eval-approved",
          assetId: "asset-1",
          overallScore: 0.94,
          recommendation: "AUTO_PUBLISH",
        }),
      );
      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            generationJobId: args.data.generationJobId,
            assetId: args.data.assetId,
            overallScore: args.data.overallScore,
            recommendation: args.data.recommendation,
            status: args.data.status,
            issues: args.data.issues,
            diagnostics: args.data.diagnostics,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job, "req-2");

      expect(result.recommendation).toBe("block");
      expect(
        result.issues?.some(
          (issue) =>
            issue.message ===
              "Regenerated artifact quality regressed below the approved baseline" ||
            issue.message ===
              "Regenerated artifact is materially thinner than the approved baseline",
        ),
      ).toBe(true);
      expect(prisma.evaluationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            diagnostics: expect.objectContaining({
              promptHash: "prompt-regenerated-v2",
              regressionBaseline: expect.objectContaining({
                version: 3,
                promptHash: "prompt-approved-v1",
                evaluationId: "eval-approved",
                evaluationScore: 0.94,
              }),
            }),
          }),
        }),
      );
    });

    it("emits pedagogy blockers when assessment content lacks scaffolding and validity details", async () => {
      const job = makeJob({
        id: "job-assessment-pedagogy",
        jobType: "ASSESSMENT",
        parameters: {
          gradeLevel: "grade_3_5",
        },
        outputData: {
          assessments: [
            {
              question: "What is 4 + 5?",
            },
          ],
        },
      });

      prisma.evaluationRecord.create.mockImplementation((args: any) =>
        Promise.resolve(
          makeEvalRow({
            overallScore: args.data.overallScore,
            recommendation: args.data.recommendation,
            status: args.data.status,
            issues: args.data.issues,
            diagnostics: args.data.diagnostics,
          }),
        ),
      );

      const result = await service.evaluateJob("tenant-1", job);

      expect(result.recommendation).toBe("block");
      expect(
        result.issues?.some(
          (issue) => issue.message === "Content lacks explicit scaffolding for the learner",
        ),
      ).toBe(true);
      expect(
        result.issues?.some(
          (issue) =>
            issue.message === "Assessment item 1 is missing the expected answer or rubric",
        ),
      ).toBe(true);
      expect(prisma.evaluationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            diagnostics: expect.objectContaining({
              pedagogy: expect.objectContaining({
                gradeLevel: "grade_3_5",
              }),
            }),
          }),
        }),
      );
    });

    it("keeps the golden high-trust domain fixtures publishable", async () => {
      for (const dataset of goldenEvaluationDatasets) {
        for (const fixtureCase of dataset.cases) {
          prisma.evaluationRecord.create.mockImplementation((args: any) =>
            Promise.resolve(
              makeEvalRow({
                generationJobId: args.data.generationJobId,
                overallScore: args.data.overallScore,
                recommendation: args.data.recommendation,
                status: args.data.status,
                issues: args.data.issues,
                diagnostics: args.data.diagnostics,
              }),
            ),
          );

          const result = await service.evaluateJob("tenant-1", {
            ...makeJob({
              id: `${dataset.domain}-${fixtureCase.name}`,
              jobType: fixtureCase.jobType,
              outputData: fixtureCase.outputData,
              parameters: {
                domain: dataset.domain,
                gradeLevel: dataset.gradeLevel,
              },
            }),
          });

          expect(result.overallScore).toBeGreaterThanOrEqual(
            fixtureCase.minOverallScore,
          );
          expect(result.recommendation).not.toBe("block");
          expect(
            result.issues?.filter(
              (issue) =>
                issue.dimension === "pedagogy" && issue.severity === "error",
            ) ?? [],
          ).toHaveLength(0);
        }
      }
    });
  });

  // =========================================================================
  // evaluateGenerationRequest
  // =========================================================================

  describe("evaluateGenerationRequest", () => {
    it("throws if request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.evaluateGenerationRequest("tenant-1", "missing-req"),
      ).rejects.toThrow("not found");
    });

    it("deletes previous evaluation records before re-evaluating", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest([]));

      await service.evaluateGenerationRequest("tenant-1", "req-1");

      expect(prisma.evaluationRecord.deleteMany).toHaveBeenCalledWith({
        where: { generationRequestId: "req-1" },
      });
    });

    it("returns scorecard with correct field shape", async () => {
      const job = makeJob({
        outputData: { claims: [{ text: "x" }], count: 1 },
      });
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest([job]));

      prisma.evaluationRecord.create.mockResolvedValue(
        makeEvalRow({ generationRequestId: "req-1" }),
      );

      const scorecard = await service.evaluateGenerationRequest(
        "tenant-1",
        "req-1",
      );

      expect(scorecard.generationRequestId).toBe("req-1");
      expect(scorecard.overallScore).toBeGreaterThanOrEqual(0);
      expect(scorecard.overallScore).toBeLessThanOrEqual(1);
      expect(["auto_publish", "manual_review", "block"]).toContain(
        scorecard.recommendation,
      );
      expect(scorecard.dimensions).toBeDefined();
    });

    it("returns empty scorecard when request has no completed jobs", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest([]));

      const scorecard = await service.evaluateGenerationRequest(
        "tenant-1",
        "req-1",
      );

      expect(scorecard.issues).toHaveLength(0);
      expect(scorecard.overallScore).toBe(0);
    });

    it("evaluates all completed jobs and aggregates scores", async () => {
      const jobs = [
        makeJob({
          id: "job-1",
          jobType: "CLAIM",
          outputData: { claims: [{}], count: 1 },
        }),
        makeJob({
          id: "job-2",
          jobType: "EXPLAINER",
          outputData: { examples: [{}], count: 1 },
        }),
      ];
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest(jobs));

      let callCount = 0;
      prisma.evaluationRecord.create.mockImplementation((args: any) => {
        callCount++;
        return Promise.resolve(
          makeEvalRow({
            id: `eval-${callCount}`,
            generationJobId: args.data.generationJobId,
          }),
        );
      });

      const scorecard = await service.evaluateGenerationRequest(
        "tenant-1",
        "req-1",
      );

      expect(prisma.evaluationRecord.create).toHaveBeenCalledTimes(2);
      expect(scorecard.overallScore).toBeGreaterThanOrEqual(0);
    });

    it("flags missing modality and evidence coverage across the request before review", async () => {
      const jobs = [
        makeJob({
          id: "job-claim",
          jobType: "CLAIM",
          outputData: {
            claims: [
              {
                text: "Learners compare heat transfer mechanisms.",
                contentNeeds: {
                  examples: { required: true },
                  simulation: { required: true },
                  animation: { required: true },
                },
              },
            ],
            count: 1,
          },
        }),
      ];

      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest(jobs));
      prisma.evaluationRecord.create.mockResolvedValue(
        makeEvalRow({ generationRequestId: "req-1" }),
      );

      const scorecard = await service.evaluateGenerationRequest(
        "tenant-1",
        "req-1",
      );

      expect(scorecard.recommendation).toBe("block");
      expect(scorecard.blockedReasons).toContain(
        "Claims do not yet have any concrete examples",
      );
      expect(scorecard.blockedReasons).toContain(
        "Generated content has no evidence-producing task or assessment coverage",
      );
      expect(
        scorecard.issues.some(
          (issue) =>
            issue.message ===
            "Claims requiring simulations are missing interactive modality coverage",
        ),
      ).toBe(true);
      expect(
        scorecard.issues.some(
          (issue) =>
            issue.message ===
            "Claims requiring animations are missing visual modality coverage",
        ),
      ).toBe(true);
    });
  });

  // =========================================================================
  // getEvaluationsByRequest
  // =========================================================================

  describe("getEvaluationsByRequest", () => {
    it("returns mapped evaluation records", async () => {
      prisma.evaluationRecord.findMany.mockResolvedValue([makeEvalRow()]);

      const records = await service.getEvaluationsByRequest(
        "tenant-1",
        "req-1",
      );

      expect(records).toHaveLength(1);
      expect(records[0].id).toBe("eval-1");
      expect(records[0].status).toBe("passed");
      expect(records[0].recommendation).toBe("auto_publish");
    });

    it("returns empty array when no records found", async () => {
      prisma.evaluationRecord.findMany.mockResolvedValue([]);

      const records = await service.getEvaluationsByRequest(
        "tenant-1",
        "req-1",
      );

      expect(records).toEqual([]);
    });
  });

  // =========================================================================
  // getEvaluation
  // =========================================================================

  describe("getEvaluation", () => {
    it("returns null when evaluation not found", async () => {
      prisma.evaluationRecord.findFirst.mockResolvedValue(null);

      const result = await service.getEvaluation("tenant-1", "missing-eval");

      expect(result).toBeNull();
    });

    it("returns mapped record when found", async () => {
      prisma.evaluationRecord.findFirst.mockResolvedValue(makeEvalRow());

      const result = await service.getEvaluation("tenant-1", "eval-1");

      expect(result).not.toBeNull();
      expect(result!.id).toBe("eval-1");
    });
  });
});
