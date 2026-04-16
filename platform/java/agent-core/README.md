# platform:java:agent-core

Canonical packages: `com.ghatana.agent.*`, `com.ghatana.core.template.*`

## Purpose

`platform:java:agent-core` provides the shared agent contracts, typed execution model, registry abstractions, coordination primitives, and supporting framework types used across products that implement deterministic, probabilistic, planning, reactive, composite, and related agent patterns.

## Dependencies

- `platform:java:core` for common platform types
- `platform:java:observability` for agent diagnostics and reporting hooks
- `platform:java:ai-integration` and `platform:java:governance` for runtime integrations behind the core contracts
- ActiveJ and Jackson bundles for async flows and configuration materialization

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:agent-core"))
}
```

Implement a typed agent against the shared contract:

```java
public final class ExampleAgent implements TypedAgent<ExampleInput, ExampleOutput> {
    @Override
    public Promise<AgentResult<ExampleOutput>> process(AgentContext context, ExampleInput input) {
        return Promise.of(AgentResult.success(new ExampleOutput("ok")));
    }
}
```

## Public API Surface

- `TypedAgent`, `AgentResult`, `AgentDescriptor`, and agent taxonomy enums
- Registry, catalog, workflow, memory, and SPI packages under `com.ghatana.agent.*`
- Template support under `com.ghatana.core.template.*`