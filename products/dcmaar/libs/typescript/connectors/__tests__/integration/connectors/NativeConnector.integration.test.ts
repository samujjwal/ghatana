/**
 * @fileoverview Comprehensive unit tests for NativeConnector
 *
 * Tests cover:
 * - Constructor and default configuration
 * - Module loading (N-API, WASM, FFI mocking)
 * - Sync and async function calls
 * - Zero-copy buffer transfers
 * - Data preparation and result processing
 * - Call queue management
 * - Thread pool configuration
 * - Memory management and limits
 * - Error handling
 * - Resource cleanup
 */

import { NativeConnector, NativeConnectorConfig } from '../../../src/connectors/NativeConnector';

describe('NativeConnector', () => {
  let connector: NativeConnector;

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
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);

      expect(connector).toBeInstanceOf(NativeConnector);
      expect(connector.id).toBe('test-native');
      expect(connector.type).toBe('native');
      expect(connector.status).toBe('disconnected');
    });

    it('should apply default configuration values', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.async).toBe(true);
      expect(internalConfig.threadPoolSize).toBe(4);
      expect(internalConfig.zeroCopy).toBe(true);
      expect(internalConfig.bufferSize).toBe(65536);
      expect(internalConfig.autoConvert).toBe(true);
      expect(internalConfig.memoryLimit).toBe(100 * 1024 * 1024);
    });

    it('should override default configuration', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        async: false,
        threadPoolSize: 8,
        zeroCopy: false,
        bufferSize: 131072,
        autoConvert: false,
        memoryLimit: 200 * 1024 * 1024,
      };

      connector = new NativeConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.async).toBe(false);
      expect(internalConfig.threadPoolSize).toBe(8);
      expect(internalConfig.zeroCopy).toBe(false);
      expect(internalConfig.bufferSize).toBe(131072);
      expect(internalConfig.autoConvert).toBe(false);
      expect(internalConfig.memoryLimit).toBe(200 * 1024 * 1024);
    });

    it('should configure function name and args', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        functionName: 'processData',
        args: [1, 2, 3],
      };

      connector = new NativeConnector(config);

      const internalConfig = (connector as any)._config;
      expect(internalConfig.functionName).toBe('processData');
      expect(internalConfig.args).toEqual([1, 2, 3]);
    });
  });

  describe('Module Loading', () => {
    beforeEach(() => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        functionName: 'testFunction',
      };

      connector = new NativeConnector(config);
    });

    it('should connect and emit connected event', async () => {
      const connectedHandler = jest.fn();
      connector.on('connected', connectedHandler);

      // Mock native module
      (connector as any).nativeModule = {
        testFunction: jest.fn(),
      };

      await connector.connect();

      expect(connector.status).toBe('connected');
      expect(connectedHandler).toHaveBeenCalledTimes(1);
    });

    it('should throw error when function is not found', async () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      // Mock native module without the function
      (connector as any).nativeModule = {};

      await expect(connector.connect()).rejects.toThrow(
        "Function 'testFunction' not found in native module"
      );
    });

    it('should handle connection without function name', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);

      const connectedHandler = jest.fn();
      connector.on('connected', connectedHandler);

      await connector.connect();

      expect(connector.status).toBe('connected');
      expect(connectedHandler).toHaveBeenCalledTimes(1);
    });

    it('should emit error on connection failure', async () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      // Mock _connect to throw error
      const mockError = new Error('Module not found');
      jest.spyOn(connector as any, '_connect').mockImplementationOnce(async () => {
        connector.emit('error', mockError);
        throw mockError;
      });

      await expect(connector.connect()).rejects.toThrow('Module not found');
      expect(errorHandler).toHaveBeenCalledWith(mockError);
    });
  });

  describe('Disconnection and Cleanup', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should disconnect successfully', async () => {
      await connector.disconnect();
      expect(connector.status).toBe('disconnected');
    });

    it('should wait for pending operations before disconnect', async () => {
      // Add items to call queue
      (connector as any).callQueue.push({
        data: { fn: jest.fn(), args: [] },
        resolve: jest.fn(),
        reject: jest.fn(),
      });

      const disconnectPromise = connector.disconnect();

      // Clear queue to allow disconnect to complete
      (connector as any).callQueue = [];

      jest.advanceTimersByTime(100);

      await disconnectPromise;
      expect(connector.status).toBe('disconnected');
    });

    it('should call cleanup function if available', async () => {
      const cleanupMock = jest.fn().mockResolvedValue(undefined);

      (connector as any).nativeModule = {
        cleanup: cleanupMock,
      };

      await connector.disconnect();

      expect(cleanupMock).toHaveBeenCalledTimes(1);
    });

    it('should handle cleanup function errors', async () => {
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      const cleanupError = new Error('Cleanup failed');
      (connector as any).nativeModule = {
        cleanup: jest.fn().mockRejectedValue(cleanupError),
      };

      await connector.disconnect();

      expect(errorHandler).toHaveBeenCalledWith(cleanupError);
    });

    it('should clear native module references on disconnect', async () => {
      (connector as any).nativeModule = {
        testFunction: jest.fn(),
      };
      (connector as any).nativeFunction = jest.fn();

      await connector.disconnect();

      expect((connector as any).nativeModule).toBeNull();
      expect((connector as any).nativeFunction).toBeNull();
    });
  });

  describe('Synchronous Function Calls', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        async: false,
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should call sync function via send()', async () => {
      const mockFunction = jest.fn().mockReturnValue({ result: 42 });

      (connector as any).nativeModule = {
        processData: mockFunction,
      };

      const eventHandler = jest.fn();
      connector.onEvent('result', eventHandler);

      await connector.send({ value: 10 }, { functionName: 'processData' });

      expect(mockFunction).toHaveBeenCalled();
      expect(eventHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'result',
          payload: { result: 42 },
        })
      );
    });

    it('should call sync function via callSync()', async () => {
      const mockFunction = jest.fn().mockReturnValue(100);

      (connector as any).nativeModule = {
        calculate: mockFunction,
      };

      const result = connector.callSync('calculate', 10, 20);

      expect(result).toBe(100);
      expect(mockFunction).toHaveBeenCalledWith(10, 20);
    });

    it('should throw error when calling non-existent sync function', async () => {
      (connector as any).nativeModule = {};

      expect(() => connector.callSync('nonexistent')).toThrow(
        "Function 'nonexistent' not found"
      );
    });

    it('should throw error when module is not loaded', async () => {
      await connector.disconnect();
      (connector as any).nativeModule = null;

      expect(() => connector.callSync('test')).toThrow('Native module is not loaded');
    });

    it('should emit events on sync call completion', async () => {
      const callCompletedHandler = jest.fn();
      connector.on('callCompleted', callCompletedHandler);

      const mockFunction = jest.fn().mockReturnValue(42);

      (connector as any).nativeModule = {
        test: mockFunction,
      };

      await connector.send({ value: 10 }, { functionName: 'test' });

      expect(callCompletedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          sync: true,
          duration: expect.any(Number),
        })
      );
    });

    it('should emit events on sync call failure', async () => {
      const callFailedHandler = jest.fn();
      connector.on('callFailed', callFailedHandler);

      const mockError = new Error('Native error');
      const mockFunction = jest.fn().mockImplementation(() => {
        throw mockError;
      });

      (connector as any).nativeModule = {
        failing: mockFunction,
      };

      await expect(
        connector.send({ value: 10 }, { functionName: 'failing' })
      ).rejects.toThrow('Native error');

      expect(callFailedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          sync: true,
          error: mockError,
        })
      );
    });
  });

  describe('Asynchronous Function Calls', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        async: true,
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should call async function via send()', async () => {
      const mockFunction = jest.fn().mockResolvedValue({ result: 42 });

      (connector as any).nativeModule = {
        processData: mockFunction,
      };

      const eventHandler = jest.fn();
      connector.onEvent('result', eventHandler);

      const sendPromise = connector.send({ value: 10 }, { functionName: 'processData' });

      // Process the queue
      jest.advanceTimersByTime(0);
      await Promise.resolve();

      await sendPromise;

      expect(mockFunction).toHaveBeenCalled();
      expect(eventHandler).toHaveBeenCalled();
    });

    it('should call async function via callAsync()', async () => {
      const mockFunction = jest.fn().mockResolvedValue(100);

      (connector as any).nativeModule = {
        calculate: mockFunction,
      };

      const resultPromise = connector.callAsync('calculate', 10, 20);

      // Process the queue
      jest.advanceTimersByTime(0);
      await Promise.resolve();

      const result = await resultPromise;

      expect(result).toBe(100);
      expect(mockFunction).toHaveBeenCalledWith(10, 20);
    });

    it('should throw error when calling non-existent async function', async () => {
      (connector as any).nativeModule = {};

      await expect(connector.callAsync('nonexistent')).rejects.toThrow(
        "Function 'nonexistent' not found"
      );
    });

    it('should process call queue sequentially', async () => {
      const results: number[] = [];
      const mockFunction = jest.fn().mockImplementation(async (value: number) => {
        results.push(value);
        return value * 2;
      });

      (connector as any).nativeModule = {
        multiply: mockFunction,
      };

      const promise1 = connector.callAsync('multiply', 1);
      const promise2 = connector.callAsync('multiply', 2);
      const promise3 = connector.callAsync('multiply', 3);

      // Process the queue
      jest.advanceTimersByTime(0);
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();

      await Promise.all([promise1, promise2, promise3]);

      expect(results).toEqual([1, 2, 3]);
    });

    it('should emit events on async call completion', async () => {
      const callCompletedHandler = jest.fn();
      connector.on('callCompleted', callCompletedHandler);

      const mockFunction = jest.fn().mockResolvedValue(42);

      (connector as any).nativeModule = {
        test: mockFunction,
      };

      const callPromise = connector.callAsync('test', 10);

      jest.advanceTimersByTime(0);
      await Promise.resolve();

      await callPromise;

      expect(callCompletedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          sync: false,
          duration: expect.any(Number),
        })
      );
    });

    it('should emit events on async call failure', async () => {
      const callFailedHandler = jest.fn();
      connector.on('callFailed', callFailedHandler);

      const mockError = new Error('Async error');
      const mockFunction = jest.fn().mockRejectedValue(mockError);

      (connector as any).nativeModule = {
        failing: mockFunction,
      };

      const callPromise = connector.callAsync('failing', 10);

      jest.advanceTimersByTime(0);
      await Promise.resolve();

      await expect(callPromise).rejects.toThrow('Async error');

      expect(callFailedHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          sync: false,
          error: mockError,
        })
      );
    });

    it('should handle queue when already processing', async () => {
      const mockFunction = jest.fn().mockResolvedValue(42);

      (connector as any).nativeModule = {
        test: mockFunction,
      };

      // Start processing
      (connector as any).isProcessing = true;

      connector.callAsync('test', 1);

      // Should not start processing again
      expect((connector as any).callQueue.length).toBe(1);
    });
  });

  describe('Zero-Copy Buffer Transfers', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        zeroCopy: true,
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should transfer Buffer with zero-copy', async () => {
      const mockFunction = jest.fn().mockReturnValue(Buffer.from([4, 5, 6]));

      (connector as any).nativeModule = {
        transform: mockFunction,
      };

      const buffer = Buffer.from([1, 2, 3]);
      const result = await connector.transferBuffer(buffer, 'transform');

      expect(mockFunction).toHaveBeenCalledWith(buffer);
      expect(result).toEqual(Buffer.from([4, 5, 6]));
    });

    it('should transfer ArrayBuffer with zero-copy', async () => {
      const mockFunction = jest.fn().mockReturnValue(new ArrayBuffer(8));

      (connector as any).nativeModule = {
        transform: mockFunction,
      };

      const arrayBuffer = new ArrayBuffer(8);
      const result = await connector.transferBuffer(arrayBuffer, 'transform');

      expect(mockFunction).toHaveBeenCalledWith(arrayBuffer);
      expect(result).toBeInstanceOf(ArrayBuffer);
    });

    it('should throw error when function is not found', async () => {
      (connector as any).nativeModule = {};

      await expect(
        connector.transferBuffer(Buffer.from([1, 2, 3]), 'nonexistent')
      ).rejects.toThrow("Function 'nonexistent' not found");
    });

    it('should throw error when module is not loaded', async () => {
      await connector.disconnect();
      (connector as any).nativeModule = null;

      await expect(
        connector.transferBuffer(Buffer.from([1, 2, 3]), 'test')
      ).rejects.toThrow('Native module is not loaded');
    });
  });

  describe('Data Preparation and Processing', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        autoConvert: true,
        zeroCopy: true,
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should preserve Buffer data', () => {
      const buffer = Buffer.from([1, 2, 3]);
      const prepared = (connector as any)._prepareData(buffer);

      expect(prepared).toBe(buffer);
    });

    it('should preserve ArrayBuffer data', () => {
      const arrayBuffer = new ArrayBuffer(8);
      const prepared = (connector as any)._prepareData(arrayBuffer);

      expect(prepared).toBe(arrayBuffer);
    });

    it('should handle object with zero-copy', () => {
      const data = { key: 'value', number: 42 };
      const prepared = (connector as any)._prepareData(data);

      expect(prepared).toEqual(data);
    });

    it('should serialize object without zero-copy', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        zeroCopy: false,
      };

      connector = new NativeConnector(config);

      const data = { key: 'value', number: 42 };
      const prepared = (connector as any)._prepareData(data);

      expect(prepared).toBe(JSON.stringify(data));
    });

    it('should preserve primitive data', () => {
      expect((connector as any)._prepareData(42)).toBe(42);
      expect((connector as any)._prepareData('test')).toBe('test');
      expect((connector as any)._prepareData(true)).toBe(true);
      expect((connector as any)._prepareData(null)).toBe(null);
    });

    it('should not convert when autoConvert is false', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        autoConvert: false,
      };

      connector = new NativeConnector(config);

      const data = { key: 'value' };
      const prepared = (connector as any)._prepareData(data);

      expect(prepared).toBe(data);
    });

    it('should process Buffer result', () => {
      const buffer = Buffer.from([1, 2, 3]);
      const result = (connector as any)._processResult(buffer);

      expect(result).toBe(buffer);
    });

    it('should parse JSON string result', () => {
      const jsonString = JSON.stringify({ key: 'value' });
      const result = (connector as any)._processResult(jsonString);

      expect(result).toEqual({ key: 'value' });
    });

    it('should preserve non-JSON string result', () => {
      const result = (connector as any)._processResult('plain text');

      expect(result).toBe('plain text');
    });

    it('should preserve other result types', () => {
      expect((connector as any)._processResult(42)).toBe(42);
      expect((connector as any)._processResult(true)).toBe(true);
      expect((connector as any)._processResult({ key: 'value' })).toEqual({ key: 'value' });
    });

    it('should not process when autoConvert is false', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        autoConvert: false,
      };

      connector = new NativeConnector(config);

      const result = (connector as any)._processResult(JSON.stringify({ key: 'value' }));

      expect(typeof result).toBe('string');
    });
  });

  describe('Function Introspection', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should list available functions', () => {
      (connector as any).nativeModule = {
        func1: jest.fn(),
        func2: jest.fn(),
        notAFunction: 'value',
      };

      const functions = connector.getFunctions();

      expect(functions).toEqual(['func1', 'func2']);
    });

    it('should return empty array when module is not loaded', () => {
      (connector as any).nativeModule = null;

      const functions = connector.getFunctions();

      expect(functions).toEqual([]);
    });

    it('should check if function exists', () => {
      (connector as any).nativeModule = {
        existingFunction: jest.fn(),
      };

      expect(connector.hasFunction('existingFunction')).toBe(true);
      expect(connector.hasFunction('nonExistent')).toBe(false);
    });

    it('should return false when module is not loaded', () => {
      (connector as any).nativeModule = null;

      expect(connector.hasFunction('test')).toBe(false);
    });
  });

  describe('Memory Management', () => {
    beforeEach(async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        memoryLimit: 50 * 1024 * 1024,
      };

      connector = new NativeConnector(config);
      await connector.connect();
    });

    it('should return memory statistics', () => {
      const stats = connector.getMemoryStats();

      expect(stats).toEqual({
        used: 0,
        limit: 50 * 1024 * 1024,
        available: 50 * 1024 * 1024,
      });
    });

    it('should use default memory limit', () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);

      const stats = connector.getMemoryStats();

      expect(stats.limit).toBe(100 * 1024 * 1024);
    });
  });

  describe('Error Handling', () => {
    beforeEach(() => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);
    });

    it('should throw error when sending without module loaded', async () => {
      await expect(
        connector.send({ value: 10 }, { functionName: 'test' })
      ).rejects.toThrow('Native module is not loaded');
    });

    it('should throw error when function name is missing', async () => {
      await connector.connect();
      (connector as any).nativeModule = {};

      await expect(connector.send({ value: 10 })).rejects.toThrow(
        'Function name is required'
      );
    });

    it('should throw error when function is not found in send()', async () => {
      await connector.connect();
      (connector as any).nativeModule = {};

      await expect(
        connector.send({ value: 10 }, { functionName: 'nonexistent' })
      ).rejects.toThrow("Function 'nonexistent' not found in native module");
    });

    it('should emit error event on send failure', async () => {
      await connector.connect();

      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      const mockError = new Error('Native error');
      const mockFunction = jest.fn().mockImplementation(() => {
        throw mockError;
      });

      (connector as any).nativeModule = {
        failing: mockFunction,
      };

      await expect(
        connector.send({ value: 10 }, { functionName: 'failing' })
      ).rejects.toThrow('Native error');

      expect(errorHandler).toHaveBeenCalledWith(mockError);
    });
  });

  describe('Resource Cleanup', () => {
    it('should clear call queue on destroy', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);
      await connector.connect();

      const rejectMock = jest.fn();

      (connector as any).callQueue = [
        { data: {}, resolve: jest.fn(), reject: rejectMock },
        { data: {}, resolve: jest.fn(), reject: rejectMock },
      ];

      await connector.destroy();

      expect((connector as any).callQueue).toEqual([]);
      expect(rejectMock).toHaveBeenCalledWith(expect.objectContaining({
        message: 'Connector destroyed',
      }));
    });

    it('should clean up native module on destroy', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
      };

      connector = new NativeConnector(config);
      await connector.connect();

      (connector as any).nativeModule = {
        test: jest.fn(),
      };

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
      expect((connector as any).nativeModule).toBeNull();
    });
  });

  describe('Integration Scenarios', () => {
    it('should handle complete sync call workflow', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        async: false,
        functionName: 'process',
      };

      connector = new NativeConnector(config);

      const mockFunction = jest.fn().mockReturnValue({ processed: true });

      (connector as any).nativeModule = {
        process: mockFunction,
      };

      await connector.connect();

      const eventHandler = jest.fn();
      connector.onEvent('result', eventHandler);

      await connector.send({ input: 'data' }, { functionName: 'process' });

      expect(mockFunction).toHaveBeenCalled();
      expect(eventHandler).toHaveBeenCalled();

      await connector.destroy();
    });

    it('should handle complete async call workflow', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        async: true,
        functionName: 'processAsync',
      };

      connector = new NativeConnector(config);

      const mockFunction = jest.fn().mockResolvedValue({ processed: true });

      (connector as any).nativeModule = {
        processAsync: mockFunction,
      };

      await connector.connect();

      const eventHandler = jest.fn();
      connector.onEvent('result', eventHandler);

      const sendPromise = connector.send({ input: 'data' }, { functionName: 'processAsync' });

      jest.advanceTimersByTime(0);
      await Promise.resolve();

      await sendPromise;

      expect(mockFunction).toHaveBeenCalled();
      expect(eventHandler).toHaveBeenCalled();

      await connector.destroy();
    });

    it('should handle buffer transfer workflow', async () => {
      const config: NativeConnectorConfig = {
        id: 'test-native',
        type: 'native',
        modulePath: './native-addon.node',
        zeroCopy: true,
      };

      connector = new NativeConnector(config);

      const mockFunction = jest.fn().mockReturnValue(Buffer.from([4, 5, 6]));

      (connector as any).nativeModule = {
        transformBuffer: mockFunction,
      };

      await connector.connect();

      const buffer = Buffer.from([1, 2, 3]);
      const result = await connector.transferBuffer(buffer, 'transformBuffer');

      expect(result).toEqual(Buffer.from([4, 5, 6]));

      await connector.destroy();
    });
  });
});
