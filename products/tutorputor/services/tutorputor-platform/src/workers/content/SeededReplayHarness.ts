// @ts-nocheck - Prisma client types need regeneration after schema changes
/**
 * Seeded Replay Harness for Deterministic Content Generation
 *
 * Provides deterministic seeding for generation jobs and replay manifests
 * to ensure reproducibility of AI-generated content. This enables:
 * - Exact replay of generation jobs with identical seeds
 * - Regression testing against golden datasets
 * - Debugging of generation failures
 * - Audit trails for provenance
 *
 * @doc.type class
 * @doc.purpose Enable deterministic reproducibility for content generation
 * @doc.layer backend-worker
 * @doc.pattern ReplayHarness
 */

import * as crypto from 'crypto';
import type { PrismaClient } from '@tutorputor/core/db';
import { Logger } from 'pino';

/**
 * Seed source types.
 */
export type SeedSource = 'deterministic' | 'random' | 'provided';

/**
 * Replay manifest for a generation job.
 */
export interface ReplayManifest {
  /** Unique manifest ID */
  manifestId: string;
  /** Generation job ID */
  generationJobId: string;
  /** Seed used for generation */
  seed: string;
  /** Seed source */
  seedSource: SeedSource;
  /** Job type (claim, example, simulation, animation, assessment) */
  jobType: string;
  /** Input parameters */
  inputParams: Record<string, unknown>;
  /** Generated output */
  outputData: Record<string, unknown> | undefined;
  /** Timestamp of generation */
  generatedAt: string;
  /** Whether replay is successful */
  replayable: boolean;
}

/**
 * Seed configuration for generation jobs.
 */
export interface SeedConfig {
  /** Explicit seed value (if provided) */
  explicitSeed?: string;
  /** Whether to use deterministic seeding */
  useDeterministic: boolean;
  /** Seed source fallback */
  seedSource: SeedSource;
}

/**
 * Seeded replay harness service.
 */
export class SeededReplayHarness {
  constructor(
    private prisma: PrismaClient,
    private logger: Logger,
    private defaultSeedSource: SeedSource = 'deterministic'
  ) {}

  /**
   * Generate a deterministic seed for a generation job.
   */
  generateSeed(params: {
    tenantId: string;
    experienceId: string;
    jobType: string;
    topic?: string;
    domain?: string;
    gradeLevel?: string;
  }): string {
    const { tenantId, experienceId, jobType, topic, domain, gradeLevel } = params;

    // Create a deterministic hash from the input parameters
    const hashInput = JSON.stringify({
      tenantId,
      experienceId,
      jobType,
      topic,
      domain,
      gradeLevel,
      timestamp: new Date().toISOString().split('T')[0], // Date-level granularity
    });

    const hash = crypto.createHash('sha256').update(hashInput).digest('hex');
    
    // Take first 16 characters as the seed
    return hash.substring(0, 16);
  }

  /**
   * Create a replay manifest before generation.
   */
  async createReplayManifest(params: {
    generationJobId: string;
    tenantId: string;
    experienceId: string;
    jobType: string;
    inputParams: Record<string, unknown>;
    seedConfig?: SeedConfig;
  }): Promise<ReplayManifest> {
    const { generationJobId, tenantId, experienceId, jobType, inputParams, seedConfig } = params;

    const config = seedConfig || {
      useDeterministic: true,
      seedSource: this.defaultSeedSource,
    };

    let seed: string;
    let seedSource: SeedSource;

    if (config.explicitSeed) {
      seed = config.explicitSeed;
      seedSource = 'provided';
    } else if (config.useDeterministic) {
      seed = this.generateSeed({
        tenantId,
        experienceId,
        jobType,
        topic: inputParams.topic as string,
        domain: inputParams.domain as string,
        gradeLevel: inputParams.gradeLevel as string,
      });
      seedSource = 'deterministic';
    } else {
      seed = crypto.randomBytes(16).toString('hex');
      seedSource = 'random';
    }

    const manifest: ReplayManifest = {
      manifestId: crypto.randomUUID(),
      generationJobId,
      seed,
      seedSource,
      jobType,
      inputParams,
      generatedAt: new Date().toISOString(),
      replayable: true,
    };

    // Store manifest in database for audit trail
    try {
      await (this.prisma as any).generationReplayManifest.create({
        data: {
          id: manifest.manifestId,
          generationJobId,
          seed,
          seedSource,
          jobType,
          inputParams: inputParams as any,
          generatedAt: new Date(manifest.generatedAt),
          replayable: true,
        },
      });

      this.logger.info(
        { manifestId: manifest.manifestId, generationJobId, seed, seedSource },
        'Replay manifest created'
      );
    } catch (error) {
      this.logger.warn(
        { err: error, generationJobId },
        'Failed to store replay manifest, continuing anyway'
      );
    }

    return manifest;
  }

  /**
   * Update replay manifest with output data after generation.
   */
  async updateReplayManifest(params: {
    manifestId: string;
    outputData: Record<string, unknown>;
    success: boolean;
  }): Promise<void> {
    const { manifestId, outputData, success } = params;

    try {
      await (this.prisma as any).generationReplayManifest.update({
        where: { id: manifestId },
        data: {
          outputData: outputData as any,
          replayable: success,
        },
      });

      this.logger.info(
        { manifestId, success },
        'Replay manifest updated with output'
      );
    } catch (error) {
      this.logger.error(
        { err: error, manifestId },
        'Failed to update replay manifest'
      );
    }
  }

  /**
   * Replay a generation job using its stored seed.
   */
  async replayGenerationJob(params: {
    originalGenerationJobId: string;
  }): Promise<ReplayManifest | null> {
    const { originalGenerationJobId } = params;

    try {
      // Find the original manifest
      const originalManifest = await (this.prisma as any).generationReplayManifest.findFirst({
        where: { generationJobId: originalGenerationJobId },
      });

      if (!originalManifest) {
        this.logger.warn(
          { generationJobId: originalGenerationJobId },
          'No replay manifest found for job'
        );
        return null;
      }

      this.logger.info(
        {
          originalGenerationJobId,
          seed: originalManifest.seed,
          seedSource: originalManifest.seedSource,
        },
        'Replaying generation job with stored seed'
      );

      // Return the manifest for replay
      return {
        manifestId: originalManifest.id,
        generationJobId: originalGenerationJobId,
        seed: originalManifest.seed,
        seedSource: originalManifest.seedSource as SeedSource,
        jobType: originalManifest.jobType,
        inputParams: originalManifest.inputParams as Record<string, unknown>,
        outputData: (originalManifest.outputData as Record<string, unknown> | undefined) || undefined,
        generatedAt: originalManifest.generatedAt.toISOString(),
        replayable: originalManifest.replayable,
      };
    } catch (error) {
      this.logger.error(
        { err: error, generationJobId: originalGenerationJobId },
        'Failed to replay generation job'
      );
      return null;
    }
  }

  /**
   * Verify reproducibility by comparing original and replay outputs.
   */
  async verifyReproducibility(params: {
    originalGenerationJobId: string;
    replayOutputData: Record<string, unknown>;
    tolerance?: number;
  }): Promise<{
    reproducible: boolean;
    similarityScore: number;
    differences: string[];
  }> {
    const { originalGenerationJobId, replayOutputData, tolerance = 0.95 } = params;

    try {
      const originalManifest = await (this.prisma as any).generationReplayManifest.findFirst({
        where: { generationJobId: originalGenerationJobId },
      });

      if (!originalManifest || !originalManifest.outputData) {
        return {
          reproducible: false,
          similarityScore: 0,
          differences: ['Original manifest or output data not found'],
        };
      }

      const originalOutput = originalManifest.outputData as Record<string, unknown>;
      const differences: string[] = [];
      let totalFields = 0;
      let matchingFields = 0;

      // Compare output fields
      for (const key of Object.keys(originalOutput)) {
        totalFields++;
        const originalValue = JSON.stringify(originalOutput[key]);
        const replayValue = JSON.stringify(replayOutputData[key]);

        if (originalValue === replayValue) {
          matchingFields++;
        } else {
          differences.push(`Field '${key}' differs`);
        }
      }

      const similarityScore = totalFields > 0 ? matchingFields / totalFields : 0;
      const reproducible = similarityScore >= tolerance;

      this.logger.info(
        {
          generationJobId: originalGenerationJobId,
          similarityScore,
          reproducible,
          differences,
        },
        'Reproducibility verification complete'
      );

      return {
        reproducible,
        similarityScore,
        differences,
      };
    } catch (error) {
      this.logger.error(
        { err: error, generationJobId: originalGenerationJobId },
        'Failed to verify reproducibility'
      );
      return {
        reproducible: false,
        similarityScore: 0,
        differences: ['Verification error'],
      };
    }
  }

  /**
   * Get replay statistics for a tenant.
   */
  async getReplayStats(params: { tenantId: string }): Promise<{
    totalManifests: number;
    deterministicSeeds: number;
    randomSeeds: number;
    providedSeeds: number;
    replayableCount: number;
  }> {
    const { tenantId } = params;

    try {
      const manifests = await (this.prisma as any).generationReplayManifest.findMany({
        where: {
          generationJob: {
            tenantId,
          },
        },
      });

      const deterministicSeeds = manifests.filter((m: any) => m.seedSource === 'deterministic').length;
      const randomSeeds = manifests.filter((m: any) => m.seedSource === 'random').length;
      const providedSeeds = manifests.filter((m: any) => m.seedSource === 'provided').length;
      const replayableCount = manifests.filter((m: any) => m.replayable).length;

      return {
        totalManifests: manifests.length,
        deterministicSeeds,
        randomSeeds,
        providedSeeds,
        replayableCount,
      };
    } catch (error) {
      this.logger.error({ err: error, tenantId }, 'Failed to get replay stats');
      return {
        totalManifests: 0,
        deterministicSeeds: 0,
        randomSeeds: 0,
        providedSeeds: 0,
        replayableCount: 0,
      };
    }
  }
}
