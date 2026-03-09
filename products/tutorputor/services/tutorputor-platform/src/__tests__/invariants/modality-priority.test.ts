/**
 * Modality Priority Invariant Tests
 * 
 * These tests verify the priority order enforcement:
 * Simulation (highest) > Animation > Example (lowest)
 * 
 * @doc.type invariant-test
 * @doc.priority P0
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ModalitySelector } from '../../utils/modality-selector';
import {
  createMockPrisma,
  createExample,
  createSimulation,
  createAnimation,
  createSimulationManifest,
} from '../test-utils';

describe('Invariant: Modality Priority Order', () => {
  let selector: ModalitySelector;
  let mockPrisma: ReturnType<typeof createMockPrisma>;

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    selector = new ModalitySelector(mockPrisma as any);
  });

  describe('selectBestModality', () => {
    it('should select simulation when all modalities available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([createSimulation({
        simulationManifest: createSimulationManifest(),
      })]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const result = selector.selectBestModality(available);

      expect(result).toBe('simulation');
    });

    it('should select animation when simulation not available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const result = selector.selectBestModality(available);

      expect(result).toBe('animation');
    });

    it('should select example when only example available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const result = selector.selectBestModality(available);

      expect(result).toBe('example');
    });

    it('should return null when no modalities available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const result = selector.selectBestModality(available);

      expect(result).toBeNull();
    });

    it('should not count simulations without valid manifests', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([createSimulation({
        simulationManifest: null,  // Invalid - no manifest
      })]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const result = selector.selectBestModality(available);

      expect(available.hasSimulation).toBe(false);
      expect(result).toBe('animation');
    });
  });

  describe('getPriorityOrder', () => {
    it('should return correct priority order', () => {
      const order = selector.getPriorityOrder();

      expect(order).toEqual(['simulation', 'animation', 'example']);
    });
  });

  describe('getModalityPriority', () => {
    it('should return 0 for simulation (highest)', () => {
      expect(selector.getModalityPriority('simulation')).toBe(0);
    });

    it('should return 1 for animation (medium)', () => {
      expect(selector.getModalityPriority('animation')).toBe(1);
    });

    it('should return 2 for example (lowest)', () => {
      expect(selector.getModalityPriority('example')).toBe(2);
    });
  });

  describe('compareModalityPriority', () => {
    it('should return negative when first has higher priority', () => {
      expect(selector.compareModalityPriority('simulation', 'animation')).toBeLessThan(0);
      expect(selector.compareModalityPriority('simulation', 'example')).toBeLessThan(0);
      expect(selector.compareModalityPriority('animation', 'example')).toBeLessThan(0);
    });

    it('should return positive when second has higher priority', () => {
      expect(selector.compareModalityPriority('animation', 'simulation')).toBeGreaterThan(0);
      expect(selector.compareModalityPriority('example', 'simulation')).toBeGreaterThan(0);
      expect(selector.compareModalityPriority('example', 'animation')).toBeGreaterThan(0);
    });

    it('should return 0 when priorities are equal', () => {
      expect(selector.compareModalityPriority('simulation', 'simulation')).toBe(0);
      expect(selector.compareModalityPriority('animation', 'animation')).toBe(0);
      expect(selector.compareModalityPriority('example', 'example')).toBe(0);
    });
  });

  describe('getFallbackModality', () => {
    it('should return animation when simulation preferred but not available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const fallback = selector.getFallbackModality('simulation', available);

      expect(fallback).toBe('animation');
    });

    it('should return example when animation preferred but not available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const fallback = selector.getFallbackModality('animation', available);

      expect(fallback).toBe('example');
    });

    it('should return null when no fallback available', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([]);

      const available = await selector.getAvailableModalities('exp-1', 'C1');
      const fallback = selector.getFallbackModality('example', available);

      expect(fallback).toBeNull();
    });
  });

  describe('selectModalityForClaim', () => {
    it('should return complete selection result', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([createSimulation({
        simulationManifest: createSimulationManifest(),
      })]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const result = await selector.selectModalityForClaim('exp-1', 'C1');

      expect(result.selectedModality).toBe('simulation');
      expect(result.availableModalities).toContain('simulation');
      expect(result.availableModalities).toContain('animation');
      expect(result.availableModalities).toContain('example');
      expect(result.details.simulation).toBeDefined();
      expect(result.details.animation).toBeDefined();
      expect(result.details.example).toBeDefined();
    });

    it('should indicate fallback was used when not first priority', async () => {
      mockPrisma.claimExample.findMany.mockResolvedValue([createExample()]);
      mockPrisma.claimSimulation.findMany.mockResolvedValue([]);
      mockPrisma.claimAnimation.findMany.mockResolvedValue([createAnimation()]);

      const result = await selector.selectModalityForClaim('exp-1', 'C1');

      expect(result.selectedModality).toBe('animation');
      expect(result.fallbackUsed).toBe(true);
    });
  });
});
