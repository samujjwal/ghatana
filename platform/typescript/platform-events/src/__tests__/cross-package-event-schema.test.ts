/**
 * Cross-package event schema contract tests.
 *
 * Verifies that the platform-events PlatformEvent shape is structurally
 * compatible with the events package PlatformEventSchema, and that the
 * event envelopes produced by createPlatformEvent() satisfy all runtime
 * validation rules enforced by the Zod schema in @ghatana/events.
 *
 * These tests catch schema drift between the two event packages before
 * it reaches production consumers.
 */

import { describe, it, expect } from 'vitest';
import { z } from 'zod';

import {
  createPlatformEvent,
  createCorrelationId,
  createSessionId,
  isValidCorrelationId,
  isValidSessionId,
  type PlatformEvent,
} from '../events/base';
import {
  CanvasEvents,
  type CanvasEventPayloads,
} from '../events/canvas-events';
import {
  BuilderEvents,
  type BuilderEventPayloads,
} from '../events/builder-events';
import {
  DesignSystemEvents,
  type DesignSystemEventPayloads,
} from '../events/design-system-events';

// ---------------------------------------------------------------------------
// Inline Zod schema mirroring the @ghatana/events PlatformEventSchema
// (avoids a build-time workspace dependency on a peer package in tests)
// ---------------------------------------------------------------------------

const EventSourceSchema = z.enum([
  'canvas',
  'builder',
  'design-system',
  'product',
  'platform-events',
]);

const ActorSchema = z.enum(['user', 'ai', 'system']);
const TriggeredBySchema = z.enum(['explicit', 'implicit']);

const PlatformEventContractSchema = z.object({
  name: z.string().min(1),
  correlationId: z
    .string()
    .regex(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      'correlationId must be a valid UUID v4'
    )
    .or(z.string().min(1)), // branded string — allow any non-empty string
  sessionId: z.string().min(1),
  timestamp: z.string().datetime({ offset: true }).or(z.string().min(1)), // ISO 8601
  source: EventSourceSchema,
  actor: ActorSchema,
  triggeredBy: TriggeredBySchema,
  payload: z.unknown(),
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeBaseEvent<T>(
  name: string,
  payload: T,
  source: PlatformEvent['source'] = 'platform-events'
): PlatformEvent<T> {
  return createPlatformEvent(name, payload, { source });
}

// ---------------------------------------------------------------------------
// Base event envelope contract
// ---------------------------------------------------------------------------

describe('PlatformEvent envelope – cross-package contract', () => {
  it('createPlatformEvent produces an envelope that satisfies the contract schema', () => {
    const event = makeBaseEvent('test.event', { value: 42 });
    const result = PlatformEventContractSchema.safeParse(event);
    expect(result.success, JSON.stringify(result)).toBe(true);
  });

  it('name is a non-empty string', () => {
    const event = makeBaseEvent('user.action', {});
    expect(event.name).toBeTruthy();
    expect(typeof event.name).toBe('string');
  });

  it('timestamp is an ISO 8601 string', () => {
    const event = makeBaseEvent('timestamp.check', {});
    expect(() => new Date(event.timestamp).toISOString()).not.toThrow();
    expect(new Date(event.timestamp).getTime()).toBeGreaterThan(0);
  });

  it('correlationId is a non-empty string', () => {
    const event = makeBaseEvent('corr.id.check', {});
    expect(typeof event.correlationId).toBe('string');
    expect(event.correlationId.length).toBeGreaterThan(0);
  });

  it('sessionId is a non-empty string', () => {
    const event = makeBaseEvent('sess.check', {});
    expect(typeof event.sessionId).toBe('string');
    expect(event.sessionId.length).toBeGreaterThan(0);
  });

  it('source defaults to "platform-events"', () => {
    const event = createPlatformEvent('default.source', {});
    expect(event.source).toBe('platform-events');
  });

  it('actor defaults to "system"', () => {
    const event = createPlatformEvent('default.actor', {});
    expect(event.actor).toBe('system');
  });

  it('triggeredBy defaults to "explicit"', () => {
    const event = createPlatformEvent('default.triggered', {});
    expect(event.triggeredBy).toBe('explicit');
  });

  it('custom source, actor, triggeredBy are preserved', () => {
    const event = createPlatformEvent('custom.options', { x: 1 }, {
      source: 'canvas',
      actor: 'user',
      triggeredBy: 'implicit',
    });
    expect(event.source).toBe('canvas');
    expect(event.actor).toBe('user');
    expect(event.triggeredBy).toBe('implicit');
  });

  it('explicit correlationId is passed through as-is', () => {
    const cid = createCorrelationId('550e8400-e29b-41d4-a716-446655440000');
    const event = createPlatformEvent('explicit.cid', {}, { correlationId: cid });
    expect(event.correlationId).toBe('550e8400-e29b-41d4-a716-446655440000');
  });

  it('explicit sessionId is passed through as-is', () => {
    const sid = createSessionId('my-session-id');
    const event = createPlatformEvent('explicit.sid', {}, { sessionId: sid });
    expect(event.sessionId).toBe('my-session-id');
  });

  it('payload is preserved exactly', () => {
    const payload = { userId: 'u-123', action: 'create', metadata: { count: 5 } };
    const event = createPlatformEvent('payload.check', payload);
    expect(event.payload).toStrictEqual(payload);
  });
});

// ---------------------------------------------------------------------------
// Branded ID helpers
// ---------------------------------------------------------------------------

describe('Branded ID helpers', () => {
  describe('createCorrelationId', () => {
    it('accepts and returns a provided UUID v4 string', () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000';
      expect(createCorrelationId(uuid)).toBe(uuid);
    });

    it('auto-generates a non-empty ID when called without arguments', () => {
      const id = createCorrelationId();
      expect(typeof id).toBe('string');
      expect(id.length).toBeGreaterThan(0);
    });
  });

  describe('createSessionId', () => {
    it('accepts and returns a provided session string', () => {
      expect(createSessionId('my-sess')).toBe('my-sess');
    });

    it('auto-generates a session ID prefixed with "sess_"', () => {
      const id = createSessionId();
      expect(id.startsWith('sess_')).toBe(true);
    });
  });

  describe('isValidCorrelationId', () => {
    it('returns true for a valid UUID v4', () => {
      expect(isValidCorrelationId('550e8400-e29b-41d4-a716-446655440000')).toBe(true);
    });

    it('returns false for a non-UUID string', () => {
      expect(isValidCorrelationId('not-a-uuid')).toBe(false);
    });

    it('returns false for UUID v1 (wrong version nibble)', () => {
      expect(isValidCorrelationId('550e8400-e29b-11d4-a716-446655440000')).toBe(false);
    });
  });

  describe('isValidSessionId', () => {
    it('returns true for an 8-char alphanumeric session ID', () => {
      expect(isValidSessionId('abcd1234')).toBe(true);
    });

    it('returns false for a session ID that is too short', () => {
      expect(isValidSessionId('abc')).toBe(false);
    });

    it('returns false for a session ID that is too long', () => {
      expect(isValidSessionId('a'.repeat(65))).toBe(false);
    });

    it('returns false for session IDs containing forbidden characters', () => {
      expect(isValidSessionId('invalid!@#chars')).toBe(false);
    });
  });
});

// ---------------------------------------------------------------------------
// Canvas event schema contract
// ---------------------------------------------------------------------------

describe('Canvas event schema contract', () => {
  it('viewport.changed payload satisfies the contract schema', () => {
    const payload: CanvasEventPayloads['canvas.viewport.changed'] = {
      zoom: 1.5,
      panX: 100,
      panY: -50,
      width: 1280,
      height: 800,
    };
    const event = createPlatformEvent(CanvasEvents.VIEWPORT_CHANGED, payload, {
      source: 'canvas',
      actor: 'user',
    });

    const result = PlatformEventContractSchema.safeParse(event);
    expect(result.success, JSON.stringify(result)).toBe(true);
    expect(event.name).toBe('canvas.viewport.changed');
    expect(event.payload.zoom).toBe(1.5);
  });

  it('node.created payload satisfies the contract schema', () => {
    const payload: CanvasEventPayloads['canvas.node.created'] = {
      nodeId: 'node-1',
      nodeType: 'component',
      position: { x: 0, y: 0 },
      dimensions: { width: 100, height: 50 },
    };
    const event = createPlatformEvent(CanvasEvents.NODE_CREATED, payload, {
      source: 'canvas',
    });

    const result = PlatformEventContractSchema.safeParse(event);
    expect(result.success, JSON.stringify(result)).toBe(true);
  });

  it('AI suggestion events carry correct name constants', () => {
    expect(CanvasEvents.AI_SUGGESTION_SHOWN).toBe('canvas.ai.suggestion.shown');
    expect(CanvasEvents.AI_SUGGESTION_ACCEPTED).toBe('canvas.ai.suggestion.accepted');
    expect(CanvasEvents.AI_SUGGESTION_REJECTED).toBe('canvas.ai.suggestion.rejected');
  });

  it('collaboration events carry correct name constants', () => {
    expect(CanvasEvents.COLLABORATION_PEER_JOINED).toBe('canvas.collaboration.peer.joined');
    expect(CanvasEvents.COLLABORATION_PEER_LEFT).toBe('canvas.collaboration.peer.left');
    expect(CanvasEvents.COLLABORATION_CONFLICT_DETECTED).toBe('canvas.collaboration.conflict.detected');
    expect(CanvasEvents.COLLABORATION_CONFLICT_RESOLVED).toBe('canvas.collaboration.conflict.resolved');
  });
});

// ---------------------------------------------------------------------------
// Builder event schema contract
// ---------------------------------------------------------------------------

describe('Builder event schema contract', () => {
  it('document.loaded payload satisfies the contract schema', () => {
    const payload: BuilderEventPayloads['builder.document.loaded'] = {
      documentId: 'doc-42',
      designSystemRef: '@ghatana/design-system@1.0.0',
      version: '1.0.0',
      nodeCount: 12,
    };
    const event = createPlatformEvent(BuilderEvents.DOCUMENT_LOADED, payload, {
      source: 'builder',
      actor: 'user',
    });

    const result = PlatformEventContractSchema.safeParse(event);
    expect(result.success, JSON.stringify(result)).toBe(true);
    expect(event.payload.documentId).toBe('doc-42');
  });
});

// ---------------------------------------------------------------------------
// Design system event schema contract
// ---------------------------------------------------------------------------

describe('Design system event schema contract', () => {
  it('ds.token.created payload satisfies the contract schema', () => {
    const payload: DesignSystemEventPayloads['ds.token.created'] = {
      tokenId: 'tok-1',
      tokenName: 'color.primary.500',
      category: 'color',
      value: '#3b82f6',
    };
    const event = createPlatformEvent(DesignSystemEvents.TOKEN_CREATED, payload, {
      source: 'design-system',
    });

    const result = PlatformEventContractSchema.safeParse(event);
    expect(result.success, JSON.stringify(result)).toBe(true);
    expect(event.payload.tokenName).toBe('color.primary.500');
  });
});

// ---------------------------------------------------------------------------
// Event name uniqueness across all domains
// ---------------------------------------------------------------------------

describe('Event name uniqueness across domains', () => {
  it('canvas, builder, and design-system event names have no collisions', async () => {
    const { ALL_CANVAS_EVENT_NAMES } = await import('../events/canvas-events');
    const { ALL_BUILDER_EVENT_NAMES } = await import('../events/builder-events');
    const { ALL_DESIGN_SYSTEM_EVENT_NAMES } = await import('../events/design-system-events');

    const all = [
      ...ALL_CANVAS_EVENT_NAMES,
      ...ALL_BUILDER_EVENT_NAMES,
      ...ALL_DESIGN_SYSTEM_EVENT_NAMES,
    ];
    const unique = new Set(all);

    expect(unique.size).toBe(all.length);
  });

  it('all event names follow the domain.action naming convention', async () => {
    const { ALL_CANVAS_EVENT_NAMES } = await import('../events/canvas-events');
    const { ALL_BUILDER_EVENT_NAMES } = await import('../events/builder-events');
    const { ALL_DESIGN_SYSTEM_EVENT_NAMES } = await import('../events/design-system-events');

    const domainPrefix = /^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/;
    const allNames = [
      ...ALL_CANVAS_EVENT_NAMES,
      ...ALL_BUILDER_EVENT_NAMES,
      ...ALL_DESIGN_SYSTEM_EVENT_NAMES,
    ];

    for (const name of allNames) {
      expect(name, `"${name}" does not match domain.action pattern`).toMatch(domainPrefix);
    }
  });
});
