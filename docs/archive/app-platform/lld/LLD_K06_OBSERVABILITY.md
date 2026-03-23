# LOW-LEVEL DESIGN: K-06 OBSERVABILITY STACK

**Module**: K-06 Observability Stack  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-06 provides **unified metrics collection, distributed tracing, centralized logging, alerting, and SLO management** for all Siddhanta platform services.

**Core Responsibilities**:
- Prometheus metrics collection with custom business dimensions (tenant, jurisdiction, module)
- OpenTelemetry distributed tracing with automatic context propagation
- ELK-based centralized logging with structured JSON format
- Grafana dashboards with configurable alert rules (T1 config packs)
- SLO/SLI management with error budget tracking and burn-rate alerts
- Health check aggregation and readiness probes
- Dual-calendar timestamping in all observability data
- On-chain transaction tracing for digital asset operations

**Invariants**:
1. All services MUST expose Prometheus metrics at `/metrics`
2. All services MUST propagate OpenTelemetry trace context (W3C Trace Context)
3. All logs MUST be structured JSON with mandatory fields: `trace_id`, `tenant_id`, `module`, `timestamp_bs`, `timestamp_gregorian`
4. Alert rules MUST be configurable via K-02 without redeploy
5. Observability overhead MUST NOT exceed 2% of request latency
6. Metric cardinality MUST be capped (max 10K unique label combinations per metric)

### 1.2 Explicit Non-Goals

- ❌ Application Performance Monitoring (APM) agent — use OpenTelemetry auto-instrumentation
- ❌ Log storage beyond retention window — archived via lifecycle policies
- ❌ Business intelligence / analytics — use dedicated analytics DB

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Alert thresholds, dashboard config, SLO targets | K-02 stable |
| K-15 Dual-Calendar | BS timestamps for all observability data | K-15 stable |
| Prometheus | Metrics storage and querying | Infra available |
| Jaeger / OTLP | Trace storage and visualization | Infra available |
| Elasticsearch | Log storage and search | Infra available |
| Grafana | Dashboard rendering and alerting | Infra available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/observability/health
Authorization: Bearer {service_token}

Response 200:
{
  "status": "HEALTHY",
  "checks": {
    "prometheus": { "status": "UP", "latency_ms": 2 },
    "jaeger": { "status": "UP", "latency_ms": 5 },
    "elasticsearch": { "status": "UP", "latency_ms": 8 },
    "grafana": { "status": "UP", "latency_ms": 3 }
  },
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

```yaml
GET /api/v1/observability/slo/{slo_name}
Authorization: Bearer {service_token}

Response 200:
{
  "slo_name": "order_placement_latency",
  "target": 0.999,
  "current": 0.9995,
  "error_budget_remaining": 0.85,
  "burn_rate_1h": 0.001,
  "burn_rate_6h": 0.0005,
  "window": "30d",
  "status": "HEALTHY",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

```yaml
POST /api/v1/observability/alerts/silence
Authorization: Bearer {admin_token}

Request:
{
  "alert_name": "HighOrderLatency",
  "duration_minutes": 60,
  "reason": "Planned maintenance window",
  "silenced_by": "usr_admin1"
}

Response 200:
{
  "silence_id": "sil_abc123",
  "expires_at": "2025-03-02T11:30:00Z"
}
```

```yaml
GET /api/v1/observability/dashboards
Authorization: Bearer {service_token}

Response 200:
{
  "dashboards": [
    { "id": "platform_overview", "title": "Platform Overview", "category": "system" },
    { "id": "oms_operations", "title": "OMS Operations", "category": "domain" },
    { "id": "slo_tracker", "title": "SLO Tracker", "category": "sre" }
  ]
}
```

### 2.2 SDK Method Signatures

```typescript
interface ObservabilityClient {
  /** Record a metric value */
  recordMetric(name: string, value: number, labels: Record<string, string>): void;

  /** Start a trace span */
  startSpan(name: string, attributes?: Record<string, string>): Span;

  /** Log a structured message */
  log(level: LogLevel, message: string, context: LogContext): void;

  /** Get SLO status */
  getSloStatus(sloName: string): Promise<SloStatus>;

  /** Register health check */
  registerHealthCheck(name: string, checker: () => Promise<HealthResult>): void;
}

interface Span {
  setAttribute(key: string, value: string | number | boolean): void;
  addEvent(name: string, attributes?: Record<string, string>): void;
  setStatus(code: SpanStatusCode, message?: string): void;
  end(): void;
}

interface LogContext {
  traceId: string;
  tenantId: string;
  module: string;
  userId?: string;
  correlationId?: string;
  timestampBs: string;
  [key: string]: unknown;
}
```

### 2.3 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| OBS_E001 | 503 | Yes | Prometheus unavailable |
| OBS_E002 | 503 | Yes | Jaeger unavailable |
| OBS_E003 | 503 | Yes | Elasticsearch unavailable |
| OBS_E004 | 400 | No | Invalid SLO name |
| OBS_E005 | 400 | No | Metric cardinality exceeded |
| OBS_E006 | 429 | Yes | Log ingestion rate exceeded |

---

## 3. DATA MODEL

### 3.1 SLO Definitions Table

```sql
CREATE TABLE slo_definitions (
  slo_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slo_name VARCHAR(255) NOT NULL UNIQUE,
  description TEXT,
  target DECIMAL(6,4) NOT NULL,  -- e.g., 0.9990 = 99.90%
  metric_query TEXT NOT NULL,     -- PromQL expression
  window_days INT NOT NULL DEFAULT 30,
  burn_rate_thresholds JSONB NOT NULL DEFAULT '{"critical": 14.4, "warning": 6.0}',
  alert_channels JSONB NOT NULL DEFAULT '["pagerduty", "slack"]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at_bs VARCHAR(30) NOT NULL
);
```

### 3.2 Alert Rules Table

```sql
CREATE TABLE alert_rules (
  rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_name VARCHAR(255) NOT NULL UNIQUE,
  severity VARCHAR(20) NOT NULL CHECK (severity IN ('P1', 'P2', 'P3', 'P4')),
  promql_expression TEXT NOT NULL,
  for_duration VARCHAR(20) NOT NULL DEFAULT '5m',
  labels JSONB NOT NULL DEFAULT '{}',
  annotations JSONB NOT NULL DEFAULT '{}',
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  config_source VARCHAR(50) NOT NULL DEFAULT 'K-02'  -- T1 config pack origin
);
```

### 3.3 Standard Log Schema

```json
{
  "timestamp_gregorian": "2025-03-02T10:30:00.123Z",
  "timestamp_bs": "2081-11-17",
  "level": "INFO",
  "module": "D-01-OMS",
  "trace_id": "tr_abc123",
  "span_id": "sp_def456",
  "tenant_id": "tenant_np_1",
  "user_id": "usr_abc",
  "correlation_id": "corr_789",
  "message": "Order placed successfully",
  "metadata": {
    "order_id": "ord_123",
    "instrument": "NABIL",
    "latency_ms": 8
  }
}
```

### 3.4 Standard Metric Dimensions

All platform metrics MUST include these labels:
- `tenant` — tenant identifier
- `module` — source module (e.g., `k01_iam`, `d01_oms`)
- `environment` — deployment environment (`prod`, `staging`, `uat`)
- `jurisdiction` — optional, for jurisdiction-specific metrics

---

## 4. CONTROL FLOW

### 4.1 Metrics Collection Flow

```
Service → OpenTelemetry SDK auto-instrumentation
  → Prometheus Exporter (/metrics endpoint)
  → Prometheus Server (scrape every 15s)
  → Grafana (dashboard visualization)
  → Alertmanager (if threshold breached)
    → PagerDuty / Slack / Email (routed by severity + module)
```

### 4.2 Distributed Tracing Flow

```
Client Request → API Gateway (K-11)
  → Extract/Generate W3C traceparent header
  → Propagate trace_id, span_id to downstream services
  → Each service:
    → OTel SDK creates child span
    → Span attributes: tenant_id, user_id, operation
    → Span exported to Jaeger via OTLP exporter
  → Jaeger stores trace (7-day hot, sampled retention)
  → Grafana Tempo UI for trace visualization
```

### 4.3 Log Ingestion Flow

```
Service → Structured JSON log (stdout)
  → Fluentd/Fluent Bit (sidecar or DaemonSet)
    → Enrich: add pod_name, namespace, node
    → Validate: mandatory fields present
  → Elasticsearch (index: logs-{module}-{date})
    → ILM policy: 30d hot → 90d warm → 10yr cold (S3)
  → Kibana for search and visualization
```

### 4.4 SLO Burn Rate Alert Flow

```
Prometheus → Evaluate SLO PromQL every 1min
  → Calculate error_budget_consumed over window
  → Calculate burn_rate (1h, 6h, 24h, 30d)
  → IF burn_rate_1h > critical_threshold:
      → FIRE P1 alert → PagerDuty immediate
  → IF burn_rate_6h > warning_threshold:
      → FIRE P2 alert → Slack on-call channel
  → Grafana SLO dashboard updated in real-time
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 SLO Error Budget Calculation

```python
def calculate_error_budget(slo: SLODefinition, window_start: datetime) -> ErrorBudget:
    """
    Multi-window burn rate SLO as per Google SRE book.
    """
    total_requests = prometheus.query(f'sum(rate({slo.metric_query}_total[{slo.window_days}d]))')
    error_requests = prometheus.query(f'sum(rate({slo.metric_query}_errors[{slo.window_days}d]))')
    
    error_rate = error_requests / total_requests if total_requests > 0 else 0
    budget_total = 1 - slo.target  # e.g., 0.001 for 99.9%
    budget_consumed = error_rate / budget_total
    budget_remaining = max(0, 1 - budget_consumed)
    
    burn_rates = {
        "1h": calculate_burn_rate(slo, "1h"),
        "6h": calculate_burn_rate(slo, "6h"),
        "24h": calculate_burn_rate(slo, "24h"),
        "30d": calculate_burn_rate(slo, f"{slo.window_days}d")
    }
    
    return ErrorBudget(
        target=slo.target,
        budget_total=budget_total,
        budget_consumed=budget_consumed,
        budget_remaining=budget_remaining,
        burn_rates=burn_rates,
        status="HEALTHY" if budget_remaining > 0.2 else "WARNING" if budget_remaining > 0 else "EXHAUSTED"
    )
```

### 5.2 Adaptive Sampling Strategy

```python
def should_sample_trace(trace_context: TraceContext) -> bool:
    """
    Adaptive sampling: higher rate for errors, lower for success.
    Controlled via K-02 config.
    """
    config = k02.get("observability.sampling")
    
    # Always sample errors
    if trace_context.has_error:
        return True
    
    # Always sample slow requests (> P99 threshold)
    if trace_context.duration_ms > config.get("slow_request_threshold_ms", 12):
        return True
    
    # Base rate sampling (default 10%)
    base_rate = config.get("base_sample_rate", 0.10)
    return random.random() < base_rate
```

### 5.3 Metric Cardinality Guard

```python
MAX_CARDINALITY = 10_000  # per metric name

def record_metric(name: str, value: float, labels: dict):
    """Guard against cardinality explosion."""
    current_cardinality = cardinality_tracker.get(name)
    if current_cardinality >= MAX_CARDINALITY:
        log.warn(f"Metric {name} cardinality exceeded {MAX_CARDINALITY}, dropping labels")
        labels = {k: v for k, v in labels.items() if k in REQUIRED_LABELS}  # keep only required
    
    prometheus_client.record(name, value, labels)
    cardinality_tracker.increment(name, labels)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| Metric scrape (/metrics) | 5ms | 20ms | 50ms | 200ms |
| Log ingestion (per record) | 1ms | 3ms | 5ms | 10ms |
| Trace export (async) | N/A | N/A | N/A | Non-blocking |
| SLO query | 50ms | 200ms | 500ms | 2s |
| Health check | 2ms | 5ms | 10ms | 50ms |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Metrics ingestion | 1M data points/min |
| Log ingestion | 500K events/sec |
| Trace spans | 200K spans/sec |
| Concurrent Grafana dashboards | 500 |

### 6.3 Storage Targets

| Data Type | Hot Retention | Cold Retention | Estimated Volume |
|-----------|--------------|----------------|------------------|
| Metrics | 30 days | 1 year | 500GB/month |
| Logs | 90 days | 10 years | 2TB/month |
| Traces | 7 days (sampled) | N/A | 200GB/month |
| Dashboards/Alerts | Permanent | N/A | <1GB |

---

## 7. SECURITY DESIGN

### 7.1 Access Control

- Grafana dashboards: RBAC via K-01 SSO integration
- Prometheus: internal-only access (no public exposure)
- Elasticsearch: X-Pack security with API keys
- Alert silencing requires `observability:admin` permission

### 7.2 Data Sensitivity

- PII MUST NOT appear in logs (masked at ingestion)
- Trace attributes MUST NOT contain passwords or tokens
- Metrics labels MUST NOT contain user-identifiable information
- Log redaction rules configurable via K-02

### 7.3 Multi-Tenant Isolation

- All metrics tagged with `tenant` label
- Grafana data source filters enforce tenant isolation
- Elasticsearch index-per-tenant for log isolation
- Trace data filtered by `tenant_id` attribute

---

## 8. OBSERVABILITY & AUDIT (META-OBSERVABILITY)

### 8.1 Self-Monitoring Metrics

```
# Ingestion health
siddhanta_obs_metric_scrape_duration_ms{target}       histogram
siddhanta_obs_log_ingestion_rate{module}               counter
siddhanta_obs_trace_export_rate{exporter}              counter
siddhanta_obs_dropped_events_total{type, reason}       counter

# Storage health
siddhanta_obs_prometheus_storage_bytes                  gauge
siddhanta_obs_elasticsearch_index_size_bytes{index}    gauge
siddhanta_obs_jaeger_storage_bytes                      gauge

# Alert health
siddhanta_obs_alert_firing_total{severity}             gauge
siddhanta_obs_alert_silence_active_total               gauge
siddhanta_obs_slo_budget_remaining{slo_name}           gauge
```

### 8.2 Alerting on Alerting

| Alert | Condition | Severity |
|-------|-----------|----------|
| Prometheus scrape failures | >5% targets failing for 5min | P1 |
| Elasticsearch cluster red | Cluster health RED for 2min | P1 |
| Log ingestion lag | >60s lag on any pipeline | P2 |
| Jaeger collector errors | >1% error rate for 5min | P2 |
| SLO budget exhausted | Any SLO budget <= 0 | P1 |
| Metric cardinality warning | Any metric >8K cardinality | P3 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Dashboard Templates (T1)

```json
{
  "content_pack_type": "T1",
  "name": "nepal-sebon-compliance-dashboard",
  "jurisdiction": "NP",
  "dashboards": [
    {
      "id": "sebon_daily_report",
      "title": "SEBON Daily Compliance",
      "panels": [
        { "type": "stat", "title": "Orders Processed", "query": "sum(siddhanta_oms_orders_total{tenant=~\".*\"})" },
        { "type": "timeseries", "title": "Settlement Latency", "query": "histogram_quantile(0.99, ...)" }
      ]
    }
  ]
}
```

### 9.2 Custom Alert Rules (T2)

```rego
package siddhanta.observability.alerts

# Jurisdiction-specific alert: Nepal market hours only
fire_alert["sebon_circuit_breaker"] {
    input.metric == "price_change_pct"
    abs(input.value) > 10  # SEBON 10% circuit breaker
    is_nepal_trading_hours(input.timestamp)
}
```

### 9.3 Custom Metric Exporters (T3)

```typescript
interface MetricExporterPlugin {
  readonly metadata: { name: string; version: string };

  /** Export metrics to external system */
  export(metrics: MetricBatch): Promise<void>;

  /** Health check for export target */
  healthCheck(): Promise<boolean>;
}
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-OBS-001 | Metric recording | Metric appears at /metrics endpoint |
| UT-OBS-002 | Cardinality guard | Excess labels dropped gracefully |
| UT-OBS-003 | Log format validation | Mandatory fields present |
| UT-OBS-004 | SLO calculation | Error budget matches manual calculation |
| UT-OBS-005 | Adaptive sampling | Error traces always sampled |
| UT-OBS-006 | PII redaction | Sensitive fields masked in logs |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-OBS-001 | End-to-end trace propagation | Trace spans linked across 3+ services |
| IT-OBS-002 | Log pipeline | Log appears in Elasticsearch within 5s |
| IT-OBS-003 | Alert firing | Alert triggers notification within 2min |
| IT-OBS-004 | SLO dashboard | SLO status updates reflect metric changes |
| IT-OBS-005 | Tenant isolation | Tenant A cannot query Tenant B logs |
| IT-OBS-006 | K-02 config reload | Alert threshold change takes effect without restart |

### 10.3 Load Tests

| Test | Description | Target |
|------|-------------|--------|
| LT-OBS-001 | Metric scrape under load | 1M data points/min without lag |
| LT-OBS-002 | Log ingestion burst | 500K events/sec sustained for 5min |
| LT-OBS-003 | Trace export under load | 200K spans/sec without drops |
| LT-OBS-004 | Grafana concurrent users | 500 dashboards rendering simultaneously |

### 10.4 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-OBS-001 | Prometheus node failure | Thanos/peer recovers within 30s |
| CT-OBS-002 | Elasticsearch node failure | Cluster self-heals, no data loss |
| CT-OBS-003 | Fluentd sidecar crash | Logs buffered on disk, replayed on restart |
| CT-OBS-004 | Grafana failure | Dashboards unavailable, metrics/logs unaffected |

---

**END OF K-06 OBSERVABILITY LLD**
