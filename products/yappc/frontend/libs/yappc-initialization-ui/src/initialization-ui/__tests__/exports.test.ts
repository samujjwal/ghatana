/**
 * @doc.type test
 * @doc.purpose Verify that initialization-ui components are properly exported
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('initialization-ui exports', () => {
  it('should export initialization-ui components', async () => {
    const exports = await import('../index');
    expect(exports).toBeDefined();
    expect(typeof exports).toBe('object');
  });

  it('should have ResourcesList component', async () => {
    const { ResourcesList } = await import('../index');
    expect(ResourcesList).toBeDefined();
  });

  it('should have PresetCard component', async () => {
    const { PresetCard } = await import('../index');
    expect(PresetCard).toBeDefined();
  });
});
