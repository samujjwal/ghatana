/**
 * @fileoverview Comprehensive unit tests for BaseConnector
 *
 * Tests cover:
 * - Constructor initialization and defaults
 * - Connection lifecycle (connect, disconnect, reconnect)
 * - State transitions and event emission
 * - Event handler registration and cleanup
 * - Configuration validation and updates
 * - Retry and backoff logic
 * - Error handling and recovery
 * - Resource cleanup
 */

import { BaseConnector } from '../../../src/BaseConnector';
import { ConnectionOptions, ConnectionStatus, Event } from '../../../src/types';

/**
 * Concrete implementation of BaseConnector for testing.
 * Allows control over connection behavior through mock functions.
 */
class TestConnector extends BaseConnector<ConnectionOptions> {
  public connectMock = jest.fn();
  public disconnectMock = jest.fn();
  public sendMock = jest.fn();

  protected async _connect(): Promise<void> {
    return this.connectMock();
  }

  protected async _disconnect(): Promise<void> {
    return this.disconnectMock();
  }

  public async send(data: any, options?: Record<string, any>): Promise<void> {
    return this.sendMock(data, options);
  }

  // Expose protected members for testing
  public getStatus(): ConnectionStatus {
    return this._status;
  }

  public getReconnectAttempts(): number {
    return this._reconnectAttempts;
  }

  public getConnectionRetries(): number {
    return this._connectionRetries;
  }

  public setIsDisposing(value: boolean): void {
    this._isDisposing = value;
  }

  public emitTestEvent(event: Event<any>): void {
    this._emitEvent(event);
  }

  public handleTestConnectionError(error: Error): void {
    this._handleConnectionError(error);
  }
}

describe('BaseConnector', () => {
  let connector: TestConnector;
  let config: ConnectionOptions;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    config = {
      id: 'test-connector',
      type: 'test',
      maxRetries: 3,
      timeout: 5000,
      secure: true,
      headers: { 'X-Test': 'value' },
      debug: false,
    };
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
    jest.useRealTimers();
  });

  describe('Constructor', () => {
    it('should initialize with provided configuration', () => {
      connector = new TestConnector(config);

      expect(connector.id).toBe('test-connector');
      expect(connector.type).toBe('test');
      expect(connector.status).toBe('disconnected');
    });

    it('should generate unique ID when not provided', () => {
      const configWithoutId = { ...config, id: undefined as any };
      connector = new TestConnector(configWithoutId);

      expect(connector.id).toMatch(/^connector-/);
      expect(connector.id).toHaveLength(46); // 'connector-' + UUID
    });

    it('should apply default configuration values', () => {
      const minimalConfig: ConnectionOptions = {
        id: 'minimal',
        type: 'test',
      };
      connector = new TestConnector(minimalConfig);

      const retrievedConfig = connector.getConfig();
      expect(retrievedConfig.maxRetries).toBe(3);
      expect(retrievedConfig.timeout).toBe(30000);
      expect(retrievedConfig.secure).toBe(true);
      expect(retrievedConfig.debug).toBe(false);
      expect(retrievedConfig.headers).toEqual({});
    });

    it('should override default values with provided config', () => {
      connector = new TestConnector(config);

      const retrievedConfig = connector.getConfig();
      expect(retrievedConfig.timeout).toBe(5000);
      expect(retrievedConfig.headers).toEqual({ 'X-Test': 'value' });
    });

    it('should initialize with disconnected status', () => {
      connector = new TestConnector(config);
      expect(connector.status).toBe('disconnected');
    });

    it('should initialize event handlers map', () => {
      connector = new TestConnector(config);
      const handler = jest.fn();
      connector.onEvent('test', handler);

      // Should not throw, indicating map is initialized
      connector.offEvent('test', handler);
    });
  });

  describe('Connection Lifecycle', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    describe('connect()', () => {
      it('should transition from disconnected to connecting to connected', async () => {
        const statuses: ConnectionStatus[] = [];
        connector.on('statusChanged', ({ newStatus }: any) => {
          statuses.push(newStatus);
        });

        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        expect(statuses).toEqual(['connecting', 'connected']);
        expect(connector.status).toBe('connected');
      });

      it('should emit connecting event when starting connection', async () => {
        const connectingListener = jest.fn();
        connector.on('connecting', connectingListener);

        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        expect(connectingListener).toHaveBeenCalledTimes(1);
      });

      it('should emit connected event on successful connection', async () => {
        const connectedListener = jest.fn();
        connector.on('connected', connectedListener);

        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        expect(connectedListener).toHaveBeenCalledTimes(1);
      });

      it('should call _connect() implementation', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        expect(connector.connectMock).toHaveBeenCalledTimes(1);
      });

      it('should return immediately if already connected', async () => {
        connector.connectMock.mockResolvedValue(undefined);

        await connector.connect();
        connector.connectMock.mockClear();

        await connector.connect();
        expect(connector.connectMock).not.toHaveBeenCalled();
      });

      it('should return same promise if connection in progress', async () => {
        connector.connectMock.mockImplementation(() =>
          new Promise(resolve => setTimeout(resolve, 100))
        );

        const promise1 = connector.connect();
        const promise2 = connector.connect();

        expect(promise1).toBe(promise2);

        jest.advanceTimersByTime(100);
        await promise1;
      });

      it('should reset reconnect attempts on successful connection', async () => {
        connector.connectMock.mockResolvedValue(undefined);

        // Simulate previous failed attempts
        connector.handleTestConnectionError(new Error('Test'));
        expect(connector.getReconnectAttempts()).toBeGreaterThan(0);

        await connector.connect();
        expect(connector.getReconnectAttempts()).toBe(0);
      });

      it('should reset connection retries on successful connection', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        expect(connector.getConnectionRetries()).toBe(0);
      });

      it('should transition to error status on connection failure', async () => {
        const error = new Error('Connection failed');
        connector.connectMock.mockRejectedValue(error);

        await expect(connector.connect()).rejects.toThrow('Connection failed');
        expect(connector.status).toBe('error');
      });

      it('should emit error event on connection failure', async () => {
        const errorListener = jest.fn();
        connector.on('error', errorListener);

        const error = new Error('Connection failed');
        connector.connectMock.mockRejectedValue(error);

        await expect(connector.connect()).rejects.toThrow();
        expect(errorListener).toHaveBeenCalledWith(error);
      });

      it('should handle connection errors and trigger reconnection', async () => {
        const reconnectingListener = jest.fn();
        connector.on('reconnecting', reconnectingListener);

        const error = new Error('Connection failed');
        connector.connectMock.mockRejectedValue(error);

        await expect(connector.connect()).rejects.toThrow();
        expect(reconnectingListener).toHaveBeenCalled();
      });

      it('should clear connection promise after connection attempt', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        await connector.connect();

        // Should be able to connect again without using cached promise
        connector.connectMock.mockClear();
        await connector.disconnect();
        await connector.connect();

        expect(connector.connectMock).toHaveBeenCalled();
      });
    });

    describe('disconnect()', () => {
      it('should transition from connected to disconnected', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();
        await connector.disconnect();

        expect(connector.status).toBe('disconnected');
      });

      it('should emit disconnected event', async () => {
        const disconnectedListener = jest.fn();
        connector.on('disconnected', disconnectedListener);

        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();
        await connector.disconnect();

        expect(disconnectedListener).toHaveBeenCalledTimes(1);
      });

      it('should call _disconnect() implementation', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();
        await connector.disconnect();

        expect(connector.disconnectMock).toHaveBeenCalledTimes(1);
      });

      it('should return immediately if already disconnected', async () => {
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.disconnect();
        expect(connector.disconnectMock).not.toHaveBeenCalled();
      });

      it('should set disposing flag during disconnection', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockImplementation(async () => {
          // Check flag is set during disconnect
          expect((connector as any)._isDisposing).toBe(true);
        });

        await connector.connect();
        await connector.disconnect();
      });

      it('should clear disposing flag after disconnection', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();
        await connector.disconnect();

        expect((connector as any)._isDisposing).toBe(false);
      });

      it('should clear pending reconnection timeout', async () => {
        connector.connectMock.mockRejectedValue(new Error('Fail'));
        connector.disconnectMock.mockResolvedValue(undefined);

        // Trigger reconnection attempt
        await expect(connector.connect()).rejects.toThrow();

        // Disconnect should clear the timeout
        await connector.disconnect();

        // Advance timers - should not trigger reconnection
        connector.connectMock.mockClear();
        jest.advanceTimersByTime(10000);

        expect(connector.connectMock).not.toHaveBeenCalled();
      });

      it('should emit error event on disconnection failure', async () => {
        const errorListener = jest.fn();
        connector.on('error', errorListener);

        connector.connectMock.mockResolvedValue(undefined);
        const error = new Error('Disconnect failed');
        connector.disconnectMock.mockRejectedValue(error);

        await connector.connect();
        await expect(connector.disconnect()).rejects.toThrow('Disconnect failed');

        expect(errorListener).toHaveBeenCalledWith(error);
      });

      it('should set error status on disconnection failure', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockRejectedValue(new Error('Disconnect failed'));

        await connector.connect();
        await expect(connector.disconnect()).rejects.toThrow();

        expect(connector.status).toBe('error');
      });

      it('should clear disposing flag even if disconnection fails', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockRejectedValue(new Error('Fail'));

        await connector.connect();
        await expect(connector.disconnect()).rejects.toThrow();

        expect((connector as any)._isDisposing).toBe(false);
      });
    });

    describe('Reconnection Logic', () => {
      it('should attempt reconnection on connection failure', async () => {
        const reconnectingListener = jest.fn();
        connector.on('reconnecting', reconnectingListener);

        connector.connectMock.mockRejectedValueOnce(new Error('Fail'));

        await expect(connector.connect()).rejects.toThrow();

        expect(reconnectingListener).toHaveBeenCalledWith({
          attempt: 1,
          maxAttempts: 5,
          delay: 1000,
        });
      });

      it('should use exponential backoff for reconnection delays', async () => {
        const reconnectingListener = jest.fn();
        connector.on('reconnecting', reconnectingListener);

        for (let i = 0; i < 3; i++) {
          connector.connectMock.mockRejectedValueOnce(new Error('Fail'));
          await expect(connector.connect()).rejects.toThrow();
        }

        expect(reconnectingListener).toHaveBeenNthCalledWith(1, {
          attempt: 1,
          maxAttempts: 5,
          delay: 1000, // 1000 * 2^0
        });
        expect(reconnectingListener).toHaveBeenNthCalledWith(2, {
          attempt: 2,
          maxAttempts: 5,
          delay: 2000, // 1000 * 2^1
        });
        expect(reconnectingListener).toHaveBeenNthCalledWith(3, {
          attempt: 3,
          maxAttempts: 5,
          delay: 4000, // 1000 * 2^2
        });
      });

      it('should automatically retry connection after delay', async () => {
        connector.connectMock
          .mockRejectedValueOnce(new Error('Fail'))
          .mockResolvedValueOnce(undefined);

        await expect(connector.connect()).rejects.toThrow();

        connector.connectMock.mockClear();
        jest.advanceTimersByTime(1000);

        // Wait for reconnection promise
        await jest.runAllTimersAsync();

        expect(connector.connectMock).toHaveBeenCalled();
      });

      it('should emit reconnectFailed after max attempts', async () => {
        const reconnectFailedListener = jest.fn();
        connector.on('reconnectFailed', reconnectFailedListener);

        connector.connectMock.mockRejectedValue(new Error('Fail'));

        // Attempt connection 6 times (initial + 5 reconnects)
        for (let i = 0; i < 6; i++) {
          await expect(connector.connect()).rejects.toThrow();
        }

        expect(reconnectFailedListener).toHaveBeenCalledWith({
          attempts: 5,
          error: expect.any(Error),
        });
      });

      it('should not reconnect if disposing', async () => {
        connector.connectMock.mockRejectedValue(new Error('Fail'));

        connector.setIsDisposing(true);
        await expect(connector.connect()).rejects.toThrow();

        jest.advanceTimersByTime(10000);
        connector.connectMock.mockClear();

        expect(connector.connectMock).not.toHaveBeenCalled();
      });

      it('should stop reconnection after reaching max attempts', async () => {
        connector.connectMock.mockRejectedValue(new Error('Fail'));

        // Exhaust max attempts
        for (let i = 0; i < 6; i++) {
          await expect(connector.connect()).rejects.toThrow();
        }

        connector.connectMock.mockClear();
        jest.advanceTimersByTime(100000);

        expect(connector.connectMock).not.toHaveBeenCalled();
      });
    });
  });

  describe('Event Handling', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    describe('onEvent() and offEvent()', () => {
      it('should register event handler', () => {
        const handler = jest.fn();
        connector.onEvent('message', handler);

        const event: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test',
        };

        connector.emitTestEvent(event);
        expect(handler).toHaveBeenCalledWith(event);
      });

      it('should support multiple handlers for same event type', () => {
        const handler1 = jest.fn();
        const handler2 = jest.fn();

        connector.onEvent('message', handler1);
        connector.onEvent('message', handler2);

        const event: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test',
        };

        connector.emitTestEvent(event);
        expect(handler1).toHaveBeenCalledWith(event);
        expect(handler2).toHaveBeenCalledWith(event);
      });

      it('should support wildcard event handlers', () => {
        const handler = jest.fn();
        connector.onEvent('*', handler);

        const event1: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test1',
        };

        const event2: Event<string> = {
          id: '2',
          type: 'other',
          timestamp: Date.now(),
          payload: 'test2',
        };

        connector.emitTestEvent(event1);
        connector.emitTestEvent(event2);

        expect(handler).toHaveBeenCalledTimes(2);
        expect(handler).toHaveBeenCalledWith(event1);
        expect(handler).toHaveBeenCalledWith(event2);
      });

      it('should unregister event handler', () => {
        const handler = jest.fn();
        connector.onEvent('message', handler);
        connector.offEvent('message', handler);

        const event: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test',
        };

        connector.emitTestEvent(event);
        expect(handler).not.toHaveBeenCalled();
      });

      it('should only unregister specified handler', () => {
        const handler1 = jest.fn();
        const handler2 = jest.fn();

        connector.onEvent('message', handler1);
        connector.onEvent('message', handler2);
        connector.offEvent('message', handler1);

        const event: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test',
        };

        connector.emitTestEvent(event);
        expect(handler1).not.toHaveBeenCalled();
        expect(handler2).toHaveBeenCalledWith(event);
      });

      it('should handle unregistering non-existent handler gracefully', () => {
        const handler = jest.fn();

        expect(() => {
          connector.offEvent('message', handler);
        }).not.toThrow();
      });

      it('should clean up empty handler sets', () => {
        const handler = jest.fn();
        connector.onEvent('message', handler);
        connector.offEvent('message', handler);

        // Internal map should be cleaned up
        const eventHandlers = (connector as any)._eventHandlers;
        expect(eventHandlers.has('message')).toBe(false);
      });

      it('should emit error when handler throws', () => {
        const errorListener = jest.fn();
        connector.on('error', errorListener);

        const error = new Error('Handler error');
        const faultyHandler = jest.fn(() => {
          throw error;
        });

        connector.onEvent('message', faultyHandler);

        const event: Event<string> = {
          id: '1',
          type: 'message',
          timestamp: Date.now(),
          payload: 'test',
        };

        connector.emitTestEvent(event);
        expect(errorListener).toHaveBeenCalledWith(error);
      });
    });
  });

  describe('Configuration Management', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    describe('getConfig()', () => {
      it('should return copy of configuration', () => {
        const retrievedConfig = connector.getConfig();

        expect(retrievedConfig).toEqual(expect.objectContaining({
          id: 'test-connector',
          type: 'test',
          maxRetries: 3,
          timeout: 5000,
        }));
      });

      it('should return copy not reference', () => {
        const retrievedConfig = connector.getConfig();
        retrievedConfig.id = 'modified';

        expect(connector.getConfig().id).toBe('test-connector');
      });
    });

    describe('validateConfig()', () => {
      it('should validate valid configuration', () => {
        const result = connector.validateConfig(config);

        expect(result.valid).toBe(true);
        expect(result.error).toBeUndefined();
      });

      it('should reject configuration without ID', () => {
        const invalidConfig = { ...config, id: '' };
        const result = connector.validateConfig(invalidConfig);

        expect(result.valid).toBe(false);
        expect(result.error).toContain('Invalid or missing ID');
      });

      it('should reject configuration with non-string ID', () => {
        const invalidConfig = { ...config, id: 123 as any };
        const result = connector.validateConfig(invalidConfig);

        expect(result.valid).toBe(false);
        expect(result.error).toContain('Invalid or missing ID');
      });

      it('should handle validation errors gracefully', () => {
        const invalidConfig = null as any;
        const result = connector.validateConfig(invalidConfig);

        expect(result.valid).toBe(false);
        expect(result.error).toBeDefined();
      });
    });

    describe('updateConfig()', () => {
      it('should update configuration', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.updateConfig({ timeout: 10000 });

        const updatedConfig = connector.getConfig();
        expect(updatedConfig.timeout).toBe(10000);
      });

      it('should disconnect and reconnect if connected', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();

        connector.connectMock.mockClear();
        connector.disconnectMock.mockClear();

        await connector.updateConfig({ timeout: 10000 });

        expect(connector.disconnectMock).toHaveBeenCalledTimes(1);
        expect(connector.connectMock).toHaveBeenCalledTimes(1);
      });

      it('should not reconnect if not connected', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.updateConfig({ timeout: 10000 });

        expect(connector.disconnectMock).not.toHaveBeenCalled();
        expect(connector.connectMock).not.toHaveBeenCalled();
      });

      it('should reject invalid configuration updates', async () => {
        await expect(
          connector.updateConfig({ id: '' })
        ).rejects.toThrow('Invalid configuration');
      });

      it('should not update config if validation fails', async () => {
        const originalTimeout = connector.getConfig().timeout;

        await expect(
          connector.updateConfig({ id: '' })
        ).rejects.toThrow();

        expect(connector.getConfig().timeout).toBe(originalTimeout);
      });
    });
  });

  describe('Status Management', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    it('should emit statusChanged event on status change', () => {
      const listener = jest.fn();
      connector.on('statusChanged', listener);

      connector.connectMock.mockResolvedValue(undefined);
      connector.connect();

      expect(listener).toHaveBeenCalledWith({
        oldStatus: 'disconnected',
        newStatus: 'connecting',
      });
    });

    it('should not emit statusChanged if status unchanged', () => {
      const listener = jest.fn();
      connector.on('statusChanged', listener);

      // Try to set same status
      (connector as any).status = 'disconnected';

      expect(listener).not.toHaveBeenCalled();
    });

    it('should emit error event when status changes to error', () => {
      const errorListener = jest.fn();
      connector.on('error', errorListener);

      (connector as any).status = 'error';

      expect(errorListener).toHaveBeenCalledWith(
        expect.objectContaining({
          message: expect.stringContaining('Connection error'),
        })
      );
    });

    it('should reset connection retries when status changes to connected', () => {
      (connector as any)._connectionRetries = 5;
      (connector as any).status = 'connected';

      expect(connector.getConnectionRetries()).toBe(0);
    });
  });

  describe('Resource Cleanup', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    describe('destroy()', () => {
      it('should disconnect when destroyed', async () => {
        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.connect();
        await connector.destroy();

        expect(connector.disconnectMock).toHaveBeenCalledTimes(1);
      });

      it('should remove all event listeners', async () => {
        const listener1 = jest.fn();
        const listener2 = jest.fn();

        connector.on('connected', listener1);
        connector.on('error', listener2);

        connector.connectMock.mockResolvedValue(undefined);
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.destroy();

        expect(connector.listenerCount('connected')).toBe(0);
        expect(connector.listenerCount('error')).toBe(0);
      });

      it('should clear event handlers', async () => {
        const handler = jest.fn();
        connector.onEvent('message', handler);

        connector.disconnectMock.mockResolvedValue(undefined);
        await connector.destroy();

        const eventHandlers = (connector as any)._eventHandlers;
        expect(eventHandlers.size).toBe(0);
      });

      it('should be idempotent', async () => {
        connector.disconnectMock.mockResolvedValue(undefined);

        await connector.destroy();
        await connector.destroy();

        // Should only disconnect once
        expect(connector.disconnectMock).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('Error Handling', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    it('should handle errors in event handlers', () => {
      const errorListener = jest.fn();
      connector.on('error', errorListener);

      const faultyHandler = jest.fn(() => {
        throw new Error('Handler failed');
      });

      connector.onEvent('message', faultyHandler);

      const event: Event<string> = {
        id: '1',
        type: 'message',
        timestamp: Date.now(),
        payload: 'test',
      };

      connector.emitTestEvent(event);

      expect(errorListener).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Handler failed' })
      );
    });

    it('should handle errors in wildcard handlers', () => {
      const errorListener = jest.fn();
      connector.on('error', errorListener);

      const faultyHandler = jest.fn(() => {
        throw new Error('Wildcard handler failed');
      });

      connector.onEvent('*', faultyHandler);

      const event: Event<string> = {
        id: '1',
        type: 'message',
        timestamp: Date.now(),
        payload: 'test',
      };

      connector.emitTestEvent(event);

      expect(errorListener).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Wildcard handler failed' })
      );
    });
  });

  describe('Edge Cases', () => {
    beforeEach(() => {
      connector = new TestConnector(config);
    });

    it('should handle rapid connect/disconnect cycles', async () => {
      connector.connectMock.mockResolvedValue(undefined);
      connector.disconnectMock.mockResolvedValue(undefined);

      for (let i = 0; i < 5; i++) {
        await connector.connect();
        await connector.disconnect();
      }

      expect(connector.status).toBe('disconnected');
    });

    it('should handle connection during pending disconnection', async () => {
      connector.connectMock.mockResolvedValue(undefined);
      connector.disconnectMock.mockImplementation(() =>
        new Promise(resolve => setTimeout(resolve, 100))
      );

      await connector.connect();
      const disconnectPromise = connector.disconnect();

      jest.advanceTimersByTime(50);

      // Try to connect while disconnecting
      const connectPromise = connector.connect();

      jest.advanceTimersByTime(100);
      await disconnectPromise;
      await connectPromise;

      expect(connector.status).toBe('connected');
    });

    it('should handle empty event type', () => {
      const handler = jest.fn();
      connector.onEvent('', handler);

      const event: Event<string> = {
        id: '1',
        type: '',
        timestamp: Date.now(),
        payload: 'test',
      };

      connector.emitTestEvent(event);
      expect(handler).toHaveBeenCalledWith(event);
    });

    it('should handle configuration with undefined optional fields', () => {
      const minConfig: ConnectionOptions = {
        id: 'min',
        type: 'test',
        maxRetries: undefined,
        timeout: undefined,
        secure: undefined,
        headers: undefined,
        debug: undefined,
      };

      connector = new TestConnector(minConfig);
      const config = connector.getConfig();

      expect(config.maxRetries).toBe(3);
      expect(config.timeout).toBe(30000);
      expect(config.secure).toBe(true);
      expect(config.headers).toEqual({});
      expect(config.debug).toBe(false);
    });
  });
});
