import { beforeEach, describe, expect, it, vi } from 'vitest';

type FeatureGatesModule = typeof import('@/lib/feature-gates');

async function loadModule(): Promise<FeatureGatesModule> {
  vi.resetModules();
  return import('@/lib/feature-gates');
}

describe('feature gates', () => {
  beforeEach(() => {
    vi.unstubAllEnvs();
  });

  it('defaults optional surfaces to enabled in non-strict profiles except opt-in Data Fabric', async () => {
    vi.stubEnv('MODE', 'development');

    const gates = await loadModule();

    expect(gates.isAlertsSurfaceEnabled()).toBe(true);
    expect(gates.isFabricSurfaceEnabled()).toBe(false);
    expect(gates.isAiOperationsEnabled()).toBe(true);
    expect(gates.isAiAlertGroupingFallbackEnabled()).toBe(true);
    expect(gates.isMemorySurfaceEnabled()).toBe(true);
    expect(gates.isEntityBrowserSurfaceEnabled()).toBe(true);
    expect(gates.isContextSurfaceEnabled()).toBe(true);
    expect(gates.isAgentCatalogSurfaceEnabled()).toBe(true);
    expect(gates.isSettingsSurfaceEnabled()).toBe(true);
  });

  it('defaults optional surfaces to disabled in strict profiles', async () => {
    vi.stubEnv('VITE_DATACLOUD_PROFILE', 'production');

    const gates = await loadModule();

    expect(gates.isAlertsSurfaceEnabled()).toBe(false);
    expect(gates.isFabricSurfaceEnabled()).toBe(false);
    expect(gates.isAiOperationsEnabled()).toBe(false);
    expect(gates.isAiAlertGroupingFallbackEnabled()).toBe(false);
    expect(gates.isMemorySurfaceEnabled()).toBe(false);
    expect(gates.isEntityBrowserSurfaceEnabled()).toBe(false);
    expect(gates.isContextSurfaceEnabled()).toBe(false);
    expect(gates.isAgentCatalogSurfaceEnabled()).toBe(false);
    expect(gates.isSettingsSurfaceEnabled()).toBe(false);
  });

  it('allows explicit enable overrides in strict profiles', async () => {
    vi.stubEnv('VITE_DATACLOUD_PROFILE', 'production');
    vi.stubEnv('VITE_FEATURE_ALERTS', 'true');
    vi.stubEnv('VITE_FEATURE_FABRIC', '1');
    vi.stubEnv('VITE_FEATURE_AI_OPERATIONS', 'yes');
    vi.stubEnv('VITE_FEATURE_AI_ALERT_GROUPING_FALLBACK', 'on');
    vi.stubEnv('VITE_FEATURE_MEMORY', 'true');
    vi.stubEnv('VITE_FEATURE_ENTITY_BROWSER', 'true');
    vi.stubEnv('VITE_FEATURE_CONTEXT_EXPLORER', 'true');
    vi.stubEnv('VITE_FEATURE_AGENT_CATALOG', 'true');
    vi.stubEnv('VITE_FEATURE_SETTINGS', 'true');

    const gates = await loadModule();

    expect(gates.isAlertsSurfaceEnabled()).toBe(true);
    expect(gates.isFabricSurfaceEnabled()).toBe(true);
    expect(gates.isAiOperationsEnabled()).toBe(true);
    expect(gates.isAiAlertGroupingFallbackEnabled()).toBe(true);
    expect(gates.isMemorySurfaceEnabled()).toBe(true);
    expect(gates.isEntityBrowserSurfaceEnabled()).toBe(true);
    expect(gates.isContextSurfaceEnabled()).toBe(true);
    expect(gates.isAgentCatalogSurfaceEnabled()).toBe(true);
    expect(gates.isSettingsSurfaceEnabled()).toBe(true);
  });
});
