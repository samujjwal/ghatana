import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import {
  PLUGIN_INTEGRATION_BOUNDARY_MESSAGE,
  useDataCloudPlugins,
  usePluginConfiguration,
  usePluginInstallation,
} from '../plugin-integration';

describe('plugin integration launcher boundaries', () => {
  it('returns boundary errors and rejects unsupported plugin operations', async () => {
    const { result: pluginsResult } = renderHook(() =>
      useDataCloudPlugins({ includeMarketplace: true }),
    );
    const { result: installationResult } = renderHook(() => usePluginInstallation());
    const { result: configurationResult } = renderHook(() => usePluginConfiguration('plugin-1'));

    expect(pluginsResult.current.installed).toEqual([]);
    expect(pluginsResult.current.marketplace).toEqual([]);
    expect(pluginsResult.current.error?.message).toBe(PLUGIN_INTEGRATION_BOUNDARY_MESSAGE);
    await expect(pluginsResult.current.refetchInstalled()).rejects.toThrow(
      PLUGIN_INTEGRATION_BOUNDARY_MESSAGE,
    );

    await expect(
      installationResult.current.install('plugin-1', { enableOnInstall: true }),
    ).rejects.toThrow(PLUGIN_INTEGRATION_BOUNDARY_MESSAGE);
    await expect(installationResult.current.enable('plugin-1')).rejects.toThrow(
      PLUGIN_INTEGRATION_BOUNDARY_MESSAGE,
    );

    expect(configurationResult.current.plugin).toBeUndefined();
    expect(configurationResult.current.updateError?.message).toBe(
      PLUGIN_INTEGRATION_BOUNDARY_MESSAGE,
    );
    await expect(
      configurationResult.current.updateConfig({ enabled: true }),
    ).rejects.toThrow(PLUGIN_INTEGRATION_BOUNDARY_MESSAGE);
  });
});