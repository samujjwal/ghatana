import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExperienceRemediationService } from "../experience-remediation-service";

function makePrisma() {
  return {
    contentAsset: {
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue({}),
    },
    explorerEvent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    evaluationRecord: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
    },
    generationReviewDecision: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
    regenerationCandidate: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue({}),
    },
    recommendationEdge: {
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue({}),
      update: vi.fn().mockResolvedValue({}),
    },
    learningExperience: {
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue({ moduleId: "module-1" }),
      update: vi.fn().mockResolvedValue({}),
    },
    enrollment: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    assessmentAttempt: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    driftSignal: {
      create: vi.fn().mockResolvedValue({}),
      findMany: vi.fn().mockResolvedValue([]),
    },
    regenerationInsight: {
      create: vi.fn().mockResolvedValue({}),
    },
    aBExperiment: {
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue({}),
    },
    aBExperimentObservation: {
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

describe("ExperienceRemediationService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: ExperienceRemediationService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new ExperienceRemediationService(prisma as never);

    vi.spyOn((service as any).outcomeService, "analyzeExperienceAssets").mockResolvedValue({
      experienceId: "experience-1",
      totalAssets: 2,
      healthyAssets: 1,
      watchAssets: 1,
      interveneAssets: 0,
      assets: [
        { recommendedActions: ["monitor"] },
        { recommendedActions: ["recompute_recommendations"] },
      ],
    });
    vi.spyOn((service as any).driftDetector, "scanExperienceAdaptive").mockResolvedValue({
      experienceId: "experience-1",
      signals: [{ signalType: "low_completion" }],
      insights: [{ category: "content_difficulty" }],
      scanDurationMs: 5,
      thresholds: {
        minCompletionRate: 0.5,
        maxAbortRate: 0.4,
        minAverageMastery: 0.5,
        minEngagementScore: 0.4,
        minPositiveFeedbackRatio: 0.5,
        sampleSize: 50,
        minLearnerCount: 10,
      },
    });
    vi.spyOn((service as any).experimentService, "listExperiments").mockResolvedValue([
      { id: "exp-1", status: "running", winner: null },
    ]);
    vi.spyOn((service as any).qualityPipeline, "applyPredictionsForExperience").mockResolvedValue([
      { assetId: "asset-1" },
      { assetId: "asset-2" },
    ]);
    vi.spyOn(
      (service as any).recommendationService,
      "recomputeOutcomeAwareEdgesForExperience",
    ).mockResolvedValue({
      processedAssets: 2,
      updatedEdges: 3,
      skippedEdges: 1,
    });
    vi.spyOn((service as any).experimentService, "evaluateActiveExperiments").mockResolvedValue({
      evaluated: 1,
      promoted: 1,
      results: [
        {
          experimentId: "exp-1",
          status: "winner_promoted",
          outcome: { winner: "treatment" },
        },
      ],
    });
  });

  it("summarizes experience-level remediation state", async () => {
    const result = await service.summarizeExperience("tenant-1", "experience-1");

    expect(result.totalAssets).toBe(2);
    expect(result.driftSignalCount).toBe(1);
    expect(result.runningExperiments).toBe(1);
    expect(result.recommendedActions).toContain("recompute_recommendations");
    expect(result.policyBreakdown?.primaryDriver).toBeDefined();
    expect(
      result.policyBreakdown?.policySource === "trained_empirical" ||
        result.policyBreakdown?.policySource === "trained_causal_blend",
    ).toBe(true);
    expect(result.policyBreakdown?.learnedWeights?.quality).toBeGreaterThanOrEqual(0);
    expect(result.executedActions).toBeUndefined();
  });

  it("applies remediation across quality, outcomes, recommendations, and experiments", async () => {
    const result = await service.applyExperienceRemediation(
      "tenant-1",
      "experience-1",
    );

    expect(result.qualityPredictionsApplied).toBe(2);
    expect(result.promotableExperiments).toBe(1);
    expect(result.recommendationRefresh).toEqual({
      processedAssets: 2,
      updatedEdges: 3,
      skippedEdges: 1,
    });
    expect(result.executedActions).toContain("apply_quality_predictions");
    expect(result.executedActions).toContain("evaluate_active_experiments");
    expect(result.policyBreakdown?.recommendationPriority).toBeGreaterThan(0);
    expect(result.policyBreakdown?.modelConfidence).toBeGreaterThanOrEqual(0);
  });

  it("summarizes tenant-wide remediation policy focus from asset and experiment state", async () => {
    prisma.contentAsset.findMany.mockResolvedValueOnce([
      {
        id: "asset-1",
        confidenceScore: 0.4,
        recommendationStatus: "STALE",
        reviewState: JSON.stringify({ healthStatus: "intervene" }),
      },
      {
        id: "asset-2",
        confidenceScore: 0.7,
        recommendationStatus: "COMPUTED",
        reviewState: JSON.stringify({ healthStatus: "watch" }),
      },
    ]).mockResolvedValueOnce([
      {
        id: "asset-1",
        qualityScore: 0.4,
        confidenceScore: 0.4,
        recommendationStatus: "STALE",
        reviewState: JSON.stringify({ healthStatus: "intervene" }),
      },
      {
        id: "asset-2",
        qualityScore: 0.8,
        confidenceScore: 0.7,
        recommendationStatus: "COMPUTED",
        reviewState: JSON.stringify({ healthStatus: "watch" }),
      },
    ]);
    prisma.aBExperiment.findMany.mockResolvedValueOnce([
      { status: "running", winner: null },
      { status: "winner_promoted", winner: "treatment" },
    ]).mockResolvedValueOnce([
      {
        status: "running",
        winner: null,
        pValue: 0.1,
        statisticalPower: 0.4,
        effectSize: 0.06,
      },
      {
        status: "winner_promoted",
        winner: "treatment",
        pValue: 0.02,
        statisticalPower: 0.8,
        effectSize: 0.12,
      },
    ]);
    prisma.explorerEvent.findMany.mockResolvedValueOnce([
      { assetId: "asset-1", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-1", eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
      { assetId: "asset-2", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-2", eventType: "CLICK", feedbackLabel: null },
      { assetId: "asset-2", eventType: "ASSET_COMPLETE", feedbackLabel: null },
    ]);
    prisma.driftSignal.findMany.mockResolvedValueOnce([{ severity: "medium" }]);
    prisma.aBExperimentObservation.findMany.mockResolvedValueOnce([
      {
        experimentId: "exp-1",
        variant: "control",
        metricValue: 0.45,
        completed: false,
        masteryScore: 0.4,
        feedbackScore: 0.25,
      },
      {
        experimentId: "exp-1",
        variant: "treatment",
        metricValue: 0.62,
        completed: true,
        masteryScore: 0.58,
        feedbackScore: 0.5,
      },
    ]);

    const result = await service.summarizeTenantPolicyProfile("tenant-1");

    expect(result.totalPublishedAssets).toBe(2);
    expect(result.staleRecommendationAssets).toBe(1);
    expect(result.interveneAssets).toBe(1);
    expect(result.promotableExperiments).toBe(1);
    expect(result.priorityWeights.quality).toBeGreaterThan(0);
    expect(result.policyModel?.source).toBe("trained_empirical");
    expect(result.causalModel?.source).toBe("trained_causal");
    expect(result.policyBlend?.causalWeight).toBeGreaterThan(0);
    expect(result.policyModel?.sampleSize).toBeGreaterThan(0);
    expect(result.recommendedFocus).toBeDefined();
  });

  it("trains an empirical tenant policy model from assets, telemetry, drift, and experiments", async () => {
    prisma.contentAsset.findMany.mockResolvedValueOnce([
      {
        id: "asset-1",
        qualityScore: 0.4,
        confidenceScore: 0.45,
        recommendationStatus: "STALE",
        reviewState: JSON.stringify({ healthStatus: "intervene" }),
      },
      {
        id: "asset-2",
        qualityScore: 0.85,
        confidenceScore: 0.9,
        recommendationStatus: "COMPUTED",
        reviewState: JSON.stringify({ healthStatus: "healthy" }),
      },
    ]);
    prisma.aBExperiment.findMany.mockResolvedValueOnce([
      {
        status: "running",
        winner: "inconclusive",
        pValue: 0.2,
        statisticalPower: 0.35,
        effectSize: 0.08,
      },
    ]);
    prisma.explorerEvent.findMany.mockResolvedValueOnce([
      { assetId: "asset-1", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-1", eventType: "CLICK", feedbackLabel: null },
      { assetId: "asset-1", eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
      { assetId: "asset-2", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-2", eventType: "CLICK", feedbackLabel: null },
      { assetId: "asset-2", eventType: "ASSET_COMPLETE", feedbackLabel: null },
      { assetId: "asset-2", eventType: "RANKING_FEEDBACK", feedbackLabel: "positive" },
    ]);
    prisma.driftSignal.findMany.mockResolvedValueOnce([{ severity: "high" }]);

    const model = await service.trainTenantPolicyModel("tenant-1");

    expect(model.sampleSize).toBeGreaterThan(0);
    expect(model.confidence).toBeGreaterThan(0);
    expect(model.weights.quality).toBeGreaterThan(0);
    expect(model.weights.experiments).toBeGreaterThan(0);
    expect(model.observedLift.drift).toBeGreaterThan(0);
  });

  it("trains a causal tenant policy model from experiment observations", async () => {
    prisma.aBExperiment.findMany.mockResolvedValueOnce([
      {
        id: "exp-1",
        status: "winner_promoted",
        winner: "treatment",
        effectSize: 0.2,
        statisticalPower: 0.85,
      },
      {
        id: "exp-2",
        status: "completed",
        winner: "inconclusive",
        effectSize: 0.04,
        statisticalPower: 0.5,
      },
    ]);
    prisma.aBExperimentObservation.findMany.mockResolvedValueOnce([
      {
        experimentId: "exp-1",
        variant: "control",
        metricValue: 0.4,
        completed: false,
        masteryScore: 0.35,
        feedbackScore: 0.2,
      },
      {
        experimentId: "exp-1",
        variant: "treatment",
        metricValue: 0.62,
        completed: true,
        masteryScore: 0.58,
        feedbackScore: 0.5,
      },
      {
        experimentId: "exp-2",
        variant: "control",
        metricValue: 0.5,
        completed: true,
        masteryScore: 0.5,
        feedbackScore: 0.45,
      },
      {
        experimentId: "exp-2",
        variant: "treatment",
        metricValue: 0.54,
        completed: true,
        masteryScore: 0.52,
        feedbackScore: 0.48,
      },
    ]);

    const model = await service.trainTenantCausalPolicyModel("tenant-1");

    expect(model.sampleSize).toBe(4);
    expect(model.confidence).toBeGreaterThan(0);
    expect(model.weights.experiments).toBeGreaterThan(0);
    expect(model.observedLift.quality).toBeGreaterThan(0);
    expect(model.observedLift.outcomes).toBeGreaterThan(0);
  });

  it("simulates tenant policy scenarios over the trained baseline model", async () => {
    prisma.contentAsset.findMany
      .mockResolvedValueOnce([
        {
          id: "asset-1",
          confidenceScore: 0.4,
          recommendationStatus: "STALE",
          reviewState: JSON.stringify({ healthStatus: "intervene" }),
        },
      ])
      .mockResolvedValueOnce([
        {
          id: "asset-1",
          qualityScore: 0.4,
          confidenceScore: 0.4,
          recommendationStatus: "STALE",
          reviewState: JSON.stringify({ healthStatus: "intervene" }),
        },
      ]);
    prisma.aBExperiment.findMany
      .mockResolvedValueOnce([
        { status: "running", winner: null },
      ])
      .mockResolvedValueOnce([
        {
          status: "running",
          winner: null,
          pValue: 0.08,
          statisticalPower: 0.45,
          effectSize: 0.07,
        },
      ]);
    prisma.explorerEvent.findMany.mockResolvedValueOnce([
      { assetId: "asset-1", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-1", eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
    ]);
    prisma.driftSignal.findMany.mockResolvedValueOnce([{ severity: "high" }]);

    const analysis = await service.simulateTenantPolicyScenarios("tenant-1");

    expect(analysis.scenarios).toHaveLength(6);
    expect(analysis.baselineConfidence).toBeGreaterThanOrEqual(0);
    expect(analysis.recommendedScenario).toBeDefined();
  });

  it("ranks experience interventions from trained and causal-proxy signals", async () => {
    vi.spyOn((service as any).experimentService, "listExperiments").mockResolvedValueOnce([
      { id: "exp-1", status: "winner_promoted", winner: "treatment" },
    ]);
    prisma.contentAsset.findMany.mockResolvedValueOnce([
      {
        id: "asset-1",
        confidenceScore: 0.4,
        recommendationStatus: "STALE",
        reviewState: JSON.stringify({ healthStatus: "intervene" }),
      },
    ]);
    prisma.aBExperiment.findMany.mockResolvedValueOnce([
      {
        status: "running",
        winner: "treatment",
        pValue: 0.02,
        statisticalPower: 0.82,
        effectSize: 0.14,
      },
    ]);
    prisma.explorerEvent.findMany.mockResolvedValueOnce([
      { assetId: "asset-1", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-1", eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
    ]);
    prisma.driftSignal.findMany.mockResolvedValueOnce([{ severity: "high" }]);
    prisma.aBExperimentObservation.findMany.mockResolvedValueOnce([
      {
        experimentId: "exp-1",
        variant: "control",
        metricValue: 0.4,
        completed: false,
        masteryScore: 0.35,
        feedbackScore: 0.2,
      },
      {
        experimentId: "exp-1",
        variant: "treatment",
        metricValue: 0.66,
        completed: true,
        masteryScore: 0.61,
        feedbackScore: 0.55,
      },
    ]);

    const plan = await service.rankExperienceInterventions("tenant-1", "experience-1");

    expect(plan.experienceId).toBe("experience-1");
    expect(plan.interventions.length).toBeGreaterThan(0);
    expect(plan.interventions[0]?.score).toBeGreaterThan(0);
    expect(
      plan.interventions.some(
        (item) => item.source === "causal_proxy" || item.source === "trained_causal",
      ),
    ).toBe(true);
  });

  it("applies top-ranked interventions and returns execution results", async () => {
    vi.spyOn((service as any).experimentService, "listExperiments").mockResolvedValueOnce([
      { id: "exp-1", status: "winner_promoted", winner: "treatment" },
    ]);
    prisma.contentAsset.findMany.mockResolvedValueOnce([
      {
        id: "asset-1",
        qualityScore: 0.45,
        confidenceScore: 0.4,
        recommendationStatus: "STALE",
        reviewState: JSON.stringify({ healthStatus: "intervene" }),
      },
    ]);
    prisma.aBExperiment.findMany.mockResolvedValueOnce([
      {
        status: "winner_promoted",
        winner: "treatment",
        pValue: 0.01,
        statisticalPower: 0.9,
        effectSize: 0.2,
      },
    ]);
    prisma.explorerEvent.findMany.mockResolvedValueOnce([
      { assetId: "asset-1", eventType: "IMPRESSION", feedbackLabel: null },
      { assetId: "asset-1", eventType: "CLICK", feedbackLabel: null },
    ]);
    prisma.driftSignal.findMany.mockResolvedValueOnce([{ severity: "high" }]);

    const result = await service.applyRankedExperienceInterventions(
      "tenant-1",
      "experience-1",
      { limit: 2 },
    );

    expect(result.limit).toBe(2);
    expect(result.appliedActions.length + result.skippedActions.length).toBe(2);
    expect(result.baselineSummary.experienceId).toBe("experience-1");
    expect(result.summary.experienceId).toBe("experience-1");
    expect(typeof result.delta.interveneAssets).toBe("number");
  });

  it("ranks the tenant remediation portfolio across experiences", async () => {
    prisma.learningExperience.findMany.mockResolvedValueOnce([
      { id: "experience-1", title: "First Experience" },
      { id: "experience-2", title: "Second Experience" },
    ]);
    vi.spyOn(service, "summarizeExperience")
      .mockResolvedValueOnce({
        experienceId: "experience-1",
        totalAssets: 4,
        healthyAssets: 1,
        watchAssets: 2,
        interveneAssets: 1,
        driftSignalCount: 2,
        driftInsightCount: 1,
        runningExperiments: 1,
        promotableExperiments: 1,
        recommendedActions: [],
        policyBreakdown: {
          primaryDriver: "outcomes",
          policySource: "trained_empirical",
          qualityPriority: 0.5,
          outcomePriority: 0.8,
          driftPriority: 0.6,
          experimentPriority: 0.4,
          recommendationPriority: 0.3,
          learnedWeights: {
            quality: 0.2,
            outcomes: 0.3,
            drift: 0.2,
            experiments: 0.1,
            recommendations: 0.2,
          },
          modelConfidence: 0.7,
        },
      })
      .mockResolvedValueOnce({
        experienceId: "experience-2",
        totalAssets: 3,
        healthyAssets: 3,
        watchAssets: 0,
        interveneAssets: 0,
        driftSignalCount: 0,
        driftInsightCount: 0,
        runningExperiments: 0,
        promotableExperiments: 0,
        recommendedActions: [],
        policyBreakdown: {
          primaryDriver: "balanced",
          policySource: "trained_empirical",
          qualityPriority: 0.1,
          outcomePriority: 0.1,
          driftPriority: 0.1,
          experimentPriority: 0.1,
          recommendationPriority: 0.1,
          learnedWeights: {
            quality: 0.2,
            outcomes: 0.2,
            drift: 0.2,
            experiments: 0.2,
            recommendations: 0.2,
          },
          modelConfidence: 0.6,
        },
      });

    const result = await service.rankTenantRemediationPortfolio("tenant-1");

    expect(result.tenantId).toBe("tenant-1");
    expect(result.experiences).toHaveLength(2);
    expect(result.experiences[0]?.experienceId).toBe("experience-1");
    expect(result.experiences[0]?.priorityScore).toBeGreaterThan(
      result.experiences[1]?.priorityScore ?? 0,
    );
  });

  it("ranks tenant-wide remediation interventions across the portfolio", async () => {
    vi.spyOn(service, "rankTenantRemediationPortfolio").mockResolvedValueOnce({
      tenantId: "tenant-1",
      generatedAt: new Date().toISOString(),
      experiences: [
        {
          experienceId: "experience-1",
          title: "First Experience",
          priorityScore: 0.8,
          primaryDriver: "outcomes",
          totalAssets: 4,
          interveneAssets: 2,
          driftSignalCount: 1,
          promotableExperiments: 1,
        },
        {
          experienceId: "experience-2",
          title: "Second Experience",
          priorityScore: 0.4,
          primaryDriver: "quality",
          totalAssets: 3,
          interveneAssets: 1,
          driftSignalCount: 0,
          promotableExperiments: 0,
        },
      ],
    });
    vi.spyOn(service, "rankExperienceInterventions")
      .mockResolvedValueOnce({
        experienceId: "experience-1",
        primaryDriver: "outcomes",
        interventions: [
          {
            action: "recompute_asset_outcomes",
            dimension: "outcomes",
            score: 0.9,
            expectedImpact: 0.7,
            confidence: 0.8,
            source: "trained_empirical",
            rationale: "Outcome pressure",
          },
        ],
      })
      .mockResolvedValueOnce({
        experienceId: "experience-2",
        primaryDriver: "quality",
        interventions: [
          {
            action: "apply_quality_predictions",
            dimension: "quality",
            score: 0.7,
            expectedImpact: 0.4,
            confidence: 0.6,
            source: "trained_empirical",
            rationale: "Quality pressure",
          },
        ],
      });

    const result = await service.rankTenantPortfolioInterventions("tenant-1");

    expect(result.tenantId).toBe("tenant-1");
    expect(result.interventions).toHaveLength(2);
    expect(result.interventions[0]?.experienceId).toBe("experience-1");
    expect(result.interventions[0]?.priorityScore).toBeGreaterThan(
      result.interventions[1]?.priorityScore ?? 0,
    );
  });

  it("applies tenant-wide top remediation interventions grouped by experience", async () => {
    vi.spyOn(service, "rankTenantPortfolioInterventions").mockResolvedValueOnce({
      tenantId: "tenant-1",
      generatedAt: new Date().toISOString(),
      interventions: [
        {
          experienceId: "experience-1",
          title: "First Experience",
          priorityScore: 0.81,
          primaryDriver: "outcomes",
          action: "recompute_asset_outcomes",
          dimension: "outcomes",
          score: 0.9,
          expectedImpact: 0.7,
          confidence: 0.8,
          source: "trained_empirical",
          rationale: "Outcome pressure",
        },
        {
          experienceId: "experience-1",
          title: "First Experience",
          priorityScore: 0.74,
          primaryDriver: "quality",
          action: "apply_quality_predictions",
          dimension: "quality",
          score: 0.85,
          expectedImpact: 0.6,
          confidence: 0.7,
          source: "trained_empirical",
          rationale: "Quality pressure",
        },
        {
          experienceId: "experience-2",
          title: "Second Experience",
          priorityScore: 0.4,
          primaryDriver: "quality",
          action: "apply_quality_predictions",
          dimension: "quality",
          score: 0.7,
          expectedImpact: 0.4,
          confidence: 0.6,
          source: "trained_empirical",
          rationale: "Secondary pressure",
        },
      ],
    });
    vi.spyOn(service, "summarizeExperience").mockResolvedValue({
      experienceId: "experience-1",
      totalAssets: 2,
      healthyAssets: 1,
      watchAssets: 1,
      interveneAssets: 0,
      driftSignalCount: 0,
      driftInsightCount: 0,
      runningExperiments: 0,
      promotableExperiments: 0,
      recommendedActions: [],
      policyBreakdown: {
        primaryDriver: "balanced",
        policySource: "trained_empirical",
        qualityPriority: 0.3,
        outcomePriority: 0.4,
        driftPriority: 0.1,
        experimentPriority: 0.1,
        recommendationPriority: 0.1,
        learnedWeights: {
          quality: 0.2,
          outcomes: 0.3,
          drift: 0.1,
          experiments: 0.1,
          recommendations: 0.3,
        },
        modelConfidence: 0.7,
      },
    });

    const result = await service.applyTenantPortfolioInterventions("tenant-1", {
      maxActions: 2,
    });

    expect(result.processedExperiences).toBe(1);
    expect(result.appliedExperiences).toBe(1);
    expect(result.totalAppliedActions).toBeGreaterThanOrEqual(1);
    expect(result.items[0]?.selectedActions).toEqual([
      "recompute_asset_outcomes",
      "apply_quality_predictions",
    ]);
  });
});
