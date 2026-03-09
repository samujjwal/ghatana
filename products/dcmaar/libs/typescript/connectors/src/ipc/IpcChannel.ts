/**
 * @fileoverview IPC (Inter-Process Communication) abstraction for cross-context messaging.
 *
 * Provides a unified interface for message passing across different platforms:
 * - Browser extensions: chrome.runtime.sendMessage, content script messaging
 * - Electron: ipcRenderer/ipcMain channels
 * - Node.js: child_process, worker_threads, or custom channels
 * - Agent: Named pipes, Unix sockets, or gRPC
 *
 * Products inject platform-specific implementations while connectors use the abstract interface.
 *
 * @module ipc/IpcChannel
 * @since 1.1.0
 */

/**
 * Message envelope for typed IPC communication.
 *
 * Wraps payloads with metadata for routing, correlation, and error handling.
 */
export interface IpcMessage<T = any> {
  /**
   * Unique message identifier for correlation.
   */
  id: string;

  /**
   * Message type/channel for routing.
   */
  type: string;

  /**
   * Message payload.
   */
  payload: T;

  /**
   * Timestamp when message was created.
   */
  timestamp: number;

  /**
   * Optional correlation ID for request/response tracking.
   */
  correlationId?: string;

  /**
   * Optional metadata.
   */
  metadata?: Record<string, any>;
}

/**
 * Message sender information.
 */
export interface IpcSender {
  /**
   * Sender identifier (extension ID, process ID, etc.).
   */
  id?: string;

  /**
   * Sender type (background, content, main, renderer, etc.).
   */
  type?: string;

  /**
   * Platform-specific sender info.
   */
  [key: string]: unknown;
}

/**
 * Message handler function signature.
 */
export type IpcMessageHandler<T = any> = (
  message: IpcMessage<T>,
  sender: IpcSender
) => void | Promise<void> | any | Promise<unknown>;

/**
 * IPC channel send options.
 */
export interface IpcSendOptions {
  /**
   * Target identifier (extension ID, process ID, tab ID, etc.).
   */
  target?: string;

  /**
   * Timeout in milliseconds for response (if expecting reply).
   */
  timeout?: number;

  /**
   * Whether to expect a response.
   * @default false
   */
  expectResponse?: boolean;
}

/**
 * IPC channel interface for cross-context message passing.
 *
 * This interface abstracts IPC operations to work across different platforms:
 * - **Browser Extensions**: chrome.runtime.sendMessage, browser.tabs.sendMessage
 * - **Electron**: ipcRenderer.send/invoke, ipcMain.on/handle
 * - **Node.js**: process.send, child_process events, worker postMessage
 * - **Agent**: Named pipes, Unix sockets, gRPC channels
 *
 * **Key Design Principles**:
 * - Async-first API (works everywhere)
 * - Type-safe message passing
 * - Bidirectional communication
 * - Request/response pattern support
 * - Multi-target support
 * - Handler registration/removal
 *
 * **Usage**:
 * ```typescript
 * // Extension implementation
 * class ExtensionIpcChannel implements IpcChannel {
 *   async send(message) {
 *     await browser.runtime.sendMessage(message);
 *   }
 *
 *   onMessage(handler) {
 *     browser.runtime.onMessage.addListener(handler);
 *   }
 * }
 *
 * // Use in connectors
 * const ipc = new ExtensionIpcChannel();
 * ipc.onMessage((msg) => console.log('Received:', msg));
 * await ipc.send({ type: 'hello', payload: 'world' });
 * ```
 */
export interface IpcChannel {
  /**
   * Sends a message through the channel.
   *
   * @param message - Message to send
   * @param options - Send options
   * @returns Response if expectResponse is true, void otherwise
   * @throws Error if send fails
   *
   * @example
   * ```typescript
   * // Fire-and-forget
   * await ipc.send({
   *   id: '123',
   *   type: 'notification',
   *   payload: { text: 'Hello' },
   *   timestamp: Date.now()
   * });
   *
   * // Request-response
   * const response = await ipc.send(
   *   { id: '456', type: 'query', payload: {...}, timestamp: Date.now() },
   *   { expectResponse: true, timeout: 5000 }
   * );
   * ```
   */
  send<T = any, R = any>(message: IpcMessage<T>, options?: IpcSendOptions): Promise<R | void>;

  /**
   * Registers a message handler.
   *
   * Handlers receive all messages of the specified type (or all messages if type is '*').
   * Multiple handlers can be registered for the same type.
   *
   * @param type - Message type to handle ('*' for all messages)
   * @param handler - Message handler function
   *
   * @example
   * ```typescript
   * ipc.onMessage('command', async (msg, sender) => {
   *   console.log('Command from', sender.id, ':', msg.payload);
   *   return { status: 'ok' };
   * });
   *
   * // Handle all messages
   * ipc.onMessage('*', (msg) => {
   *   console.log('Received:', msg.type);
   * });
   * ```
   */
  onMessage<T = any>(type: string, handler: IpcMessageHandler<T>): void;

  /**
   * Unregisters a message handler.
   *
   * @param type - Message type
   * @param handler - Handler function to remove
   * @returns true if handler was removed
   *
   * @example
   * ```typescript
   * const handler = (msg) => console.log(msg);
   * ipc.onMessage('test', handler);
   * ipc.offMessage('test', handler);
   * ```
   */
  offMessage<T = any>(type: string, handler: IpcMessageHandler<T>): boolean;

  /**
   * Removes all message handlers.
   *
   * @param type - Optional message type (removes all if omitted)
   *
   * @example
   * ```typescript
   * ipc.removeAllListeners('command'); // Remove command handlers
   * ipc.removeAllListeners(); // Remove all handlers
   * ```
   */
  removeAllListeners(type?: string): void;

  /**
   * Checks if the channel is connected/available.
   *
   * @returns true if channel can send/receive messages
   */
  isConnected(): boolean;

  /**
   * Gets channel statistics.
   *
   * @returns Channel statistics (implementation-dependent)
   */
  getStats?(): IpcChannelStats;
}

/**
 * IPC channel statistics.
 */
export interface IpcChannelStats {
  /**
   * Number of messages sent.
   */
  messagesSent: number;

  /**
   * Number of messages received.
   */
  messagesReceived: number;

  /**
   * Number of registered handlers.
   */
  handlers: number;

  /**
   * Number of send errors.
   */
  sendErrors?: number;

  /**
   * Number of handler errors.
   */
  handlerErrors?: number;

  /**
   * Implementation-specific stats.
   */
  [key: string]: unknown;
}

/**
 * Creates a unique message ID.
 */
export function createMessageId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Creates an IPC message from payload.
 *
 * @param type - Message type
 * @param payload - Message payload
 * @param options - Optional message options
 * @returns IPC message
 */
export function createIpcMessage<T = any>(
  type: string,
  payload: T,
  options?: {
    id?: string;
    correlationId?: string;
    metadata?: Record<string, any>;
  }
): IpcMessage<T> {
  return {
    id: options?.id ?? createMessageId(),
    type,
    payload,
    timestamp: Date.now(),
    correlationId: options?.correlationId,
    metadata: options?.metadata,
  };
}
