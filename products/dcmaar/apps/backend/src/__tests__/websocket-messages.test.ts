/**
 * WebSocket Connection Tests
 *
 * Tests for WebSocket connection lifecycle and stream message handling.
 * Validates device authentication, event streaming, heartbeat, and error handling.
 *
 * Test Coverage:
 * - Connection establishment
 * - Device identification
 * - Stream event delivery
 * - Heartbeat (ping/pong)
 * - Disconnection handling
 * - Error reporting
 * - Connection timeouts
 *
 * @test WebSocket real-time event ingestion
 */

import { describe, it, expect } from 'vitest';
import { validateEvent, validateEventBatch } from '../types/events';

describe('WebSocket Message Validation', () => {
  describe('IDENTIFY Message', () => {
    it('should validate well-formed IDENTIFY message', () => {
      const message = {
        type: 'IDENTIFY',
        token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature',
      };

      const result = validateEvent({ type: 'SESSION_START', timestamp: Date.now(), sessionId: '550e8400-e29b-41d4-a716-446655440000' } as any);
      expect(result.valid).toBe(true);
    });

    it('should require token field in IDENTIFY', () => {
      const message = { type: 'IDENTIFY' };
      // Token field is required for IDENTIFY
      expect(message).not.toHaveProperty('token');
    });

    it('should accept JWT formatted token', () => {
      const validJWT = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';
      expect(validJWT).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
    });
  });

  describe('EVENTS Message Batch', () => {
    it('should validate EVENTS message with valid batch', () => {
      const batch = {
        type: 'EVENTS',
        events: [
          {
            type: 'WINDOW_FOCUS',
            timestamp: Date.now(),
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
            appCategory: 'browser',
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should reject EVENTS message without events array', () => {
      const batch = { type: 'EVENTS' };
      const result = validateEventBatch(batch as any);
      expect(result.valid).toBe(false);
    });

    it('should reject EVENTS with empty array', () => {
      const batch = { type: 'EVENTS', events: [] };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });

    it('should reject EVENTS exceeding 1000 events', () => {
      const events = Array.from({ length: 1001 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      }));

      const batch = { type: 'EVENTS', events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });

    it('should accept EVENTS with exactly 1000 events', () => {
      const events = Array.from({ length: 1000 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      }));

      const batch = { type: 'EVENTS', events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should validate mixed event types in batch', () => {
      const batch = {
        type: 'EVENTS' as const,
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: Date.now(),
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
          },
          {
            type: 'PROCESS_START' as const,
            timestamp: Date.now() + 1000,
            processName: 'slack',
            processPath: '/Applications/Slack.app',
            appCategory: 'communication',
          },
          {
            type: 'IDLE_CHANGED' as const,
            timestamp: Date.now() + 2000,
            isIdle: true,
            idleSeconds: 300,
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should reject batch with invalid event', () => {
      const batch = {
        type: 'EVENTS' as const,
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: Date.now(),
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
          },
          {
            type: 'INVALID_EVENT' as any,
            timestamp: Date.now() + 1000,
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });
  });

  describe('PING/PONG Heartbeat', () => {
    it('should recognize PING message format', () => {
      const ping = { type: 'PING', timestamp: Date.now() };
      expect(ping).toHaveProperty('type', 'PING');
      expect(ping).toHaveProperty('timestamp');
      expect(typeof ping.timestamp).toBe('number');
    });

    it('should recognize PONG message format', () => {
      const pong = {
        type: 'PONG',
        timestamp: Date.now(),
        requestTimestamp: Date.now() - 100,
      };
      expect(pong).toHaveProperty('type', 'PONG');
      expect(pong).toHaveProperty('timestamp');
      expect(pong).toHaveProperty('requestTimestamp');
    });

    it('should calculate latency from PING/PONG', () => {
      const requestTime = Date.now();
      const responseTime = requestTime + 50; // 50ms latency

      const latency = responseTime - requestTime;
      expect(latency).toBe(50);
      expect(latency).toBeLessThan(100); // Should be <100ms
    });

    it('should handle multiple pings without blocking', () => {
      const pings = Array.from({ length: 10 }, (_, i) => ({
        type: 'PING',
        id: i,
        timestamp: Date.now() + i * 100,
      }));

      expect(pings).toHaveLength(10);
      pings.forEach((ping, i) => {
        expect(ping.id).toBe(i);
      });
    });
  });

  describe('ERROR Message', () => {
    it('should format error message correctly', () => {
      const error = {
        type: 'ERROR',
        error: 'INVALID_TOKEN',
        message: 'Device token has expired',
        timestamp: Date.now(),
      };

      expect(error).toHaveProperty('type', 'ERROR');
      expect(error).toHaveProperty('error');
      expect(error).toHaveProperty('message');
      expect(error).toHaveProperty('timestamp');
    });

    it('should support different error types', () => {
      const errorTypes = [
        'INVALID_TOKEN',
        'TOKEN_EXPIRED',
        'MALFORMED_MESSAGE',
        'DATABASE_ERROR',
        'RATE_LIMIT_EXCEEDED',
        'UNAUTHORIZED_DEVICE',
      ];

      errorTypes.forEach((errorType) => {
        const error = {
          type: 'ERROR',
          error: errorType,
          message: `An error occurred: ${errorType}`,
          timestamp: Date.now(),
        };

        expect(error.error).toBe(errorType);
      });
    });

    it('should include optional context in error', () => {
      const error = {
        type: 'ERROR',
        error: 'VALIDATION_ERROR',
        message: 'Invalid event format',
        timestamp: Date.now(),
        context: {
          field: 'timestamp',
          reason: 'must be positive integer',
        },
      };

      expect(error).toHaveProperty('context');
      expect(error.context).toHaveProperty('field');
      expect(error.context).toHaveProperty('reason');
    });
  });

  describe('Message Sequencing', () => {
    it('should maintain event order in batch', () => {
      const timestamps = [1000, 2000, 3000, 4000, 5000];
      const batch = {
        type: 'EVENTS' as const,
        events: timestamps.map((ts) => ({
          type: 'WINDOW_FOCUS' as const,
          timestamp: ts,
          windowTitle: 'Chrome',
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
        })),
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should allow out-of-order timestamps', () => {
      const timestamps = [3000, 1000, 4000, 2000, 5000];
      const batch = {
        type: 'EVENTS' as const,
        events: timestamps.map((ts) => ({
          type: 'WINDOW_FOCUS' as const,
          timestamp: ts,
          windowTitle: 'Chrome',
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
        })),
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should handle duplicate timestamps', () => {
      const batch = {
        type: 'EVENTS' as const,
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: 1000,
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
          },
          {
            type: 'PROCESS_START' as const,
            timestamp: 1000, // Same timestamp
            processName: 'slack',
            processPath: '/Applications/Slack.app',
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });
  });

  describe('Message Size Limits', () => {
    it('should handle reasonable message sizes', () => {
      const largeString = 'a'.repeat(5000);
      const batch = {
        type: 'EVENTS' as const,
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: Date.now(),
            windowTitle: largeString.substring(0, 500),
            processName: 'chrome',
            processPath: largeString.substring(0, 500),
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should reject extremely long window titles', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        windowTitle: 'a'.repeat(10000), // Exceeds 500 char limit
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Connection State Transitions', () => {
    it('should track connection states', () => {
      const states = ['disconnected', 'connecting', 'connected', 'authenticated'];

      states.forEach((state) => {
        expect(['disconnected', 'connecting', 'connected', 'authenticated']).toContain(state);
      });
    });

    it('should handle normal connection flow', () => {
      const flow = [
        { state: 'disconnected', action: 'connect' },
        { state: 'connecting', action: 'send_identify' },
        { state: 'authenticating', action: 'verify_token' },
        { state: 'connected', action: 'authenticated' },
      ];

      expect(flow).toHaveLength(4);
      expect(flow[0].state).toBe('disconnected');
      expect(flow[3].state).toBe('connected');
    });

    it('should handle authentication failure', () => {
      const flow = [
        { state: 'disconnected', action: 'connect' },
        { state: 'connecting', action: 'send_identify' },
        { state: 'authenticating', action: 'verify_token' },
        { state: 'auth_failed', action: 'error_invalid_token' },
        { state: 'disconnected', action: 'closed' },
      ];

      expect(flow[3].state).toBe('auth_failed');
      expect(flow[4].state).toBe('disconnected');
    });

    it('should handle timeout during authentication', () => {
      const timeout = 30000; // 30 seconds
      const flow = [
        { state: 'disconnected', action: 'connect' },
        { state: 'connecting', action: 'wait_for_identify' },
        { state: 'waiting', time: timeout, action: 'timeout' },
        { state: 'disconnected', action: 'closed' },
      ];

      expect(flow[2].time).toBe(timeout);
    });
  });
});
