import { ConnectorManager } from '../src/ConnectorManager';
import { HttpConnector } from '../src/connectors/HttpConnector';

// Mock the connector factory to prevent real connections
jest.mock('../src/index', () => {
  const originalModule = jest.requireActual('../src/index');
  return {
    ...originalModule,
    createConnector: jest.fn((config: any) => {
      const mockConnector = {
        id: config.id,
        type: config.type,
        status: 'disconnected' as const,
        connect: jest.fn().mockResolvedValue(undefined),
        disconnect: jest.fn().mockResolvedValue(undefined),
        send: jest.fn().mockResolvedValue(undefined),
        on: jest.fn(),
        onEvent: jest.fn(),
        removeListener: jest.fn(),
        destroy: jest.fn().mockResolvedValue(undefined),
        config: config,
      };
      // Simulate status change to connected after connect
      mockConnector.connect = jest.fn().mockImplementation(async () => {
        (mockConnector as any).status = 'connected';
      });
      return mockConnector;
    }),
  };
});

describe('ConnectorManager', () => {
  let manager: ConnectorManager;

  beforeEach(() => {
    jest.clearAllMocks();
    manager = new ConnectorManager();
  });

  afterEach(async () => {
    await manager.shutdown();
  });

  describe('initialization', () => {
    it('should initialize with sources and sinks', async () => {
      await manager.initialize({
        sources: [
          {
            id: 'test-source',
            type: 'http',
            url: 'http://example.com',
            sinks: ['test-sink'],
          },
        ],
        sinks: [
          {
            id: 'test-sink',
            type: 'http',
            url: 'http://sink.example.com',
          },
        ],
      });

      expect(manager.status).toBe('running');
      expect(manager.getSources()).toHaveLength(1);
      expect(manager.getSinks()).toHaveLength(1);
    });

    it('should throw error if already initialized', async () => {
      await manager.initialize();
      await expect(manager.initialize()).rejects.toThrow('already initialized');
    });
  });

  describe('source management', () => {
    it('should add a source', async () => {
      const source = await manager.addSource({
        id: 'new-source',
        type: 'http',
        url: 'http://example.com',
      });

      expect(source).toBeDefined();
      expect(manager.getSource('new-source')).toBe(source);
    });

    it('should remove a source', async () => {
      await manager.addSource({
        id: 'temp-source',
        type: 'http',
        url: 'http://example.com',
      });

      const removed = await manager.removeSource('temp-source');
      expect(removed).toBe(true);
      expect(manager.getSource('temp-source')).toBeUndefined();
    });

    it('should enable/disable a source', async () => {
      await manager.addSource({
        id: 'toggle-source',
        type: 'http',
        url: 'http://example.com',
      });

      await manager.setSourceEnabled('toggle-source', false);
      const status = manager.getStatus();
      const source = status.sources.find(s => s.id === 'toggle-source');
      expect(source?.enabled).toBe(false);
    });
  });

  describe('sink management', () => {
    it('should add a sink', async () => {
      const sink = await manager.addSink({
        id: 'new-sink',
        type: 'http',
        url: 'http://sink.example.com',
      });

      expect(sink).toBeDefined();
      expect(manager.getSink('new-sink')).toBe(sink);
    });

    it('should remove a sink', async () => {
      await manager.addSink({
        id: 'temp-sink',
        type: 'http',
        url: 'http://sink.example.com',
      });

      const removed = await manager.removeSink('temp-sink');
      expect(removed).toBe(true);
      expect(manager.getSink('temp-sink')).toBeUndefined();
    });
  });

  describe('routing', () => {
    it('should update source routes', async () => {
      await manager.initialize({
        sources: [
          {
            id: 'source-1',
            type: 'http',
            url: 'http://example.com',
          },
        ],
        sinks: [
          {
            id: 'sink-1',
            type: 'http',
            url: 'http://sink1.example.com',
          },
          {
            id: 'sink-2',
            type: 'http',
            url: 'http://sink2.example.com',
          },
        ],
      });

      manager.updateSourceRoutes('source-1', ['sink-1', 'sink-2']);
      const status = manager.getStatus();
      const route = status.routes.find(r => r.sourceId === 'source-1');
      expect(route?.sinkIds).toEqual(['sink-1', 'sink-2']);
    });
  });

  describe('processors', () => {
    it('should add a processor to a connector', async () => {
      await manager.addSource({
        id: 'proc-source',
        type: 'http',
        url: 'http://example.com',
      });

      const processor = jest.fn(async (event) => event);
      manager.addProcessor('proc-source', processor);

      // Processor should be added (no error thrown)
      expect(true).toBe(true);
    });

    it('should remove a processor', async () => {
      await manager.addSource({
        id: 'proc-source',
        type: 'http',
        url: 'http://example.com',
      });

      const processor = jest.fn(async (event) => event);
      manager.addProcessor('proc-source', processor);
      const removed = manager.removeProcessor('proc-source', processor);

      expect(removed).toBe(true);
    });
  });

  describe('lifecycle', () => {
    it('should shutdown gracefully', async () => {
      await manager.initialize({
        sources: [
          {
            id: 'source-1',
            type: 'http',
            url: 'http://example.com',
          },
        ],
      });

      await manager.shutdown();
      expect(manager.status).toBe('idle');
      expect(manager.getSources()).toHaveLength(0);
    });

    it('should restart', async () => {
      await manager.initialize({
        sources: [
          {
            id: 'source-1',
            type: 'http',
            url: 'http://example.com',
          },
        ],
      });

      await manager.restart();
      expect(manager.status).toBe('running');
    });
  });

  describe('status', () => {
    it('should return current status', async () => {
      await manager.initialize({
        sources: [
          {
            id: 'source-1',
            type: 'http',
            url: 'http://example.com',
          },
        ],
        sinks: [
          {
            id: 'sink-1',
            type: 'http',
            url: 'http://sink.example.com',
          },
        ],
      });

      const status = manager.getStatus();
      expect(status.status).toBe('running');
      expect(status.sources).toHaveLength(1);
      expect(status.sinks).toHaveLength(1);
    });
  });
});
