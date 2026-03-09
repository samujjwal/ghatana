import { PluginRegistry } from '../../core/PluginRegistry';

describe('PluginRegistry Integration Tests', () => {
  let registry: PluginRegistry;

  beforeEach(() => {
    registry = new PluginRegistry();
  });

  describe('Registration', () => {
    it('should register and retrieve notification plugins', () => {
      const plugin: any = {
        id: 'notify-1',
        name: 'Test Notification',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      expect(registry.has('notify-1')).toBe(true);
      expect(registry.getPlugin('notify-1')).toEqual(plugin);
    });

    it('should reject duplicate registrations', () => {
      const plugin: any = {
        id: 'notify-1',
        name: 'Test',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      expect(() => registry.register(plugin)).toThrow();
    });
  });

  describe('Discovery', () => {
    it('should find plugins by type', () => {
      const notif: any = {
        id: 'n1',
        name: 'N',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      const storage: any = {
        id: 's1',
        name: 'S',
        version: '1.0.0',
        type: 'storage',
        set: jest.fn(),
        get: jest.fn(),
        delete: jest.fn(),
        exists: jest.fn(),
      };

      registry.register(notif);
      registry.register(storage);

      expect(registry.findByType('INotification')).toHaveLength(1);
      expect(registry.findByType('IStorage')).toHaveLength(1);
      expect(registry.findByType('IDataCollector')).toHaveLength(0);
    });

    it('should return all plugins', () => {
      const notif: any = {
        id: 'n1',
        name: 'N',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(notif);
      expect(registry.getAllPlugins()).toHaveLength(1);
    });
  });

  describe('Search', () => {
    it('should search by predicate', () => {
      const notif: any = {
        id: 'n1',
        name: 'Notify',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(notif);
      const results = registry.search((p: any) => p.type === 'notification');
      expect(results).toHaveLength(1);
    });
  });

  describe('Unregistration', () => {
    it('should unregister plugins', () => {
      const plugin: any = {
        id: 'n1',
        name: 'N',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      expect(registry.count()).toBe(1);
      registry.unregister('n1');
      expect(registry.count()).toBe(0);
    });
  });

  describe('Version Management', () => {
    it('should track plugin versions', () => {
      const plugin: any = {
        id: 'n1',
        name: 'N',
        version: '2.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      const versionInfo = registry.getVersion('n1');
      expect(versionInfo).not.toBeNull();
      expect(versionInfo?.version).toBe('2.0.0');
      expect(versionInfo?.compatibleVersions).toContain('2.0.0');
      expect(registry.getVersion('nonexistent')).toBeNull();
    });
  });

  describe('Statistics', () => {
    it('should provide statistics', () => {
      const plugin: any = {
        id: 'n1',
        name: 'N',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      const stats = registry.getStats();
      expect(stats.totalPlugins).toBe(1);
      expect(typeof stats.pluginTypes).toBe('object');
      expect(stats.pluginTypes).toHaveProperty('INotification', 1);
    });
  });

  describe('Clear', () => {
    it('should clear all plugins', () => {
      const plugin: any = {
        id: 'n1',
        name: 'N',
        version: '1.0.0',
        type: 'notification',
        notify: jest.fn(),
      };

      registry.register(plugin);
      registry.clear();
      expect(registry.count()).toBe(0);
    });
  });
});
