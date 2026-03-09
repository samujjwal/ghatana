/**
 * @fileoverview Native connector template illustrating how to bridge to N-API / FFI modules.
 *
 * Provides scaffolding for invoking native code (Rust, C/C++, WASM) from connector pipelines while
 * handling async queues, zero-copy transfers, and type conversion. Pair with `Telemetry` to trace
 * native call durations and `RetryPolicy` to retry transient failures from native modules.
 *
 * @see {@link NativeConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

/**
 * Configuration contract for `NativeConnector`.
 */
export interface NativeConnectorConfig extends ConnectionOptions {
  /**
   * Path to the native module
   */
  modulePath: string;
  
  /**
   * Function name to call in the native module
   */
  functionName?: string;
  
  /**
   * Arguments to pass to the native function
   */
  args?: unknown[];
  
  /**
   * Enable/disable async mode
   * @default true
   */
  async?: boolean;
  
  /**
   * Thread pool size for async operations
   * @default 4
   */
  threadPoolSize?: number;
  
  /**
   * Enable/disable zero-copy transfers
   * @default true
   */
  zeroCopy?: boolean;
  
  /**
   * Buffer size for data transfers
   * @default 65536 (64KB)
   */
  bufferSize?: number;
  
  /**
   * Enable/disable automatic type conversion
   * @default true
   */
  autoConvert?: boolean;
  
  /**
   * Memory limit for native operations (bytes)
   * @default 100MB
   */
  memoryLimit?: number;
}

/**
 * Connector that exercises native modules via N-API, WASM, or FFI-based bridges.
 *
 * **Example (Rust N-API addon):**
 * ```ts
 * const connector = new NativeConnector({ modulePath: './native-addon.node', functionName: 'process' });
 * await connector.connect();
 * await connector.send({ value: 42 });
 * ```
 *
 * **Example (WASM module with zero-copy buffers):**
 * ```ts
 * const connector = new NativeConnector({
 *   modulePath: './module.wasm',
 *   functionName: 'transform',
 *   zeroCopy: true,
 *   async: true
 * });
 * await connector.connect();
 * const buffer = Buffer.from([1, 2, 3]);
 * await connector.transferBuffer(buffer, 'transform');
 * ```
 */
export class NativeConnector extends BaseConnector<NativeConnectorConfig> {
  private nativeModule: unknown = null;
  private nativeFunction: Function | null = null;
  private callQueue: Array<{ data: unknown; resolve: Function; reject: Function }> = [];
  private isProcessing: boolean = false;

  /**
   * Applies performance-friendly defaults (async execution, zero-copy, thread pool size).
   */
  constructor(config: NativeConnectorConfig) {
    super({
      async: true,
      threadPoolSize: 4,
      zeroCopy: true,
      bufferSize: 65536, // 64KB
      autoConvert: true,
      memoryLimit: 100 * 1024 * 1024, // 100MB
      ...config,
      type: 'native',
    });
  }

  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    try {
      // Load the native module
      // In a real implementation:
      // 
      // For N-API (Rust with napi-rs):
      // this.nativeModule = require(this._config.modulePath);
      // 
      // For WASM:
      // const fs = require('fs');
      // const wasmBuffer = fs.readFileSync(this._config.modulePath);
      // const wasmModule = await WebAssembly.instantiate(wasmBuffer);
      // this.nativeModule = wasmModule.instance.exports;
      // 
      // For FFI:
      // const ffi = require('ffi-napi');
      // this.nativeModule = ffi.Library(this._config.modulePath, {
      //   [this._config.functionName]: ['type', ['arg_types']]
      // });

      if (this._config.functionName) {
        this.nativeFunction = this.nativeModule?.[this._config.functionName];
        if (!this.nativeFunction) {
          throw new Error(`Function '${this._config.functionName}' not found in native module`);
        }
      }

      this.emit('connected');
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /** @inheritdoc */
  protected async _disconnect(): Promise<void> {
    // Wait for pending operations to complete
    while (this.callQueue.length > 0 || this.isProcessing) {
      await new Promise(resolve => setTimeout(resolve, 100));
    }

    // Clean up native module
    if (this.nativeModule) {
      // Call cleanup function if available
      if (typeof this.nativeModule.cleanup === 'function') {
        try {
          await this.nativeModule.cleanup();
        } catch (error) {
          this.emit('error', error);
        }
      }
      
      this.nativeModule = null;
      this.nativeFunction = null;
    }
  }

  /**
   * Invokes configured native entry point and emits processed result events.
   *
   * Handles data preparation (zero-copy vs. serialized), executes native call (sync or async), and
   * emits structured `result` events containing metadata. Wire this into `Telemetry` to capture
   * latency distribution.
   *
   * @param data - Payload forwarded to native layer.
   * @param options - Call overrides (e.g., alternative `functionName`).
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    if (!this.nativeModule) {
      throw new Error('Native module is not loaded');
    }

    const functionName = options.functionName || this._config.functionName;
    if (!functionName) {
      throw new Error('Function name is required');
    }

    const fn = this.nativeModule[functionName];
    if (!fn || typeof fn !== 'function') {
      throw new Error(`Function '${functionName}' not found in native module`);
    }

    try {
      // Prepare data for native call
      const preparedData = this._prepareData(data);
      
      // Call native function
      let result;
      if (this._config.async) {
        result = await this._callAsync(fn, preparedData, options);
      } else {
        result = this._callSync(fn, preparedData, options);
      }

      // Process result
      const processedResult = this._processResult(result);

      // Emit event
      this._emitEvent({
        id: uuidv4(),
        type: 'result',
        timestamp: Date.now(),
        payload: processedResult,
        metadata: {
          functionName,
          duration: 0, // Would be calculated in real implementation
        },
      });
    } catch (error) {
      this.emit('error', error);
      throw error;
    }
  }

  /**
   * Call a native function synchronously.
   */
  public callSync(functionName: string, ...args: unknown[]): unknown {
    if (!this.nativeModule) {
      throw new Error('Native module is not loaded');
    }

    const fn = this.nativeModule[functionName];
    if (!fn || typeof fn !== 'function') {
      throw new Error(`Function '${functionName}' not found`);
    }

    return fn(...args);
  }

  /**
   * Call a native function asynchronously.
   */
  public async callAsync(functionName: string, ...args: unknown[]): Promise<unknown> {
    if (!this.nativeModule) {
      throw new Error('Native module is not loaded');
    }

    const fn = this.nativeModule[functionName];
    if (!fn || typeof fn !== 'function') {
      throw new Error(`Function '${functionName}' not found`);
    }

    return new Promise((resolve, reject) => {
      this.callQueue.push({
        data: { fn, args },
        resolve,
        reject,
      });

      this._processQueue();
    });
  }

  /**
   * Transfer data to native code with zero-copy.
   */
  public async transferBuffer(buffer: Buffer | ArrayBuffer, functionName: string): Promise<unknown> {
    if (!this.nativeModule) {
      throw new Error('Native module is not loaded');
    }

    const fn = this.nativeModule[functionName];
    if (!fn || typeof fn !== 'function') {
      throw new Error(`Function '${functionName}' not found`);
    }

    // In a real implementation with N-API:
    // The buffer can be passed directly without copying
    // using napi_create_external_arraybuffer
    
    return fn(buffer);
  }

  /**
   * Get memory usage statistics.
   */
  public getMemoryStats(): {
    used: number;
    limit: number;
    available: number;
  } {
    // In a real implementation, query the native module
    // this.nativeModule?.getMemoryStats?.()
    
    return {
      used: 0,
      limit: this._config.memoryLimit || 0,
      available: this._config.memoryLimit || 0,
    };
  }

  /**
   * Converts JS payloads into native-friendly representations.
   */
  private _prepareData(data: unknown): unknown {
    if (!this._config.autoConvert) {
      return data;
    }

    // Convert JavaScript types to native-friendly formats
    if (Buffer.isBuffer(data) || data instanceof ArrayBuffer) {
      return data;
    }

    if (typeof data === 'object' && data !== null) {
      // For complex objects, serialize to JSON or use structured clone
      if (this._config.zeroCopy) {
        // Use structured clone for zero-copy transfer
        return data;
      } else {
        return JSON.stringify(data);
      }
    }

    return data;
  }

  /**
   * Converts native outputs back into JavaScript-friendly shapes.
   */
  private _processResult(result: unknown): unknown {
    if (!this._config.autoConvert) {
      return result;
    }

    // Convert native types back to JavaScript
    if (Buffer.isBuffer(result)) {
      return result;
    }

    if (typeof result === 'string') {
      try {
        return JSON.parse(result);
      } catch {
        return result;
      }
    }

    return result;
  }

  /**
   * Queues async native execution preserving ordering.
   */
  private async _callAsync(fn: Function, data: unknown, options: Record<string, any>): Promise<unknown> {
    return new Promise((resolve, reject) => {
      this.callQueue.push({
        data: { fn, args: [data, options] },
        resolve,
        reject,
      });

      this._processQueue();
    });
  }

  /**
   * Executes synchronous native function with timing metadata.
   */
  private _callSync(fn: Function, data: unknown, options: Record<string, any>): unknown {
    const startTime = Date.now();
    
    try {
      const result = fn(data, options);
      
      const duration = Date.now() - startTime;
      this.emit('callCompleted', { duration, sync: true });
      
      return result;
    } catch (error) {
      this.emit('callFailed', { error, sync: true });
      throw error;
    }
  }

  /**
   * Drains queued async calls sequentially.
   */
  private async _processQueue(): Promise<void> {
    if (this.isProcessing || this.callQueue.length === 0) {
      return;
    }

    this.isProcessing = true;

    while (this.callQueue.length > 0) {
      const { data, resolve, reject } = this.callQueue.shift()!;
      const { fn, args } = data;

      try {
        const startTime = Date.now();
        
        // Call the native function
        const result = await fn(...args);
        
        const duration = Date.now() - startTime;
        this.emit('callCompleted', { duration, sync: false });
        
        resolve(result);
      } catch (error) {
        this.emit('callFailed', { error, sync: false });
        reject(error);
      }
    }

    this.isProcessing = false;
  }

  /**
   * Get available functions in the native module
   */
  public getFunctions(): string[] {
    if (!this.nativeModule) {
      return [];
    }

    return Object.keys(this.nativeModule).filter(key => 
      typeof this.nativeModule[key] === 'function'
    );
  }

  /**
   * Check if a function exists in the native module
   */
  public hasFunction(name: string): boolean {
    return this.nativeModule && typeof this.nativeModule[name] === 'function';
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    // Clear the call queue
    for (const { reject } of this.callQueue) {
      reject(new Error('Connector destroyed'));
    }
    this.callQueue = [];

    await super.destroy();
  }
}
