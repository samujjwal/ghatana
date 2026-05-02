# core-observability

Platform-level observability base for Ghatana plugins.

## Purpose

Provides `PluginObservability` — an abstract base class that gives every plugin:

- Named OpenTelemetry **spans** with automatic correlation ID and tenant ID propagation
- `Counter` metrics for operation counts and error rates
- Consistent span naming (`<pluginId>/<operation>`)

## Usage

Extend `PluginObservability` in your plugin implementation:

```java
public class MyPlugin extends PluginObservability implements CompliancePlugin {

    public MyPlugin() {
        super("compliance");
    }

    @Override
    public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext ctx) {
        return withSpan("evaluate", Map.of("ruleSetId", ruleSetId), () ->
            Promise.of(doEvaluate(ruleSetId, ctx)));
    }
}
```

## Dependencies

- `platform-kernel:kernel-core` (for `CorrelationIdContext`)
- `opentelemetry-api` and `opentelemetry-context`

Products do not import this module directly — it is consumed by platform plugins.
