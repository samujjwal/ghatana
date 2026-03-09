/**
 * @fileoverview Comprehensive unit tests for WebSocketConnector
 *
 * Tests cover:
 * - Connection lifecycle and reconnection
 * - WebSocket protocol handling
 * - Ping/pong heartbeat mechanism
 * - Message handling (text, binary, JSON)
 * - Message queuing while disconnected
 * - Authentication (basic, bearer, API key)
 * - Error handling and event emission
 * - Resource cleanup
 */

import { WebSocketConnector, WebSocketConnectorConfig } from '../../../src/connectors/WebSocketConnector';
import { EventEmitter } from 'events';

// Mock WebSocket module
const mockWs = jest.fn();
const mockWebSocket = {
  on: jest.fn(),
  send: jest.fn(),
  close: jest.fn(),
  terminate: jest.fn(),
  ping: jest.fn(),
  pong: jest.fn(),
  removeAllListeners: jest.fn(),
  readyState: 1, // OPEN
};

jest.mock('ws', () => mockWs);

describe('WebSocketConnector', () => {
  let connector: WebSocketConnector;
  let config: WebSocketConnectorConfig;
  let mockSocket: any;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    config = {
      id: 'ws-test',
      type: 'websocket',
      url: 'ws://localhost:8080',
      autoReconnect: true,
      reconnectionDelay: 1000,
      maxReconnectionAttempts: 3,
      queueMessages: true,
      maxQueueSize: 10,
      pingPong: true,
      pingInterval: 30000,
      pongTimeout: 5000,
    };

    mockSocket = {
      ...mockWebSocket,
      on: jest.fn(),
      send: jest.fn(),
      close: jest.fn(),
      terminate: jest.fn(),
      ping: jest.fn(),
      pong: jest.fn(),
      removeAllListeners: jest.fn(),
      readyState: 1,
    };

    mockWs.mockReturnValue(mockSocket);
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
    jest.useRealTimers();
  });

  describe('Constructor', () => {
    it('should create connector with config', () => {
      connector = new WebSocketConnector(config);

      expect(connector.id).toBe('ws-test');
      expect(connector.type).toBe('websocket');
    });

    it('should apply default config values', () => {
      connector = new WebSocketConnector({
        id: 'test',
        type: 'websocket',
        url: 'ws://localhost:8080',
      });

      expect(connector.type).toBe('websocket');
    });

    it('should override type to websocket', () => {
      const customConfig = { ...config, type: 'custom' as any };
      connector = new WebSocketConnector(customConfig);

      expect(connector.type).toBe('websocket');
    });
  });

  describe('Connection', () => {
    describe('connect()', () => {
      it('should establish WebSocket connection', async () => {
        connector = new WebSocketConnector(config);

        const connectPromise = connector.connect();

        // Simulate 'open' event
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;

        expect(mockWs).toHaveBeenCalledWith(
          expect.stringContaining('ws://localhost:8080'),
          expect.any(Object)
        );
        expect(connector.status).toBe('connected');
      });

      it('should set status to connecting during connection', () => {
        connector = new WebSocketConnector(config);

        connector.connect();

        expect(connector.status).toBe('connecting');
      });

      it('should emit connected event on successful connection', async () => {
        connector = new WebSocketConnector(config);
        const connectedListener = jest.fn();
        connector.on('connected', connectedListener);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;

        expect(connectedListener).toHaveBeenCalled();
      });

      it('should return early if already connected', async () => {
        connector = new WebSocketConnector(config);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;
        mockWs.mockClear();

        await connector.connect();

        expect(mockWs).not.toHaveBeenCalled();
      });

      it('should handle connection errors', async () => {
        connector = new WebSocketConnector(config);
        const error = new Error('Connection failed');

        const connectPromise = connector.connect();
        const errorHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'error')?.[1];
        errorHandler?.(error);

        await expect(connectPromise).rejects.toThrow('Connection failed');
        expect(connector.status).toBe('error');
      });

      it('should apply authentication to URL for basic auth', async () => {
        const authConfig = {
          ...config,
          auth: {
            type: 'basic' as const,
            username: 'user',
            password: 'pass',
          },
        };
        connector = new WebSocketConnector(authConfig);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;

        const calledUrl = mockWs.mock.calls[0][0];
        expect(calledUrl).toContain('user');
        expect(calledUrl).toContain('pass');
      });

      it('should apply bearer token to URL query params', async () => {
        const authConfig = {
          ...config,
          auth: {
            type: 'bearer' as const,
            token: 'secret-token',
          },
        };
        connector = new WebSocketConnector(authConfig);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;

        const calledUrl = mockWs.mock.calls[0][0];
        expect(calledUrl).toContain('token=secret-token');
      });

      it('should apply API key to URL query params', async () => {
        const authConfig = {
          ...config,
          auth: {
            type: 'api_key' as const,
            apiKey: 'key123',
            paramName: 'apikey',
          },
        };
        connector = new WebSocketConnector(authConfig);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();

        await connectPromise;

        const calledUrl = mockWs.mock.calls[0][0];
        expect(calledUrl).toContain('apikey=key123');
      });
    });

    describe('disconnect()', () => {
      it('should close WebSocket connection', async () => {
        connector = new WebSocketConnector(config);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();
        await connectPromise;

        await connector.disconnect();

        expect(mockSocket.close).toHaveBeenCalledWith(1000, 'Normal closure');
        expect(connector.status).toBe('disconnected');
      });

      it('should cleanup socket listeners', async () => {
        connector = new WebSocketConnector(config);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();
        await connectPromise;

        await connector.disconnect();

        expect(mockSocket.removeAllListeners).toHaveBeenCalled();
      });

      it('should cancel reconnection attempts', async () => {
        connector = new WebSocketConnector(config);

        const connectPromise = connector.connect();
        const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
        openHandler?.();
        await connectPromise;

        // Trigger close to start reconnection
        const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
        closeHandler?.(1000, 'Test close');

        await connector.disconnect();

        // Advance timers - no reconnection should happen
        jest.advanceTimersByTime(10000);

        expect(mockWs).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('Message Handling', () => {
    beforeEach(async () => {
      connector = new WebSocketConnector(config);
      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;
    });

    it('should parse JSON messages', () => {
      const messageHandler = jest.fn();
      connector.onEvent('message', messageHandler);

      const jsonData = { test: 'data', number: 42 };
      const messageListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'message')?.[1];
      messageListener?.(JSON.stringify(jsonData));

      expect(messageHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: jsonData,
          type: 'message',
        })
      );
    });

    it('should handle text messages', () => {
      const messageHandler = jest.fn();
      connector.onEvent('message', messageHandler);

      const textData = 'plain text message';
      const messageListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'message')?.[1];
      messageListener?.(textData);

      expect(messageHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: textData,
          type: 'message',
        })
      );
    });

    it('should handle binary messages', () => {
      const messageHandler = jest.fn();
      connector.onEvent('message', messageHandler);

      const buffer = Buffer.from([1, 2, 3, 4]);
      const messageListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'message')?.[1];
      messageListener?.(buffer);

      expect(messageHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.any(Buffer),
          type: 'message',
        })
      );
    });

    it('should handle ArrayBuffer messages', () => {
      const messageHandler = jest.fn();
      connector.onEvent('message', messageHandler);

      const arrayBuffer = new ArrayBuffer(8);
      const messageListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'message')?.[1];
      messageListener?.(arrayBuffer);

      expect(messageHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'message',
        })
      );
    });

    it('should emit error on message parsing failure', () => {
      const errorHandler = jest.fn();
      connector.onEvent('error', errorHandler);

      const messageListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'message')?.[1];

      // Create a mock message that will cause an error in processing
      const badMessage = { toString: () => { throw new Error('Parse error'); } };
      messageListener?.(badMessage);

      expect(errorHandler).toHaveBeenCalled();
    });
  });

  describe('send()', () => {
    beforeEach(async () => {
      connector = new WebSocketConnector(config);
      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;
    });

    it('should send string messages', async () => {
      const message = 'test message';

      await connector.send(message);

      expect(mockSocket.send).toHaveBeenCalledWith(message, undefined);
    });

    it('should send object messages as JSON', async () => {
      const message = { test: 'data', number: 42 };

      await connector.send(message);

      expect(mockSocket.send).toHaveBeenCalledWith(JSON.stringify(message), undefined);
    });

    it('should send binary data', async () => {
      const buffer = Buffer.from([1, 2, 3, 4]);

      await connector.send(buffer);

      expect(mockSocket.send).toHaveBeenCalledWith(buffer, undefined);
    });

    it('should send ArrayBuffer data', async () => {
      const arrayBuffer = new ArrayBuffer(8);

      await connector.send(arrayBuffer);

      expect(mockSocket.send).toHaveBeenCalledWith(arrayBuffer, undefined);
    });

    it('should queue messages when not connected', async () => {
      await connector.disconnect();

      mockSocket.readyState = 3; // CLOSED
      await connector.send({ test: 'data' });

      expect(mockSocket.send).not.toHaveBeenCalled();
    });

    it('should throw error when queuing is disabled and not connected', async () => {
      const noQueueConfig = { ...config, queueMessages: false };
      connector = new WebSocketConnector(noQueueConfig);

      await expect(connector.send({ test: 'data' })).rejects.toThrow('WebSocket is not connected');
    });

    it('should respect max queue size', async () => {
      const smallQueueConfig = { ...config, maxQueueSize: 2 };
      connector = new WebSocketConnector(smallQueueConfig);

      mockSocket.readyState = 3; // CLOSED

      await connector.send({ msg: 1 });
      await connector.send({ msg: 2 });

      await expect(connector.send({ msg: 3 })).rejects.toThrow('WebSocket is not connected');
    });

    it('should process queued messages on reconnection', async () => {
      await connector.disconnect();
      mockSocket.readyState = 3; // CLOSED

      await connector.send({ msg: 1 });
      await connector.send({ msg: 2 });

      // Reconnect
      mockSocket.readyState = 1; // OPEN
      mockSocket.send.mockClear();
      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      // Wait for queue processing
      await Promise.resolve();

      expect(mockSocket.send).toHaveBeenCalledTimes(2);
    });

    it('should handle send errors', async () => {
      const error = new Error('Send failed');
      mockSocket.send.mockImplementation(() => { throw error; });

      await expect(connector.send({ test: 'data' })).rejects.toThrow('Send failed');
    });
  });

  describe('Ping/Pong Mechanism', () => {
    beforeEach(async () => {
      connector = new WebSocketConnector({
        ...config,
        pingPong: true,
        pingInterval: 1000,
        pongTimeout: 500,
      });
      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;
    });

    it('should send ping at configured interval', () => {
      jest.advanceTimersByTime(1000);

      expect(mockSocket.ping).toHaveBeenCalled();
    });

    it('should emit ping event', () => {
      const pingHandler = jest.fn();
      connector.on('ping', pingHandler);

      jest.advanceTimersByTime(1000);

      expect(pingHandler).toHaveBeenCalled();
    });

    it('should respond to ping with pong', () => {
      const pingData = Buffer.from('ping');
      const pingListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'ping')?.[1];

      pingListener?.(pingData);

      expect(mockSocket.pong).toHaveBeenCalledWith(pingData);
    });

    it('should emit pong event on receiving pong', () => {
      const pongHandler = jest.fn();
      connector.on('pong', pongHandler);

      const pongData = Buffer.from('pong');
      const pongListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'pong')?.[1];

      pongListener?.(pongData);

      expect(pongHandler).toHaveBeenCalledWith(pongData);
    });

    it('should terminate connection on pong timeout', () => {
      jest.advanceTimersByTime(1000); // Trigger ping
      jest.advanceTimersByTime(500); // Wait for pong timeout

      expect(mockSocket.terminate).toHaveBeenCalled();
    });

    it('should clear pong timeout on receiving pong', () => {
      jest.advanceTimersByTime(1000); // Trigger ping

      const pongData = Buffer.from('pong');
      const pongListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'pong')?.[1];
      pongListener?.(pongData);

      mockSocket.terminate.mockClear();
      jest.advanceTimersByTime(500);

      expect(mockSocket.terminate).not.toHaveBeenCalled();
    });

    it('should cleanup ping/pong timers on disconnect', async () => {
      await connector.disconnect();

      mockSocket.ping.mockClear();
      jest.advanceTimersByTime(10000);

      expect(mockSocket.ping).not.toHaveBeenCalled();
    });

    it('should not setup ping/pong when disabled', async () => {
      const noPingConfig = { ...config, pingPong: false };
      connector = new WebSocketConnector(noPingConfig);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      jest.advanceTimersByTime(10000);

      expect(mockSocket.ping).not.toHaveBeenCalled();
    });
  });

  describe('Reconnection', () => {
    it('should attempt reconnection on close', async () => {
      connector = new WebSocketConnector(config);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      mockWs.mockClear();
      mockSocket.on.mockClear();

      // Simulate close
      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
      closeHandler?.(1006, 'Abnormal closure');

      jest.advanceTimersByTime(1000);

      expect(mockWs).toHaveBeenCalled();
    });

    it('should use exponential backoff for reconnection', async () => {
      connector = new WebSocketConnector(config);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];

      // First reconnection
      closeHandler?.(1006, 'Test');
      jest.advanceTimersByTime(1000);

      // Second reconnection
      mockSocket.on.mockClear();
      const errorHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'error')?.[1];
      if (errorHandler) {
        errorHandler(new Error('Connection failed'));
      }
      jest.advanceTimersByTime(1500); // Should be 1000 * 1.5

      expect(mockWs).toHaveBeenCalledTimes(2);
    });

    it('should emit reconnecting event', async () => {
      connector = new WebSocketConnector(config);
      const reconnectingHandler = jest.fn();
      connector.on('reconnecting', reconnectingHandler);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
      closeHandler?.(1006, 'Test');

      expect(reconnectingHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          attempt: 1,
          delay: expect.any(Number),
        })
      );
    });

    it('should stop reconnecting after max attempts', async () => {
      const limitedConfig = { ...config, maxReconnectionAttempts: 2 };
      connector = new WebSocketConnector(limitedConfig);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];

      // First attempt
      closeHandler?.(1006, 'Test');
      jest.advanceTimersByTime(1000);

      // Second attempt
      mockSocket.on.mockClear();
      jest.advanceTimersByTime(1500);

      mockWs.mockClear();
      jest.advanceTimersByTime(10000);

      expect(mockWs).not.toHaveBeenCalled();
    });

    it('should not reconnect when autoReconnect is false', async () => {
      const noReconnectConfig = { ...config, autoReconnect: false };
      connector = new WebSocketConnector(noReconnectConfig);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      mockWs.mockClear();

      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
      closeHandler?.(1000, 'Normal');

      jest.advanceTimersByTime(10000);

      expect(mockWs).not.toHaveBeenCalled();
    });

    it('should reset reconnection attempts on successful connection', async () => {
      connector = new WebSocketConnector(config);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      // Trigger reconnection
      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
      closeHandler?.(1006, 'Test');
      jest.advanceTimersByTime(1000);

      // Simulate successful reconnection
      mockSocket.on.mockClear();
      const newOpenHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      if (newOpenHandler) {
        newOpenHandler();
      }

      // Next reconnection should use initial delay
      mockSocket.on.mockClear();
      closeHandler?.(1006, 'Test again');
      jest.advanceTimersByTime(1000);

      expect(mockWs).toHaveBeenCalled();
    });
  });

  describe('Error Handling', () => {
    it('should emit error events', async () => {
      connector = new WebSocketConnector(config);
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      const error = new Error('WebSocket error');
      const connectPromise = connector.connect();
      const errorListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'error')?.[1];
      errorListener?.(error);

      await expect(connectPromise).rejects.toThrow('WebSocket error');
      expect(errorHandler).toHaveBeenCalledWith(error);
    });

    it('should set error status on error', async () => {
      connector = new WebSocketConnector(config);

      const error = new Error('Connection error');
      const connectPromise = connector.connect();
      const errorListener = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'error')?.[1];
      errorListener?.(error);

      await expect(connectPromise).rejects.toThrow();
      expect(connector.status).toBe('error');
    });

    it('should handle close events', async () => {
      connector = new WebSocketConnector(config);
      const disconnectedHandler = jest.fn();
      connector.on('disconnected', disconnectedHandler);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      const closeHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'close')?.[1];
      closeHandler?.(1000, 'Normal closure');

      expect(disconnectedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          code: 1000,
          reason: 'Normal closure',
          wasConnected: true,
        })
      );
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      connector = new WebSocketConnector(config);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      await connector.destroy();

      expect(mockSocket.close).toHaveBeenCalled();
      expect(connector.status).toBe('disconnected');
    });

    it('should clear message queue on destroy', async () => {
      connector = new WebSocketConnector(config);
      mockSocket.readyState = 3; // CLOSED

      await connector.send({ msg: 1 });
      await connector.send({ msg: 2 });

      await connector.destroy();

      // After destroy, queue should be cleared
      expect(connector.status).toBe('disconnected');
    });

    it('should cleanup timers on destroy', async () => {
      connector = new WebSocketConnector({
        ...config,
        pingPong: true,
        pingInterval: 1000,
      });

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      await connector.destroy();

      mockSocket.ping.mockClear();
      jest.advanceTimersByTime(10000);

      expect(mockSocket.ping).not.toHaveBeenCalled();
    });
  });

  describe('Socket Access', () => {
    it('should provide access to underlying socket', async () => {
      connector = new WebSocketConnector(config);

      const connectPromise = connector.connect();
      const openHandler = mockSocket.on.mock.calls.find((call: any[]) => call[0] === 'open')?.[1];
      openHandler?.();
      await connectPromise;

      expect(connector.socket).toBe(mockSocket);
    });

    it('should return null when not connected', () => {
      connector = new WebSocketConnector(config);

      expect(connector.socket).toBeNull();
    });
  });
});
