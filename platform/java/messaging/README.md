# platform:java:messaging

Canonical package: `com.ghatana.platform.messaging.*`

## Purpose

`platform:java:messaging` provides shared messaging and connector abstractions for platform and product services, covering event-log patterns, broker integration points, and unified infrastructure for Kafka, SQS, RabbitMQ, and related messaging adapters.

## Dependencies

- `platform:java:core`, `platform:java:domain`, `platform:java:governance`, `platform:java:observability`, and `platform:java:http`
- ActiveJ for async flows
- PostgreSQL and HikariCP for durable messaging-related persistence support
- Jackson for message serialization and timestamp handling
- Optional broker integrations for Kafka, AWS SDK messaging, and RabbitMQ

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:messaging"))
}
```

Use the module’s shared abstractions when building product-owned connectors or durable event-processing services rather than introducing broker-specific contracts into product domain code.

## Public API Surface

- Messaging abstractions under `com.ghatana.platform.messaging.*`
- Shared event-log and connector infrastructure aligned to platform domain contracts
- Async broker integration support for higher-level product adapters