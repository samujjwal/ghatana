# Canonical Plugin Contract

This document defines the canonical contract that all Ghatana platform plugins must implement.

## Overview

All plugins in the `platform-plugins` directory must extend the canonical `Plugin` interface from `com.ghatana.platform.plugin.Plugin` in the `platform-kernel/kernel-plugin` module. This ensures consistent lifecycle management, health checking, and capability discovery across all plugins.

## Required Interface

All plugins must implement the following interface:

```java
package com.ghatana.platform.plugin;

import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

public interface Plugin {
    @NotNull
    PluginMetadata metadata();

    @NotNull
    PluginState getState();

    @NotNull
    Promise<Void> initialize(@NotNull PluginContext context);

    @NotNull
    Promise<Void> start();

    @NotNull
    Promise<Void> stop();

    @NotNull
    default Promise<Void> shutdown() {
        return stop();
    }

    @NotNull
    default Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.ok());
    }

    @NotNull
    default Set<PluginCapability> getCapabilities() {
        return Set.of();
    }

    @NotNull
    default <T extends PluginCapability> Optional<T> getCapability(@NotNull Class<T> capabilityType) {
        return getCapabilities().stream()
            .filter(capabilityType::isInstance)
            .map(capabilityType::cast)
            .findFirst();
    }
}
```

## Required Methods

### metadata()
- **Purpose**: Returns plugin metadata including name, version, description, and dependencies
- **Required**: Yes
- **Implementation**: Must return a valid `PluginMetadata` object

### getState()
- **Purpose**: Returns the current state of the plugin (INITIALIZED, STARTING, RUNNING, STOPPING, STOPPED, FAILED)
- **Required**: Yes
- **Implementation**: Must track and return the correct state based on lifecycle

### initialize(PluginContext context)
- **Purpose**: Initialize the plugin with configuration and dependencies
- **Required**: Yes
- **Implementation**: Must complete initialization before returning the Promise

### start()
- **Purpose**: Start the plugin and begin accepting requests
- **Required**: Yes
- **Implementation**: Must complete startup before returning the Promise

### stop()
- **Purpose**: Stop the plugin gracefully
- **Required**: Yes
- **Implementation**: Must complete graceful shutdown before returning the Promise

### shutdown()
- **Purpose**: Final cleanup and resource release
- **Required**: No (default implementation delegates to stop())
- **Implementation**: Override if additional cleanup is needed after stop()

### healthCheck()
- **Purpose**: Return the current health status of the plugin
- **Required**: No (default returns OK)
- **Implementation**: Override to provide custom health checks

### getCapabilities()
- **Purpose**: Return the set of capabilities provided by this plugin
- **Required**: No (default returns empty set)
- **Implementation**: Override if plugin provides capabilities

### getCapability(Class<T> capabilityType)
- **Purpose**: Retrieve a specific capability implementation
- **Required**: No (default implementation searches capabilities)
- **Implementation**: Override for custom capability lookup

## Plugin Metadata

All plugins must provide valid metadata:

```java
public record PluginMetadata(
    String pluginId,
    String name,
    String version,
    String description,
    Set<String> dependencies,
    Map<String, Object> config
) {}
```

### Required Metadata Fields
- **pluginId**: Unique identifier for the plugin (e.g., "compliance", "audit-trail")
- **name**: Human-readable name (e.g., "Compliance Plugin")
- **version**: Semantic version (e.g., "1.0.0")
- **description**: Brief description of plugin purpose
- **dependencies**: Set of plugin IDs this plugin depends on
- **config**: Configuration schema for the plugin

## Plugin State

Plugins must track and report their state accurately:

```java
public enum PluginState {
    INITIALIZED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
```

### State Transitions
- `INITIALIZED` → `STARTING` → `RUNNING` (normal startup)
- `RUNNING` → `STOPPING` → `STOPPED` (normal shutdown)
- Any state → `FAILED` (on error)

## Plugin Capabilities

Plugins may optionally expose capabilities for plugin-to-plugin communication:

```java
public interface PluginCapability {
    String capabilityId();
    String version();
}
```

### Capability Examples
- `GovernancePlugin` - Policy evaluation and enforcement
- `StoragePlugin` - Data storage and retrieval
- `StreamingPlugin` - Event streaming and subscription
- `AIModelPlugin` - AI model inference and management

## Plugin Contracts

Plugins may define typed contracts for inter-plugin communication:

```java
public interface PluginContract<Req, Res> {
    @NotNull
    String contractId();

    @NotNull
    default String schemaVersion() {
        return "1.0.0";
    }

    @NotNull
    Class<Req> requestType();

    @NotNull
    Class<Res> responseType();

    @NotNull
    default PluginInteractionPolicy policy() {
        return PluginInteractionPolicy.allowAll();
    }
}
```

## Implementation Guidelines

### 1. Package Structure
```
platform-plugins/
├── plugin-{name}/
│   ├── src/main/java/com/ghatana/plugin/{name}/
│   │   ├── {Name}Plugin.java          # Main plugin interface
│   │   └── impl/
│   │       └── Standard{Name}Plugin.java  # Default implementation
│   ├── src/test/java/com/ghatana/plugin/{name}/
│   │   └── {Name}PluginTest.java
│   ├── build.gradle.kts
│   └── README.md
```

### 2. Naming Conventions
- Plugin interface: `{Name}Plugin` (e.g., `CompliancePlugin`)
- Implementation: `Standard{Name}Plugin` or `Durable{Name}Plugin`
- Package: `com.ghatana.plugin.{name}` (lowercase)

### 3. Dependency Management
- All plugins must depend on `platform-kernel:kernel-plugin`
- Plugins should declare dependencies on other plugins in metadata
- Use `PluginContext` to resolve dependencies at runtime

### 4. Error Handling
- All Promise-returning methods must handle errors properly
- Failed operations should transition state to `FAILED`
- Health checks should reflect error state

### 5. Multi-Tenancy
- Plugins must be tenant-aware where applicable
- Use tenant context from `PluginContext` for tenant-scoped operations
- Ensure proper isolation between tenants

### 6. Observability
- Plugins should emit metrics for key operations
- Health checks should be comprehensive
- Consider integrating with `core-observability` plugin

## Testing Requirements

All plugins must include tests for:
1. Lifecycle transitions (initialize → start → stop → shutdown)
2. Health check behavior
3. Capability registration and retrieval
4. Error handling and state transitions
5. Multi-tenancy (if applicable)

## Existing Plugins

The following plugins in `platform-plugins` follow this contract:

- **plugin-audit-trail**: Audit trail and event logging
- **plugin-compliance**: Compliance rule engine
- **plugin-consent**: Consent management
- **plugin-fraud-detection**: Fraud detection
- **plugin-human-approval**: Human approval workflows
- **plugin-ledger**: Immutable ledger
- **plugin-notification**: Notification delivery
- **plugin-risk-management**: Risk assessment

## References

- Canonical Plugin interface: `platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/Plugin.java`
- Plugin Contract: `platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/PluginContract.java`
- Plugin Metadata: `platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/PluginMetadata.java`
- Plugin State: `platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/PluginState.java`
