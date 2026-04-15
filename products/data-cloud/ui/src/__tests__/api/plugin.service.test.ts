import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import { pluginService } from '@/api/plugin.service';

describe('pluginService contract mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockApiClient.get.mockImplementation((path: string) => {
      if (path === '/plugins') {
        return Promise.resolve({
          plugins: [
            {
              id: 'plugin-orders',
              displayName: 'Orders Connector',
              version: '1.2.3',
              status: 'enabled',
              supportedRecordTypes: ['Entity', 'Metric'],
            },
          ],
          total: 1,
        });
      }

      if (path === '/plugins/plugin-orders') {
        return Promise.resolve({
          id: 'plugin-orders',
          displayName: 'Orders Connector',
          version: '1.2.3',
          status: 'enabled',
          supportedRecordTypes: ['Entity', 'Metric'],
        });
      }

      return Promise.reject(new Error(`Unexpected path: ${path}`));
    });

    mockApiClient.post.mockResolvedValue({ ok: true });
  });

  it('maps canonical bundled plugin payloads into the UI read model', async () => {
    const plugins = await pluginService.getInstalledPlugins();

    expect(mockApiClient.get).toHaveBeenCalledWith('/plugins');
    expect(plugins).toHaveLength(1);
    expect(plugins[0]).toMatchObject({
      id: 'plugin-orders',
      status: 'active',
      metadata: {
        name: 'Orders Connector',
        version: '1.2.3',
        category: 'connector',
      },
    });
  });

  it('uses canonical enable and disable routes before reloading the plugin view', async () => {
    await pluginService.enablePlugin('plugin-orders');
    await pluginService.disablePlugin('plugin-orders');

    expect(mockApiClient.post).toHaveBeenCalledWith('/plugins/plugin-orders/enable');
    expect(mockApiClient.post).toHaveBeenCalledWith('/plugins/plugin-orders/disable');
    expect(mockApiClient.get).toHaveBeenCalledWith('/plugins/plugin-orders');
  });

  it('fails explicitly for unsupported marketplace and runtime mutation helpers', async () => {
    await expect(pluginService.updatePluginConfiguration('plugin-orders', { retries: 3 })).rejects.toThrow(/Plugin configuration is not exposed/i);
    await expect(pluginService.uninstallPlugin('plugin-orders')).rejects.toThrow(/cannot be uninstalled at runtime/i);
    await expect(pluginService.getMarketplacePlugin('marketplace-plugin')).rejects.toThrow(/Marketplace metadata is not exposed/i);
    await expect(pluginService.installPlugin({ pluginId: 'marketplace-plugin' })).rejects.toThrow(/Runtime plugin installation is not supported/i);
  });
});