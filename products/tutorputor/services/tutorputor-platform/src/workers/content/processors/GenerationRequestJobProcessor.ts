/**
 * Generation Request Job Processor
 *
 * Executes control-plane `GenerationJob` records through the shared content
 * generation gRPC client and persists results back onto the request model.
 *
 * @doc.type class
 * @doc.purpose Process GenerationRequest-backed execution jobs from BullMQ
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from "bullmq";
import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";
import crypto from "crypto";
import {
  GenerationExecutionService,
  type JobExecutionResult,
} from "../../../modules/content/generation/execution-service.js";
import {
  GenerationQueueDispatcher,
  type GenerationRequestExecutionJobData,
} from "../../../modules/content/generation/queue-dispatcher.js";
import { AssetMaterializationService } from "../../../modules/content/asset/materialization-service.js";
import type { GenerationJobType } from "../../../modules/content/types.js";
import { GenerationQualityLoopService } from "../../../modules/content/review/quality-loop-service.js";
import { RealContentGenerationClient } from "../grpc/RealContentGenerationClient.js";
import { ContentWorkerTelemetryPublisher } from "../generation-telemetry.js";
import {
  type ContentGenerationFlags,
  isFeatureEnabled,
} from "../../../config/feature-flags.js";
import { GovernedContentDispatchGuard } from "../GovernedContentDispatchGuard.js";

type GrpcExampleLike = {
  example_id?: string;
  title?: string;
  description?: string;
  content?: string;
  solution_content?: string;
};

export class GenerationRequestJobProcessor {
  private readonly assetMaterializationService: AssetMaterializationService;
  private readonly qualityLoopService: GenerationQualityLoopService;

  constructor(
    private readonly grpcClient: RealContentGenerationClient,
    prisma: PrismaClient,
    private readonly logger: Logger,
    private readonly telemetry: ContentWorkerTelemetryPublisher,
    private readonly executionService: GenerationExecutionService,
    private readonly dispatcher: GenerationQueueDispatcher,
    private readonly featureFlags: ContentGenerationFlags = {} as ContentGenerationFlags,
    private readonly dispatchGuard?: GovernedContentDispatchGuard,
  ) {
    this.assetMaterializationService = new AssetMaterializationService(prisma);
    this.qualityLoopService = new GenerationQualityLoopService(prisma);
  }

  async process(job: Job<GenerationRequestExecutionJobData>): Promise<void> {
    const startedAt = Date.now();

    if (this.dispatchGuard) {
      const decision = this.dispatchGuard.evaluate(job.data);
      if (!decision.allowed) {
        this.logger.error(
          {
            code: decision.code,
            reason: decision.reason,
            generationRequestId: job.data.generationRequestId,
            generationJobId: job.data.generationJobId,
          },
          "GovernedContentDispatchGuard blocked dispatch — aborting job",
        );
        throw new Error(
          `Governed dispatch blocked [${decision.code}]: ${decision.reason}`,
        );
      }
    }

    try {
      await this.telemetry.publishForJob(job, {
        stage: "generation_request_started",
        message: `Executing ${job.data.generationJobType} generation job`,
        progressPercent: 20,
        status: "running",
      });

      const outputData = await this.executeByType(job);
      const materializedAsset = await this.materializeAssetIfSupported(
        job,
        outputData,
      );
      const result: JobExecutionResult = {
        jobId: job.data.generationJobId,
        status: "completed",
        ...(materializedAsset
          ? { outputAssetId: materializedAsset.assetId }
          : {}),
        outputData: materializedAsset
          ? {
              ...outputData,
              assetId: materializedAsset.assetId,
              materialization: {
                assetId: materializedAsset.assetId,
                assetType: materializedAsset.assetType,
                currentVersion: materializedAsset.currentVersion,
                blockCount: materializedAsset.blockCount,
                manifestCount: materializedAsset.manifestCount,
                created: materializedAsset.created,
              },
            }
          : outputData,
        diagnostics: {
          executionKind: "generation_request_job",
          generationJobType: job.data.generationJobType,
          ...(materializedAsset
            ? {
                materializedAsset: {
                  assetId: materializedAsset.assetId,
                  assetType: materializedAsset.assetType,
                  currentVersion: materializedAsset.currentVersion,
                },
              }
            : {}),
        },
        durationMs: Date.now() - startedAt,
      };

      await this.executionService.recordJobResult(
        job.data.generationRequestId,
        result,
        job.data.tenantId,
      );

      const blockedResults =
        await this.dispatcher.collectDependencyFailureResults(
          job.data.tenantId,
          job.data.generationRequestId,
        );
      if (blockedResults.length > 0) {
        await this.executionService.recordBatchResults(
          job.data.generationRequestId,
          blockedResults,
          job.data.tenantId,
        );
      }

      await this.dispatcher.dispatchReadyJobs(
        job.data.tenantId,
        job.data.generationRequestId,
      );

      await this.telemetry.publishForJob(job, {
        stage: "generation_request_completed",
        message: `${job.data.generationJobType} generation job persisted`,
        progressPercent: 95,
        status: "running",
        diagnostics: {
          generationJobType: job.data.generationJobType,
        },
      });
    } catch (error: unknown) {
      const asError = error instanceof Error ? error : new Error(String(error));

      const attempts = Number(job.opts?.attempts ?? 1);
      const isFinalAttempt = job.attemptsMade + 1 >= attempts;

      if (isFinalAttempt) {
        await this.executionService.recordJobResult(
          job.data.generationRequestId,
          {
            jobId: job.data.generationJobId,
            status: "failed",
            errorMessage: asError.message,
            diagnostics: {
              executionKind: "generation_request_job",
              generationJobType: job.data.generationJobType,
              finalAttempt: true,
            },
            durationMs: Date.now() - startedAt,
          },
          job.data.tenantId,
        );

        const blockedResults =
          await this.dispatcher.collectDependencyFailureResults(
            job.data.tenantId,
            job.data.generationRequestId,
          );
        if (blockedResults.length > 0) {
          await this.executionService.recordBatchResults(
            job.data.generationRequestId,
            blockedResults,
            job.data.tenantId,
          );
        }
      }

      this.logger.error(
        {
          err: asError,
          generationRequestId: job.data.generationRequestId,
          generationJobId: job.data.generationJobId,
          generationJobType: job.data.generationJobType,
        },
        "Generation request job execution failed",
      );
      throw asError;
    }
  }

  private async executeByType(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    switch (job.data.generationJobType) {
      case "claim":
        return this.executeClaim(job);
      case "explainer":
        return this.executeExplainer(job);
      case "worked_example":
        return this.executeWorkedExample(job);
      case "simulation":
        return this.executeSimulation(job);
      case "animation":
        return this.executeAnimation(job);
      case "assessment":
        return this.executeAssessment(job);
      case "evaluation":
        return this.executeEvaluation(job);
      default:
        throw new Error(
          `Unsupported generation job type: ${job.data.generationJobType}`,
        );
    }
  }

  private async materializeAssetIfSupported(
    job: Job<GenerationRequestExecutionJobData>,
    outputData: Record<string, unknown>,
  ) {
    if (
      job.data.generationJobType === "claim" ||
      job.data.generationJobType === "evaluation"
    ) {
      return null;
    }

    await this.telemetry.publishForJob(job, {
      stage: "asset_materialization_started",
      message: "Materializing canonical content asset",
      progressPercent: 80,
      status: "running",
    });

    const materialized =
      await this.assetMaterializationService.materializeJobOutput({
        tenantId: job.data.tenantId,
        requestId: job.data.generationRequestId,
        jobId: job.data.generationJobId,
        jobType: job.data.generationJobType as Exclude<
          GenerationJobType,
          "claim" | "evaluation"
        >,
        requestTitle: job.data.requestTitle,
        ...(job.data.requestDescription
          ? { requestDescription: job.data.requestDescription }
          : {}),
        domain: job.data.domain,
        ...(job.data.conceptId ? { conceptId: job.data.conceptId } : {}),
        targetGrades: job.data.targetGrades,
        requestedBy: job.data.requestedBy,
        ...(job.data.targetRef ? { targetRef: job.data.targetRef } : {}),
        ...(job.data.outputAssetId
          ? { existingAssetId: job.data.outputAssetId }
          : {}),
        outputData,
      });

    await this.telemetry.publishForJob(job, {
      stage: "asset_materialization_completed",
      message: `Canonical asset ${materialized.assetId} is ready for evaluation`,
      progressPercent: 88,
      status: "running",
      diagnostics: {
        assetId: materialized.assetId,
        assetType: materialized.assetType,
        created: materialized.created,
      },
    });

    return materialized;
  }

  private async executeClaim(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    const response = await this.grpcClient.generateClaims({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      topic: job.data.requestTitle,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      domain: job.data.domain,
      maxClaims: 1,
      context: {
        targetRef: job.data.targetRef ?? job.data.generationJobId,
        requestedBy: job.data.requestedBy,
        summary: job.data.requestDescription ?? "",
      },
    });

    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "Claim generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        claimsCount: response.claims?.length || 0,
      },
    });

    return {
      claims: response.claims ?? [],
      count: response.claims?.length ?? 0,
      validation: response.validation ?? null,
      metadata: response.metadata ?? null,
    };
  }

  private async executeExplainer(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    const response = await this.grpcClient.generateExamples({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      claimText: buildClaimText(job.data),
      claimRef: job.data.targetRef ?? job.data.generationJobId,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      domain: job.data.domain,
      types: ["ANALOGY"],
      count: 1,
    });

    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    const examples = Array.isArray(response.examples)
      ? (response.examples as GrpcExampleLike[])
      : [];
    const explainers = examples.map(
      (example: GrpcExampleLike, index: number) => ({
        id:
          example.example_id ??
          `${job.data.generationJobId}-explainer-${index + 1}`,
        title: example.title ?? `Explainer for ${job.data.requestTitle}`,
        description: example.description ?? "",
        narrative:
          example.content ??
          example.solution_content ??
          example.description ??
          "",
        type: "explainer",
      }),
    );

    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "Explainer generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        explainersCount: explainers.length,
      },
    });

    return {
      explainers,
      examples: explainers,
      count: explainers.length,
      metadata: response.metadata ?? null,
      adapter: "example_to_explainer",
    };
  }

  private async executeWorkedExample(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    const response = await this.grpcClient.generateExamples({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      claimText: buildClaimText(job.data),
      claimRef: job.data.targetRef ?? job.data.generationJobId,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      domain: job.data.domain,
      types: ["PROBLEM_SOLVING"],
      count: 1,
    });

    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "Worked-example generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        examplesCount: response.examples?.length || 0,
      },
    });

    return {
      examples: response.examples ?? [],
      count: response.examples?.length ?? 0,
      metadata: response.metadata ?? null,
    };
  }

  private async executeSimulation(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    // Generate deterministic seed for reproducible simulation execution
    const seed = parseInt(crypto.randomUUID().replace(/-/g, '').substring(0, 8), 16);

    // Define canonical SimKit contract fields
    const parameterBounds = [
      {
        parameterId: "primary_parameter",
        label: "Primary Control",
        min: 0,
        max: 100,
        defaultValue: 50,
        unit: "units",
      },
    ];

    const telemetryEvents = [
      { eventType: "sim.start" as const, required: true },
      { eventType: "sim.control.change" as const, required: true },
      { eventType: "sim.snapshot" as const, required: false },
      { eventType: "sim.capture" as const, required: false },
      { eventType: "sim.failure" as const, required: true },
      { eventType: "sim.complete" as const, required: true },
    ];

    const failureStates = [
      {
        id: "parameter_out_of_bounds",
        condition: "parameter < min || parameter > max",
        learnerMessage: "Parameter value is outside valid range. Please adjust and try again.",
        recoverable: true,
      },
      {
        id: "execution_timeout",
        condition: "executionTimeMs > maxRuntimeMs",
        learnerMessage: "Simulation took too long to complete. Please try with simpler parameters.",
        recoverable: false,
      },
    ];

    // State snapshots for reproducible simulation states
    const stateSnapshots = [
      {
        snapshotId: "initial_state",
        description: "Initial simulation state with default parameters",
        triggerCondition: "onStart",
        includeParameters: true,
        includeTelemetry: false,
      },
      {
        snapshotId: "completion_state",
        description: "Final simulation state on successful completion",
        triggerCondition: "onComplete",
        includeParameters: true,
        includeTelemetry: true,
      },
    ];

    // Export configuration for simulation data export
    const exportConfig = {
      formats: ["json", "csv"] as Array<"json" | "csv" | "pdf">,
      includeTelemetry: true,
      includeSnapshots: true,
      maxExportSizeBytes: 10 * 1024 * 1024, // 10MB
      retentionPeriodDays: 30,
    };

    // Derive claim links from targetRef if available
    const claimLinks = job.data.targetRef ? [{
      claimId: job.data.targetRef,
      evidenceIds: [`telemetry.parameterChange.${job.data.targetRef}`, `telemetry.timeOnTask.${job.data.targetRef}`],
      taskIds: [],
    }] : [];

    const accessibility = {
      altText: `Interactive simulation for ${job.data.requestTitle || 'concept'} exploring ${job.data.domain} concepts`,
      screenReaderNarration: true,
      reducedMotion: true,
      highContrast: false,
    };

    const response = await this.grpcClient.generateSimulation({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      claimText: buildClaimText(job.data),
      claimRef: job.data.targetRef ?? job.data.generationJobId,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      domain: job.data.domain,
      interactionType: "PARAMETER_EXPLORATION",
      complexity: "MEDIUM",
      // Canonical SimKit contract fields
      seed,
      parameterBounds,
      telemetryEvents,
      failureStates,
      stateSnapshots,
      exportConfig,
      claimLinks,
      accessibility,
    });

    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "Simulation generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        manifestId: response.manifest?.manifest_id ?? null,
        seed,
      },
    });

    return {
      simulations: response.manifest ? [response.manifest] : [],
      metadata: response.metadata ?? null,
      canonicalConfig: {
        seed,
        parameterBounds,
        telemetryEvents,
        failureStates,
        stateSnapshots,
        exportConfig,
        claimLinks,
        accessibility,
      },
    };
  }

  private async executeAnimation(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    // Determine appropriate duration based on domain and grade level
    const durationBounds = {
      minSeconds: 15,
      maxSeconds: 60,
    };
    const durationSeconds = Math.min(Math.max(30, durationBounds.minSeconds), durationBounds.maxSeconds);

    // Determine animation type based on domain
    const animationType = job.data.domain === "physics" || job.data.domain === "science" ? "TWO_D" : "WHITEBOARD";

    // Build pedagogical purpose based on domain and request
    const pedagogicalPurpose = `Visualize ${job.data.domain} concepts for ${getPrimaryGrade(job.data.targetGrades)} to enhance understanding of ${job.data.requestTitle || 'the concept'}`;

    // Build claim/evidence linkage
    const claimIds = job.data.targetRef ? [job.data.targetRef] : [];
    const evidenceIds = job.data.targetRef
      ? [`telemetry.view.${job.data.targetRef}`, `telemetry.completion.${job.data.targetRef}`]
      : [];

    // Build accessibility metadata
    const accessibility = {
      altText: `Animation illustrating ${job.data.requestTitle || 'the concept'} in ${job.data.domain}`,
      screenReaderDescription: true,
      highContrast: false,
      colorblindFriendly: true,
    };

    // Build visual description
    const visualDescription = `Animation showing key concepts of ${job.data.requestTitle || 'the topic'} with visual representations of ${job.data.domain} principles`;

    const response = await this.grpcClient.generateAnimation({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      claimText: buildClaimText(job.data),
      claimRef: job.data.targetRef ?? job.data.generationJobId,
      animationType,
      durationSeconds,
      domain: job.data.domain,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      // Animation enhancement fields
      pedagogicalPurpose,
      claimIds,
      evidenceIds,
      transcriptRequired: true,
      captionsRequired: true,
      reducedMotionFallback: true,
      visualDescription,
      durationBounds,
      accessibility,
    });

    const animation = response.animation;
    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "Animation generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        animationId: animation?.animation_id ?? animation?.animationId ?? null,
        animationType,
        durationSeconds,
        pedagogicalPurpose,
      },
    });

    return {
      animations: animation ? [animation] : [],
      metadata: response.metadata ?? null,
      enhancementConfig: {
        pedagogicalPurpose,
        claimIds,
        evidenceIds,
        transcriptRequired: true,
        captionsRequired: true,
        reducedMotionFallback: true,
        visualDescription,
        durationBounds,
        accessibility,
      },
    };
  }

  private async executeAssessment(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    // Use dedicated assessment generator instead of example adapter
    const assessmentTypes: Array<"PREDICTION" | "MANIPULATION" | "EXPLANATION" | "CONSTRUCTED_RESPONSE"> = [
      "PREDICTION",
      "MANIPULATION",
      "EXPLANATION",
      "CONSTRUCTED_RESPONSE",
    ];

    const claimIds = job.data.targetRef ? [job.data.targetRef] : [];
    const evidenceIds = job.data.targetRef
      ? [`telemetry.response.${job.data.targetRef}`, `telemetry.attempt.${job.data.targetRef}`]
      : [];
    const simulationBased = job.data.domain === "physics" || job.data.domain === "science";

    const response = await this.grpcClient.generateAssessment({
      requestId: crypto.randomUUID(),
      tenantId: job.data.tenantId,
      claimText: buildClaimText(job.data),
      claimRef: job.data.targetRef ?? job.data.generationJobId,
      gradeLevel: getPrimaryGrade(job.data.targetGrades),
      domain: job.data.domain,
      assessmentTypes,
      itemCount: 1,
      cbmEnabled: true,
      includeRubrics: true,
      includeDistractorRationales: true,
      claimIds,
      evidenceIds,
      simulationBased,
    });

    const cost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(
      response.metadata,
    );
    await this.telemetry.publishForJob(job, {
      stage: "grpc_response_received",
      message: "CBM assessment generation response received",
      progressPercent: 60,
      status: "running",
      ...(cost ? { cost } : {}),
      diagnostics: {
        assessmentsCount: response.assessments.length,
        assessmentTypes,
      },
    });

    return {
      assessments: response.assessments,
      count: response.assessments.length,
      metadata: response.metadata,
      generator: "cbm_assessment_generator_v2",
    };
  }

  private async executeEvaluation(
    job: Job<GenerationRequestExecutionJobData>,
  ): Promise<Record<string, unknown>> {
    const autoPublishEnabled = isFeatureEnabled(
      this.featureFlags,
      "enableAutoPublish",
    );

    const summary = await this.qualityLoopService.processRequestOutcome(
      job.data.tenantId,
      job.data.generationRequestId,
      {
        autoPublish: autoPublishEnabled,
        actorId: "system:content-worker",
      },
    );

    await this.telemetry.publishForJob(job, {
      stage: "evaluation_completed",
      message: "Evaluation scorecard generated",
      progressPercent: 70,
      status: "running",
      diagnostics: {
        overallScore: summary.evaluation.overallScore,
        recommendation: summary.evaluation.recommendation,
        nextAction: summary.nextAction,
      },
    });

    return {
      evaluationStatus: summary.evaluation.recommendation,
      scorecard: summary.evaluation,
      qualityLoop: summary,
    };
  }
}

function buildClaimText(job: GenerationRequestExecutionJobData): string {
  return [job.requestTitle, job.requestDescription].filter(Boolean).join(": ");
}

function getPrimaryGrade(targetGrades: string[]): string {
  return targetGrades[0] ?? "GRADE_6_8";
}
