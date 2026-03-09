/**
 * @fileoverview mTLS connector template supporting client/server TLS with cert validation.
 *
 * Provides scaffolding for mutual TLS client and server flows, certificate management, and event
 * emission for observability. Integrate with `Telemetry` to trace handshake latencies and
 * `RetryPolicy` to retry failed sends.
 *
 * @see {@link MtlsConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../resilience/RetryPolicy.RetryPolicy | RetryPolicy}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';
import * as tls from 'tls';
import * as fs from 'fs';

export interface MtlsConnectorConfig extends ConnectionOptions {
  /**
   * Server host
   */
  host: string;
  
  /**
   * Server port
   */
  port: number;
  
  /**
   * Client certificate path or content
   */
  cert: string;
  
  /**
   * Client private key path or content
   */
  key: string;
  
  /**
   * CA certificate path or content
   */
  ca: string;
  
  /**
   * Passphrase for the private key
   */
  passphrase?: string;
  
  /**
   * Enable/disable certificate verification
   * @default true
   */
  rejectUnauthorized?: boolean;
  
  /**
   * Check server identity
   * @default true
   */
  checkServerIdentity?: boolean;
  
  /**
   * Server name for SNI
   */
  servername?: string;
  
  /**
   * Minimum TLS version
   * @default 'TLSv1.2'
   */
  minVersion?: 'TLSv1' | 'TLSv1.1' | 'TLSv1.2' | 'TLSv1.3';
  
  /**
   * Maximum TLS version
   * @default 'TLSv1.3'
   */
  maxVersion?: 'TLSv1' | 'TLSv1.1' | 'TLSv1.2' | 'TLSv1.3';
  
  /**
   * Allowed cipher suites
   */
  ciphers?: string;
  
  /**
   * Enable/disable session resumption
   * @default true
   */
  sessionResumption?: boolean;
  
  /**
   * Session timeout in seconds
   * @default 300
   */
  sessionTimeout?: number;
  
  /**
   * Enable/disable ALPN
   * @default false
   */
  alpn?: boolean;
  
  /**
   * ALPN protocols
   */
  alpnProtocols?: string[];
  
  /**
   * Connection mode: 'client' or 'server'
   * @default 'client'
   */
  mode?: 'client' | 'server';
  
  /**
   * Request client certificate (server mode)
   * @default true
   */
  requestCert?: boolean;
  
  /**
   * Reject unauthorized clients (server mode)
   * @default true
   */
  rejectUnauthorizedClients?: boolean;
}

/**
 * mTLS Connector for mutual TLS authentication
 * Supports both client and server modes with certificate validation
 *
 * **Example (client mode):**
 * ```ts
 * const connector = new MtlsConnector({
 *   host: 'api.service',
 *   port: 443,
 *   cert: '/etc/certs/client.crt',
 *   key: '/etc/certs/client.key',
 *   ca: '/etc/certs/ca.pem'
 * });
 * await connector.connect();
 * await connector.send({ command: 'ping' });
 * ```
 *
 * **Example (server mode broadcasting to clients):**
 * ```ts
 * const connector = new MtlsConnector({
 *   mode: 'server',
 *   host: '0.0.0.0',
 *   port: 8443,
 *   cert: '/etc/certs/server.crt',
 *   key: '/etc/certs/server.key',
 *   ca: '/etc/certs/ca.pem',
 *   requestCert: true,
 *   rejectUnauthorizedClients: true
 * });
 * await connector.connect();
 * connector.on('clientConnected', ({ clientId }) => console.log('client', clientId));
 * await connector.send({ notice: 'maintenance' });
 * ```
 */
export class MtlsConnector extends BaseConnector<MtlsConnectorConfig> {
  private socket: tls.TLSSocket | null = null;
  private server: tls.Server | null = null;
  private clients: Map<string, tls.TLSSocket> = new Map();

  /**
   * Initializes mTLS with secure defaults (TLSv1.2+, verify enabled).
   */
  constructor(config: MtlsConnectorConfig) {
    super({
      rejectUnauthorized: true,
      checkServerIdentity: true,
      minVersion: 'TLSv1.2',
      maxVersion: 'TLSv1.3',
      sessionResumption: true,
      sessionTimeout: 300,
      alpn: false,
      mode: 'client',
      requestCert: true,
      rejectUnauthorizedClients: true,
      ...config,
      type: 'mtls',
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
    if (this._config.mode === 'server') {
      await this._stopServer();
    } else {
      await this._stopClient();
    }
  }

  /** Sends payload to server or broadcast to clients in server mode. */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    const payload = typeof data === 'string' ? data : JSON.stringify(data);

    if (this._config.mode === 'server') {
      // Send to specific client or broadcast
      const clientId = options.clientId;
      if (clientId) {
        const client = this.clients.get(clientId);
        if (!client) {
          throw new Error(`Client ${clientId} not found`);
        }
        await this._sendToSocket(client, payload);
      } else {
        // Broadcast to all clients
        await Promise.all(
          Array.from(this.clients.values()).map(client => 
            this._sendToSocket(client, payload)
          )
        );
      }
    } else {
      // Send to server
      if (!this.socket) {
        throw new Error('mTLS socket is not connected');
      }
      await this._sendToSocket(this.socket, payload);
    }
  }

  /** Establishes a TLS client connection and verifies authorization. */
  private async _startClient(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const options: tls.ConnectionOptions = {
          host: this._config.host,
          port: this._config.port,
          cert: this._loadCertificate(this._config.cert),
          key: this._loadPrivateKey(this._config.key),
          ca: this._loadCA(this._config.ca),
          passphrase: this._config.passphrase,
          rejectUnauthorized: this._config.rejectUnauthorized,
          checkServerIdentity: this._config.checkServerIdentity 
            ? tls.checkServerIdentity 
            : () => undefined,
          servername: this._config.servername || this._config.host,
          minVersion: this._config.minVersion,
          maxVersion: this._config.maxVersion,
          ciphers: this._config.ciphers,
          ALPNProtocols: this._config.alpnProtocols,
        };

        this.socket = tls.connect(options, () => {
          if (!this.socket) return;

          // Verify the connection
          if (!this.socket.authorized && this._config.rejectUnauthorized) {
            const error = new Error(`TLS authorization failed: ${this.socket.authorizationError}`);
            this.socket.destroy();
            reject(error);
            return;
          }

          // Get certificate information
          const peerCert = this.socket.getPeerCertificate();
          this.emit('connected', {
            authorized: this.socket.authorized,
            peerCertificate: this._sanitizeCertificate(peerCert),
            protocol: this.socket.getProtocol(),
            cipher: this.socket.getCipher(),
          });

          resolve();
        });

        this.socket.on('data', (data) => this._handleData(data));
        this.socket.on('error', (error) => {
          this.emit('error', error);
          reject(error);
        });
        this.socket.on('close', () => {
          this.emit('disconnected');
        });
        this.socket.on('secureConnect', () => {
          this.emit('secureConnect');
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  /** Gracefully closes the TLS client connection. */
  private async _stopClient(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.socket) {
        resolve();
        return;
      }

      this.socket.end(() => {
        this.socket = null;
        resolve();
      });
    });
  }

  /** Starts a TLS server and accepts client connections. */
  private async _startServer(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const options: tls.TlsOptions = {
          cert: this._loadCertificate(this._config.cert),
          key: this._loadPrivateKey(this._config.key),
          ca: this._loadCA(this._config.ca),
          passphrase: this._config.passphrase,
          requestCert: this._config.requestCert,
          rejectUnauthorized: this._config.rejectUnauthorizedClients,
          minVersion: this._config.minVersion,
          maxVersion: this._config.maxVersion,
          ciphers: this._config.ciphers,
          ALPNProtocols: this._config.alpnProtocols,
          sessionTimeout: this._config.sessionTimeout,
        };

        this.server = tls.createServer(options, (socket) => {
          this._handleClientConnection(socket);
        });

        this.server.listen(this._config.port, this._config.host, () => {
          this.emit('serverStarted', {
            host: this._config.host,
            port: this._config.port,
          });
          resolve();
        });

        this.server.on('error', (error) => {
          this.emit('error', error);
          reject(error);
        });

        this.server.on('tlsClientError', (error, socket) => {
          this.emit('tlsClientError', { error, socket });
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  /** Gracefully shuts down the TLS server and clients. */
  private async _stopServer(): Promise<void> {
    return new Promise((resolve) => {
      if (!this.server) {
        resolve();
        return;
      }

      // Close all client connections
      for (const client of this.clients.values()) {
        try {
          client.end();
        } catch (error) {
          this.emit('error', error);
        }
      }
      this.clients.clear();

      // Close server
      this.server.close(() => {
        this.server = null;
        this.emit('serverStopped');
        resolve();
      });
    });
  }

  /** Handles new client connections, validating certificates as configured. */
  private _handleClientConnection(socket: tls.TLSSocket): void {
    const clientId = uuidv4();
    this.clients.set(clientId, socket);

    // Verify client certificate
    if (!socket.authorized && this._config.rejectUnauthorizedClients) {
      this.emit('clientRejected', {
        clientId,
        error: socket.authorizationError,
      });
      socket.destroy();
      return;
    }

    const peerCert = socket.getPeerCertificate();
    this.emit('clientConnected', {
      clientId,
      authorized: socket.authorized,
      peerCertificate: this._sanitizeCertificate(peerCert),
      protocol: socket.getProtocol(),
      cipher: socket.getCipher(),
    });

    socket.on('data', (data) => this._handleData(data, clientId));
    socket.on('error', (error) => {
      this.emit('clientError', { clientId, error });
    });
    socket.on('close', () => {
      this.clients.delete(clientId);
      this.emit('clientDisconnected', { clientId });
    });
  }

  /** Normalizes inbound TLS data and emits structured events. */
  private _handleData(data: Buffer, clientId?: string): void {
    try {
      const str = data.toString();
      let payload: unknown;

      try {
        payload = JSON.parse(str);
      } catch {
        payload = str;
      }

      this._emitEvent({
        id: uuidv4(),
        type: 'message',
        timestamp: Date.now(),
        payload,
        metadata: {
          clientId,
          size: data.length,
        },
      });
    } catch (error) {
      this.emit('error', error);
    }
  }

  /** Writes a message to a TLS socket and awaits write completion. */
  private async _sendToSocket(socket: tls.TLSSocket, data: string): Promise<void> {
    return new Promise((resolve, reject) => {
      socket.write(data, (error) => {
        if (error) {
          reject(error);
        } else {
          resolve();
        }
      });
    });
  }

  /** Loads certificate from file path or inline content. */
  private _loadCertificate(cert: string): Buffer {
    try {
      // Check if it's a file path
      if (fs.existsSync(cert)) {
        return fs.readFileSync(cert);
      }
      // Assume it's the certificate content
      return Buffer.from(cert);
    } catch (error) {
      throw new Error(`Failed to load certificate: ${error}`);
    }
  }

  /** Loads private key from file path or inline content. */
  private _loadPrivateKey(key: string): Buffer {
    try {
      // Check if it's a file path
      if (fs.existsSync(key)) {
        return fs.readFileSync(key);
      }
      // Assume it's the key content
      return Buffer.from(key);
    } catch (error) {
      throw new Error(`Failed to load private key: ${error}`);
    }
  }

  /** Loads CA certificate from file path or inline content. */
  private _loadCA(ca: string): Buffer {
    try {
      // Check if it's a file path
      if (fs.existsSync(ca)) {
        return fs.readFileSync(ca);
      }
      // Assume it's the CA content
      return Buffer.from(ca);
    } catch (error) {
      throw new Error(`Failed to load CA certificate: ${error}`);
    }
  }

  /** Redacts and normalizes certificate properties for telemetry. */
  private _sanitizeCertificate(cert: unknown): unknown {
    if (!cert) return null;

    return {
      subject: cert.subject,
      issuer: cert.issuer,
      valid_from: cert.valid_from,
      valid_to: cert.valid_to,
      fingerprint: cert.fingerprint,
      fingerprint256: cert.fingerprint256,
      serialNumber: cert.serialNumber,
    };
  }

  /**
   * Get certificate information
   */
  /** Returns certificate/cipher/protocol info for the active client. */
  public getCertificateInfo(): unknown {
    if (this._config.mode === 'client' && this.socket) {
      return {
        peer: this._sanitizeCertificate(this.socket.getPeerCertificate()),
        local: this._sanitizeCertificate(this.socket.getCertificate()),
        protocol: this.socket.getProtocol(),
        cipher: this.socket.getCipher(),
      };
    }
    return null;
  }

  /**
   * Get connected clients (server mode)
   */
  /** Returns identifiers of connected TLS clients (server mode). */
  public getClients(): string[] {
    return Array.from(this.clients.keys());
  }

  /**
   * Get client count (server mode)
   */
  /** Returns count of connected TLS clients (server mode). */
  public getClientCount(): number {
    return this.clients.size;
  }

  /**
   * Verify a certificate
   */
  public static verifyCertificate(_cert: Buffer, _ca: Buffer): boolean {
    // In a real implementation, use crypto module to verify
    // const crypto = require('crypto');
    // const verify = crypto.createVerify('SHA256');
    // verify.update(cert);
    // return verify.verify(ca, signature);
    return true;
  }

  /** @inheritdoc */
  public override async destroy(): Promise<void> {
    this.clients.clear();
    await super.destroy();
  }
}
