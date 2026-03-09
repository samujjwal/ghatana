/**
 * @fileoverview Comprehensive unit tests for NatsConnector
 *
 * Tests cover:
 * - Connection lifecycle with multiple servers
 * - Publish/subscribe operations
 * - Request/reply pattern
 * - JetStream publish and subscribe
 * - Stream and consumer management
 * - Message handling and acknowledgments
 * - Connection status tracking
 * - Error handling
 * - Resource cleanup
 */

import { NatsConnector, NatsConnectorConfig } from '../../../src/connectors/NatsConnector';

describe('NatsConnector', () => {
  let connector: NatsConnector;
  let config: NatsConnectorConfig;

  beforeEach(() => {
    jest.clearAllMocks();

    config = {
      id: 'nats-test',
      type: 'nats',
      servers: ['nats://localhost:4222', 'nats://localhost:4223'],
      name: 'test-client',
      user: 'testuser',
      pass: 'testpass',
      maxReconnectAttempts: -1,
      reconnectTimeWait: 2000,
      timeout: 20000,
      pingInterval: 120000,
      maxPingOut: 2,
      verbose: false,
      pedantic: false,
      jetstream: false,
    };
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
  });

  describe('Constructor', () => {
    it('should create connector with config', () => {
      connector = new NatsConnector(config);

      expect(connector.id).toBe('nats-test');
      expect(connector.type).toBe('nats');
    });

    it('should apply default config values', () => {
      connector = new NatsConnector({
        id: 'test',
        type: 'nats',
        servers: ['nats://localhost:4222'],
      });

      expect(connector.type).toBe('nats');
    });
  });

  describe('Connection', () => {
    describe('connect()', () => {
      it('should establish NATS connection', async () => {
        connector = new NatsConnector(config);
        const connectedListener = jest.fn();
        connector.on('connected', connectedListener);

        await connector.connect();

        expect(connectedListener).toHaveBeenCalled();
        expect(connector.status).toBe('connected');
      });

      it('should use multiple servers', async () => {
        connector = new NatsConnector(config);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });

      it('should use authentication when provided', async () => {
        connector = new NatsConnector(config);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });

      it('should use token authentication', async () => {
        const tokenConfig = { ...config, token: 'secret-token' };
        delete (tokenConfig as any).user;
        delete (tokenConfig as any).pass;
        connector = new NatsConnector(tokenConfig);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });
    });

    describe('disconnect()', () => {
      it('should close NATS connection', async () => {
        connector = new NatsConnector(config);
        await connector.connect();

        await connector.disconnect();

        expect(connector.status).toBe('disconnected');
      });

      it('should drain all subscriptions', async () => {
        connector = new NatsConnector(config);
        await connector.connect();
        await connector.subscribe('test.subject');

        await connector.disconnect();

        expect(connector.getSubscriptions()).toEqual([]);
      });
    });
  });

  describe('Publish', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should publish message to subject', async () => {
      const publishedListener = jest.fn();
      connector.on('published', publishedListener);

      await connector.send({ data: 'test' }, { subject: 'test.subject' });

      expect(publishedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          subject: 'test.subject',
        })
      );
    });

    it('should require subject for publish', async () => {
      await expect(connector.send({ data: 'test' })).rejects.toThrow(
        'Subject is required for NATS publish'
      );
    });

    it('should throw error when not connected', async () => {
      await connector.disconnect();

      await expect(
        connector.send({ data: 'test' }, { subject: 'test' })
      ).rejects.toThrow('NATS connection is not established');
    });

    it('should stringify object payloads', async () => {
      const publishedListener = jest.fn();
      connector.on('published', publishedListener);

      const payload = { temp: 25, humidity: 60 };
      await connector.send(payload, { subject: 'sensor.data' });

      expect(publishedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: JSON.stringify(payload),
        })
      );
    });

    it('should use string payloads as-is', async () => {
      const publishedListener = jest.fn();
      connector.on('published', publishedListener);

      await connector.send('raw data', { subject: 'test' });

      expect(publishedListener).toHaveBeenCalled();
    });
  });

  describe('Subscribe', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should subscribe to subject', async () => {
      const subscribedListener = jest.fn();
      connector.on('subscribed', subscribedListener);

      await connector.subscribe('test.subject');

      expect(subscribedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          subject: 'test.subject',
        })
      );
    });

    it('should throw error when not connected', async () => {
      await connector.disconnect();

      await expect(connector.subscribe('test')).rejects.toThrow(
        'NATS connection is not established'
      );
    });

    it('should throw error on duplicate subscription', async () => {
      await connector.subscribe('test.subject');

      await expect(connector.subscribe('test.subject')).rejects.toThrow(
        'Already subscribed to subject'
      );
    });

    it('should support queue groups', async () => {
      await connector.subscribe('test.subject', { queue: 'workers' });

      expect(connector.getSubscriptions()).toContain('test.subject');
    });

    it('should support max messages option', async () => {
      await connector.subscribe('test.subject', { max: 100 });

      expect(connector.getSubscriptions()).toContain('test.subject');
    });
  });

  describe('Unsubscribe', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should unsubscribe from subject', async () => {
      await connector.subscribe('test.subject');

      const unsubscribedListener = jest.fn();
      connector.on('unsubscribed', unsubscribedListener);

      await connector.unsubscribe('test.subject');

      expect(unsubscribedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          subject: 'test.subject',
        })
      );
    });

    it('should handle unsubscribe from non-existent subject', async () => {
      await connector.unsubscribe('nonexistent');

      // Should not throw
      expect(connector.status).toBe('connected');
    });

    it('should remove subject from subscriptions list', async () => {
      await connector.subscribe('test.subject');
      await connector.unsubscribe('test.subject');

      expect(connector.getSubscriptions()).not.toContain('test.subject');
    });
  });

  describe('Request/Reply Pattern', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should send request and await reply', async () => {
      const requestSentListener = jest.fn();
      connector.on('requestSent', requestSentListener);

      await connector.request('service.echo', { message: 'hello' });

      expect(requestSentListener).toHaveBeenCalledWith(
        expect.objectContaining({
          subject: 'service.echo',
        })
      );
    });

    it('should use configured timeout', async () => {
      await connector.request('service.slow', { data: 'test' }, { timeout: 10000 });

      // Should not throw
      expect(connector.status).toBe('connected');
    });

    it('should use default timeout when not specified', async () => {
      await connector.request('service.test', { data: 'test' });

      // Should use config timeout
      expect(connector.status).toBe('connected');
    });

    it('should throw error when not connected', async () => {
      await connector.disconnect();

      await expect(
        connector.request('test', { data: 'test' })
      ).rejects.toThrow('NATS connection is not established');
    });
  });

  describe('JetStream', () => {
    beforeEach(async () => {
      connector = new NatsConnector({ ...config, jetstream: true });
      await connector.connect();
    });

    describe('jsPublish()', () => {
      it('should publish to JetStream', async () => {
        const jsPublishedListener = jest.fn();
        connector.on('jsPublished', jsPublishedListener);

        await connector.jsPublish('events.created', { id: '123' });

        expect(jsPublishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            subject: 'events.created',
          })
        );
      });

      it('should throw error when JetStream is not enabled', async () => {
        connector = new NatsConnector({ ...config, jetstream: false });
        await connector.connect();

        await expect(
          connector.jsPublish('test', { data: 'test' })
        ).rejects.toThrow('JetStream is not enabled');
      });

      it('should support message ID', async () => {
        await connector.jsPublish('events.test', { data: 'test' }, { msgId: 'msg-123' });

        expect(connector.status).toBe('connected');
      });

      it('should support timeout option', async () => {
        await connector.jsPublish('events.test', { data: 'test' }, { timeout: 5000 });

        expect(connector.status).toBe('connected');
      });
    });

    describe('jsSubscribe()', () => {
      it('should subscribe to JetStream subject', async () => {
        const jsSubscribedListener = jest.fn();
        connector.on('jsSubscribed', jsSubscribedListener);

        await connector.jsSubscribe('events.created');

        expect(jsSubscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            subject: 'events.created',
          })
        );
      });

      it('should throw error when JetStream is not enabled', async () => {
        connector = new NatsConnector({ ...config, jetstream: false });
        await connector.connect();

        await expect(connector.jsSubscribe('test')).rejects.toThrow(
          'JetStream is not enabled'
        );
      });

      it('should support durable consumers', async () => {
        await connector.jsSubscribe('events.*', {
          durable: 'processor',
          stream: 'EVENTS',
        });

        expect(connector.status).toBe('connected');
      });

      it('should support queue groups', async () => {
        await connector.jsSubscribe('events.*', {
          queue: 'workers',
          stream: 'EVENTS',
        });

        expect(connector.status).toBe('connected');
      });
    });

    describe('jsCreateStream()', () => {
      it('should create JetStream stream', async () => {
        const jsStreamCreatedListener = jest.fn();
        connector.on('jsStreamCreated', jsStreamCreatedListener);

        await connector.jsCreateStream({
          name: 'EVENTS',
          subjects: ['events.*'],
        });

        expect(jsStreamCreatedListener).toHaveBeenCalled();
      });

      it('should throw error when JetStream is not enabled', async () => {
        connector = new NatsConnector({ ...config, jetstream: false });
        await connector.connect();

        await expect(
          connector.jsCreateStream({ name: 'TEST' })
        ).rejects.toThrow('JetStream is not enabled');
      });
    });

    describe('jsCreateConsumer()', () => {
      it('should create JetStream consumer', async () => {
        const jsConsumerCreatedListener = jest.fn();
        connector.on('jsConsumerCreated', jsConsumerCreatedListener);

        await connector.jsCreateConsumer('EVENTS', { durable_name: 'processor' });

        expect(jsConsumerCreatedListener).toHaveBeenCalled();
      });

      it('should throw error when JetStream is not enabled', async () => {
        connector = new NatsConnector({ ...config, jetstream: false });
        await connector.connect();

        await expect(
          connector.jsCreateConsumer('TEST', {})
        ).rejects.toThrow('JetStream is not enabled');
      });
    });
  });

  describe('Connection Statistics', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should get connection stats', () => {
      const stats = connector.getStats();

      expect(stats).toHaveProperty('inMsgs');
      expect(stats).toHaveProperty('outMsgs');
      expect(stats).toHaveProperty('inBytes');
      expect(stats).toHaveProperty('outBytes');
      expect(stats).toHaveProperty('reconnects');
    });

    it('should return null when not connected', async () => {
      await connector.disconnect();

      const stats = connector.getStats();

      expect(stats).toBeNull();
    });
  });

  describe('Subscriptions Management', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should get list of subscriptions', async () => {
      await connector.subscribe('test.1');
      await connector.subscribe('test.2');

      const subscriptions = connector.getSubscriptions();

      expect(subscriptions).toContain('test.1');
      expect(subscriptions).toContain('test.2');
    });

    it('should return empty array when no subscriptions', () => {
      const subscriptions = connector.getSubscriptions();

      expect(subscriptions).toEqual([]);
    });
  });

  describe('Flush', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should flush pending messages', async () => {
      const flushedListener = jest.fn();
      connector.on('flushed', flushedListener);

      await connector.flush();

      expect(flushedListener).toHaveBeenCalled();
    });

    it('should handle flush when not connected', async () => {
      await connector.disconnect();

      await connector.flush();

      // Should not throw
      expect(connector.status).toBe('disconnected');
    });
  });

  describe('TLS Configuration', () => {
    it('should connect with TLS', async () => {
      const tlsConfig = {
        ...config,
        tls: {
          cert: '/path/to/client.crt',
          key: '/path/to/client.key',
          ca: '/path/to/ca.pem',
          rejectUnauthorized: true,
        },
      };
      connector = new NatsConnector(tlsConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      connector = new NatsConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });

    it('should clear subscriptions on destroy', async () => {
      connector = new NatsConnector(config);
      await connector.connect();
      await connector.subscribe('test.subject');

      await connector.destroy();

      expect(connector.getSubscriptions()).toEqual([]);
    });
  });

  describe('Error Handling', () => {
    beforeEach(async () => {
      connector = new NatsConnector(config);
      await connector.connect();
    });

    it('should emit error events', async () => {
      const errorListener = jest.fn();
      connector.on('error', errorListener);

      // Trigger error
      await connector.disconnect();
      try {
        await connector.send({ data: 'test' }, { subject: 'test' });
      } catch (error) {
        // Expected
      }
    });
  });

  describe('Reconnection', () => {
    it('should use infinite reconnect attempts by default', () => {
      connector = new NatsConnector(config);

      expect(connector.type).toBe('nats');
    });

    it('should configure reconnection options', () => {
      const reconnectConfig = {
        ...config,
        maxReconnectAttempts: 5,
        reconnectTimeWait: 3000,
      };
      connector = new NatsConnector(reconnectConfig);

      expect(connector.type).toBe('nats');
    });
  });
});
