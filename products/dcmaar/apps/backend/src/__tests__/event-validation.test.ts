/**
 * Event Validation Tests
 *
 * Tests for Zod event validation schemas for WebSocket events.
 * Validates all 6 event types and batch processing.
 *
 * Test Coverage:
 * - Valid event schemas
 * - Invalid event handling
 * - Event type discrimination
 * - Batch validation
 * - Message envelope validation
 * - Type safety
 *
 * @test EventType validation schemas
 */

import { describe, it, expect } from 'vitest';
import {
  EventType,
  eventValidationSchema,
  batchEventsSchema,
  identifyMessageSchema,
  validateEvent,
  validateEventBatch,
} from '../types/events';

describe('Event Validation Schemas', () => {
  describe('EventType enum', () => {
    it('should have all event types defined', () => {
      expect(EventType.WINDOW_FOCUS).toBe('WINDOW_FOCUS');
      expect(EventType.PROCESS_START).toBe('PROCESS_START');
      expect(EventType.PROCESS_END).toBe('PROCESS_END');
      expect(EventType.IDLE_CHANGED).toBe('IDLE_CHANGED');
      expect(EventType.SESSION_START).toBe('SESSION_START');
      expect(EventType.SESSION_END).toBe('SESSION_END');
    });
  });

  describe('Window Focus Event Validation', () => {
    it('should validate correct window focus event', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        windowTitle: 'Google Chrome',
        processName: 'chrome',
        processPath: '/Applications/Google Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject window focus event with invalid type', () => {
      const event = {
        type: 'INVALID_TYPE' as any,
        timestamp: Date.now(),
        windowTitle: 'Google Chrome',
        processName: 'chrome',
        processPath: '/Applications/Google Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should accept window focus event with optional sessionId', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        sessionId: '550e8400-e29b-41d4-a716-446655440000',
        windowTitle: 'VS Code',
        processName: 'code',
        processPath: '/Applications/Visual Studio Code.app',
        appCategory: 'development',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });
  });

  describe('Process Start Event Validation', () => {
    it('should validate correct process start event', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        processName: 'spotify',
        processPath: '/Applications/Spotify.app',
        appCategory: 'entertainment',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject process start event without processName', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        // Missing processName
        processPath: '/Applications/Spotify.app',
        appCategory: 'entertainment',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should reject process start event without processPath', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        processName: 'spotify',
        // Missing processPath
        appCategory: 'entertainment',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Process End Event Validation', () => {
    it('should validate correct process end event', () => {
      const event = {
        type: 'PROCESS_END' as const,
        timestamp: Date.now(),
        processName: 'firefox',
        processPath: '/Applications/Firefox.app',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject process end event without processName', () => {
      const event = {
        type: 'PROCESS_END' as const,
        timestamp: Date.now(),
        // Missing processName
        processPath: '/Applications/Firefox.app',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Idle Changed Event Validation', () => {
    it('should validate idle changed event - idle', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        isIdle: true,
        idleSeconds: 300,
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should validate idle changed event - active', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        isIdle: false,
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject idle changed event without isIdle', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        idleSeconds: 300,
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Session Start Event Validation', () => {
    it('should validate correct session start event', () => {
      const event = {
        type: 'SESSION_START' as const,
        timestamp: Date.now(),
        sessionId: '550e8400-e29b-41d4-a716-446655440000',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject session start event without sessionId', () => {
      const event = {
        type: 'SESSION_START' as const,
        timestamp: Date.now(),
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should reject session start event with invalid sessionId', () => {
      const event = {
        type: 'SESSION_START' as const,
        timestamp: Date.now(),
        sessionId: 'not-a-uuid',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Session End Event Validation', () => {
    it('should validate correct session end event', () => {
      const event = {
        type: 'SESSION_END' as const,
        timestamp: Date.now(),
        sessionId: '550e8400-e29b-41d4-a716-446655440001',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject session end event without sessionId', () => {
      const event = {
        type: 'SESSION_END' as const,
        timestamp: Date.now(),
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });
  });

  describe('Event Batch Validation', () => {
    it('should validate batch of valid events', () => {
      const batch = {
        type: 'EVENTS' as const,
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: Date.now(),
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
            appCategory: 'browser',
          },
          {
            type: 'PROCESS_START' as const,
            timestamp: Date.now(),
            processName: 'slack',
            processPath: '/Applications/Slack.app',
            appCategory: 'communication',
          },
        ],
      };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should reject batch with more than 1000 events', () => {
      const events = Array.from({ length: 1001 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      }));

      const batch = { type: 'EVENTS' as const, events };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });

    it('should reject batch with empty events', () => {
      const batch = { type: 'EVENTS' as const, events: [] };

      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });

    it('should reject batch without type field', () => {
      const batch = {
        events: [
          {
            type: 'WINDOW_FOCUS' as const,
            timestamp: Date.now(),
            windowTitle: 'Chrome',
            processName: 'chrome',
            processPath: '/Applications/Chrome.app',
            appCategory: 'browser',
          },
        ],
      };

      const result = validateEventBatch(batch as any);
      expect(result.valid).toBe(false);
    });
  });

  describe('IDENTIFY Message Validation', () => {
    it('should validate correct IDENTIFY message', () => {
      const message = {
        type: 'IDENTIFY' as const,
        token: 'jwt-token-here',
      };

      const result = identifyMessageSchema.parse(message);
      expect(result.type).toBe('IDENTIFY');
      expect(result.token).toBe('jwt-token-here');
    });

    it('should reject IDENTIFY message without token', () => {
      const message = {
        type: 'IDENTIFY' as const,
      };

      const result = identifyMessageSchema.safeParse(message as any);
      expect(result.success).toBe(false);
    });

    it('should reject IDENTIFY message with wrong type', () => {
      const message = {
        type: 'AUTH' as const,
        token: 'jwt-token',
      };

      const result = identifyMessageSchema.safeParse(message as any);
      expect(result.success).toBe(false);
    });
  });

  describe('Helper Functions', () => {
    describe('validateEvent()', () => {
      it('should validate valid event', () => {
        const event = {
          type: 'WINDOW_FOCUS' as const,
          timestamp: Date.now(),
          windowTitle: 'Chrome',
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
          appCategory: 'browser',
        };

        const result = validateEvent(event);
        expect(result.valid).toBe(true);
      });

      it('should reject invalid event', () => {
        const event = {
          type: 'INVALID_TYPE',
          timestamp: Date.now(),
        };

        const result = validateEvent(event as any);
        expect(result.valid).toBe(false);
        expect(result.error).toBeDefined();
      });

      it('should provide error message on validation failure', () => {
        const event = {
          type: 'WINDOW_FOCUS' as const,
          timestamp: 'invalid' as any,
          // Invalid timestamp type
        };

        const result = validateEvent(event);
        expect(result.valid).toBe(false);
        expect(result.error).toBeTruthy();
      });
    });

    describe('validateEventBatch()', () => {
      it('should validate batch of events', () => {
        const batch = {
          type: 'EVENTS' as const,
          events: [
            {
              type: 'WINDOW_FOCUS' as const,
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

      it('should return error for invalid batch', () => {
        const batch = { type: 'EVENTS' as const, events: 'not-an-array' };

        const result = validateEventBatch(batch as any);
        expect(result.valid).toBe(false);
        expect(result.error).toBeDefined();
      });

      it('should validate multiple events in batch', () => {
        const batch = {
          type: 'EVENTS' as const,
          events: [
            {
              type: 'WINDOW_FOCUS' as const,
              timestamp: Date.now(),
              windowTitle: 'Chrome',
              processName: 'chrome',
              processPath: '/Applications/Chrome.app',
              appCategory: 'browser',
            },
            {
              type: 'PROCESS_START' as const,
              timestamp: Date.now() + 1000,
              processName: 'slack',
              processPath: '/Applications/Slack.app',
              appCategory: 'communication',
            },
          ],
        };

        const result = validateEventBatch(batch);
        expect(result.valid).toBe(true);
      });
    });
  });

  describe('Event Type Discrimination', () => {
    it('should discriminate between different event types', () => {
      const events = [
        {
          type: 'WINDOW_FOCUS' as const,
          timestamp: Date.now(),
          windowTitle: 'Chrome',
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
          appCategory: 'browser',
        },
        {
          type: 'IDLE_CHANGED' as const,
          timestamp: Date.now(),
          isIdle: true,
          idleSeconds: 60,
        },
      ];

      events.forEach((event) => {
        const result = validateEvent(event);
        expect(result.valid).toBe(true);
      });
    });

    it('should correctly parse window focus event', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        windowTitle: 'VS Code',
        processName: 'code',
        processPath: '/Applications/Code.app',
      };

      const parsed = eventValidationSchema.parse(event);
      expect(parsed.type).toBe('WINDOW_FOCUS');
    });

    it('should correctly parse process start event', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        processName: 'node',
        processPath: '/usr/local/bin/node',
      };

      const parsed = eventValidationSchema.parse(event);
      expect(parsed.type).toBe('PROCESS_START');
    });

    it('should correctly parse idle changed event', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        isIdle: false,
      };

      const parsed = eventValidationSchema.parse(event);
      expect(parsed.type).toBe('IDLE_CHANGED');
      expect((parsed as any).isIdle).toBe(false);
    });
  });

  describe('Timestamp Validation', () => {
    it('should accept valid timestamps', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject non-positive timestamps', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: 0,
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should reject negative timestamps', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: -1000,
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should reject non-integer timestamps', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + 0.5,
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(false);
    });

    it('should reject string timestamps', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: 'not-a-timestamp',
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });
  });
});
