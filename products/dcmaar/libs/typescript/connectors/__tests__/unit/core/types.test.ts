/**
 * @fileoverview Unit tests for type definitions and schemas
 *
 * Tests cover:
 * - Schema validation with Zod
 * - Type guard functions (if any)
 * - Interface compliance
 * - Default values
 */

import { ConnectionOptionsSchema } from '../../../src/types';
import type { ConnectionOptions, ConnectionStatus, Event, EventHandler } from '../../../src/types';

describe('Type Definitions', () => {
  describe('ConnectionOptionsSchema', () => {
    describe('Valid Configurations', () => {
      it('should validate minimal valid configuration', () => {
        const config = {
          id: 'test',
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data.id).toBe('test');
          expect(result.data.type).toBe('http');
        }
      });

      it('should validate full configuration', () => {
        const config = {
          id: 'test-connector',
          type: 'websocket',
          maxRetries: 5,
          timeout: 10000,
          secure: false,
          headers: { 'X-Custom': 'value' },
          auth: { type: 'bearer', token: 'secret' },
          debug: true,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data).toEqual(config);
        }
      });

      it('should apply default values', () => {
        const config = {
          id: 'test',
          type: 'http',
        };

        const result = ConnectionOptionsSchema.parse(config);

        expect(result.maxRetries).toBe(3);
        expect(result.timeout).toBe(30000);
        expect(result.secure).toBe(true);
        expect(result.debug).toBe(false);
      });

      it('should accept string ID', () => {
        const config = {
          id: 'my-connector-123',
          type: 'grpc',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should accept any connector type string', () => {
        const types = ['http', 'websocket', 'grpc', 'mqtt', 'custom'];

        types.forEach(type => {
          const config = {
            id: 'test',
            type,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(true);
        });
      });

      it('should accept non-negative maxRetries', () => {
        const validRetries = [0, 1, 5, 10, 100];

        validRetries.forEach(maxRetries => {
          const config = {
            id: 'test',
            type: 'http',
            maxRetries,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(true);
        });
      });

      it('should accept positive timeout values', () => {
        const validTimeouts = [1, 100, 1000, 30000, 60000];

        validTimeouts.forEach(timeout => {
          const config = {
            id: 'test',
            type: 'http',
            timeout,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(true);
        });
      });

      it('should accept boolean secure flag', () => {
        const config1 = {
          id: 'test',
          type: 'http',
          secure: true,
        };

        const config2 = {
          id: 'test',
          type: 'http',
          secure: false,
        };

        expect(ConnectionOptionsSchema.safeParse(config1).success).toBe(true);
        expect(ConnectionOptionsSchema.safeParse(config2).success).toBe(true);
      });

      it('should accept headers object', () => {
        const config = {
          id: 'test',
          type: 'http',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer token',
            'X-Custom-Header': 'value',
          },
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data.headers).toEqual(config.headers);
        }
      });

      it('should accept empty headers object', () => {
        const config = {
          id: 'test',
          type: 'http',
          headers: {},
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should accept auth configuration', () => {
        const authConfigs = [
          { type: 'none' },
          { type: 'basic', username: 'user', password: 'pass' },
          { type: 'bearer', token: 'secret' },
          { type: 'api_key', key: 'key123' },
          { type: 'oauth2', clientId: 'id', clientSecret: 'secret' },
        ];

        authConfigs.forEach(auth => {
          const config = {
            id: 'test',
            type: 'http',
            auth,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(true);
        });
      });

      it('should accept boolean debug flag', () => {
        const config1 = {
          id: 'test',
          type: 'http',
          debug: true,
        };

        const config2 = {
          id: 'test',
          type: 'http',
          debug: false,
        };

        expect(ConnectionOptionsSchema.safeParse(config1).success).toBe(true);
        expect(ConnectionOptionsSchema.safeParse(config2).success).toBe(true);
      });
    });

    describe('Invalid Configurations', () => {
      it('should reject missing id', () => {
        const config = {
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject empty string id', () => {
        const config = {
          id: '',
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject missing type', () => {
        const config = {
          id: 'test',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject empty string type', () => {
        const config = {
          id: 'test',
          type: '',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject non-string id', () => {
        const invalidIds = [123, true, null, undefined, {}, []];

        invalidIds.forEach(id => {
          const config = {
            id: id as any,
            type: 'http',
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(false);
        });
      });

      it('should reject non-string type', () => {
        const invalidTypes = [123, true, null, undefined, {}, []];

        invalidTypes.forEach(type => {
          const config = {
            id: 'test',
            type: type as any,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(false);
        });
      });

      it('should reject negative maxRetries', () => {
        const config = {
          id: 'test',
          type: 'http',
          maxRetries: -1,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject non-integer maxRetries', () => {
        const config = {
          id: 'test',
          type: 'http',
          maxRetries: 3.5,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject zero timeout', () => {
        const config = {
          id: 'test',
          type: 'http',
          timeout: 0,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject negative timeout', () => {
        const config = {
          id: 'test',
          type: 'http',
          timeout: -1000,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject non-integer timeout', () => {
        const config = {
          id: 'test',
          type: 'http',
          timeout: 1000.5,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(false);
      });

      it('should reject non-boolean secure', () => {
        const invalidValues = ['true', 1, 0, null];

        invalidValues.forEach(secure => {
          const config = {
            id: 'test',
            type: 'http',
            secure: secure as any,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(false);
        });
      });

      it('should reject non-object headers', () => {
        const invalidHeaders = ['headers', 123, true, null, []];

        invalidHeaders.forEach(headers => {
          const config = {
            id: 'test',
            type: 'http',
            headers: headers as any,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(false);
        });
      });

      it('should reject non-boolean debug', () => {
        const invalidValues = ['true', 1, 0, null];

        invalidValues.forEach(debug => {
          const config = {
            id: 'test',
            type: 'http',
            debug: debug as any,
          };

          const result = ConnectionOptionsSchema.safeParse(config);
          expect(result.success).toBe(false);
        });
      });
    });

    describe('Edge Cases', () => {
      it('should handle very long ID', () => {
        const config = {
          id: 'a'.repeat(1000),
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle special characters in ID', () => {
        const config = {
          id: 'test-connector_123.v1@server:port',
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle unicode in ID', () => {
        const config = {
          id: 'test-コネクタ-连接器',
          type: 'http',
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle very large maxRetries', () => {
        const config = {
          id: 'test',
          type: 'http',
          maxRetries: Number.MAX_SAFE_INTEGER,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle very large timeout', () => {
        const config = {
          id: 'test',
          type: 'http',
          timeout: Number.MAX_SAFE_INTEGER,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle headers with special characters', () => {
        const config = {
          id: 'test',
          type: 'http',
          headers: {
            'X-Custom-Header!@#$%': 'value-with-特殊文字',
          },
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        expect(result.success).toBe(true);
      });

      it('should handle extra unknown fields', () => {
        const config = {
          id: 'test',
          type: 'http',
          unknownField: 'value',
          anotherField: 123,
        };

        const result = ConnectionOptionsSchema.safeParse(config);

        // Zod should either accept or reject based on strict mode
        // By default, it passes through unknown keys
        expect(result.success).toBe(true);
      });
    });
  });

  describe('ConnectionStatus Type', () => {
    it('should accept valid status values', () => {
      const validStatuses: ConnectionStatus[] = [
        'disconnected',
        'connecting',
        'connected',
        'error',
      ];

      validStatuses.forEach(status => {
        const testStatus: ConnectionStatus = status;
        expect(['disconnected', 'connecting', 'connected', 'error']).toContain(testStatus);
      });
    });
  });

  describe('Event Interface', () => {
    it('should create valid event object', () => {
      const event: Event<string> = {
        id: 'event-1',
        type: 'message',
        timestamp: Date.now(),
        payload: 'test payload',
      };

      expect(event.id).toBe('event-1');
      expect(event.type).toBe('message');
      expect(event.timestamp).toBeGreaterThan(0);
      expect(event.payload).toBe('test payload');
    });

    it('should support optional metadata', () => {
      const event: Event<string> = {
        id: 'event-1',
        type: 'message',
        timestamp: Date.now(),
        payload: 'test',
        metadata: { source: 'test', priority: 'high' },
      };

      expect(event.metadata).toEqual({ source: 'test', priority: 'high' });
    });

    it('should support optional correlationId', () => {
      const event: Event<string> = {
        id: 'event-1',
        type: 'message',
        timestamp: Date.now(),
        payload: 'test',
        correlationId: 'corr-123',
      };

      expect(event.correlationId).toBe('corr-123');
    });

    it('should support generic payload types', () => {
      const stringEvent: Event<string> = {
        id: '1',
        type: 'string',
        timestamp: Date.now(),
        payload: 'text',
      };

      const numberEvent: Event<number> = {
        id: '2',
        type: 'number',
        timestamp: Date.now(),
        payload: 42,
      };

      const objectEvent: Event<{ key: string }> = {
        id: '3',
        type: 'object',
        timestamp: Date.now(),
        payload: { key: 'value' },
      };

      expect(stringEvent.payload).toBe('text');
      expect(numberEvent.payload).toBe(42);
      expect(objectEvent.payload).toEqual({ key: 'value' });
    });
  });

  describe('EventHandler Type', () => {
    it('should accept synchronous handler', () => {
      const handler: EventHandler<string> = (event) => {
        expect(event.payload).toBeDefined();
      };

      const event: Event<string> = {
        id: '1',
        type: 'test',
        timestamp: Date.now(),
        payload: 'test',
      };

      handler(event);
    });

    it('should accept async handler', async () => {
      const handler: EventHandler<string> = async (event) => {
        await Promise.resolve();
        expect(event.payload).toBeDefined();
      };

      const event: Event<string> = {
        id: '1',
        type: 'test',
        timestamp: Date.now(),
        payload: 'test',
      };

      await handler(event);
    });

    it('should accept handler that returns void', () => {
      const handler: EventHandler<string> = (event): void => {
        // Do nothing
      };

      const event: Event<string> = {
        id: '1',
        type: 'test',
        timestamp: Date.now(),
        payload: 'test',
      };

      const result = handler(event);
      expect(result).toBeUndefined();
    });

    it('should accept handler that returns Promise<void>', async () => {
      const handler: EventHandler<string> = async (event): Promise<void> => {
        await Promise.resolve();
      };

      const event: Event<string> = {
        id: '1',
        type: 'test',
        timestamp: Date.now(),
        payload: 'test',
      };

      const result = await handler(event);
      expect(result).toBeUndefined();
    });
  });

  describe('ConnectionOptions Interface', () => {
    it('should create valid minimal config', () => {
      const config: ConnectionOptions = {
        id: 'test',
        type: 'http',
      };

      expect(config.id).toBe('test');
      expect(config.type).toBe('http');
    });

    it('should create valid full config', () => {
      const config: ConnectionOptions = {
        id: 'test-connector',
        type: 'websocket',
        maxRetries: 5,
        timeout: 10000,
        secure: false,
        headers: { 'X-Custom': 'value' },
        auth: { type: 'bearer', token: 'secret' },
        debug: true,
      };

      expect(config).toMatchObject({
        id: 'test-connector',
        type: 'websocket',
        maxRetries: 5,
        timeout: 10000,
        secure: false,
        debug: true,
      });
    });

    it('should support all auth types', () => {
      const authTypes: Array<'none' | 'basic' | 'bearer' | 'api_key' | 'oauth2'> = [
        'none',
        'basic',
        'bearer',
        'api_key',
        'oauth2',
      ];

      authTypes.forEach(type => {
        const config: ConnectionOptions = {
          id: 'test',
          type: 'http',
          auth: { type },
        };

        expect(config.auth?.type).toBe(type);
      });
    });
  });
});
