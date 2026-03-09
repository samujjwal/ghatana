# Plugin Interface Migration Guide

## Problem

The migrated plugins from the old `ghatana` repository use an **old Plugin interface** that differs from the **new Plugin interface** in `ghatana-new`.

### Old Interface (from original ghatana repo):

```java
public interface Plugin {
    String getPluginId();
    String getPluginName();
    String getVersion();
    Promise<Void> initialize(PluginContext context);
    Promise<Void> start();
    Promise<Void> stop();
    Promise<HealthStatus> healthCheck(); // Different HealthStatus type
}
```

### New Interface (ghatana-new):

```java
public interface Plugin {
    PluginMetadata metadata();  // NEW: Replaces getPluginId/getName/getVersion
    PluginState getState();     // NEW: Required
    Promise<Void> initialize(Map<String, Object> config);  // Changed parameter
    Promise<Void> start();
    Promise<Void> stop();
    Promise<Void> shutdown();   // NEW: Required
    Promise<HealthStatus> healthCheck(); // Different HealthStatus type
}
```

## Changes Required

### 1. Replace Individual ID/Name/Version with metadata()

**Old:**
```java
@Override
public String getPluginId() {
    return "compliance-plugin";
}

@Override
public String getPluginName() {
    return "Compliance Plugin";
}

@Override
public String getVersion() {
    return "1.0.0";
}
```

**New:**
```java
private static final PluginMetadata METADATA = PluginMetadata.builder()
    .id("compliance-plugin")
    .name("Compliance Plugin")
    .version("1.0.0")
    .vendor("Ghatana")
    .description("Regulatory compliance and governance")
    .type(PluginMetadata.PluginType.PROCESSING)
    .build();

@Override
public PluginMetadata metadata() {
    return METADATA;
}
```

### 2. Add getState() Method

**New:**
```java
private volatile PluginState state = PluginState.UNLOADED;

@Override
public PluginState getState() {
    return state;
}
```

### 3. Update initialize() Parameter

**Old:**
```java
@Override
public Promise<Void> initialize(PluginContext context) {
    logger.info("Initializing");
    // ...
    return Promise.complete();
}
```

**New:**
```java
@Override
public Promise<Void> initialize(Map<String, Object> config) {
    logger.info("Initializing");
    state = PluginState.INITIALIZED;
    // ...
    return Promise.complete();
}
```

### 4. Add shutdown() Method

**New:**
```java
@Override
public Promise<Void> shutdown() {
    state = PluginState.STOPPED;
    logger.info("Plugin shutdown");
    // Clean up resources
    return Promise.complete();
}
```

### 5. Update State Transitions

Add state changes in lifecycle methods:

```java
@Override
public Promise<Void> start() {
    state = PluginState.STARTED;
    running = true;
    logger.info("Plugin started");
    return Promise.complete();
}

@Override
public Promise<Void> stop() {
    state = PluginState.STOPPED;
    running = false;
    logger.info("Plugin stopped");
    return Promise.complete();
}
```

### 6. Update healthCheck() Return Type

**Old:**
```java
@Override
public Promise<HealthStatus> healthCheck() {
    return Promise.of(new HealthStatus(
        "compliance-plugin",
        running ? "HEALTHY" : "UNHEALTHY",
        running ? "Plugin is running" : "Plugin is not running"
    ));
}
```

**New:**
```java
@Override
public Promise<Plugin.HealthStatus> healthCheck() {
    return Promise.of(
        running 
            ? Plugin.HealthStatus.ok("Plugin is running")
            : Plugin.HealthStatus.error("Plugin is not running")
    );
}
```

## Affected Files

All migrated plugins need these changes:

### Data Cloud Plugins:
1. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/compliance/CompliancePlugin.java`
2. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/lineage/LineagePlugin.java`
3. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/RedisHotTierPlugin.java`
4. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/ColdTierArchivePlugin.java`
5. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/CoolTierStoragePlugin.java`
6. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/streaming/KafkaStreamingPlugin.java`
7. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/knowledgegraph/KnowledgeGraphPlugin.java`
8. `/products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/vector/vector/VectorMemoryPlugin.java`
9. Any other plugins implementing the Plugin interface directly

## PluginMetadata.Builder Pattern

```java
import com.ghatana.datacloud.spi.PluginMetadata;
import com.ghatana.datacloud.spi.Plugin.PluginState;

private static final PluginMetadata METADATA = PluginMetadata.builder()
    .id("unique-plugin-id")
    .name("Human Readable Plugin Name")
    .version("1.0.0")
    .vendor("Ghatana")
    .description("What this plugin does")
    .type(PluginMetadata.PluginType.STORAGE)  // or STREAMING, PROCESSING, etc.
    .capability("feature-1")
    .capability("feature-2")
    .build();
```

## Plugin Types

Choose appropriate type from:
- `STORAGE` - Database, object store backends
- `STREAMING` - Real-time streaming connectors (Kafka)
- `ROUTING` - Event routing and transformation
- `ARCHIVE` - Long-term archive storage (S3, Glacier)
- `PROCESSING` - Data processing and enrichment
- `OBSERVABILITY` - Monitoring
- `ANALYTICS` - Analytics and reporting (Trino)
- `AI_ML` - AI/ML and cognitive services (Vector search, Knowledge graph)
- `INTEGRATION` - External system integration
- `UNKNOWN` - Fallback

## Implementation Order

1. **First**: Fix simple enterprise plugins (Compliance, Lineage) - they're simpler
2. **Second**: Fix storage plugins (Redis, S3, Iceberg)
3. **Third**: Fix knowledge graph and vector plugins
4. **Fourth**: Fix streaming and analytics plugins

## Example Full Migration

See [CompliancePlugin.java](products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/compliance/CompliancePlugin.java) (to be updated) for a complete example after migration.

