/**
 * Observability Configuration for Ghatana Platform
 *
 * Unified monitoring, tracing, and metrics collection.
 * Integrates with OpenTelemetry for distributed tracing.
 *
 * @doc.type configuration
 * @doc.purpose Observability and monitoring setup
 * @doc.layer infrastructure
 */

// ============================================================================
// Tracing Configuration
// ============================================================================

export const TRACING_CONFIG = {
  serviceName: process.env.OTEL_SERVICE_NAME || 'ghatana-platform',
  serviceVersion: process.env.OTEL_SERVICE_VERSION || '1.0.0',
  environment: process.env.NODE_ENV || 'development',
  
  // OpenTelemetry Collector endpoint
  collector: {
    endpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4317',
    protocol: 'grpc' as const,
  },
  
  // Sampling configuration
  sampling: {
    type: 'parentbased_traceidratio',
    ratio: parseFloat(process.env.OTEL_SAMPLING_RATIO || '0.1'),
  },
  
  // Propagation formats
  propagation: {
    traceContext: true,
    baggage: true,
  },
  
  // Instrumentation libraries
  instrumentations: [
    '@opentelemetry/instrumentation-http',
    '@opentelemetry/instrumentation-express',
    '@opentelemetry/instrumentation-graphql',
    '@opentelemetry/instrumentation-pg',
    '@opentelemetry/instrumentation-redis',
  ],
};

// ============================================================================
// Metrics Configuration
// ============================================================================

export const METRICS_CONFIG = {
  // Export interval
  exportIntervalMillis: 60000,
  
  // Metric readers
  readers: [
    {
      type: 'prometheus',
      endpoint: '/metrics',
      port: 9090,
    },
    {
      type: 'otlp',
      endpoint: process.env.OTEL_METRICS_ENDPOINT || 'http://localhost:4318/v1/metrics',
    },
  ],
  
  // Custom metrics
  customMetrics: {
    // Business metrics
    activeUsers: 'ghatana.active_users',
    requestsPerSecond: 'ghatana.requests_per_second',
    errorRate: 'ghatana.error_rate',
    latencyP99: 'ghatana.latency_p99',
    
    // Infrastructure metrics
    cpuUsage: 'ghatana.cpu_usage',
    memoryUsage: 'ghatana.memory_usage',
    diskUsage: 'ghatana.disk_usage',
    networkIO: 'ghatana.network_io',
  },
};

// ============================================================================
// Logging Configuration
// ============================================================================

export const LOGGING_CONFIG = {
  // Log levels
  levels: {
    development: 'debug',
    staging: 'info',
    production: 'warn',
  },
  
  // Log format
  format: 'json',
  
  // Structured logging fields
  fields: {
    timestamp: true,
    level: true,
    service: true,
    traceId: true,
    spanId: true,
    userId: true,
    requestId: true,
  },
  
  // Outputs
  outputs: [
    {
      type: 'stdout',
    },
    {
      type: 'file',
      path: '/var/log/ghatana/app.log',
      rotation: {
        maxSize: '100m',
        maxFiles: 10,
      },
    },
  ],
  
  // Sensitive fields to redact
  redact: [
    'password',
    'token',
    'secret',
    'apiKey',
    'creditCard',
  ],
};

// ============================================================================
// Alerting Configuration
// ============================================================================

export const ALERTING_CONFIG = {
  // Alert rules
  rules: [
    {
      name: 'high-error-rate',
      condition: 'error_rate > 0.05',
      duration: '5m',
      severity: 'critical',
    },
    {
      name: 'high-latency',
      condition: 'latency_p99 > 1000',
      duration: '10m',
      severity: 'warning',
    },
    {
      name: 'low-availability',
      condition: 'availability < 0.99',
      duration: '5m',
      severity: 'critical',
    },
    {
      name: 'memory-pressure',
      condition: 'memory_usage > 0.85',
      duration: '15m',
      severity: 'warning',
    },
    {
      name: 'disk-space-low',
      condition: 'disk_usage > 0.9',
      duration: '1m',
      severity: 'critical',
    },
  ],
  
  // Notification channels
  channels: [
    {
      type: 'slack',
      webhook: process.env.SLACK_WEBHOOK_URL,
      severity: ['critical', 'warning'],
    },
    {
      type: 'pagerduty',
      apiKey: process.env.PAGERDUTY_API_KEY,
      severity: ['critical'],
    },
    {
      type: 'email',
      recipients: process.env.ALERT_EMAILS?.split(',') || [],
      severity: ['critical'],
    },
  ],
};

// ============================================================================
// SRE Configuration
// ============================================================================

export const SRE_CONFIG = {
  // SLOs (Service Level Objectives)
  slos: [
    {
      name: 'availability',
      target: 0.999, // 99.9% uptime
      window: '30d',
    },
    {
      name: 'latency',
      target: 0.99, // 99% of requests under 500ms
      threshold: 500,
      window: '30d',
    },
    {
      name: 'error-rate',
      target: 0.001, // 0.1% error rate
      window: '30d',
    },
  ],
  
  // Error budgets
  errorBudgets: {
    availability: 0.001, // 0.1% downtime budget
    latency: 0.01, // 1% of requests can exceed threshold
  },
  
  // Incident response
  incidentResponse: {
    autoPage: true,
    escalationMinutes: [5, 15, 30],
    postMortemRequired: true,
    postMortemDeadline: '24h',
  },
};

// ============================================================================
// Dashboard Configuration
// ============================================================================

export const DASHBOARD_CONFIG = {
  // Grafana dashboards
  grafana: {
    enabled: true,
    url: process.env.GRAFANA_URL || 'http://localhost:3000',
    apiKey: process.env.GRAFANA_API_KEY,
    
    // Default dashboards
    dashboards: [
      'ghatana-overview',
      'ghatana-services',
      'ghatana-infrastructure',
      'ghatana-business-metrics',
    ],
  },
  
  // Custom visualizations
  visualizations: {
    // Service dependency graph
    dependencyGraph: true,
    
    // Heat maps for latency distribution
    latencyHeatmap: true,
    
    // Error rate trends
    errorTrends: true,
    
    // Capacity planning charts
    capacityPlanning: true,
  },
};

// ============================================================================
// Initialization Helper
// ============================================================================

export function initializeObservability(): void {
  console.log('🔭 Initializing observability...');
  
  // Log configuration
  console.log(`  Service: ${TRACING_CONFIG.serviceName}`);
  console.log(`  Environment: ${TRACING_CONFIG.environment}`);
  console.log(`  Collector: ${TRACING_CONFIG.collector.endpoint}`);
  console.log(`  Sampling: ${TRACING_CONFIG.sampling.ratio * 100}%`);
  
  // Check required environment variables
  const required = ['NODE_ENV'];
  const missing = required.filter(v => !process.env[v]);
  
  if (missing.length > 0) {
    console.warn(`  ⚠️  Missing environment variables: ${missing.join(', ')}`);
  }
  
  console.log('✅ Observability initialized');
}

// Auto-initialize if run directly
if (import.meta.url === `file://${process.argv[1]}`) {
  initializeObservability();
}
