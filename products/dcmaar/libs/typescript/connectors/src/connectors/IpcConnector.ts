/**
 * @fileoverview IPC connector template supporting Unix sockets, named pipes, and message passing.
 *
 * Demonstrates how to coordinate inter-process communication for connectors and local agents. Pair
 * with `RetryPolicy` for reliable command delivery and `Telemetry` to trace IPC message latency.
 *
 * @see {@link IpcConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';
// imports reordered to remove unused EventEmitter

/**
 * Configuration contract for `IpcConnector`.
 */
export interface IpcConnectorConfig extends ConnectionOptions {
  /**
   * IPC channel name or path
   */
  channel: string;
  
  /**
   * IPC mode: 'server' or 'client'
   * @default 'client'
   */
  mode?: 'server' | 'client';
  
  /**
   * Transport type: 'socket' or 'pipe'
   * @default 'socket'
   */
  transport?: 'socket' | 'pipe';
  
  /**
   * Socket path (for Unix domain sockets)
   */
  socketPath?: string;
  
  /**
   * Named pipe path (for Windows)
   */
  pipePath?: string;
  
  /**
   * Enable/disable message serialization
   * @default true
   */
  serialize?: boolean;
  
  /**
   * Serialization format: 'json' or 'msgpack'
   * @default 'json'
   */
  format?: 'json' | 'msgpack';
  
  /**
   * Maximum message size in bytes
   * @default 1MB
   */
  maxMessageSize?: number;
  
  /**
   * Enable/disable message acknowledgment
   * @default false
   */
  ack?: boolean;
  
  /**
   * Acknowledgment timeout in milliseconds
   * @default 5000
   */
  ackTimeout?: number;
}

/**
 * Inter‑process communication connector using sockets or pipes.
 *
 * **Example (client broadcasting message):**
 * ```ts
 * const connector = new IpcConnector({ channel: 'dcmaar-ipc', mode: 'client' });
 * await connector.connect();
 * await connector.send({ ping: true });
 * ```
 *
 * **Example (server mode with targeted send + ack):**
 * ```ts
 * const connector = new IpcConnector({ channel: 'dcmaar-ipc', mode: 'server', ack: true });
 * await connector.connect();
 * connector.on('clientConnected', ({ connectionId }) => console.log('client', connectionId));
 * await connector.send({ command: 'flush' }, { targetId: 'client-123' });
 * ```
 */
export class IpcConnector extends BaseConnector<IpcConnectorConfig> {
  private server: unknown = null;
  private client: unknown = null;
  private connections: Map<string, any> = new Map();
  private pendingAcks: Map<string, { resolve: Function; reject: Function; timeout: NodeJS.Timeout }> = new Map();

  constructor(config: IpcConnectorConfig) {
    super({
      mode: 'client',
      transport: 'socket',
      serialize: true,
      format: 'json',
      maxMessageSize: 1024 * 1024, // 1MB
      ack: false,
      ackTimeout: 5000,
      ...config,
      type: 'ipc',
    });
  }

  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    if (this._config.mode === 'server') {
      await this._startServer();
    } else {
      await this._startClient();
    }
  }

  /** @inheritdoc */
  protected async _disconnect(): Promise<void> {
    // Clear pending acknowledgments
    for (const { reject, timeout } of this.pendingAcks.values()) {
      clearTimeout(timeout);
      reject(new Error('Connection closed'));
    }
    this.pendingAcks.clear();

    if (this._config.mode === 'server') {
      await this._stopServer();
    } else {
      await this._stopClient();
    }
  }

  /**
   * Sends a message to the active endpoint (server or client).
   *
   * @param data - Payload to transmit.
   * @param options - Optional `{ targetId }` for server broadcast targeting.
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    if (!this.client && this.connections.size === 0) {
      throw new Error('IPC connector is not connected');
    }

    const message = this._prepareMessage(data, options);

    if (this._config.mode === 'server') {
      // Broadcast to all connected clients
      const targetId = options.targetId;
      if (targetId) {
        const connection = this.connections.get(targetId);
        if (connection) {
          await this._sendToConnection(connection, message);
        } else {
          throw new Error(`Client ${targetId} not found`);
        }
      } else {
        // Broadcast to all
        await Promise.all(
          Array.from(this.connections.values()).map(conn => 
            this._sendToConnection(conn, message)
          )
        );
      }
    } else {
      // Send to server
      await this._sendToConnection(this.client, message);
    }

    // Wait for acknowledgment if enabled
    if (this._config.ack && message.id) {
      await this._waitForAck(message.id);
    }
  }

  /** Starts an IPC server and begins accepting connections. */
  private async _startServer(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // In a real implementation, you would use Node.js 'net' module:
        // const net = require('net');
        // 
        // this.server = net.createServer((socket) => {
        //   const connectionId = uuidv4();
        //   this.connections.set(connectionId, socket);
        //   
        //   socket.on('data', (data) => {
        //     this._handleMessage(data, connectionId);
        //   });
        //   
        //   socket.on('close', () => {
        //     this.connections.delete(connectionId);
        //     this.emit('clientDisconnected', { connectionId });
        //   });
        //   
        //   socket.on('error', (error) => {
        //     this.emit('error', error);
        //   });
        //   
        //   this.emit('clientConnected', { connectionId });
        // });
        // 
        // const path = this._getSocketPath();
        // this.server.listen(path, () => {
        //   this.emit('serverStarted', { path });
        //   resolve();
        // });
        // 
        // this.server.on('error', (error) => {
        //   reject(error);
        // });

        // Simulate server start
        this.emit('serverStarted', { path: this._getSocketPath() });
        resolve();
      } catch (error) {
        reject(error);
      }
    });
  }

  /** Gracefully stops the IPC server and disconnects clients. */
  private async _stopServer(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.server) {
        resolve();
        return;
      }

      // Close all client connections
      for (const connection of this.connections.values()) {
        try {
          connection.end();
        } catch (error) {
          this.emit('error', error);
        }
      }
      this.connections.clear();

      // Close server
      // this.server.close(() => {
      //   this.server = null;
      //   this.emit('serverStopped');
      //   resolve();
      // });

      this.server = null;
      this.emit('serverStopped');
      resolve();
    });
  }

  /** Connects to an IPC server as a client. */
  private async _startClient(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // In a real implementation:
        // const net = require('net');
        // 
        // const path = this._getSocketPath();
        // this.client = net.createConnection(path, () => {
        //   this.emit('connected');
        //   resolve();
        // });
        // 
        // this.client.on('data', (data) => {
        //   this._handleMessage(data);
        // });
        // 
        // this.client.on('close', () => {
        //   this.emit('disconnected');
        // });
        // 
        // this.client.on('error', (error) => {
        //   this.emit('error', error);
        //   reject(error);
        // });

        // Simulate client connection
        this.emit('connected');
        resolve();
      } catch (error) {
        reject(error);
      }
    });
  }

  /** Disconnects the IPC client from the server. */
  private async _stopClient(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.client) {
        resolve();
        return;
      }

      // this.client.end(() => {
      //   this.client = null;
      //   resolve();
      // });

      this.client = null;
      resolve();
    });
  }

  /** Builds a normalized message envelope honoring serialization settings. */
  private _prepareMessage(data: unknown, options: Record<string, any>): unknown {
    const message = {
      id: options.messageId || uuidv4(),
      type: options.type || 'message',
      timestamp: Date.now(),
      payload: data,
      ack: this._config.ack,
    };

    if (this._config.serialize) {
      return this._serialize(message);
    }

    return message;
  }

  /** Serializes a message according to `format`. */
  private _serialize(data: unknown): Buffer | string {
    switch (this._config.format) {
      case 'json':
        return JSON.stringify(data);
      case 'msgpack':
        // In a real implementation, use msgpack library
        // const msgpack = require('msgpack');
        // return msgpack.pack(data);
        return JSON.stringify(data);
      default:
        return JSON.stringify(data);
    }
  }

  /** Deserializes raw message back to an object. */
  private _deserialize(data: Buffer | string): unknown {
    const str = Buffer.isBuffer(data) ? data.toString() : data;
    
    switch (this._config.format) {
      case 'json':
        return JSON.parse(str);
      case 'msgpack':
        // In a real implementation, use msgpack library
        // const msgpack = require('msgpack');
        // return msgpack.unpack(data);
        return JSON.parse(str);
      default:
        return JSON.parse(str);
    }
  }

  /** Handles inbound messages, acking when requested and emitting events. */
  private _handleMessage(data: Buffer | string, connectionId?: string): void {
    try {
      const message = this._config.serialize ? this._deserialize(data) : data;

      // Handle acknowledgment messages
      if (message && typeof message === 'object' && 'type' in message && message.type === 'ack') {
        if ('id' in message && typeof message.id === 'string') {
          this._handleAck(message.id);
        }
        return;
      }

      // Send acknowledgment if required
      if (message && typeof message === 'object' && 'ack' in message && message.ack) {
        if ('id' in message && typeof message.id === 'string') {
          this._sendAck(message.id, connectionId);
        }
      }

      // Emit event
      if (message && typeof message === 'object') {
        this._emitEvent({
          id: ('id' in message && typeof message.id === 'string') ? message.id : uuidv4(),
          type: ('type' in message && typeof message.type === 'string') ? message.type : 'message',
          timestamp: ('timestamp' in message && typeof message.timestamp === 'number') ? message.timestamp : Date.now(),
          payload: ('payload' in message) ? message.payload : undefined,
          metadata: {
            connectionId,
            channel: this._config.channel,
          },
        });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('error', new Error(`IPC handler error: ${errorMessage}`));
    }
  }

  /** Writes a message to a specific connection. */
  private async _sendToConnection(connection: unknown, _message: unknown): Promise<void> {
    return new Promise((resolve, reject) => {
      if (!connection) {
        reject(new Error('Connection is null'));
        return;
      }

      // connection.write(message, (error) => {
      //   if (error) {
      //     reject(error);
      //   } else {
      //     resolve();
      //   }
      // });

      // Simulate send
      resolve();
    });
  }

  /** Sends an acknowledgment for a received message ID. */
  private _sendAck(messageId: string, connectionId?: string): void {
    const ackMessage = {
      id: uuidv4(),
      type: 'ack',
      messageId,
      timestamp: Date.now(),
    };

    const serialized = this._serialize(ackMessage);

    if (this._config.mode === 'server' && connectionId) {
      const connection = this.connections.get(connectionId);
      if (connection) {
        this._sendToConnection(connection, serialized).catch(error => {
          this.emit('error', error);
        });
      }
    } else if (this.client) {
      this._sendToConnection(this.client, serialized).catch(error => {
        this.emit('error', error);
      });
    }
  }

  /** Resolves a pending acknowledgment promise for the given ID. */
  private _handleAck(messageId: string): void {
    const pending = this.pendingAcks.get(messageId);
    if (pending) {
      clearTimeout(pending.timeout);
      pending.resolve();
      this.pendingAcks.delete(messageId);
    }
  }

  /** Awaits an acknowledgment event for the given message ID. */
  private async _waitForAck(messageId: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingAcks.delete(messageId);
        reject(new Error(`Acknowledgment timeout for message ${messageId}`));
      }, this._config.ackTimeout);

      this.pendingAcks.set(messageId, { resolve, reject, timeout });
    });
  }

  /** Resolves platform-specific socket/pipe path for the configured channel. */
  private _getSocketPath(): string {
    if (this._config.socketPath) {
      return this._config.socketPath;
    }

    if (this._config.pipePath) {
      return this._config.pipePath;
    }

    // Default socket path
    const platform = process.platform;
    if (platform === 'win32') {
      return `\\\\.\\pipe\\${this._config.channel}`;
    } else {
      return `/tmp/${this._config.channel}.sock`;
    }
  }

  /**
   * Get list of connected clients (server mode only)
   */
  /** Returns identifiers for currently connected clients (server mode). */
  public getConnections(): string[] {
    return Array.from(this.connections.keys());
  }

  /**
   * Get connection count (server mode only)
   */
  /** Returns count of currently connected clients (server mode). */
  public getConnectionCount(): number {
    return this.connections.size;
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    this.pendingAcks.clear();
    await super.destroy();
  }
}
