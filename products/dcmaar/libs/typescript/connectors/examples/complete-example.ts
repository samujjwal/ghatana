/**
 * Complete Example: DCMAAR Connectors
 * 
 * This example demonstrates all major features of the connector library:
 * - Multiple connector types
 * - ConnectorManager for orchestration
 * - Resilience patterns (circuit breakers, retries, DLQ)
 * - Monitoring (metrics, health checks)
 * - Security features
 */

import {
  ConnectorManager,
  HttpConnector,
  WebSocketConnector,
  MqttConnector,
  NatsConnector,
  IpcConnector,
  CircuitBreaker,
  RetryPolicy,
  RetryPresets,
  DeadLetterQueue,
  MetricsCollector,
  HealthChecker,
  createMemoryHealthCheck,
  createEventLoopHealthCheck,
} from '@ghatana/dcmaar-connectors';

async function main() {
  console.log('🚀 Starting DCMAAR Connectors Example\n');

  // ========================================
  // 1. Initialize Metrics and Health Checks
  // ========================================
  console.log('📊 Setting up monitoring...');
  
  const metrics = new MetricsCollector();
  const healthChecker = new HealthChecker();

  // Register built-in health checks
  healthChecker.registerCheck(createMemoryHealthCheck());
  healthChecker.registerCheck(createEventLoopHealthCheck());

  // Start health checks
  healthChecker.start();

  // Listen to health events
  healthChecker.on('statusChanged', ({ name, result }) => {
    console.log(`  Health check '${name}': ${result.status}`);
  });

  // ========================================
  // 2. Set up Resilience Patterns
  // ========================================
  console.log('\n🛡️  Setting up resilience patterns...');

  // Circuit Breaker
  const circuitBreaker = new CircuitBreaker({
    failureThreshold: 5,
    timeout: 60000,
    successThreshold: 2,
  });

  circuitBreaker.on('stateChange', ({ from, to }) => {
    console.log(`  Circuit breaker: ${from} → ${to}`);
    metrics.incrementCounter('circuit_breaker_state_changes', 1, { from, to });
  });

  // Retry Policy
  const retryPolicy = new RetryPolicy(RetryPresets.standard);

  retryPolicy.on('retry', ({ attempt, delay }) => {
    console.log(`  Retry attempt ${attempt} after ${delay}ms`);
    metrics.incrementCounter('retries', 1, { attempt: String(attempt) });
  });

  // Dead Letter Queue
  const dlq = new DeadLetterQueue({
    maxSize: 1000,
    ttl: 86400000, // 24 hours
    autoCleanup: true,
  });

  dlq.on('entryAdded', ({ entry }) => {
    console.log(`  Event added to DLQ: ${entry.id}`);
    metrics.incrementCounter('dlq_entries', 1);
  });

  // ========================================
  // 3. Initialize Connector Manager
  // ========================================
  console.log('\n🔌 Initializing connector manager...');

  const manager = new ConnectorManager();

  // Listen to manager events
  manager.on('sourceAdded', ({ sourceId }) => {
    console.log(`  ✓ Source added: ${sourceId}`);
  });

  manager.on('sinkAdded', ({ sinkId }) => {
    console.log(`  ✓ Sink added: ${sinkId}`);
  });

  manager.on('eventProcessed', ({ sourceId, sinkId, status }) => {
    metrics.incrementCounter('events_processed', 1, { sourceId, sinkId, status });
  });

  manager.on('eventError', ({ sourceId, sinkId, error }) => {
    console.error(`  ✗ Event error: ${sourceId} → ${sinkId}:`, error.message);
    metrics.incrementCounter('events_failed', 1, { sourceId, sinkId });
  });

  // Initialize with sources and sinks
  await manager.initialize({
    sources: [
      {
        id: 'http-api',
        type: 'http',
        url: 'https://api.example.com/events',
        pollInterval: 10000,
        headers: {
          'Authorization': 'Bearer YOUR_TOKEN',
        },
        processors: [
          // Validate events
          async (event) => {
            if (!event.payload || typeof event.payload !== 'object') {
              throw new Error('Invalid event payload');
            }
            return event;
          },
          // Enrich with metadata
          async (event) => ({
            ...event,
            metadata: {
              ...event.metadata,
              processed: true,
              processedAt: new Date().toISOString(),
            },
          }),
        ],
        sinks: ['console-sink', 'storage-sink'],
      },
      {
        id: 'websocket-stream',
        type: 'websocket',
        url: 'wss://stream.example.com',
        autoReconnect: true,
        queueMessages: true,
        processors: [
          // Filter events
          async (event) => {
            if (event.type === 'heartbeat') {
              return null; // Skip heartbeat events
            }
            return event;
          },
        ],
        sinks: ['analytics-sink'],
      },
    ],
    sinks: [
      {
        id: 'console-sink',
        type: 'console',
        processors: [
          // Format for console output
          async (event) => ({
            ...event,
            payload: JSON.stringify(event.payload, null, 2),
          }),
        ],
      },
      {
        id: 'storage-sink',
        type: 'filesystem',
        path: './data/events',
        mode: 'write',
        format: 'json',
      },
      {
        id: 'analytics-sink',
        type: 'http',
        url: 'https://analytics.example.com/ingest',
        headers: {
          'Content-Type': 'application/json',
        },
      },
    ],
  });

  // ========================================
  // 4. Individual Connector Examples
  // ========================================
  console.log('\n🔗 Creating individual connectors...');

  // MQTT Connector
  const mqttConnector = new MqttConnector({
    id: 'mqtt-client',
    url: 'mqtt://broker.example.com',
    topics: ['sensors/#', 'alerts/+'],
  });

  mqttConnector.onEvent('message', (event) => {
    console.log(`  MQTT message on ${event.metadata.topic}:`, event.payload);
    metrics.incrementCounter('mqtt_messages', 1, { topic: event.metadata.topic });
  });

  // NATS Connector
  const natsConnector = new NatsConnector({
    id: 'nats-client',
    servers: ['nats://localhost:4222'],
  });

  // IPC Connector (for inter-process communication)
  const ipcConnector = new IpcConnector({
    id: 'ipc-client',
    channel: 'dcmaar-ipc',
    mode: 'client',
  });

  // ========================================
  // 5. Demonstrate Resilience
  // ========================================
  console.log('\n🔄 Testing resilience patterns...');

  // Wrap an unreliable operation with circuit breaker and retry
  async function unreliableOperation() {
    return await circuitBreaker.execute(async () => {
      return await retryPolicy.execute(async () => {
        // Simulate API call
        const success = Math.random() > 0.3;
        if (!success) {
          throw new Error('API call failed');
        }
        return { data: 'success' };
      });
    });
  }

  try {
    const result = await unreliableOperation();
    console.log('  ✓ Operation succeeded:', result);
  } catch (error) {
    console.error('  ✗ Operation failed:', error.message);
    
    // Add to DLQ
    dlq.add(
      {
        id: 'failed-op-1',
        type: 'api_call',
        timestamp: Date.now(),
        payload: { operation: 'unreliableOperation' },
      },
      error as Error,
      3
    );
  }

  // ========================================
  // 6. Monitor System Health
  // ========================================
  console.log('\n💚 Checking system health...');

  const health = healthChecker.getHealth();
  console.log(`  Overall status: ${health.status}`);
  
  for (const [name, result] of Object.entries(health.checks)) {
    console.log(`  - ${name}: ${result.status} (${result.message})`);
  }

  // ========================================
  // 7. Display Metrics
  // ========================================
  console.log('\n📈 Current metrics:');

  const snapshot = metrics.getSnapshot();
  const metricsTable = snapshot.reduce((acc, metric) => {
    acc[metric.name] = metric.value;
    return acc;
  }, {} as Record<string, number>);

  console.table(metricsTable);

  // ========================================
  // 8. Display Manager Status
  // ========================================
  console.log('\n📊 Connector Manager Status:');

  const status = manager.getStatus();
  console.log(`  Status: ${status.status}`);
  console.log(`  Sources: ${status.sources.length}`);
  console.log(`  Sinks: ${status.sinks.length}`);
  console.log(`  Routes: ${status.routes.length}`);

  // ========================================
  // 9. Cleanup
  // ========================================
  console.log('\n🧹 Cleaning up...');

  // Wait a bit to see some activity
  await new Promise(resolve => setTimeout(resolve, 5000));

  // Shutdown everything
  await manager.shutdown();
  healthChecker.stop();
  await dlq.destroy();

  console.log('\n✅ Example completed successfully!');
}

// Run the example
main().catch(console.error);
