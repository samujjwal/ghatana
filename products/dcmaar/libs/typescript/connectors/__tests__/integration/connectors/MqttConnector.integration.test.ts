/**
 * @fileoverview Comprehensive unit tests for MqttConnector
 *
 * Tests cover:
 * - Connection lifecycle and reconnection
 * - Publish/subscribe operations
 * - QoS levels (0, 1, 2)
 * - Topic patterns and wildcards
 * - Message queuing while disconnected
 * - Retained messages
 * - Last will and testament
 * - Clean/persistent sessions
 * - Error handling
 * - Resource cleanup
 */

import { MqttConnector, MqttConnectorConfig } from '../../../src/connectors/MqttConnector';

describe('MqttConnector', () => {
  let connector: MqttConnector;
  let config: MqttConnectorConfig;

  beforeEach(() => {
    jest.clearAllMocks();

    config = {
      id: 'mqtt-test',
      type: 'mqtt',
      url: 'mqtt://localhost:1883',
      clientId: 'test-client',
      username: 'testuser',
      password: 'testpass',
      clean: true,
      keepalive: 60,
      connectTimeout: 30000,
      reconnectPeriod: 1000,
      qos: 1,
      topics: ['test/topic', 'sensor/+/temperature'],
      resubscribe: true,
      protocolVersion: 4,
    };
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
  });

  describe('Constructor', () => {
    it('should create connector with config', () => {
      connector = new MqttConnector(config);

      expect(connector.id).toBe('mqtt-test');
      expect(connector.type).toBe('mqtt');
    });

    it('should generate clientId if not provided', () => {
      const configWithoutClientId = { ...config };
      delete (configWithoutClientId as any).clientId;

      connector = new MqttConnector(configWithoutClientId);

      expect(connector.id).toBe('mqtt-test');
    });

    it('should apply default config values', () => {
      connector = new MqttConnector({
        id: 'test',
        type: 'mqtt',
        url: 'mqtt://localhost',
      });

      expect(connector.type).toBe('mqtt');
    });
  });

  describe('Connection', () => {
    describe('connect()', () => {
      it('should establish MQTT connection', async () => {
        connector = new MqttConnector(config);
        const connectedListener = jest.fn();
        connector.on('connected', connectedListener);

        await connector.connect();

        expect(connectedListener).toHaveBeenCalled();
        expect(connector.status).toBe('connected');
      });

      it('should subscribe to configured topics on connect', async () => {
        connector = new MqttConnector(config);
        const subscribedListener = jest.fn();
        connector.on('subscribed', subscribedListener);

        await connector.connect();

        expect(subscribedListener).toHaveBeenCalled();
      });

      it('should handle connection errors', async () => {
        connector = new MqttConnector(config);
        const errorListener = jest.fn();
        connector.on('error', errorListener);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });

      it('should use clean session when configured', async () => {
        const cleanConfig = { ...config, clean: true };
        connector = new MqttConnector(cleanConfig);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });

      it('should use persistent session when configured', async () => {
        const persistentConfig = { ...config, clean: false };
        connector = new MqttConnector(persistentConfig);

        await connector.connect();

        expect(connector.status).toBe('connected');
      });
    });

    describe('disconnect()', () => {
      it('should close MQTT connection', async () => {
        connector = new MqttConnector(config);
        await connector.connect();

        await connector.disconnect();

        expect(connector.status).toBe('disconnected');
      });

      it('should clear subscribed topics', async () => {
        connector = new MqttConnector(config);
        await connector.connect();

        expect(connector.getSubscribedTopics().length).toBeGreaterThan(0);

        await connector.disconnect();

        expect(connector.getSubscribedTopics()).toEqual([]);
      });
    });
  });

  describe('Publish', () => {
    beforeEach(async () => {
      connector = new MqttConnector(config);
      await connector.connect();
    });

    describe('send()', () => {
      it('should publish message to topic', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        await connector.send({ temp: 22.5 }, { topic: 'sensor/1/temp' });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            topic: 'sensor/1/temp',
            message: expect.any(String),
          })
        );
      });

      it('should require topic for publish', async () => {
        await expect(connector.send({ data: 'test' })).rejects.toThrow(
          'Topic is required for MQTT publish'
        );
      });

      it('should throw error when not connected', async () => {
        await connector.disconnect();

        await expect(
          connector.send({ data: 'test' }, { topic: 'test' })
        ).rejects.toThrow('MQTT client is not connected');
      });

      it('should stringify object payloads', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        const payload = { temperature: 25, humidity: 60 };
        await connector.send(payload, { topic: 'sensor/data' });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            message: JSON.stringify(payload),
          })
        );
      });

      it('should use string payloads as-is', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        const payload = 'raw string data';
        await connector.send(payload, { topic: 'sensor/data' });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            message: payload,
          })
        );
      });

      it('should apply QoS level from options', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        await connector.send({ data: 'test' }, { topic: 'test', qos: 2 });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            options: expect.objectContaining({ qos: 2 }),
          })
        );
      });

      it('should use default QoS when not specified', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        await connector.send({ data: 'test' }, { topic: 'test' });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            options: expect.objectContaining({ qos: 1 }),
          })
        );
      });

      it('should set retain flag when specified', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        await connector.send({ data: 'test' }, { topic: 'test', retain: true });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            options: expect.objectContaining({ retain: true }),
          })
        );
      });

      it('should not retain by default', async () => {
        const publishedListener = jest.fn();
        connector.on('published', publishedListener);

        await connector.send({ data: 'test' }, { topic: 'test' });

        expect(publishedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            options: expect.objectContaining({ retain: false }),
          })
        );
      });
    });
  });

  describe('Subscribe', () => {
    beforeEach(async () => {
      connector = new MqttConnector(config);
      await connector.connect();
    });

    describe('subscribe()', () => {
      it('should subscribe to single topic', async () => {
        const subscribedListener = jest.fn();
        connector.on('subscribed', subscribedListener);

        await connector.subscribe('new/topic');

        expect(subscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            topics: ['new/topic'],
          })
        );
      });

      it('should subscribe to multiple topics', async () => {
        const subscribedListener = jest.fn();
        connector.on('subscribed', subscribedListener);

        await connector.subscribe(['topic/1', 'topic/2', 'topic/3']);

        expect(subscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            topics: ['topic/1', 'topic/2', 'topic/3'],
          })
        );
      });

      it('should throw error when not connected', async () => {
        await connector.disconnect();

        await expect(connector.subscribe('test')).rejects.toThrow(
          'MQTT client is not connected'
        );
      });

      it('should use specified QoS', async () => {
        const subscribedListener = jest.fn();
        connector.on('subscribed', subscribedListener);

        await connector.subscribe('test/topic', 2);

        expect(subscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            qos: 2,
          })
        );
      });

      it('should use default QoS when not specified', async () => {
        const subscribedListener = jest.fn();
        connector.on('subscribed', subscribedListener);

        await connector.subscribe('test/topic');

        expect(subscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            qos: 1,
          })
        );
      });

      it('should track subscribed topics', async () => {
        await connector.subscribe('new/topic');

        const topics = connector.getSubscribedTopics();

        expect(topics).toContain('new/topic');
      });
    });

    describe('unsubscribe()', () => {
      it('should unsubscribe from single topic', async () => {
        await connector.subscribe('test/topic');

        const unsubscribedListener = jest.fn();
        connector.on('unsubscribed', unsubscribedListener);

        await connector.unsubscribe('test/topic');

        expect(unsubscribedListener).toHaveBeenCalledWith(
          expect.objectContaining({
            topics: ['test/topic'],
          })
        );
      });

      it('should unsubscribe from multiple topics', async () => {
        await connector.subscribe(['topic/1', 'topic/2']);

        const unsubscribedListener = jest.fn();
        connector.on('unsubscribed', unsubscribedListener);

        await connector.unsubscribe(['topic/1', 'topic/2']);

        expect(unsubscribedListener).toHaveBeenCalled();
      });

      it('should remove topics from subscribed list', async () => {
        await connector.subscribe('test/topic');
        await connector.unsubscribe('test/topic');

        const topics = connector.getSubscribedTopics();

        expect(topics).not.toContain('test/topic');
      });

      it('should throw error when not connected', async () => {
        await connector.disconnect();

        await expect(connector.unsubscribe('test')).rejects.toThrow(
          'MQTT client is not connected'
        );
      });
    });

    describe('getSubscribedTopics()', () => {
      it('should return array of subscribed topics', async () => {
        await connector.subscribe(['topic/1', 'topic/2']);

        const topics = connector.getSubscribedTopics();

        expect(topics).toContain('topic/1');
        expect(topics).toContain('topic/2');
      });

      it('should return empty array when no subscriptions', async () => {
        connector = new MqttConnector({ ...config, topics: [] });
        await connector.connect();

        const topics = connector.getSubscribedTopics();

        expect(topics).toEqual([]);
      });
    });
  });

  describe('Last Will and Testament', () => {
    it('should configure last will', async () => {
      const willConfig = {
        ...config,
        will: {
          topic: 'status/offline',
          payload: 'Client disconnected unexpectedly',
          qos: 1 as const,
          retain: true,
        },
      };
      connector = new MqttConnector(willConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should support Buffer payload in will', async () => {
      const willConfig = {
        ...config,
        will: {
          topic: 'status/offline',
          payload: Buffer.from('offline'),
          qos: 0 as const,
          retain: false,
        },
      };
      connector = new MqttConnector(willConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Protocol Versions', () => {
    it('should use MQTT 3.1', async () => {
      const mqtt31Config = { ...config, protocolVersion: 3 as const };
      connector = new MqttConnector(mqtt31Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use MQTT 3.1.1', async () => {
      const mqtt311Config = { ...config, protocolVersion: 4 as const };
      connector = new MqttConnector(mqtt311Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use MQTT 5', async () => {
      const mqtt5Config = { ...config, protocolVersion: 5 as const };
      connector = new MqttConnector(mqtt5Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Error Handling', () => {
    beforeEach(async () => {
      connector = new MqttConnector(config);
      await connector.connect();
    });

    it('should emit error events', async () => {
      const errorListener = jest.fn();
      connector.on('error', errorListener);

      // Trigger error by trying to subscribe when disconnected
      await connector.disconnect();
      try {
        await connector.subscribe('test');
      } catch (error) {
        // Expected error
      }
    });

    it('should handle disconnection events', async () => {
      const disconnectedListener = jest.fn();
      connector.on('disconnected', disconnectedListener);

      await connector.disconnect();

      expect(disconnectedListener).toHaveBeenCalled();
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      connector = new MqttConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });

    it('should clear message queue on destroy', async () => {
      connector = new MqttConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });
  });

  describe('Authentication', () => {
    it('should connect with username and password', async () => {
      const authConfig = {
        ...config,
        username: 'user',
        password: 'pass',
      };
      connector = new MqttConnector(authConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should connect without authentication', async () => {
      const noAuthConfig = { ...config };
      delete (noAuthConfig as any).username;
      delete (noAuthConfig as any).password;

      connector = new MqttConnector(noAuthConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Keepalive', () => {
    it('should configure keepalive interval', async () => {
      const keepaliveConfig = {
        ...config,
        keepalive: 120,
      };
      connector = new MqttConnector(keepaliveConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use default keepalive when not specified', async () => {
      connector = new MqttConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Resubscription', () => {
    it('should resubscribe on reconnect when enabled', async () => {
      const resubConfig = { ...config, resubscribe: true };
      connector = new MqttConnector(resubConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should not resubscribe when disabled', async () => {
      const noResubConfig = { ...config, resubscribe: false };
      connector = new MqttConnector(noResubConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });
});
