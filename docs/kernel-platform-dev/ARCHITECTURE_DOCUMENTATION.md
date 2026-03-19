# Ghatana Kernel Platform - Architecture Documentation

## Executive Summary

The Ghatana Kernel Platform is a production-grade, plugin-based architecture designed for building scalable healthcare and financial applications. This document provides comprehensive architectural guidance for developers, operators, and architects.

## Table of Contents

1. [System Overview](#system-overview)
2. [Architectural Principles](#architectural-principles)
3. [Core Components](#core-components)
4. [Module Architecture](#module-architecture)
5. [Extension Architecture](#extension-architecture)
6. [Plugin Architecture](#plugin-architecture)
7. [Dependency Management](#dependency-management)
8. [Lifecycle Management](#lifecycle-management)
9. [Health Monitoring](#health-monitoring)
10. [Tenant Isolation](#tenant-isolation)
11. [Data Flow](#data-flow)
12. [Deployment Architecture](#deployment-architecture)
13. [Security Model](#security-model)
14. [Performance Considerations](#performance-considerations)

---

## System Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Ghatana Kernel Platform                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     Kernel Core Layer                                │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │  │   Registry   │  │   Context    │  │   Config     │              │  │
│  │  │              │  │              │  │   Resolver   │              │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │  │
│  │  │   Health     │  │   Event      │  │   Tenant     │              │  │
│  │  │   Monitor    │  │   System     │  │   Context    │              │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     Product Module Layer                             │  │
│  │                                                                     │  │
│  │   ┌─────────────┐         ┌─────────────┐                          │  │
│  │   │    PHR      │         │   Finance   │                          │  │
│  │   │   Module    │         │   Module    │                          │  │
│  │   └─────────────┘         └─────────────┘                          │  │
│  │        │                         │                                  │  │
│  │   9 Services               8 Services                              │  │
│  │                                                                     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     Extension Layer                                  │  │
│  │                                                                     │  │
│  │  ┌───────────────────┐  ┌───────────────────┐                      │  │
│  │  │Healthcare Consent │  │  Dual Calendar    │                      │  │
│  │  │  (Nepal 2081)     │  │   (AD/BS)         │                      │  │
│  │  └───────────────────┘  └───────────────────┘                      │  │
│  │  ┌───────────────────┐  ┌───────────────────┐                      │  │
│  │  │ Risk Management   │  │   Compliance      │                      │  │
│  │  │  (Real-time)      │  │  (SOX/PCI-DSS)    │                      │  │
│  │  └───────────────────┘  └───────────────────┘                      │  │
│  │  ┌───────────────────┐                                              │  │
│  │  │ FHIR Interop      │                                              │  │
│  │  │  (R4 Plugin)      │                                              │  │
│  │  └───────────────────┘                                              │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                     Adapter Layer                                    │  │
│  │                                                                     │  │
│  │   ┌─────────────────┐    ┌─────────────────┐                         │  │
│  │   │  Data-Cloud     │    │     AEP         │                         │  │
│  │   │   Adapter       │    │   Adapter       │                         │  │
│  │   │ (Promise wrap)  │    │ (Promise wrap)  │                         │  │
│  │   └─────────────────┘    └─────────────────┘                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Architectural Principles

### 1. Plugin-Based Extensibility

- **No Product Coupling**: Kernel core has zero knowledge of specific products
- **Generic Capabilities**: All capabilities are product-agnostic (DATA_STORAGE, not PHR_STORAGE)
- **Metadata-Driven**: Product mapping happens through capability metadata, not hardcoded logic

### 2. ActiveJ-Native Design

- **Promise-Based Async**: All async operations use `io.activej.promise.Promise`
- **Event Loop Integration**: Full integration with ActiveJ Eventloop for non-blocking I/O
- **No CompletableFuture**: Strictly no `java.util.concurrent.CompletableFuture` in kernel core

### 3. Strong Contracts

- **Interface Segregation**: Each component has a focused, minimal interface
- **Version Compatibility**: Semantic versioning for all components
- **Dependency Declaration**: Explicit dependency graphs with validation

### 4. Production-Grade Quality

- **Atomic State Management**: All state transitions use atomic operations
- **Health Monitoring**: Every component reports granular health status
- **Graceful Degradation**: System continues operating with reduced functionality

### 5. Tenant Isolation

- **Multi-Tenancy First**: Every operation is tenant-scoped
- **Feature Gating**: Per-tenant capability enablement
- **Security Boundaries**: Strict tenant data isolation

---

## Core Components

### KernelRegistry

**Responsibilities:**
- Module and plugin registration/unregistration
- Dependency resolution and validation
- Capability discovery and management
- Lifecycle orchestration (start/stop ordering)

**Design Pattern:** Registry + Facade

**Key Algorithms:**
- **Topological Sort**: Kahn's algorithm for dependency ordering
- **Cycle Detection**: DFS-based cycle detection (planned)
- **Health Aggregation**: Hierarchical health status rollup

### KernelContext

**Responsibilities:**
- Dependency lookup (typed and named)
- Event publishing and subscription
- Tenant context management
- Configuration resolution

**Design Pattern:** Service Locator + Mediator

**Thread Safety:**
- All operations are thread-safe
- Event handlers use CopyOnWriteArrayList for lock-free reads
- Dependencies use ConcurrentHashMap

### KernelDescriptor

**Responsibilities:**
- Component metadata (id, name, version, type)
- Capability declarations
- Dependency specifications
- Lifecycle policies
- Resource requirements

**Design Pattern:** Builder + Immutable Object

**Validation:**
- Semantic version format checking
- Unique identifier enforcement
- Required field validation

---

## Module Architecture

### KernelModule Lifecycle

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│  NEW    │ --> │ INITIAL │ --> │ STARTED │ --> │ STOPPED │
│         │     │  IZED   │     │         │     │         │
└─────────┘     └─────────┘     └─────────┘     └─────────┘
     │               │               │               │
     │ initialize()  │   start()     │    stop()     │
     │               │               │               │
     v               v               v               v
  Validates      Validates       Runs           Cleans up
  dependencies   state           services       resources
```

### Module Composition Pattern

Product modules use the **Facade Pattern** to compose multiple internal services:

```java
public class PhrKernelModule implements KernelModule {
    private final List<PhrService> services = new ArrayList<>();
    
    private interface PhrService {
        String getName();
        Promise<Void> start();
        Promise<Void> stop();
        boolean isHealthy();
    }
    
    // Each service implements this interface
    // Module delegates lifecycle calls to all services
}
```

### Service Base Class Pattern

```java
private static abstract class AbstractPhrService implements PhrService {
    protected final AtomicBoolean running = new AtomicBoolean(false);
    
    @Override
    public Promise<Void> start() {
        if (running.compareAndSet(false, true)) {
            return doStart();
        }
        return Promise.complete();
    }
    
    protected abstract Promise<Void> doStart();
}
```

---

## Extension Architecture

### Extension Point Model

Extensions hook into module lifecycle events:

```
Module Initialization
      │
      v
┌─────────────────────┐
│ Extension Initialized│
│  (onModuleInitialized)│
└─────────────────────┘
      │
      v
Module Start
      │
      v
┌─────────────────────┐
│  Extension Started   │
│    (onModuleStarted) │
└─────────────────────┘
      │
      v
   ... running ...
      │
      v
Module Stop
      │
      v
┌─────────────────────┐
│   Extension Stopped  │
│    (onModuleStopped)   │
└─────────────────────┘
```

### Extension Compatibility

Extensions declare compatibility requirements:

```java
@Override
public boolean isCompatible(KernelModule hostModule) {
    return hostModule.getCapabilities().stream()
        .anyMatch(c -> c.getCapabilityId().equals("data.storage"));
}
```

### Extension Priority

Extensions are ordered by priority (higher = earlier):

- **Critical (200+)**: Risk management, compliance
- **High (100-199)**: Healthcare consent, security
- **Medium (50-99)**: Calendar support, utilities
- **Low (0-49)**: Optional enhancements

---

## Plugin Architecture

### Plugin vs Module Distinction

| Aspect | Module | Plugin |
|--------|--------|--------|
| Lifecycle | init → start → stop | install → init → start → stop → uninstall |
| Dynamic Loading | No | Yes |
| Contracts | Implicit | Explicit (exported/required) |
| Persistence | Always | Optional |
| Reload | No | Yes |

### Plugin Contract Model

```java
public interface KernelPlugin extends KernelModule {
    Set<String> getExportedContracts();  // Services provided
    Set<String> getRequiredContracts();  // Services needed
}
```

### Plugin Manifest

Contains metadata for discovery:
- Plugin ID and version
- Capability contributions
- Dependencies
- Required kernel version
- Tags for categorization

---

## Dependency Management

### Dependency Types

```java
public enum DependencyType {
    MODULE,           // Another kernel module
    CAPABILITY,       // Abstract capability
    EXTERNAL_SERVICE, // External system (FHIR, market data)
    FEATURE,          // Feature flag
    PLUGIN,           // Another plugin
    EXTENSION,        // Extension availability
    CONFIGURATION     // Config value
}
```

### Dependency Validation

1. **Registration Time**: Check all required dependencies exist
2. **Initialization Time**: Verify dependencies are initialized
3. **Runtime**: Lazy resolution with fallback to optional

### Dependency Resolution Strategy

```
registerModule(A)
      │
      ▼
validateDependencies(A)
      │
      ├── Check MODULE deps → Must exist in registry
      ├── Check CAPABILITY deps → Must be registered
      ├── Check EXTERNAL_SERVICE deps → Optional if marked
      └── Check others → Soft validation
      │
      ▼
Resolution Order: Topological sort using Kahn's algorithm
```

---

## Lifecycle Management

### Start Order Algorithm

```java
// Kahn's Algorithm for Topological Sorting
1. Build dependency graph
2. Calculate in-degree for each node
3. Queue nodes with in-degree = 0
4. Process queue:
   - Remove node from queue
   - Start module
   - Decrement in-degree of dependents
   - If dependent in-degree = 0, add to queue
```

### Stop Order

Reverse of start order (leaf nodes stop first):

```
Start:  A → B → C → D
Stop:   D → C → B → A
```

### Failure Handling

1. **Start Failure**: Rollback started modules in reverse order
2. **Dependency Failure**: Cascade failure to dependent modules
3. **Partial Failure**: Mark system as DEGRADED, continue with working components

---

## Health Monitoring

### Health Status Hierarchy

```
HealthStatus
├── Status: HEALTHY / DEGRADED / UNHEALTHY
├── Message: Human-readable description
└── Checks: Map<String, HealthCheck>
    ├── Check Name
    ├── Status
    ├── Message
    └── ResponseTimeMs
```

### Health Aggregation Rules

- ALL HEALTHY → HEALTHY
- ANY UNHEALTHY + some HEALTHY → DEGRADED
- ALL UNHEALTHY or CRITICAL FAILURE → UNHEALTHY

### Service Health Checks

Each service reports:
- **Running State**: Is service active?
- **Response Time**: How long did check take?
- **Custom Metrics**: Service-specific health indicators

---

## Tenant Isolation

### Tenant Context Model

```java
public class KernelTenantContext {
    String tenantId;              // Unique identifier
    TenantType type;              // SYSTEM, ENTERPRISE, PERSONAL
    Map<String, String> config;   // Tenant-specific config
    Set<String> enabledProducts;   // Enabled products for tenant
    SecurityContext security;       // Auth/authz context
    FeatureFlags features;        // Feature toggles
}
```

### Configuration Resolution Chain

```
1. Cross-product defaults
        ↓
2. Product-specific overrides
        ↓
3. Tenant-specific overrides
        ↓
4. User-specific overrides (if applicable)
```

### Tenant Scoping in Operations

All operations are tenant-scoped:

```java
// Implicit tenant from context
ConsentRecord record = consentExt.grantConsent(...);
// Uses context.getTenantContext() automatically

// Explicit tenant
KernelTenantContext tenant = context.getTenantContext("tenant-001");
```

---

## Data Flow

### Typical Request Flow

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  Client  │ --> │  Module  │ --> │ Extension│ --> │ Adapter  │
│  Request │     │  Service │     │  Logic   │     │  Layer   │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
                                              │
                                              v
                                         ┌──────────┐
                                         │ External │
                                         │ System   │
                                         │(Data-Cloud│
                                         │  / AEP)  │
                                         └──────────┘
```

### Event Flow

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Event     │  ->  │   Kernel    │  ->  │   Handler   │
│   Source    │      │   Context   │      │   Chain     │
└─────────────┘      └─────────────┘      └─────────────┘
                          │
                    publishEvent()
                          │
              ┌─────────────┼─────────────┐
              v             v             v
        ┌─────────┐   ┌─────────┐   ┌─────────┐
        │Handler 1│   │Handler 2│   │Handler 3│
        │(Audit)  │   │(Metrics)│   │(Notify) │
        └─────────┘   └─────────┘   └─────────┘
```

---

## Deployment Architecture

### Component Deployment

```
┌─────────────────────────────────────────────────────────┐
│                     JVM Instance                         │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │               Kernel Core                          │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐             │  │
│  │  │Registry │ │ Context │ │ Config  │             │  │
│  │  └─────────┘ └─────────┘ └─────────┘             │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌─────────────────────┐  ┌─────────────────────┐     │
│  │    PHR Module      │  │   Finance Module    │     │
│  │  ┌───────────────┐  │  │  ┌───────────────┐  │     │
│  │  │   Services    │  │  │  │   Services    │  │     │
│  │  │  ├─ patient   │  │  │  │  ├─ order     │  │     │
│  │  │  ├─ consent   │  │  │  │  ├─ risk     │  │     │
│  │  │  └─ ...       │  │  │  │  └─ ...       │  │     │
│  │  └───────────────┘  │  │  └───────────────┘  │     │
│  └─────────────────────┘  └─────────────────────┘     │
│                                                         │
│  ┌─────────────────────┐  ┌─────────────────────┐     │
│  │  PHR Extensions    │  │ Finance Extensions  │     │
│  │  ├─ Healthcare     │  │  ├─ Dual Calendar   │     │
│  │  │   Consent       │  │  ├─ Risk Mgmt       │     │
│  │  └─ FHIR Plugin    │  │  └─ Compliance      │     │
│  └─────────────────────┘  └─────────────────────┘     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### High Availability Deployment

```
┌──────────────────────────────────────────────────────────┐
│                      Load Balancer                        │
└──────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           v               v               v
      ┌─────────┐    ┌─────────┐    ┌─────────┐
      │ Kernel  │    │ Kernel  │    │ Kernel  │
      │ Node 1  │◄──►│ Node 2  │◄──►│ Node 3  │
      │ (Active)│    │ (Active)│    │ (Active)│
      └─────────┘    └─────────┘    └─────────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
                    ┌──────────────┐
                    │ Shared State │
                    │  (Consul/    │
                    │   etcd)      │
                    └──────────────┘
```

---

## Security Model

### Authentication Flow

```
┌─────────┐      ┌─────────────┐      ┌─────────────┐
│  User   │ -->  │ Auth Service│ -->  │  Kernel     │
│ Request │      │ (External)  │      │  Context    │
└─────────┘      └─────────────┘      └─────────────┘
                                              │
                                              v
                                       ┌─────────────┐
                                       │ Tenant      │
                                       │ Context     │
                                       │ with Security│
                                       └─────────────┘
```

### Authorization Model

- **Capability-based**: Operations require specific capabilities
- **Tenant-scoped**: All authorization is within tenant boundary
- **Audit Trail**: All security decisions are logged

### Data Isolation

- **Tenant Isolation**: Complete data separation between tenants
- **Product Isolation**: Cross-product data access controlled
- **Service Isolation**: Internal service boundaries enforced

---

## Performance Considerations

### Throughput Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Module Start | < 5s | All 9 PHR services |
| Health Check | < 100ms | Per-service check |
| Event Publish | < 1ms | To all handlers |
| Dependency Lookup | < 10μs | From cache |

### Scalability Patterns

1. **Horizontal Scaling**: Multiple kernel instances behind load balancer
2. **Event Loop per Core**: ActiveJ event loop per CPU core
3. **Non-blocking I/O**: All external calls use Promise-based async
4. **Connection Pooling**: Reuse connections to external services

### Caching Strategy

| Cache | Scope | TTL | Invalidation |
|-------|-------|-----|--------------|
| Capabilities | Global | None | On module change |
| Dependencies | Context | None | On context change |
| Config | Tenant | 5 min | On reload |
| Health | Module | 10s | On check |

### Memory Management

- **Immutable Objects**: All descriptors are immutable
- **Weak References**: Event handlers use weak refs where appropriate
- **Off-Heap**: Large data structures use off-heap storage (planned)

---

## Monitoring and Observability

### Metrics Collection

```
Kernel Metrics:
├── Module Metrics
│   ├── startup_time_ms
│   ├── service_count
│   └── health_check_duration_ms
├── Extension Metrics
│   ├── initialization_time_ms
│   └── contributed_capabilities
├── Registry Metrics
│   ├── registered_modules
│   ├── registered_plugins
│   └── dependency_resolution_time_ms
└── Context Metrics
    ├── event_publish_rate
    ├── dependency_lookup_rate
    └── tenant_context_switches
```

### Distributed Tracing

- **Trace Points**: Module boundaries, extension calls, adapter invocations
- **Correlation ID**: Passed through entire request chain
- **Span Context**: Captured in tenant context

---

## Development Guidelines

### Adding a New Module

1. Implement `KernelModule` interface
2. Define capabilities and dependencies
3. Implement lifecycle methods with Promise
4. Add health check implementation
5. Write unit and integration tests

### Adding a New Extension

1. Implement `KernelExtension` interface
2. Define contributed capabilities
3. Implement compatibility check
4. Set appropriate priority
5. Register extension callbacks

### Adding a New Plugin

1. Implement `KernelPlugin` interface
2. Define exported and required contracts
3. Create `PluginManifest` with metadata
4. Implement install/uninstall lifecycle
5. Add dynamic reload support (optional)

---

## Migration Guide

### From Monolithic to Kernel-Based

1. **Phase 1**: Identify service boundaries
2. **Phase 2**: Create kernel modules for each service
3. **Phase 3**: Migrate capabilities gradually
4. **Phase 4**: Remove legacy coupling
5. **Phase 5**: Enable dynamic plugins

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2024 | Initial release with PHR and Finance modules |

---

## References

- [API Documentation](./API_DOCUMENTATION.md)
- [Detailed Implementation Plan](./DETAILED_KERNEL_IMPLEMENTATION_PLAN.md)
- [Granular Phase Specifications](./GRANULAR_PHASE_SPECIFICATIONS.md)
- [Plugin-Based Architecture](./PLUGIN_BASED_ARCHITECTURE.md)

---

**Document Version:** 1.0.0  
**Last Updated:** 2024-03-18  
**Author:** Ghatana Kernel Team
