import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import React from 'react';
import { LifecycleWebSocketService, lifecycleWebSocketService, useLifecycleWebSocket } from '../../services/LifecycleWebSocketService';

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  url: string;
  readyState: number = MockWebSocket.CONNECTING;
  onopen: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  private listeners = new Map<string, Set<(event: Event) => void>>();

  constructor(url: string) {
    this.url = url;
  }

  send(data: string): void {
    if (this.readyState !== MockWebSocket.OPEN) {
      throw new Error('WebSocket is not open');
    }
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent('close'));
    }
  }

  addEventListener(type: string, listener: (event: Event) => void): void {
    const typeListeners = this.listeners.get(type) ?? new Set();
    typeListeners.add(listener);
    this.listeners.set(type, typeListeners);
  }

  removeEventListener(type: string, listener: (event: Event) => void): void {
    this.listeners.get(type)?.delete(listener);
  }

  dispatchEvent(event: Event): boolean {
    this.listeners.get(event.type)?.forEach((listener) => listener(event));
    return true;
  }

  // Helper method for tests to simulate receiving messages
  simulateMessage(data: unknown): void {
    if (this.onmessage) {
      this.onmessage(new MessageEvent('message', { data: JSON.stringify(data) }));
    }
  }

  // Helper method for tests to simulate connection close
  simulateClose(code: number = 1000, reason: string = ''): void {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent('close', { code, reason }));
    }
  }
}

// Mock global WebSocket
vi.stubGlobal('WebSocket', MockWebSocket);

vi.mock('react', () => ({
  default: {
    useState: vi.fn(),
    useEffect: vi.fn(),
  },
}));

const mockReact = vi.mocked(React);

function getSocket(instance: LifecycleWebSocketService) {
  return (instance as unknown as {
    ws: {
      readyState: number;
      onopen: ((event: Event) => void) | null;
      onmessage: ((event: MessageEvent) => void) | null;
      onclose: ((event: CloseEvent) => void) | null;
      send: (data: string) => void;
    } | null;
  }).ws;
}

function openSocket(instance: LifecycleWebSocketService) {
  const ws = getSocket(instance);

  if (!ws) {
    throw new Error('Expected WebSocket connection to be initialized');
  }

  ws.readyState = MockWebSocket.OPEN;
  ws.onopen?.(new Event('open'));
  return ws;
}

function emitMessage(instance: LifecycleWebSocketService, data: unknown) {
  const ws = getSocket(instance);

  if (!ws) {
    throw new Error('Expected WebSocket connection to be initialized');
  }

  ws.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }));
}

function emitClose(
  instance: LifecycleWebSocketService,
  code: number = 1000,
  reason: string = ''
) {
  const ws = getSocket(instance);

  if (!ws) {
    throw new Error('Expected WebSocket connection to be initialized');
  }

  ws.readyState = MockWebSocket.CLOSED;
  ws.onclose?.(new CloseEvent('close', { code, reason }));
}

describe('LifecycleWebSocketService', () => {
  let service: LifecycleWebSocketService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = new LifecycleWebSocketService({
      url: 'ws://localhost:8080/lifecycle',
      maxReconnectAttempts: 3,
      reconnectIntervalMs: 100,
      heartbeatIntervalMs: 1000,
    });
  });

  afterEach(() => {
    service.disconnect();
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe('Connection Management', () => {
    it('should connect successfully', async () => {
      service.connect('project-123');

      openSocket(service);

      expect(service.isConnected()).toBe(true);
    });

    it('should disconnect successfully', () => {
      service.connect('project-123');
      service.disconnect();
      
      expect(service.isConnected()).toBe(false);
    });

    it('should not reconnect if already connected to same project', () => {
      service.connect('project-123');
      vi.advanceTimersByTime(10);

      expect(() => service.connect('project-123')).not.toThrow();
    });

    it('should disconnect and reconnect for different project', () => {
      service.connect('project-123');
      openSocket(service);
      service.connect('project-456');

      openSocket(service);
      
      expect(service.isConnected()).toBe(true);
    });
  });

  describe('Event Handling', () => {
    beforeEach(() => {
      service.connect('project-123');
      openSocket(service);
    });

    it('should handle lifecycle state updates', () => {
      const mockHandler = vi.fn();
      service.onUpdate(mockHandler);

      const update = {
        type: 'lifecycle.state.changed',
        projectId: 'project-123',
        timestamp: Date.now(),
        data: {
          currentStage: 'execute',
          previousStage: 'plan',
          tasks: [
            { id: 'task-1', status: 'completed' },
            { id: 'task-2', status: 'in_progress' },
          ],
        },
      };

      // Simulate receiving message
      emitMessage(service, update);

      expect(mockHandler).toHaveBeenCalledWith(update);
    });

    it('should ignore updates for different projects', () => {
      const mockHandler = vi.fn();
      service.onUpdate(mockHandler);

      const update = {
        type: 'lifecycle.state.changed',
        projectId: 'different-project',
        timestamp: Date.now(),
        data: { currentStage: 'execute' },
      };

      emitMessage(service, update);

      expect(mockHandler).not.toHaveBeenCalled();
    });

    it('should handle connection change events', () => {
      const mockHandler = vi.fn();
      service.onConnectionChange(mockHandler);

      service.connect('project-123');
      openSocket(service);
      emitClose(service, 1000, 'Normal closure');

      expect(mockHandler).toHaveBeenNthCalledWith(1, true);
      expect(mockHandler).toHaveBeenNthCalledWith(2, false);
    });

    it('should handle invalid message format gracefully', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const mockHandler = vi.fn();
      service.onUpdate(mockHandler);

      const invalidUpdate = {
        // Missing required fields
        type: 'lifecycle.state.changed',
      };

      emitMessage(service, invalidUpdate);

      expect(consoleSpy).toHaveBeenCalledWith(
        'Invalid lifecycle update format:',
        invalidUpdate
      );
      expect(mockHandler).not.toHaveBeenCalled();

      consoleSpy.mockRestore();
    });
  });

  describe('Reconnection Logic', () => {
    it('should attempt reconnection on unexpected close', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      service.connect('project-123');

      openSocket(service);

      // Simulate unexpected close
      emitClose(service, 1006, 'Connection lost');

      expect(consoleSpy).toHaveBeenCalledWith(
        'Scheduling reconnect attempt 1/3'
      );

      await vi.advanceTimersByTimeAsync(100);
      openSocket(service);

      expect(service.isConnected()).toBe(true);

      consoleSpy.mockRestore();
    });

    it('should not reconnect on normal close', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      service.connect('project-123');

      openSocket(service);

      // Simulate normal close
      emitClose(service, 1000, 'Normal closure');

      expect(consoleSpy).not.toHaveBeenCalledWith(
        expect.stringContaining('Scheduling reconnect')
      );

      consoleSpy.mockRestore();
    });

    it('should stop reconnecting after max attempts', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      const retryLimitedService = new LifecycleWebSocketService({
        url: 'ws://localhost:8080/lifecycle',
        maxReconnectAttempts: 1,
        reconnectIntervalMs: 10,
      });

      retryLimitedService.connect('project-123');

      openSocket(retryLimitedService);

      // Simulate close that will trigger reconnection
      emitClose(retryLimitedService, 1006, 'Connection lost');

      await vi.advanceTimersByTimeAsync(10);

      expect(consoleSpy).toHaveBeenCalledWith(
        'Scheduling reconnect attempt 1/1'
      );

      retryLimitedService.disconnect();
      consoleSpy.mockRestore();
    });
  });

  describe('Heartbeat', () => {
    it('should send heartbeat messages', async () => {
      const sendSpy = vi.fn();

      service.connect('project-123');

      const ws = openSocket(service);

      // Mock WebSocket send method
      ws.send = sendSpy;

      await vi.advanceTimersByTimeAsync(1000);

      expect(sendSpy).toHaveBeenCalledTimes(1);
      expect(JSON.parse(sendSpy.mock.calls[0][0] as string)).toEqual({
        type: 'ping',
        timestamp: expect.any(Number),
      });
    });
  });

  describe('Handler Management', () => {
    it('should register and unregister update handlers', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      const unsubscribe1 = service.onUpdate(handler1);
      service.onUpdate(handler2);

      service.connect('project-123');
      openSocket(service);

      const update = {
        type: 'lifecycle.state.changed',
        projectId: 'project-123',
        timestamp: Date.now(),
        data: { currentStage: 'execute' },
      };

      emitMessage(service, update);

      expect(handler1).toHaveBeenCalledWith(update);
      expect(handler2).toHaveBeenCalledWith(update);

      // Unregister first handler
      unsubscribe1();

      emitMessage(service, update);

      expect(handler1).toHaveBeenCalledTimes(1); // Called once before unregister
      expect(handler2).toHaveBeenCalledTimes(2); // Called twice
    });

    it('should register and unregister connection handlers', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      const unsubscribe1 = service.onConnectionChange(handler1);
      service.onConnectionChange(handler2);

      service.connect('project-123');
      openSocket(service);

      expect(handler1).toHaveBeenCalledWith(true);
      expect(handler2).toHaveBeenCalledWith(true);

      // Unregister first handler
      unsubscribe1();

      emitClose(service, 1000, 'Normal closure');

      expect(handler1).toHaveBeenCalledTimes(1); // Called once before unregister
      expect(handler2).toHaveBeenNthCalledWith(1, true);
      expect(handler2).toHaveBeenNthCalledWith(2, false);
    });
  });
});

describe('useLifecycleWebSocket Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    
    // Mock React hooks
    const mockState = { isConnected: false, lastUpdate: null };
    mockReact.useState.mockReturnValue([mockState.isConnected, vi.fn()]);
    mockReact.useState.mockReturnValue([mockState.lastUpdate, vi.fn()]);
    mockReact.useEffect.mockImplementation((fn, deps) => fn());
  });

  it('should connect and disconnect based on projectId', () => {
    const mockSetIsConnected = vi.fn();
    const mockSetLastUpdate = vi.fn();

    mockReact.useState
      .mockReturnValueOnce([false, mockSetIsConnected])
      .mockReturnValueOnce([null, mockSetLastUpdate]);

    const hookResult = useLifecycleWebSocket('project-123');

    expect(hookResult.connect).toBeDefined();
    expect(hookResult.disconnect).toBeDefined();
    expect(hookResult.isConnected).toBeDefined();
    expect(hookResult.lastUpdate).toBeDefined();
  });

  it('should handle empty projectId', () => {
    mockReact.useState.mockReturnValue([false, vi.fn()]);
    mockReact.useState.mockReturnValue([null, vi.fn()]);

    const hookResult = useLifecycleWebSocket('');

    // Should not attempt connection with empty projectId
    expect(mockReact.useEffect).toHaveBeenCalled();
  });
});

describe('Singleton Instance', () => {
  it('should export singleton service instance', () => {
    expect(lifecycleWebSocketService).toBeInstanceOf(LifecycleWebSocketService);
  });

  it('should return same instance on multiple imports', () => {
    const service1 = lifecycleWebSocketService;
    const service2 = lifecycleWebSocketService;

    expect(service1).toBe(service2);
  });
});
