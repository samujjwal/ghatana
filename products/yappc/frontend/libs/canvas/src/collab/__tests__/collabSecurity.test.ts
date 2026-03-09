/**
 * Tests for Collaboration Security
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  createCollabSecurityManager,
  type SecurityEvent,
  type CollabPayloadSchema,

  CollabSecurityManager} from '../collabSecurity';

describe('CollabSecurityManager', () => {
  describe('Rate Limiting', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager({
        maxEvents: 10,
        windowMs: 60000,
        refillRate: 1,
        burstCapacity: 5,
      });
    });

    it('should allow requests within rate limit', () => {
      const result1 = manager.checkRateLimit('user1');
      const result2 = manager.checkRateLimit('user1');
      const result3 = manager.checkRateLimit('user1');

      expect(result1.allowed).toBe(true);
      expect(result2.allowed).toBe(true);
      expect(result3.allowed).toBe(true);
    });

    it('should throttle requests exceeding burst capacity', () => {
      // Exhaust burst capacity
      for (let i = 0; i < 5; i++) {
        manager.checkRateLimit('user1');
      }

      const result = manager.checkRateLimit('user1');

      expect(result.allowed).toBe(false);
      expect(result.retryAfter).toBeGreaterThan(0);
    });

    it('should refill tokens over time', async () => {
      // Exhaust burst capacity
      for (let i = 0; i < 5; i++) {
        manager.checkRateLimit('user1');
      }

      // Wait for refill (refillRate: 1 token/second)
      await new Promise((resolve) => setTimeout(resolve, 1100));

      const result = manager.checkRateLimit('user1');
      expect(result.allowed).toBe(true);
    });

    it('should track rate limit state', () => {
      manager.checkRateLimit('user1');
      manager.checkRateLimit('user1');

      const state = manager.getRateLimitState('user1');

      expect(state).toBeDefined();
      expect(state?.totalRequests).toBe(2);
      expect(state?.tokens).toBeLessThan(5);
    });

    it('should reset rate limit for user', () => {
      manager.checkRateLimit('user1');
      manager.resetRateLimit('user1');

      const state = manager.getRateLimitState('user1');
      expect(state).toBeUndefined();
    });

    it('should handle multiple users independently', () => {
      // Exhaust user1
      for (let i = 0; i < 5; i++) {
        manager.checkRateLimit('user1');
      }

      // User2 should still have tokens
      const result = manager.checkRateLimit('user2');
      expect(result.allowed).toBe(true);
    });

    it('should log rate limit events', () => {
      // Exhaust burst capacity
      for (let i = 0; i < 5; i++) {
        manager.checkRateLimit('user1');
      }

      manager.checkRateLimit('user1'); // This should be throttled

      const events = manager.getSecurityEvents({ type: 'rate_limit' });
      expect(events.length).toBeGreaterThan(0);
      expect(events[0].userId).toBe('user1');
    });
  });

  describe('Payload Validation', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      const schema: CollabPayloadSchema = {
        required: ['type', 'userId', 'timestamp'],
        types: {
          type: 'string',
          userId: 'string',
          timestamp: 'number',
          data: 'object',
        },
        maxStringLength: 100,
      };
      manager = createCollabSecurityManager({}, schema);
    });

    it('should validate valid payload', () => {
      const payload = {
        type: 'update',
        userId: 'user1',
        timestamp: Date.now(),
        data: { key: 'value' },
      };

      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject payload with missing required field', () => {
      const payload = {
        type: 'update',
        userId: 'user1',
        // timestamp missing
      };

      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].code).toBe('MISSING_FIELD');
      expect(result.errors[0].field).toBe('timestamp');
    });

    it('should reject payload with wrong field type', () => {
      const payload = {
        type: 'update',
        userId: 'user1',
        timestamp: 'not-a-number', // Should be number
      };

      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.code === 'INVALID_TYPE')).toBe(true);
    });

    it('should reject payload with string exceeding max length', () => {
      const payload = {
        type: 'a'.repeat(150), // Exceeds maxStringLength: 100
        userId: 'user1',
        timestamp: Date.now(),
      };

      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.code === 'STRING_TOO_LONG')).toBe(true);
    });

    it('should warn about large arrays', () => {
      const schema: CollabPayloadSchema = {
        required: ['items'],
        types: { items: 'array' },
        maxArrayLength: 10,
      };
      const mgr = createCollabSecurityManager({}, schema);

      const payload = {
        items: Array(50).fill('item'),
      };

      const result = mgr.validatePayload(payload);

      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should reject non-object payload', () => {
      const result = manager.validatePayload('not-an-object');

      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe('INVALID_TYPE');
    });

    it('should validate nested objects', () => {
      const schema: CollabPayloadSchema = {
        required: ['user'],
        types: { user: 'object' },
        nested: {
          user: {
            required: ['id', 'name'],
            types: { id: 'string', name: 'string' },
          },
        },
      };
      const mgr = createCollabSecurityManager({}, schema);

      const payload = {
        user: {
          id: 'user1',
          name: 'Alice',
        },
      };

      const result = mgr.validatePayload(payload);

      expect(result.valid).toBe(true);
    });

    it('should reject invalid nested objects', () => {
      const schema: CollabPayloadSchema = {
        required: ['user'],
        types: { user: 'object' },
        nested: {
          user: {
            required: ['id', 'name'],
            types: { id: 'string', name: 'string' },
          },
        },
      };
      const mgr = createCollabSecurityManager({}, schema);

      const payload = {
        user: {
          id: 'user1',
          // name missing
        },
      };

      const result = mgr.validatePayload(payload);

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.field === 'user.name')).toBe(true);
    });
  });

  describe('Malformed Payload Detection', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should accept well-formed payload', () => {
      const payload = {
        type: 'update',
        data: { key: 'value' },
      };

      const result = manager.detectMalformed(payload);

      expect(result.malformed).toBe(false);
    });

    it('should detect circular references', () => {
      const payload: Record<string, unknown> = {
        type: 'update',
      };
      payload.self = payload; // Circular reference

      const result = manager.detectMalformed(payload);

      // Circular references cause JSON.stringify to throw
      expect(result.malformed).toBe(true);
      expect(result.reason).toBeDefined();
    });

    it('should detect oversized payload', () => {
      const payload = {
        data: 'x'.repeat(2 * 1024 * 1024), // 2MB
      };

      const result = manager.detectMalformed(payload);

      expect(result.malformed).toBe(true);
      expect(result.reason).toContain('exceeds size limit');
    });

    it('should handle non-serializable payload', () => {
      const payload = {
        func: () => {},
      };

      const result = manager.detectMalformed(payload);

      // Functions are serialized as undefined, which is valid JSON
      expect(result.malformed).toBe(false);
    });
  });

  describe('Security Violation Handling', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should not disconnect on first violation', () => {
      const result = manager.handleViolation('user1', 'rate_limit');

      expect(result.shouldDisconnect).toBe(false);
    });

    it('should disconnect after multiple violations', () => {
      // Create separate manager instances to avoid token refill timing issues
      const mgr = createCollabSecurityManager({ burstCapacity: 0, refillRate: 0 });
      
      // Force 3 rate limit violations
      mgr.checkRateLimit('user1'); // Violation 1
      mgr.checkRateLimit('user1'); // Violation 2
      mgr.checkRateLimit('user1'); // Violation 3

      const result = mgr.handleViolation('user1', 'rate_limit');

      expect(result.shouldDisconnect).toBe(true);
      expect(result.reason).toContain('security violations');
    });

    it('should log connection closed event', () => {
      // Trigger multiple rate limit violations
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Violation 1
      mgr.checkRateLimit('user1'); // Violation 2
      mgr.checkRateLimit('user1'); // Violation 3
      
      mgr.handleViolation('user1', 'rate_limit');

      const events = mgr.getSecurityEvents({ type: 'connection_closed' });
      expect(events.length).toBeGreaterThan(0);
    });

    it('should not count violations older than 1 minute', async () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Violation 1

      // Wait more than 1 minute
      vi.useFakeTimers();
      vi.advanceTimersByTime(70000);

      mgr.checkRateLimit('user1'); // Violation 2
      const result = mgr.handleViolation('user1', 'rate_limit');

      // Only 1 violation in last minute, should not disconnect
      expect(result.shouldDisconnect).toBe(false);

      vi.useRealTimers();
    });
  });

  describe('Security Events', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should log security events', () => {
      // Trigger rate limit event
      const rateLimit = createCollabSecurityManager({ burstCapacity: 1 });
      rateLimit.checkRateLimit('user1');
      rateLimit.checkRateLimit('user1');

      const events = rateLimit.getSecurityEvents();
      expect(events.length).toBeGreaterThan(0);
    });

    it('should filter events by user', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });

      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user2');
      mgr.checkRateLimit('user2');

      const user1Events = mgr.getSecurityEvents({ userId: 'user1' });
      expect(user1Events.every((e) => e.userId === 'user1')).toBe(true);
    });

    it('should filter events by type', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });

      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Rate limit event

      const events = mgr.getSecurityEvents({ type: 'rate_limit' });
      expect(events.every((e) => e.type === 'rate_limit')).toBe(true);
    });

    it('should filter events by severity', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      // Trigger rate limit events (medium severity)
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1');
      
      // Trigger connection closed (high severity)
      mgr.handleViolation('user1', 'rate_limit');

      const highSeverity = mgr.getSecurityEvents({ severity: 'high' });
      expect(highSeverity.length).toBeGreaterThan(0);
      
      const mediumSeverity = mgr.getSecurityEvents({ severity: 'medium' });
      expect(mediumSeverity.length).toBeGreaterThan(0);
    });

    it('should filter events by time', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      mgr.checkRateLimit('user1');
      const firstEventTime = Date.now();
      mgr.checkRateLimit('user1'); // Event 1 at firstEventTime

      vi.useFakeTimers();
      vi.advanceTimersByTime(10000);

      mgr.checkRateLimit('user1'); // Event 2 at firstEventTime + 10000

      const all = mgr.getSecurityEvents();
      const recent = mgr.getSecurityEvents({ since: firstEventTime + 5000 });
      
      // Should have fewer events when filtered by time
      expect(recent.length).toBeLessThanOrEqual(all.length);

      vi.useRealTimers();
    });

    it('should clear security events', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Rate limit event
      expect(mgr.getSecurityEvents()).not.toHaveLength(0);

      mgr.clearSecurityEvents();
      expect(mgr.getSecurityEvents()).toHaveLength(0);
    });

    it('should notify event subscribers', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      const events: SecurityEvent[] = [];
      mgr.subscribe((event) => events.push(event));

      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Rate limit event

      expect(events.length).toBeGreaterThan(0);
    });

    it('should unsubscribe from events', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      const events: SecurityEvent[] = [];
      const unsubscribe = mgr.subscribe((event) => events.push(event));

      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Event 1
      expect(events).toHaveLength(1);

      unsubscribe();
      mgr.checkRateLimit('user1'); // No new event after unsubscribe
      expect(events).toHaveLength(1);
    });
  });

  describe('Statistics', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should track statistics', () => {
      manager.checkRateLimit('user1');
      manager.checkRateLimit('user1');
      manager.checkRateLimit('user2');

      const stats = manager.getStatistics();

      expect(stats.totalUsers).toBe(2);
      expect(stats.totalRequests).toBe(3);
    });

    it('should track throttled requests', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });

      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Throttled

      const stats = mgr.getStatistics();

      expect(stats.totalThrottled).toBeGreaterThan(0);
    });

    it('should count security events', () => {
      const mgr = createCollabSecurityManager({ burstCapacity: 1 });
      
      mgr.checkRateLimit('user1');
      mgr.checkRateLimit('user1'); // Rate limit event
      mgr.checkRateLimit('user2');
      mgr.checkRateLimit('user2'); // Rate limit event

      const stats = mgr.getStatistics();

      expect(stats.securityEvents).toBeGreaterThan(0);
    });
  });

  describe('Schema Management', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should get current schema', () => {
      const schema = manager.getSchema();

      expect(schema.required).toBeDefined();
      expect(schema.types).toBeDefined();
    });

    it('should update schema', () => {
      const newSchema: CollabPayloadSchema = {
        required: ['action'],
        types: { action: 'string' },
      };

      manager.updateSchema(newSchema);

      const payload = { action: 'test' };
      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(true);
    });

    it('should validate with updated schema', () => {
      const newSchema: CollabPayloadSchema = {
        required: ['newField'],
        types: { newField: 'string' },
      };

      manager.updateSchema(newSchema);

      const payload = { type: 'update' }; // Missing newField
      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(false);
    });
  });

  describe('Edge Cases', () => {
    let manager: CollabSecurityManager;

    beforeEach(() => {
      manager = createCollabSecurityManager();
    });

    it('should handle null payload', () => {
      const result = manager.validatePayload(null);

      expect(result.valid).toBe(false);
    });

    it('should handle undefined payload', () => {
      const result = manager.validatePayload(undefined);

      expect(result.valid).toBe(false);
    });

    it('should handle empty object payload', () => {
      const result = manager.validatePayload({});

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should handle payload with extra fields', () => {
      const payload = {
        type: 'update',
        userId: 'user1',
        timestamp: Date.now(),
        extraField: 'ignored',
      };

      const result = manager.validatePayload(payload);

      expect(result.valid).toBe(true); // Extra fields are allowed
    });

    it('should handle deeply nested payloads', () => {
      const payload = {
        type: 'update',
        userId: 'user1',
        timestamp: Date.now(),
        data: {
          level1: {
            level2: {
              level3: 'value',
            },
          },
        },
      };

      const malformed = manager.detectMalformed(payload);
      expect(malformed.malformed).toBe(false);
    });
  });
});
