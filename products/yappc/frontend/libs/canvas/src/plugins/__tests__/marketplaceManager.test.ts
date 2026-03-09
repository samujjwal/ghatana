import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { MarketplaceManager } from '../marketplaceManager';

import type {
  MarketplacePlugin,
  SearchFilters,
  InstallationProgress,
  SecurityVerification,
} from '../marketplaceTypes';

describe.skip('MarketplaceManager', () => {
  let manager: MarketplaceManager;
  let fetchMock: ReturnType<typeof vi.fn>;

  // Mock plugin data
  const mockPlugin: MarketplacePlugin = {
    manifest: {
      id: 'test-plugin',
      name: 'Test Plugin',
      version: '1.0.0',
      author: { name: 'Test Author', email: 'test@example.com' },
      description: 'A test plugin',
      capabilities: ['element-type'],
    },
    marketplace: {
      publisherId: 'test-publisher',
      publishedAt: new Date('2024-01-01'),
      updatedAt: new Date('2024-10-01'),
      verified: true,
      featured: false,
      category: 'diagram',
      tags: ['test', 'example'],
      stats: {
        downloads: 1000,
        rating: 4.5,
        reviews: 50,
        weeklyDownloads: 100,
      },
      versions: [
        {
          version: '1.0.0',
          releaseDate: new Date('2024-10-01'),
          changelog: 'Initial release',
          downloadUrl: 'https://example.com/test-plugin-1.0.0.zip',
          checksum: 'abc123',
          verified: true,
        },
      ],
      license: 'MIT',
      homepage: 'https://example.com',
      repository: 'https://github.com/test/plugin',
      documentation: 'https://example.com/docs',
      changelog: 'https://example.com/changelog',
      screenshots: [],
      dependencies: {},
      permissions: [],
    },
  };

  const mockSearchResults = {
    plugins: [mockPlugin],
    total: 1,
    offset: 0,
    limit: 20,
    hasMore: false,
  };

  beforeEach(() => {
    // Reset singleton instance
    (MarketplaceManager as unknown).instance = null;
    manager = MarketplaceManager.getInstance({
      apiEndpoint: 'https://marketplace.example.com/api',
      autoUpdateCheck: false,
    });

    // Mock global fetch
    fetchMock = vi.fn();
    global.fetch = fetchMock;
  });

  afterEach(() => {
    vi.clearAllMocks();
    // Clean up event listeners
    manager.destroy();
  });

  describe('Singleton Pattern', () => {
    it('should return the same instance', () => {
      const instance1 = MarketplaceManager.getInstance();
      const instance2 = MarketplaceManager.getInstance();
      expect(instance1).toBe(instance2);
    });

    it('should allow reconfiguration on subsequent getInstance calls', () => {
      const instance1 = MarketplaceManager.getInstance({
        apiEndpoint: 'https://api1.example.com',
      });
      const instance2 = MarketplaceManager.getInstance({
        apiEndpoint: 'https://api2.example.com',
      });
      expect(instance1).toBe(instance2);
      expect(instance1['config'].apiEndpoint).toBe('https://api2.example.com');
    });
  });

  describe('search()', () => {
    it('should search plugins with filters', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockSearchResults,
      });

      const filters: SearchFilters = {
        query: 'test',
        category: 'productivity',
        page: 1,
        pageSize: 20,
      };

      const results = await manager.search(filters);

      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('search'),
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify(filters),
        })
      );

      expect(results.plugins).toHaveLength(1);
      expect(results.plugins[0].id).toBe('test-plugin');
      expect(results.total).toBe(1);
    });

    it('should handle search errors gracefully', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      });

      await expect(manager.search({ query: 'test' })).rejects.toThrow(
        'Marketplace search failed: 500 Internal Server Error'
      );
    });

    it('should search without filters', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockSearchResults,
      });

      const results = await manager.search();

      expect(fetchMock).toHaveBeenCalled();
      expect(results.plugins).toHaveLength(1);
    });
  });

  describe('getPlugin()', () => {
    it('should fetch plugin details', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });

      const plugin = await manager.getPlugin('test-plugin');

      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('plugins/test-plugin'),
        expect.objectContaining({
          method: 'GET',
        })
      );

      expect(plugin.id).toBe('test-plugin');
      expect(plugin.manifest.name).toBe('Test Plugin');
    });

    it('should handle plugin not found', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      await expect(manager.getPlugin('nonexistent')).rejects.toThrow(
        'Failed to fetch plugin: 404 Not Found'
      );
    });
  });

  describe('install()', () => {
    it('should install a plugin successfully', async () => {
      // Mock getPlugin
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });

      // Mock download
      const mockBlob = new Blob(['plugin-content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({
          'content-length': '100',
        }),
      });

      const progressEvents: InstallationProgress[] = [];
      manager.on('installation-progress', (event) => {
        progressEvents.push((event as CustomEvent).detail);
      });

      const result = await manager.install('test-plugin');

      expect(result.success).toBe(true);
      expect(result.pluginId).toBe('test-plugin');

      // Check that progress events were fired
      expect(progressEvents.length).toBeGreaterThan(0);
      expect(progressEvents[0].status).toBe('downloading');
      expect(progressEvents.some((e) => e.status === 'verifying')).toBe(true);
      expect(progressEvents.some((e) => e.status === 'installing')).toBe(true);
      expect(progressEvents[progressEvents.length - 1].status).toBe('completed');
    });

    it('should handle installation errors', async () => {
      // Mock getPlugin failure
      fetchMock.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      });

      const result = await manager.install('nonexistent-plugin');

      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
      expect(result.error).toContain('Failed to fetch plugin');
    });

    it('should resolve dependencies automatically', async () => {
      const pluginWithDeps: MarketplacePlugin = {
        ...mockPlugin,
        id: 'plugin-with-deps',
        manifest: {
          ...mockPlugin.manifest,
          id: 'plugin-with-deps',
          dependencies: ['dep-plugin-1', 'dep-plugin-2'],
        },
      };

      const depPlugin1: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dep-plugin-1',
        manifest: {
          ...mockPlugin.manifest,
          id: 'dep-plugin-1',
          name: 'Dependency 1',
        },
      };

      const depPlugin2: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dep-plugin-2',
        manifest: {
          ...mockPlugin.manifest,
          id: 'dep-plugin-2',
          name: 'Dependency 2',
        },
      };

      // Mock getPlugin calls for main plugin and dependencies
      fetchMock
        .mockResolvedValueOnce({
          ok: true,
          json: async () => pluginWithDeps,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => depPlugin1,
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => depPlugin2,
        });

      // Mock download calls
      const mockBlob = new Blob(['content']);
      fetchMock
        .mockResolvedValueOnce({
          ok: true,
          blob: async () => mockBlob,
          headers: new Headers({ 'content-length': '100' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          blob: async () => mockBlob,
          headers: new Headers({ 'content-length': '100' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          blob: async () => mockBlob,
          headers: new Headers({ 'content-length': '100' }),
        });

      const result = await manager.install('plugin-with-deps', {
        resolveDependencies: true,
      });

      expect(result.success).toBe(true);
      expect(fetchMock).toHaveBeenCalledTimes(6); // 3 getPlugin + 3 downloads
    });

    it('should skip dependency resolution when disabled', async () => {
      const pluginWithDeps: MarketplacePlugin = {
        ...mockPlugin,
        id: 'plugin-with-deps',
        manifest: {
          ...mockPlugin.manifest,
          id: 'plugin-with-deps',
          dependencies: ['dep-plugin'],
        },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => pluginWithDeps,
      });

      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      const result = await manager.install('plugin-with-deps', {
        resolveDependencies: false,
      });

      expect(result.success).toBe(true);
      expect(fetchMock).toHaveBeenCalledTimes(2); // Only main plugin
    });
  });

  describe('uninstall()', () => {
    it('should uninstall a plugin', async () => {
      // First install a plugin
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      await manager.install('test-plugin');

      // Now uninstall it
      const result = await manager.uninstall('test-plugin');

      expect(result.success).toBe(true);
      expect(result.pluginId).toBe('test-plugin');
    });

    it('should handle uninstalling non-existent plugin', async () => {
      const result = await manager.uninstall('nonexistent-plugin');

      expect(result.success).toBe(false);
      expect(result.error).toContain('Plugin not found');
    });

    it('should check for dependent plugins', async () => {
      // Install dependency first
      const depPlugin: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dep-plugin',
        manifest: {
          ...mockPlugin.manifest,
          id: 'dep-plugin',
          name: 'Dependency Plugin',
        },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => depPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('dep-plugin');

      // Install dependent plugin
      const dependentPlugin: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dependent-plugin',
        manifest: {
          ...mockPlugin.manifest,
          id: 'dependent-plugin',
          dependencies: ['dep-plugin'],
        },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => dependentPlugin,
      });
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('dependent-plugin');

      // Try to uninstall dependency
      const result = await manager.uninstall('dep-plugin', {
        checkDependents: true,
      });

      expect(result.success).toBe(false);
      expect(result.error).toContain('other plugins depend on it');
    });

    it('should force uninstall when checkDependents is false', async () => {
      // Setup same scenario as above
      const depPlugin: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dep-plugin',
      };
      const dependentPlugin: MarketplacePlugin = {
        ...mockPlugin,
        id: 'dependent-plugin',
        manifest: {
          ...mockPlugin.manifest,
          id: 'dependent-plugin',
          dependencies: ['dep-plugin'],
        },
      };

      const mockBlob = new Blob(['content']);
      fetchMock
        .mockResolvedValueOnce({ ok: true, json: async () => depPlugin })
        .mockResolvedValueOnce({
          ok: true,
          blob: async () => mockBlob,
          headers: new Headers({ 'content-length': '100' }),
        })
        .mockResolvedValueOnce({ ok: true, json: async () => dependentPlugin })
        .mockResolvedValueOnce({
          ok: true,
          blob: async () => mockBlob,
          headers: new Headers({ 'content-length': '100' }),
        });

      await manager.install('dep-plugin');
      await manager.install('dependent-plugin');

      // Force uninstall
      const result = await manager.uninstall('dep-plugin', {
        checkDependents: false,
      });

      expect(result.success).toBe(true);
    });
  });

  describe('checkUpdates()', () => {
    it('should detect available updates', async () => {
      // Install old version
      const oldPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '1.0.0' },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => oldPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('test-plugin');

      // Mock marketplace API returning new version
      const newPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '2.0.0' },
        versions: [
          ...mockPlugin.versions,
          {
            version: '2.0.0',
            releaseDate: new Date('2024-11-01'),
            changelog: 'Major update',
            downloadUrl: 'https://example.com/test-plugin-2.0.0.zip',
            checksum: 'def456',
            breaking: true,
          },
        ],
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ plugins: [newPlugin] }),
      });

      const updates = await manager.checkUpdates();

      expect(updates).toHaveLength(1);
      expect(updates[0].pluginId).toBe('test-plugin');
      expect(updates[0].currentVersion).toBe('1.0.0');
      expect(updates[0].latestVersion).toBe('2.0.0');
      expect(updates[0].breaking).toBe(true);
    });

    it('should return empty array when no updates available', async () => {
      // Install current version
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('test-plugin');

      // Mock marketplace returning same version
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ plugins: [mockPlugin] }),
      });

      const updates = await manager.checkUpdates();

      expect(updates).toHaveLength(0);
    });

    it('should prioritize security updates', async () => {
      const oldPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '1.0.0' },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => oldPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('test-plugin');

      const newPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '1.0.1' },
        versions: [
          ...mockPlugin.versions,
          {
            version: '1.0.1',
            releaseDate: new Date('2024-11-01'),
            changelog: 'Security fix',
            downloadUrl: 'https://example.com/test-plugin-1.0.1.zip',
            checksum: 'sec789',
            security: true,
          },
        ],
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ plugins: [newPlugin] }),
      });

      const updates = await manager.checkUpdates();

      expect(updates[0].priority).toBe('critical');
      expect(updates[0].security).toBe(true);
    });
  });

  describe('update()', () => {
    it('should update a plugin to latest version', async () => {
      // Install old version
      const oldPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '1.0.0' },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => oldPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });
      await manager.install('test-plugin');

      // Mock new version
      const newPlugin: MarketplacePlugin = {
        ...mockPlugin,
        manifest: { ...mockPlugin.manifest, version: '2.0.0' },
      };

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => newPlugin,
      });
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      const result = await manager.update('test-plugin');

      expect(result.success).toBe(true);
      expect(result.pluginId).toBe('test-plugin');
    });

    it('should handle update errors', async () => {
      const result = await manager.update('nonexistent-plugin');

      expect(result.success).toBe(false);
      expect(result.error).toContain('Plugin not found');
    });
  });

  describe('Security Verification', () => {
    it('should verify plugin signatures', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });

      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      let verificationResult: SecurityVerification | null = null;
      manager.on('security-verification', (event) => {
        verificationResult = (event as CustomEvent).detail;
      });

      await manager.install('test-plugin');

      expect(verificationResult).not.toBeNull();
      expect(verificationResult?.pluginId).toBe('test-plugin');
      expect(verificationResult?.signatureValid).toBeDefined();
      expect(verificationResult?.checksumValid).toBeDefined();
    });
  });

  describe('Event System', () => {
    it('should emit installation progress events', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });

      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      const events: string[] = [];
      manager.on('installation-progress', (event) => {
        events.push((event as CustomEvent).detail.status);
      });

      await manager.install('test-plugin');

      expect(events).toContain('downloading');
      expect(events).toContain('verifying');
      expect(events).toContain('installing');
      expect(events).toContain('completed');
    });

    it('should allow removing event listeners', async () => {
      const listener = vi.fn();
      manager.on('installation-progress', listener);
      manager.off('installation-progress', listener);

      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      await manager.install('test-plugin');

      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe('Installed Plugins Management', () => {
    it('should list installed plugins', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      await manager.install('test-plugin');

      const installed = manager.getInstalledPlugins();

      expect(installed).toHaveLength(1);
      expect(installed[0].id).toBe('test-plugin');
      expect(installed[0].manifest.version).toBe('1.0.0');
    });

    it('should check if a plugin is installed', async () => {
      fetchMock.mockResolvedValueOnce({
        ok: true,
        json: async () => mockPlugin,
      });
      const mockBlob = new Blob(['content']);
      fetchMock.mockResolvedValueOnce({
        ok: true,
        blob: async () => mockBlob,
        headers: new Headers({ 'content-length': '100' }),
      });

      await manager.install('test-plugin');

      expect(manager.isInstalled('test-plugin')).toBe(true);
      expect(manager.isInstalled('nonexistent')).toBe(false);
    });
  });
});
