#!/usr/bin/env node
/**
 * DCMAAR Bridge Client Example
 *
 * Simple WebSocket client that sends telemetry events to the Agent's BridgeSourceAdapter.
 * This demonstrates how Desktop and Extension apps can send telemetry to the Agent.
 *
 * Usage:
 *   node examples/bridge-client.js [port]
 *
 * Default port: 9000
 */

const WebSocket = require('ws');

// Configuration
const PORT = process.argv[2] || 9000;
const HOST = 'localhost';
const WS_URL = `ws://${HOST}:${PORT}`;

// Event generation
let eventCount = 0;

function generateCpuMetric() {
  return {
    id: `cpu-metric-${++eventCount}`,
    type: 'metric.cpu',
    timestamp: Date.now(),
    payload: {
      value: Math.random() * 100,
      unit: 'percent',
      cores: 8,
    },
    metadata: {
      source: 'bridge-client-example',
      hostname: require('os').hostname(),
      version: '1.0.0',
    },
  };
}

function generateMemoryMetric() {
  return {
    id: `memory-metric-${++eventCount}`,
    type: 'metric.memory',
    timestamp: Date.now(),
    payload: {
      value: Math.floor(Math.random() * 16384),
      unit: 'MB',
      total: 16384,
    },
    metadata: {
      source: 'bridge-client-example',
      hostname: require('os').hostname(),
      version: '1.0.0',
    },
  };
}

function generateNetworkMetric() {
  return {
    id: `network-metric-${++eventCount}`,
    type: 'metric.network',
    timestamp: Date.now(),
    payload: {
      bytesReceived: Math.floor(Math.random() * 1000000),
      bytesSent: Math.floor(Math.random() * 500000),
      packetsReceived: Math.floor(Math.random() * 10000),
      packetsSent: Math.floor(Math.random() * 5000),
    },
    metadata: {
      source: 'bridge-client-example',
      interface: 'en0',
      version: '1.0.0',
    },
  };
}

// Main client
console.log(`\n╔═══════════════════════════════════════════════╗`);
console.log(`║  DCMAAR Bridge Client Example                ║`);
console.log(`╚═══════════════════════════════════════════════╝\n`);
console.log(`Connecting to Agent at ${WS_URL}...\n`);

const ws = new WebSocket(WS_URL);

ws.on('open', () => {
  console.log('✓ Connected to Agent bridge server\n');
});

ws.on('message', (data) => {
  const message = JSON.parse(data.toString());

  if (message.type === 'welcome') {
    console.log('✓ Received welcome message');
    console.log(`  Protocol: ${message.protocol}`);
    console.log(`  Timestamp: ${new Date(message.timestamp).toISOString()}\n`);

    // Start sending metrics
    console.log('Starting to send telemetry events...\n');
    sendMetrics();
  } else if (message.type === 'ack') {
    console.log(`✓ ACK received for message: ${message.messageId}`);
  } else if (message.type === 'error') {
    console.error(`✗ Error from server: ${message.error}`);
    if (message.originalMessageId) {
      console.error(`  Original message: ${message.originalMessageId}`);
    }
  } else {
    console.log(`? Unknown message type: ${message.type}`, message);
  }
});

ws.on('error', (error) => {
  console.error(`\n✗ WebSocket error: ${error.message}`);
  console.error('\nMake sure the Agent is running with BridgeSourceAdapter enabled.');
  console.error('Start the connector with:');
  console.error('  cd apps/agent && pnpm run dev\n');
  process.exit(1);
});

ws.on('close', () => {
  console.log('\n✓ Connection closed');
  console.log(`Total events sent: ${eventCount}\n`);
  process.exit(0);
});

// Send metrics periodically
let intervalId = null;
let metricsPerSecond = 2;
let maxEvents = 20;

function sendMetrics() {
  const generators = [generateCpuMetric, generateMemoryMetric, generateNetworkMetric];

  intervalId = setInterval(() => {
    if (eventCount >= maxEvents) {
      console.log(`\n✓ Sent ${maxEvents} events. Closing connection...\n`);
      clearInterval(intervalId);
      ws.close();
      return;
    }

    // Pick random metric generator
    const generator = generators[Math.floor(Math.random() * generators.length)];
    const event = generator();

    console.log(`→ Sending ${event.type} (${event.id})`);
    console.log(`  Value: ${JSON.stringify(event.payload).substring(0, 60)}...`);

    try {
      ws.send(JSON.stringify(event));
    } catch (error) {
      console.error(`✗ Failed to send event: ${error.message}`);
    }
  }, 1000 / metricsPerSecond);
}

// Handle Ctrl+C
process.on('SIGINT', () => {
  console.log('\n\nReceived SIGINT, closing connection...');
  if (intervalId) {
    clearInterval(intervalId);
  }
  ws.close();
});

// Print help
if (process.argv.includes('--help') || process.argv.includes('-h')) {
  console.log(`
DCMAAR Bridge Client Example

Usage:
  node examples/bridge-client.js [port]

Arguments:
  port    WebSocket port (default: 9000)

Examples:
  node examples/bridge-client.js          # Connect to localhost:9000
  node examples/bridge-client.js 9001     # Connect to localhost:9001

Environment:
  This client sends simulated telemetry events to the Agent's BridgeSourceAdapter.
  Make sure the Agent is running with a BridgeSource connector configured.

Configuration:
  Edit the constants at the top of this file to customize:
  - metricsPerSecond: Rate of metric generation
  - maxEvents: Total number of events to send before closing

Press Ctrl+C to stop.
  `);
  process.exit(0);
}
