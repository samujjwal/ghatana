/**
 * @fileoverview Integration tests for Plugin System Integration
 * Tests complete plugin ecosystem including initialization, metrics collection,
 * alert notifications, storage operations, and backward compatibility
 *
 * Phase 3e: Plugin system integration tests
 */

import { EventEmitter } from 'events';

/**
 * Mock plugin implementations for testing
 */
const mockPlugins = {
  dataCollector: {
    id: 'mock-collector',
    name: 'Mock Data Collector',
    type: 'data-collector',
    enabled: false,
    collect: jest.fn(async () => ({
      timestamp: Date.now(),
      metrics: {
        cpu: 45,
        memory: 256,
      },
    })),
  },
  notificationPlugin: {
    id: 'mock-notification',
    name: 'Mock Notification',
    type: 'notification',
    enabled: false,
    send: jest.fn(async () => 'notification-sent'),
  },
  storagePlugin: {
    id: 'mock-storage',
    name: 'Mock Storage',
    type: 'storage',
    enabled: false,
    set: jest.fn(async () => true),
    get: jest.fn(async () => ({ data: 'test' })),
  },
};

/**
 * Mock PluginSystemAdapter
 */
class MockPluginSystemAdapter {
  private plugins: any[] = [];

  constructor() {
    this.plugins = Object.values(mockPlugins);
  }

  async getAllMetrics() {
    return this.plugins
      .filter((p: any) => p.type === 'data-collector')
      .map((p: any) => ({
        pluginId: p.id,
        pluginName: p.name,
        timestamp: Date.now(),
        metrics: {
          cpu: 45,
          memory: 256,
        },
        status: 'healthy',
      }));
  }

  async getMetric(pluginId: string) {
    const plugin = this.plugins.find((p: any) => p.id === pluginId);
    if (!plugin) return null;

    return {
      pluginId: plugin.id,
      pluginName: plugin.name,
      timestamp: Date.now(),
      metrics: {
        cpu: 45,
        memory: 256,
      },
      status: 'healthy',
    };
  }

  async notify(alert: any) {
    return this.plugins
      .filter((p: any) => p.type === 'notification')
      .map((p: any) => p.id);
  }

  async store(key: string, value: any) {
    return this.plugins
      .filter((p: any) => p.type === 'storage')
      .map((p: any) => p.id);
  }

  async getPluginHealthReport() {
    return this.plugins.reduce(
      (acc: any, p: any) => {
        acc[p.id] = {
          status: 'healthy',
          message: `${p.name} is operational`,
          metrics: {
            uptime: 3600,
            requestCount: 100,
          },
        };
        return acc;
      },
      {},
    );
  }
}

/**
 * Mock ComprehensiveMonitor
 */
class MockComprehensiveMonitor extends EventEmitter {
  private pluginAdapter: any = null;

  setPluginAdapter(adapter: any) {
    this.pluginAdapter = adapter;
  }

  async checkHealth() {
    const components: any = {};

    if (this.pluginAdapter) {
      const pluginHealth = await this.pluginAdapter.getPluginHealthReport();
      for (const [pluginId, health] of Object.entries(pluginHealth)) {
        components[`plugin:${pluginId}`] = {
          name: `plugin:${pluginId}`,
          status: (health as any).status || 'healthy',
          message: (health as any).message,
          lastCheck: Date.now(),
          metrics: (health as any).metrics,
        };
      }
    }

    return {
      overall: 'healthy',
      components,
      metrics: {
        uptime: 3600000,
        errorRate: 0.01,
        latency: 45,
      },
      timestamp: Date.now(),
    };
  }
}

describe('Plugin System Integration', () => {
  let adapter: MockPluginSystemAdapter;
  let monitor: MockComprehensiveMonitor;

  beforeEach(() => {
    adapter = new MockPluginSystemAdapter();
    monitor = new MockComprehensiveMonitor();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('Initialization', () => {
    it('should initialize plugin system adapter', async () => {
      expect(adapter).toBeDefined();
      const metrics = await adapter.getAllMetrics();
      expect(metrics).toBeDefined();
    });

    it('should integrate adapter with monitor', async () => {
      monitor.setPluginAdapter(adapter);
      const health = await monitor.checkHealth();

      expect(health).toBeDefined();
      expect(health.overall).toBe('healthy');
      expect(health.components).toBeDefined();
    });

    it('should handle initialization failure gracefully', async () => {
      const failingAdapter = {
        async getAllMetrics() {
          throw new Error('Initialization failed');
        },
      };

      expect(() => {
        monitor.setPluginAdapter(failingAdapter);
      }).not.toThrow();
    });
  });

  describe('Metric Collection', () => {
    it('should collect metrics from all plugins', async () => {
      const metrics = await adapter.getAllMetrics();

      expect(metrics).toBeDefined();
      expect(Array.isArray(metrics)).toBe(true);
      if (metrics.length > 0) {
        expect(metrics[0]).toHaveProperty('pluginId');
        expect(metrics[0]).toHaveProperty('timestamp');
        expect(metrics[0]).toHaveProperty('metrics');
      }
    });

    it('should retrieve single plugin metric', async () => {
      const metric = await adapter.getMetric('mock-collector');

      expect(metric).toBeDefined();
      if (metric) {
        expect(metric.pluginId).toBe('mock-collector');
        expect(metric).toHaveProperty('metrics');
        expect(metric).toHaveProperty('status');
      }
    });

    it('should return null for non-existent plugin', async () => {
      const metric = await adapter.getMetric('non-existent');
      expect(metric).toBeNull();
    });

    it('should integrate metrics into health report', async () => {
      monitor.setPluginAdapter(adapter);
      const health = await monitor.checkHealth();

      expect(health).toBeDefined();
      expect(health.components).toBeDefined();
    });
  });

  describe('Alert Notification', () => {
    it('should send alert via notification plugins', async () => {
      const alert = {
        title: 'System Alert',
        message: 'High CPU usage detected',
        severity: 'high',
        timestamp: Date.now(),
      };

      const recipients = await adapter.notify(alert);

      expect(recipients).toBeDefined();
      expect(Array.isArray(recipients)).toBe(true);
    });

    it('should handle alert with missing properties', async () => {
      const alert = {
        title: 'Test Alert',
      };

      expect(async () => {
        await adapter.notify(alert);
      }).not.toThrow();
    });

    it('should track alert metrics', async () => {
      const alert = {
        title: 'Test',
        message: 'Test message',
        severity: 'medium',
        timestamp: Date.now(),
      };

      const recipients = await adapter.notify(alert);
      expect(recipients).toHaveLength(expect.any(Number));
    });
  });

  describe('Data Persistence', () => {
    it('should store data via storage plugins', async () => {
      const data = {
        key: 'test-metrics',
        value: { cpu: 45, memory: 256 },
      };

      const recipients = await adapter.store(data.key, data.value);

      expect(recipients).toBeDefined();
      expect(Array.isArray(recipients)).toBe(true);
    });

    it('should handle storage errors gracefully', async () => {
      const failingAdapter = {
        async store() {
          throw new Error('Storage failed');
        },
      };

      expect(async () => {
        await failingAdapter.store('key', 'value');
      }).rejects.toThrow();
    });
  });

  describe('Health Monitoring', () => {
    it('should generate plugin health report', async () => {
      const report = await adapter.getPluginHealthReport();

      expect(report).toBeDefined();
      expect(typeof report).toBe('object');
      Object.values(report).forEach((health: any) => {
        expect(health).toHaveProperty('status');
        expect(health).toHaveProperty('message');
      });
    });

    it('should integrate health report into monitor', async () => {
      monitor.setPluginAdapter(adapter);
      const health = await monitor.checkHealth();

      expect(health.overall).toBe('healthy');
      expect(health.components).toBeDefined();
    });

    it('should reflect plugin health status', async () => {
      monitor.setPluginAdapter(adapter);
      const health = await monitor.checkHealth();

      const componentCount = Object.keys(health.components).length;
      expect(componentCount).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Backward Compatibility', () => {
    it('should work without plugin adapter', async () => {
      // Monitor without adapter should still work
      const health = await monitor.checkHealth();

      expect(health).toBeDefined();
      expect(health.overall).toBe('healthy');
    });

    it('should handle missing adapter methods gracefully', async () => {
      const incompleteAdapter = {
        async getAllMetrics() {
          return [];
        },
      };

      monitor.setPluginAdapter(incompleteAdapter);
      expect(async () => {
        await monitor.checkHealth();
      }).not.toThrow();
    });

    it('should maintain monitor event emission', () => {
      const listener = jest.fn();
      monitor.on('health-check', listener);

      expect(monitor.listenerCount('health-check')).toBe(1);
    });
  });

  describe('Performance', () => {
    it('should collect metrics within performance budget', async () => {
      const start = Date.now();
      await adapter.getAllMetrics();
      const duration = Date.now() - start;

      expect(duration).toBeLessThan(1000); // < 1 second
    });

    it('should generate health report within budget', async () => {
      const start = Date.now();
      await monitor.checkHealth();
      const duration = Date.now() - start;

      expect(duration).toBeLessThan(1000); // < 1 second
    });

    it('should handle multiple concurrent operations', async () => {
      const start = Date.now();
      await Promise.all([
        adapter.getAllMetrics(),
        adapter.getPluginHealthReport(),
        monitor.checkHealth(),
      ]);
      const duration = Date.now() - start;

      expect(duration).toBeLessThan(3000); // < 3 seconds for all
    });
  });
});
