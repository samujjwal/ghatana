/**
 * No Orphan Claims Invariant Tests
 * 
 * These tests verify the critical invariant: every claim must have
 * at least one supporting modality (example, simulation, or animation)
 * before being published.
 * 
 * @doc.type invariant-test
 * @doc.priority P0
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ModalityValidator, PublishingError } from '../../utils/modality-validator';
import {
  createMockPrisma,
  createExperience,
  createClaim,
  createExample,
  createSimulation,
  createAnimation,
} from '../test-utils';

describe('Invariant: No Orphan Claims', () => {
  let validator: ModalityValidator;
  let mockPrisma: ReturnType<typeof createMockPrisma>;

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    validator = new ModalityValidator(mockPrisma as any);
  });

  describe('validateClaimModality', () => {
    it('should reject a claim without any modality', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(0);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(0);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(false);
      expect(result.error).toContain('has no supporting modalities');
      expect(result.modalities).toEqual({
        examples: 0,
        simulations: 0,
        animations: 0,
      });
    });

    it('should accept a claim with at least one example', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(1);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(0);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('example');
      expect(result.modalities).toEqual({
        examples: 1,
        simulations: 0,
        animations: 0,
      });
    });

    it('should accept a claim with at least one simulation', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(0);
      mockPrisma.claimSimulation.count.mockResolvedValue(1);
      mockPrisma.claimAnimation.count.mockResolvedValue(0);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('simulation');
    });

    it('should accept a claim with at least one animation', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(0);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(1);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('animation');
    });

    it('should prefer simulation over animation when both present', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(0);
      mockPrisma.claimSimulation.count.mockResolvedValue(1);
      mockPrisma.claimAnimation.count.mockResolvedValue(1);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('simulation');
    });

    it('should prefer animation over example when both present', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(1);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(1);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('animation');
    });

    it('should prefer simulation over all others when all present', async () => {
      mockPrisma.claimExample.count.mockResolvedValue(3);
      mockPrisma.claimSimulation.count.mockResolvedValue(2);
      mockPrisma.claimAnimation.count.mockResolvedValue(1);

      const result = await validator.validateClaimModality('exp-1', 'C1');

      expect(result.valid).toBe(true);
      expect(result.preferredModality).toBe('simulation');
    });
  });

  describe('validateExperienceForPublishing', () => {
    it('should throw PublishingError when experience has no claims', async () => {
      mockPrisma.learningClaim.findMany.mockResolvedValue([]);

      await expect(
        validator.validateExperienceForPublishing('exp-1')
      ).rejects.toThrow(PublishingError);
    });

    it('should throw PublishingError when a claim has no modalities', async () => {
      mockPrisma.learningClaim.findMany.mockResolvedValue([
        { claimRef: 'C1', text: 'Test claim' },
      ]);

      mockPrisma.claimExample.count.mockResolvedValue(0);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(0);

      await expect(
        validator.validateExperienceForPublishing('exp-1')
      ).rejects.toThrow(PublishingError);
    });

    it('should throw PublishingError with details when multiple claims fail', async () => {
      mockPrisma.learningClaim.findMany.mockResolvedValue([
        { claimRef: 'C1', text: 'Claim 1' },
        { claimRef: 'C2', text: 'Claim 2' },
      ]);

      // C1 has modalities, C2 does not
      mockPrisma.claimExample.count
        .mockResolvedValueOnce(1)  // C1 has example
        .mockResolvedValueOnce(0); // C2 has no example

      mockPrisma.claimSimulation.count
        .mockResolvedValueOnce(0)
        .mockResolvedValueOnce(0);

      mockPrisma.claimAnimation.count
        .mockResolvedValueOnce(0)
        .mockResolvedValueOnce(0);

      await expect(
        validator.validateExperienceForPublishing('exp-1')
      ).rejects.toThrow(/1 claim\(s\) lack supporting modalities/);
    });

    it('should pass when all claims have at least one modality', async () => {
      mockPrisma.learningClaim.findMany.mockResolvedValue([
        { claimRef: 'C1', text: 'Claim 1' },
        { claimRef: 'C2', text: 'Claim 2' },
      ]);

      mockPrisma.claimExample.count.mockResolvedValue(1);
      mockPrisma.claimSimulation.count.mockResolvedValue(0);
      mockPrisma.claimAnimation.count.mockResolvedValue(0);

      await expect(
        validator.validateExperienceForPublishing('exp-1')
      ).resolves.not.toThrow();
    });
  });

  describe('selectPreferredModality', () => {
    it('should select simulation when simulations > 0', () => {
      const result = validator.selectPreferredModality({
        examples: 0,
        simulations: 1,
        animations: 0,
      });

      expect(result).toBe('simulation');
    });

    it('should select animation when no simulations but animations > 0', () => {
      const result = validator.selectPreferredModality({
        examples: 0,
        simulations: 0,
        animations: 1,
      });

      expect(result).toBe('animation');
    });

    it('should select example when no simulations or animations', () => {
      const result = validator.selectPreferredModality({
        examples: 1,
        simulations: 0,
        animations: 0,
      });

      expect(result).toBe('example');
    });
  });
});
