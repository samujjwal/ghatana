/**
 * @doc.type test
 * @doc.purpose Verify that development-ui components are properly exported
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('development-ui exports', () => {
  it('should export development-ui components', async () => {
    const exports = await import('../index');
    expect(exports).toBeDefined();
    // These components are expected from the development-ui module
    expect(typeof exports).toBe('object');
  });

  it('should have BurndownChart component', async () => {
    const { BurndownChart } = await import('../index');
    expect(BurndownChart).toBeDefined();
  });

  it('should have VelocityChart component', async () => {
    const { VelocityChart } = await import('../index');
    expect(VelocityChart).toBeDefined();
  });

  it('should have StoryCard component', async () => {
    const { StoryCard } = await import('../index');
    expect(StoryCard).toBeDefined();
  });
});
