/**
 * A/B Testing Service
 *
 * Production-safe experimentation over published learning experiences.
 * Uses persisted assignments and observations so results are reproducible
 * and can be promoted without relying on ad hoc in-memory counters.
 *
 * @doc.type class
 * @doc.purpose Manage experience experiments, variant assignment, and results
 * @doc.layer product
 * @doc.pattern Experimentation
 */

import { createHash } from "node:crypto";
import type { PrismaClient } from "@tutorputor/core/db";

export interface CreateABExperimentInput {
  experienceId: string;
  controlVersion: number;
  treatmentVersion: number;
  notes?: string;
  priority?: number;
}

export interface RecordABObservationInput {
  sessionId?: string;
  assetId?: string;
  metricValue: number;
  completed?: boolean;
  masteryScore?: number;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
}

export interface ABVariantSummary {
  variant: "control" | "treatment";
  sampleSize: number;
  mean: number;
  variance: number;
  completionRate: number;
  averageMastery?: number;
  averageFeedback?: number;
}

export interface ABExperimentResult {
  experimentId: string;
  control: ABVariantSummary;
  treatment: ABVariantSummary;
  pValue: number;
  effectSize: number;
  relativeImprovement: number;
  statisticalPower: number;
  winner: "control" | "treatment" | "inconclusive";
  confidenceInterval: {
    lower: number;
    upper: number;
  };
}

export interface EvaluateActiveExperimentsInput {
  minSampleSize?: number;
  autoPromote?: boolean;
  maxPValue?: number;
  minRelativeImprovement?: number;
  experienceId?: string;
}

export class ABTestingService {
  constructor(private readonly prisma: PrismaClient) {}

  // The generated client already includes these models; the platform package's
  // PrismaClient import has not caught up yet, so keep the cast narrowly scoped.
  private get assignmentStore() {
    return (this.prisma as PrismaClient & {
      aBExperimentAssignment: {
        findUnique: (...args: unknown[]) => Promise<unknown>;
        update: (...args: unknown[]) => Promise<unknown>;
        create: (...args: unknown[]) => Promise<unknown>;
      };
    }).aBExperimentAssignment;
  }

  private get observationStore() {
    return (this.prisma as PrismaClient & {
      aBExperimentObservation: {
        findMany: (...args: unknown[]) => Promise<unknown[]>;
        create: (...args: unknown[]) => Promise<unknown>;
      };
    }).aBExperimentObservation;
  }

  async createExperienceExperiment(
    tenantId: string,
    input: CreateABExperimentInput,
  ) {
    return await this.prisma.aBExperiment.create({
      data: {
        tenantId,
        experienceId: input.experienceId,
        controlVersion: input.controlVersion,
        treatmentVersion: input.treatmentVersion,
        status: "draft",
        notes: input.notes ?? null,
        priority: input.priority ?? 0,
      },
    });
  }

  async startExperiment(tenantId: string, experimentId: string) {
    const experiment = await this.requireExperiment(tenantId, experimentId);
    if (experiment.status === "running") {
      return experiment;
    }

    return await this.prisma.aBExperiment.update({
      where: { id: experiment.id },
      data: {
        status: "running",
        startedAt: experiment.startedAt ?? new Date(),
      },
    });
  }

  async assignVariant(
    tenantId: string,
    experimentId: string,
    userId: string,
  ): Promise<"control" | "treatment"> {
    const experiment = await this.requireExperiment(tenantId, experimentId);

    const existing = await this.assignmentStore.findUnique({
      where: { experimentId_userId: { experimentId: experiment.id, userId } },
    });
    if (existing) {
      await this.assignmentStore.update({
        where: { id: existing.id },
        data: { lastSeenAt: new Date() },
      });
      return existing.variant as "control" | "treatment";
    }

    const variant = this.selectVariant(experiment.id, userId);
    await this.assignmentStore.create({
      data: {
        experimentId: experiment.id,
        tenantId,
        userId,
        variant,
      },
    });
    return variant;
  }

  async recordObservation(
    tenantId: string,
    experimentId: string,
    userId: string,
    input: RecordABObservationInput,
  ) {
    const experiment = await this.requireExperiment(tenantId, experimentId);
    const variant = await this.assignVariant(tenantId, experiment.id, userId);

    const observation = await this.observationStore.create({
      data: {
        experimentId: experiment.id,
        tenantId,
        userId,
        variant,
        sessionId: input.sessionId ?? null,
        assetId: input.assetId ?? null,
        metricValue: input.metricValue,
        completed: input.completed ?? null,
        masteryScore: input.masteryScore ?? null,
        feedbackScore: input.feedbackScore ?? null,
        metadata: input.metadata ?? null,
      },
    });

    await this.refreshAggregateMetrics(experiment.id);
    return observation;
  }

  async calculateResults(
    tenantId: string,
    experimentId: string,
  ): Promise<ABExperimentResult> {
    const experiment = await this.requireExperiment(tenantId, experimentId);
    const observations = await this.observationStore.findMany({
      where: { experimentId: experiment.id, tenantId },
      orderBy: { observedAt: "asc" },
    });

    const controlSummary = summarizeVariant("control", observations);
    const treatmentSummary = summarizeVariant("treatment", observations);

    const pValue = calculatePValue(controlSummary, treatmentSummary);
    const effectSize = treatmentSummary.mean - controlSummary.mean;
    const relativeImprovement =
      controlSummary.mean === 0
        ? 0
        : effectSize / Math.abs(controlSummary.mean);
    const standardError = calculateStandardError(controlSummary, treatmentSummary);
    const confidenceInterval = {
      lower: effectSize - 1.96 * standardError,
      upper: effectSize + 1.96 * standardError,
    };
    const power = estimatePower(controlSummary, treatmentSummary, standardError);

    const winner =
      pValue < 0.05 && effectSize > 0
        ? "treatment"
        : pValue < 0.05 && effectSize < 0
          ? "control"
          : "inconclusive";

    return {
      experimentId: experiment.id,
      control: controlSummary,
      treatment: treatmentSummary,
      pValue,
      effectSize,
      relativeImprovement,
      statisticalPower: power,
      winner,
      confidenceInterval,
    };
  }

  async completeExperiment(tenantId: string, experimentId: string) {
    const experiment = await this.requireExperiment(tenantId, experimentId);
    const results = await this.calculateResults(tenantId, experiment.id);

    return await this.prisma.aBExperiment.update({
      where: { id: experiment.id },
      data: {
        status: "completed",
        completedAt: new Date(),
        controlSampleSize: results.control.sampleSize,
        treatmentSampleSize: results.treatment.sampleSize,
        controlMetrics: serializeVariantSummary(results.control),
        treatmentMetrics: serializeVariantSummary(results.treatment),
        pValue: results.pValue,
        confidenceLower: results.confidenceInterval.lower,
        confidenceUpper: results.confidenceInterval.upper,
        effectSize: results.effectSize,
        winner: results.winner,
        statisticalPower: results.statisticalPower,
      },
    });
  }

  async promoteWinner(tenantId: string, experimentId: string) {
    const experiment = await this.requireExperiment(tenantId, experimentId);
    const results = await this.calculateResults(tenantId, experiment.id);

    if (results.winner === "treatment") {
      await this.prisma.learningExperience.update({
        where: { id: experiment.experienceId },
        data: {
          version: experiment.treatmentVersion,
          confidenceScore: results.statisticalPower,
        },
      });
    }

    return await this.prisma.aBExperiment.update({
      where: { id: experiment.id },
      data: {
        status: "winner_promoted",
        completedAt: experiment.completedAt ?? new Date(),
        pValue: results.pValue,
        confidenceLower: results.confidenceInterval.lower,
        confidenceUpper: results.confidenceInterval.upper,
        effectSize: results.effectSize,
        winner: results.winner,
        statisticalPower: results.statisticalPower,
      },
    });
  }

  async listExperiments(
    tenantId: string,
    options: { experienceId?: string } = {},
  ) {
    return await this.prisma.aBExperiment.findMany({
      where: {
        tenantId,
        ...(options.experienceId ? { experienceId: options.experienceId } : {}),
      },
      orderBy: [{ status: "asc" }, { createdAt: "desc" }],
    });
  }

  async evaluateActiveExperiments(
    tenantId: string,
    options: EvaluateActiveExperimentsInput = {},
  ) {
    const minSampleSize = options.minSampleSize ?? 20;
    const maxPValue = options.maxPValue ?? 0.05;
    const minRelativeImprovement = options.minRelativeImprovement ?? 0.05;
    const experiments = await this.prisma.aBExperiment.findMany({
      where: {
        tenantId,
        status: "running",
        ...(options.experienceId ? { experienceId: options.experienceId } : {}),
      },
      orderBy: [{ priority: "desc" }, { createdAt: "asc" }],
    });

    const results = [];
    for (const experiment of experiments) {
      const outcome = await this.calculateResults(tenantId, experiment.id);
      const eligible =
        outcome.control.sampleSize >= minSampleSize &&
        outcome.treatment.sampleSize >= minSampleSize;
      const significant =
        outcome.pValue <= maxPValue &&
        outcome.relativeImprovement >= minRelativeImprovement &&
        outcome.winner === "treatment";

      let status = "insufficient_sample";
      if (eligible) {
        status = significant ? "ready_to_promote" : "completed_no_winner";
        await this.completeExperiment(tenantId, experiment.id);
      }
      if (eligible && significant && options.autoPromote) {
        await this.promoteWinner(tenantId, experiment.id);
        status = "winner_promoted";
      }

      results.push({
        experimentId: experiment.id,
        status,
        eligible,
        significant,
        outcome,
      });
    }

    return {
      evaluated: results.length,
      promoted: results.filter((result) => result.status === "winner_promoted")
        .length,
      results,
    };
  }

  private async refreshAggregateMetrics(experimentId: string) {
    const observations = await this.observationStore.findMany({
      where: { experimentId },
    });
    const controlSummary = summarizeVariant("control", observations);
    const treatmentSummary = summarizeVariant("treatment", observations);

    await this.prisma.aBExperiment.update({
      where: { id: experimentId },
      data: {
        controlSampleSize: controlSummary.sampleSize,
        treatmentSampleSize: treatmentSummary.sampleSize,
        controlMetrics: serializeVariantSummary(controlSummary),
        treatmentMetrics: serializeVariantSummary(treatmentSummary),
      },
    });
  }

  private async requireExperiment(tenantId: string, experimentId: string) {
    const experiment = await this.prisma.aBExperiment.findFirst({
      where: { id: experimentId, tenantId },
    });
    if (!experiment) {
      throw new Error(`Experiment ${experimentId} not found`);
    }
    return experiment;
  }

  private selectVariant(
    experimentId: string,
    userId: string,
  ): "control" | "treatment" {
    const digest = createHash("sha256")
      .update(`${experimentId}:${userId}`)
      .digest("hex");
    const bucket = parseInt(digest.slice(0, 8), 16) / 0xffffffff;
    return bucket < 0.5 ? "control" : "treatment";
  }
}

function summarizeVariant(
  variant: "control" | "treatment",
  observations: Array<{
    variant: string;
    metricValue: number;
    completed: boolean | null;
    masteryScore: number | null;
    feedbackScore: number | null;
  }>,
): ABVariantSummary {
  const rows = observations.filter((observation) => observation.variant === variant);
  const values = rows.map((row) => row.metricValue);
  const mean = values.length === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / values.length;
  const variance =
    values.length <= 1
      ? 0
      : values.reduce((sum, value) => sum + (value - mean) ** 2, 0) /
        (values.length - 1);
  const completedRows = rows.filter((row) => row.completed === true).length;
  const masteryValues = rows
    .map((row) => row.masteryScore)
    .filter((value): value is number => typeof value === "number");
  const feedbackValues = rows
    .map((row) => row.feedbackScore)
    .filter((value): value is number => typeof value === "number");

  const summary: ABVariantSummary = {
    variant,
    sampleSize: rows.length,
    mean,
    variance,
    completionRate: rows.length === 0 ? 0 : completedRows / rows.length,
  };
  if (masteryValues.length > 0) {
    summary.averageMastery =
      masteryValues.reduce((sum, value) => sum + value, 0) /
      masteryValues.length;
  }
  if (feedbackValues.length > 0) {
    summary.averageFeedback =
      feedbackValues.reduce((sum, value) => sum + value, 0) /
      feedbackValues.length;
  }
  return summary;
}

function calculateStandardError(
  control: ABVariantSummary,
  treatment: ABVariantSummary,
): number {
  if (control.sampleSize === 0 || treatment.sampleSize === 0) {
    return 1;
  }

  const pooledVariance =
    ((control.sampleSize - 1) * control.variance +
      (treatment.sampleSize - 1) * treatment.variance) /
    Math.max(1, control.sampleSize + treatment.sampleSize - 2);

  return Math.sqrt(
    pooledVariance *
      (1 / Math.max(1, control.sampleSize) + 1 / Math.max(1, treatment.sampleSize)),
  );
}

function calculatePValue(
  control: ABVariantSummary,
  treatment: ABVariantSummary,
): number {
  if (control.sampleSize < 2 || treatment.sampleSize < 2) {
    return 1;
  }

  const standardError = calculateStandardError(control, treatment);
  if (standardError === 0) {
    return 1;
  }

  const zScore = Math.abs((treatment.mean - control.mean) / standardError);
  return clamp(2 * (1 - normalCdf(zScore)), 0, 1);
}

function estimatePower(
  control: ABVariantSummary,
  treatment: ABVariantSummary,
  standardError: number,
): number {
  if (standardError === 0 || control.sampleSize === 0 || treatment.sampleSize === 0) {
    return 0;
  }
  const effect = Math.abs(treatment.mean - control.mean) / standardError;
  return clamp(normalCdf(effect - 1.96), 0, 1);
}

function normalCdf(x: number): number {
  return 0.5 * (1 + erf(x / Math.sqrt(2)));
}

function erf(x: number): number {
  const sign = x < 0 ? -1 : 1;
  const absolute = Math.abs(x);
  const t = 1 / (1 + 0.3275911 * absolute);
  const y =
    1 -
    (((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) *
      t +
      0.254829592) *
      t *
      Math.exp(-absolute * absolute));
  return sign * y;
}

function serializeVariantSummary(summary: ABVariantSummary) {
  return {
    variant: summary.variant,
    sampleSize: summary.sampleSize,
    mean: summary.mean,
    variance: summary.variance,
    completionRate: summary.completionRate,
    averageMastery: summary.averageMastery ?? null,
    averageFeedback: summary.averageFeedback ?? null,
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
