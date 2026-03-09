/**
 * @fileoverview MQTT over TLS connector template extending core MQTT behaviour with TLS options.
 *
 * Adds certificate loading, protocol negotiation, and secure defaults on top of `MqttConnector`.
 * Demonstrates how to prepare TLS options and integrate with PKI workflows.
 *
 * @see {@link MqttsConnector}
 * @see {@link ./MqttConnector.MqttConnector | MqttConnector}
 */
import * as fs from 'fs';
import { MqttConnector, MqttConnectorConfig } from './MqttConnector';

/**
 * Extended configuration accepted by `MqttsConnector`.
 */
export interface MqttsConnectorConfig extends MqttConnectorConfig {
  /**
   * Client certificate path or content
   */
  cert?: string;
  
  /**
   * Client private key path or content
   */
  key?: string;
  
  /**
   * CA certificate path or content
   */
  ca?: string;
  
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
   * TLS protocol version
   * @default 'TLSv1.2'
   */
  protocol?: 'TLSv1' | 'TLSv1.1' | 'TLSv1.2' | 'TLSv1.3';
  
  /**
   * Allowed cipher suites
   */
  ciphers?: string;
  
  /**
   * Server name for SNI
   */
  servername?: string;
  
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
   * Enable/disable session resumption
   * @default true
   */
  sessionResumption?: boolean;
  
  /**
   * PFX or PKCS12 encoded private key and certificate chain
   */
  pfx?: string | Buffer;
  
  /**
   * Enable/disable certificate revocation list checking
   * @default false
   */
  crl?: boolean;
  
  /**
   * Certificate revocation list
   */
  crlList?: string[];
}

/**
 * TLS-enabled MQTT connector providing certificate-based configuration scaffolding.
 *
 * **Example (mTLS-enabled publish):**
 * ```ts
 * const connector = new MqttsConnector({
 *   url: 'mqtt://broker:1883',
 *   cert: '/etc/certs/client.crt',
 *   key: '/etc/certs/client.key',
 *   ca: '/etc/certs/ca.pem',
 *   rejectUnauthorized: true
 * });
 * await connector.connect();
 * await connector.send({ status: 'secure' }, { topic: 'secure/status' });
 * ```
 */
export class MqttsConnector extends MqttConnector {
  protected tlsConfig: MqttsConnectorConfig;

  /**
   * Wraps base MQTT configuration ensuring secure defaults.
   */
  constructor(config: MqttsConnectorConfig) {
    const {
      rejectUnauthorized = true,
      protocol = 'TLSv1.2',
      sessionResumption = true,
      alpn = false,
      crl = false,
      ...mqttConfig
    } = config;

    const url = config.url.replace(/^mqtt:\/\//, 'mqtts://');

    super({
      ...mqttConfig,
      url,
    });

    this.tlsConfig = {
      ...config,
      rejectUnauthorized,
      protocol,
      sessionResumption,
      alpn,
      crl,
    };
  }

  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // In a real implementation using 'mqtt' package:
        // const mqtt = require('mqtt');
        // 
        // const tlsOptions = this._prepareTlsOptions();
        // 
        // this.client = mqtt.connect(this._config.url, {
        //   ...this._getBaseMqttOptions(),
        //   ...tlsOptions,
        // });
        // 
        // this.client.on('connect', () => {
        //   this._handleConnect();
        //   resolve();
        // });
        // 
        // this.client.on('message', (topic: string, message: Buffer) => {
        //   this._handleMessage(topic, message);
        // });
        // 
        // this.client.on('error', (error: Error) => {
        //   this._handleError(error);
        //   reject(error);
        // });
        // 
        // this.client.on('close', () => {
        //   this._handleClose();
        // });

        // Simulate connection
        setTimeout(() => {
          this.emit('connected', {
            protocol: this.tlsConfig.protocol,
            ciphers: this.tlsConfig.ciphers,
            secure: true,
          });
          resolve();
        }, 100);
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Builds TLS options payload for the underlying MQTT client.
   *
   * Loads certificates/keys from disk when necessary and merges TLS flags such as ALPN and session
   * resumption. Extend to integrate HSM-backed key stores or dynamic certificate refresh.
   */
  private _prepareTlsOptions(): unknown {
    const options: unknown = {
      rejectUnauthorized: this.tlsConfig.rejectUnauthorized,
      protocol: this.tlsConfig.protocol,
    };

    // Load certificates
    if (this.tlsConfig.cert) {
      options.cert = this._loadCertificate(this.tlsConfig.cert);
    }

    if (this.tlsConfig.key) {
      options.key = this._loadPrivateKey(this.tlsConfig.key);
    }

    if (this.tlsConfig.ca) {
      options.ca = this._loadCA(this.tlsConfig.ca);
    }

    if (this.tlsConfig.passphrase) {
      options.passphrase = this.tlsConfig.passphrase;
    }

    if (this.tlsConfig.ciphers) {
      options.ciphers = this.tlsConfig.ciphers;
    }

    if (this.tlsConfig.servername) {
      options.servername = this.tlsConfig.servername;
    }

    if (this.tlsConfig.alpnProtocols) {
      options.ALPNProtocols = this.tlsConfig.alpnProtocols;
    }

    if (this.tlsConfig.pfx) {
      options.pfx = Buffer.isBuffer(this.tlsConfig.pfx) 
        ? this.tlsConfig.pfx 
        : this._loadCertificate(this.tlsConfig.pfx);
    }

    if (this.tlsConfig.crl) {
      options.crl = this.tlsConfig.crlList;
    }

    // Session resumption
    if (this.tlsConfig.sessionResumption) {
      options.sessionIdContext = 'mqtts-session';
    }

    return options;
  }

  /**
   * Derives base MQTT options from stored configuration.
   */
  private _getBaseMqttOptions(): unknown {
    return {
      clientId: this.tlsConfig.clientId,
      username: this.tlsConfig.username,
      password: this.tlsConfig.password,
      clean: this.tlsConfig.clean,
      keepalive: this.tlsConfig.keepalive,
      connectTimeout: this.tlsConfig.connectTimeout,
      reconnectPeriod: this.tlsConfig.reconnectPeriod,
      will: this.tlsConfig.will,
      resubscribe: this.tlsConfig.resubscribe,
      protocolVersion: this.tlsConfig.protocolVersion,
    };
  }

  /**
   * Resolves certificate from path or inline content.
   */
  private _loadCertificate(cert: string | Buffer): Buffer {
    if (Buffer.isBuffer(cert)) {
      return cert;
    }

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

  /**
   * Resolves private key from path or inline content.
   */
  private _loadPrivateKey(key: string | Buffer): Buffer {
    if (Buffer.isBuffer(key)) {
      return key;
    }

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

  /**
   * Resolves CA certificates supporting arrays for chains.
   */
  private _loadCA(ca: string | string[] | Buffer): Buffer | Buffer[] {
    if (Buffer.isBuffer(ca)) {
      return ca;
    }

    if (Array.isArray(ca)) {
      return ca.map(c => this._loadCA(c) as Buffer);
    }

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

  /**
   * Get TLS connection information
   * 
   * @example
   * const tlsInfo = connector.getTlsInfo();
   * console.log(tlsInfo);
   */
  public getTlsInfo(): unknown {
    // In a real implementation, get from the underlying socket
    // if (this.client && this.client.stream) {
    //   const socket = this.client.stream;
    //   return {
    //     authorized: socket.authorized,
    //     authorizationError: socket.authorizationError,
    //     peerCertificate: socket.getPeerCertificate(),
    //     protocol: socket.getProtocol(),
    //     cipher: socket.getCipher(),
    //   };
    // }

    return {
      protocol: this.tlsConfig.protocol,
      secure: true,
      rejectUnauthorized: this.tlsConfig.rejectUnauthorized,
    };
  }

  /**
   * Verify the server certificate
   * 
   * @example
   * const isValid = connector.verifyServerCertificate();
   * console.log(isValid);
   */
  public verifyServerCertificate(): boolean {
    // In a real implementation:
    // if (this.client && this.client.stream) {
    //   return this.client.stream.authorized;
    // }
    return true;
  }

  /**
   * Renegotiate TLS session
   * 
   * @example
   * await connector.renegotiate();
   */
  public async renegotiate(): Promise<void> {
    // In a real implementation:
    // if (this.client && this.client.stream && this.client.stream.renegotiate) {
    //   return new Promise((resolve, reject) => {
    //     this.client.stream.renegotiate({}, (error) => {
    //       if (error) {
    //         reject(error);
    //       } else {
    //         resolve();
    //       }
    //     });
    //   });
    // }

    this.emit('renegotiated');
  }

  /**
   * Get cipher information
   * 
   * @example
   * const cipherInfo = connector.getCipherInfo();
   * console.log(cipherInfo);
   */
  public getCipherInfo(): unknown {
    // In a real implementation:
    // if (this.client && this.client.stream) {
    //   return this.client.stream.getCipher();
    // }

    return {
      name: this.tlsConfig.ciphers || 'TLS_AES_256_GCM_SHA384',
      version: this.tlsConfig.protocol || 'TLSv1.2',
    };
  }

  /**
   * Export session for resumption
   */
  public exportSession(): Buffer | null {
    // In a real implementation:
    // if (this.client && this.client.stream && this.tlsConfig.sessionResumption) {
    //   return this.client.stream.getSession();
    // }
    return null;
  }

  /**
   * Import session for resumption
   */
  public importSession(session: Buffer): void {
    // Store for next connection
    // This would be used in the connect options
    this.emit('sessionImported', { size: session.length });
  }

  /**
   * Validate certificate chain
   */
  public static validateCertificateChain(_cert: Buffer, _ca: Buffer): boolean {
    // In a real implementation, use crypto module
    // const crypto = require('crypto');
    // const x509 = new crypto.X509Certificate(cert);
    // return x509.verify(ca);
    return true;
  }

  /**
   * Check certificate expiration
   */
  public static checkCertificateExpiration(_cert: Buffer): {
    valid: boolean;
    validFrom: Date;
    validTo: Date;
    daysUntilExpiry: number;
  } {
    // In a real implementation:
    // const crypto = require('crypto');
    // const x509 = new crypto.X509Certificate(cert);
    // const validFrom = new Date(x509.validFrom);
    // const validTo = new Date(x509.validTo);
    // const now = new Date();
    // const daysUntilExpiry = Math.floor((validTo.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    
    return {
      valid: true,
      validFrom: new Date(),
      validTo: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000),
      daysUntilExpiry: 365,
    };
  }
}
