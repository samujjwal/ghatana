/**
 * Integration tests for BridgeSourceAdapter
 *
 * Tests the WebSocket server functionality for receiving
 * telemetry from Desktop/Extension apps.
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { BridgeSourceAdapter } from '../adapters/sources/BridgeSourceAdapter';
import WebSocket from 'ws';

describe('BridgeSourceAdapter', () => {
  let adapter: BridgeSourceAdapter;
  let connector: any;
  const TEST_PORT = 9999;

  beforeAll(async () => {
    // Create adapter and connector
    adapter = new BridgeSourceAdapter();
    connector = await adapter.create({
      id: 'test-bridge-source',
      type: 'bridge',
      port: TEST_PORT,
      metadata: {
        protocol: 'dcmaar-bridge-v1',
      },
    });

    // Connect (starts WebSocket server)
    await connector.connect();

    // Wait for server to be ready
    await new Promise(resolve => setTimeout(resolve, 100));
  });

  afterAll(async () => {
    // Disconnect and cleanup
    if (connector) {
      await connector.disconnect();
    }
  });

  it('should create BridgeSource connector', () => {
    expect(adapter).toBeDefined();
    expect(adapter.type).toBe('bridge');
  });

  it('should start WebSocket server on specified port', async () => {
    const stats = connector.getStats();
    expect(stats.isListening).toBe(true);
    expect(stats.clients).toBe(0);
    expect(stats.messageCount).toBe(0);
  });

  it('should accept WebSocket client connections', (done) => {
    const client = new WebSocket(`ws://localhost:${TEST_PORT}`);

    client.on('open', () => {
      // Server should send welcome message
      client.on('message', (data) => {
        const message = JSON.parse(data.toString());
        expect(message.type).toBe('welcome');
        expect(message.protocol).toBe('dcmaar-bridge-v1');

        client.close();
      });
    });

    client.on('close', () => {
      done();
    });
  });

  it('should receive and process bridge messages', (done) => {
    const client = new WebSocket(`ws://localhost:${TEST_PORT}`);

    client.on('open', () => {
      // Skip welcome message
      client.once('message', () => {
        // Send test event
        const testEvent = {
          id: 'test-event-1',
          type: 'metric.cpu',
          timestamp: Date.now(),
          payload: {
            value: 85.5,
            unit: 'percent',
          },
          metadata: {
            source: 'desktop-app',
          },
        };

        client.send(JSON.stringify(testEvent));

        // Wait for acknowledgment
        client.once('message', (data) => {
          const ack = JSON.parse(data.toString());
          expect(ack.type).toBe('ack');
          expect(ack.messageId).toBe('test-event-1');

          // Check stats
          const stats = connector.getStats();
          expect(stats.messageCount).toBe(1);

          client.close();
        });
      });
    });

    client.on('close', () => {
      done();
    });
  });

  it('should reject invalid messages', (done) => {
    const client = new WebSocket(`ws://localhost:${TEST_PORT}`);

    client.on('open', () => {
      // Skip welcome message
      client.once('message', () => {
        // Send invalid event (missing required fields)
        const invalidEvent = {
          id: 'invalid-event',
          // Missing 'type' and 'payload'
        };

        client.send(JSON.stringify(invalidEvent));

        // Wait for error response
        client.once('message', (data) => {
          const error = JSON.parse(data.toString());
          expect(error.type).toBe('error');
          expect(error.error).toBe('Invalid message format');

          client.close();
        });
      });
    });

    client.on('close', () => {
      done();
    });
  });

  it('should handle multiple concurrent clients', async () => {
    const clients: WebSocket[] = [];
    const clientCount = 3;

    // Connect multiple clients
    for (let i = 0; i < clientCount; i++) {
      const client = new WebSocket(`ws://localhost:${TEST_PORT}`);
      clients.push(client);

      await new Promise<void>((resolve) => {
        client.on('open', () => resolve());
      });
    }

    // Wait a bit for all connections
    await new Promise(resolve => setTimeout(resolve, 100));

    // Check stats
    const stats = connector.getStats();
    expect(stats.clients).toBe(clientCount);

    // Close all clients
    for (const client of clients) {
      client.close();
    }

    // Wait for cleanup
    await new Promise(resolve => setTimeout(resolve, 100));
  });

  it('should track message count across multiple messages', (done) => {
    const client = new WebSocket(`ws://localhost:${TEST_PORT}`);
    let messagesReceived = 0;
    const messagesToSend = 5;

    client.on('open', () => {
      // Skip welcome message
      client.once('message', () => {
        // Send multiple events
        for (let i = 0; i < messagesToSend; i++) {
          const event = {
            id: `test-event-${i}`,
            type: 'metric.test',
            timestamp: Date.now(),
            payload: { value: i },
          };
          client.send(JSON.stringify(event));
        }

        // Listen for acknowledgments
        client.on('message', () => {
          messagesReceived++;

          if (messagesReceived === messagesToSend) {
            // All acks received
            const initialCount = connector.getStats().messageCount;

            // Verify count increased
            expect(connector.getStats().messageCount).toBeGreaterThanOrEqual(messagesToSend);

            client.close();
          }
        });
      });
    });

    client.on('close', () => {
      done();
    });
  });

  it('should cleanup properly on disconnect', async () => {
    // Create separate connector for this test
    const testAdapter = new BridgeSourceAdapter();
    const testConnector = await testAdapter.create({
      id: 'test-cleanup',
      type: 'bridge',
      port: TEST_PORT + 1,
    });

    await testConnector.connect();

    // Connect a client
    const client = new WebSocket(`ws://localhost:${TEST_PORT + 1}`);
    await new Promise<void>((resolve) => {
      client.on('open', () => resolve());
    });

    // Disconnect connector
    await testConnector.disconnect();

    // Server should be closed
    const stats = testConnector.getStats();
    expect(stats.isListening).toBe(false);
    expect(stats.clients).toBe(0);
  });
});
