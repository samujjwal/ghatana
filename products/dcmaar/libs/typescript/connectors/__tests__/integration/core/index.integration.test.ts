/**
 * @fileoverview Unit tests for connector factory function
 *
 * Tests cover:
 * - Creation of all connector types
 * - Error handling for unsupported types
 * - Configuration passing
 * - Type safety
 */

import { createConnector } from '../../../src/index';
import { HttpConnector } from '../../../src/connectors/HttpConnector';
import { WebSocketConnector } from '../../../src/connectors/WebSocketConnector';
import { GrpcConnector } from '../../../src/connectors/GrpcConnector';
import { MqttConnector } from '../../../src/connectors/MqttConnector';
import { MqttsConnector } from '../../../src/connectors/MqttsConnector';
import { NatsConnector } from '../../../src/connectors/NatsConnector';
import { FileSystemConnector } from '../../../src/connectors/FileSystemConnector';
import { IpcConnector } from '../../../src/connectors/IpcConnector';
import { NativeConnector } from '../../../src/connectors/NativeConnector';
import { MtlsConnector } from '../../../src/connectors/MtlsConnector';

describe('createConnector Factory Function', () => {
  describe('HTTP Connector Creation', () => {
    it('should create HttpConnector for type "http"', () => {
      const config = {
        id: 'http-test',
        type: 'http' as const,
        endpoint: 'https://api.example.com',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(HttpConnector);
      expect(connector.type).toBe('http');
      expect(connector.id).toBe('http-test');
    });

    it('should pass configuration to HttpConnector', () => {
      const config = {
        id: 'http-test',
        type: 'http' as const,
        endpoint: 'https://api.example.com',
        timeout: 5000,
        headers: { 'X-Custom': 'value' },
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.endpoint).toBe('https://api.example.com');
      expect(retrievedConfig.timeout).toBe(5000);
      expect(retrievedConfig.headers).toEqual({ 'X-Custom': 'value' });
    });
  });

  describe('WebSocket Connector Creation', () => {
    it('should create WebSocketConnector for type "websocket"', () => {
      const config = {
        id: 'ws-test',
        type: 'websocket' as const,
        url: 'wss://example.com/socket',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(WebSocketConnector);
      expect(connector.type).toBe('websocket');
      expect(connector.id).toBe('ws-test');
    });

    it('should pass configuration to WebSocketConnector', () => {
      const config = {
        id: 'ws-test',
        type: 'websocket' as const,
        url: 'wss://example.com/socket',
        protocols: ['v1', 'v2'],
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.url).toBe('wss://example.com/socket');
      expect(retrievedConfig.protocols).toEqual(['v1', 'v2']);
    });
  });

  describe('gRPC Connector Creation', () => {
    it('should create GrpcConnector for type "grpc"', () => {
      const config = {
        id: 'grpc-test',
        type: 'grpc' as const,
        host: 'localhost',
        port: 50051,
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(GrpcConnector);
      expect(connector.type).toBe('grpc');
      expect(connector.id).toBe('grpc-test');
    });

    it('should pass configuration to GrpcConnector', () => {
      const config = {
        id: 'grpc-test',
        type: 'grpc' as const,
        host: 'localhost',
        port: 50051,
        protoPath: '/path/to/proto',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.host).toBe('localhost');
      expect(retrievedConfig.port).toBe(50051);
      expect(retrievedConfig.protoPath).toBe('/path/to/proto');
    });
  });

  describe('MQTT Connector Creation', () => {
    it('should create MqttConnector for type "mqtt"', () => {
      const config = {
        id: 'mqtt-test',
        type: 'mqtt' as const,
        brokerUrl: 'mqtt://localhost:1883',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(MqttConnector);
      expect(connector.type).toBe('mqtt');
      expect(connector.id).toBe('mqtt-test');
    });

    it('should pass configuration to MqttConnector', () => {
      const config = {
        id: 'mqtt-test',
        type: 'mqtt' as const,
        brokerUrl: 'mqtt://localhost:1883',
        clientId: 'client-123',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.brokerUrl).toBe('mqtt://localhost:1883');
      expect(retrievedConfig.clientId).toBe('client-123');
    });
  });

  describe('MQTTS Connector Creation', () => {
    it('should create MqttsConnector for type "mqtts"', () => {
      const config = {
        id: 'mqtts-test',
        type: 'mqtts' as const,
        brokerUrl: 'mqtts://localhost:8883',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(MqttsConnector);
      expect(connector.type).toBe('mqtts');
      expect(connector.id).toBe('mqtts-test');
    });

    it('should pass configuration to MqttsConnector', () => {
      const config = {
        id: 'mqtts-test',
        type: 'mqtts' as const,
        brokerUrl: 'mqtts://localhost:8883',
        ca: '/path/to/ca.pem',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.brokerUrl).toBe('mqtts://localhost:8883');
      expect(retrievedConfig.ca).toBe('/path/to/ca.pem');
    });
  });

  describe('NATS Connector Creation', () => {
    it('should create NatsConnector for type "nats"', () => {
      const config = {
        id: 'nats-test',
        type: 'nats' as const,
        servers: ['nats://localhost:4222'],
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(NatsConnector);
      expect(connector.type).toBe('nats');
      expect(connector.id).toBe('nats-test');
    });

    it('should pass configuration to NatsConnector', () => {
      const config = {
        id: 'nats-test',
        type: 'nats' as const,
        servers: ['nats://localhost:4222'],
        name: 'test-client',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.servers).toEqual(['nats://localhost:4222']);
      expect(retrievedConfig.name).toBe('test-client');
    });
  });

  describe('FileSystem Connector Creation', () => {
    it('should create FileSystemConnector for type "filesystem"', () => {
      const config = {
        id: 'fs-test',
        type: 'filesystem' as const,
        basePath: '/tmp/test',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(FileSystemConnector);
      expect(connector.type).toBe('filesystem');
      expect(connector.id).toBe('fs-test');
    });

    it('should pass configuration to FileSystemConnector', () => {
      const config = {
        id: 'fs-test',
        type: 'filesystem' as const,
        basePath: '/tmp/test',
        watchMode: true,
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.basePath).toBe('/tmp/test');
      expect(retrievedConfig.watchMode).toBe(true);
    });
  });

  describe('IPC Connector Creation', () => {
    it('should create IpcConnector for type "ipc"', () => {
      const config = {
        id: 'ipc-test',
        type: 'ipc' as const,
        socketPath: '/tmp/test.sock',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(IpcConnector);
      expect(connector.type).toBe('ipc');
      expect(connector.id).toBe('ipc-test');
    });

    it('should pass configuration to IpcConnector', () => {
      const config = {
        id: 'ipc-test',
        type: 'ipc' as const,
        socketPath: '/tmp/test.sock',
        mode: 'server' as const,
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.socketPath).toBe('/tmp/test.sock');
      expect(retrievedConfig.mode).toBe('server');
    });
  });

  describe('Native Connector Creation', () => {
    it('should create NativeConnector for type "native"', () => {
      const config = {
        id: 'native-test',
        type: 'native' as const,
        modulePath: '/path/to/module.node',
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(NativeConnector);
      expect(connector.type).toBe('native');
      expect(connector.id).toBe('native-test');
    });

    it('should pass configuration to NativeConnector', () => {
      const config = {
        id: 'native-test',
        type: 'native' as const,
        modulePath: '/path/to/module.node',
        initOptions: { key: 'value' },
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.modulePath).toBe('/path/to/module.node');
      expect(retrievedConfig.initOptions).toEqual({ key: 'value' });
    });
  });

  describe('mTLS Connector Creation', () => {
    it('should create MtlsConnector for type "mtls"', () => {
      const config = {
        id: 'mtls-test',
        type: 'mtls' as const,
        host: 'secure.example.com',
        port: 443,
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(MtlsConnector);
      expect(connector.type).toBe('mtls');
      expect(connector.id).toBe('mtls-test');
    });

    it('should pass configuration to MtlsConnector', () => {
      const config = {
        id: 'mtls-test',
        type: 'mtls' as const,
        host: 'secure.example.com',
        port: 443,
        cert: '/path/to/cert.pem',
        key: '/path/to/key.pem',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.host).toBe('secure.example.com');
      expect(retrievedConfig.port).toBe(443);
      expect(retrievedConfig.cert).toBe('/path/to/cert.pem');
      expect(retrievedConfig.key).toBe('/path/to/key.pem');
    });
  });

  describe('Error Handling', () => {
    it('should throw error for unsupported connector type', () => {
      const config = {
        id: 'unknown-test',
        type: 'unknown-type' as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: unknown-type');
    });

    it('should throw error for empty connector type', () => {
      const config = {
        id: 'empty-test',
        type: '' as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: ');
    });

    it('should throw error for null connector type', () => {
      const config = {
        id: 'null-test',
        type: null as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: null');
    });

    it('should throw error for undefined connector type', () => {
      const config = {
        id: 'undefined-test',
        type: undefined as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: undefined');
    });

    it('should throw error for numeric connector type', () => {
      const config = {
        id: 'numeric-test',
        type: 123 as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: 123');
    });
  });

  describe('Configuration Validation', () => {
    it('should create connector with minimal valid configuration', () => {
      const config = {
        id: 'min-test',
        type: 'http' as const,
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(HttpConnector);
      expect(connector.id).toBe('min-test');
    });

    it('should preserve all configuration properties', () => {
      const config = {
        id: 'full-test',
        type: 'http' as const,
        endpoint: 'https://api.example.com',
        maxRetries: 5,
        timeout: 10000,
        secure: false,
        headers: {
          'Authorization': 'Bearer token',
          'Content-Type': 'application/json',
        },
        debug: true,
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect(retrievedConfig.id).toBe('full-test');
      expect(retrievedConfig.type).toBe('http');
      expect(retrievedConfig.endpoint).toBe('https://api.example.com');
      expect(retrievedConfig.maxRetries).toBe(5);
      expect(retrievedConfig.timeout).toBe(10000);
      expect(retrievedConfig.secure).toBe(false);
      expect(retrievedConfig.headers).toEqual({
        'Authorization': 'Bearer token',
        'Content-Type': 'application/json',
      });
      expect(retrievedConfig.debug).toBe(true);
    });
  });

  describe('Multiple Connector Creation', () => {
    it('should create multiple independent connectors', () => {
      const config1 = {
        id: 'connector-1',
        type: 'http' as const,
      };

      const config2 = {
        id: 'connector-2',
        type: 'websocket' as const,
      };

      const connector1 = createConnector(config1);
      const connector2 = createConnector(config2);

      expect(connector1).toBeInstanceOf(HttpConnector);
      expect(connector2).toBeInstanceOf(WebSocketConnector);
      expect(connector1.id).toBe('connector-1');
      expect(connector2.id).toBe('connector-2');
    });

    it('should create same type connectors with different IDs', () => {
      const config1 = {
        id: 'http-1',
        type: 'http' as const,
      };

      const config2 = {
        id: 'http-2',
        type: 'http' as const,
      };

      const connector1 = createConnector(config1);
      const connector2 = createConnector(config2);

      expect(connector1.id).toBe('http-1');
      expect(connector2.id).toBe('http-2');
      expect(connector1).not.toBe(connector2);
    });
  });

  describe('Type Safety', () => {
    it('should return connector with correct type interface', () => {
      const config = {
        id: 'type-test',
        type: 'http' as const,
      };

      const connector = createConnector(config);

      // Verify connector implements IConnector interface
      expect(typeof connector.connect).toBe('function');
      expect(typeof connector.disconnect).toBe('function');
      expect(typeof connector.send).toBe('function');
      expect(typeof connector.onEvent).toBe('function');
      expect(typeof connector.offEvent).toBe('function');
      expect(typeof connector.getConfig).toBe('function');
      expect(typeof connector.updateConfig).toBe('function');
      expect(typeof connector.validateConfig).toBe('function');
    });

    it('should preserve custom configuration types', () => {
      const config = {
        id: 'custom-test',
        type: 'http' as const,
        endpoint: 'https://api.example.com',
        customField: 'custom-value',
      };

      const connector = createConnector(config);
      const retrievedConfig = connector.getConfig();

      expect((retrievedConfig as any).customField).toBe('custom-value');
    });
  });

  describe('Edge Cases', () => {
    it('should handle config with extra properties', () => {
      const config = {
        id: 'extra-test',
        type: 'http' as const,
        unknownProp1: 'value1',
        unknownProp2: 123,
        unknownProp3: { nested: 'object' },
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(HttpConnector);
      expect(connector.id).toBe('extra-test');
    });

    it('should handle config with null values', () => {
      const config = {
        id: 'null-test',
        type: 'http' as const,
        headers: null as any,
      };

      const connector = createConnector(config);

      expect(connector).toBeInstanceOf(HttpConnector);
    });

    it('should handle config with special characters in ID', () => {
      const config = {
        id: 'test-connector-123_$%^&*',
        type: 'http' as const,
      };

      const connector = createConnector(config);

      expect(connector.id).toBe('test-connector-123_$%^&*');
    });

    it('should handle config with very long ID', () => {
      const longId = 'a'.repeat(1000);
      const config = {
        id: longId,
        type: 'http' as const,
      };

      const connector = createConnector(config);

      expect(connector.id).toBe(longId);
    });
  });

  describe('Case Sensitivity', () => {
    it('should be case-sensitive for connector types', () => {
      const config = {
        id: 'case-test',
        type: 'HTTP' as any,
      };

      expect(() => createConnector(config)).toThrow('Unsupported connector type: HTTP');
    });

    it('should accept exact lowercase type names', () => {
      const types = [
        'http',
        'websocket',
        'grpc',
        'mqtt',
        'mqtts',
        'nats',
        'filesystem',
        'ipc',
        'native',
        'mtls',
      ];

      types.forEach(type => {
        const config = {
          id: `${type}-test`,
          type: type as any,
        };

        expect(() => createConnector(config)).not.toThrow();
      });
    });
  });
});
