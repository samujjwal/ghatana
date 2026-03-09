/**
 * @fileoverview Unit Tests for SinkConfigHandler
 * @vitest-environment jsdom
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { SinkConfigHandler } from '../../../src/app/background/handlers/SinkConfigHandler';
import type { SinkConfigMessage } from '../../../src/app/background/contracts/messages';

describe('SinkConfigHandler', () => {
  let handler: SinkConfigHandler;

  beforeEach(() => {
    handler = new SinkConfigHandler();
  });

  describe('handle', () => {
    it('should create a new sink', async () => {
      const message: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/events',
          enabled: true,
        },
      };

      const result = await handler.handle(message);

      expect(result.success).toBe(true);
      expect(result.data).toEqual({
        sinkId: 'http-sink-1',
        sinkType: 'http',
        endpoint: 'https://api.example.com/events',
        enabled: true,
        isUpdate: false,
        status: 'active',
      });
    });

    it('should update an existing sink', async () => {
      const message1: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/events',
          enabled: true,
        },
      };

      const message2: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440001',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/events-v2',
          enabled: false,
        },
      };

      await handler.handle(message1);
      const result = await handler.handle(message2);

      expect(result.success).toBe(true);
      expect(result.data).toEqual({
        sinkId: 'http-sink-1',
        sinkType: 'http',
        endpoint: 'https://api.example.com/events-v2',
        enabled: false,
        isUpdate: true,
        status: 'active',
      });
    });

    it('should reject invalid sink type', async () => {
      const message: any = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-1',
          sinkType: 'invalid-type',
          endpoint: 'https://api.example.com',
          enabled: true,
        },
      };

      const result = await handler.handle(message);

      expect(result.success).toBe(false);
      expect(result.error).toContain('sinkType must be one of');
    });

    it('should reject invalid URL for HTTP sink', async () => {
      const message: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'not-a-url',
          enabled: true,
        },
      };

      const result = await handler.handle(message);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Invalid URL');
    });

    it('should reject empty sinkId', async () => {
      const message: any = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: '',
          sinkType: 'http',
          endpoint: 'https://api.example.com',
          enabled: true,
        },
      };

      const result = await handler.handle(message);

      expect(result.success).toBe(false);
      expect(result.error).toContain('sinkId is required');
    });
  });

  describe('getSink', () => {
    it('should retrieve a sink by ID', async () => {
      const message: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/events',
          enabled: true,
        },
      };

      await handler.handle(message);
      const sink = handler.getSink('http-sink-1');

      expect(sink).toBeDefined();
      expect(sink?.sinkId).toBe('http-sink-1');
      expect(sink?.sinkType).toBe('http');
    });

    it('should return undefined for non-existent sink', () => {
      const sink = handler.getSink('non-existent');
      expect(sink).toBeUndefined();
    });
  });

  describe('getAllSinks', () => {
    it('should return all sinks', async () => {
      const message1: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/1',
          enabled: true,
        },
      };

      const message2: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440001',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-2',
          sinkType: 'websocket',
          endpoint: 'wss://api.example.com/2',
          enabled: true,
        },
      };

      await handler.handle(message1);
      await handler.handle(message2);

      const sinks = handler.getAllSinks();

      expect(sinks).toHaveLength(2);
      expect(sinks.map((s) => s.sinkId)).toContain('sink-1');
      expect(sinks.map((s) => s.sinkId)).toContain('sink-2');
    });
  });

  describe('getEnabledSinks', () => {
    it('should return only enabled sinks', async () => {
      const message1: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/1',
          enabled: true,
        },
      };

      const message2: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440001',
        type: 'sink-config',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-2',
          sinkType: 'http',
          endpoint: 'https://api.example.com/2',
          enabled: false,
        },
      };

      await handler.handle(message1);
      await handler.handle(message2);

      const enabledSinks = handler.getEnabledSinks();

      expect(enabledSinks).toHaveLength(1);
      expect(enabledSinks[0].sinkId).toBe('sink-1');
    });
  });

  describe('removeSink', () => {
    it('should remove a sink', async () => {
      const message: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'http-sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/events',
          enabled: true,
        },
      };

      await handler.handle(message);
      const removed = handler.removeSink('http-sink-1');

      expect(removed).toBe(true);
      expect(handler.getSink('http-sink-1')).toBeUndefined();
    });

    it('should return false for non-existent sink', () => {
      const removed = handler.removeSink('non-existent');
      expect(removed).toBe(false);
    });
  });

  describe('getStatus', () => {
    it('should return correct status', async () => {
      const message1: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        type: 'sink-config',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-1',
          sinkType: 'http',
          endpoint: 'https://api.example.com/1',
          enabled: true,
        },
      };

      const message2: SinkConfigMessage = {
        id: '550e8400-e29b-41d4-a716-446655440001',
        type: 'sink-config',
        timestamp: new Date().toISOString(),
        sourceId: 'test-source',
        payload: {
          sinkId: 'sink-2',
          sinkType: 'http',
          endpoint: 'https://api.example.com/2',
          enabled: false,
        },
      };

      await handler.handle(message1);
      await handler.handle(message2);

      const status = handler.getStatus();

      expect(status.totalSinks).toBe(2);
      expect(status.enabledSinks).toBe(1);
      expect(status.disabledSinks).toBe(1);
      expect(status.sinksByType.http).toBe(2);
    });
  });
});
