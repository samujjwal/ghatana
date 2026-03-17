/**
 * Mock Data Service for DevSecOps
 *
 * Provides realistic test data for development and testing.
 *
 * @module state/devsecops/mockDataService
 */

import type { Item, Phase, Milestone } from '@ghatana/yappc-types/devsecops';
import {
  generateDevSecOpsItems,
  devsecopsPhases,
  devsecopsMilestones,
} from '@ghatana/yappc-types/devsecops/fixtures';

/**
 * Generate mock items for testing.
 */
export function generateMockItems(count: number = 50): Item[] {
  return generateDevSecOpsItems(count);
}

/**
 * Mock phases data.
 */
export const mockPhases: Phase[] = devsecopsPhases;

/**
 * Mock milestones data.
 */
export const mockMilestones: Milestone[] = devsecopsMilestones;

/**
 * Initialize mock data for DevSecOps views.
 */
export async function initializeMockData(): Promise<{
  items: Item[];
  phases: Phase[];
  milestones: Milestone[];
}> {
  // Simulate network latency
  await new Promise((resolve) => setTimeout(resolve, 300));

  return {
    items: generateMockItems(40),
    phases: mockPhases,
    milestones: mockMilestones,
  };
}
