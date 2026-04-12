/**
 * @fileoverview Tests for platform event types and validation.
 */

import { describe, it, expect } from 'vitest';
import {
  createCorrelationId,
  createSessionId,
  isValidCorrelationId,
  isValidSessionId,
  createPlatformEvent,
} from '../events/base';
import {
  CanvasEvents,
  ALL_CANVAS_EVENT_NAMES,
  type CanvasEventPayloads,
} from '../events/canvas-events';
import {
  BuilderEvents,
  ALL_BUILDER_EVENT_NAMES,
  type BuilderEventPayloads,
} from '../events/builder-events';
import {
  DesignSystemEvents,
  ALL_DESIGN_SYSTEM_EVENT_NAMES,
  type DesignSystemEventPayloads,
} from '../events/design-system-events';
import type { PlatformEvent } from '../events/base';

describe('Event Types', () => {
  describe('Base Types', () => {
    it('should create branded correlation IDs', () => {
      const id = createCorrelationId('test-id-123');
      expect(typeof id).toBe('string');
      expect(id).toBe('test-id-123');
    });

    it('should create branded session IDs', () => {
      const id = createSessionId('session-123');
      expect(typeof id).toBe('string');
      expect(id).toBe('session-123');
    });

    it('should validate UUID v4 correlation IDs', () => {
      const validUUID = '550e8400-e29b-41d4-a716-446655440000';
      expect(isValidCorrelationId(validUUID)).toBe(true);

      const invalidUUID = 'not-a-uuid';
      expect(isValidCorrelationId(invalidUUID)).toBe(false);
    });

    it('should validate session ID format', () => {
      expect(isValidSessionId('sess_12345')).toBe(true);
      expect(isValidSessionId('a')).toBe(false); // too short
      expect(isValidSessionId('a'.repeat(65))).toBe(false); // too long
      expect(isValidSessionId('invalid!@#')).toBe(false); // invalid characters
    });

    it('should create platform events with required fields', () => {
      const event = createPlatformEvent('test.event', { foo: 'bar' });

      expect(event.name).toBe('test.event');
      expect(event.payload).toEqual({ foo: 'bar' });
      expect(event.source).toBe('platform-events');
      expect(event.actor).toBe('system');
      expect(event.triggeredBy).toBe('explicit');
      expect(typeof event.timestamp).toBe('string');
      expect(typeof event.correlationId).toBe('string');
      expect(typeof event.sessionId).toBe('string');
    });

    it('should create events with custom options', () => {
      const event = createPlatformEvent(
        'custom.event',
        { data: true },
        {
          source: 'canvas',
          actor: 'user',
          triggeredBy: 'implicit',
        }
      );

      expect(event.source).toBe('canvas');
      expect(event.actor).toBe('user');
      expect(event.triggeredBy).toBe('implicit');
    });
  });

  describe('Canvas Events', () => {
    it('should have all 25 required canvas event names', () => {
      const expectedCount = 25;
      expect(ALL_CANVAS_EVENT_NAMES.length).toBe(expectedCount);
    });

    it('should have unique canvas event names', () => {
      const uniqueNames = new Set(ALL_CANVAS_EVENT_NAMES);
      expect(uniqueNames.size).toBe(ALL_CANVAS_EVENT_NAMES.length);
    });

    it('should have all canvas events as const', () => {
      // Check specific required events exist
      expect(CanvasEvents.VIEWPORT_CHANGED).toBe('canvas.viewport.changed');
      expect(CanvasEvents.SELECTION_CHANGED).toBe('canvas.selection.changed');
      expect(CanvasEvents.NODE_CREATED).toBe('canvas.node.created');
      expect(CanvasEvents.NODE_UPDATED).toBe('canvas.node.updated');
      expect(CanvasEvents.NODE_DELETED).toBe('canvas.node.deleted');
      expect(CanvasEvents.EDGE_CREATED).toBe('canvas.edge.created');
      expect(CanvasEvents.EDGE_UPDATED).toBe('canvas.edge.updated');
      expect(CanvasEvents.EDGE_DELETED).toBe('canvas.edge.deleted');
      expect(CanvasEvents.LAYOUT_APPLIED).toBe('canvas.layout.applied');
      expect(CanvasEvents.IMPORT_COMPLETED).toBe('canvas.import.completed');
      expect(CanvasEvents.EXPORT_COMPLETED).toBe('canvas.export.completed');
      expect(CanvasEvents.RENDER_FAILED).toBe('canvas.render.failed');
      expect(CanvasEvents.PERFORMANCE_SAMPLED).toBe('canvas.performance.sampled');
      expect(CanvasEvents.AI_SUGGESTION_SHOWN).toBe('canvas.ai.suggestion.shown');
      expect(CanvasEvents.AI_SUGGESTION_ACCEPTED).toBe('canvas.ai.suggestion.accepted');
      expect(CanvasEvents.AI_SUGGESTION_REJECTED).toBe('canvas.ai.suggestion.rejected');
      expect(CanvasEvents.AI_ACTION_APPLIED).toBe('canvas.ai.action.applied');
      expect(CanvasEvents.AI_REVIEW_REQUESTED).toBe('canvas.ai.review.requested');
      expect(CanvasEvents.AI_REVIEW_APPROVED).toBe('canvas.ai.review.approved');
      expect(CanvasEvents.AI_REVIEW_REJECTED).toBe('canvas.ai.review.rejected');
      expect(CanvasEvents.AI_OVERRIDE_INVOKED).toBe('canvas.ai.override.invoked');
      expect(CanvasEvents.COLLABORATION_PEER_JOINED).toBe('canvas.collaboration.peer.joined');
      expect(CanvasEvents.COLLABORATION_PEER_LEFT).toBe('canvas.collaboration.peer.left');
      expect(CanvasEvents.COLLABORATION_CONFLICT_DETECTED).toBe('canvas.collaboration.conflict.detected');
      expect(CanvasEvents.COLLABORATION_CONFLICT_RESOLVED).toBe('canvas.collaboration.conflict.resolved');
    });

    it('should satisfy PlatformEvent type constraints', () => {
      const viewportPayload: CanvasEventPayloads['canvas.viewport.changed'] = {
        zoom: 1,
        panX: 0,
        panY: 0,
        width: 800,
        height: 600,
      };

      const event: PlatformEvent<typeof viewportPayload> = {
        name: CanvasEvents.VIEWPORT_CHANGED,
        correlationId: createCorrelationId('test-correlation-id'),
        sessionId: createSessionId('test-session'),
        timestamp: new Date().toISOString(),
        source: 'canvas',
        actor: 'user',
        triggeredBy: 'explicit',
        payload: viewportPayload,
      };

      expect(event.payload.zoom).toBe(1);
      expect(event.name).toBe('canvas.viewport.changed');
    });
  });

  describe('Builder Events', () => {
    it('should have all 25 required builder event names', () => {
      const expectedCount = 25;
      expect(ALL_BUILDER_EVENT_NAMES.length).toBe(expectedCount);
    });

    it('should have unique builder event names', () => {
      const uniqueNames = new Set(ALL_BUILDER_EVENT_NAMES);
      expect(uniqueNames.size).toBe(ALL_BUILDER_EVENT_NAMES.length);
    });

    it('should satisfy PlatformEvent type constraints', () => {
      const documentPayload: BuilderEventPayloads['builder.document.loaded'] = {
        documentId: 'doc-123',
        designSystemRef: '@ghatana/design-system@1.0.0',
        version: '1.0.0',
        nodeCount: 5,
      };

      const event: PlatformEvent<typeof documentPayload> = {
        name: BuilderEvents.DOCUMENT_LOADED,
        correlationId: createCorrelationId('test-correlation-id'),
        sessionId: createSessionId('test-session'),
        timestamp: new Date().toISOString(),
        source: 'builder',
        actor: 'user',
        triggeredBy: 'explicit',
        payload: documentPayload,
      };

      expect(event.payload.documentId).toBe('doc-123');
      expect(event.name).toBe('builder.document.loaded');
    });
  });

  describe('Design System Events', () => {
    it('should have all required design system event names', () => {
      expect(ALL_DESIGN_SYSTEM_EVENT_NAMES.length).toBeGreaterThan(0);
    });

    it('should have unique design system event names', () => {
      const uniqueNames = new Set(ALL_DESIGN_SYSTEM_EVENT_NAMES);
      expect(uniqueNames.size).toBe(ALL_DESIGN_SYSTEM_EVENT_NAMES.length);
    });

    it('should satisfy PlatformEvent type constraints', () => {
      const tokenPayload: DesignSystemEventPayloads['ds.token.created'] = {
        tokenId: 'token-123',
        tokenName: 'color.primary.500',
        category: 'color',
        value: '#3b82f6',
      };

      const event: PlatformEvent<typeof tokenPayload> = {
        name: DesignSystemEvents.TOKEN_CREATED,
        correlationId: createCorrelationId('test-correlation-id'),
        sessionId: createSessionId('test-session'),
        timestamp: new Date().toISOString(),
        source: 'design-system',
        actor: 'user',
        triggeredBy: 'explicit',
        payload: tokenPayload,
      };

      expect(event.payload.tokenName).toBe('color.primary.500');
      expect(event.name).toBe('ds.token.created');
    });
  });
});
