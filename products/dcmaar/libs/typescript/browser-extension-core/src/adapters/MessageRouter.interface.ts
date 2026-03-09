/**
 * @fileoverview Message Router Interface
 *
 * Handles message passing between extension contexts (background, content, popup).
 * Thin wrapper over browser.runtime messaging APIs.
 *
 * @module core/interfaces/MessageRouter
 */

/**
 * Extension message
 */
export interface ExtensionMessage<T = unknown> {
  /** Message type for routing */
  type: string;
  /** Message payload */
  payload?: T;
  /** Optional request ID for request/response pattern */
  requestId?: string;
  /** Message timestamp */
  timestamp?: number;
}

/**
 * Message sender information
 */
export interface MessageSender {
  /** Extension ID */
  id?: string;
  /** Tab that sent the message */
  tab?: {
    id: number;
    url?: string;
    title?: string;
  };
  /** Frame that sent the message */
  frameId?: number;
  /** URL of the sender */
  url?: string;
  /** Context type */
  context?: 'background' | 'content' | 'popup' | 'options' | 'devtools';
}

/**
 * Message response
 */
export interface MessageResponse<T = unknown> {
  /** Whether the message was handled successfully */
  success: boolean;
  /** Response data */
  data?: T;
  /** Error message if success is false */
  error?: string;
}

/**
 * Message handler function
 */
export type MessageHandler<TPayload = unknown, TResponse = unknown> = (
  message: ExtensionMessage<TPayload>,
  sender: MessageSender
) => Promise<MessageResponse<TResponse>> | MessageResponse<TResponse>;

/**
 * Message Router Interface
 *
 * Routes messages between different extension contexts.
 * Provides a simple abstraction over browser.runtime messaging.
 *
 * @example
 * ```typescript
 * // In background script
 * const router = new BrowserMessageRouter();
 *
 * router.onMessage(async (message, sender) => {
 *   if (message.type === 'GET_METRICS') {
 *     const metrics = await metricsCollector.collectPageMetrics();
 *     return { success: true, data: metrics };
 *   }
 *   return { success: false, error: 'Unknown message type' };
 * });
 *
 * // In content script
 * const response = await router.sendToBackground({
 *   type: 'GET_METRICS',
 *   payload: { url: window.location.href }
 * });
 * ```
 */
export interface MessageRouter {
  /**
   * Send message to background script
   *
   * @param message - Message to send
   * @returns Promise resolving to response
   */
  sendToBackground<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse>>;

  /**
   * Send message to content script in specific tab
   *
   * @param tabId - Target tab ID
   * @param message - Message to send
   * @returns Promise resolving to response
   */
  sendToContent<TPayload = unknown, TResponse = unknown>(
    tabId: number,
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse>>;

  /**
   * Send message to all content scripts
   *
   * @param message - Message to send
   * @returns Promise resolving to array of responses
   */
  broadcastToContent<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<Array<MessageResponse<TResponse>>>;

  /**
   * Send message to popup (if open)
   *
   * @param message - Message to send
   * @returns Promise resolving to response (or undefined if popup not open)
   */
  sendToPopup<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse> | undefined>;

  /**
   * Register message handler
   *
   * @param handler - Function to handle incoming messages
   */
  onMessage<TPayload = unknown, TResponse = unknown>(
    handler: MessageHandler<TPayload, TResponse>
  ): void;

  /**
   * Register message handler for specific message type
   *
   * @param type - Message type to handle
   * @param handler - Function to handle messages of this type
   */
  onMessageType<TPayload = unknown, TResponse = unknown>(
    type: string,
    handler: MessageHandler<TPayload, TResponse>
  ): void;

  /**
   * Remove message handler
   *
   * @param handler - Handler to remove
   */
  offMessage(handler: MessageHandler): void;

  /**
   * Remove message handler for specific type
   *
   * @param type - Message type
   */
  offMessageType(type: string): void;

  /**
   * Get current context type
   *
   * @returns Context type (background, content, popup, options)
   */
  getContextType(): 'background' | 'content' | 'popup' | 'options' | 'devtools' | 'unknown';
}

/**
 * Port-based long-lived connection interface
 */
export interface PortConnection {
  /** Port name */
  readonly name: string;

  /**
   * Send message through port
   *
   * @param message - Message to send
   */
  postMessage(message: ExtensionMessage): void;

  /**
   * Listen for messages on port
   *
   * @param handler - Message handler
   */
  onMessage(handler: (message: ExtensionMessage) => void): void;

  /**
   * Listen for disconnect event
   *
   * @param handler - Disconnect handler
   */
  onDisconnect(handler: () => void): void;

  /**
   * Disconnect port
   */
  disconnect(): void;
}

/**
 * Extended message router with port support
 */
export interface ExtendedMessageRouter extends MessageRouter {
  /**
   * Create long-lived connection (port)
   *
   * @param name - Port name
   * @returns Port connection
   */
  createPort(name: string): PortConnection;

  /**
   * Listen for incoming port connections
   *
   * @param handler - Function called when port connects
   */
  onConnect(handler: (port: PortConnection) => void): void;
}
