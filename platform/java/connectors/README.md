# platform/java/connectors

Platform connectors module. Provides lifecycle-managed source and sink connectors for external systems such as Kafka, files, and in-memory ingest paths.

## Overview

This module standardizes connector behavior across shared services and products:

- Promise-based lifecycle operations via `Connector`
- Capability split between `EventSource` and `EventSink`
- Shared status and metrics model
- Connector registry and base implementations
- Kafka and file-backed adapters in `impl/`

## Main Types

- `Connector`: lifecycle, health, metrics, and capability contract
- `BaseConnector`: shared state handling for implementations
- `ConnectorRegistry`: connector discovery/lookup
- `EventSource`: input-side polling/streaming contract
- `EventSink`: output-side publish contract
- `IngestEvent`: canonical ingest event shape

## Usage

```java
Connector connector = new KafkaConnector("events-ingress");

connector.initialize(config)
    .then(() -> connector.start())
    .whenResult(() -> log.info("Connector running"));
```

## Error Handling

- Expected connector outcomes should be reflected in typed status, metrics, or result payloads.
- Initialization, connectivity, and runtime delivery failures must be surfaced through failed Promises.
- Health checks should return actionable health information instead of silently degrading to success.

## Implementation Guidance

- Keep concrete adapters in implementation packages; expose stable contracts through the top-level interfaces.
- Maintain non-blocking behavior around ActiveJ-facing entry points.
- Release external resources in `stop()` and keep lifecycle methods idempotent.

## Current Implementations

- `KafkaConnector`
- `KafkaEventSource`
- `KafkaEventSink`
- `FileConnector`
- `InMemoryEventSource`
- `LoggingEventSink`