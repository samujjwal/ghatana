/**
 * Mock Data Service for DevSecOps
 *
 * Provides realistic test data for development and testing.
 * This is a legitimate mock service for UI development when the real backend is unavailable.
 * For production, use the real DevSecOps service implementations.
 *
 * @module state/devsecops/mockDataService
 */

import type { Item, Phase, Milestone } from 'yappc-core/types/devsecops';
import {
  generateDevSecOpsItems,
  devsecopsPhases,
  devsecopsMilestones,
} from 'yappc-core/types/devsecops/fixtures';

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
