/**
 * Bridge Source Adapter
 *
 * Receives events from Desktop and Extension apps via the bridge protocol.
 * Implements the DCMAAR bridge protocol v1 for cross-application telemetry.
 */

import { BaseConnector, type IConnector } from '@ghatana/dcmaar-connectors';
import type { SourceAdapter } from '../types';
import { Logger } from '../../utils/logger';
import { WebSocketServer, type WebSocket } from 'ws';
import { EventEmitter } from 'eventemitter3';

/**
 * Source adapter for Extension/Desktop bridge protocol
 *
 * Receives telemetry from browser extensions and desktop apps
 * via WebSocket or IPC bridge connections.
 */
export class BridgeSourceAdapter implements SourceAdapter {
  readonly type = 'bridge';
  private logger: Logger;

  constructor() {
    this.logger = new Logger('BridgeSourceAdapter');
  }

  async create(config: unknown): Promise<IConnector> {
    this.logger.debug('Creating BridgeSource connector', { id: config.id });
    return new BridgeSourceConnector(config);
  }
}

/**
 * Bridge Source Connector
 *
 * Listens for incoming telemetry events from Desktop/Extension apps
 * and emits them to the connector system for processing.
 */
class BridgeSourceConnector extends BaseConnector<unknown> {
  private logger: Logger;
  private server: WebSocketServer | null = null;
  private protocol: string;
  private port: number;
  private clients: Set<WebSocket> = new Set();
  private eventBus: EventEmitter = new EventEmitter();
  private messageCount: number = 0;

  constructor(config: unknown) {
    super(config);
    this.logger = new Logger('BridgeSourceConnector');

    // Get configuration
    const metadata = config.metadata || {};
    this.protocol = metadata.protocol || 'dcmaar-bridge-v1';
    this.port = config.port || metadata.port || 9000;
  }

  protected async _connect(): Promise<void> {
    this.logger.info('BridgeSource connecting', {
      id: this._config.id,
      protocol: this.protocol,
      port: this.port,
    });

    // Create WebSocket server
    this.server = new WebSocketServer({ port: this.port });

    // Handle new connections
    this.server.on('connection', (ws: WebSocket, req) => {
      const clientIp = req.socket.remoteAddress;
      this.logger.info('Bridge client connected', {
        id: this._config.id,
        clientIp,
        clientCount: this.clients.size + 1,
      });

      // Add client to set
      this.clients.add(ws);

      // Handle incoming messages
      ws.on('message', (data: Buffer) => {
        this._handleIncomingMessage(ws, data);
      });

      // Handle client disconnect
      ws.on('close', () => {
        this.clients.delete(ws);
        this.logger.info('Bridge client disconnected', {
          id: this._config.id,
          clientIp,
          clientCount: this.clients.size,
        });
      });

      // Handle errors
      ws.on('error', (error) => {
        this.logger.error('Bridge client error', {
          id: this._config.id,
          clientIp,
          error: error.message,
        });
      });

      // Send welcome message
      this._sendMessage(ws, {
        type: 'welcome',
        protocol: this.protocol,
        timestamp: Date.now(),
      });
    });

    // Handle server errors
    this.server.on('error', (error) => {
      this.logger.error('Bridge server error', {
        id: this._config.id,
        port: this.port,
        error: error.message,
      });
    });

    this.logger.info('BridgeSource connected and listening', {
      id: this._config.id,
      port: this.port,
      protocol: this.protocol,
    });
  }

  protected async _disconnect(): Promise<void> {
    this.logger.info('BridgeSource disconnecting', { id: this._config.id });

    // Close all client connections
    for (const client of this.clients) {
      client.close();
    }
    this.clients.clear();

    // Close server
    if (this.server) {
      return new Promise((resolve) => {
        this.server!.close(() => {
          this.logger.info('BridgeSource server closed', {
            id: this._config.id,
            totalMessages: this.messageCount,
          });
          this.server = null;
          resolve();
        });
      });
    }

    this.logger.info('BridgeSource disconnected', { id: this._config.id });
  }

  async send(_data: unknown): Promise<void> {
    // Source connectors don't send data, they only receive
    throw new Error('BridgeSource is a source-only connector (cannot send)');
  }

  /**
   * Handle incoming WebSocket message
   */
  private _handleIncomingMessage(ws: WebSocket, data: Buffer): void {
    try {
      const message = JSON.parse(data.toString());
      this.messageCount++;

      this.logger.debug('Received bridge message', {
        id: this._config.id,
        messageId: message.id,
        type: message.type,
        count: this.messageCount,
      });

      // Validate message format
      if (!message.id || !message.type || !message.payload) {
        this.logger.warn('Invalid bridge message format', {
          id: this._config.id,
          message,
        });
        this._sendMessage(ws, {
          type: 'error',
          error: 'Invalid message format',
          originalMessageId: message.id,
        });
        return;
      }

      // Emit event through event bus
      this.eventBus.emit('event', {
        id: message.id,
        type: message.type,
        timestamp: message.timestamp || Date.now(),
        payload: message.payload,
        metadata: message.metadata || {},
      });

      this.logger.debug('Bridge event emitted', {
        id: this._config.id,
        eventType: message.type,
        eventId: message.id,
      });

      // Send acknowledgment
      this._sendMessage(ws, {
        type: 'ack',
        messageId: message.id,
        timestamp: Date.now(),
      });
    } catch (error) {
      this.logger.error('Failed to parse bridge message', {
        id: this._config.id,
        error: error instanceof Error ? error.message : String(error),
      });
      this._sendMessage(ws, {
        type: 'error',
        error: 'Failed to parse message',
      });
    }
  }

  /**
   * Send message to WebSocket client
   */
  private _sendMessage(ws: WebSocket, message: unknown): void {
    try {
      ws.send(JSON.stringify(message));
    } catch (error) {
      this.logger.error('Failed to send message to client', {
        id: this._config.id,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  /**
   * Get connector statistics
   */
  getStats(): { clients: number; messageCount: number; isListening: boolean } {
    return {
      clients: this.clients.size,
      messageCount: this.messageCount,
      isListening: this.server !== null,
    };
  }
}
