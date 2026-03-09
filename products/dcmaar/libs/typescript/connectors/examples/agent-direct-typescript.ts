/**
 * Agent - Direct TypeScript Usage
 * 
 * This example shows using the connectors directly in TypeScript
 * without the Rust native layer. Useful for rapid development
 * or when native performance isn't required.
 */

import {
  ConnectorManager,
  HttpConnector,
  WebSocketConnector,
  MqttConnector,
  MqttsConnector,
  NatsConnector,
  FileSystemConnector,
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

class TypeScriptAgent {
  private connectorManager: ConnectorManager;
  private metrics: MetricsCollector;
  private healthChecker: HealthChecker;
  private dlq: DeadLetterQueue;
  private circuitBreaker: CircuitBreaker;
  private retryPolicy: RetryPolicy;

  constructor() {
    this.connectorManager = new ConnectorManager();
    this.metrics = new MetricsCollector();
    this.healthChecker = new HealthChecker();
    this.dlq = new DeadLetterQueue({
      maxSize: 10000,
      ttl: 86400000, // 24 hours
      autoCleanup: true,
    });
    this.circuitBreaker = new CircuitBreaker({
      failureThreshold: 5,
      timeout: 60000,
    });
    this.retryPolicy = new RetryPolicy(RetryPresets.aggressive);
  }

  async initialize() {
    console.log('🚀 Initializing TypeScript Agent...');

    // Set up health checks
    this.healthChecker.registerCheck(createMemoryHealthCheck());
    this.healthChecker.registerCheck(createEventLoopHealthCheck());
    
    // Custom health check for connectors
    this.healthChecker.registerCheck({
      name: 'connectors',
      async check() {
        const status = this.connectorManager.getStatus();
        const allHealthy = status.sources.every(s => s.status === 'connected') &&
                          status.sinks.every(s => s.status === 'connected');
        
        return {
          status: allHealthy ? 'healthy' : 'degraded',
          message: `${status.sources.length} sources, ${status.sinks.length} sinks`,
          details: status,
          timestamp: Date.now(),
          duration: 0,
        };
      }.bind(this),
      interval: 30000,
      critical: true,
    });

    this.healthChecker.start();

    // Initialize connector manager
    await this.connectorManager.initialize({
      sources: [
        // HTTP API polling
        {
          id: 'api-poller',
          type: 'http',
          url: 'https://api.example.com/events',
          pollInterval: 5000,
          headers: {
            'Authorization': 'Bearer YOUR_TOKEN',
          },
          processors: [
            // Validate incoming data
            async (event) => {
              if (!event.payload || typeof event.payload !== 'object') {
                throw new Error('Invalid payload');
              }
              return event;
            },
            // Enrich with metadata
            async (event) => ({
              ...event,
              metadata: {
                ...event.metadata,
                receivedAt: new Date().toISOString(),
                agentId: 'typescript-agent-1',
              },
            }),
            // Track metrics
            async (event) => {
              this.metrics.incrementCounter('events_received', 1, {
                source: 'api-poller',
                type: event.type,
              });
              return event;
            },
          ],
          sinks: ['processor-sink', 'storage-sink'],
        },

        // WebSocket real-time stream
        {
          id: 'websocket-stream',
          type: 'websocket',
          url: 'wss://stream.example.com',
          autoReconnect: true,
          queueMessages: true,
          processors: [
            // Filter heartbeats
            async (event) => {
              if (event.type === 'heartbeat') {
                return null; // Skip
              }
              return event;
            },
            // Rate limiting check
            async (event) => {
              const rate = this.metrics.getCounter('ws_events_per_second');
              if (rate > 1000) {
                console.warn('Rate limit exceeded, queuing event');
                // Could implement backpressure here
              }
              return event;
            },
          ],
          sinks: ['processor-sink'],
        },

        // MQTT sensor data
        {
          id: 'mqtt-sensors',
          type: 'mqtts',
          url: 'mqtts://broker.example.com:8883',
          cert: '/path/to/client-cert.pem',
          key: '/path/to/client-key.pem',
          ca: '/path/to/ca-cert.pem',
          topics: ['sensors/+/temperature', 'sensors/+/humidity'],
          processors: [
            // Parse sensor data
            async (event) => {
              const topic = event.metadata?.topic || '';
              const sensorId = topic.split('/')[1];
              
              return {
                ...event,
                payload: {
                  ...event.payload,
                  sensorId,
                  parsedAt: Date.now(),
                },
              };
            },
          ],
          sinks: ['analytics-sink', 'storage-sink'],
        },

        // NATS high-throughput messaging
        {
          id: 'nats-events',
          type: 'nats',
          servers: ['nats://localhost:4222'],
          processors: [
            // Batch events for efficiency
            async (event) => {
              // Could implement batching logic here
              return event;
            },
          ],
          sinks: ['processor-sink'],
        },

        // File system watcher
        {
          id: 'file-watcher',
          type: 'filesystem',
          path: '/data/incoming',
          mode: 'watch',
          pattern: '*.json',
          format: 'json',
          processors: [
            // Validate file content
            async (event) => {
              if (!Array.isArray(event.payload)) {
                throw new Error('Expected array in file');
              }
              return event;
            },
          ],
          sinks: ['processor-sink'],
        },

        // IPC from other processes
        {
          id: 'ipc-receiver',
          type: 'ipc',
          channel: 'dcmaar-agent',
          mode: 'server',
          sinks: ['processor-sink'],
        },
      ],

      sinks: [
        // Main processing sink
        {
          id: 'processor-sink',
          type: 'custom',
          processors: [
            // Process with circuit breaker and retry
            async (event) => {
              try {
                return await this.circuitBreaker.execute(async () => {
                  return await this.retryPolicy.execute(async () => {
                    return await this.processEvent(event);
                  });
                });
              } catch (error) {
                // Add to DLQ if processing fails
                this.dlq.add(event, error as Error, 3);
                this.metrics.incrementCounter('events_failed', 1);
                throw error;
              }
            },
          ],
        },

        // Storage sink
        {
          id: 'storage-sink',
          type: 'filesystem',
          path: '/data/processed',
          mode: 'write',
          format: 'json',
          processors: [
            // Add timestamp to filename
            async (event) => ({
              ...event,
              metadata: {
                ...event.metadata,
                filename: `${event.id}-${Date.now()}.json`,
              },
            }),
          ],
        },

        // Analytics sink
        {
          id: 'analytics-sink',
          type: 'http',
          url: 'https://analytics.example.com/ingest',
          headers: {
            'Content-Type': 'application/json',
            'X-API-Key': 'YOUR_API_KEY',
          },
          processors: [
            // Format for analytics API
            async (event) => ({
              ...event,
              payload: {
                eventId: event.id,
                timestamp: event.timestamp,
                data: event.payload,
              },
            }),
          ],
        },

        // Alert sink for critical events
        {
          id: 'alert-sink',
          type: 'http',
          url: 'https://alerts.example.com/webhook',
          processors: [
            // Only send critical events
            async (event) => {
              if (event.metadata?.severity !== 'critical') {
                return null; // Skip non-critical
              }
              return event;
            },
          ],
        },
      ],
    });

    // Set up event listeners
    this.setupEventListeners();

    console.log('✅ TypeScript Agent initialized');
  }

  private async processEvent(event: any): Promise<any> {
    const startTime = Date.now();

    // Simulate processing
    // In a real agent, this would do:
    // - Data validation
    // - Transformation
    // - Enrichment
    // - ML/AI inference
    // - Business logic

    await new Promise(resolve => setTimeout(resolve, 10));

    const duration = Date.now() - startTime;
    this.metrics.observeHistogram('processing_duration_ms', duration);
    this.metrics.incrementCounter('events_processed', 1);

    return {
      ...event,
      payload: {
        ...event.payload,
        processed: true,
        processingTimeMs: duration,
      },
    };
  }

  private setupEventListeners() {
    // Connector manager events
    this.connectorManager.on('eventProcessed', ({ sourceId, sinkId, event, status }) => {
      this.metrics.incrementCounter('events_routed', 1, { sourceId, sinkId, status });
    });

    this.connectorManager.on('eventError', ({ sourceId, sinkId, error }) => {
      console.error(`Event error: ${sourceId} → ${sinkId}:`, error.message);
      this.metrics.incrementCounter('routing_errors', 1, { sourceId, sinkId });
    });

    // Circuit breaker events
    this.circuitBreaker.on('stateChange', ({ from, to }) => {
      console.log(`Circuit breaker: ${from} → ${to}`);
      this.metrics.incrementCounter('circuit_breaker_transitions', 1, { from, to });
    });

    // DLQ events
    this.dlq.on('entryAdded', ({ entry }) => {
      console.warn(`Event added to DLQ: ${entry.id}`);
    });

    // Health check events
    this.healthChecker.on('statusChanged', ({ name, result }) => {
      if (result.status !== 'healthy') {
        console.warn(`Health check '${name}' is ${result.status}: ${result.message}`);
      }
    });
  }

  async getMetrics() {
    return {
      snapshot: this.metrics.getSnapshot(),
      health: this.healthChecker.getHealth(),
      connectors: this.connectorManager.getStatus(),
      dlq: this.dlq.getStats(),
      circuitBreaker: this.circuitBreaker.getStats(),
    };
  }

  async retryFailedEvents() {
    console.log('🔄 Retrying failed events from DLQ...');
    
    const { succeeded, failed } = await this.dlq.retryAll(async (event) => {
      return await this.processEvent(event);
    });

    console.log(`✅ Retry complete: ${succeeded} succeeded, ${failed} failed`);
    return { succeeded, failed };
  }

  async shutdown() {
    console.log('🛑 Shutting down TypeScript Agent...');

    await this.connectorManager.shutdown();
    this.healthChecker.stop();
    await this.dlq.destroy();

    console.log('✅ Agent shut down successfully');
  }
}

// Example usage
async function main() {
  const agent = new TypeScriptAgent();

  try {
    await agent.initialize();

    // Run for a while
    console.log('\n📊 Agent running... Press Ctrl+C to stop\n');

    // Periodic metrics reporting
    const metricsInterval = setInterval(async () => {
      const metrics = await agent.getMetrics();
      console.log('\n📈 Metrics Report:');
      console.log('  Events processed:', metrics.snapshot.find(m => m.name === 'events_processed')?.value || 0);
      console.log('  Events failed:', metrics.snapshot.find(m => m.name === 'events_failed')?.value || 0);
      console.log('  DLQ size:', metrics.dlq.total);
      console.log('  Health:', metrics.health.status);
      console.log('  Circuit breaker:', metrics.circuitBreaker.state);
    }, 30000);

    // Periodic DLQ retry
    const retryInterval = setInterval(async () => {
      if (agent['dlq'].size() > 0) {
        await agent.retryFailedEvents();
      }
    }, 60000);

    // Handle graceful shutdown
    process.on('SIGINT', async () => {
      clearInterval(metricsInterval);
      clearInterval(retryInterval);
      await agent.shutdown();
      process.exit(0);
    });

    // Keep running
    await new Promise(() => {});

  } catch (error) {
    console.error('❌ Agent error:', error);
    await agent.shutdown();
    process.exit(1);
  }
}

if (require.main === module) {
  main().catch(console.error);
}

export { TypeScriptAgent };
