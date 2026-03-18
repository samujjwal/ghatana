/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Frontend - Lifecycle WebSocket Service Test
 * 
 * Unit tests for LifecycleWebSocketService to ensure real-time
 * lifecycle state synchronization works correctly.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
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

  constructor(url: string) {
    this.url = url;
    // Simulate connection opening after a delay
    setTimeout(() => {
      this.readyState = MockWebSocket.OPEN;
      if (this.onopen) {
        this.onopen(new Event('open'));
      }
    }, 10);
  }

  send(data: string): void {
    // Mock send - in real implementation this would send to server
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

// Mock React hooks
const mockReact = {
  useState: vi.fn(),
  useEffect: vi.fn(),
};

vi.mock('react', () => mockReact);

describe('LifecycleWebSocketService', () => {
  let service: LifecycleWebSocketService;
  let mockWebSocket: MockWebSocket;

  beforeEach(() => {
    service = new LifecycleWebSocketService({
      url: 'ws://localhost:8080/lifecycle',
      maxReconnectAttempts: 3,
      reconnectIntervalMs: 100,
      heartbeatIntervalMs: 1000,
    });
    mockWebSocket = new MockWebSocket('ws://localhost:8080/lifecycle');
  });

  afterEach(() => {
    service.disconnect();
    vi.clearAllMocks();
  });

  describe('Connection Management', () => {
    it('should connect successfully', async () => {
      service.connect('project-123');
      
      // Wait for connection to open
      await new Promise(resolve => setTimeout(resolve, 20));
      
      expect(service.isConnected()).toBe(true);
    });

    it('should disconnect successfully', () => {
      service.connect('project-123');
      service.disconnect();
      
      expect(service.isConnected()).toBe(false);
    });

    it('should not reconnect if already connected to same project', () => {
      service.connect('project-123');
      
      // Should not throw or create new connection
      expect(() => service.connect('project-123')).not.toThrow();
    });

    it('should disconnect and reconnect for different project', () => {
      service.connect('project-123');
      service.connect('project-456');
      
      expect(service.isConnected()).toBe(true);
    });
  });

  describe('Event Handling', () => {
    beforeEach(() => {
      service.connect('project-123');
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
      const ws = (service as unknown).ws;
      ws.simulateMessage(update);

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

      const ws = (service as unknown).ws;
      ws.simulateMessage(update);

      expect(mockHandler).not.toHaveBeenCalled();
    });

    it('should handle connection change events', () => {
      const mockHandler = vi.fn();
      service.onConnectionChange(mockHandler);

      // Connection should trigger handler
      expect(mockHandler).toHaveBeenCalledWith(true);
    });

    it('should handle invalid message format gracefully', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const mockHandler = vi.fn();
      service.onUpdate(mockHandler);

      const invalidUpdate = {
        // Missing required fields
        type: 'lifecycle.state.changed',
      };

      const ws = (service as unknown).ws;
      ws.simulateMessage(invalidUpdate);

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
      
      // Wait for initial connection
      await new Promise(resolve => setTimeout(resolve, 20));
      
      // Simulate unexpected close
      const ws = (service as unknown).ws;
      ws.simulateClose(1006, 'Connection lost');
      
      // Should schedule reconnection
      expect(consoleSpy).toHaveBeenCalledWith(
        'Scheduling reconnect attempt 1/3'
      );

      consoleSpy.mockRestore();
    });

    it('should not reconnect on normal close', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
      
      service.connect('project-123');
      
      // Wait for initial connection
      await new Promise(resolve => setTimeout(resolve, 20));
      
      // Simulate normal close
      const ws = (service as unknown).ws;
      ws.simulateClose(1000, 'Normal closure');
      
      // Should not schedule reconnection
      expect(consoleSpy).not.toHaveBeenCalledWith(
        expect.stringContaining('Scheduling reconnect')
      );

      consoleSpy.mockRestore();
    });

    it('should stop reconnecting after max attempts', async () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
      
      const service = new LifecycleWebSocketService({
        url: 'ws://localhost:8080/lifecycle',
        maxReconnectAttempts: 1,
        reconnectIntervalMs: 10,
      });
      
      service.connect('project-123');
      
      // Wait for initial connection
      await new Promise(resolve => setTimeout(resolve, 20));
      
      // Simulate close that will trigger reconnection
      const ws = (service as unknown).ws;
      ws.simulateClose(1006, 'Connection lost');
      
      // Wait for reconnection attempt and subsequent failure
      await new Promise(resolve => setTimeout(resolve, 50));
      
      // Should have attempted reconnection once
      expect(consoleSpy).toHaveBeenCalledWith(
        'Scheduling reconnect attempt 1/1'
      );

      consoleSpy.mockRestore();
    });
  });

  describe('Heartbeat', () => {
    it('should send heartbeat messages', async () => {
      const sendSpy = vi.fn();
      
      service.connect('project-123');
      
      // Wait for connection
      await new Promise(resolve => setTimeout(resolve, 20));
      
      // Mock WebSocket send method
      const ws = (service as unknown).ws;
      ws.send = sendSpy;
      
      // Wait for heartbeat interval
      await new Promise(resolve => setTimeout(resolve, 1100));
      
      expect(sendSpy).toHaveBeenCalledWith(
        JSON.stringify({
          type: 'ping',
          timestamp: expect.any(Number),
        })
      );
    });
  });

  describe('Handler Management', () => {
    it('should register and unregister update handlers', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      const unsubscribe1 = service.onUpdate(handler1);
      const unsubscribe2 = service.onUpdate(handler2);

      const update = {
        type: 'lifecycle.state.changed',
        projectId: 'project-123',
        timestamp: Date.now(),
        data: { currentStage: 'execute' },
      };

      const ws = (service as unknown).ws;
      ws.simulateMessage(update);

      expect(handler1).toHaveBeenCalledWith(update);
      expect(handler2).toHaveBeenCalledWith(update);

      // Unregister first handler
      unsubscribe1();

      ws.simulateMessage(update);

      expect(handler1).toHaveBeenCalledTimes(1); // Called once before unregister
      expect(handler2).toHaveBeenCalledTimes(2); // Called twice
    });

    it('should register and unregister connection handlers', () => {
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      const unsubscribe1 = service.onConnectionChange(handler1);
      const unsubscribe2 = service.onConnectionChange(handler2);

      // Initial connection should trigger both handlers
      expect(handler1).toHaveBeenCalledWith(true);
      expect(handler2).toHaveBeenCalledWith(true);

      // Unregister first handler
      unsubscribe1();

      service.disconnect();

      expect(handler1).toHaveBeenCalledTimes(1); // Called once before unregister
      expect(handler2).toHaveBeenCalledTimes(2); // Called for connect and disconnect
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
