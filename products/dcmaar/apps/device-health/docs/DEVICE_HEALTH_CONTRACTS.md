# Device Health Contracts

This document describes the **Device Health product contracts** and how they relate to the platform-level DCMAAR contracts.

Device Health **does not fork** DCMAAR telemetry. Instead, it defines a small set of product-specific types layered on top of the shared `dcmaar.v1` contracts.

---

## 1. Namespaces & Ownership

- **Platform contracts (shared)**
  - Package: `dcmaar.v1` (protobuf)
  - Source: `products/dcmaar/contracts/proto-core/dcmaar/v1/*.proto`
  - Examples:
    - `Metric`, `MetricEnvelope`, `MetricEnvelopeBatch`
    - `Event`, `EventEnvelope`, `EventEnvelopeBatch`
    - `IngestService`, `QueryService`, `PolicyService`

- **Device Health contracts (product-specific)**
  - Logical namespace: `devicehealth.*`
  - Implemented primarily in TypeScript under:
    - `@dcmaar/device-health-extension`
    - `@dcmaar/*` shared extension libraries (for `ExtensionPlugin`, connectors, etc.).
  - These contracts **wrap** or **annotate** DCMAAR telemetry, they do not redefine base metrics/events.

---

## 2. Core Types

> Note: These types are expressed in TS/JSON today; they can be promoted to dedicated protobuf packages later if cross-language use is required.

### 2.1 `DeviceHealthMetric`

Represents a single device health datapoint, mapped onto DCMAAR metrics and events.

Suggested structure:

```ts
export interface DeviceHealthMetric {
  id: string; // Stable ID for deduplication
  deviceId: string; // Logical device identifier
  metricType:
    | 'cpu'
    | 'memory'
    | 'disk'
    | 'network'
    | 'battery'
    | 'temperature'
    | 'custom';
  value: number; // Normalized value (e.g., percent, bytes, Celsius)
  unit: string; // "percent", "bytes", "celsius", etc.
  severity?: 'info' | 'warning' | 'critical';
  timestamp: string; // RFC3339
  dimensions?: Record<string, string>; // host, os, region, app, etc.
}
```

**Mapping to DCMAAR:**

- Each `DeviceHealthMetric` is emitted as one or more `dcmaar.v1.MetricWithLabels` inside a `MetricEnvelope`.
- `metricType`, `severity`, and `dimensions` are mapped to labels.
- `deviceId` is mapped to the `ResourceId` / host metadata in `EnvelopeMeta`.

### 2.2 `HealthConfig`

Controls which metrics Device Health collects and how aggressively.

```ts
export interface HealthConfig {
  samplingIntervalSeconds: number; // Metric collection interval
  enabledMetricTypes: string[]; // e.g. ["cpu", "memory", "disk"]
  warningThresholds?: Record<string, number>; // metricType -> threshold
  criticalThresholds?: Record<string, number>; // metricType -> threshold
  maxHistoryDays?: number; // Local retention window
}
```

This configuration is typically delivered via DCMAAR **sync/control** flows (`dcmaar.v1.sync` contracts) and stored in extension/agent-local configuration.

### 2.3 `HealthAlert`

Represents a product-level interpretation of telemetry, used for UI and notifications.

```ts
export interface HealthAlert {
  id: string;
  deviceId: string;
  kind: 'threshold_breach' | 'anomaly' | 'offline' | 'recovery';
  metricType?: string; // when applicable
  severity: 'info' | 'warning' | 'critical';
  message: string; // human-readable
  startedAt: string; // RFC3339
  endedAt?: string; // for recovered alerts
  metadata?: Record<string, string>;
}
```

**Mapping to DCMAAR events:**

- Alerts are represented as `dcmaar.v1.EventWithMetadata` with appropriate `ActivityType` and `EventSeverity`.
- `HealthAlert` instances can be stored/queried by encoding them as:
  - `EventWithMetadata` payload fields (e.g., `application = "device-health"`, `window_title = message`).
  - Additional details in structured metadata maps.

---

## 3. Extension Plugin Contracts

Device Health is implemented as an **extension plugin** over the DCMAAR browser extension framework.

### 3.1 `DeviceHealthPlugin`

Implements the shared `ExtensionPlugin` interface:

```ts
import type { ExtensionPlugin } from '@dcmaar/plugin-extension';

export interface DeviceHealthPlugin extends ExtensionPlugin {
  // Called by the host runtime to start collection
  start(): Promise<void>;

  // Called to stop collection and release resources
  stop(): Promise<void>;

  // Optional live status for UI/debug panels
  getStatus?(): Promise<{
    deviceId: string;
    lastHeartbeatAt?: string;
    lastMetricAt?: string;
    activeMetricTypes: string[];
  }>;
}
```

The concrete implementation:

- Reads `HealthConfig` from extension storage / sync.
- Periodically collects `DeviceHealthMetric` values.
- Emits those metrics via `@dcmaar/connectors`, which serialize into `dcmaar.v1.MetricEnvelopeBatch` and send to the DCMAAR ingest endpoint.

---

## 4. Data Flow & Contracts

High-level flow:

1. **Config sync**
   - Control-plane (DCMAAR server or product-specific backend) writes `HealthConfig` bundles.
   - Bundles are delivered via DCMAAR sync contracts (`dcmaar.v1.sync`) to the extension.

2. **Metric collection**
   - `DeviceHealthPlugin` collects raw OS/browser metrics.
   - Converts them into `DeviceHealthMetric` and then into `dcmaar.v1.MetricWithLabels`.
   - Sends metrics via `MetricEnvelopeBatch` through the DCMAAR ingest API (`IngestService.SendMetricEnvelopes`).

3. **Server-side storage & query**
   - DCMAAR server stores metrics using shared storage contracts (`dcmaar.v1.storage`).
   - Device Health dashboards query via `QueryService` using `dcmaar.v1.query` contracts.

4. **Alerting**
   - Either:
     - Extension-side logic converts metrics into `HealthAlert` and emits `EventEnvelopeBatch`, or
     - Server-side analytics create alerts and emit `EventEnvelopeBatch` on behalf of Device Health.
   - Alerts are surfaced in the Device Health UI via query APIs.

---

## 5. Guidelines

- **Do not** introduce Device Health–specific fields directly into `dcmaar.v1` protos.
- Prefer **labels and metadata** for Device Health context when using shared telemetry types.
- Keep Device Health TS interfaces (`DeviceHealthMetric`, `HealthConfig`, `HealthAlert`) small and well-documented; they should be easy to translate into protobufs later if needed.
- When adding new capabilities (e.g., per-process health, GPU metrics), first check whether existing DCMAAR metric/event types can model the data; only then extend product-level types as necessary.
