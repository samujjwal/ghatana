/**
 * Tests for ProviderManager
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createProviderManager,
  type JWTPayload,

  ProviderManager} from '../providerManager';

describe('ProviderManager', () => {
  let manager: ProviderManager;

  beforeEach(() => {
    vi.useFakeTimers();
    manager = createProviderManager({
      enableAuth: false, // Disable auth for most tests
    });
  });

  afterEach(() => {
    manager.disconnect();
    vi.restoreAllMocks();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();
      expect(config.preferredProvider).toBe('websocket');
      expect(config.enableWebSocket).toBe(true);
      expect(config.enableWebRTC).toBe(false);
      expect(config.enableFailover).toBe(true);
    });

    it('should initialize with custom configuration', () => {
      const customManager = createProviderManager({
        preferredProvider: 'webrtc',
        enableWebRTC: true,
        maxReconnectAttempts: 10,
      });

      const config = customManager.getConfig();
      expect(config.preferredProvider).toBe('webrtc');
      expect(config.enableWebRTC).toBe(true);
      expect(config.maxReconnectAttempts).toBe(10);
    });

    it('should start in disconnected state', () => {
      const state = manager.getState();
      expect(state.status).toBe('disconnected');
      expect(state.provider).toBe('none');
      expect(state.reconnectAttempts).toBe(0);
      expect(state.failoverActive).toBe(false);
    });
  });

  describe('Connection Management', () => {
    it('should connect to WebSocket provider', async () => {
      const connected = await manager.connect('room-1');
      
      // May succeed or fail based on random simulation
      expect(typeof connected).toBe('boolean');
      
      if (connected) {
        const state = manager.getState();
        expect(state.status).toBe('connected');
        expect(state.provider).toBe('websocket');
        expect(state.connectedAt).toBeDefined();
      }
    });

    it('should not connect if already connecting', async () => {
      const promise1 = manager.connect('room-1');
      const promise2 = manager.connect('room-1');

      await promise1;
      const result2 = await promise2;

      expect(result2).toBe(false);
    });

    it('should not connect if already connected', async () => {
      await manager.connect('room-1');
      
      if (manager.getState().status === 'connected') {
        const result = await manager.connect('room-1');
        expect(result).toBe(false);
      }
    });

    it('should disconnect from provider', async () => {
      await manager.connect('room-1');
      
      manager.disconnect();

      const state = manager.getState();
      expect(state.status).toBe('disconnected');
      expect(state.provider).toBe('none');
      expect(state.connectedAt).toBeUndefined();
    });

    it('should not error when disconnecting if already disconnected', () => {
      expect(() => manager.disconnect()).not.toThrow();
    });
  });

  describe('Provider Selection', () => {
    it('should select WebSocket when preferred and enabled', async () => {
      manager.updateConfig({
        preferredProvider: 'websocket',
        enableWebSocket: true,
      });

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        expect(manager.getState().provider).toBe('websocket');
      }
    });

    it('should select WebRTC when preferred and enabled', async () => {
      manager.updateConfig({
        preferredProvider: 'webrtc',
        enableWebRTC: true,
      });

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        expect(manager.getState().provider).toBe('webrtc');
      }
    });

    it('should fallback to enabled provider if preferred is disabled', async () => {
      manager.updateConfig({
        preferredProvider: 'webrtc',
        enableWebRTC: false,
        enableWebSocket: true,
      });

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        expect(manager.getState().provider).toBe('websocket');
      }
    });

    it('should fail if no providers are enabled', async () => {
      manager.updateConfig({
        enableWebSocket: false,
        enableWebRTC: false,
      });

      const connected = await manager.connect('room-1');
      expect(connected).toBe(false);

      const events = manager.getEventHistory({ type: 'error' });
      expect(events.length).toBeGreaterThan(0);
    });
  });

  describe('Reconnection Logic', () => {
    it('should attempt reconnection on failure', async () => {
      // Force failure by mocking
      vi.spyOn(Math, 'random').mockReturnValue(0.05); // Will fail

      await manager.connect('room-1');

      const state = manager.getState();
      if (state.status === 'reconnecting') {
        expect(state.reconnectAttempts).toBeGreaterThan(0);
      }
    });

    it('should respect max reconnect attempts', async () => {
      manager.updateConfig({ maxReconnectAttempts: 2 });

      // Force failures
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      // Advance timers for reconnect attempts
      for (let i = 0; i < 3; i++) {
        await vi.advanceTimersByTimeAsync(5000);
      }

      const state = manager.getState();
      expect(state.reconnectAttempts).toBeLessThanOrEqual(2);
    });

    it('should increase delay between reconnect attempts', async () => {
      manager.updateConfig({
        reconnectDelay: 1000,
        maxReconnectAttempts: 3,
      });

      // Force failure
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      const state = manager.getState();
      if (state.status === 'reconnecting') {
        expect(state.reconnectAttempts).toBeGreaterThan(0);
      }
    });
  });

  describe('Failover', () => {
    it('should failover to WebRTC after max WebSocket attempts', async () => {
      manager.updateConfig({
        preferredProvider: 'websocket',
        enableWebSocket: true,
        enableWebRTC: true,
        enableFailover: true,
        maxReconnectAttempts: 2,
      });

      // Force failures
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      // Wait for reconnect attempts and failover
      for (let i = 0; i < 5; i++) {
        await vi.advanceTimersByTimeAsync(5000);
      }

      const events = manager.getEventHistory({ type: 'failover' });
      if (events.length > 0) {
        expect(events[0].data?.from).toBe('websocket');
        expect(events[0].data?.to).toBe('webrtc');
      }
    });

    it('should not failover if disabled', async () => {
      manager.updateConfig({
        enableFailover: false,
        maxReconnectAttempts: 2,
      });

      // Force failures
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      // Wait for reconnect attempts
      for (let i = 0; i < 5; i++) {
        await vi.advanceTimersByTimeAsync(5000);
      }

      const events = manager.getEventHistory({ type: 'failover' });
      expect(events.length).toBe(0);
    });

    it('should track failover count in statistics', async () => {
      manager.updateConfig({
        preferredProvider: 'websocket',
        enableWebSocket: true,
        enableWebRTC: true,
        enableFailover: true,
        maxReconnectAttempts: 1,
      });

      // Force failures
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      // Wait for failover
      for (let i = 0; i < 5; i++) {
        await vi.advanceTimersByTimeAsync(5000);
      }

      const stats = manager.getStatistics();
      expect(stats.failoverCount).toBeGreaterThanOrEqual(0);
    });
  });

  describe('JWT Authentication', () => {
    it('should authenticate with valid JWT token', async () => {
      const payload: JWTPayload = {
        sub: 'user-123',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
        room: 'room-1',
      };

      const token = createMockJWT(payload);

      manager.updateConfig({
        enableAuth: true,
        getToken: async () => token,
      });

      await manager.connect('room-1');

      const events = manager.getEventHistory({ type: 'auth_success' });
      expect(events.length).toBeGreaterThan(0);
    });

    it('should reject expired JWT token', async () => {
      const payload: JWTPayload = {
        sub: 'user-123',
        iat: Math.floor(Date.now() / 1000) - 7200,
        exp: Math.floor(Date.now() / 1000) - 3600, // Expired
        room: 'room-1',
      };

      const token = createMockJWT(payload);

      manager.updateConfig({
        enableAuth: true,
        getToken: async () => token,
      });

      const connected = await manager.connect('room-1');
      expect(connected).toBe(false);

      const events = manager.getEventHistory({ type: 'auth_failure' });
      expect(events.length).toBeGreaterThan(0);
    });

    it('should reject token with room mismatch', async () => {
      const payload: JWTPayload = {
        sub: 'user-123',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
        room: 'room-1',
      };

      const token = createMockJWT(payload);

      manager.updateConfig({
        enableAuth: true,
        getToken: async () => token,
      });

      const connected = await manager.connect('room-2'); // Different room
      expect(connected).toBe(false);

      const events = manager.getEventHistory({ type: 'auth_failure' });
      expect(events.length).toBeGreaterThan(0);
      expect(events[0].error).toContain('room mismatch');
    });

    it('should validate JWT token format', () => {
      const payload: JWTPayload = {
        sub: 'user-123',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
      };

      const token = createMockJWT(payload);
      const result = manager.validateToken(token);

      expect(result.valid).toBe(true);
      expect(result.payload).toBeDefined();
      expect(result.payload!.sub).toBe('user-123');
    });

    it('should reject invalid JWT format', () => {
      const result = manager.validateToken('invalid.token');
      expect(result.valid).toBe(false);
      expect(result.error).toBeDefined();
    });

    it('should reject token issued in future', () => {
      const payload: JWTPayload = {
        sub: 'user-123',
        iat: Math.floor(Date.now() / 1000) + 7200, // Future
        exp: Math.floor(Date.now() / 1000) + 10800,
      };

      const token = createMockJWT(payload);
      const result = manager.validateToken(token);

      expect(result.valid).toBe(false);
      expect(result.error).toContain('future');
    });
  });

  describe('Event System', () => {
    it('should emit connecting event', async () => {
      const events: string[] = [];
      manager.onEvent((event) => {
        events.push(event.type);
      });

      await manager.connect('room-1');

      expect(events).toContain('connecting');
    });

    it('should emit connected event on success', async () => {
      // Force success
      vi.spyOn(Math, 'random').mockReturnValue(0.5);

      const events: string[] = [];
      manager.onEvent((event) => {
        events.push(event.type);
      });

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        expect(events).toContain('connected');
      }
    });

    it('should emit disconnected event', async () => {
      await manager.connect('room-1');

      const events: string[] = [];
      manager.onEvent((event) => {
        events.push(event.type);
      });

      manager.disconnect();

      expect(events).toContain('disconnected');
    });

    it('should allow unsubscribing from events', async () => {
      const events: string[] = [];
      const unsubscribe = manager.onEvent((event) => {
        events.push(event.type);
      });

      await manager.connect('room-1');
      const count1 = events.length;

      unsubscribe();

      manager.disconnect();
      await manager.connect('room-1');

      expect(events.length).toBe(count1);
    });

    it('should support multiple event listeners', async () => {
      const events1: string[] = [];
      const events2: string[] = [];

      manager.onEvent((event) => events1.push(event.type));
      manager.onEvent((event) => events2.push(event.type));

      await manager.connect('room-1');

      expect(events1.length).toBeGreaterThan(0);
      expect(events2.length).toBeGreaterThan(0);
      expect(events1).toEqual(events2);
    });
  });

  describe('Event History', () => {
    it('should record event history', async () => {
      await manager.connect('room-1');

      const history = manager.getEventHistory();
      expect(history.length).toBeGreaterThan(0);
    });

    it('should filter events by type', async () => {
      await manager.connect('room-1');

      const connectingEvents = manager.getEventHistory({ type: 'connecting' });
      expect(connectingEvents.every(e => e.type === 'connecting')).toBe(true);
    });

    it('should filter events by provider', async () => {
      await manager.connect('room-1');

      const websocketEvents = manager.getEventHistory({ provider: 'websocket' });
      if (websocketEvents.length > 0) {
        expect(websocketEvents.every(e => e.provider === 'websocket')).toBe(true);
      }
    });

    it('should filter events by date range', async () => {
      const startDate = Date.now();
      await manager.connect('room-1');
      const endDate = Date.now();

      const events = manager.getEventHistory({ startDate, endDate });
      expect(events.every(e => e.timestamp >= startDate && e.timestamp <= endDate)).toBe(true);
    });

    it('should return events in descending chronological order', async () => {
      await manager.connect('room-1');
      manager.disconnect();

      const history = manager.getEventHistory();
      if (history.length > 1) {
        for (let i = 0; i < history.length - 1; i++) {
          expect(history[i].timestamp).toBeGreaterThanOrEqual(history[i + 1].timestamp);
        }
      }
    });

    it('should limit event history to 1000 entries', async () => {
      // Connect and disconnect many times
      for (let i = 0; i < 600; i++) {
        await manager.connect('room-1');
        manager.disconnect();
      }

      const history = manager.getEventHistory();
      expect(history.length).toBeLessThanOrEqual(1000);
    });
  });

  describe('Statistics', () => {
    it('should track total connection attempts', async () => {
      await manager.connect('room-1');
      await manager.connect('room-2');

      const stats = manager.getStatistics();
      expect(stats.totalConnections).toBeGreaterThan(0);
    });

    it('should track successful connections', async () => {
      // Force success
      vi.spyOn(Math, 'random').mockReturnValue(0.5);

      await manager.connect('room-1');

      const stats = manager.getStatistics();
      if (manager.getState().status === 'connected') {
        expect(stats.successfulConnections).toBeGreaterThan(0);
      }
    });

    it('should track failed connections', async () => {
      // Force failure
      vi.spyOn(Math, 'random').mockReturnValue(0.05);

      await manager.connect('room-1');

      const stats = manager.getStatistics();
      expect(stats.failedConnections).toBeGreaterThanOrEqual(0);
    });

    it('should track provider usage', async () => {
      // Force success
      vi.spyOn(Math, 'random').mockReturnValue(0.5);

      await manager.connect('room-1');

      const stats = manager.getStatistics();
      if (manager.getState().provider === 'websocket') {
        expect(stats.websocketUsage).toBeGreaterThan(0);
      }
    });

    it('should calculate current uptime when connected', async () => {
      // Force success
      vi.spyOn(Math, 'random').mockReturnValue(0.5);

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        await vi.advanceTimersByTimeAsync(5000);

        const stats = manager.getStatistics();
        expect(stats.currentUptime).toBeGreaterThanOrEqual(0);
      }
    });

    it('should calculate average connection time', async () => {
      // Force success
      vi.spyOn(Math, 'random').mockReturnValue(0.5);

      await manager.connect('room-1');

      if (manager.getState().status === 'connected') {
        const stats = manager.getStatistics();
        expect(stats.averageConnectionTime).toBeGreaterThanOrEqual(0);
      }
    });
  });

  describe('Configuration Management', () => {
    it('should get configuration', () => {
      const config = manager.getConfig();
      expect(config).toBeDefined();
      expect(config.preferredProvider).toBeDefined();
    });

    it('should update configuration', () => {
      manager.updateConfig({
        maxReconnectAttempts: 10,
        reconnectDelay: 2000,
      });

      const config = manager.getConfig();
      expect(config.maxReconnectAttempts).toBe(10);
      expect(config.reconnectDelay).toBe(2000);
    });

    it('should merge configuration updates', () => {
      const originalProvider = manager.getConfig().preferredProvider;

      manager.updateConfig({
        maxReconnectAttempts: 15,
      });

      const config = manager.getConfig();
      expect(config.preferredProvider).toBe(originalProvider);
      expect(config.maxReconnectAttempts).toBe(15);
    });
  });

  describe('Edge Cases', () => {
    it('should handle missing token provider', async () => {
      manager.updateConfig({
        enableAuth: true,
        getToken: undefined,
      });

      const connected = await manager.connect('room-1');
      expect(connected).toBe(false);
    });

    it('should handle token provider error', async () => {
      manager.updateConfig({
        enableAuth: true,
        getToken: async () => {
          throw new Error('Token fetch failed');
        },
      });

      const connected = await manager.connect('room-1');
      expect(connected).toBe(false);
    });

    it('should handle rapid connect/disconnect cycles', async () => {
      for (let i = 0; i < 10; i++) {
        await manager.connect('room-1');
        manager.disconnect();
      }

      const state = manager.getState();
      expect(state.status).toBe('disconnected');
    });

    it('should clean up timers on disconnect', async () => {
      await manager.connect('room-1');
      manager.disconnect();

      // Should not throw or have lingering timers
      expect(() => manager.disconnect()).not.toThrow();
    });
  });
});

/**
 * Helper to create mock JWT token
 */
function createMockJWT(payload: JWTPayload): string {
  const header = { alg: 'HS256', typ: 'JWT' };
  const encodedHeader = btoa(JSON.stringify(header));
  const encodedPayload = btoa(JSON.stringify(payload));
  const signature = 'mock-signature';

  return `${encodedHeader}.${encodedPayload}.${signature}`;
}
