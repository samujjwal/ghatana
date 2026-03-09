/**
 * Live preview server for YAPPC component editing.
 *
 * <p><b>Purpose</b><br>
 * Provides a WebSocket-based server for real-time component preview and updates
 * during live editing sessions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { LivePreviewServer } from '@ghatana/yappc-live-preview-server';
 *
 * const server = new LivePreviewServer({ port: 5174 });
 * await server.start();
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Live preview server
 * @doc.layer product
 * @doc.pattern Server
 */

import { WebSocketServer, WebSocket } from 'ws';
import { EventEmitter } from 'events';
import type { LivePreviewServerOptions, PreviewMessage, ClientSession } from './types';

/**
 * Live preview server for component rendering and updates.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connections for live component preview, handles component
 * rendering requests, and broadcasts updates to connected clients.
 *
 * @doc.type class
 * @doc.purpose Live preview server
 * @doc.layer product
 * @doc.pattern Server
 */
export class LivePreviewServer extends EventEmitter {
  private wss: WebSocketServer | null = null;
  private clients: Map<string, ClientSession> = new Map();
  private options: Required<LivePreviewServerOptions>;

  /**
   * Creates a new LivePreviewServer instance.
   *
   * @param options - Server configuration options
   */
  constructor(options: LivePreviewServerOptions = {}) {
    super();

    this.options = {
      port: options.port ?? 5174,
      host: options.host ?? 'localhost',
      maxConnections: options.maxConnections ?? 100,
      messageTimeout: options.messageTimeout ?? 30000,
      enableCompression: options.enableCompression ?? true,
    };
  }

  /**
   * Starts the preview server.
   *
   * <p><b>Purpose</b><br>
   * Initializes the WebSocket server and starts listening for connections.
   *
   * @returns Promise that resolves when server is ready
   *
   * @doc.type method
   * @doc.purpose Start server
   * @doc.layer product
   * @doc.pattern Server
   */
  async start(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.wss = new WebSocketServer({
          port: this.options.port,
          host: this.options.host,
          perMessageDeflate: this.options.enableCompression,
        });

        this.wss.on('connection', (ws: WebSocket) => {
          this.handleConnection(ws);
        });

        this.wss.on('error', (error) => {
          this.emit('error', error);
          reject(error);
        });

        this.emit('started', {
          port: this.options.port,
          host: this.options.host,
        });

        resolve();
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Stops the preview server.
   *
   * <p><b>Purpose</b><br>
   * Closes all client connections and shuts down the server.
   *
   * @returns Promise that resolves when server is stopped
   *
   * @doc.type method
   * @doc.purpose Stop server
   * @doc.layer product
   * @doc.pattern Server
   */
  async stop(): Promise<void> {
    return new Promise((resolve) => {
      // Close all client connections
      for (const [, session] of this.clients) {
        session.ws.close();
      }
      this.clients.clear();

      // Close server
      if (this.wss) {
        this.wss.close(() => {
          this.emit('stopped');
          resolve();
        });
      } else {
        resolve();
      }
    });
  }

  /**
   * Handles new WebSocket connection.
   *
   * <p><b>Purpose</b><br>
   * Manages client connection lifecycle including setup, message handling,
   * and cleanup.
   *
   * @param ws - WebSocket connection
   *
   * @doc.type method
   * @doc.purpose Handle connection
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleConnection(ws: WebSocket): void {
    // Check max connections
    if (this.clients.size >= this.options.maxConnections) {
      ws.close(1008, 'Server at max capacity');
      return;
    }

    const clientId = this.generateClientId();
    const session: ClientSession = {
      id: clientId,
      ws,
      connectedAt: Date.now(),
      lastActivity: Date.now(),
    };

    this.clients.set(clientId, session);

    // Send welcome message
    this.sendMessage(ws, {
      type: 'welcome',
      data: {
        clientId,
        timestamp: Date.now(),
      },
    });

    // Setup message handler
    ws.on('message', (data: Buffer) => {
      this.handleMessage(session, data);
    });

    // Setup close handler
    ws.on('close', () => {
      this.handleDisconnection(clientId);
    });

    // Setup error handler
    ws.on('error', (error) => {
      this.emit('client-error', { clientId, error });
    });

    this.emit('client-connected', { clientId });
  }

  /**
   * Handles incoming WebSocket message.
   *
   * <p><b>Purpose</b><br>
   * Processes client messages and routes to appropriate handlers.
   *
   * @param session - Client session
   * @param data - Message data
   *
   * @doc.type method
   * @doc.purpose Handle message
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleMessage(session: ClientSession, data: Buffer): void {
    try {
      const message: PreviewMessage = JSON.parse(data.toString());
      session.lastActivity = Date.now();

      switch (message.type) {
        case 'render':
          this.handleRenderRequest(session, message);
          break;

        case 'update-props':
          this.handleUpdateProps(session, message);
          break;

        case 'update-style':
          this.handleUpdateStyle(session, message);
          break;

        case 'ping':
          this.handlePing(session, message);
          break;

        default:
          this.emit('unknown-message', { session: session.id, message });
      }
    } catch (error) {
      this.sendMessage(session.ws, {
        type: 'error',
        data: {
          message: 'Invalid message format',
          error: error instanceof Error ? error.message : 'Unknown error',
        },
      });
    }
  }

  /**
   * Handles render request.
   *
   * <p><b>Purpose</b><br>
   * Processes component render requests and sends rendered output.
   *
   * @param session - Client session
   * @param message - Render message
   *
   * @doc.type method
   * @doc.purpose Handle render request
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleRenderRequest(
    session: ClientSession,
    message: PreviewMessage
  ): void {
    const { componentId, props = {} } = message.data;

    this.emit('render-request', {
      clientId: session.id,
      componentId,
      props,
    });

    // Send acknowledgment
    this.sendMessage(session.ws, {
      type: 'render-ack',
      data: {
        componentId,
        timestamp: Date.now(),
      },
    });
  }

  /**
   * Handles prop update request.
   *
   * <p><b>Purpose</b><br>
   * Processes prop update requests and broadcasts to relevant clients.
   *
   * @param session - Client session
   * @param message - Update message
   *
   * @doc.type method
   * @doc.purpose Handle prop update
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleUpdateProps(
    session: ClientSession,
    message: PreviewMessage
  ): void {
    const { componentId, props } = message.data;

    this.emit('props-updated', {
      clientId: session.id,
      componentId,
      props,
    });

    // Broadcast to other clients
    this.broadcast(
      {
        type: 'props-changed',
        data: {
          componentId,
          props,
          updatedBy: session.id,
          timestamp: Date.now(),
        },
      },
      session.id
    );
  }

  /**
   * Handles style update request.
   *
   * <p><b>Purpose</b><br>
   * Processes style update requests and broadcasts to relevant clients.
   *
   * @param session - Client session
   * @param message - Update message
   *
   * @doc.type method
   * @doc.purpose Handle style update
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleUpdateStyle(
    session: ClientSession,
    message: PreviewMessage
  ): void {
    const { componentId, styles } = message.data;

    this.emit('style-updated', {
      clientId: session.id,
      componentId,
      styles,
    });

    // Broadcast to other clients
    this.broadcast(
      {
        type: 'style-changed',
        data: {
          componentId,
          styles,
          updatedBy: session.id,
          timestamp: Date.now(),
        },
      },
      session.id
    );
  }

  /**
   * Handles ping message.
   *
   * <p><b>Purpose</b><br>
   * Responds to client ping for connection health check.
   *
   * @param session - Client session
   * @param message - Ping message
   *
   * @doc.type method
   * @doc.purpose Handle ping
   * @doc.layer product
   * @doc.pattern Server
   */
  private handlePing(session: ClientSession, message: PreviewMessage): void {
    this.sendMessage(session.ws, {
      type: 'pong',
      data: {
        timestamp: Date.now(),
        clientId: session.id,
      },
    });
  }

  /**
   * Handles client disconnection.
   *
   * <p><b>Purpose</b><br>
   * Cleans up client session and notifies other clients.
   *
   * @param clientId - Client ID
   *
   * @doc.type method
   * @doc.purpose Handle disconnection
   * @doc.layer product
   * @doc.pattern Server
   */
  private handleDisconnection(clientId: string): void {
    this.clients.delete(clientId);
    this.emit('client-disconnected', { clientId });
  }

  /**
   * Sends message to specific client.
   *
   * <p><b>Purpose</b><br>
   * Sends a message to a specific WebSocket client.
   *
   * @param ws - WebSocket connection
   * @param message - Message to send
   *
   * @doc.type method
   * @doc.purpose Send message
   * @doc.layer product
   * @doc.pattern Server
   */
  private sendMessage(ws: WebSocket, message: PreviewMessage): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
    }
  }

  /**
   * Broadcasts message to all clients except sender.
   *
   * <p><b>Purpose</b><br>
   * Sends a message to all connected clients except the specified sender.
   *
   * @param message - Message to broadcast
   * @param excludeClientId - Client ID to exclude
   *
   * @doc.type method
   * @doc.purpose Broadcast message
   * @doc.layer product
   * @doc.pattern Server
   */
  private broadcast(message: PreviewMessage, excludeClientId?: string): void {
    for (const [clientId, session] of this.clients) {
      if (excludeClientId && clientId === excludeClientId) {
        continue;
      }

      this.sendMessage(session.ws, message);
    }
  }

  /**
   * Generates unique client ID.
   *
   * <p><b>Purpose</b><br>
   * Creates a unique identifier for each connected client.
   *
   * @returns Unique client ID
   *
   * @doc.type method
   * @doc.purpose Generate client ID
   * @doc.layer product
   * @doc.pattern Utility
   */
  private generateClientId(): string {
    return `client-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Gets connected clients count.
   *
   * <p><b>Purpose</b><br>
   * Returns the number of currently connected clients.
   *
   * @returns Number of connected clients
   *
   * @doc.type method
   * @doc.purpose Get client count
   * @doc.layer product
   * @doc.pattern Utility
   */
  getClientCount(): number {
    return this.clients.size;
  }

  /**
   * Gets server status.
   *
   * <p><b>Purpose</b><br>
   * Returns current server status and statistics.
   *
   * @returns Server status object
   *
   * @doc.type method
   * @doc.purpose Get server status
   * @doc.layer product
   * @doc.pattern Utility
   */
  getStatus(): {
    running: boolean;
    port: number;
    host: string;
    clientCount: number;
    maxConnections: number;
  } {
    return {
      running: this.wss !== null,
      port: this.options.port,
      host: this.options.host,
      clientCount: this.clients.size,
      maxConnections: this.options.maxConnections,
    };
  }
}

export type { LivePreviewServerOptions, PreviewMessage, ClientSession } from './types';
