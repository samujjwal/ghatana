/**
 * A/B Testing Framework
 *
 * Feature flag system with A/B test variants for canvas features.
 * Supports gradual rollouts, user segmentation, and metrics tracking.
 *
 * @doc.type utility
 * @doc.purpose A/B testing
 * @doc.layer platform
 * @doc.pattern FeatureFlag
 */

import React from 'react';
import { getCanvasTelemetry, CanvasTelemetryEvent } from './canvas-telemetry';

/**
 * A/B test variant
 */
export interface ABTestVariant {
  /** Variant ID */
  id: string;
  /** Variant name */
  name: string;
  /** Variant weight (0-1) */
  weight: number;
  /** Variant configuration */
  config: Record<string, unknown>;
}

/**
 * A/B test definition
 */
export interface ABTest {
  /** Test ID */
  id: string;
  /** Test name */
  name: string;
  /** Test description */
  description: string;
  /** Test variants */
  variants: ABTestVariant[];
  /** Whether test is active */
  active: boolean;
  /** Start date */
  startDate?: Date;
  /** End date */
  endDate?: Date;
  /** User segment filter */
  segmentFilter?: (userId: string) => boolean;
}

/**
 * Canvas A/B tests
 */
export const CANVAS_AB_TESTS: ABTest[] = [
  {
    id: 'calm-mode-default',
    name: 'Calm Mode Default State',
    description: 'Test whether calm mode should be enabled by default',
    active: true,
    variants: [
      {
        id: 'control',
        name: 'Calm Mode Off',
        weight: 0.5,
        config: { calmModeDefault: false },
      },
      {
        id: 'treatment',
        name: 'Calm Mode On',
        weight: 0.5,
        config: { calmModeDefault: true },
      },
    ],
  },
  {
    id: 'context-bar-position',
    name: 'Context Bar Position',
    description: 'Test optimal position for context bar',
    active: true,
    variants: [
      {
        id: 'control',
        name: 'Center',
        weight: 0.33,
        config: { contextBarPosition: 'center' },
      },
      {
        id: 'variant-a',
        name: 'Near Selection',
        weight: 0.33,
        config: { contextBarPosition: 'near-selection' },
      },
      {
        id: 'variant-b',
        name: 'Top',
        weight: 0.34,
        config: { contextBarPosition: 'top' },
      },
    ],
  },
  {
    id: 'onboarding-timing',
    name: 'Onboarding Tour Timing',
    description: 'Test when to show onboarding tour',
    active: true,
    variants: [
      {
        id: 'control',
        name: 'Immediate',
        weight: 0.5,
        config: { tourDelay: 0 },
      },
      {
        id: 'treatment',
        name: 'Delayed (5s)',
        weight: 0.5,
        config: { tourDelay: 5000 },
      },
    ],
  },
  {
    id: 'frame-default-size',
    name: 'Frame Default Size',
    description: 'Test optimal default frame size',
    active: true,
    variants: [
      {
        id: 'control',
        name: 'Medium (400x300)',
        weight: 0.33,
        config: { frameWidth: 400, frameHeight: 300 },
      },
      {
        id: 'variant-a',
        name: 'Large (500x400)',
        weight: 0.33,
        config: { frameWidth: 500, frameHeight: 400 },
      },
      {
        id: 'variant-b',
        name: 'Small (300x200)',
        weight: 0.34,
        config: { frameWidth: 300, frameHeight: 200 },
      },
    ],
  },
  {
    id: 'semantic-zoom-thresholds',
    name: 'Semantic Zoom Thresholds',
    description: 'Test zoom level thresholds for semantic modes',
    active: false,
    variants: [
      {
        id: 'control',
        name: 'Current (50%, 150%)',
        weight: 0.5,
        config: { overviewThreshold: 0.5, detailThreshold: 1.5 },
      },
      {
        id: 'treatment',
        name: 'Adjusted (40%, 200%)',
        weight: 0.5,
        config: { overviewThreshold: 0.4, detailThreshold: 2.0 },
      },
    ],
  },
];

/**
 * A/B test assignment
 */
export interface ABTestAssignment {
  /** Test ID */
  testId: string;
  /** Variant ID */
  variantId: string;
  /** Assignment timestamp */
  timestamp: number;
}

/**
 * A/B testing manager
 */
export class ABTestManager {
  private assignments: Map<string, ABTestAssignment> = new Map();
  private userId: string;

  constructor(userId?: string) {
    this.userId = userId || this.generateUserId();
    this.loadAssignments();
  }

  /**
   * Generate stable user ID
   */
  private generateUserId(): string {
    let userId = localStorage.getItem('canvas-user-id');
    if (!userId) {
      userId = `user-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      localStorage.setItem('canvas-user-id', userId);
    }
    return userId;
  }

  /**
   * Load assignments from storage
   */
  private loadAssignments(): void {
    const stored = localStorage.getItem('canvas-ab-assignments');
    if (stored) {
      try {
        const assignments = JSON.parse(stored);
        this.assignments = new Map(Object.entries(assignments));
      } catch (error) {
        console.error('Failed to load AB test assignments:', error);
      }
    }
  }

  /**
   * Save assignments to storage
   */
  private saveAssignments(): void {
    const assignments = Object.fromEntries(this.assignments);
    localStorage.setItem('canvas-ab-assignments', JSON.stringify(assignments));
  }

  /**
   * Hash string to number (for consistent variant assignment)
   */
  private hash(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash);
  }

  /**
   * Get variant for test
   */
  getVariant(testId: string): ABTestVariant | null {
    const test = CANVAS_AB_TESTS.find((t) => t.id === testId);
    if (!test || !test.active) return null;

    // Check date range
    if (test.startDate && new Date() < test.startDate) return null;
    if (test.endDate && new Date() > test.endDate) return null;

    // Check segment filter
    if (test.segmentFilter && !test.segmentFilter(this.userId)) return null;

    // Check existing assignment
    const existing = this.assignments.get(testId);
    if (existing) {
      const variant = test.variants.find((v) => v.id === existing.variantId);
      if (variant) return variant;
    }

    // Assign new variant
    const variant = this.assignVariant(test);
    if (variant) {
      this.assignments.set(testId, {
        testId,
        variantId: variant.id,
        timestamp: Date.now(),
      });
      this.saveAssignments();

      // Track assignment
      const telemetry = getCanvasTelemetry();
      telemetry.track(CanvasTelemetryEvent.CANVAS_LOADED, {
        abTest: testId,
        abVariant: variant.id,
        abAssigned: true,
      });
    }

    return variant;
  }

  /**
   * Assign variant using weighted random selection
   */
  private assignVariant(test: ABTest): ABTestVariant | null {
    if (test.variants.length === 0) return null;

    // Use consistent hashing for stable assignment
    const hash = this.hash(this.userId + test.id);
    const random = (hash % 10000) / 10000; // 0-1

    let cumulative = 0;
    for (const variant of test.variants) {
      cumulative += variant.weight;
      if (random <= cumulative) {
        return variant;
      }
    }

    // Fallback to first variant
    return test.variants[0];
  }

  /**
   * Get variant config value
   */
  getConfig<T = unknown>(testId: string, key: string, defaultValue: T): T {
    const variant = this.getVariant(testId);
    if (!variant) return defaultValue;
    return (variant.config[key] as T) ?? defaultValue;
  }

  /**
   * Check if variant is active
   */
  isVariant(testId: string, variantId: string): boolean {
    const variant = this.getVariant(testId);
    return variant?.id === variantId;
  }

  /**
   * Get all active assignments
   */
  getAssignments(): ABTestAssignment[] {
    return Array.from(this.assignments.values());
  }

  /**
   * Clear all assignments (for testing)
   */
  clearAssignments(): void {
    this.assignments.clear();
    localStorage.removeItem('canvas-ab-assignments');
  }
}

/**
 * Global AB test manager instance
 */
let abTestManager: ABTestManager | null = null;

/**
 * Get or create AB test manager
 */
export function getABTestManager(): ABTestManager {
  if (!abTestManager) {
    abTestManager = new ABTestManager();
  }
  return abTestManager;
}

/**
 * React hook for A/B testing
 */
export function useABTest(testId: string) {
  const manager = getABTestManager();
  const variant = manager.getVariant(testId);

  return {
    variant,
    variantId: variant?.id,
    config: variant?.config || {},
    isVariant: (variantId: string) => manager.isVariant(testId, variantId),
    getConfig: <T = unknown>(key: string, defaultValue: T) =>
      manager.getConfig(testId, key, defaultValue),
  };
}

/**
 * React hook for feature flag
 */
export function useFeatureFlag(flagId: string): boolean {
  const manager = getABTestManager();
  const variant = manager.getVariant(flagId);
  return variant?.id === 'treatment';
}

/**
 * HOC for A/B testing
 */
export function withABTest<P extends object>(
  testId: string,
  variantComponents: Record<string, React.ComponentType<P>>
) {
  return function ABTestWrapper(props: P) {
    const { variantId } = useABTest(testId);
    const Component = variantComponents[variantId || 'control'];
    return Component ? React.createElement(Component, props) : null;
  };
}
