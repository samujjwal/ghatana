/**
 * Seeded Replay Harness Tests
 *
 * Tests for deterministic seeding and replay functionality.
 *
 * @doc.type test
 * @doc.purpose Test seeded replay harness for reproducibility
 * @doc.layer backend-worker
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SeededReplayHarness, type SeedConfig } from '../SeededReplayHarness';

describe('SeededReplayHarness', () => {
  let harness: SeededReplayHarness;
  let mockPrisma: any;
  let mockLogger: any;

  beforeEach(() => {
    mockPrisma = {
      generationReplayManifest: {
        create: vi.fn().mockResolvedValue({ id: 'manifest-1' }),
        findFirst: vi.fn(),
        update: vi.fn(),
        findMany: vi.fn(),
      },
    };

    mockLogger = {
      info: vi.fn(),
      warn: vi.fn(),
      error: vi.fn(),
    };

    harness = new SeededReplayHarness(mockPrisma, mockLogger, 'deterministic');
  });

  describe('generateSeed', () => {
    it('generates deterministic seed from input parameters', () => {
      const seed1 = harness.generateSeed({
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        topic: 'Photosynthesis',
        domain: 'SCIENCE',
        gradeLevel: 'grade_6_8',
      });

      const seed2 = harness.generateSeed({
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        topic: 'Photosynthesis',
        domain: 'SCIENCE',
        gradeLevel: 'grade_6_8',
      });

      expect(seed1).toBe(seed2);
      expect(seed1).toHaveLength(16);
    });

    it('generates different seeds for different parameters', () => {
      const seed1 = harness.generateSeed({
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        topic: 'Photosynthesis',
        domain: 'SCIENCE',
        gradeLevel: 'grade_6_8',
      });

      const seed2 = harness.generateSeed({
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        topic: 'Respiration',
        domain: 'SCIENCE',
        gradeLevel: 'grade_6_8',
      });

      expect(seed1).not.toBe(seed2);
    });

    it('handles missing optional parameters', () => {
      const seed = harness.generateSeed({
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
      });

      expect(seed).toHaveLength(16);
    });
  });

  describe('createReplayManifest', () => {
    it('creates manifest with deterministic seed by default', async () => {
      const manifest = await harness.createReplayManifest({
        generationJobId: 'job-1',
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        inputParams: { topic: 'Photosynthesis', domain: 'SCIENCE', gradeLevel: 'grade_6_8' },
      });

      expect(manifest.seedSource).toBe('deterministic');
      expect(manifest.seed).toHaveLength(16);
      expect(manifest.replayable).toBe(true);
      expect(mockPrisma.generationReplayManifest.create).toHaveBeenCalled();
    });

    it('uses explicit seed when provided', async () => {
      const config: SeedConfig = {
        explicitSeed: 'my-custom-seed-123',
        useDeterministic: false,
        seedSource: 'provided',
      };

      const manifest = await harness.createReplayManifest({
        generationJobId: 'job-1',
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        inputParams: {},
        seedConfig: config,
      });

      expect(manifest.seed).toBe('my-custom-seed-123');
      expect(manifest.seedSource).toBe('provided');
    });

    it('uses random seed when deterministic is disabled', async () => {
      const config: SeedConfig = {
        useDeterministic: false,
        seedSource: 'random',
      };

      const manifest1 = await harness.createReplayManifest({
        generationJobId: 'job-1',
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        inputParams: {},
        seedConfig: config,
      });

      const manifest2 = await harness.createReplayManifest({
        generationJobId: 'job-2',
        tenantId: 'tenant-1',
        experienceId: 'exp-1',
        jobType: 'claim',
        inputParams: {},
        seedConfig: config,
      });

      expect(manifest1.seedSource).toBe('random');
      expect(manifest2.seedSource).toBe('random');
      expect(manifest1.seed).not.toBe(manifest2.seed);
    });
  });

  describe('updateReplayManifest', () => {
    it('updates manifest with output data', async () => {
      await harness.updateReplayManifest({
        manifestId: 'manifest-1',
        outputData: { claims: [{ text: 'Claim 1' }] },
        success: true,
      });

      expect(mockPrisma.generationReplayManifest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'manifest-1' },
          data: expect.objectContaining({
            outputData: { claims: [{ text: 'Claim 1' }] },
            replayable: true,
          }),
        })
      );
    });

    it('marks manifest as not replayable on failure', async () => {
      await harness.updateReplayManifest({
        manifestId: 'manifest-1',
        outputData: {},
        success: false,
      });

      expect(mockPrisma.generationReplayManifest.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            replayable: false,
          }),
        })
      );
    });
  });

  describe('replayGenerationJob', () => {
    it('retrieves original manifest for replay', async () => {
      mockPrisma.generationReplayManifest.findFirst.mockResolvedValue({
        id: 'manifest-1',
        generationJobId: 'job-1',
        seed: 'deterministic-seed',
        seedSource: 'deterministic',
        jobType: 'claim',
        inputParams: { topic: 'Photosynthesis' },
        outputData: { claims: [{ text: 'Claim 1' }] },
        generatedAt: new Date('2026-04-20'),
        replayable: true,
      });

      const manifest = await harness.replayGenerationJob({
        originalGenerationJobId: 'job-1',
      });

      expect(manifest).not.toBeNull();
      expect(manifest?.seed).toBe('deterministic-seed');
      expect(manifest?.seedSource).toBe('deterministic');
    });

    it('returns null when manifest not found', async () => {
      mockPrisma.generationReplayManifest.findFirst.mockResolvedValue(null);

      const manifest = await harness.replayGenerationJob({
        originalGenerationJobId: 'nonexistent-job',
      });

      expect(manifest).toBeNull();
    });
  });

  describe('verifyReproducibility', () => {
    it('verifies reproducibility with identical outputs', async () => {
      mockPrisma.generationReplayManifest.findFirst.mockResolvedValue({
        id: 'manifest-1',
        outputData: { claims: [{ text: 'Claim 1' }, { text: 'Claim 2' }] },
      });

      const result = await harness.verifyReproducibility({
        originalGenerationJobId: 'job-1',
        replayOutputData: { claims: [{ text: 'Claim 1' }, { text: 'Claim 2' }] },
      });

      expect(result.reproducible).toBe(true);
      expect(result.similarityScore).toBe(1);
      expect(result.differences).toHaveLength(0);
    });

    it('detects differences in outputs', async () => {
      mockPrisma.generationReplayManifest.findFirst.mockResolvedValue({
        id: 'manifest-1',
        outputData: { claims: [{ text: 'Claim 1' }, { text: 'Claim 2' }] },
      });

      const result = await harness.verifyReproducibility({
        originalGenerationJobId: 'job-1',
        replayOutputData: { claims: [{ text: 'Claim 1' }, { text: 'Different Claim' }] },
      });

      expect(result.reproducible).toBe(false);
      expect(result.similarityScore).toBe(0.5);
      expect(result.differences).toContain("Field 'claims' differs");
    });
  });

  describe('getReplayStats', () => {
    it('returns replay statistics for tenant', async () => {
      mockPrisma.generationReplayManifest.findMany.mockResolvedValue([
        { seedSource: 'deterministic', replayable: true },
        { seedSource: 'deterministic', replayable: true },
        { seedSource: 'random', replayable: false },
        { seedSource: 'provided', replayable: true },
      ]);

      const stats = await harness.getReplayStats({ tenantId: 'tenant-1' });

      expect(stats.totalManifests).toBe(4);
      expect(stats.deterministicSeeds).toBe(2);
      expect(stats.randomSeeds).toBe(1);
      expect(stats.providedSeeds).toBe(1);
      expect(stats.replayableCount).toBe(3);
    });
  });
});
