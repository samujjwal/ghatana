import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CollabRealtimeMonitor } from '../CollabRealtimeMonitor';
import type { CollabRealtimeMonitorConfig, CollabHealthMetric } from '../CollabRealtimeMonitor';

// ============================================================================
// Mock @ghatana/realtime WebSocketClient
//
// The `client.ts` WebSocketClient uses:
//   - connect(): Promise<void>
//   - disconnect(): void
//   - onStateChange(listener): () => void
//   - subscribe<T>(type, handler): () => void
//   - send(message): boolean
//
// vi.mock is hoisted but the factory is called lazily (when the module is
// first imported). Module-level `let` variables are initialized before the
// factory runs, so shared state is safe.
// ============================================================================

type StateListener = (state: { status: string; reconnectAttempt: number }) => void;
type SubscribeHandler = (message: { payload: unknown }) => void;

/** Shared state — reset by mock constructor on each instantiation. */
const mockStateListeners = new Set<StateListener>();
const mockSubscriptions = new Map<string, Set<SubscribeHandler>>();
let mockConnectCount = 0;
let mockDisconnectCount = 0;

/** Fire a mock connection state change into all registered listeners. */
function fireMockState(status: string, reconnectAttempt = 0): void {
  mockStateListeners.forEach((cb) => cb({ status, reconnectAttempt }));
}

/** Fire a mock subscribed message to registered handlers. */
function fireMockMessage(type: string, payload: unknown): void {
  mockSubscriptions.get(type)?.forEach((cb) => cb({ payload }));
}

vi.mock('@ghatana/realtime', () => ({
  WebSocketClient: class {
    constructor(_opts: unknown) {
      // Reset shared state on each instantiation so tests are isolated.
      mockStateListeners.clear();
      mockSubscriptions.clear();
      mockConnectCount = 0;
      mockDisconnectCount = 0;
    }

    connect(): Promise<void> {
      mockConnectCount += 1;
      return Promise.resolve();
    }

    disconnect(): void {
      mockDisconnectCount += 1;
    }

    onStateChange(listener: StateListener): () => void {
      // Immediately call with disconnected state as the real client does.
      listener({ status: 'disconnected', reconnectAttempt: 0 });
      mockStateListeners.add(listener);
      return () => {
        mockStateListeners.delete(listener);
      };
    }

    subscribe(type: string, handler: SubscribeHandler): () => void {
      if (!mockSubscriptions.has(type)) mockSubscriptions.set(type, new Set());
      mockSubscriptions.get(type)!.add(handler);
      return () => {
        mockSubscriptions.get(type)?.delete(handler);
      };
    }

    send(_message: unknown): boolean {
      return true;
    }
  },
}));

// ============================================================================
// Helpers
// ============================================================================

function makeConfig(
  overrides: Partial<CollabRealtimeMonitorConfig> = {},
): CollabRealtimeMonitorConfig {
  return {
    healthUrl: 'ws://collab-server/health',
    sessionId: 'sess-1',
    documentId: 'doc-xyz',
    ...overrides,
  };
}

// ============================================================================
// Tests
// ============================================================================

describe('CollabRealtimeMonitor', () => {
  beforeEach(() => {
    mockStateListeners.clear();
    mockSubscriptions.clear();
    mockConnectCount = 0;
    mockDisconnectCount = 0;
  });

  describe('start() and stop()', () => {
    it('calls connect on start', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      expect(mockConnectCount).toBe(1);
    });

    it('calls disconnect on stop', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      monitor.stop();
      expect(mockDisconnectCount).toBe(1);
    });

    it('isHealthy() is false before any connection', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      // onStateChange fires 'disconnected' immediately in the mock constructor
      expect(monitor.isHealthy()).toBe(false);
    });
  });

  describe('connection health state', () => {
    it('isHealthy() becomes true on connected state', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      fireMockState('connected');
      expect(monitor.isHealthy()).toBe(true);
    });

    it('isHealthy() becomes false on disconnected state after connected', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      fireMockState('connected');
      fireMockState('disconnected');
      expect(monitor.isHealthy()).toBe(false);
    });

    it('isHealthy() becomes false on reconnecting state after connected', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      fireMockState('connected');
      fireMockState('reconnecting', 1);
      expect(monitor.isHealthy()).toBe(false);
    });

    it('stop() sets isHealthy to false', () => {
      const monitor = new CollabRealtimeMonitor(makeConfig());
      monitor.start();
      fireMockState('connected');
      expect(monitor.isHealthy()).toBe(true);
      monitor.stop();
      expect(monitor.isHealthy()).toBe(false);
    });
  });

  describe('onHealthChange callback', () => {
    it('calls onHealthChange(true) when connected state fires', () => {
      const onHealthChange = vi.fn();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onHealthChange }));
      monitor.start();
      fireMockState('connected');
      expect(onHealthChange).toHaveBeenCalledWith(true);
    });

    it('calls onHealthChange(false) when disconnected fires after connected', () => {
      const onHealthChange = vi.fn();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onHealthChange }));
      monitor.start();
      fireMockState('connected');
      fireMockState('disconnected');
      expect(onHealthChange).toHaveBeenCalledWith(false);
    });

    it('does not call onHealthChange(false) twice for repeated disconnected events', () => {
      const onHealthChange = vi.fn();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onHealthChange }));
      monitor.start();
      fireMockState('connected');
      fireMockState('disconnected');
      fireMockState('disconnected'); // already unhealthy — should not fire again
      const falseCalls = onHealthChange.mock.calls.filter(([v]) => v === false);
      expect(falseCalls).toHaveLength(1);
    });

    it('calls onHealthChange(false) on reconnecting after connected', () => {
      const onHealthChange = vi.fn();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onHealthChange }));
      monitor.start();
      fireMockState('connected');
      fireMockState('reconnecting', 1);
      expect(onHealthChange).toHaveBeenCalledWith(false);
    });
  });

  describe('onMetric callback', () => {
    it('emits connected metric when state becomes connected', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('connected');
      expect(onMetric).toHaveBeenCalledWith(
        expect.objectContaining<Partial<CollabHealthMetric>>({
          type: 'connected',
          sessionId: 'sess-1',
          documentId: 'doc-xyz',
        }),
      );
    });

    it('emits disconnected metric when state transitions to disconnected after connected', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('connected');
      onMetric.mockClear();
      fireMockState('disconnected');
      const types = onMetric.mock.calls.map(([m]) => m.type);
      expect(types).toContain('disconnected');
    });

    it('emits reconnect metric on reconnecting state', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('reconnecting', 1);
      const types = onMetric.mock.calls.map(([m]) => m.type);
      expect(types).toContain('reconnect');
    });

    it('reconnect metric carries reconnect attempt as value', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('reconnecting', 3);
      const reconnectMetric = onMetric.mock.calls.find(([m]) => m.type === 'reconnect');
      expect(reconnectMetric![0].value).toBe(3);
    });

    it('metric has timestamp as number', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('connected');
      const metric = onMetric.mock.calls[0]?.[0];
      expect(typeof metric?.timestamp).toBe('number');
    });

    it('emits latency metric when pong message is received', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('connected');
      onMetric.mockClear();

      const sentAt = Date.now() - 42;
      fireMockMessage('collab.health.pong', { sentAt });

      const latencyMetric = onMetric.mock.calls.find(([m]) => m.type === 'latency');
      expect(latencyMetric).toBeDefined();
      expect(latencyMetric![0].value).toBeGreaterThanOrEqual(42);
    });

    it('ignores pong without sentAt field', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(makeConfig({ onMetric }));
      monitor.start();
      fireMockState('connected');
      onMetric.mockClear();

      fireMockMessage('collab.health.pong', { other: 'data' });

      expect(onMetric).not.toHaveBeenCalled();
    });
  });

  describe('config passthrough', () => {
    it('metric carries correct sessionId and documentId', () => {
      const onMetric = vi.fn<[CollabHealthMetric], void>();
      const monitor = new CollabRealtimeMonitor(
        makeConfig({ sessionId: 'my-session', documentId: 'my-doc', onMetric }),
      );
      monitor.start();
      fireMockState('connected');

      const metric = onMetric.mock.calls[0]?.[0];
      expect(metric?.sessionId).toBe('my-session');
      expect(metric?.documentId).toBe('my-doc');
    });
  });
});
