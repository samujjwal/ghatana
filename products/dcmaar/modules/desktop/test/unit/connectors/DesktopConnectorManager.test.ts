/**
 * Unit tests for DesktopConnectorManager
 * Tests lifecycle, source/sink management, health checks, error handling, and event subscriptions
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { DesktopConnectorManager } from '../../../src/libs/connectors/DesktopConnectorManager';
import type {
  DesktopConnectorConfig,
  ConnectorConfig,
} from '../../../src/libs/connectors/DesktopConnectorManager';
import type {
  TelemetrySource,
  ControlSink,
  TelemetrySnapshot,
  ControlCommand,
  HealthStatus,
} from '../../../src/libs/adapters/types';

// Mock adapter factory
vi.mock('../../../src/libs/adapters/adapterFactory', () => ({
  adapterFactory: {
    createSource: vi.fn(),
    createSink: vi.fn(),
  },
}));

// Mock logger
vi.mock('../../../src/libs/adapters/logger', () => ({
  createLogger: vi.fn(() => ({
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    child: vi.fn(() => ({
      debug: vi.fn(),
      info: vi.fn(),
      warn: vi.fn(),
      error: vi.fn(),
    })),
  })),
}));

// Mock tracer
vi.mock('../../../src/libs/adapters/tracer', () => ({
  createTracer: vi.fn(() => ({
    startSpan: vi.fn(() => ({
      end: vi.fn(),
      setStatus: vi.fn(),
      recordException: vi.fn(),
    })),
  })),
}));

// Mock queue
vi.mock('../../../src/libs/adapters/queue', () => ({
  createQueue: vi.fn(() => ({
    enqueue: vi.fn(),
    dequeue: vi.fn(),
    size: vi.fn(() => 0),
    clear: vi.fn(),
  })),
}));

// Mock keyring
vi.mock('../../../src/libs/adapters/keyring', () => ({
  createKeyring: vi.fn(() => ({
    store: vi.fn(),
    retrieve: vi.fn(),
    delete: vi.fn(),
  })),
}));

// Mock source
class MockSource implements TelemetrySource {
  kind = 'mock' as const;
  id = 'mock-source';
  isConnected = false;
  private subscriptions = new Set<(snapshot: TelemetrySnapshot) => void>();
  private unsubscribeFn?: () => void;

  async connect(): Promise<void> {
    this.isConnected = true;
  }

  async disconnect(): Promise<void> {
    this.isConnected = false;
    if (this.unsubscribeFn) {
      this.unsubscribeFn();
    }
  }

  async subscribe(emit: (snapshot: TelemetrySnapshot) => void): Promise<() => void> {
    this.subscriptions.add(emit);
    this.unsubscribeFn = () => {
      this.subscriptions.delete(emit);
    };
    return this.unsubscribeFn;
  }

  async healthCheck(): Promise<HealthStatus> {
    return {
      healthy: this.isConnected,
      lastCheck: Date.now(),
      message: this.isConnected ? 'Connected' : 'Disconnected',
    };
  }

  // Helper to emit test data
  emit(snapshot: TelemetrySnapshot): void {
    this.subscriptions.forEach(handler => handler(snapshot));
  }
}

// Mock sink
class MockSink implements ControlSink {
  id = 'mock-sink';
  isConnected = false;
  private queue: ControlCommand[] = [];

  async connect(): Promise<void> {
    this.isConnected = true;
  }

  async disconnect(): Promise<void> {
    this.isConnected = false;
  }

  async enqueue(command: ControlCommand): Promise<void> {
    if (!this.isConnected) {
      throw new Error('Sink not connected');
    }
    this.queue.push(command);
  }

  async flush(): Promise<void> {
    this.queue = [];
  }

  async healthCheck(): Promise<{ healthy: boolean; message?: string }> {
    return { healthy: this.isConnected };
  }

  // Helper to get queued commands
  getQueue(): ControlCommand[] {
    return [...this.queue];
  }
}

describe('DesktopConnectorManager', () => {
  let manager: DesktopConnectorManager;
  let mockSource: MockSource;
  let mockSink: MockSink;
  let config: DesktopConnectorConfig;

  beforeEach(async () => {
    vi.clearAllMocks();

    mockSource = new MockSource();
    mockSink = new MockSink();

    // Setup mock factory to return our mocks
    const { adapterFactory } = await import('../../../src/libs/adapters/adapterFactory');
    vi.mocked(adapterFactory.createSource).mockResolvedValue(mockSource as any);
    vi.mocked(adapterFactory.createSink).mockResolvedValue(mockSink as any);

    config = {
      workspaceId: 'test-workspace',
      sources: [
        {
          id: 'source-1',
          name: 'Test Source',
          type: 'mock',
          enabled: true,
          options: {},
        },
      ],
      sinks: [
        {
          id: 'sink-1',
          name: 'Test Sink',
          type: 'mock',
          enabled: true,
          options: {},
        },
      ],
      autoStart: false,
      healthCheckIntervalMs: 30000,
      logging: {
        level: 'info',
        outputs: ['console'],
      },
    };
  });

  afterEach(async () => {
    if (manager) {
      await manager.shutdown();
    }
  });

  describe('Lifecycle Management', () => {
    it('should initialize manager with config', async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.phase).toBe('ready');
      expect(snapshot.sources).toHaveLength(1);
      expect(snapshot.sinks).toHaveLength(1);
    });

    it('should auto-start if configured', async () => {
      config.autoStart = true;
      manager = new DesktopConnectorManager();
      await manager.initialize(config);

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.phase).toBe('running');
      expect(mockSource.isConnected).toBe(true);
      expect(mockSink.isConnected).toBe(true);
    });

    it('should start all connectors', async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();

      expect(mockSource.isConnected).toBe(true);
      expect(mockSink.isConnected).toBe(true);

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.phase).toBe('running');
    });

    it('should stop all connectors', async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();
      await manager.stopAll();

      expect(mockSource.isConnected).toBe(false);
      expect(mockSink.isConnected).toBe(false);

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.phase).toBe('stopped');
    });

    it('should shutdown cleanly', async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();
      await manager.shutdown();

      expect(mockSource.isConnected).toBe(false);
      expect(mockSink.isConnected).toBe(false);

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.phase).toBe('shutdown');
    });

    it('should handle start errors gracefully', async () => {
      const errorSource = new MockSource();
      vi.spyOn(errorSource, 'connect').mockRejectedValueOnce(new Error('Connection failed'));

      const { adapterFactory } = await import('../../../src/libs/adapters/adapterFactory');
      vi.mocked(adapterFactory.createSource).mockResolvedValueOnce(errorSource as any);

      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();

      const snapshot = manager.getSnapshot();
      expect(snapshot.state.errors.length).toBeGreaterThan(0);
    });
  });

  describe('Source Management', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
    });

    it('should add new source at runtime', async () => {
      const newSource: ConnectorConfig = {
        id: 'source-2',
        name: 'New Source',
        type: 'mock',
        enabled: true,
        options: {},
      };

      await manager.addSource(newSource);

      const snapshot = manager.getSnapshot();
      expect(snapshot.sources).toHaveLength(2);
      expect(snapshot.sources.find(s => s.id === 'source-2')).toBeDefined();
    });

    it('should remove source', async () => {
      await manager.startAll();
      await manager.removeSource('source-1');

      const snapshot = manager.getSnapshot();
      expect(snapshot.sources).toHaveLength(0);
      expect(mockSource.isConnected).toBe(false);
    });

    it('should toggle source enabled state', async () => {
      await manager.startAll();

      await manager.toggleSource('source-1', false);
      let snapshot = manager.getSnapshot();
      const disabledSource = snapshot.sources.find(s => s.id === 'source-1');
      expect(disabledSource?.enabled).toBe(false);

      await manager.toggleSource('source-1', true);
      snapshot = manager.getSnapshot();
      const enabledSource = snapshot.sources.find(s => s.id === 'source-1');
      expect(enabledSource?.enabled).toBe(true);
    });

    it('should receive telemetry from sources', async () => {
      const telemetryHandler = vi.fn();
      manager.on('telemetryUpdate', telemetryHandler);

      await manager.startAll();

      const testSnapshot: TelemetrySnapshot = {
        timestamp: Date.now(),
        workspaceId: 'test-workspace',
        agents: [],
        metrics: {},
      };

      mockSource.emit(testSnapshot);

      // Wait for async event propagation
      await new Promise(resolve => setTimeout(resolve, 100));

      expect(telemetryHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          workspaceId: 'test-workspace',
        })
      );
    });
  });

  describe('Sink Management', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
    });

    it('should add new sink at runtime', async () => {
      const newSink: ConnectorConfig = {
        id: 'sink-2',
        name: 'New Sink',
        type: 'mock',
        enabled: true,
        options: {},
      };

      await manager.addSink(newSink);

      const snapshot = manager.getSnapshot();
      expect(snapshot.sinks).toHaveLength(2);
      expect(snapshot.sinks.find(s => s.id === 'sink-2')).toBeDefined();
    });

    it('should remove sink', async () => {
      await manager.startAll();
      await manager.removeSink('sink-1');

      const snapshot = manager.getSnapshot();
      expect(snapshot.sinks).toHaveLength(0);
      expect(mockSink.isConnected).toBe(false);
    });

    it('should toggle sink enabled state', async () => {
      await manager.startAll();

      await manager.toggleSink('sink-1', false);
      let snapshot = manager.getSnapshot();
      const disabledSink = snapshot.sinks.find(s => s.id === 'sink-1');
      expect(disabledSink?.enabled).toBe(false);

      await manager.toggleSink('sink-1', true);
      snapshot = manager.getSnapshot();
      const enabledSink = snapshot.sinks.find(s => s.id === 'sink-1');
      expect(enabledSink?.enabled).toBe(true);
    });

    it('should send commands to sinks', async () => {
      await manager.startAll();

      const command: ControlCommand = {
        id: 'cmd-1',
        timestamp: Date.now(),
        type: 'config.update',
        target: 'agent-1',
        payload: { key: 'value' },
      };

      await manager.sendCommand(command);

      const queue = mockSink.getQueue();
      expect(queue).toHaveLength(1);
      expect(queue[0]).toMatchObject(command);
    });
  });

  describe('Health Monitoring', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();
    });

    it('should check health of all connectors', async () => {
      const health = await manager.healthCheck();

      expect(health.overall).toBe('healthy');
      expect(health.sources).toHaveLength(1);
      expect(health.sinks).toHaveLength(1);
      expect(health.sources[0].healthy).toBe(true);
      expect(health.sinks[0].healthy).toBe(true);
    });

    it('should detect unhealthy connectors', async () => {
      // Disconnect source to make it unhealthy
      await mockSource.disconnect();

      const health = await manager.healthCheck();

      expect(health.overall).toBe('degraded');
      expect(health.sources[0].healthy).toBe(false);
    });

    it('should emit health check events', async () => {
      const healthHandler = vi.fn();
      manager.on('healthCheck', healthHandler);

      await manager.healthCheck();

      expect(healthHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          overall: expect.any(String),
          sources: expect.any(Array),
          sinks: expect.any(Array),
        })
      );
    });
  });

  describe('Event System', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
    });

    it('should emit stateChange events', async () => {
      const stateHandler = vi.fn();
      manager.on('stateChange', stateHandler);

      await manager.startAll();

      expect(stateHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          phase: 'running',
        })
      );
    });

    it('should emit error events', async () => {
      const errorHandler = vi.fn();
      manager.on('error', errorHandler);

      const errorSource = new MockSource();
      vi.spyOn(errorSource, 'connect').mockRejectedValueOnce(new Error('Test error'));

      const { adapterFactory } = await import('../../../src/libs/adapters/adapterFactory');
      vi.mocked(adapterFactory.createSource).mockResolvedValueOnce(errorSource as any);

      await manager.addSource({
        id: 'error-source',
        name: 'Error Source',
        type: 'mock',
        enabled: true,
        options: {},
      });

      await manager.startSource('error-source');

      expect(errorHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          message: expect.stringContaining('Test error'),
        })
      );
    });

    it('should unsubscribe from events', async () => {
      const handler = vi.fn();
      manager.on('stateChange', handler);
      manager.off('stateChange', handler);

      await manager.startAll();

      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('Error Handling', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
    });

    it('should handle invalid connector config', async () => {
      const invalidConfig: any = {
        id: 'invalid',
        name: 'Invalid',
        type: 'invalid-type',
        enabled: true,
        options: {},
      };

      await expect(manager.addSource(invalidConfig)).rejects.toThrow();
    });

    it('should continue after individual connector failures', async () => {
      // Add a good source
      await manager.addSource({
        id: 'good-source',
        name: 'Good Source',
        type: 'mock',
        enabled: true,
        options: {},
      });

      // Add a failing source
      const errorSource = new MockSource();
      errorSource.id = 'error-source';
      vi.spyOn(errorSource, 'connect').mockRejectedValue(new Error('Connection failed'));

      const { adapterFactory } = await import('../../../src/libs/adapters/adapterFactory');
      vi.mocked(adapterFactory.createSource).mockResolvedValueOnce(errorSource as any);

      await manager.addSource({
        id: 'error-source',
        name: 'Error Source',
        type: 'mock',
        enabled: true,
        options: {},
      });

      await manager.startAll();

      const snapshot = manager.getSnapshot();
      // Should have started despite one failure
      expect(snapshot.state.phase).toBe('running');
      expect(snapshot.state.errors.length).toBeGreaterThan(0);
    });

    it('should handle command send failures', async () => {
      await manager.startAll();

      vi.spyOn(mockSink, 'enqueue').mockRejectedValueOnce(new Error('Enqueue failed'));

      const command: ControlCommand = {
        id: 'cmd-1',
        timestamp: Date.now(),
        type: 'config.update',
        target: 'agent-1',
        payload: {},
      };

      await expect(manager.sendCommand(command)).rejects.toThrow();
    });
  });

  describe('Configuration Management', () => {
    it('should validate connector config', async () => {
      const validConfig: DesktopConnectorConfig = {
        workspaceId: 'test',
        sources: [],
        sinks: [],
        autoStart: false,
        logging: {
          level: 'info',
          outputs: ['console'],
        },
      };

      manager = new DesktopConnectorManager();
      await expect(manager.initialize(validConfig)).resolves.not.toThrow();
    });

    it('should reject config without workspaceId', async () => {
      const invalidConfig: any = {
        sources: [],
        sinks: [],
      };

      manager = new DesktopConnectorManager();
      await expect(manager.initialize(invalidConfig)).rejects.toThrow();
    });

    it('should handle config with disabled connectors', async () => {
      config.sources[0].enabled = false;
      config.sinks[0].enabled = false;

      manager = new DesktopConnectorManager();
      await manager.initialize(config);
      await manager.startAll();

      expect(mockSource.isConnected).toBe(false);
      expect(mockSink.isConnected).toBe(false);
    });
  });

  describe('Snapshot Generation', () => {
    beforeEach(async () => {
      manager = new DesktopConnectorManager();
      await manager.initialize(config);
    });

    it('should generate comprehensive snapshot', () => {
      const snapshot = manager.getSnapshot();

      expect(snapshot).toHaveProperty('state');
      expect(snapshot).toHaveProperty('sources');
      expect(snapshot).toHaveProperty('sinks');
      expect(snapshot).toHaveProperty('telemetry');
      expect(snapshot.state).toHaveProperty('phase');
      expect(snapshot.state).toHaveProperty('startedAt');
      expect(snapshot.state).toHaveProperty('errors');
    });

    it('should update snapshot after state changes', async () => {
      const beforeSnapshot = manager.getSnapshot();
      expect(beforeSnapshot.state.phase).toBe('ready');

      await manager.startAll();

      const afterSnapshot = manager.getSnapshot();
      expect(afterSnapshot.state.phase).toBe('running');
      expect(afterSnapshot.state.startedAt).toBeDefined();
    });
  });
});
