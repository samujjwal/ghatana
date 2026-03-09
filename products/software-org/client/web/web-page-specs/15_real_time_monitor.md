# 15. Real-Time Monitor – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 15. `/realtime-monitor` – Real-Time Monitor](../WEB_PAGE_FEATURE_INVENTORY.md#15-realtime-monitor--real-time-monitor)

**Code file:**

- `src/pages/RealTimeMonitor.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a NOC-style live view of infrastructure and services, showing streaming metrics, anomalies, and alerts so operators can detect and respond to issues quickly.

**Primary goals:**

- Show **live system health** (CPU, memory, disk, uptime, etc.).
- Visualize **time-series metrics** for a selected signal.
- List **anomalies** and **alerts** with severity and acknowledge actions.

**Non-goals:**

- Long-term trend analysis (Reports handle that).
- Deep per-service dependency mapping (future enhancement).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **On-call SRE / Ops engineer.**
- **NOC analysts.**
- **Platform engineers.**

**Scenarios:**

1. **Handling an incident**
   - GIVEN: Pager goes off for high error rates.
   - WHEN: On-call opens Real-Time Monitor.
   - THEN: They see elevated CPU/latency, related anomalies, and new critical alerts, then acknowledge and start remediation.

2. **Watching a deployment**
   - GIVEN: Production deployment is in progress.
   - WHEN: SRE keeps Real-Time Monitor open.
   - THEN: They observe metrics and watch for anomalies or alerts.

3. **Validating auto-scaling**
   - GIVEN: Team tuned auto-scaling rules.
   - WHEN: They observe CPU and request rate metrics.
   - THEN: They confirm resources adjust without generating critical alerts.

---

## 3. Content & Layout Overview

From `RealTimeMonitor.tsx`:

- **Header:**
  - Title: `Real-Time Monitor`.
  - Subtitle: `Live metrics, anomalies and alerts for your services`.
  - Connection status pill: `● Live` / `Reconnecting...`.

- **System Health cards:**
  - CPU Usage.
  - Memory Usage.
  - Disk Usage.
  - Uptime.

- **Real-time metrics section:**
  - Metric selector.
  - Time-series chart (`MetricChart`).

- **Anomalies section:**
  - `AnomalyDetector` component listing anomalies.

- **Alerts sidebar/panel:**
  - Alert severity filter (All, Critical, Warning, Info).
  - `AlertPanel` listing alerts with text and acknowledge button.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Live feel:**
  - Metrics and connection status should visually indicate liveness.
- **Severity clarity:**
  - Critical vs Warning vs Info must be easy to distinguish.
- **Fast acknowledgement:**
  - Acknowledge buttons near each alert.
- **Noise control:**
  - Filters to only show high-severity alerts when needed.

---

## 5. Completeness and Real-World Coverage

Monitor should support:

- Observing resource health.
- Detecting anomalies (e.g., sudden spike in error rate or latency).
- Managing alerts queue (what’s new, what’s acknowledged).

---

## 6. Modern UI/UX Nuances and Features

- Smooth chart updates without jank.
- Flashing/pulsing indicator for `Reconnecting...` state.
- Sticky alerts panel header.
- Clear empty state when there are no alerts/anomalies.

---

## 7. Coherence and Consistency Across the App

- Alerts and anomalies align with AI/HITL/Automation semantics.
- Metric names match those used on Dashboard and Reports.
- Incidents/alerts link conceptually to Event Simulator for testing.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#15-realtime-monitor--real-time-monitor`
- Implementation: `src/pages/RealTimeMonitor.tsx`
- Shared components: `MetricChart`, `AlertPanel`, `AnomalyDetector` in shared/features directories.

---

## 9. Open Gaps & Enhancement Plan

- Integrate with real metrics backend (Prometheus, Datadog, etc.).
- Add per-service drill-down views.
- Correlate alerts with deployments/events for faster triage.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Real-Time Monitor                [● Live]
Subtitle: Live metrics, anomalies and alerts for your services

[System Health Cards]
CPU Usage     Memory Usage     Disk Usage     Uptime

[Metric Chart]
[ Metric: CPU Usage ▼ ]  [ Time Range: Last 15 min ▼ ]

[Anomalies]                      [Alerts Panel]
```

### 10.2 Sample System Health Values

- CPU Usage: `72%`
- Memory Usage: `65%`
- Disk Usage: `58%`
- Uptime: `14d 3h 22m`

### 10.3 Sample Metric Chart Context

Selected metric: `CPU Usage`  
Time range: `Last 15 minutes`

Chart shows line oscillating between `55%` and `80%` with a recent peak at `78%`.

### 10.4 Sample Anomalies

```text
Anomalies (last 30 min)

[CRITICAL] Error rate spike in checkout-api
- Detected at: 10:32
- Metric: HTTP 5xx rate
- Baseline: 0.3%   Observed: 5.8%

[WARNING] Latency increase in fraud-service
- Detected at: 10:28
- Metric: P95 latency
- Baseline: 180ms  Observed: 320ms
```

### 10.5 Sample Alerts Panel

Filter: [ All | Critical | Warning | Info ] (active: `Critical`)

```text
[CRITICAL] P1 – Checkout errors above threshold
- Started: 10:32
- Service: checkout-api

[ Acknowledge ]

[WARNING] Elevated latency on fraud-service
- Started: 10:28
- Service: fraud-service

[ Acknowledge ]
```

These examples paint a concrete picture of Real-Time Monitor’s live operations view.
