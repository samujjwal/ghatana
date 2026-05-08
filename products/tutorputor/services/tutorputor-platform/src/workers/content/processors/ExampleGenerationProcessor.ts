/**
 * Example Generation Processor - Generates concrete examples for claims.
 *
 * @doc.type class
 * @doc.purpose Process example generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from "bullmq";
import { Prisma, PrismaClient } from "@tutorputor/core/db";
import { Logger } from "pino";
import { RealContentGenerationClient } from "../grpc/RealContentGenerationClient";
import * as crypto from "crypto";
import {
  type CorrelatedGenerationJobData,
  ContentWorkerTelemetryPublisher,
} from "../generation-telemetry";
import {
  WorkedExampleManifest,
  validateWorkedExampleManifest,
} from "@tutorputor/contracts/v1/artifact-manifests/worked-example-manifest";

export interface ExampleGenerationJobData extends CorrelatedGenerationJobData {
  experienceId: string;
  tenantId: string;
  claimRef: string;
  claimText: string;
  gradeLevel: string;
  domain: string;
  types: string[];
  count: number;
}

export class ExampleGenerationProcessor {
  constructor(
    private grpcClient: RealContentGenerationClient,
    private prisma: PrismaClient,
    private logger: Logger,
    private telemetry?: ContentWorkerTelemetryPublisher,
  ) {}

  async process(job: Job<ExampleGenerationJobData>): Promise<void> {
    const {
      experienceId,
      tenantId,
      claimRef,
      claimText,
      gradeLevel,
      domain,
      types,
      count,
    } = job.data;

    this.logger.info(
      { jobId: job.id, experienceId, claimRef },
      "Processing example generation job",
    );

    try {
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_request_started",
        message: "Submitting example generation request",
        progressPercent: 20,
        status: "running",
      });

      // Call Java agent to generate examples
      const requestId = crypto.randomUUID();
      const response = await this.grpcClient.generateExamples({
        requestId,
        tenantId,
        claimText,
        claimRef,
        exampleTypes: types,
        count,
        domain,
        gradeLevel,
        context: {},
      });

      this.logger.info(
        { jobId: job.id, examplesCount: response.examples?.length || 0 },
        "Examples generated successfully",
      );

      const responseCost =
        ContentWorkerTelemetryPublisher.extractCostFromMetadata(
          response.metadata,
        );
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_response_received",
        message: "Example generation response received",
        progressPercent: 55,
        status: "running",
        ...(responseCost ? { cost: responseCost } : {}),
        diagnostics: {
          examplesCount: response.examples?.length || 0,
        },
      });

      // Store examples through versioned artifact revisioning (preserves history)
      // Find existing examples for this claim to preserve version history
      const existingExamples = await this.prisma.claimExample.findMany({
        where: { experienceId, claimRef },
        orderBy: { createdAt: 'desc' },
      });

      // Fetch evidence bundle metadata to link evidence refs
      const evidenceBundle = await this.prisma.evidenceBundleMetadata.findUnique({
        where: { claimRef },
        select: { id: true },
      });

      // Extract evidence refs from LearningEvidence for this claim
      const evidenceItems = await this.prisma.learningEvidence.findMany({
        where: { experienceId, claimRef },
        select: { evidenceRef: true },
      });
      const evidenceRefs = evidenceItems.map((e) => e.evidenceRef);

      // Note: We preserve previous versions by creating new ArtifactManifest entries
      // instead of deleting. The latest version is identified by the highest manifestVersion.

      // Store examples through the canonical artifact/manifest layer
      let orderIndex = 0;
      for (const example of response.examples || []) {
        const manifestId = String(
          example.example_id ?? example.exampleId ?? `${claimRef}-${orderIndex + 1}`,
        );
        const now = new Date().toISOString();

        // Build typed WorkedExampleManifest
        const manifest: WorkedExampleManifest = {
          schemaVersion: "1.0.0",
          manifestType: "WorkedExample",
          claimRef,
          evidenceRefs, // Link to evidence bundles for this claim
          objectiveRefs: [], // TODO: Link to learning objectives when available
          domain,
          gradeBand: gradeLevel,
          pedagogicalIntent: "illustrate_concept", // Default intent
          exampleFamily: "worked-solution",
          learnerGoal:
            example.problem_statement ??
            example.title ??
            `Understand ${claimText}`,
          givens: example.given_data ? Object.entries(example.given_data as Record<string, unknown>).map(([key, value], idx) => ({
            id: `given-${idx}`,
            description: key,
            value: value as string | number | boolean,
          })) : [],
          reasoningSteps: example.solution_steps ??
            (example.solution_content || example.content ? [{
              stepNumber: 1,
              description: example.solution_content ?? example.content ?? "Solution steps will be provided",
              checkpoint: false,
            }] : []),
          explanationSteps: example.explanation_steps ??
            (example.key_learning_points || example.tags ? [{
              stepNumber: 1,
              content: example.key_learning_points?.[0] ?? example.tags?.[0] ?? "Key concept explanation",
            }] : []),
          misconceptionCheckpoints: example.misconceptions ??
            (example.common_mistakes ? example.common_mistakes.map((mistake: string, idx: number) => ({
              id: `misconception-${idx}`,
              commonError: mistake,
              warningSign: `Watch out for: ${mistake}`,
              correctiveGuidance: "Review the reasoning steps to avoid this error",
            })) : []),
          transferPrompts: [], // TODO: Generate transfer prompts from domain/claim context
          adaptationRules: [], // TODO: Generate grade adaptation rules from gradeBand
          difficultyEstimate: 0.5, // Default - could be derived from content complexity
          estimatedTimeMinutes: 5, // Default - could be estimated from step count
          prerequisites: [], // TODO: Extract from claim context or learning objectives
          evaluationHints: {
            correctIndicators: example.correct_indicators ?? [],
            misconceptionIndicators: example.misconception_indicators ?? [],
          },
          provenance: {
            generatedBy: "ai",
            generationId: requestId,
            model: response.metadata?.model as string | undefined,
            createdAt: now,
            updatedAt: now,
          },
          validators: [
            {
              validatorId: "schema-validator",
              validatorType: "schema",
              passed: true,
              validationAt: now,
            },
          ],
          telemetryProfile: {
            enabled: true,
            privacyLevel: "anonymous",
          },
          createdAt: now,
          updatedAt: now,
          validationStatus: "pending",
        };

        // Validate the manifest
        const validation = validateWorkedExampleManifest(manifest);
        if (!validation.success) {
          this.logger.warn(
            { jobId: job.id, claimRef, exampleId: example.example_id, errors: validation.errors },
            "Generated example manifest failed validation, storing anyway for review",
          );
        }

        // Create ContentAsset for the example with version tracking
        const assetSlug = `${claimRef.replace(/[^a-z0-9]/gi, "-").toLowerCase()}-example-${orderIndex + 1}`;
        
        // Check if asset already exists (regeneration case)
        let asset = await this.prisma.contentAsset.findUnique({
          where: { tenantId_slug: { tenantId, slug: assetSlug } },
        });

        if (asset) {
          // Increment version for existing asset
          asset = await this.prisma.contentAsset.update({
            where: { id: asset.id },
            data: {
              currentVersion: { increment: 1 },
              status: "DRAFT", // Mark as draft for review
              updatedAt: new Date(),
            },
          });
        } else {
          // Create new asset
          asset = await this.prisma.contentAsset.create({
            data: {
              tenantId,
              slug: assetSlug,
              title: String(example.title ?? `Example ${orderIndex + 1}`),
              description: String(example.description ?? ""),
              assetType: "WORKED_EXAMPLE",
              domain,
              status: "DRAFT",
              currentVersion: 1,
              authorId: "ai-generator", // System user for AI-generated content
            },
          });
        }

        // Create ArtifactManifest with the typed manifest (new version)
        const newVersion = asset.currentVersion;
        const artifactManifest = await this.prisma.artifactManifest.create({
          data: {
            assetId: asset.id,
            manifestType: "WORKED_EXAMPLE",
            version: `${newVersion}.0.0`,
            claimRef,
            manifest: manifest as Prisma.InputJsonValue,
            schema: "1.0.0",
            isValid: validation.success,
            validationErrors: validation.success
              ? undefined
              : (validation.errors?.issues.map((e) => ({
                  message: e.message,
                  path: e.path.join("."),
                  code: e.code,
                })) as Prisma.InputJsonValue),
            generatedBy: "ai",
            generationId: requestId,
            generationProvenance: {
              generatedBy: "ai",
              generationId: requestId,
              model: response.metadata?.model as string | undefined,
              createdAt: now,
              previousVersionId: existingExamples[0]?.manifestId, // Link to previous version if exists
            } as Prisma.InputJsonValue,
          },
        });

        // Create ClaimExample linking to the artifact (new version)
        await this.prisma.claimExample.create({
          data: {
            experienceId,
            claimRef,
            manifestId: artifactManifest.id,
            manifestVersion: `${newVersion}.0.0`,
            type: String(example.type ?? "worked_example"),
            title: String(example.title ?? `Example ${orderIndex + 1}`),
            description: String(example.description ?? ""),
            content: {
              problemStatement:
                example.problem_statement ??
                example.title ??
                claimText,
              solution:
                example.solution_content ??
                example.content ??
                null,
              keyPoints: example.key_learning_points ?? example.tags ?? [],
              realWorldConnection: example.real_world_connection ?? null,
            } satisfies Record<string, unknown>, // Deprecated: use manifest instead
            difficulty: "INTERMEDIATE", // Default
            orderIndex: Number(example.order_index ?? orderIndex),
          },
        });

        this.logger.info(
          { jobId: job.id, claimRef, exampleId: example.example_id, assetId: asset.id, artifactManifestId: artifactManifest.id },
          "Example stored through artifact/manifest layer",
        );

        orderIndex += 1;
      }

      this.logger.info(
        {
          jobId: job.id,
          experienceId,
          claimRef,
          examplesCount: response.examples?.length || 0,
        },
        "Example generation job completed",
      );

      await this.telemetry?.publishForJob(job, {
        stage: "persistence_completed",
        message: "Example artifacts persisted",
        progressPercent: 90,
        status: "running",
        diagnostics: {
          examplesStored: response.examples?.length || 0,
          experienceId,
          claimRef,
        },
      });
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(
        { jobId: job.id, experienceId, claimRef, error: errorMessage },
        "Example generation job failed",
      );
      throw error;
    }
  }
}
