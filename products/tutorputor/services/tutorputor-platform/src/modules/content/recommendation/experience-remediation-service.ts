/**
 * Experience Remediation Service
 *
 * Orchestrates experience-scoped feedback loops across quality prediction,
 * outcome analysis, recommendation refresh, adaptive drift scans, and A/B
 * experimentation so operators can inspect or apply one coherent remediation
 * pass for a learning experience.
 *
 * @doc.type service
 * @doc.purpose Experience-level remediation orchestration
 * @doc.layer product
 * @doc.pattern Orchestrator
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { ContentQualityMLPipeline } from "../quality-ml/pipeline.js";
import { AssetOutcomeService } from "./asset-outcome-service.js";
import { RecommendationService } from "./recommendation-service.js";
import { createContentDriftDetector } from "../../content-needs/drift-detector.js";
import { ABTestingService } from "../experiments/ab-testing/service.js";

type RemediationAction =
  | "apply_quality_predictions"
  | "recompute_asset_outcomes"
  | "refresh_recommendation_edges"
  | "scan_adaptive_drift"
  | "promote_experiment_winners"
  | "evaluate_active_experiments";

type ExperienceRemediationSummary = any;
type ExperienceRemediationInterventionPlan = any;
type ExperienceRemediationInterventionExecution = any;
type TenantRemediationPolicyProfile = any;
type TenantRemediationPolicyScenarioAnalysis = any;
type TenantRemediationPortfolio = any;
type TenantPortfolioRemediationIntervention = any;
type TenantRemediationPortfolioPlan = any;
type TenantRemediationPortfolioExecution = any;

export class ExperienceRemediationService {
  private readonly qualityPipeline: ContentQualityMLPipeline;
  private readonly outcomeService: AssetOutcomeService;
  private readonly recommendationService: RecommendationService;
  private readonly driftDetector;
  private readonly experimentService: ABTestingService;

  constructor(private readonly prisma: PrismaClient) {
    this.qualityPipeline = new ContentQualityMLPipeline(prisma);
    this.outcomeService = new AssetOutcomeService(prisma);
    this.recommendationService = new RecommendationService(prisma);
    this.driftDetector = createContentDriftDetector(prisma);
    this.experimentService = new ABTestingService(prisma);
  }

  async summarizeExperience(
    tenantId: string,
    experienceId: string,
  ): Promise<ExperienceRemediationSummary> {
    const [outcome, drift, experiments, policyModel, causalModel] = await Promise.all([
      this.outcomeService.analyzeExperienceAssets(tenantId, experienceId),
      this.driftDetector.scanExperienceAdaptive(tenantId, experienceId),
      this.experimentService.listExperiments(tenantId, { experienceId }),
      this.trainTenantPolicyModel(tenantId),
      this.trainTenantCausalPolicyModel(tenantId),
    ]);

    return buildRemediationSummary({
      experienceId,
      outcome,
      drift,
      experiments,
      executedActions: [],
      policyModel,
      causalModel,
    });
  }

  async applyExperienceRemediation(
    tenantId: string,
    experienceId: string,
    options: {
      autoPromoteExperiments?: boolean;
      recomputeRecommendations?: boolean;
    } = {},
  ): Promise<ExperienceRemediationSummary> {
    const [
      qualityPredictions,
      outcome,
      recommendationRefresh,
      drift,
      experiments,
      policyModel,
    ] =
      await Promise.all([
        this.qualityPipeline.applyPredictionsForExperience(tenantId, experienceId),
        this.outcomeService.analyzeExperienceAssets(tenantId, experienceId, {
          apply: true,
          recomputeRecommendations: options.recomputeRecommendations ?? true,
        }),
        this.recommendationService.recomputeOutcomeAwareEdgesForExperience(
          tenantId,
          experienceId,
        ),
        this.driftDetector.scanExperienceAdaptive(tenantId, experienceId),
        this.experimentService.evaluateActiveExperiments(tenantId, {
          experienceId,
          autoPromote: options.autoPromoteExperiments ?? true,
        }),
        this.trainTenantPolicyModel(tenantId),
      ]);

    return buildRemediationSummary({
      experienceId,
      outcome,
      drift,
      experiments: experiments.results.map((result: any) => ({
        id: result.experimentId,
        status: result.status,
        winner: result.outcome.winner,
      })),
      qualityPredictionsApplied: qualityPredictions.length,
      recommendationRefresh,
      policyModel,
      executedActions: [
        qualityPredictions.length > 0 ? "apply_quality_predictions" : null,
        outcome.assets.length > 0 ? "recompute_asset_outcomes" : null,
        recommendationRefresh.updatedEdges > 0
          ? "refresh_recommendation_edges"
          : null,
        drift.signals.length > 0 ? "scan_adaptive_drift" : null,
        experiments.promoted > 0 ? "promote_experiment_winners" : null,
        experiments.evaluated > 0 ? "evaluate_active_experiments" : null,
      ].filter((value): value is string => value !== null),
    });
  }

  async summarizeTenantPolicyProfile(
    tenantId: string,
  ): Promise<TenantRemediationPolicyProfile> {
    const [assets, experiments, policyModel, causalModel] = await Promise.all([
      this.prisma.contentAsset.findMany({
        where: {
          tenantId,
          status: "PUBLISHED",
        },
        select: {
          id: true,
          confidenceScore: true,
          recommendationStatus: true,
          reviewState: true,
        },
      }),
      this.prisma.aBExperiment.findMany({
        where: { tenantId },
        select: {
          status: true,
          winner: true,
        },
      }),
      this.trainTenantPolicyModel(tenantId),
      this.trainTenantCausalPolicyModel(tenantId),
    ]);

    let staleRecommendationAssets = 0;
    let lowConfidenceAssets = 0;
    let watchAssets = 0;
    let interveneAssets = 0;

    for (const asset of assets) {
      if (String(asset.recommendationStatus ?? "").toUpperCase() === "STALE") {
        staleRecommendationAssets++;
      }
      if ((asset.confidenceScore ?? 0.5) < 0.55) {
        lowConfidenceAssets++;
      }

      const parsedReviewState = parseReviewState(asset.reviewState);
      if (parsedReviewState?.healthStatus === "watch") {
        watchAssets++;
      }
      if (parsedReviewState?.healthStatus === "intervene") {
        interveneAssets++;
      }
    }

    const runningExperiments = experiments.filter((experiment: any) =>
      String(experiment.status ?? "").includes("running"),
    ).length;
    const promotableExperiments = experiments.filter(
      (experiment: any) =>
        experiment.winner === "treatment" ||
        String(experiment.status ?? "").includes("winner_promoted") ||
        String(experiment.status ?? "").includes("ready_to_promote"),
    ).length;
    const totalPublishedAssets = assets.length;
    const qualityWeight =
      totalPublishedAssets === 0 ? 0 : lowConfidenceAssets / totalPublishedAssets;
    const outcomeWeight =
      totalPublishedAssets === 0 ? 0 : interveneAssets / totalPublishedAssets;
    const driftWeight =
      totalPublishedAssets === 0
        ? 0
        : Math.min(1, (watchAssets + interveneAssets) / Math.max(1, totalPublishedAssets));
    const experimentWeight =
      experiments.length === 0 ? 0 : promotableExperiments / experiments.length;
    const recommendationWeight =
      totalPublishedAssets === 0 ? 0 : staleRecommendationAssets / totalPublishedAssets;
    const weights = {
      quality: qualityWeight,
      outcomes: outcomeWeight,
      drift: driftWeight,
      experiments: experimentWeight,
      recommendations: recommendationWeight,
    };
    const rankedWeights = Object.entries(weights).sort((a: any, b: any) => b[1] - a[1]);
    const topWeight = rankedWeights[0] ?? ["balanced", 0];
    const nextWeight = rankedWeights[1] ?? ["balanced", 0];
    const recommendedFocus =
      topWeight[1] === 0 || topWeight[1] - nextWeight[1] < 0.1
        ? "balanced"
        : (topWeight[0] as TenantRemediationPolicyProfile["recommendedFocus"]);

    return {
      tenantId,
      totalPublishedAssets,
      staleRecommendationAssets,
      lowConfidenceAssets,
      watchAssets,
      interveneAssets,
      runningExperiments,
      promotableExperiments,
      priorityWeights: weights,
      policyModel: {
        source: "trained_empirical",
        sampleSize: policyModel.sampleSize,
        confidence: policyModel.confidence,
        weights: policyModel.weights,
        observedLift: policyModel.observedLift,
      },
      causalModel: {
        source: "trained_causal",
        sampleSize: causalModel.sampleSize,
        confidence: causalModel.confidence,
        weights: causalModel.weights,
        observedLift: causalModel.observedLift,
      },
      policyBlend: derivePolicyBlend(policyModel.confidence, causalModel.confidence),
      recommendedFocus,
    };
  }

  async trainTenantPolicyModel(tenantId: string): Promise<{
    sampleSize: number;
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
    observedLift: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  }> {
    const [assets, experiments, events, driftSignals] = await Promise.all([
      this.prisma.contentAsset.findMany({
        where: {
          tenantId,
          status: "PUBLISHED",
        },
        select: {
          id: true,
          qualityScore: true,
          confidenceScore: true,
          recommendationStatus: true,
          reviewState: true,
        },
      }),
      this.prisma.aBExperiment.findMany({
        where: { tenantId },
        select: {
          status: true,
          winner: true,
          pValue: true,
          statisticalPower: true,
          effectSize: true,
        },
      }),
      this.prisma.explorerEvent.findMany({
        where: { tenantId },
        select: {
          assetId: true,
          eventType: true,
          feedbackLabel: true,
        },
      }),
      (this.prisma as PrismaClient & {
        driftSignal?: {
          findMany: (...args: any[]) => Promise<Array<{ severity?: string | null }>>;
        };
      }).driftSignal?.findMany({
        where: { tenantId },
        select: {
          severity: true,
        },
      }) ?? Promise.resolve([]),
    ]);

    const eventByAsset = new Map<
      string,
      Array<{ eventType?: string | null; feedbackLabel?: string | null }>
    >();
    for (const event of events) {
      if (!event.assetId) {
        continue;
      }
      const rows = eventByAsset.get(event.assetId) ?? [];
      rows.push(event);
      eventByAsset.set(event.assetId, rows);
    }

    const lifts = {
      quality: 0,
      outcomes: 0,
      drift: 0,
      experiments: 0,
      recommendations: 0,
    };
    const exposures = {
      quality: 0,
      outcomes: 0,
      drift: 0,
      recommendations: 0,
    };

    for (const asset of assets) {
      const reviewState = parseReviewState(asset.reviewState);
      const telemetry = summarizePolicyTelemetry(eventByAsset.get(asset.id) ?? []);
      const successScore = clamp01(
        normalizeScore(asset.qualityScore) * 0.3 +
          clamp01(asset.confidenceScore ?? 0.5) * 0.25 +
          telemetry.completionRate * 0.2 +
          telemetry.feedbackRatio * 0.15 +
          telemetry.ctr * 0.1,
      );

      if (normalizeScore(asset.qualityScore) < 0.65 || (asset.confidenceScore ?? 0.5) < 0.55) {
        exposures.quality++;
        lifts.quality += 1 - successScore;
      }
      if (reviewState?.healthStatus === "intervene") {
        exposures.outcomes++;
        lifts.outcomes += 1 - successScore;
      }
      if (reviewState?.healthStatus === "watch" || reviewState?.healthStatus === "intervene") {
        exposures.drift++;
        lifts.drift += 1 - successScore;
      }
      if (String(asset.recommendationStatus ?? "").toUpperCase() === "STALE") {
        exposures.recommendations++;
        lifts.recommendations += 1 - successScore;
      }
    }

    const experimentExposure = Math.max(1, experiments.length);
    for (const experiment of experiments) {
      const statisticalPower = clamp01(experiment.statisticalPower ?? 0);
      const effectSize = Math.abs(experiment.effectSize ?? 0);
      const inconclusivePenalty =
        experiment.winner === "inconclusive" || String(experiment.status).includes("running")
          ? 0.2
          : 0;
      lifts.experiments += clamp01((1 - statisticalPower) * 0.5 + effectSize * 0.3 + inconclusivePenalty);
    }

    const driftSeverityLift =
      driftSignals.length === 0
        ? 0
        : driftSignals.reduce((sum: any, signal: any) => {
            const severity = String(signal.severity ?? "").toLowerCase();
            if (severity === "high" || severity === "critical") return sum + 1;
            if (severity === "medium") return sum + 0.6;
            return sum + 0.3;
          }, 0) / driftSignals.length;
    if (driftSignals.length > 0) {
      lifts.drift = clamp01((lifts.drift / Math.max(1, exposures.drift)) * 0.7 + driftSeverityLift * 0.3) *
        Math.max(1, exposures.drift);
    }

    const rawWeights = {
      quality: exposures.quality === 0 ? 0 : lifts.quality / exposures.quality,
      outcomes: exposures.outcomes === 0 ? 0 : lifts.outcomes / exposures.outcomes,
      drift: exposures.drift === 0 ? 0 : lifts.drift / exposures.drift,
      experiments: lifts.experiments / experimentExposure,
      recommendations:
        exposures.recommendations === 0
          ? 0
          : lifts.recommendations / exposures.recommendations,
    };
    const normalizedWeights = normalizeWeightVector(rawWeights);
    const sampleSize = assets.length + experiments.length + driftSignals.length;
    const confidence = clamp01(sampleSize / 50);

    return {
      sampleSize,
      confidence,
      weights: normalizedWeights,
      observedLift: {
        quality: roundToThree(rawWeights.quality),
        outcomes: roundToThree(rawWeights.outcomes),
        drift: roundToThree(rawWeights.drift),
        experiments: roundToThree(rawWeights.experiments),
        recommendations: roundToThree(rawWeights.recommendations),
      },
    };
  }

  async trainTenantCausalPolicyModel(tenantId: string): Promise<{
    sampleSize: number;
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
    observedLift: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  }> {
    const [experiments, observations] = await Promise.all([
      this.prisma.aBExperiment.findMany({
        where: { tenantId },
        select: {
          id: true,
          status: true,
          winner: true,
          effectSize: true,
          statisticalPower: true,
        },
      }),
      (
        this.prisma as PrismaClient & {
          aBExperimentObservation: {
            findMany: (...args: any[]) => Promise<
              Array<{
                experimentId: string;
                variant: string;
                metricValue: number;
                completed?: boolean | null;
                masteryScore?: number | null;
                feedbackScore?: number | null;
              }>
            >;
          };
        }
      ).aBExperimentObservation.findMany({
        where: { tenantId },
        select: {
          experimentId: true,
          variant: true,
          metricValue: true,
          completed: true,
          masteryScore: true,
          feedbackScore: true,
        },
      }),
    ]);

    const observationsByExperiment = new Map<
      string,
      Array<{
        experimentId: string;
        variant: string;
        metricValue: number;
        completed?: boolean | null;
        masteryScore?: number | null;
        feedbackScore?: number | null;
      }>
    >();
    for (const observation of observations) {
      const rows = observationsByExperiment.get(observation.experimentId) ?? [];
      rows.push(observation);
      observationsByExperiment.set(observation.experimentId, rows);
    }

    const cumulative = {
      quality: 0,
      outcomes: 0,
      drift: 0,
      experiments: 0,
      recommendations: 0,
    };
    let contributingExperiments = 0;

    for (const experiment of experiments) {
      const rows = observationsByExperiment.get(experiment.id) ?? [];
      if (rows.length === 0) {
        continue;
      }
      const control = rows.filter((row: any) => row.variant === "control");
      const treatment = rows.filter((row: any) => row.variant === "treatment");
      if (control.length === 0 || treatment.length === 0) {
        continue;
      }

      contributingExperiments++;
      const power = clamp01(experiment.statisticalPower ?? 0.4);
      const qualityLift = computeVariantLift(control, treatment, (row: any) => row.metricValue);
      const outcomeLift = computeVariantLift(
        control,
        treatment,
        (row: any) => row.masteryScore ?? 0,
      );
      const driftLift = computeVariantLift(
        control,
        treatment,
        (row: any) => (row.completed ? 1 : 0),
      );
      const recommendationLift = computeVariantLift(
        control,
        treatment,
        (row: any) => row.feedbackScore ?? 0,
      );
      const experimentLift = clamp01(
        ((experiment.effectSize ?? 0) > 0 ? experiment.effectSize ?? 0 : 0) * 2,
      );

      cumulative.quality += Math.max(0, qualityLift) * power;
      cumulative.outcomes += Math.max(0, outcomeLift) * power;
      cumulative.drift += Math.max(0, driftLift) * power;
      cumulative.recommendations += Math.max(0, recommendationLift) * power;
      cumulative.experiments += experimentLift * power;
    }

    const divisor = Math.max(1, contributingExperiments);
    const rawWeights = {
      quality: cumulative.quality / divisor,
      outcomes: cumulative.outcomes / divisor,
      drift: cumulative.drift / divisor,
      experiments: cumulative.experiments / divisor,
      recommendations: cumulative.recommendations / divisor,
    };
    const normalizedWeights = normalizeWeightVector(rawWeights);
    const sampleSize = observations.length;
    const confidence = clamp01((contributingExperiments / 8) * 0.6 + (sampleSize / 120) * 0.4);

    return {
      sampleSize,
      confidence,
      weights: normalizedWeights,
      observedLift: {
        quality: roundToThree(rawWeights.quality),
        outcomes: roundToThree(rawWeights.outcomes),
        drift: roundToThree(rawWeights.drift),
        experiments: roundToThree(rawWeights.experiments),
        recommendations: roundToThree(rawWeights.recommendations),
      },
    };
  }

  async simulateTenantPolicyScenarios(
    tenantId: string,
  ): Promise<TenantRemediationPolicyScenarioAnalysis> {
    const profile = await this.summarizeTenantPolicyProfile(tenantId);
    const baseWeights =
      profile.policyModel && profile.causalModel
        ? blendWeightInputs(
            profile.policyModel.weights,
            profile.causalModel.weights,
            profile.policyModel.confidence,
            profile.causalModel.confidence,
          )
        : profile.policyModel?.weights ?? profile.causalModel?.weights ?? profile.priorityWeights;
    const scenarios = (
      [
        { scenario: "baseline", focus: null },
        { scenario: "quality_boost", focus: "quality" },
        { scenario: "outcome_boost", focus: "outcomes" },
        { scenario: "drift_boost", focus: "drift" },
        { scenario: "experiment_boost", focus: "experiments" },
        { scenario: "recommendation_boost", focus: "recommendations" },
      ] as const
    ).map((scenario: any) => {
      const weights = scenario.focus
        ? normalizeWeightVector({
            quality: baseWeights.quality * (scenario.focus === "quality" ? 1.25 : 1),
            outcomes: baseWeights.outcomes * (scenario.focus === "outcomes" ? 1.25 : 1),
            drift: baseWeights.drift * (scenario.focus === "drift" ? 1.25 : 1),
            experiments:
              baseWeights.experiments * (scenario.focus === "experiments" ? 1.25 : 1),
            recommendations:
              baseWeights.recommendations *
              (scenario.focus === "recommendations" ? 1.25 : 1),
          })
        : normalizeWeightVector(baseWeights);
      const ranking = rankPolicyWeights(weights);
      const top = ranking[0] ?? (["balanced", 0] as const);
      const next = ranking[1] ?? (["balanced", 0] as const);
      const primaryDriver =
        top[1] === 0 || top[1] - next[1] < 0.05
          ? "balanced"
          : (top[0] as TenantRemediationPolicyScenarioAnalysis["baselineFocus"]);

      return {
        scenario: scenario.scenario,
        primaryDriver,
        weights,
        expectedPriority: roundToThree(top[1]),
      };
    });

    const recommendedScenario =
      scenarios
        .filter((scenario: any) => scenario.scenario !== "baseline")
        .sort((a: any, b: any) => b.expectedPriority - a.expectedPriority)[0]?.scenario ??
      "baseline";

    return {
      tenantId,
      baselineFocus: profile.recommendedFocus,
      baselineConfidence:
        Math.max(profile.policyModel?.confidence ?? 0, profile.causalModel?.confidence ?? 0),
      scenarios,
      recommendedScenario,
    };
  }

  async rankExperienceInterventions(
    tenantId: string,
    experienceId: string,
  ): Promise<ExperienceRemediationInterventionPlan> {
    const [summary, policyModel, causalModel] = await Promise.all([
      this.summarizeExperience(tenantId, experienceId),
      this.trainTenantPolicyModel(tenantId),
      this.trainTenantCausalPolicyModel(tenantId),
    ]);
    const blended = blendPolicyModels(policyModel, causalModel);

    const interventions = [
      {
        action: "apply_quality_predictions" as const,
        dimension: "quality" as const,
        score: (summary.policyBreakdown?.qualityPriority ?? 0) * blended.weights.quality,
        expectedImpact: blended.observedLift.quality,
        confidence: blended.confidence,
        source: blended.source,
        rationale: "Low confidence or quality signals are present on the experience asset set.",
      },
      {
        action: "recompute_asset_outcomes" as const,
        dimension: "outcomes" as const,
        score: (summary.policyBreakdown?.outcomePriority ?? 0) * blended.weights.outcomes,
        expectedImpact: blended.observedLift.outcomes,
        confidence: blended.confidence,
        source: blended.source,
        rationale: "Outcome health indicates watch/intervene assets that should be recomputed.",
      },
      {
        action: "refresh_recommendation_edges" as const,
        dimension: "recommendations" as const,
        score:
          (summary.policyBreakdown?.recommendationPriority ?? 0) *
          blended.weights.recommendations,
        expectedImpact: blended.observedLift.recommendations,
        confidence: blended.confidence,
        source: blended.source,
        rationale: "Recommendation staleness and feedback imply ranking refresh value.",
      },
      {
        action: "scan_adaptive_drift" as const,
        dimension: "drift" as const,
        score: (summary.policyBreakdown?.driftPriority ?? 0) * blended.weights.drift,
        expectedImpact: blended.observedLift.drift,
        confidence: blended.confidence,
        source: blended.source,
        rationale: "Adaptive drift indicators imply misalignment between content and learner behavior.",
      },
      {
        action: "evaluate_active_experiments" as const,
        dimension: "experiments" as const,
        score:
          (summary.policyBreakdown?.experimentPriority ?? 0) * blended.weights.experiments,
        expectedImpact: blended.observedLift.experiments,
        confidence: blended.confidence,
        source: "causal_proxy" as const,
        rationale: "Active experiments provide the strongest direct uplift proxy for intervention choice.",
      },
      {
        action: "promote_experiment_winners" as const,
        dimension: "experiments" as const,
        score:
          summary.promotableExperiments > 0
            ? Math.max(
                0.05,
                (summary.policyBreakdown?.experimentPriority ?? 0) *
                  policyModel.weights.experiments *
                  blended.weights.experiments /
                  Math.max(0.0001, policyModel.weights.experiments) *
                  1.15,
              )
            : 0,
        expectedImpact:
          summary.promotableExperiments > 0
            ? roundToThree(blended.observedLift.experiments * 1.1)
            : 0,
        confidence: blended.confidence,
        source: "causal_proxy" as const,
        rationale: "A promoted winner is the closest available proxy for observed causal uplift.",
      },
    ]
      .filter((intervention: any) => intervention.score > 0)
      .sort((left: any, right: any) => right.score - left.score)
      .map((intervention: any) => ({
        ...intervention,
        score: roundToThree(intervention.score),
      }));

    return {
      experienceId,
      primaryDriver: summary.policyBreakdown?.primaryDriver ?? "balanced",
      interventions,
    };
  }

  async applyRankedExperienceInterventions(
    tenantId: string,
    experienceId: string,
    input: {
      limit?: number;
    } = {},
  ): Promise<ExperienceRemediationInterventionExecution> {
    const [plan, baselineSummary] = await Promise.all([
      this.rankExperienceInterventions(tenantId, experienceId),
      this.summarizeExperience(tenantId, experienceId),
    ]);
    const limit = Math.max(1, input.limit ?? 3);
    const selected = plan.interventions.slice(0, limit).map((intervention: any) => intervention.action);

    return this.executeExperienceInterventions(
      tenantId,
      experienceId,
      selected,
      baselineSummary,
    );
  }

  async rankTenantPortfolioInterventions(
    tenantId: string,
    input: {
      experienceLimit?: number;
      interventionsPerExperience?: number;
    } = {},
  ): Promise<TenantRemediationPortfolioPlan> {
    const experienceLimit = Math.max(1, input.experienceLimit ?? 10);
    const interventionsPerExperience = Math.max(1, input.interventionsPerExperience ?? 2);
    const portfolio = await this.rankTenantRemediationPortfolio(tenantId, {
      limit: experienceLimit,
    });
    const interventions: TenantPortfolioRemediationIntervention[] = [];

    for (const experience of portfolio.experiences) {
      const plan = await this.rankExperienceInterventions(tenantId, experience.experienceId);
      for (const intervention of plan.interventions.slice(0, interventionsPerExperience)) {
        interventions.push({
          ...intervention,
          experienceId: experience.experienceId,
          ...(experience.title ? { title: experience.title } : {}),
          priorityScore: roundToThree(
            intervention.score * Math.max(0.05, experience.priorityScore) * intervention.confidence,
          ),
          primaryDriver: experience.primaryDriver,
        });
      }
    }

    interventions.sort((left: any, right: any) => right.priorityScore - left.priorityScore);

    return {
      tenantId,
      generatedAt: new Date().toISOString(),
      interventions,
    };
  }

  async applyTenantPortfolioInterventions(
    tenantId: string,
    input: {
      experienceLimit?: number;
      interventionsPerExperience?: number;
      maxActions?: number;
    } = {},
  ): Promise<TenantRemediationPortfolioExecution> {
    const maxActions = Math.max(1, input.maxActions ?? 6);
    const plan = await this.rankTenantPortfolioInterventions(tenantId, input);
    const selected = plan.interventions.slice(0, maxActions);
    const actionsByExperience = new Map<
      string,
      {
        title?: string;
        actions: ExperienceRemediationInterventionPlan["interventions"][number]["action"][];
      }
    >();

    for (const intervention of selected) {
      const entry = actionsByExperience.get(intervention.experienceId) ?? {
        ...(intervention.title ? { title: intervention.title } : {}),
        actions: [] as ExperienceRemediationInterventionPlan["interventions"][number]["action"][],
      };
      if (!entry.actions.includes(intervention.action)) {
        entry.actions.push(intervention.action);
      }
      actionsByExperience.set(intervention.experienceId, entry);
    }

    const items = [];
    for (const [experienceId, entry] of actionsByExperience.entries()) {
      const baselineSummary = await this.summarizeExperience(tenantId, experienceId);
      const result = await this.executeExperienceInterventions(
        tenantId,
        experienceId,
        entry.actions,
        baselineSummary,
      );
      items.push({
        experienceId,
        ...(entry.title ? { title: entry.title } : {}),
        selectedActions: entry.actions,
        result,
      });
    }

    return {
      tenantId,
      generatedAt: new Date().toISOString(),
      processedExperiences: items.length,
      appliedExperiences: items.filter((item: any) => item.result.appliedActions.length > 0).length,
      totalAppliedActions: items.reduce(
        (sum: any, item: any) => sum + item.result.appliedActions.length,
        0,
      ),
      items,
    };
  }

  private async executeExperienceInterventions(
    tenantId: string,
    experienceId: string,
    selected: ExperienceRemediationInterventionPlan["interventions"][number]["action"][],
    baselineSummary: ExperienceRemediationSummary,
  ): Promise<ExperienceRemediationInterventionExecution> {
    const appliedActions: string[] = [];
    const skippedActions: string[] = [];

    for (const intervention of selected) {
      switch (intervention) {
        case "apply_quality_predictions": {
          const predictions = await this.qualityPipeline.applyPredictionsForExperience(
            tenantId,
            experienceId,
          );
          if (predictions.length > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
        case "recompute_asset_outcomes": {
          const outcome = await this.outcomeService.analyzeExperienceAssets(
            tenantId,
            experienceId,
            {
              apply: true,
              recomputeRecommendations: false,
            },
          );
          if (outcome.totalAssets > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
        case "refresh_recommendation_edges": {
          const refreshed =
            await this.recommendationService.recomputeOutcomeAwareEdgesForExperience(
              tenantId,
              experienceId,
            );
          if (refreshed.updatedEdges > 0 || refreshed.processedAssets > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
        case "scan_adaptive_drift": {
          const drift = await this.driftDetector.scanExperienceAdaptive(
            tenantId,
            experienceId,
          );
          if (drift.signals.length > 0 || drift.insights.length > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
        case "evaluate_active_experiments": {
          const result = await this.experimentService.evaluateActiveExperiments(
            tenantId,
            {
              experienceId,
              autoPromote: false,
            },
          );
          if (result.evaluated > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
        case "promote_experiment_winners": {
          const evaluated = await this.experimentService.evaluateActiveExperiments(
            tenantId,
            {
              experienceId,
              autoPromote: true,
            },
          );
          if (evaluated.promoted > 0) {
            appliedActions.push(intervention);
          } else {
            skippedActions.push(intervention);
          }
          break;
        }
      }
    }

    const summary = await this.summarizeExperience(tenantId, experienceId);

    return {
      experienceId,
      appliedActions,
      skippedActions,
      limit: selected.length,
      baselineSummary,
      summary,
      delta: {
        healthyAssets: summary.healthyAssets - baselineSummary.healthyAssets,
        watchAssets: summary.watchAssets - baselineSummary.watchAssets,
        interveneAssets: summary.interveneAssets - baselineSummary.interveneAssets,
        driftSignalCount: summary.driftSignalCount - baselineSummary.driftSignalCount,
        promotableExperiments:
          summary.promotableExperiments - baselineSummary.promotableExperiments,
      },
    };
  }

  async rankTenantRemediationPortfolio(
    tenantId: string,
    input: { limit?: number } = {},
  ): Promise<TenantRemediationPortfolio> {
    const experiences = await this.prisma.learningExperience.findMany({
      where: { tenantId },
      select: {
        id: true,
        title: true,
      },
      orderBy: { updatedAt: "desc" },
      take: input.limit ?? 25,
    });

    const ranked = [];
    for (const experience of experiences) {
      const summary = await this.summarizeExperience(tenantId, experience.id);
      const priorityScore = roundToThree(
        (summary.policyBreakdown?.qualityPriority ?? 0) * 0.2 +
          (summary.policyBreakdown?.outcomePriority ?? 0) * 0.25 +
          (summary.policyBreakdown?.driftPriority ?? 0) * 0.2 +
          (summary.policyBreakdown?.experimentPriority ?? 0) * 0.15 +
          (summary.policyBreakdown?.recommendationPriority ?? 0) * 0.2,
      );

      ranked.push({
        experienceId: experience.id,
        ...(experience.title ? { title: experience.title } : {}),
        priorityScore,
        primaryDriver: summary.policyBreakdown?.primaryDriver ?? "balanced",
        totalAssets: summary.totalAssets,
        interveneAssets: summary.interveneAssets,
        driftSignalCount: summary.driftSignalCount,
        promotableExperiments: summary.promotableExperiments,
      });
    }

    ranked.sort((left: any, right: any) => right.priorityScore - left.priorityScore);

    return {
      tenantId,
      generatedAt: new Date().toISOString(),
      experiences: ranked,
    };
  }
}

function parseReviewState(value: string | null | undefined):
  | {
      healthStatus?: "healthy" | "watch" | "intervene";
    }
  | null {
  if (!value) {
    return null;
  }

  try {
    const parsed = JSON.parse(value) as { healthStatus?: "healthy" | "watch" | "intervene" };
    return parsed;
  } catch {
    return null;
  }
}

function buildRemediationSummary(input: {
  experienceId: string;
  outcome: Awaited<ReturnType<AssetOutcomeService["analyzeExperienceAssets"]>>;
  drift: Awaited<ReturnType<ReturnType<typeof createContentDriftDetector>["scanExperienceAdaptive"]>>;
  experiments: Array<{ id?: string; status?: string; winner?: string | null }>;
  qualityPredictionsApplied?: number;
  recommendationRefresh?: {
    processedAssets: number;
    updatedEdges: number;
    skippedEdges: number;
  };
  policyModel?: {
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  };
  causalModel?: {
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  };
  executedActions?: string[];
}): ExperienceRemediationSummary {
  const recommendedActions = new Set<string>();
  for (const asset of input.outcome.assets) {
    for (const action of asset.recommendedActions) {
      recommendedActions.add(action);
    }
  }
  for (const signal of input.drift.signals) {
    recommendedActions.add(`drift:${signal.signalType}`);
  }
  if (input.experiments.some((experiment: any) => experiment.winner === "treatment")) {
    recommendedActions.add("promote_successful_variant_signals");
  }

  const qualityPriority =
    input.outcome.totalAssets === 0
      ? 0
      : (input.outcome.watchAssets + input.outcome.interveneAssets) /
        input.outcome.totalAssets;
  const outcomePriority =
    input.outcome.totalAssets === 0 ? 0 : input.outcome.interveneAssets / input.outcome.totalAssets;
  const driftPriority = Math.min(1, input.drift.signals.length / 5);
  const experimentPriority =
    input.experiments.length === 0
      ? 0
      : input.experiments.filter(
          (experiment: any) =>
            experiment.winner === "treatment" ||
            String(experiment.status ?? "").includes("ready_to_promote") ||
            String(experiment.status ?? "").includes("winner_promoted"),
        ).length / input.experiments.length;
  const recommendationPriority = input.recommendationRefresh
    ? input.recommendationRefresh.processedAssets === 0
      ? 0
      : input.recommendationRefresh.updatedEdges /
        Math.max(1, input.recommendationRefresh.processedAssets * 3)
    : input.outcome.assets.length === 0
      ? 0
      : input.outcome.assets.filter((asset: any) =>
          asset.recommendedActions.includes("recompute_recommendations"),
        ).length / input.outcome.assets.length;

  const blendedWeights = blendWeightInputs(
    input.policyModel?.weights,
    input.causalModel?.weights,
    input.policyModel?.confidence ?? 0,
    input.causalModel?.confidence ?? 0,
  );
  const policySource =
    input.policyModel && input.causalModel && (input.causalModel.confidence ?? 0) >= 0.15
      ? "trained_causal_blend"
      : input.policyModel
        ? "trained_empirical"
        : "heuristic";
  const weightedPriorities = {
    quality: qualityPriority * blendedWeights.quality,
    outcomes: outcomePriority * blendedWeights.outcomes,
    drift: driftPriority * blendedWeights.drift,
    experiments: experimentPriority * blendedWeights.experiments,
    recommendations: recommendationPriority * blendedWeights.recommendations,
  };
  const priorityPairs = [
    ["quality", weightedPriorities.quality],
    ["outcomes", weightedPriorities.outcomes],
    ["drift", weightedPriorities.drift],
    ["experiments", weightedPriorities.experiments],
    ["recommendations", weightedPriorities.recommendations],
  ] as const;
  const sortedPriorities = [...priorityPairs].sort((a: any, b: any) => b[1] - a[1]);
  const topPriority = sortedPriorities[0] ?? ["balanced", 0] as const;
  const nextPriority = sortedPriorities[1] ?? ["balanced", 0] as const;
  const primaryDriver =
    sortedPriorities.length < 2 ||
    topPriority[1] === 0 ||
    topPriority[1] - nextPriority[1] < 0.1
      ? "balanced"
      : topPriority[0];

  return {
    experienceId: input.experienceId,
    totalAssets: input.outcome.totalAssets,
    healthyAssets: input.outcome.healthyAssets,
    watchAssets: input.outcome.watchAssets,
    interveneAssets: input.outcome.interveneAssets,
    driftSignalCount: input.drift.signals.length,
    driftInsightCount: input.drift.insights.length,
    runningExperiments: input.experiments.filter((experiment: any) =>
      String(experiment.status ?? "").includes("running"),
    ).length,
    promotableExperiments: input.experiments.filter(
      (experiment: any) =>
        experiment.winner === "treatment" ||
        String(experiment.status ?? "").includes("winner_promoted") ||
        String(experiment.status ?? "").includes("ready_to_promote"),
    ).length,
    recommendedActions: Array.from(recommendedActions),
    ...(input.qualityPredictionsApplied !== undefined
      ? { qualityPredictionsApplied: input.qualityPredictionsApplied }
      : {}),
    ...(input.recommendationRefresh
      ? { recommendationRefresh: input.recommendationRefresh }
      : {}),
    policyBreakdown: {
      primaryDriver,
      policySource,
      qualityPriority,
      outcomePriority,
      driftPriority,
      experimentPriority,
      recommendationPriority,
      ...(input.policyModel ? { learnedWeights: input.policyModel.weights } : {}),
      ...(input.causalModel ? { causalWeights: input.causalModel.weights } : {}),
      ...((input.policyModel || input.causalModel)
        ? {
            modelConfidence: roundToThree(
              Math.max(input.policyModel?.confidence ?? 0, input.causalModel?.confidence ?? 0),
            ),
          }
        : {}),
    },
    ...(input.executedActions?.length
      ? { executedActions: input.executedActions }
      : {}),
  };
}

function blendWeightInputs(
  empirical:
    | {
        quality: number;
        outcomes: number;
        drift: number;
        experiments: number;
        recommendations: number;
      }
    | undefined,
  causal:
    | {
        quality: number;
        outcomes: number;
        drift: number;
        experiments: number;
        recommendations: number;
      }
    | undefined,
  empiricalConfidence: number,
  causalConfidence: number,
) {
  if (!empirical && !causal) {
    return {
      quality: 1,
      outcomes: 1,
      drift: 1,
      experiments: 1,
      recommendations: 1,
    };
  }
  if (!causal) {
    return empirical ?? {
      quality: 1,
      outcomes: 1,
      drift: 1,
      experiments: 1,
      recommendations: 1,
    };
  }
  if (!empirical) {
    return causal;
  }

  const blend = derivePolicyBlend(empiricalConfidence, causalConfidence);
  return {
    quality: empirical.quality * blend.empiricalWeight + causal.quality * blend.causalWeight,
    outcomes:
      empirical.outcomes * blend.empiricalWeight + causal.outcomes * blend.causalWeight,
    drift: empirical.drift * blend.empiricalWeight + causal.drift * blend.causalWeight,
    experiments:
      empirical.experiments * blend.empiricalWeight +
      causal.experiments * blend.causalWeight,
    recommendations:
      empirical.recommendations * blend.empiricalWeight +
      causal.recommendations * blend.causalWeight,
  };
}

function derivePolicyBlend(empiricalConfidence: number, causalConfidence: number) {
  const empiricalWeight = clamp01(Math.max(0.2, empiricalConfidence));
  const causalWeight = clamp01(Math.max(0.1, causalConfidence));
  const total = empiricalWeight + causalWeight;
  return {
    empiricalWeight: roundToThree(empiricalWeight / total),
    causalWeight: roundToThree(causalWeight / total),
  };
}

function blendPolicyModels(
  empirical: {
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
    observedLift: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  },
  causal: {
    confidence: number;
    weights: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
    observedLift: {
      quality: number;
      outcomes: number;
      drift: number;
      experiments: number;
      recommendations: number;
    };
  },
) {
  const blend = derivePolicyBlend(empirical.confidence, causal.confidence);
  return {
    source:
      causal.confidence >= empirical.confidence * 0.8
        ? ("trained_causal" as const)
        : ("trained_empirical" as const),
    confidence: roundToThree(
      empirical.confidence * blend.empiricalWeight + causal.confidence * blend.causalWeight,
    ),
    weights: normalizeWeightVector({
      quality:
        empirical.weights.quality * blend.empiricalWeight +
        causal.weights.quality * blend.causalWeight,
      outcomes:
        empirical.weights.outcomes * blend.empiricalWeight +
        causal.weights.outcomes * blend.causalWeight,
      drift:
        empirical.weights.drift * blend.empiricalWeight +
        causal.weights.drift * blend.causalWeight,
      experiments:
        empirical.weights.experiments * blend.empiricalWeight +
        causal.weights.experiments * blend.causalWeight,
      recommendations:
        empirical.weights.recommendations * blend.empiricalWeight +
        causal.weights.recommendations * blend.causalWeight,
    }),
    observedLift: {
      quality: roundToThree(
        empirical.observedLift.quality * blend.empiricalWeight +
          causal.observedLift.quality * blend.causalWeight,
      ),
      outcomes: roundToThree(
        empirical.observedLift.outcomes * blend.empiricalWeight +
          causal.observedLift.outcomes * blend.causalWeight,
      ),
      drift: roundToThree(
        empirical.observedLift.drift * blend.empiricalWeight +
          causal.observedLift.drift * blend.causalWeight,
      ),
      experiments: roundToThree(
        empirical.observedLift.experiments * blend.empiricalWeight +
          causal.observedLift.experiments * blend.causalWeight,
      ),
      recommendations: roundToThree(
        empirical.observedLift.recommendations * blend.empiricalWeight +
          causal.observedLift.recommendations * blend.causalWeight,
      ),
    },
  };
}

function computeVariantLift<T>(
  control: T[],
  treatment: T[],
  selector: (row: T) => number,
) {
  const controlMean =
    control.length === 0 ? 0 : control.reduce((sum: number, row: T) => sum + selector(row), 0) / control.length;
  const treatmentMean =
    treatment.length === 0
      ? 0
      : treatment.reduce((sum: number, row: T) => sum + selector(row), 0) / treatment.length;
  if (controlMean === 0) {
    return treatmentMean;
  }
  return (treatmentMean - controlMean) / Math.abs(controlMean);
}

function normalizeScore(value: number | null | undefined): number {
  if (value == null) {
    return 0.5;
  }
  return clamp01(value > 1 ? value / 100 : value);
}

function normalizeWeightVector(weights: {
  quality: number;
  outcomes: number;
  drift: number;
  experiments: number;
  recommendations: number;
}) {
  const total = Object.values(weights).reduce((sum: number, value: number) => sum + value, 0);
  if (total <= 0) {
    return {
      quality: 0.2,
      outcomes: 0.2,
      drift: 0.2,
      experiments: 0.2,
      recommendations: 0.2,
    };
  }

  return {
    quality: roundToThree(weights.quality / total),
    outcomes: roundToThree(weights.outcomes / total),
    drift: roundToThree(weights.drift / total),
    experiments: roundToThree(weights.experiments / total),
    recommendations: roundToThree(weights.recommendations / total),
  };
}

function rankPolicyWeights(weights: {
  quality: number;
  outcomes: number;
  drift: number;
  experiments: number;
  recommendations: number;
}) {
  const entries: Array<
    [
      "quality" | "outcomes" | "drift" | "experiments" | "recommendations",
      number,
    ]
  > = [
    ["quality", weights.quality],
    ["outcomes", weights.outcomes],
    ["drift", weights.drift],
    ["experiments", weights.experiments],
    ["recommendations", weights.recommendations],
  ];
  return entries.sort((a, b) => b[1] - a[1]);
}

function summarizePolicyTelemetry(
  events: Array<{ eventType?: string | null; feedbackLabel?: string | null }>,
): {
  ctr: number;
  completionRate: number;
  feedbackRatio: number;
} {
  let impressions = 0;
  let clicks = 0;
  let completions = 0;
  let positiveFeedback = 0;
  let negativeFeedback = 0;

  for (const event of events) {
    const eventType = String(event.eventType ?? "").toUpperCase();
    if (eventType === "IMPRESSION") impressions++;
    if (eventType === "CLICK") clicks++;
    if (eventType === "ASSET_COMPLETE") completions++;
    if (eventType === "RANKING_FEEDBACK") {
      const label = String(event.feedbackLabel ?? "").toLowerCase();
      if (label === "positive" || label === "helpful" || label === "relevant") {
        positiveFeedback++;
      }
      if (
        label === "negative" ||
        label === "not_relevant" ||
        label === "unhelpful"
      ) {
        negativeFeedback++;
      }
    }
  }

  return {
    ctr: impressions === 0 ? 0 : clicks / impressions,
    completionRate: clicks === 0 ? 0 : completions / clicks,
    feedbackRatio:
      positiveFeedback + negativeFeedback === 0
        ? 0.5
        : positiveFeedback / (positiveFeedback + negativeFeedback),
  };
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function roundToThree(value: number): number {
  return Math.round(value * 1000) / 1000;
}

// =============================================================================
// Advanced Learned Ranking and Causal-Policy Modeling
// =============================================================================

interface InterventionOutcomeRecord {
  interventionId: string;
  action: string;
  dimension: string;
  experienceId: string;
  tenantId: string;
  timestamp: string;
  predictedImpact: number;
  actualImpact?: number;
  successMetrics: {
    qualityDelta?: number;
    outcomeDelta?: number;
    engagementDelta?: number;
  };
  feedbackScore?: number;
  learnerSatisfaction?: number;
}

interface CounterfactualPolicy {
  policyId: string;
  tenantId: string;
  scenario: string;
  hypotheticalWeights: Record<string, number>;
  predictedOutcomes: Record<string, number>;
  confidence: number;
  evidenceCount: number;
}

export class LearnedInterventionRanker {
  private outcomeHistory: InterventionOutcomeRecord[] = [];
  private counterfactualModels: Map<string, CounterfactualPolicy> = new Map();

  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Record intervention outcome for learning
   */
  async recordInterventionOutcome(
    outcome: InterventionOutcomeRecord,
  ): Promise<void> {
    this.outcomeHistory.push(outcome);
    
    // Persist to database for long-term learning
    await this.prisma.$executeRaw`
      INSERT INTO intervention_outcome_logs (
        intervention_id, action, dimension, experience_id, tenant_id,
        timestamp, predicted_impact, actual_impact, feedback_score
      ) VALUES (
        ${outcome.interventionId}, ${outcome.action}, ${outcome.dimension},
        ${outcome.experienceId}, ${outcome.tenantId}, ${new Date(outcome.timestamp)},
        ${outcome.predictedImpact}, ${outcome.actualImpact ?? null},
        ${outcome.feedbackScore ?? null}
      )
    `.catch(() => {
      // Table may not exist, silently fail for now
    });
  }

  /**
   * Learned ranking of interventions based on historical outcomes
   */
  async rankInterventionsWithLearning(
    tenantId: string,
    candidateInterventions: Array<{
      action: string;
      dimension: string;
      predictedImpact: number;
    }>,
  ): Promise<Array<{
    action: string;
    dimension: string;
    learnedScore: number;
    predictedImpact: number;
    confidence: number;
    historicalSuccessRate: number;
  }>> {
    // Get historical outcomes for this tenant
    const historicalOutcomes = this.outcomeHistory.filter(
      (o: InterventionOutcomeRecord) => o.tenantId === tenantId,
    );

    // Calculate per-action success rates
    const actionStats = new Map<
      string,
      { successes: number; total: number; avgImpact: number }
    >();

    for (const outcome of historicalOutcomes) {
      const key = `${outcome.action}:${outcome.dimension}`;
      const stats = actionStats.get(key) ?? { successes: 0, total: 0, avgImpact: 0 };
      stats.total++;
      
      const actualImpact = outcome.actualImpact ?? outcome.predictedImpact;
      const wasSuccessful = actualImpact > 0.3 && (outcome.feedbackScore ?? 0.5) > 0.5;
      
      if (wasSuccessful) {
        stats.successes++;
      }
      stats.avgImpact = (stats.avgImpact * (stats.total - 1) + actualImpact) / stats.total;
      actionStats.set(key, stats);
    }

    // Score candidates with learned weights
    return candidateInterventions.map((intervention) => {
      const key = `${intervention.action}:${intervention.dimension}`;
      const stats = actionStats.get(key);
      
      const historicalSuccessRate = stats 
        ? stats.successes / Math.max(1, stats.total)
        : 0.5;
      
      const experienceWeight = stats ? Math.min(1, stats.total / 10) : 0;
      
      // Blend predicted impact with learned success rate
      const learnedScore = 
        intervention.predictedImpact * (1 - experienceWeight * 0.3) +
        (stats?.avgImpact ?? intervention.predictedImpact) * experienceWeight * 0.3 +
        historicalSuccessRate * 0.1;

      return {
        action: intervention.action,
        dimension: intervention.dimension,
        learnedScore: roundToThree(learnedScore),
        predictedImpact: intervention.predictedImpact,
        confidence: 0.5 + experienceWeight * 0.5,
        historicalSuccessRate: roundToThree(historicalSuccessRate),
      };
    }).sort((a, b) => b.learnedScore - a.learnedScore);
  }

  /**
   * Build counterfactual policy model for "what-if" scenarios
   */
  async buildCounterfactualPolicy(
    tenantId: string,
    scenario: string,
    hypotheticalChanges: Record<string, number>,
  ): Promise<CounterfactualPolicy> {
    // Retrieve historical data for this scenario type
    const relevantOutcomes = this.outcomeHistory.filter(
      (o: InterventionOutcomeRecord) => o.tenantId === tenantId && o.action.includes(scenario),
    );

    // Simple counterfactual: extrapolate from observed outcomes
    const predictedOutcomes: Record<string, number> = {};
    
    for (const [dimension, change] of Object.entries(hypotheticalChanges)) {
      const relatedOutcomes = relevantOutcomes.filter(
        (o: InterventionOutcomeRecord) => o.dimension === dimension,
      );
      
      if (relatedOutcomes.length === 0) {
        predictedOutcomes[dimension] = change * 0.5; // Conservative estimate
        continue;
      }
      
      const avgActualImpact = relatedOutcomes.reduce(
        (sum: number, o: InterventionOutcomeRecord) => sum + (o.actualImpact ?? o.predictedImpact),
        0,
      ) / relatedOutcomes.length;
      
      // Counterfactual: what if we had applied 2x the intervention?
      predictedOutcomes[dimension] = avgActualImpact * (1 + change);
    }

    const confidence = Math.min(0.9, relevantOutcomes.length / 50);
    
    const policy: CounterfactualPolicy = {
      policyId: `cf-${tenantId}-${scenario}-${Date.now()}`,
      tenantId,
      scenario,
      hypotheticalWeights: hypotheticalChanges,
      predictedOutcomes,
      confidence,
      evidenceCount: relevantOutcomes.length,
    };
    
    this.counterfactualModels.set(policy.policyId, policy);
    return policy;
  }

  /**
   * Predict intervention outcomes using causal inference patterns
   */
  async predictInterventionOutcomes(
    tenantId: string,
    proposedInterventions: Array<{
      action: string;
      dimension: string;
      targetExperienceId: string;
    }>,
  ): Promise<Array<{
    action: string;
    dimension: string;
    predictedQualityDelta: number;
    predictedOutcomeDelta: number;
    predictedEngagementDelta: number;
    confidence: number;
    recommendedTiming: "immediate" | "delayed" | "batched";
  }>> {
    // Analyze historical patterns
    const historicalOutcomes = this.outcomeHistory.filter(
      (o: InterventionOutcomeRecord) => o.tenantId === tenantId,
    );

    return proposedInterventions.map((intervention) => {
      // Find similar past interventions
      const similarOutcomes = historicalOutcomes.filter(
        (o: InterventionOutcomeRecord) => o.action === intervention.action && o.dimension === intervention.dimension,
      );

      // Calculate average deltas
      const avgQualityDelta = similarOutcomes.length > 0
        ? similarOutcomes.reduce((sum: number, o: InterventionOutcomeRecord) => sum + (o.successMetrics?.qualityDelta ?? 0), 0) 
          / similarOutcomes.length
        : 0.1;
        
      const avgOutcomeDelta = similarOutcomes.length > 0
        ? similarOutcomes.reduce((sum: number, o: InterventionOutcomeRecord) => sum + (o.successMetrics?.outcomeDelta ?? 0), 0) 
          / similarOutcomes.length
        : 0.15;
        
      const avgEngagementDelta = similarOutcomes.length > 0
        ? similarOutcomes.reduce((sum: number, o: InterventionOutcomeRecord) => sum + (o.successMetrics?.engagementDelta ?? 0), 0) 
          / similarOutcomes.length
        : 0.05;

      // Determine confidence based on sample size
      const confidence = Math.min(0.95, 0.3 + similarOutcomes.length / 20);

      // Recommend timing based on historical patterns
      const recommendedTiming: "immediate" | "delayed" | "batched" = 
        similarOutcomes.length > 5 && avgQualityDelta > 0.2
          ? "immediate"
          : similarOutcomes.length > 2
            ? "batched"
            : "delayed";

      return {
        action: intervention.action,
        dimension: intervention.dimension,
        predictedQualityDelta: roundToThree(avgQualityDelta),
        predictedOutcomeDelta: roundToThree(avgOutcomeDelta),
        predictedEngagementDelta: roundToThree(avgEngagementDelta),
        confidence: roundToThree(confidence),
        recommendedTiming,
      };
    });
  }

  /**
   * Update policy weights based on actual intervention outcomes
   */
  async updatePolicyFromOutcomes(
    currentPolicy: {
      weights: Record<string, number>;
      confidence: number;
    },
    recentOutcomes: InterventionOutcomeRecord[],
  ): Promise<{
    updatedWeights: Record<string, number>;
    learningDelta: number;
    confidenceChange: number;
  }> {
    if (recentOutcomes.length === 0) {
      return {
        updatedWeights: currentPolicy.weights,
        learningDelta: 0,
        confidenceChange: 0,
      };
    }

    // Calculate performance by dimension
    const dimensionPerformance = new Map<string, { expected: number; actual: number }>();
    
    for (const outcome of recentOutcomes) {
      const perf = dimensionPerformance.get(outcome.dimension) ?? { expected: 0, actual: 0 };
      perf.expected += outcome.predictedImpact;
      perf.actual += outcome.actualImpact ?? outcome.predictedImpact;
      dimensionPerformance.set(outcome.dimension, perf);
    }

    // Adjust weights based on prediction accuracy
    const updatedWeights: Record<string, number> = { ...currentPolicy.weights };
    let totalAdjustment = 0;

    for (const [dimension, perf] of dimensionPerformance.entries()) {
      const predictionError = Math.abs(perf.expected - perf.actual);
      const direction = perf.actual > perf.expected ? 1 : -1;
      
      // Boost weight if predictions were conservative, reduce if overconfident
      const adjustment = direction * Math.min(0.1, predictionError * 0.05);
      updatedWeights[dimension] = clamp01((updatedWeights[dimension] ?? 0.2) + adjustment);
      totalAdjustment += Math.abs(adjustment);
    }

    // Normalize weights
    const weightSum = Object.values(updatedWeights).reduce((a: number, b: number) => a + b, 0);
    for (const key of Object.keys(updatedWeights)) {
      updatedWeights[key] = updatedWeights[key]! / weightSum;
    }

    // Confidence increases with more outcomes, up to a point
    const confidenceChange = Math.min(0.05, recentOutcomes.length / 200);

    return {
      updatedWeights,
      learningDelta: roundToThree(totalAdjustment),
      confidenceChange: roundToThree(confidenceChange),
    };
  }
}

// Extend the ExperienceRemediationService with learned ranking capabilities
export interface RemediationServiceWithLearning {
  learnedRanker: LearnedInterventionRanker;
  applyRemediationWithLearning(tenantId: string, experienceId: string): Promise<{
    interventions: Array<{
      action: string;
      learnedScore: number;
      predictedImpact: number;
      historicalSuccessRate: number;
    }>;
    execution: ExperienceRemediationInterventionExecution;
  }>;
  predictInterventionOutcomes(
    tenantId: string, 
    experienceId: string
  ): Promise<Array<{
    action: string;
    predictedQualityDelta: number;
    predictedOutcomeDelta: number;
    recommendedTiming: string;
  }>>;
  buildCounterfactualScenario(
    tenantId: string,
    scenario: string,
    changes: Record<string, number>
  ): Promise<CounterfactualPolicy>;
  autoUpdatePolicyFromExecutionResults(
    tenantId: string,
    executionId: string
  ): Promise<{
    updated: boolean;
    weightChanges: Record<string, number>;
    newConfidence: number;
  }>;
}
