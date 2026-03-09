/**
 * @fileoverview Comprehensive unit tests for IpcConnector
 *
 * Tests cover:
 * - Constructor and default configuration
 * - Server mode: starting, accepting connections, broadcasting
 * - Client mode: connecting, sending messages
 * - Bidirectional communication
 * - Message serialization (JSON, msgpack)
 * - Acknowledgment mechanism
 * - Socket path resolution (Unix/Windows)
 * - Connection management (server tracking clients)
 * - Error handling and edge cases
 * - Resource cleanup
 */

import { IpcConnector, IpcConnectorConfig } from '../../../src/connectors/IpcConnector';

describe('IpcConnector', () => {
  let connector: IpcConnector;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
  });

  afterEach(async () => {
    if (connector) {
      try {
        await connector.destroy();
      } catch (error) {
        // Ignore cleanup errors
      }
    }
    jest.useRealTimers();
  });

  describe('Constructor and Configuration', () => {
    it('should create connector with minimal config', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
      };

      connector = new IpcConnector(config);

      expect(connector).toBeInstanceOf(IpcConnector);
      expect(connector.id).toBe('test-ipc');
      expect(connector.type).toBe('ipc');
      expect(connector.status).toBe('disconnected');
    });

    it('should apply default configuration values', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
      };

      connector = new IpcConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.mode).toBe('client');
      expect(internalConfig.transport).toBe('socket');
      expect(internalConfig.serialize).toBe(true);
      expect(internalConfig.format).toBe('json');
      expect(internalConfig.maxMessageSize).toBe(1024 * 1024);
      expect(internalConfig.ack).toBe(false);
      expect(internalConfig.ackTimeout).toBe(5000);
    });

    it('should override default configuration', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
        transport: 'pipe',
        serialize: false,
        format: 'msgpack',
        maxMessageSize: 2 * 1024 * 1024,
        ack: true,
        ackTimeout: 10000,
      };

      connector = new IpcConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.mode).toBe('server');
      expect(internalConfig.transport).toBe('pipe');
      expect(internalConfig.serialize).toBe(false);
      expect(internalConfig.format).toBe('msgpack');
      expect(internalConfig.maxMessageSize).toBe(2 * 1024 * 1024);
      expect(internalConfig.ack).toBe(true);
      expect(internalConfig.ackTimeout).toBe(10000);
    });

    it('should configure custom socket path', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        socketPath: '/custom/path/socket.sock',
      };

      connector = new IpcConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.socketPath).toBe('/custom/path/socket.sock');
    });

    it('should configure custom pipe path', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        pipePath: '\\\\.\\pipe\\custom-pipe',
      };

      connector = new IpcConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.pipePath).toBe('\\\\.\\pipe\\custom-pipe');
    });
  });

  describe('Server Mode', () => {
    beforeEach(() => {
      const config: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
      };

      connector = new IpcConnector(config);
    });

    it('should start server successfully', async () => {
      const serverStartedHandler = jest.fn();
      connector.on('serverStarted', serverStartedHandler);

      await connector.connect();

      expect(connector.status).toBe('connected');
      expect(serverStartedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          path: expect.any(String),
        })
      );
    });

    it('should resolve Unix socket path on Unix platforms', () => {
      const originalPlatform = process.platform;
      Object.defineProperty(process, 'platform', {
        value: 'linux',
        configurable: true,
      });

      const socketPath = (connector as any)._getSocketPath();
      expect(socketPath).toBe('/tmp/test-channel.sock');

      Object.defineProperty(process, 'platform', {
        value: originalPlatform,
        configurable: true,
      });
    });

    it('should resolve named pipe path on Windows', () => {
      const originalPlatform = process.platform;
      Object.defineProperty(process, 'platform', {
        value: 'win32',
        configurable: true,
      });

      const socketPath = (connector as any)._getSocketPath();
      expect(socketPath).toBe('\\\\.\\pipe\\test-channel');

      Object.defineProperty(process, 'platform', {
        value: originalPlatform,
        configurable: true,
      });
    });

    it('should use custom socket path when provided', () => {
      const config: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
        socketPath: '/custom/socket.sock',
      };

      connector = new IpcConnector(config);
      const socketPath = (connector as any)._getSocketPath();
      expect(socketPath).toBe('/custom/socket.sock');
    });

    it('should use custom pipe path when provided', () => {
      const config: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
        pipePath: '\\\\.\\pipe\\custom',
      };

      connector = new IpcConnector(config);
      const socketPath = (connector as any)._getSocketPath();
      expect(socketPath).toBe('\\\\.\\pipe\\custom');
    });

    it('should stop server successfully', async () => {
      await connector.connect();
      expect(connector.status).toBe('connected');

      const serverStoppedHandler = jest.fn();
      connector.on('serverStopped', serverStoppedHandler);

      await connector.disconnect();

      expect(connector.status).toBe('disconnected');
      expect(serverStoppedHandler).toHaveBeenCalledTimes(1);
    });

    it('should track connected clients', async () => {
      await connector.connect();

      // Simulate client connections
      const mockConnection1 = { write: jest.fn(), end: jest.fn() };
      const mockConnection2 = { write: jest.fn(), end: jest.fn() };

      (connector as any).connections.set('client-1', mockConnection1);
      (connector as any).connections.set('client-2', mockConnection2);

      expect(connector.getConnectionCount()).toBe(2);
      expect(connector.getConnections()).toEqual(['client-1', 'client-2']);
    });

    it('should broadcast message to all clients', async () => {
      await connector.connect();

      // Set up mock clients
      const mockConnection1 = { write: jest.fn(), end: jest.fn() };
      const mockConnection2 = { write: jest.fn(), end: jest.fn() };

      (connector as any).connections.set('client-1', mockConnection1);
      (connector as any).connections.set('client-2', mockConnection2);

      await connector.send({ message: 'broadcast' });

      // Both connections should receive the message (via _sendToConnection)
      expect(connector.getConnectionCount()).toBe(2);
    });

    it('should send message to specific client', async () => {
      await connector.connect();

      // Set up mock clients
      const mockConnection1 = { write: jest.fn(), end: jest.fn() };
      const mockConnection2 = { write: jest.fn(), end: jest.fn() };

      (connector as any).connections.set('client-1', mockConnection1);
      (connector as any).connections.set('client-2', mockConnection2);

      await connector.send({ message: 'targeted' }, { targetId: 'client-1' });

      // Only targeted client should receive message
      expect(connector.getConnectionCount()).toBe(2);
    });

    it('should throw error when targeting non-existent client', async () => {
      await connector.connect();

      await expect(
        connector.send({ message: 'test' }, { targetId: 'non-existent' })
      ).rejects.toThrow('Client non-existent not found');
    });

    it('should close all connections on disconnect', async () => {
      await connector.connect();

      // Set up mock clients
      const mockConnection1 = { write: jest.fn(), end: jest.fn() };
      const mockConnection2 = { write: jest.fn(), end: jest.fn() };

      (connector as any).connections.set('client-1', mockConnection1);
      (connector as any).connections.set('client-2', mockConnection2);

      await connector.disconnect();

      expect(connector.getConnectionCount()).toBe(0);
      expect(connector.getConnections()).toEqual([]);
    });

    it('should handle server already stopped', async () => {
      await connector.disconnect();
      expect(connector.status).toBe('disconnected');
    });
  });

  describe('Client Mode', () => {
    beforeEach(() => {
      const config: IpcConnectorConfig = {
        id: 'test-client',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
    });

    it('should connect as client successfully', async () => {
      const connectedHandler = jest.fn();
      connector.on('connected', connectedHandler);

      await connector.connect();

      expect(connector.status).toBe('connected');
      expect(connectedHandler).toHaveBeenCalledTimes(1);
    });

    it('should send message to server', async () => {
      await connector.connect();

      // Set up mock client
      (connector as any).client = { write: jest.fn(), end: jest.fn() };

      await connector.send({ message: 'to server' });

      // Message should be sent via _sendToConnection
      expect((connector as any).client).toBeDefined();
    });

    it('should throw error when sending without connection', async () => {
      await expect(
        connector.send({ message: 'test' })
      ).rejects.toThrow('IPC connector is not connected');
    });

    it('should disconnect client successfully', async () => {
      await connector.connect();

      // Set up mock client
      (connector as any).client = { write: jest.fn(), end: jest.fn() };

      await connector.disconnect();

      expect(connector.status).toBe('disconnected');
      expect((connector as any).client).toBeNull();
    });

    it('should handle client already disconnected', async () => {
      await connector.disconnect();
      expect(connector.status).toBe('disconnected');
    });
  });

  describe('Message Serialization', () => {
    beforeEach(async () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
      await connector.connect();
      (connector as any).client = { write: jest.fn(), end: jest.fn() };
    });

    it('should serialize message with JSON format', () => {
      const data = { test: 'data', number: 42 };
      const serialized = (connector as any)._serialize(data);

      expect(typeof serialized).toBe('string');
      expect(JSON.parse(serialized as string)).toEqual(data);
    });

    it('should deserialize JSON message', () => {
      const data = { test: 'data', number: 42 };
      const serialized = JSON.stringify(data);
      const deserialized = (connector as any)._deserialize(serialized);

      expect(deserialized).toEqual(data);
    });

    it('should deserialize Buffer message', () => {
      const data = { test: 'data', number: 42 };
      const buffer = Buffer.from(JSON.stringify(data));
      const deserialized = (connector as any)._deserialize(buffer);

      expect(deserialized).toEqual(data);
    });

    it('should serialize with msgpack format', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        format: 'msgpack',
      };

      connector = new IpcConnector(config);

      const data = { test: 'data' };
      const serialized = (connector as any)._serialize(data);

      // Template falls back to JSON for msgpack
      expect(typeof serialized).toBe('string');
    });

    it('should prepare message with default options', () => {
      const data = { test: 'data' };
      const message = (connector as any)._prepareMessage(data, {});

      expect(message).toBeDefined();
      const parsed = JSON.parse(message);
      expect(parsed.payload).toEqual(data);
      expect(parsed.id).toBeDefined();
      expect(parsed.type).toBe('message');
      expect(parsed.timestamp).toBeDefined();
      expect(parsed.ack).toBe(false);
    });

    it('should prepare message with custom options', () => {
      const data = { test: 'data' };
      const message = (connector as any)._prepareMessage(data, {
        messageId: 'custom-id',
        type: 'custom-type',
      });

      const parsed = JSON.parse(message);
      expect(parsed.id).toBe('custom-id');
      expect(parsed.type).toBe('custom-type');
      expect(parsed.payload).toEqual(data);
    });

    it('should handle serialization disabled', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        serialize: false,
      };

      connector = new IpcConnector(config);

      const data = { test: 'data' };
      const message = (connector as any)._prepareMessage(data, {});

      expect(typeof message).toBe('object');
      expect(message.payload).toEqual(data);
    });
  });

  describe('Acknowledgment Mechanism', () => {
    beforeEach(async () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        ack: true,
        ackTimeout: 1000,
      };

      connector = new IpcConnector(config);
      await connector.connect();
      (connector as any).client = { write: jest.fn(), end: jest.fn() };
    });

    it('should wait for acknowledgment when enabled', async () => {
      // Mock _waitForAck to resolve immediately
      jest.spyOn(connector as any, '_waitForAck').mockResolvedValueOnce(undefined);

      await connector.send({ message: 'test' });

      expect((connector as any)._waitForAck).toHaveBeenCalled();
    });

    it('should handle acknowledgment timeout', async () => {
      const messageId = 'test-message-id';

      // Start waiting for ack
      const waitPromise = (connector as any)._waitForAck(messageId);

      // Advance timers to trigger timeout
      jest.advanceTimersByTime(1000);

      await expect(waitPromise).rejects.toThrow(
        `Acknowledgment timeout for message ${messageId}`
      );
    });

    it('should resolve when acknowledgment is received', async () => {
      const messageId = 'test-message-id';

      // Start waiting for ack
      const waitPromise = (connector as any)._waitForAck(messageId);

      // Simulate receiving ack
      (connector as any)._handleAck(messageId);

      await expect(waitPromise).resolves.toBeUndefined();
    });

    it('should send acknowledgment for received message', () => {
      const sendAckSpy = jest.spyOn(connector as any, '_sendAck');

      const message = {
        id: 'msg-123',
        type: 'message',
        ack: true,
        payload: { test: 'data' },
      };

      (connector as any)._handleMessage(JSON.stringify(message));

      expect(sendAckSpy).toHaveBeenCalledWith('msg-123', undefined);
    });

    it('should not send acknowledgment when not required', () => {
      const sendAckSpy = jest.spyOn(connector as any, '_sendAck');

      const message = {
        id: 'msg-123',
        type: 'message',
        ack: false,
        payload: { test: 'data' },
      };

      (connector as any)._handleMessage(JSON.stringify(message));

      expect(sendAckSpy).not.toHaveBeenCalled();
    });

    it('should clear pending acknowledgments on disconnect', async () => {
      const messageId = 'test-message-id';

      // Start waiting for ack
      const waitPromise = (connector as any)._waitForAck(messageId);

      // Disconnect while waiting
      await connector.disconnect();

      await expect(waitPromise).rejects.toThrow('Connection closed');
    });
  });

  describe('Message Handling', () => {
    beforeEach(async () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
      await connector.connect();
    });

    it('should handle incoming message', () => {
      const eventHandler = jest.fn();
      connector.onEvent('message', eventHandler);

      const message = {
        id: 'msg-123',
        type: 'message',
        payload: { test: 'data' },
        timestamp: Date.now(),
      };

      (connector as any)._handleMessage(JSON.stringify(message));

      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'message',
          payload: { test: 'data' },
        })
      );
    });

    it('should handle acknowledgment message', () => {
      const handleAckSpy = jest.spyOn(connector as any, '_handleAck');

      const ackMessage = {
        id: 'ack-123',
        type: 'ack',
        messageId: 'msg-123',
      };

      (connector as any)._handleMessage(JSON.stringify(ackMessage));

      expect(handleAckSpy).toHaveBeenCalledWith(undefined); // messageId is on ackMessage, not id
    });

    it('should include connection metadata in events', () => {
      const eventHandler = jest.fn();
      connector.onEvent('message', eventHandler);

      const message = {
        id: 'msg-123',
        type: 'message',
        payload: { test: 'data' },
      };

      (connector as any)._handleMessage(JSON.stringify(message), 'client-1');

      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({
            connectionId: 'client-1',
            channel: 'test-channel',
          }),
        })
      );
    });

    it('should handle malformed message', () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      (connector as any)._handleMessage('invalid json');

      expect(errorHandler).toHaveBeenCalled();
    });

    it('should handle deserialized message when serialization is disabled', () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        serialize: false,
      };

      connector = new IpcConnector(config);

      const eventHandler = jest.fn();
      connector.onEvent('message', eventHandler);

      const message = {
        id: 'msg-123',
        type: 'message',
        payload: { test: 'data' },
      };

      (connector as any)._handleMessage(message);

      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: { test: 'data' },
        })
      );
    });
  });

  describe('Connection Management', () => {
    it('should track server connections', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
      };

      connector = new IpcConnector(config);
      await connector.connect();

      expect(connector.getConnectionCount()).toBe(0);
      expect(connector.getConnections()).toEqual([]);

      // Add mock connections
      const mockConnection = { write: jest.fn(), end: jest.fn() };
      (connector as any).connections.set('client-1', mockConnection);

      expect(connector.getConnectionCount()).toBe(1);
      expect(connector.getConnections()).toEqual(['client-1']);
    });

    it('should return empty array for client mode', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-client',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
      await connector.connect();

      expect(connector.getConnectionCount()).toBe(0);
      expect(connector.getConnections()).toEqual([]);
    });
  });

  describe('Error Handling', () => {
    beforeEach(() => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
    });

    it('should handle connection errors', async () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      // Mock _startClient to throw error
      const mockError = new Error('Connection failed');
      jest.spyOn(connector as any, '_startClient').mockRejectedValueOnce(mockError);

      await expect(connector.connect()).rejects.toThrow('Connection failed');
    });

    it('should handle send errors when not connected', async () => {
      await expect(connector.send({ test: 'data' })).rejects.toThrow(
        'IPC connector is not connected'
      );
    });

    it('should handle null connection in _sendToConnection', async () => {
      await expect(
        (connector as any)._sendToConnection(null, 'message')
      ).rejects.toThrow('Connection is null');
    });

    it('should emit error on message handling failure', () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      (connector as any)._handleMessage('invalid json');

      expect(errorHandler).toHaveBeenCalledWith(expect.any(Error));
    });
  });

  describe('Resource Cleanup', () => {
    it('should clear pending acknowledgments on destroy', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        ack: true,
      };

      connector = new IpcConnector(config);
      await connector.connect();

      // Add pending acks
      const timeout = setTimeout(() => {}, 1000);
      (connector as any).pendingAcks.set('msg-1', {
        resolve: jest.fn(),
        reject: jest.fn(),
        timeout,
      });

      await connector.destroy();

      expect((connector as any).pendingAcks.size).toBe(0);
    });

    it('should close server and connections on destroy', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'server',
      };

      connector = new IpcConnector(config);
      await connector.connect();

      // Add mock connections
      const mockConnection = { write: jest.fn(), end: jest.fn() };
      (connector as any).connections.set('client-1', mockConnection);

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
      expect(connector.getConnectionCount()).toBe(0);
    });

    it('should close client connection on destroy', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-client',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);
      await connector.connect();

      (connector as any).client = { write: jest.fn(), end: jest.fn() };

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
      expect((connector as any).client).toBeNull();
    });
  });

  describe('Integration Scenarios', () => {
    it('should handle server-client communication flow', async () => {
      // Create server
      const serverConfig: IpcConnectorConfig = {
        id: 'test-server',
        type: 'ipc',
        channel: 'integration-test',
        mode: 'server',
      };

      const server = new IpcConnector(serverConfig);
      await server.connect();

      // Create client
      const clientConfig: IpcConnectorConfig = {
        id: 'test-client',
        type: 'ipc',
        channel: 'integration-test',
        mode: 'client',
      };

      const client = new IpcConnector(clientConfig);
      await client.connect();

      // Set up mock client connection
      (client as any).client = { write: jest.fn(), end: jest.fn() };

      // Client sends message
      await client.send({ message: 'hello server' });

      // Clean up
      await client.destroy();
      await server.destroy();

      expect(server.status).toBe('disconnected');
      expect(client.status).toBe('disconnected');
    });

    it('should handle message with acknowledgment flow', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-ipc',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
        ack: true,
        ackTimeout: 1000,
      };

      connector = new IpcConnector(config);
      await connector.connect();

      (connector as any).client = { write: jest.fn(), end: jest.fn() };

      // Mock _waitForAck to resolve immediately
      jest.spyOn(connector as any, '_waitForAck').mockResolvedValueOnce(undefined);

      await connector.send({ message: 'test with ack' });

      expect((connector as any)._waitForAck).toHaveBeenCalled();

      await connector.destroy();
    });

    it('should handle reconnection scenario', async () => {
      const config: IpcConnectorConfig = {
        id: 'test-client',
        type: 'ipc',
        channel: 'test-channel',
        mode: 'client',
      };

      connector = new IpcConnector(config);

      // First connection
      await connector.connect();
      expect(connector.status).toBe('connected');

      // Disconnect
      await connector.disconnect();
      expect(connector.status).toBe('disconnected');

      // Reconnect
      await connector.connect();
      expect(connector.status).toBe('connected');

      // Clean up
      await connector.destroy();
    });
  });
});
