/**
 * @fileoverview Browser Message Router
 *
 * Wrapper over browser.runtime messaging API for routing messages between
 * extension contexts (background, content, popup, options).
 *
 * @module browser/messaging/BrowserMessageRouter
 */

import browser from "webextension-polyfill";

import type {
  MessageRouter,
  ExtendedMessageRouter,
  ExtensionMessage,
  MessageSender,
  MessageResponse,
  MessageHandler,
  PortConnection,
} from "./MessageRouter.interface";

/**
 * Browser Message Router implementation
 *
 * Routes messages between extension contexts using browser.runtime API.
 *
 * @example
 * ```typescript
 * // In background script
 * const router = new BrowserMessageRouter();
 *
 * router.onMessage(async (message, sender) => {
 *   if (message.type === 'GET_METRICS') {
 *     const metrics = await collectMetrics();
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
export class BrowserMessageRouter implements MessageRouter {
  private messageHandlers = new Set<MessageHandler>();
  private typeHandlers = new Map<string, MessageHandler>();
  private contextType:
    | "background"
    | "content"
    | "popup"
    | "options"
    | "devtools"
    | "unknown";

  constructor() {
    this.contextType = this.detectContextType();
    this.setupMessageListener();
  }

  /**
   * Detect the current extension context
   */
  private detectContextType():
    | "background"
    | "content"
    | "popup"
    | "options"
    | "devtools"
    | "unknown" {
    if (typeof window === "undefined") {
      return "background";
    }

    if (
      window.location.protocol === "chrome-extension:" ||
      window.location.protocol === "moz-extension:"
    ) {
      if (window.location.pathname.includes("popup")) {
        return "popup";
      }
      if (window.location.pathname.includes("options")) {
        return "options";
      }
      if (window.location.pathname.includes("devtools")) {
        return "devtools";
      }
      return "background";
    }

    return "content";
  }

  /**
   * Set up message listener
   */
  private setupMessageListener(): void {
    // Note: We allow listeners to return `undefined` so other runtime.onMessage
    // listeners (for example a legacy background listener) can handle messages
    // if this router does not have a handler registered yet. Returning a
    // non-undefined value will short-circuit other listeners.
    browser.runtime.onMessage.addListener(
      (
        message: unknown,
        sender: browser.Runtime.MessageSender
      ): Promise<MessageResponse | undefined> | MessageResponse | undefined => {
        return this.handleIncomingMessage(message, sender);
      }
    );
  }

  /**
   * Handle incoming messages
   */
  private async handleIncomingMessage(
    message: unknown,
    sender: browser.Runtime.MessageSender
  ): Promise<MessageResponse | undefined> {
    // Validate message structure
    if (!this.isValidMessage(message)) {
      return {
        success: false,
        error: "Invalid message format",
      };
    }

    const msg = message as ExtensionMessage;
    const messageSender = this.convertSender(sender);

    // Verbose debug to help trace which context handled the message.
    try {
      console.debug("[DCMAAR][BrowserMessageRouter] incoming message", {
        type: msg.type,
        from: messageSender.context,
        sender: messageSender,
      });
    } catch {
      // ignore logging errors
    }

    // Try type-specific handler first
    const typeHandler = this.typeHandlers.get(msg.type);
    if (typeHandler) {
      try {
        const resp = await typeHandler(msg, messageSender);
        try {
          console.debug(
            "[DCMAAR][BrowserMessageRouter] handled by typeHandler",
            { type: msg.type, response: resp }
          );
        } catch {}
        try {
          if (resp && typeof resp === "object") {
            (resp as any).source =
              (resp as any).source || "BrowserMessageRouter";
          }
        } catch {}
        return resp;
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : "Handler error",
        };
      }
    }

    // Try generic handlers
    for (const handler of this.messageHandlers) {
      try {
        const response = await handler(msg, messageSender);
        try {
          console.debug(
            "[DCMAAR][BrowserMessageRouter] handled by generic handler",
            { type: msg.type, response }
          );
        } catch {}
        if (response && (response.success || response.error)) {
          try {
            if (response && typeof response === "object") {
              (response as any).source =
                (response as any).source || "BrowserMessageRouter";
            }
          } catch {}
          return response;
        }
      } catch (e) {
        // Continue to next handler
        try {
          console.debug("[DCMAAR][BrowserMessageRouter] handler threw", {
            type: msg.type,
            error: String(e),
          });
        } catch {}
      }
    }

    // No handler registered in this router - return undefined so other
    // runtime.onMessage listeners get a chance to process the message.
    try {
      console.debug(
        "[DCMAAR][BrowserMessageRouter] no handler found, returning undefined",
        { type: msg.type }
      );
    } catch {}
    return undefined;
  }

  /**
   * Validate message structure
   */
  private isValidMessage(message: unknown): boolean {
    return (
      typeof message === "object" &&
      message !== null &&
      typeof (message as Record<string, unknown>).type === "string"
    );
  }

  /**
   * Convert browser MessageSender to our MessageSender
   */
  private convertSender(sender: browser.Runtime.MessageSender): MessageSender {
    const browserSender = sender as {
      id?: string;
      tab?: {
        id?: number;
        url?: string;
        title?: string;
      };
      frameId?: number;
      url?: string;
    };

    return {
      id: browserSender.id,
      tab: browserSender.tab
        ? {
            id: browserSender.tab.id!,
            url: browserSender.tab.url,
            title: browserSender.tab.title,
          }
        : undefined,
      frameId: browserSender.frameId,
      url: browserSender.url,
      context: this.detectSenderContext(sender),
    };
  }

  /**
   * Detect sender context
   */
  private detectSenderContext(
    sender: browser.Runtime.MessageSender
  ): "background" | "content" | "popup" | "options" | "devtools" {
    const browserSender = sender as {
      tab?: unknown;
      url?: string;
    };

    if (browserSender.tab) {
      return "content";
    }
    if (browserSender.url) {
      if (browserSender.url.includes("popup")) return "popup";
      if (browserSender.url.includes("options")) return "options";
      if (browserSender.url.includes("devtools")) return "devtools";
    }
    return "background";
  }

  /**
   * Send message to background script
   */
  async sendToBackground<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse>> {
    try {
      const response = await browser.runtime.sendMessage({
        ...message,
        timestamp: message.timestamp || Date.now(),
      });

      return response as MessageResponse<TResponse>;
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error ? error.message : "Failed to send message",
      };
    }
  }

  /**
   * Send message to content script in specific tab
   */
  async sendToContent<TPayload = unknown, TResponse = unknown>(
    tabId: number,
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse>> {
    try {
      const response = await browser.tabs.sendMessage(tabId, {
        ...message,
        timestamp: message.timestamp || Date.now(),
      });

      return response as MessageResponse<TResponse>;
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error
            ? error.message
            : "Failed to send message to tab",
      };
    }
  }

  /**
   * Send message to all content scripts
   */
  async broadcastToContent<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<Array<MessageResponse<TResponse>>> {
    try {
      const tabs = await browser.tabs.query({});
      const responses: Array<MessageResponse<TResponse>> = [];

      for (const tab of tabs) {
        if (tab.id) {
          const response = await this.sendToContent<TPayload, TResponse>(
            tab.id,
            message
          );
          responses.push(response);
        }
      }

      return responses;
    } catch (error) {
      return [
        {
          success: false,
          error: error instanceof Error ? error.message : "Failed to broadcast",
        },
      ];
    }
  }

  /**
   * Send message to popup (if open)
   */
  async sendToPopup<TPayload = unknown, TResponse = unknown>(
    message: ExtensionMessage<TPayload>
  ): Promise<MessageResponse<TResponse> | undefined> {
    try {
      // Check if popup is open by trying to get popup views
      const views = browser.extension.getViews({ type: "popup" });
      if (views.length === 0) {
        return undefined; // Popup not open
      }

      return await this.sendToBackground<TPayload, TResponse>(message);
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error ? error.message : "Failed to send to popup",
      };
    }
  }

  /**
   * Register message handler
   */
  onMessage<TPayload = unknown, TResponse = unknown>(
    handler: MessageHandler<TPayload, TResponse>
  ): void {
    this.messageHandlers.add(handler as MessageHandler);
  }

  /**
   * Register message handler for specific message type
   */
  onMessageType<TPayload = unknown, TResponse = unknown>(
    type: string,
    handler: MessageHandler<TPayload, TResponse>
  ): void {
    this.typeHandlers.set(type, handler as MessageHandler);
  }

  /**
   * Remove message handler
   */
  offMessage(handler: MessageHandler): void {
    this.messageHandlers.delete(handler);
  }

  /**
   * Remove message handler for specific type
   */
  offMessageType(type: string): void {
    this.typeHandlers.delete(type);
  }

  /**
   * Get current context type
   */
  getContextType():
    | "background"
    | "content"
    | "popup"
    | "options"
    | "devtools"
    | "unknown" {
    return this.contextType;
  }
}

/**
 * Browser Port Connection implementation
 */
class BrowserPortConnection implements PortConnection {
  readonly name: string;
  private port: {
    name: string;
    postMessage: (message: unknown) => void;
    disconnect: () => void;
    onMessage: {
      addListener: (callback: (msg: unknown) => void) => void;
    };
    onDisconnect: {
      addListener: (callback: () => void) => void;
    };
  };
  private messageHandlers = new Set<(message: ExtensionMessage) => void>();
  private disconnectHandlers = new Set<() => void>();

  constructor(port: browser.Runtime.Port) {
    this.port = port as never;
    this.name = this.port.name;

    // Set up listeners
    this.port.onMessage.addListener((msg: unknown) => {
      this.messageHandlers.forEach((handler) =>
        handler(msg as ExtensionMessage)
      );
    });

    this.port.onDisconnect.addListener(() => {
      this.disconnectHandlers.forEach((handler) => handler());
    });
  }

  postMessage(message: ExtensionMessage): void {
    this.port.postMessage(message);
  }

  onMessage(handler: (message: ExtensionMessage) => void): void {
    this.messageHandlers.add(handler);
  }

  onDisconnect(handler: () => void): void {
    this.disconnectHandlers.add(handler);
  }

  disconnect(): void {
    this.port.disconnect();
  }
}

/**
 * Extended Browser Message Router with port support
 */
export class ExtendedBrowserMessageRouter
  extends BrowserMessageRouter
  implements ExtendedMessageRouter
{
  private connectHandlers = new Set<(port: PortConnection) => void>();

  constructor() {
    super();
    this.setupConnectListener();
  }

  /**
   * Set up connect listener
   */
  private setupConnectListener(): void {
    browser.runtime.onConnect.addListener((port: browser.Runtime.Port) => {
      const connection = new BrowserPortConnection(port);
      this.connectHandlers.forEach((handler) => handler(connection));
    });
  }

  /**
   * Create long-lived connection (port)
   */
  createPort(name: string): PortConnection {
    const port = browser.runtime.connect({ name });
    return new BrowserPortConnection(port);
  }

  /**
   * Listen for incoming port connections
   */
  onConnect(handler: (port: PortConnection) => void): void {
    this.connectHandlers.add(handler);
  }
}
