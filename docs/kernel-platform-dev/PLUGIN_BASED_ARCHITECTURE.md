# Plugin-Based Kernel Architecture

## Overview

The Ghatana Kernel Platform uses a **plugin-based architecture** that eliminates tight coupling between products and the kernel. This approach allows products to register their capabilities dynamically without requiring kernel modifications.

## Architecture

```
Products (FlashIt, PHR, Finance, Aura)
    ↓ ProductPlugin Interface
Plugin Registry (Dynamic Registration)
    ↓ KernelCapability Interface
Kernel Platform (Generic Capabilities)
    ↓ Infrastructure Services
Core Infrastructure (Data-Cloud, AEP, Shared Libraries)
```

## Key Components

### 1. KernelCapability (Generic)
- **Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/capability/KernelCapability.java`
- **Purpose**: Generic capability definitions without product knowledge
- **Features**: 
  - Core capabilities (DATA_STORAGE, USER_AUTHENTICATION, API_FRAMEWORK, etc.)
  - Metadata-driven configuration
  - Service dependency specification

### 2. ProductPlugin Interface
- **Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/ProductPlugin.java`
- **Purpose**: Interface for products to register capabilities
- **Features**:
  - Dynamic capability registration
  - Dependency declaration
  - Lifecycle management (initialize, start, stop, shutdown)
  - Extension, operator, and workflow registration

### 3. PluginRegistry
- **Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/PluginRegistry.java`
- **Purpose**: Dynamic plugin management
- **Features**:
  - Plugin registration and discovery
  - Capability registration
  - Dependency validation
  - Plugin lifecycle management

### 4. PluginLoader
- **Location**: `platform/java/kernel/src/main/java/com/ghatana/kernel/loader/PluginLoader.java`
- **Purpose**: Dynamic plugin loading from JAR files
- **Features**:
  - Directory scanning for plugin JARs
  - ServiceLoader-based plugin discovery
  - Hot loading and unloading
  - Plugin reloading

## Product Implementations

### FlashIt Plugin
- **Location**: `products/flashit/src/main/java/com/ghatana/flashit/plugin/FlashItProductPlugin.java`
- **Capabilities**:
  - `moment.capture` - Multimedia moment capture
  - `reflection.engine` - AI-powered reflection generation
  - `context.search` - Semantic search across moments

### PHR Plugin
- **Location**: `products/phr/src/main/java/com/ghatana/phr/plugin/PHRProductPlugin.java`
- **Capabilities**:
  - `patient.records` - Healthcare patient records
  - `consent.management` - Healthcare consent management
  - `fhir.interop` - FHIR R4 interoperability
  - `appointment.scheduling` - Appointment scheduling
  - `medication.management` - Medication management

### Finance Plugin
- **Location**: `products/finance/src/main/java/com/ghatana/finance/plugin/FinanceProductPlugin.java`
- **Capabilities**:
  - `trade.processing` - High-frequency trade processing
  - `risk.management` - Real-time risk assessment
  - `compliance.checking` - Financial compliance monitoring
  - `portfolio.management` - Investment portfolio management
  - `market.data` - Real-time market data processing

## Benefits

### 1. Loose Coupling
- **Kernel knows nothing about products**
- **Products know only about kernel interfaces**
- **Easy to add/remove products without kernel changes**

### 2. Dynamic Extensibility
- **Plugins can be loaded/unloaded at runtime**
- **New products can be added without redeployment**
- **Capability discovery happens dynamically**

### 3. Clear Boundaries
- **Product-specific logic stays in products**
- **Kernel provides generic capabilities**
- **Clean separation of concerns**

### 4. Easy Testing
- **Products can be tested independently**
- **Kernel can be tested with mock plugins**
- **Isolated development environments**

### 5. Scalability
- **Products can be deployed independently**
- **Resource allocation per product**
- **Independent scaling decisions**

## Usage Examples

### Creating a New Product Plugin

```java
public class MyProductPlugin implements ProductPlugin {
    @Override
    public String getProductId() {
        return "my-product";
    }

    @Override
    public Set<KernelCapability> getDeclaredCapabilities() {
        return Set.of(
            new KernelCapability(
                "my.feature", "My Feature", "Product-specific feature",
                CapabilityType.DATA_MANAGEMENT, Map.of(
                    "required_services", "my_service"
                )
            )
        );
    }

    @Override
    public Set<KernelDependency> getRequiredDependencies() {
        return Set.of(
            new KernelDependency("data.storage", "1.0.0", DependencyType.CAPABILITY, false)
        );
    }

    // Implement other required methods...
}
```

### Loading Plugins

```java
// Create plugin registry
PluginRegistry registry = new PluginRegistry(capabilityRegistry, serviceRegistry);

// Create plugin loader
PluginLoader loader = new PluginLoader(registry, "/path/to/plugins");

// Load all plugins
loader.loadPlugins();

// Start all plugins
registry.startAllPlugins();
```

### Accessing Plugin Capabilities

```java
// Get plugin by ID
Optional<ProductPlugin> plugin = registry.getPlugin("flashit");

// Get capabilities for a product
Set<KernelCapability> capabilities = registry.getPluginCapabilities("flashit");

// Find plugins by capability
Set<ProductPlugin> plugins = registry.getPluginsByCapability(
    KernelCapability.Core.DATA_STORAGE
);
```

## Migration from Hardcoded Approach

### Before (Tight Coupling)
```java
// Hardcoded in kernel
public static final KernelCapability FLASHIT_CAPTURE = new KernelCapability(...);

// Product module
public Set<KernelCapability> getCapabilities() {
    return Set.of(KernelCapability.Products.FLASHIT_CAPTURE);
}
```

### After (Loose Coupling)
```java
// Product plugin
public Set<KernelCapability> getDeclaredCapabilities() {
    return Set.of(new KernelCapability("moment.capture", ...));
}

// Kernel module
public Set<KernelCapability> getCapabilities() {
    return Set.of(KernelCapability.Core.DATA_STORAGE); // Only core capabilities
}
```

## Plugin Deployment

### 1. Build Plugin JAR
```bash
mvn clean package -DskipTests
```

### 2. Create Service Provider Configuration
Create `META-INF/services/com.ghatana.kernel.plugin.ProductPlugin`:
```
com.ghatana.flashit.plugin.FlashItProductPlugin
```

### 3. Deploy to Plugin Directory
```bash
cp target/flashit-plugin-1.0.0.jar /path/to/plugins/
```

### 4. Load Plugin
```java
PluginLoader loader = new PluginLoader(registry, "/path/to/plugins");
loader.loadPlugins();
```

## Comparison with Hardcoded Approach

| Aspect | Hardcoded (Before) | Plugin-Based (After) |
|---------|-------------------|----------------------|
| **Coupling** | Tight (kernel knows products) | Loose (kernel knows interfaces) |
| **Extensibility** | Requires kernel changes | Dynamic plugin loading |
| **Maintenance** | High (kernel changes for products) | Low (independent product development) |
| **Testing** | Complex (integrated) | Simple (isolated) |
| **Deployment** | Monolithic | Independent |
| **Scalability** | Limited | Per-product scaling |

## Best Practices

### 1. Plugin Design
- Keep plugins focused on product-specific functionality
- Use kernel capabilities for common functionality
- Implement proper error handling and logging
- Follow plugin lifecycle best practices

### 2. Capability Design
- Keep capabilities generic and reusable
- Use metadata for configuration
- Specify required services clearly
- Document capability contracts

### 3. Dependency Management
- Declare all dependencies explicitly
- Use version constraints appropriately
- Mark optional dependencies correctly
- Validate dependencies at plugin registration

### 4. Error Handling
- Handle plugin loading failures gracefully
- Provide clear error messages
- Implement proper cleanup on failures
- Log plugin lifecycle events

## Future Enhancements

### 1. Plugin Marketplace
- Centralized plugin repository
- Version management and updates
- Plugin dependency resolution
- Automated plugin discovery

### 2. Plugin Sandboxing
- Isolated plugin execution environments
- Resource limits and quotas
- Security policies and enforcement
- Plugin communication channels

### 3. Hot Reloading
- Runtime plugin updates without restart
- State preservation during reloads
- Graceful migration between versions
- Rollback capabilities

### 4. Plugin Analytics
- Plugin usage metrics
- Performance monitoring
- Resource utilization tracking
- Plugin health monitoring

This plugin-based architecture provides a flexible, extensible foundation for the Ghatana Kernel Platform while maintaining clean separation between the kernel and product-specific functionality.
