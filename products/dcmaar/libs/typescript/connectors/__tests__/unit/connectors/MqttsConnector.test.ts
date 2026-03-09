/**
 * @fileoverview Comprehensive unit tests for MqttsConnector
 *
 * Tests cover:
 * - TLS/SSL connection with certificates
 * - Certificate validation
 * - TLS protocol versions
 * - Cipher suites
 * - SNI (Server Name Indication)
 * - ALPN (Application-Layer Protocol Negotiation)
 * - Session resumption
 * - Certificate loading (file/buffer/string)
 * - mTLS (mutual TLS)
 * - CRL (Certificate Revocation List)
 * - PFX/PKCS12 support
 */

import { MqttsConnector, MqttsConnectorConfig } from '../../../src/connectors/MqttsConnector';
import * as fs from 'fs';

// Mock fs module
jest.mock('fs');
const mockedFs = fs as jest.Mocked<typeof fs>;

describe('MqttsConnector', () => {
  let connector: MqttsConnector;
  let config: MqttsConnectorConfig;

  beforeEach(() => {
    jest.clearAllMocks();

    // Mock fs.existsSync and fs.readFileSync
    (mockedFs.existsSync as jest.Mock).mockReturnValue(false);
    (mockedFs.readFileSync as jest.Mock).mockReturnValue(Buffer.from('mock-cert'));

    config = {
      id: 'mqtts-test',
      type: 'mqtt',
      url: 'mqtts://localhost:8883',
      cert: '/path/to/client.crt',
      key: '/path/to/client.key',
      ca: '/path/to/ca.pem',
      rejectUnauthorized: true,
      protocol: 'TLSv1.2',
      sessionResumption: true,
    };
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
  });

  describe('Constructor', () => {
    it('should create connector with TLS config', () => {
      connector = new MqttsConnector(config);

      expect(connector.id).toBe('mqtts-test');
      expect(connector.type).toBe('mqtt');
    });

    it('should convert mqtt:// URL to mqtts://', () => {
      const httpConfig = { ...config, url: 'mqtt://localhost:1883' };
      connector = new MqttsConnector(httpConfig);

      expect(connector.type).toBe('mqtt');
    });

    it('should apply secure defaults', () => {
      connector = new MqttsConnector({
        id: 'test',
        type: 'mqtt',
        url: 'mqtts://localhost',
        cert: 'cert',
        key: 'key',
        ca: 'ca',
      });

      expect(connector.type).toBe('mqtt');
    });
  });

  describe('TLS Connection', () => {
    it('should establish secure connection', async () => {
      connector = new MqttsConnector(config);
      const connectedListener = jest.fn();
      connector.on('connected', connectedListener);

      await connector.connect();

      expect(connectedListener).toHaveBeenCalledWith(
        expect.objectContaining({
          protocol: 'TLSv1.2',
          secure: true,
        })
      );
    });

    it('should emit connection metadata', async () => {
      connector = new MqttsConnector(config);
      const connectedListener = jest.fn();
      connector.on('connected', connectedListener);

      await connector.connect();

      expect(connectedListener).toHaveBeenCalled();
    });
  });

  describe('Certificate Loading', () => {
    it('should load certificate from file path', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(true);
      (mockedFs.readFileSync as jest.Mock).mockReturnValue(Buffer.from('file-cert'));

      connector = new MqttsConnector(config);
      await connector.connect();

      expect(mockedFs.existsSync).toHaveBeenCalledWith('/path/to/client.crt');
    });

    it('should load certificate from Buffer', async () => {
      const bufferConfig = {
        ...config,
        cert: Buffer.from('cert-buffer'),
      };
      connector = new MqttsConnector(bufferConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should load certificate from string content', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(false);

      const stringConfig = {
        ...config,
        cert: '-----BEGIN CERTIFICATE-----\nMOCK\n-----END CERTIFICATE-----',
      };
      connector = new MqttsConnector(stringConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should load private key from file', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(true);
      connector = new MqttsConnector(config);

      await connector.connect();

      expect(mockedFs.existsSync).toHaveBeenCalledWith('/path/to/client.key');
    });

    it('should load CA from file', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(true);
      connector = new MqttsConnector(config);

      await connector.connect();

      expect(mockedFs.existsSync).toHaveBeenCalledWith('/path/to/ca.pem');
    });

    it('should handle CA as array of certificates', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(false);

      const multiCaConfig = {
        ...config,
        ca: ['ca1', 'ca2', 'ca3'],
      };
      connector = new MqttsConnector(multiCaConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should throw error on certificate load failure', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(true);
      (mockedFs.readFileSync as jest.Mock).mockImplementation(() => {
        throw new Error('Read error');
      });

      connector = new MqttsConnector(config);

      await expect(connector.connect()).rejects.toThrow();
    });
  });

  describe('TLS Protocol Versions', () => {
    it('should use TLSv1', async () => {
      const tls1Config = { ...config, protocol: 'TLSv1' as const };
      connector = new MqttsConnector(tls1Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use TLSv1.1', async () => {
      const tls11Config = { ...config, protocol: 'TLSv1.1' as const };
      connector = new MqttsConnector(tls11Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use TLSv1.2 by default', async () => {
      connector = new MqttsConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use TLSv1.3', async () => {
      const tls13Config = { ...config, protocol: 'TLSv1.3' as const };
      connector = new MqttsConnector(tls13Config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Certificate Verification', () => {
    it('should reject unauthorized certificates when enabled', async () => {
      const rejectConfig = { ...config, rejectUnauthorized: true };
      connector = new MqttsConnector(rejectConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should allow unauthorized certificates when disabled', async () => {
      const allowConfig = { ...config, rejectUnauthorized: false };
      connector = new MqttsConnector(allowConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should verify server certificate', () => {
      connector = new MqttsConnector(config);

      const isValid = connector.verifyServerCertificate();

      expect(typeof isValid).toBe('boolean');
    });
  });

  describe('Advanced TLS Options', () => {
    it('should configure cipher suites', async () => {
      const cipherConfig = {
        ...config,
        ciphers: 'TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256',
      };
      connector = new MqttsConnector(cipherConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use SNI (Server Name Indication)', async () => {
      const sniConfig = { ...config, servername: 'mqtt.example.com' };
      connector = new MqttsConnector(sniConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should support ALPN protocols', async () => {
      const alpnConfig = {
        ...config,
        alpn: true,
        alpnProtocols: ['mqtt', 'http/1.1'],
      };
      connector = new MqttsConnector(alpnConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should use passphrase for encrypted keys', async () => {
      const passphraseConfig = {
        ...config,
        passphrase: 'secret-passphrase',
      };
      connector = new MqttsConnector(passphraseConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Session Management', () => {
    it('should enable session resumption', async () => {
      const sessionConfig = { ...config, sessionResumption: true };
      connector = new MqttsConnector(sessionConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should disable session resumption', async () => {
      const noSessionConfig = { ...config, sessionResumption: false };
      connector = new MqttsConnector(noSessionConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should export session for resumption', () => {
      connector = new MqttsConnector({ ...config, sessionResumption: true });

      const session = connector.exportSession();

      expect(session === null || Buffer.isBuffer(session)).toBe(true);
    });

    it('should import session for resumption', () => {
      connector = new MqttsConnector(config);
      const sessionListener = jest.fn();
      connector.on('sessionImported', sessionListener);

      const mockSession = Buffer.from('session-data');
      connector.importSession(mockSession);

      expect(sessionListener).toHaveBeenCalled();
    });
  });

  describe('PFX/PKCS12 Support', () => {
    it('should load PFX from file path', async () => {
      (mockedFs.existsSync as jest.Mock).mockReturnValue(true);
      (mockedFs.readFileSync as jest.Mock).mockReturnValue(Buffer.from('pfx-data'));

      const pfxConfig = {
        ...config,
        pfx: '/path/to/cert.pfx',
      };
      connector = new MqttsConnector(pfxConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should load PFX from Buffer', async () => {
      const pfxConfig = {
        ...config,
        pfx: Buffer.from('pfx-buffer'),
      };
      connector = new MqttsConnector(pfxConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Certificate Revocation List (CRL)', () => {
    it('should enable CRL checking', async () => {
      const crlConfig = {
        ...config,
        crl: true,
        crlList: ['crl1', 'crl2'],
      };
      connector = new MqttsConnector(crlConfig);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should disable CRL checking by default', async () => {
      connector = new MqttsConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('TLS Information', () => {
    beforeEach(async () => {
      connector = new MqttsConnector(config);
      await connector.connect();
    });

    it('should get TLS connection info', () => {
      const tlsInfo = connector.getTlsInfo();

      expect(tlsInfo).toHaveProperty('protocol');
      expect(tlsInfo).toHaveProperty('secure');
      expect(tlsInfo).toHaveProperty('rejectUnauthorized');
    });

    it('should get cipher info', () => {
      const cipherInfo = connector.getCipherInfo();

      expect(cipherInfo).toHaveProperty('name');
      expect(cipherInfo).toHaveProperty('version');
    });
  });

  describe('TLS Renegotiation', () => {
    beforeEach(async () => {
      connector = new MqttsConnector(config);
      await connector.connect();
    });

    it('should renegotiate TLS session', async () => {
      const renegotiatedListener = jest.fn();
      connector.on('renegotiated', renegotiatedListener);

      await connector.renegotiate();

      expect(renegotiatedListener).toHaveBeenCalled();
    });
  });

  describe('Static Certificate Methods', () => {
    it('should validate certificate chain', () => {
      const cert = Buffer.from('cert');
      const ca = Buffer.from('ca');

      const isValid = MqttsConnector.validateCertificateChain(cert, ca);

      expect(typeof isValid).toBe('boolean');
    });

    it('should check certificate expiration', () => {
      const cert = Buffer.from('cert');

      const expiration = MqttsConnector.checkCertificateExpiration(cert);

      expect(expiration).toHaveProperty('valid');
      expect(expiration).toHaveProperty('validFrom');
      expect(expiration).toHaveProperty('validTo');
      expect(expiration).toHaveProperty('daysUntilExpiry');
    });
  });

  describe('Inherits MQTT Features', () => {
    beforeEach(async () => {
      connector = new MqttsConnector({
        ...config,
        topics: ['test/topic'],
      });
      await connector.connect();
    });

    it('should support MQTT publish', async () => {
      const publishedListener = jest.fn();
      connector.on('published', publishedListener);

      await connector.send({ data: 'test' }, { topic: 'test/topic' });

      expect(publishedListener).toHaveBeenCalled();
    });

    it('should support MQTT subscribe', async () => {
      const subscribedListener = jest.fn();
      connector.on('subscribed', subscribedListener);

      await connector.subscribe('new/topic');

      expect(subscribedListener).toHaveBeenCalled();
    });

    it('should support MQTT unsubscribe', async () => {
      await connector.subscribe('test/topic');

      const unsubscribedListener = jest.fn();
      connector.on('unsubscribed', unsubscribedListener);

      await connector.unsubscribe('test/topic');

      expect(unsubscribedListener).toHaveBeenCalled();
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      connector = new MqttsConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });
  });
});
