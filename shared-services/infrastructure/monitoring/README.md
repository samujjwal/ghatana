# Ghatana Observability Stack

Complete monitoring, metrics, logging, and tracing infrastructure for the Ghatana platform.

## Stack Components

| Component | Port | Purpose | Access URL |
|:----------|:-----|:--------|:-----------|
| **Grafana** | 3001 | Visualization & Dashboards | http://localhost:3001 |
| **Prometheus** | 9090 | Metrics Collection & Storage | http://localhost:9090 |
| **Jaeger** | 16686 | Distributed Tracing | http://localhost:16686 |
| **Loki** | 3100 | Log Aggregation | http://localhost:3100 |
| **AlertManager** | 9093 | Alert Management | http://localhost:9093 |
| **Node Exporter** | 9100 | Host Metrics | http://localhost:9100/metrics |

## Quick Start

### 1. Start the Monitoring Stack

```bash
cd monitoring
docker-compose up -d
```

### 2. Verify Services

```bash
# Check all containers are running
docker-compose ps

# Check Prometheus targets
open http://localhost:9090/targets

# Open Grafana
open http://localhost:3001
# Login: admin / admin
```

### 3. View Dashboards

Navigate to Grafana → Dashboards → Ghatana folder:
- **Ghatana Services Overview** - Main dashboard for all services
- Import additional dashboards from [grafana.com/dashboards](https://grafana.com/grafana/dashboards/)

## Configuration

### Prometheus Scrape Targets

Configured in [prometheus/prometheus.yml](prometheus/prometheus.yml):

**YAPPC Services (Java/ActiveJ):**
- yappc-domain: `localhost:8080/metrics`
- yappc-ai-requirements: `localhost:8081/metrics`
- yappc-lifecycle: `localhost:8082/metrics`
- yappc-canvas-ai: `localhost:8083/metrics`

**Backend API (Node.js/Fastify):**
- backend-api: `localhost:8000/metrics`

**AEP Services:**
- aep-unified-launcher: `localhost:8090/metrics`

**Data Cloud:**
- data-cloud-event: `localhost:8100/metrics`

**Infrastructure:**
- PostgreSQL exporter: `localhost:9187/metrics`
- Redis exporter: `localhost:9121/metrics`

### Alert Rules

Configured in [prometheus/rules/ghatana-alerts.yml](prometheus/rules/ghatana-alerts.yml):

**Service Health:**
- ServiceDown (critical): Service unavailable for 2 minutes
- HighErrorRate (critical): 5xx errors > 5% for 5 minutes
- HighLatency (warning): P95 latency > 1s for 10 minutes

**JVM Resources:**
- HighJVMMemory (warning): Heap usage > 90% for 5 minutes
- HighGCTime (warning): GC consuming > 10% CPU time

**ActiveJ Specific:**
- EventLoopLag (warning): Event loop lag > 100ms for 5 minutes
- TaskQueueSaturation (warning): Task queue > 80% full

**Database:**
- HighConnectionPoolUsage (warning): Pool usage > 80%
- SlowQueries (warning): P95 query time > 1s

### Log Collection

Promtail collects logs from:
- `/tmp/ghatana-logs/yappc-*.log` (YAPPC services)
- `/tmp/ghatana-logs/backend-api.log` (Backend API)
- `/tmp/ghatana-logs/aep-*.log` (AEP services)

**Log to this directory from your services:**
```java
// Java (SLF4J with JSON encoder)
System.setProperty("log.dir", "/tmp/ghatana-logs");
System.setProperty("log.file", "yappc-domain.log");
```

```javascript
// Node.js (Pino)
const logger = pino({
  transport: {
    target: 'pino/file',
    options: { destination: '/tmp/ghatana-logs/backend-api.log' }
  }
});
```

## Service Integration

### Java Services (ActiveJ + Micrometer)

Already configured via `libs:observability` module:

```java
// Automatically exposes /metrics endpoint
@Inject MicrometerMetrics metrics;

// Custom metrics
metrics.counter("custom.events.processed").increment();
metrics.timer("custom.operation.duration").record(duration);
```

### Node.js Services (Fastify + prom-client)

Add to `package.json`:
```json
{
  "dependencies": {
    "prom-client": "^15.1.0"
  }
}
```

Add to server:
```javascript
import client from 'prom-client';

// Enable default metrics
client.collectDefaultMetrics({ timeout: 5000 });

// Expose /metrics endpoint
fastify.get('/metrics', async (request, reply) => {
  reply.type('text/plain');
  return client.register.metrics();
});

// Custom metrics
const httpRequestDuration = new client.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5]
});
```

### Distributed Tracing (OpenTelemetry)

**Java:**
```java
@Inject Tracer tracer;

Span span = tracer.spanBuilder("operation-name").startSpan();
try (Scope scope = span.makeCurrent()) {
    // Your code
} finally {
    span.end();
}
```

**Node.js:**
```javascript
import { trace } from '@opentelemetry/api';

const tracer = trace.getTracer('service-name');
const span = tracer.startSpan('operation-name');
try {
  // Your code
} finally {
  span.end();
}
```

## Grafana Dashboards

### Pre-configured Dashboards

1. **Ghatana Services Overview** (`ghatana-overview`)
   - Service status (up/down)
   - Request rate
   - Response time (P95/P99)
   - JVM heap usage
   - Error rate (5xx)

### Recommended Community Dashboards

Import via Dashboard ID:

- **JVM (Micrometer)**: 4701
- **Spring Boot**: 12900
- **Node.js**: 11159
- **PostgreSQL**: 9628
- **Redis**: 11835
- **Prometheus Stats**: 2

## Alert Configuration

### Slack Integration

1. Create Slack Webhook:
   - Go to https://api.slack.com/messaging/webhooks
   - Create incoming webhook for `#ghatana-alerts`
   - Copy webhook URL

2. Update [alertmanager/alertmanager.yml](alertmanager/alertmanager.yml):
   ```yaml
   global:
     slack_api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
   
   receivers:
     - name: 'critical-alerts'
       slack_configs:
         - channel: '#ghatana-critical'
           title: '🚨 Critical Alert'
           text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
   ```

3. Restart AlertManager:
   ```bash
   docker-compose restart alertmanager
   ```

## Troubleshooting

### Services Not Appearing in Prometheus

1. Check service is exposing `/metrics`:
   ```bash
   curl http://localhost:8080/metrics
   ```

2. Check Prometheus targets:
   ```bash
   open http://localhost:9090/targets
   ```

3. Verify `host.docker.internal` resolves:
   ```bash
   docker exec ghatana-prometheus ping host.docker.internal
   ```

### No Logs in Loki

1. Check Promtail is running:
   ```bash
   docker-compose logs promtail
   ```

2. Verify log files exist:
   ```bash
   ls -la /tmp/ghatana-logs/
   ```

3. Check Loki API:
   ```bash
   curl http://localhost:3100/ready
   ```

### Grafana Can't Connect to Datasources

1. Check datasource configuration:
   ```bash
   docker exec ghatana-grafana cat /etc/grafana/provisioning/datasources/datasources.yml
   ```

2. Test from Grafana container:
   ```bash
   docker exec ghatana-grafana curl http://prometheus:9090/-/healthy
   docker exec ghatana-grafana curl http://loki:3100/ready
   ```

## Maintenance

### Data Retention

- **Prometheus**: 30 days (configurable in docker-compose.yml)
- **Loki**: 7 days (configurable in loki-config.yml)
- **Jaeger**: In-memory (ephemeral)

### Backup

```bash
# Backup Prometheus data
docker run --rm -v ghatana_prometheus-data:/data -v $(pwd):/backup alpine tar czf /backup/prometheus-backup.tar.gz -C /data .

# Backup Grafana dashboards
docker exec ghatana-grafana grafana-cli admin export-dashboards > dashboards-backup.json
```

### Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove volumes (⚠️ deletes all data)
docker-compose down -v
```

## Performance Tips

1. **Adjust scrape intervals** if you have many services:
   ```yaml
   # prometheus.yml
   global:
     scrape_interval: 30s  # Increase from 15s
   ```

2. **Limit metric cardinality**:
   - Avoid high-cardinality labels (user IDs, timestamps)
   - Use `labelkeep` or `labeldrop` in relabel configs

3. **Tune storage**:
   ```yaml
   # prometheus.yml
   command:
     - '--storage.tsdb.retention.size=10GB'
     - '--storage.tsdb.min-block-duration=2h'
   ```

## Next Steps

- [ ] Add custom dashboards for AEP and Data Cloud
- [ ] Integrate with PagerDuty for critical alerts
- [ ] Set up log-based metrics in Loki
- [ ] Add business metrics (events processed, AI model latency)
- [ ] Configure recording rules for expensive queries
- [ ] Add Grafana OnCall for incident management

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
