# platform:java:tool-runtime

Canonical package: `com.ghatana.platform.toolruntime.*`

## Purpose

`platform:java:tool-runtime` provides the shared runtime for sandboxed tool execution, monitoring, approval workflows, and policy-aware orchestration for agent-invoked tools.

## Dependencies

- `platform:java:core`, `platform:java:domain`, `platform:java:agent-core`, `platform:java:policy-as-code`, and `platform:java:observability`
- `platform:java:database` plus PostgreSQL and HikariCP for durable execution and approval state
- ActiveJ promise support and Jackson serialization for async runtime flows

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:tool-runtime"))
}
```

Use the shared tool execution and sandbox abstractions when building agent-facing tool handlers, approval flows, or monitoring adapters.

## Public API Surface

- Tool execution, sandbox, and monitoring contracts under `com.ghatana.platform.toolruntime.*`
- Approval, change-management, MCP, and recertification support packages
- Shared runtime primitives for policy-aware tool orchestration