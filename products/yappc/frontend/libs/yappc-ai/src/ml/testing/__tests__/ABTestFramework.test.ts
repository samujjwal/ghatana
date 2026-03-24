/**
 * ABTestFramework Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  ABTestFramework,
  type Variant,
  type ExperimentMetric,
} from '../ABTestFramework';

describe.skip('ABTestFramework', () => {
  let framework: ABTestFramework;
  let variants: Variant[];
  let metrics: ExperimentMetric[];

  beforeEach(() => {
    framework = new ABTestFramework();

    variants = [
      {
        id: 'control',
        name: 'Control',
        weight: 0.5,
        config: { color: 'blue' },
      },
      {
        id: 'variant-a',
        name: 'Variant A',
        weight: 0.5,
        config: { color: 'green' },
      },
    ];

    metrics = [
      {
        id: 'conversion',
        name: 'Conversion Rate',
        type: 'conversion',
        goal: 'maximize',
        isPrimary: true,
      },
    ];
  });

  describe('Experiment Creation', () => {
    it('should create experiment', async () => {
      const experiment = await framework.createExperiment({
        id: 'test-1',
        name: 'Button Color Test',
        variants,
        metrics,
      });

      expect(experiment.id).toBe('test-1');
      expect(experiment.status).toBe('draft');
      expect(experiment.variants).toHaveLength(2);
    });

    it('should validate variant weights sum to 1', async () => {
      const invalidVariants = [
        { id: 'control', name: 'Control', weight: 0.6, config: {} },
        { id: 'variant-a', name: 'Variant A', weight: 0.6, config: {} },
      ];

      await expect(
        framework.createExperiment({
          id: 'test-1',
          name: 'Invalid Test',
          variants: invalidVariants,
          metrics,
        })
      ).rejects.toThrow();
    });

    it('should require at least 2 variants', async () => {
      const singleVariant = [variants[0]];

      await expect(
        framework.createExperiment({
          id: 'test-1',
          name: 'Invalid Test',
          variants: singleVariant,
          metrics,
        })
      ).rejects.toThrow(/at least 2 variants/i);
    });

    it('should require unique variant IDs', async () => {
      const duplicateVariants = [
        { id: 'control', name: 'Control 1', weight: 0.5, config: {} },
        { id: 'control', name: 'Control 2', weight: 0.5, config: {} },
      ];

      await expect(
        framework.createExperiment({
          id: 'test-1',
          name: 'Invalid Test',
          variants: duplicateVariants,
          metrics,
        })
      ).rejects.toThrow(/unique/i);
    });
  });

  describe('Experiment Lifecycle', () => {
    let experimentId: string;

    beforeEach(async () => {
      const experiment = await framework.createExperiment({
        id: 'test-1',
        name: 'Test Experiment',
        variants,
        metrics,
      });
      experimentId = experiment.id;
    });

    it('should start experiment', async () => {
      await framework.startExperiment(experimentId);

      const assignment = await framework.assignVariant(experimentId, 'user1');
      expect(assignment).toBeDefined();
    });

    it('should stop experiment', async () => {
      await framework.startExperiment(experimentId);
      await framework.stopExperiment(experimentId);

      await expect(
        framework.assignVariant(experimentId, 'user1')
      ).rejects.toThrow(/not running/i);
    });

    it('should not assign before starting', async () => {
      await expect(
        framework.assignVariant(experimentId, 'user1')
      ).rejects.toThrow(/not running/i);
    });
  });

  describe('Variant Assignment', () => {
    let experimentId: string;

    beforeEach(async () => {
      await framework.createExperiment({
        id: 'test-1',
        name: 'Test Experiment',
        variants,
        metrics,
      });
      experimentId = 'test-1';
      await framework.startExperiment(experimentId);
    });

    it('should assign variant to user', async () => {
      const assignment = await framework.assignVariant(experimentId, 'user1');

      expect(assignment.experimentId).toBe(experimentId);
      expect(assignment.userId).toBe('user1');
      expect(['control', 'variant-a']).toContain(assignment.variantId);
    });

    it('should maintain sticky assignments', async () => {
      const assignment1 = await framework.assignVariant(experimentId, 'user1');
      const assignment2 = await framework.assignVariant(experimentId, 'user1');

      expect(assignment1.variantId).toBe(assignment2.variantId);
    });

    it('should respect variant weights', async () => {
      const assignments = new Map<string, number>();

      // Assign many users
      for (let i = 0; i < 1000; i++) {
        const assignment = await framework.assignVariant(
          experimentId,
          `user${i}`
        );
        assignments.set(
          assignment.variantId,
          (assignments.get(assignment.variantId) || 0) + 1
        );
      }

      const controlCount = assignments.get('control') || 0;
      const variantCount = assignments.get('variant-a') || 0;

      // Should be roughly 50/50 (within 10% margin)
      const ratio = controlCount / (controlCount + variantCount);
      expect(ratio).toBeGreaterThan(0.4);
      expect(ratio).toBeLessThan(0.6);
    });

    it('should get assigned variant', async () => {
      await framework.assignVariant(experimentId, 'user1');
      const variant = await framework.getVariant(experimentId, 'user1');

      expect(variant).toBeDefined();
      expect(['control', 'variant-a']).toContain(variant?.id);
    });

    it('should return null for unassigned user', async () => {
      const variant = await framework.getVariant(experimentId, 'unassigned');
      expect(variant).toBeNull();
    });
  });

  describe('Target Audience', () => {
    it('should target specific users', async () => {
      await framework.createExperiment({
        id: 'test-targeted',
        name: 'Targeted Test',
        variants,
        metrics,
        targetAudience: {
          userIds: ['user1', 'user2'],
        },
      });

      await framework.startExperiment('test-targeted');

      // user1 should be assigned
      const assignment1 = await framework.assignVariant(
        'test-targeted',
        'user1'
      );
      expect(assignment1).toBeDefined();

      // user3 should not be in audience
      await expect(
        framework.assignVariant('test-targeted', 'user3')
      ).rejects.toThrow(/not in target audience/i);
    });

    it('should support percentage rollout', async () => {
      await framework.createExperiment({
        id: 'test-rollout',
        name: 'Rollout Test',
        variants,
        metrics,
        targetAudience: {
          percentage: 50, // 50% of users
        },
      });

      await framework.startExperiment('test-rollout');

      let assignedCount = 0;
      let _notInAudienceCount = 0;

      for (let i = 0; i < 100; i++) {
        try {
          await framework.assignVariant('test-rollout', `user${i}`);
          assignedCount++;
        } catch {
          _notInAudienceCount++;
        }
      }

      // Should be roughly 50% (within 20% margin due to sampling)
      expect(assignedCount).toBeGreaterThan(30);
      expect(assignedCount).toBeLessThan(70);
    });
  });

  describe('Metric Tracking', () => {
    let experimentId: string;

    beforeEach(async () => {
      await framework.createExperiment({
        id: 'test-metrics',
        name: 'Metrics Test',
        variants,
        metrics: [
          {
            id: 'conversion',
            name: 'Conversion',
            type: 'conversion',
            goal: 'maximize',
            isPrimary: true,
          },
          {
            id: 'revenue',
            name: 'Revenue',
            type: 'revenue',
            goal: 'maximize',
          },
        ],
      });
      experimentId = 'test-metrics';
      await framework.startExperiment(experimentId);
    });

    it('should track metric events', async () => {
      await framework.assignVariant(experimentId, 'user1');
      await framework.trackMetric(experimentId, 'user1', 'conversion', 1);

      // Should not throw
      expect(true).toBe(true);
    });

    it('should track conversions', async () => {
      await framework.assignVariant(experimentId, 'user1');
      await framework.trackConversion(experimentId, 'user1', 'conversion');

      // Should not throw
      expect(true).toBe(true);
    });

    it('should require user assignment before tracking', async () => {
      await expect(
        framework.trackMetric(experimentId, 'unassigned', 'conversion', 1)
      ).rejects.toThrow(/not assigned/i);
    });

    it('should track multiple metrics', async () => {
      await framework.assignVariant(experimentId, 'user1');
      await framework.trackMetric(experimentId, 'user1', 'conversion', 1);
      await framework.trackMetric(experimentId, 'user1', 'revenue', 99.99);

      // Both metrics tracked
      expect(true).toBe(true);
    });
  });

  describe('Results and Analysis', () => {
    let experimentId: string;

    beforeEach(async () => {
      await framework.createExperiment({
        id: 'test-results',
        name: 'Results Test',
        variants,
        metrics,
      });
      experimentId = 'test-results';
      await framework.startExperiment(experimentId);

      // Assign users and track conversions
      // Control: 50% conversion (5/10)
      for (let i = 0; i < 10; i++) {
        const assignment = await framework.assignVariant(
          experimentId,
          `control-user${i}`
        );
        if (assignment.variantId === 'control' && i < 5) {
          await framework.trackConversion(
            experimentId,
            `control-user${i}`,
            'conversion'
          );
        }
      }

      // Variant A: 70% conversion (7/10)
      for (let i = 0; i < 10; i++) {
        const assignment = await framework.assignVariant(
          experimentId,
          `variant-user${i}`
        );
        if (assignment.variantId === 'variant-a' && i < 7) {
          await framework.trackConversion(
            experimentId,
            `variant-user${i}`,
            'conversion'
          );
        }
      }
    });

    it('should get experiment results', async () => {
      const results = await framework.getResults(experimentId);

      expect(results.experimentId).toBe(experimentId);
      expect(results.variants).toHaveLength(2);
      expect(results.status).toBe('running');
    });

    it('should calculate variant metrics', async () => {
      const results = await framework.getResults(experimentId);

      results.variants.forEach((variant) => {
        expect(variant.sampleSize).toBeGreaterThan(0);
        expect(variant.metrics).toHaveLength(1);
        expect(variant.metrics[0].metricId).toBe('conversion');
      });
    });

    it('should provide confidence intervals', async () => {
      const results = await framework.getResults(experimentId);

      results.variants.forEach((variant) => {
        const metric = variant.metrics[0];
        expect(metric.confidenceInterval).toHaveLength(2);
        expect(metric.confidenceInterval[0]).toBeLessThanOrEqual(metric.value);
        expect(metric.confidenceInterval[1]).toBeGreaterThanOrEqual(
          metric.value
        );
      });
    });

    it('should calculate standard deviation', async () => {
      const results = await framework.getResults(experimentId);

      results.variants.forEach((variant) => {
        const metric = variant.metrics[0];
        expect(metric.standardDeviation).toBeGreaterThanOrEqual(0);
      });
    });

    it('should provide recommendations', async () => {
      const results = await framework.getResults(experimentId);

      expect(results.recommendations).toBeDefined();
      expect(results.recommendations.length).toBeGreaterThan(0);
    });

    it('should calculate confidence', async () => {
      const results = await framework.getResults(experimentId);

      expect(results.confidence).toBeGreaterThanOrEqual(0);
      expect(results.confidence).toBeLessThanOrEqual(1);
    });
  });

  describe('Statistical Testing', () => {
    let experimentId: string;

    beforeEach(async () => {
      await framework.createExperiment({
        id: 'test-stats',
        name: 'Stats Test',
        variants,
        metrics,
      });
      experimentId = 'test-stats';
      await framework.startExperiment(experimentId);

      // Generate sample data
      for (let i = 0; i < 100; i++) {
        const assignment = await framework.assignVariant(
          experimentId,
          `user${i}`
        );

        // Control: 50% conversion
        if (assignment.variantId === 'control' && Math.random() < 0.5) {
          await framework.trackConversion(
            experimentId,
            `user${i}`,
            'conversion'
          );
        }

        // Variant A: 60% conversion
        if (assignment.variantId === 'variant-a' && Math.random() < 0.6) {
          await framework.trackConversion(
            experimentId,
            `user${i}`,
            'conversion'
          );
        }
      }
    });

    it('should run statistical test', async () => {
      const test = await framework.runStatisticalTest(
        experimentId,
        'control',
        'variant-a',
        'conversion'
      );

      expect(test.testType).toBe('ttest');
      expect(test.pValue).toBeGreaterThanOrEqual(0);
      expect(test.pValue).toBeLessThanOrEqual(1);
      expect(test.confidence).toBeGreaterThanOrEqual(0);
      expect(test.confidence).toBeLessThanOrEqual(1);
    });

    it('should determine significance', async () => {
      const test = await framework.runStatisticalTest(
        experimentId,
        'control',
        'variant-a',
        'conversion'
      );

      expect(typeof test.isSignificant).toBe('boolean');
    });

    it('should calculate effect size', async () => {
      const test = await framework.runStatisticalTest(
        experimentId,
        'control',
        'variant-a',
        'conversion'
      );

      expect(typeof test.effectSize).toBe('number');
    });
  });

  describe('Winner Determination', () => {
    it('should determine winner with sufficient data', async () => {
      await framework.createExperiment({
        id: 'test-winner',
        name: 'Winner Test',
        variants,
        metrics,
      });
      await framework.startExperiment('test-winner');

      // Generate clear winner: variant-a performs much better
      for (let i = 0; i < 200; i++) {
        const assignment = await framework.assignVariant(
          'test-winner',
          `user${i}`
        );

        if (assignment.variantId === 'control' && i < 50) {
          await framework.trackConversion(
            'test-winner',
            `user${i}`,
            'conversion'
          );
        }

        if (assignment.variantId === 'variant-a' && i < 150) {
          await framework.trackConversion(
            'test-winner',
            `user${i}`,
            'conversion'
          );
        }
      }

      const results = await framework.getResults('test-winner');

      expect(results.winner).toBeDefined();
      expect(results.confidence).toBeGreaterThan(0.8);
    });

    it('should not determine winner with insufficient data', async () => {
      await framework.createExperiment({
        id: 'test-no-winner',
        name: 'No Winner Test',
        variants,
        metrics,
      });
      await framework.startExperiment('test-no-winner');

      // Very little data
      await framework.assignVariant('test-no-winner', 'user1');
      await framework.trackConversion('test-no-winner', 'user1', 'conversion');

      const results = await framework.getResults('test-no-winner');

      expect(results.winner).toBeUndefined();
      expect(results.confidence).toBeLessThan(0.8);
    });
  });

  describe('Multi-Variant Tests', () => {
    it('should support more than 2 variants', async () => {
      const multiVariants: Variant[] = [
        {
          id: 'control',
          name: 'Control',
          weight: 0.33,
          config: { color: 'blue' },
        },
        {
          id: 'variant-a',
          name: 'Variant A',
          weight: 0.33,
          config: { color: 'green' },
        },
        {
          id: 'variant-b',
          name: 'Variant B',
          weight: 0.34,
          config: { color: 'red' },
        },
      ];

      const experiment = await framework.createExperiment({
        id: 'test-multi',
        name: 'Multi-Variant Test',
        variants: multiVariants,
        metrics,
      });

      expect(experiment.variants).toHaveLength(3);
    });

    it('should distribute users across all variants', async () => {
      const multiVariants: Variant[] = [
        { id: 'control', name: 'Control', weight: 0.33, config: {} },
        { id: 'variant-a', name: 'Variant A', weight: 0.33, config: {} },
        { id: 'variant-b', name: 'Variant B', weight: 0.34, config: {} },
      ];

      await framework.createExperiment({
        id: 'test-multi',
        name: 'Multi-Variant Test',
        variants: multiVariants,
        metrics,
      });
      await framework.startExperiment('test-multi');

      const assignments = new Map<string, number>();

      for (let i = 0; i < 300; i++) {
        const assignment = await framework.assignVariant(
          'test-multi',
          `user${i}`
        );
        assignments.set(
          assignment.variantId,
          (assignments.get(assignment.variantId) || 0) + 1
        );
      }

      // All variants should have assignments
      expect(assignments.size).toBe(3);

      // Each should have roughly 1/3 (within 20% margin)
      assignments.forEach((count) => {
        expect(count).toBeGreaterThan(60);
        expect(count).toBeLessThan(140);
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle non-existent experiment', async () => {
      await expect(
        framework.assignVariant('nonexistent', 'user1')
      ).rejects.toThrow(/not found/i);
    });

    it('should handle empty metric events', async () => {
      await framework.createExperiment({
        id: 'test-empty',
        name: 'Empty Test',
        variants,
        metrics,
      });
      await framework.startExperiment('test-empty');

      const results = await framework.getResults('test-empty');

      expect(results.variants).toHaveLength(2);
      results.variants.forEach((variant) => {
        expect(variant.sampleSize).toBe(0);
      });
    });

    it('should handle experiments without primary metric', async () => {
      await framework.createExperiment({
        id: 'test-no-primary',
        name: 'No Primary Metric Test',
        variants,
        metrics: [
          {
            id: 'metric1',
            name: 'Metric 1',
            type: 'conversion',
            goal: 'maximize',
          },
        ],
      });
      await framework.startExperiment('test-no-primary');

      const results = await framework.getResults('test-no-primary');

      expect(results.winner).toBeUndefined();
      expect(results.recommendations).toContain('No primary metric defined');
    });
  });
});
