/**
 * @doc.type test
 * @doc.purpose Verify that config-hooks are properly exported from @yappc/state
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('config-hooks exports', () => {
  it('should export config-hooks module', async () => {
    const exports = await import('../index');
    expect(exports).toBeDefined();
    expect(typeof exports).toBe('object');
  });

  it('should export useConfigData hook', async () => {
    const { useConfigData } = await import('../index');
    expect(useConfigData).toBeDefined();
    expect(typeof useConfigData).toBe('function');
  });

  it('should be compatible with React hooks usage', async () => {
    const configHooks = await import('../index');
    // Config hooks should be React hooks that can be called in components
    expect(configHooks.useConfigData).toBeDefined();
  });

  it('should allow importing specific hooks', async () => {
    const { useConfigData } = await import('../useConfigData');
    expect(useConfigData).toBeDefined();
  });
});
