# platform/java/config

Platform configuration module. Provides source composition, typed property lookup, interpolation, validation, reload watching, and the runtime configuration engine used by shared services and platform modules.

## Overview

Use this module when configuration must stay explicit, testable, and observable.

- Source composition via `ConfigManager`
- Multiple sources: environment, system properties, YAML, files, in-memory
- Typed access through `ConfigSource`
- Validation through `validation/ConfigValidator`
- Variable interpolation through `interpolation/VariableResolver`
- Runtime config state in `com.ghatana.config.runtime.engine`

## Source Precedence

`ConfigManager` resolves sources in insertion order. Add higher-priority sources first.

Typical order:

1. `EnvironmentConfigSource`
2. `SystemPropertiesConfigSource`
3. `YamlConfigSource` or `FileConfigSource`
4. `MemoryConfigSource` for tests and overrides

## Usage

```java
ConfigManager config = new ConfigManager("aep")
    .addSource(new EnvironmentConfigSource())
    .addSource(new SystemPropertiesConfigSource())
    .addSource(new YamlConfigSource("config/application.yaml"));

int port = config.getInt("http.port").orElse(8080);
```

## Error Handling

- Missing optional values should surface as `Optional.empty()`.
- Expected validation failures should use the canonical validation/result types from `platform:java:core`.
- Runtime parsing or I/O failures must be surfaced explicitly; do not swallow malformed configuration.

## Main Types

- `ConfigManager`: source aggregation and precedence
- `ConfigSource`: typed configuration lookup contract
- `AppConfig`: strongly typed application config model
- `ConfigRegistry`: named config registration and lookup
- `ConfigReloadWatcher`: reload/change observation
- `ConfigurationEngine`: runtime configuration engine

## Testing Guidance

- Prefer `MemoryConfigSource` for unit tests.
- Add regression coverage for source precedence and validation failures.
- Avoid filesystem coupling when the behavior under test is merge or validation logic.