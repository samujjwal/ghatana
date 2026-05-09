# Content Generation Observability Dashboards

This document defines the production-grade dashboards for content generation observability.

## Dashboard 1: Content Generation Overview

**Purpose**: High-level view of content generation health and throughput

**Panels**:
1. **Generation Throughput** (Time Series)
   - Metric: `content_generation_requests_total`
   - Labels: `status` (success, failure, pending)
   - Aggregation: Sum over 5m intervals
   - Visualization: Stacked area chart

2. **Validation Failure Rate** (Gauge)
   - Metric: `content_generation_validation_failures_total / content_generation_requests_total`
   - Thresholds: <5% (green), 5-15% (yellow), >15% (red)
   - Visualization: Single stat with color coding

3. **Review Backlog** (Time Series)
   - Metric: `content_review_queue_depth`
   - Aggregation: Current value
   - Visualization: Single stat with trend line

4. **AI Model Latency** (Heatmap)
   - Metric: `content_generation_ai_latency_seconds`
   - Labels: `model` (openai, ollama, claude)
   - Aggregation: p95 over 5m intervals
   - Visualization: Heatmap by model and time

5. **Auto-Publish Eligibility** (Pie Chart)
   - Metric: `content_generation_auto_publish_eligible`
   - Labels: `eligible` (true, false)
   - Visualization: Pie chart

6. **Cost per Artifact** (Time Series)
   - Metric: `content_generation_cost_per_artifact`
   - Aggregation: Average over 1h intervals
   - Visualization: Line chart

## Dashboard 2: Content Quality Monitoring

**Purpose**: Monitor content quality metrics and validation results

**Panels**:
1. **Quality Score Distribution** (Histogram)
   - Metric: `content_generation_quality_score`
   - Aggregation: Histogram
   - Visualization: Bar chart

2. **Validation Failures by Reason** (Bar Chart)
   - Metric: `content_generation_validation_failures_total`
   - Labels: `reason` (profanity, pii, safety, format, accuracy)
   - Aggregation: Sum over 1h intervals
   - Visualization: Grouped bar chart

3. **Human Review Rate** (Time Series)
   - Metric: `content_generation_human_review_rate`
   - Aggregation: Percentage over 1h intervals
   - Visualization: Line chart with threshold line

4. **Content Type Distribution** (Pie Chart)
   - Metric: `content_generation_by_type`
   - Labels: `type` (claim, example, simulation, animation, assessment)
   - Aggregation: Sum over 24h
   - Visualization: Donut chart

5. **Domain Coverage** (Table)
   - Metric: `content_generation_by_domain`
   - Labels: `domain` (math, science, engineering, medicine, etc.)
   - Aggregation: Sum over 7d
   - Visualization: Table with trend

## Dashboard 3: Infrastructure Health

**Purpose**: Monitor underlying infrastructure for content generation

**Panels**:
1. **AI Model Status** (Status Panel)
   - Metric: `content_generation_ai_model_status`
   - Labels: `model`
   - Visualization: Status icons with success rate

2. **Database Connection Pool** (Gauge)
   - Metric: `database_connection_pool_usage`
   - Labels: `database` (postgresql)
   - Thresholds: <70% (green), 70-90% (yellow), >90% (red)
   - Visualization: Gauge

3. **Cache Hit Rate** (Time Series)
   - Metric: `cache_hit_rate`
   - Labels: `cache` (redis, content)
   - Aggregation: Percentage over 5m intervals
   - Visualization: Line chart

4. **Queue Depth** (Time Series)
   - Metric: `content_generation_queue_depth`
   - Labels: `queue` (generation, validation, review)
   - Aggregation: Current value
   - Visualization: Multi-line chart

5. **Dead Letter Queue Count** (Single Stat)
   - Metric: `content_generation_dlq_count`
   - Visualization: Single stat with alert threshold

6. **Storage Usage** (Gauge)
   - Metric: `storage_usage_bytes`
   - Labels: `storage` (s3, local)
   - Aggregation: Percentage
   - Visualization: Gauge with threshold

## Dashboard 4: Cost and Performance

**Purpose**: Track cost efficiency and performance optimization

**Panels**:
1. **Total Cost per Hour** (Time Series)
   - Metric: `content_generation_cost_total`
   - Aggregation: Sum over 1h intervals
   - Visualization: Line chart with budget line

2. **Cost per Model** (Bar Chart)
   - Metric: `content_generation_cost_by_model`
   - Labels: `model`
   - Aggregation: Sum over 24h
   - Visualization: Grouped bar chart

3. **Token Usage** (Time Series)
   - Metric: `content_generation_tokens_total`
   - Labels: `direction` (input, output)
   - Aggregation: Sum over 1h intervals
   - Visualization: Stacked area chart

4. **Cost per 1k Tokens** (Table)
   - Metric: `content_generation_cost_per_1k_tokens`
   - Labels: `model`
   - Aggregation: Average over 7d
   - Visualization: Table

5. **Generation Time vs Cost** (Scatter Plot)
   - Metric: `content_generation_duration_seconds` vs `content_generation_cost`
   - Aggregation: Scatter plot of individual requests
   - Visualization: Scatter plot

## Dashboard 5: Alerts and Incidents

**Purpose**: Monitor alert status and incident response

**Panels**:
1. **Active Alerts** (Table)
   - Metric: `alerts_active`
   - Labels: `severity`, `service`
   - Visualization: Table with alert details

2. **Alert Rate** (Time Series)
   - Metric: `alerts_total`
   - Labels: `severity` (critical, warning, info)
   - Aggregation: Rate over 1h intervals
   - Visualization: Stacked area chart

3. **MTTR (Mean Time to Resolve)** (Time Series)
   - Metric: `incident_mttr_minutes`
   - Aggregation: Average over 7d
   - Visualization: Line chart with SLA line

4. **Incident Count by Service** (Bar Chart)
   - Metric: `incidents_total`
   - Labels: `service`
   - Aggregation: Sum over 30d
   - Visualization: Bar chart

5. **On-Call Status** (Status Panel)
   - Metric: `on_call_status`
   - Visualization: Status indicator

## Alert Definitions

### Critical Alerts

1. **High Validation Failure Rate**
   - Condition: `rate(content_generation_validation_failures_total[5m]) > 0.1`
   - Duration: 5m
   - Severity: critical
   - Notification: PagerDuty, Slack #alerts-critical
   - Runbook: [Validation Failure Runbook](#validation-failure-runbook)

2. **AI Model Degradation**
   - Condition: `content_generation_ai_model_success_rate < 0.95`
   - Duration: 10m
   - Severity: critical
   - Notification: PagerDuty, Slack #alerts-critical
   - Runbook: [AI Model Degradation Runbook](#ai-model-degradation-runbook)

3. **Queue Depth Threshold**
   - Condition: `content_generation_queue_depth > 1000`
   - Duration: 15m
   - Severity: critical
   - Notification: PagerDuty, Slack #alerts-critical
   - Runbook: [Queue Backlog Runbook](#queue-backlog-runbook)

### Warning Alerts

1. **Review Backlog High**
   - Condition: `content_review_queue_depth > 500`
   - Duration: 30m
   - Severity: warning
   - Notification: Slack #alerts-warning
   - Runbook: [Review Backlog Runbook](#review-backlog-runbook)

2. **Cost Budget Exceeded**
   - Condition: `rate(content_generation_cost_total[1h]) > budget_threshold`
   - Duration: 1h
   - Severity: warning
   - Notification: Slack #alerts-warning, Email
   - Runbook: [Cost Management Runbook](#cost-management-runbook)

3. **Cache Hit Rate Low**
   - Condition: `cache_hit_rate < 0.8`
   - Duration: 30m
   - Severity: warning
   - Notification: Slack #alerts-warning
   - Runbook: [Cache Performance Runbook](#cache-performance-runbook)

### Info Alerts

1. **Content Generation Milestone**
   - Condition: `content_generation_requests_total % 1000 == 0`
   - Duration: 1m
   - Severity: info
   - Notification: Slack #observability
   - Runbook: None (informational)

2. **New Content Type Published**
   - Condition: `content_generation_published_total increases`
   - Duration: 1m
   - Severity: info
   - Notification: Slack #content-team
   - Runbook: None (informational)
