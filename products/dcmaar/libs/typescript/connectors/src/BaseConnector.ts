/**
 * @fileoverview Base connector class providing common functionality for all connectors
 * 
 * This module provides the abstract base class that all connectors extend.
 * It handles connection lifecycle, event management, configuration, reconnection
 * logic, and error handling. Concrete connectors implement protocol-specific
 * connection and data transmission logic.
 * 
 * **Key Features:**
 * - Automatic reconnection with exponential backoff
 * - Event-driven architecture with type-safe handlers
 * - Configuration validation and hot-reload
 * - Connection state management
 * - Error handling and recovery
 * - Resource cleanup
 * 
 * **Connection States:**
 * - disconnected: Not connected
 * - connecting: Connection in progress
 * - connected: Successfully connected
 * - error: Connection failed
 * 
 * @module BaseConnector
 * @since 1.0.0
 */

import { EventEmitter } from 'events';
import { v4 as uuidv4 } from 'uuid';
import { 
  ConnectionOptions, 
  ConnectionStatus, 
  Event, 
  EventHandler, 
  IConnector 
} from './types';

/**
 * Abstract base class for all connectors.
 * 
 * Provides common functionality for connection management, event handling,
 * configuration, and error recovery. Concrete connectors extend this class
 * and implement protocol-specific connection and transmission logic.
 * 
 * **How it works:**
 * 1. Manages connection lifecycle (connect/disconnect/reconnect)
 * 2. Handles event registration and emission
 * 3. Validates and manages configuration
 * 4. Implements automatic reconnection with backoff
 * 5. Provides resource cleanup
 * 
 * **Why this class exists:**
 * Eliminates code duplication across connectors by providing common
 * infrastructure. Ensures consistent behavior and error handling.
 * 
 * **Subclass responsibilities:**
 * - Implement `_connect()`: Protocol-specific connection logic
 * - Implement `_disconnect()`: Protocol-specific disconnection logic
 * - Implement `send()`: Protocol-specific data transmission
 * - Override `validateConfig()`: Add protocol-specific validation
 * 
 * **Events emitted:**
 * - `connecting`: Connection attempt started
 * - `connected`: Successfully connected
 * - `disconnected`: Disconnected
 * - `statusChanged`: Connection status changed
 * - `error`: Error occurred
 * - `reconnecting`: Reconnection attempt
 * - `reconnectFailed`: Max reconnection attempts reached
 * 
 * @abstract
 * @class BaseConnector
 * @extends EventEmitter
 * @implements IConnector
 * @template TConfig - Configuration type extending ConnectionOptions
 * @template TEvent - Event data type
 * 
 * @example
 * ```typescript
 * // Implementing a custom connector
 * class HttpConnector extends BaseConnector<HttpConfig> {
 *   protected async _connect(): Promise<void> {
 *     // Establish HTTP connection
 *     this.client = createHttpClient(this._config);
 *   }
 * 
 *   protected async _disconnect(): Promise<void> {
 *     // Close HTTP connection
 *     await this.client.close();
 *   }
 * 
 *   async send(data: any): Promise<void> {
 *     // Send HTTP request
 *     await this.client.post(this._config.endpoint, data);
 *   }
 * }
 * ```
 * 
 * @example
 * ```typescript
 * // Using a connector
 * const connector = new HttpConnector({
 *   id: 'api-connector',
 *   type: 'http',
 *   endpoint: 'https://api.example.com'
 * });
 * 
 * // Listen for events
 * connector.on('connected', () => {
 *   console.log('Connected!');
 * });
 * 
 * connector.on('error', (error) => {
 *   console.error('Connection error:', error);
 * });
 * 
 * // Connect and send data
 * await connector.connect();
 * await connector.send({ message: 'Hello' });
 * await connector.disconnect();
 * ```
 * 
 * @example
 * ```typescript
 * // With automatic reconnection
 * const connector = new WebSocketConnector(config);
 * 
 * connector.on('reconnecting', ({ attempt, maxAttempts, delay }) => {
 *   console.log(`Reconnecting (${attempt}/${maxAttempts}) in ${delay}ms`);
 * });
 * 
 * connector.on('reconnectFailed', () => {
 *   console.error('Failed to reconnect after max attempts');
 * });
 * 
 * await connector.connect();
 * // Connection will auto-reconnect on failure
 * ```
 */
export abstract class BaseConnector<TConfig extends ConnectionOptions, TEvent = any> 
  extends EventEmitter 
  implements IConnector<TConfig, TEvent> 
{
  /** Unique identifier for this connector instance. @protected */
  protected _id: string;

  /** Connector type (e.g., 'http', 'websocket', 'kafka'). @protected */
  protected _type: string;

  /** Current connection status. @protected */
  protected _status: ConnectionStatus = 'disconnected';

  /** Connector configuration. @protected */
  protected _config: TConfig;

  /** Map of event types to registered handlers. @protected */
  protected _eventHandlers: Map<string, Set<EventHandler<TEvent>>> = new Map();

  /** Active connection promise for preventing concurrent connections. @protected */
  protected _connectionPromise: Promise<void> | null = null;

  /** Number of connection retry attempts. @protected */
  protected _connectionRetries: number = 0;

  /** Timeout handle for reconnection delay. @protected */
  protected _connectionTimeout: NodeJS.Timeout | null = null;

  /** Current reconnection attempt count. @protected */
  protected _reconnectAttempts: number = 0;

  /** Maximum number of reconnection attempts. @protected */
  protected _maxReconnectAttempts: number = 5;

  /** Base delay between reconnection attempts (ms). @protected */
  protected _reconnectDelay: number = 1000;

  /** Flag indicating if connector is being disposed. @protected */
  protected _isDisposing: boolean = false;

  /**
   * Creates a new BaseConnector instance.
   * 
   * **Initialization:**
   * 1. Generates unique ID if not provided
   * 2. Validates and sanitizes configuration
   * 3. Sets up event forwarding
   * 
   * @param {TConfig} config - Connector configuration
   */
  constructor(config: TConfig) {
    super();
    this._id = config.id || `connector-${uuidv4()}`;
    this._type = config.type;
    this._config = this._validateAndSanitizeConfig(config);
    this._setupEventForwarding();
  }

  /**
   * Gets the connector's unique identifier.
   * @returns {string} Connector ID
   */
  get id(): string {
    return this._id;
  }

  /**
   * Gets the connector type.
   * @returns {string} Connector type
   */
  get type(): string {
    return this._type;
  }

  /**
   * Gets the current connection status.
   * @returns {ConnectionStatus} Current status
   */
  get status(): ConnectionStatus {
    return this._status;
  }

  /**
   * Sets the connection status and emits events.
   * 
   * **How it works:**
   * 1. Checks if status actually changed
   * 2. Updates internal status
   * 3. Emits statusChanged event
   * 4. Emits specific status events (connected, error)
   * 5. Resets retry counter on successful connection
   * 
   * **Why this exists:**
   * Centralizes status management and ensures consistent event emission.
   * 
   * @param {ConnectionStatus} value - New status
   * @fires BaseConnector#statusChanged
   * @fires BaseConnector#connected
   * @fires BaseConnector#error
   * @protected
   */
  protected set status(value: ConnectionStatus) {
    if (this._status !== value) {
      const oldStatus = this._status;
      this._status = value;
      this.emit('statusChanged', { oldStatus, newStatus: value });
      
      // Emit specific status events
      if (value === 'connected') {
        this.emit('connected');
        this._connectionRetries = 0; // Reset retry counter on successful connection
      } else if (value === 'error') {
        this.emit('error', new Error(`Connection error in ${this._type} connector`));
      }
    }
  }

  /**
   * Establishes connection to the target system.
   * 
   * **How it works:**
   * 1. Checks if already connected/connecting
   * 2. Sets status to 'connecting'
   * 3. Calls protocol-specific `_connect()`
   * 4. Sets status to 'connected' on success
   * 5. Handles errors and triggers reconnection
   * 
   * **Why this method exists:**
   * Provides consistent connection lifecycle management across all connectors.
   * 
   * @returns {Promise<void>}
   * @throws {Error} If connection fails
   * @fires BaseConnector#connecting
   * @fires BaseConnector#connected
   * @fires BaseConnector#error
   * 
   * @example
   * await connector.connect();
   * console.log('Connected!');
   */
  public async connect(): Promise<void> {
    if (this._status === 'connected' || this._status === 'connecting') {
      return this._connectionPromise || Promise.resolve();
    }

    this.status = 'connecting';
    this.emit('connecting');

    try {
      this._connectionPromise = this._connect();
      await this._connectionPromise;
      this.status = 'connected';
      this._reconnectAttempts = 0; // Reset reconnect attempts on successful connection
    } catch (error) {
      this.status = 'error';
      this._handleConnectionError(error as Error);
      throw error;
    } finally {
      this._connectionPromise = null;
    }
  }

  /**
   * Disconnects from the target system.
   * 
   * **How it works:**
   * 1. Checks if already disconnected
   * 2. Sets disposing flag
   * 3. Clears pending timeouts
   * 4. Calls protocol-specific `_disconnect()`
   * 5. Sets status to 'disconnected'
   * 
   * **Why this method exists:**
   * Provides graceful disconnection with proper cleanup.
   * 
   * @returns {Promise<void>}
   * @throws {Error} If disconnection fails
   * @fires BaseConnector#disconnected
   * @fires BaseConnector#error
   * 
   * @example
   * await connector.disconnect();
   * console.log('Disconnected');
   */
  public async disconnect(): Promise<void> {
    if (this._status === 'disconnected') {
      return;
    }

    this._isDisposing = true;
    
    // Clear any pending timeouts
    if (this._connectionTimeout) {
      clearTimeout(this._connectionTimeout);
      this._connectionTimeout = null;
    }

    try {
      await this._disconnect();
      this.status = 'disconnected';
      this.emit('disconnected');
    } catch (error) {
      this.status = 'error';
      this.emit('error', error);
      throw error;
    } finally {
      this._isDisposing = false;
    }
  }

  /**
   * Sends data through the connector.
   * 
   * **Why this is abstract:**
   * Each connector implements protocol-specific data transmission.
   * 
   * @abstract
   * @param {any} data - Data to send
   * @param {Record<string, any>} [options] - Protocol-specific options
   * @returns {Promise<void>}
   * @throws {Error} If send fails
   * 
   * @example
   * await connector.send({ message: 'Hello' });
   */
  public abstract send(data: unknown, options?: Record<string, any>): Promise<void>;

  /**
   * Registers an event handler for a specific event type.
   * 
   * **How it works:**
   * Adds handler to the event type's handler set.
   * Use '*' as eventType to handle all events.
   * 
   * **Why this method exists:**
   * Provides type-safe event handling separate from EventEmitter.
   * 
   * @param {string} eventType - Event type to listen for (or '*' for all)
   * @param {EventHandler<TEvent>} handler - Handler function
   * 
   * @example
   * connector.onEvent('message', (event) => {
   *   console.log('Received:', event.data);
   * });
   * 
   * @example
   * // Listen to all events
   * connector.onEvent('*', (event) => {
   *   console.log('Event:', event.type, event.data);
   * });
   */
  public onEvent(eventType: string, handler: EventHandler<TEvent>): void {
    if (!this._eventHandlers.has(eventType)) {
      this._eventHandlers.set(eventType, new Set());
    }
    this._eventHandlers.get(eventType)?.add(handler);
  }

  /**
   * Unregisters an event handler.
   * 
   * **How it works:**
   * Removes handler from the event type's handler set.
   * Cleans up empty handler sets.
   * 
   * **Why this method exists:**
   * Prevents memory leaks by allowing handler cleanup.
   * 
   * @param {string} eventType - Event type
   * @param {EventHandler<TEvent>} handler - Handler to remove
   * 
   * @example
   * const handler = (event) => console.log(event);
   * connector.onEvent('message', handler);
   * // Later...
   * connector.offEvent('message', handler);
   */
  public offEvent(eventType: string, handler: EventHandler<TEvent>): void {
    if (this._eventHandlers.has(eventType)) {
      const handlers = this._eventHandlers.get(eventType)!;
      handlers.delete(handler);
      if (handlers.size === 0) {
        this._eventHandlers.delete(eventType);
      }
    }
  }

  /**
   * Gets a copy of the current configuration.
   * 
   * **Why this method exists:**
   * Provides read-only access to configuration.
   * Returns a copy to prevent external modification.
   * 
   * @returns {TConfig} Configuration copy
   * 
   * @example
   * const config = connector.getConfig();
   * console.log('Endpoint:', config.endpoint);
   */
  public getConfig(): TConfig {
    return { ...this._config };
  }

  /**
   * Updates connector configuration with hot-reload.
   * 
   * **How it works:**
   * 1. Merges new config with existing
   * 2. Validates merged configuration
   * 3. Disconnects if currently connected
   * 4. Applies new configuration
   * 5. Reconnects if was connected
   * 
   * **Why this method exists:**
   * Enables configuration changes without recreating connector.
   * 
   * @param {Partial<TConfig>} config - Configuration updates
   * @returns {Promise<void>}
   * @throws {Error} If configuration is invalid
   * 
   * @example
   * await connector.updateConfig({
   *   timeout: 60000,
   *   maxRetries: 5
   * });
   */
  public async updateConfig(config: Partial<TConfig>): Promise<void> {
    const newConfig = { ...this._config, ...config };
    const validation = this.validateConfig(newConfig as TConfig);
    
    if (!validation.valid) {
      throw new Error(`Invalid configuration: ${validation.error}`);
    }

    const wasConnected = this._status === 'connected';
    
    if (wasConnected) {
      await this.disconnect();
    }

    this._config = this._validateAndSanitizeConfig(newConfig as TConfig);
    
    if (wasConnected) {
      await this.connect();
    }
  }

  /**
   * Validates connector configuration.
   * 
   * **How it works:**
   * Performs basic validation. Subclasses should override
   * to add protocol-specific validation.
   * 
   * **Why this method exists:**
   * Catches configuration errors early before connection attempts.
   * 
   * @param {TConfig} config - Configuration to validate
   * @returns {{ valid: boolean; error?: string }} Validation result
   * 
   * @example
   * const result = connector.validateConfig(config);
   * if (!result.valid) {
   *   console.error('Invalid config:', result.error);
   * }
   */
  public validateConfig(config: TConfig): { valid: boolean; error?: string } {
    try {
      // Basic validation can be implemented here
      if (!config.id || typeof config.id !== 'string') {
        return { valid: false, error: 'Invalid or missing ID' };
      }
      return { valid: true };
    } catch (error) {
      return { 
        valid: false, 
        error: error instanceof Error ? error.message : 'Unknown validation error' 
      };
    }
  }

  /**
   * Protocol-specific connection implementation.
   * 
   * **Why this is abstract:**
   * Each connector implements its own connection logic
   * (HTTP, WebSocket, Kafka, etc.).
   * 
   * **Implementation requirements:**
   * - Establish connection to target system
   * - Throw error if connection fails
   * - Set up any protocol-specific handlers
   * 
   * @abstract
   * @returns {Promise<void>}
   * @throws {Error} If connection fails
   * @protected
   */
  protected abstract _connect(): Promise<void>;

  /**
   * Protocol-specific disconnection implementation.
   * 
   * **Why this is abstract:**
   * Each connector implements its own disconnection logic.
   * 
   * **Implementation requirements:**
   * - Close connection gracefully
   * - Clean up protocol-specific resources
   * - Throw error if disconnection fails
   * 
   * @abstract
   * @returns {Promise<void>}
   * @throws {Error} If disconnection fails
   * @protected
   */
  protected abstract _disconnect(): Promise<void>;

  /**
   * Emits an event to registered handlers.
   * 
   * **How it works:**
   * 1. Calls handlers for specific event type
   * 2. Calls wildcard ('*') handlers
   * 3. Catches and emits handler errors
   * 
   * **Why this method exists:**
   * Centralizes event emission with error handling.
   * 
   * @param {Event<TEvent>} event - Event to emit
   * @protected
   */
  protected _emitEvent(event: Event<TEvent>): void {
    // Emit to all handlers for this event type
    if (this._eventHandlers.has(event.type)) {
      const handlers = this._eventHandlers.get(event.type)!;
      for (const handler of handlers) {
        try {
          handler(event);
        } catch (error) {
          this.emit('error', error);
        }
      }
    }

    // Emit to wildcard handlers
    if (this._eventHandlers.has('*')) {
      const handlers = this._eventHandlers.get('*')!;
      for (const handler of handlers) {
        try {
          handler(event);
        } catch (error) {
          this.emit('error', error);
        }
      }
    }
  }

  /**
   * Handles connection errors and manages reconnection.
   * 
   * **How it works:**
   * 1. Emits error event
   * 2. Checks if should reconnect
   * 3. Calculates exponential backoff delay
   * 4. Schedules reconnection attempt
   * 5. Emits reconnectFailed if max attempts reached
   * 
   * **Why this method exists:**
   * Implements automatic reconnection with exponential backoff.
   * 
   * @param {Error} error - Connection error
   * @fires BaseConnector#error
   * @fires BaseConnector#reconnecting
   * @fires BaseConnector#reconnectFailed
   * @protected
   */
  protected _handleConnectionError(error: Error): void {
    this.emit('error', error);
    
    // Only attempt reconnection if we're not already disposing
    if (!this._isDisposing && this._reconnectAttempts < this._maxReconnectAttempts) {
      this._reconnectAttempts++;
      const delay = this._reconnectDelay * Math.pow(2, this._reconnectAttempts - 1);
      
      this.emit('reconnecting', { 
        attempt: this._reconnectAttempts,
        maxAttempts: this._maxReconnectAttempts,
        delay
      });
      
      this._connectionTimeout = setTimeout(() => {
        this.connect().catch(() => {}); // Errors are handled in connect()
      }, delay);
    } else if (this._reconnectAttempts >= this._maxReconnectAttempts) {
      this.emit('reconnectFailed', { 
        attempts: this._reconnectAttempts,
        error: new Error('Max reconnection attempts reached')
      });
    }
  }

  /**
   * Validates and applies default values to configuration.
   * 
   * **How it works:**
   * Merges provided config with default values.
   * 
   * **Why this method exists:**
   * Ensures all required config fields have values.
   * 
   * @param {TConfig} config - Configuration to process
   * @returns {TConfig} Processed configuration
   * @protected
   */
  protected _validateAndSanitizeConfig(config: TConfig): TConfig {
    // Apply default values
    const defaultConfig: Partial<ConnectionOptions> = {
      maxRetries: 3,
      timeout: 30000,
      secure: true,
      headers: {},
      debug: false
    };

    // Merge with provided config
    return { ...defaultConfig, ...config } as TConfig;
  }

  /**
   * Sets up automatic event forwarding.
   * 
   * **Why this method exists:**
   * Forwards EventEmitter events to typed event handlers.
   * 
   * @private
   */
  private _setupEventForwarding(): void {
    // Forward all events from the connector to the instance
    this.on('*', (event: unknown) => {
      if (event && typeof event === 'object' && 'type' in event) {
        this._emitEvent(event as Event<TEvent>);
      }
    });
  }

  /**
   * Destroys the connector and cleans up all resources.
   * 
   * **How it works:**
   * 1. Disconnects if connected
   * 2. Removes all event listeners
   * 3. Clears event handlers
   * 
   * **Why this method exists:**
   * Ensures proper cleanup and prevents resource leaks.
   * 
   * @returns {Promise<void>}
   * 
   * @example
   * // Cleanup on shutdown
   * process.on('SIGTERM', async () => {
   *   await connector.destroy();
   *   process.exit(0);
   * });
   */
  public async destroy(): Promise<void> {
    await this.disconnect();
    this.removeAllListeners();
    this._eventHandlers.clear();
  }
}
