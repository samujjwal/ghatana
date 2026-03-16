# YAPPC Observability Dashboards

## Overview

This directory contains Grafana dashboard configurations for YAPPC platform observability.

## Dashboards

### `agent-execution-metrics.json` (6.1.5–6.1.6)

**Purpose:** Monitor YAPPC agent execution performance and lifecycle metrics in real-time.

**Metrics Tracked:**

| Metric | Type | Purpose |
|--------|------|---------|
| `agent_execution_duration_ms` | Histogram | Total time for agent to complete a turn (PERCEIVE → REASON → ACT → CAPTURE → REFLECT) |
| `phase_advance_count_total` | Counter | Number of  times agents advance between lifecycle phases (by phase label) |
| `llm_call_latency_ms` | Histogram | Time to receive response from LLM provider (by model label) |
| `intent_capture_duration_ms` | Histogram | Time to capture and parse user intent |

**Dashboard Panels:**

1. **Agent Execution Duration (5m rate)** — Time-series showing execution duration trend
   - Query: `rate(agent_execution_duration_ms_bucket[5m])`
   - Legend: Bucket boundaries (le=...)
   - Unit: milliseconds

2. **Phase Advance Count (5m rate)** — Rate of phase transitions
   - Query: `rate(phase_advance_count_total[5m])`
   - Legend: Phase names (PERCEIVE, REASON, ACT, CAPTURE, REFLECT)
   - Unit: operations/second

3. **LLM Call Latency (histogram percentiles)** — P95 and P50 latency
   - Query: 
     - `histogram_quantile(0.95, rate(llm_call_latency_ms_bucket[5m]))`
     - `histogram_quantile(0.50, rate(llm_call_latency_ms_bucket[5m]))`
   - Legend: Model names (gpt-4, gpt-3.5-turbo, claude, etc.)
   - Unit: milliseconds

4. **Gauges (Bottom Row):**
   - Agent Execution Duration — p95
   - Phase Advance Rate (1m)
   - LLM Call Latency — p95

## Installation

### Grafana UI
1. Open Grafana at `http://localhost:3000`
2. Go to **Dashboards** → **Import**
3. Click **Upload JSON File** and select `agent-execution-metrics.json`
4. Choose Prometheus data source
5. Click **Import**

### Grafana Provisioning (Automated)
1. Copy `agent-execution-metrics.json` to Grafana provisioning directory:
   ```bash
   cp agent-execution-metrics.json /etc/grafana/provisioning/dashboards/
   ```
2. Restart Grafana: `systemctl restart grafana-server`

## Configuration

### Prometheus Data Source

Ensure Prometheus is configured to scrape YAPPC metrics endpoint:

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'yappc'
    static_configs:
      - targets: ['localhost:8080']  # YAPPC API service
    metrics_path: '/metrics'
```

### Grafana Variables (Optional)

To add dynamic filtering, create template variables in Grafana:

| Variable | Query |
|----------|-------|
| `agent_id` | `label_values(agent_execution_duration_ms, agent_id)` |
| `phase` | `label_values(phase_advance_count_total, phase)` |
| `model` | `label_values(llm_call_latency_ms, model)` |

Then use in panel queries:
```
rate(agent_execution_duration_ms_bucket{agent_id=~"$agent_id"}[5m])
```

## Metrics Interpretation

### Agent Execution Duration
- **Green (< 5s):** Healthy, normal execution
- **Yellow (5–10s):** Degraded performance, may indicate LLM latency or policy processing delays
- **Red (> 10s):** Poor performance, investigate LLM provider or policy bottlenecks

### Phase Advance Count
- **Increasing:** Healthy agent activity
- **Flat/Decreasing:** Possible pipeline stalls or failures
- Check DLQ (`/api/v1/dlq`) for failed events

### LLM Call Latency
- **p95 < 3s:** Normal
- **p95 5–10s:** Acceptable
- **p95 > 10s:** LLM provider degradation or rate-limiting

## Testing

Unit tests for metrics collection: [MetricsCollectionE2eTest.java](../backend/api/src/test/java/com/ghatana/yappc/api/observability/MetricsCollectionE2eTest.java)

Test scenarios:
- Histogram bucket creation and percentile calculation
- Counter increments and rates
- Correlation ID propagation in metric labels
- Prometheus scrape format compliance

To run tests:
```bash
./gradlew :products:yappc:backend:api:test --tests MetricsCollectionE2eTest
```

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
|Metrics not appearing | Prometheus not scraping | Check `/metrics` endpoint responds with Prometheus format |
| No data in panels | Dashboard time range too narrow | Extend time range (top-right of Grafana) |
| Inconsistent p95 values | Insufficient data | Wait 2–5 minutes for histogram buckets to populate |

## References

- [Prometheus Histograms](https://prometheus.io/docs/practices/histograms/)
- [Grafana Dashboard API](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/manage-dashboards/)
- [Micrometer Prometheus Registry](https://micrometer.io/docs/registry/prometheus)
