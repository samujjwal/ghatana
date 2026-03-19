# Ghatana Kernel Platform - API Documentation

## Overview

The Ghatana Kernel Platform provides a plugin-based architecture for building scalable, modular healthcare and financial applications. This document describes the public APIs available for kernel core components, product modules, extensions, and plugins.

## Table of Contents

1. [Kernel Core APIs](#kernel-core-apis)
2. [Product Module APIs](#product-module-apis)
3. [Extension APIs](#extension-apis)
4. [Plugin APIs](#plugin-apis)
5. [Adapter APIs](#adapter-apis)
6. [Usage Examples](#usage-examples)

---

## Kernel Core APIs

### KernelModule Interface

The primary interface for all kernel modules.

```java
public interface KernelModule {
    /** Returns unique module identifier */
    String getModuleId();
    
    /** Returns semantic version (e.g., "1.0.0") */
    String getVersion();
    
    /** Returns set of capabilities provided by this module */
    Set<KernelCapability> getCapabilities();
    
    /** Returns set of dependencies required by this module */
    Set<KernelDependency> getDependencies();
    
    /** Initialize module with kernel context */
    void initialize(KernelContext context);
    
    /** Start module - returns Promise for async completion */
    Promise<Void> start();
    
    /** Stop module - returns Promise for async completion */
    Promise<Void> stop();
    
    /** Get current health status */
    HealthStatus getHealthStatus();
}
```

### KernelExtension Interface

Interface for extending module capabilities.

```java
public interface KernelExtension {
    /** Returns unique extension identifier */
    String getExtensionId();
    
    /** Returns human-readable name */
    String getName();
    
    /** Returns extension descriptor */
    KernelDescriptor getDescriptor();
    
    /** Returns set of contributed capabilities */
    Set<KernelCapability> getContributedCapabilities();
    
    /** Called when host module is initialized */
    void onModuleInitialized(KernelContext context);
    
    /** Called when host module is started */
    void onModuleStarted(KernelContext context);
    
    /** Called when host module is stopped */
    void onModuleStopped(KernelContext context);
    
    /** Check compatibility with host module */
    boolean isCompatible(KernelModule hostModule);
    
    /** Get extension priority (higher = earlier initialization) */
    int getPriority();
    
    /** Check if enabled by default */
    default boolean isEnabledByDefault() { return true; }
}
```

### KernelPlugin Interface

Interface for dynamically loadable plugins extending KernelModule.

```java
public interface KernelPlugin extends KernelModule {
    /** Returns plugin manifest */
    PluginManifest getManifest();
    
    /** Returns set of exported service contracts */
    Set<String> getExportedContracts();
    
    /** Returns set of required service contracts */
    Set<String> getRequiredContracts();
    
    /** Install plugin - one-time setup */
    Promise<Void> install();
    
    /** Uninstall plugin - cleanup */
    Promise<Void> uninstall();
    
    /** Reload plugin configuration */
    default Promise<Void> reload() { return Promise.complete(); }
}
```

### KernelContext Interface

Runtime context for dependency lookup and event handling.

```java
public interface KernelContext {
    /** Get required dependency by type */
    <T> T getDependency(Class<T> type);
    
    /** Get optional dependency by type */
    <T> Optional<T> getOptionalDependency(Class<T> type);
    
    /** Check if dependency is available */
    <T> boolean hasDependency(Class<T> type);
    
    /** Get dependency by name */
    <T> T getDependency(String name, Class<T> type);
    
    /** Register event handler */
    <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler);
    
    /** Unregister event handler */
    <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler);
    
    /** Publish event to all registered handlers */
    <E> void publishEvent(E event);
    
    /** Get current tenant context */
    KernelTenantContext getTenantContext();
    
    /** Get specific tenant context by ID */
    KernelTenantContext getTenantContext(String tenantId);
    
    /** Get ActiveJ Eventloop instance */
    Eventloop getEventloop();
    
    /** Get available capabilities */
    Set<KernelCapability> getAvailableCapabilities();
    
    /** Check if capability is available */
    boolean hasCapability(KernelCapability capability);
    
    /** Get configuration value */
    <T> T getConfig(String key, Class<T> type);
    
    /** Get optional configuration value */
    <T> Optional<T> getOptionalConfig(String key, Class<T> type);
    
    /** Get kernel version */
    String getKernelVersion();
    
    /** Get environment name */
    String getEnvironment();
}
```

### KernelRegistry Interface

Central registry for module and plugin management.

```java
public interface KernelRegistry {
    /** Register a kernel module */
    void registerModule(KernelModule module);
    
    /** Register a kernel plugin */
    void registerPlugin(KernelPlugin plugin);
    
    /** Register a capability */
    void registerCapability(KernelCapability capability);
    
    /** Unregister a module */
    boolean unregisterModule(String moduleId);
    
    /** Get module by ID */
    Optional<KernelModule> getModule(String moduleId);
    
    /** Get plugin by ID */
    Optional<KernelPlugin> getPlugin(String pluginId);
    
    /** Get all modules */
    List<KernelModule> getAllModules();
    
    /** Get all plugins */
    List<KernelPlugin> getAllPlugins();
    
    /** Get all capabilities */
    List<KernelCapability> getAllCapabilities();
    
    /** Get plugins providing specific capability */
    List<KernelPlugin> getPluginsByCapability(KernelCapability capability);
    
    /** Get modules providing specific capability */
    List<KernelModule> getModulesByCapability(KernelCapability capability);
    
    /** Get modules dependent on specified module */
    List<KernelModule> getDependentModules(String moduleId);
    
    /** Resolve dependencies in topological order */
    List<KernelModule> resolveDependencies(KernelModule module);
    
    /** Validate module dependencies */
    boolean validateDependencies(KernelModule module);
    
    /** Get validation errors */
    List<String> getDependencyValidationErrors(KernelModule module);
    
    /** Start all modules in dependency order */
    Promise<Void> startAllModules();
    
    /** Stop all modules in reverse dependency order */
    Promise<Void> stopAllModules();
    
    /** Check if module is registered */
    boolean isModuleRegistered(String moduleId);
    
    /** Check if capability is available */
    boolean isCapabilityAvailable(String capabilityId);
}
```

---

## Product Module APIs

### PHR Module

```java
public class PhrKernelModule implements KernelModule {
    // Module ID: "phr-core"
    // Version: "1.0.0"
    
    // Services managed:
    // - patient: Patient record management
    // - consent: Consent management
    // - document: Document management
    // - appointment: Appointment scheduling
    // - medication: Medication management
    // - billing: Billing and invoicing
    // - fhir: FHIR interoperability
    // - imaging: Medical imaging
    // - referral: Referral management
}
```

### Healthcare Consent Extension

```java
public class HealthcareConsentKernelExtension implements KernelExtension {
    // Extension ID: "healthcare-consent-nepal-2081"
    
    /** Grant consent for specific purpose */
    Promise<ConsentRecord> grantConsent(
        String patientId,
        ConsentPurpose purpose,
        ConsentScope scope,
        ConsentDuration duration
    );
    
    /** Withdraw previously granted consent */
    Promise<Void> withdrawConsent(String consentId, String reason);
    
    /** Verify consent validity */
    Promise<ConsentVerification> verifyConsent(
        String patientId,
        ConsentPurpose purpose,
        String dataType
    );
    
    /** Get consent history for patient */
    Promise<Set<ConsentRecord>> getConsentHistory(String patientId);
}
```

### FHIR Interop Plugin

```java
public class FhirInteropKernelPlugin implements KernelPlugin {
    // Plugin ID: "fhir-interop-r4"
    
    /** Validate FHIR resource against R4 specification */
    Promise<ValidationResult> validateResource(String resourceType, String resourceJson);
    
    /** Transform internal data to FHIR format */
    Promise<String> transformToFhir(Object internalData, String targetResourceType);
    
    /** Transform FHIR to internal format */
    Promise<Object> transformFromFhir(String fhirJson, String sourceResourceType);
    
    /** Store FHIR resource */
    Promise<Void> storeResource(FhirResource resource);
    
    /** Retrieve FHIR resource by ID */
    Promise<FhirResource> getResource(String resourceId);
    
    /** Search FHIR resources */
    Promise<SearchResult> searchResources(String resourceType, Map<String, String> searchParams);
}
```

### Finance Module

```java
public class FinanceKernelModule implements KernelModule {
    // Module ID: "finance-core"
    // Version: "1.0.0"
    
    // Services managed:
    // - order-management: Order management system (OMS)
    // - execution: Execution management system (EMS)
    // - portfolio: Portfolio management
    // - market-data: Market data processing
    // - pricing: Pricing calculations
    // - risk: Risk management
    // - compliance: Compliance engine
    // - surveillance: Market surveillance
}
```

### Dual Calendar Extension

```java
public class DualCalendarKernelExtension implements KernelExtension {
    // Extension ID: "dual-calendar-nepal"
    
    /** Convert BS date to AD (Gregorian) */
    LocalDate convertBsToAd(int bsYear, int bsMonth, int bsDay);
    
    /** Convert AD date to BS (Bikram Sambat) */
    BsDate convertAdToBs(LocalDate adDate);
    
    /** Parse date string in specified calendar */
    Object parseDate(String dateString, String pattern, CalendarType calendarType);
    
    /** Format date in specified calendar */
    String formatDate(Object date, String pattern, CalendarType calendarType);
    
    /** Get current date in specified calendar */
    Object getCurrentDate(CalendarType calendarType);
    
    /** Check if year is leap year */
    boolean isLeapYear(int year, CalendarType calendarType);
}
```

### Risk Management Extension

```java
public class RiskManagementKernelExtension implements KernelExtension {
    // Extension ID: "risk-management-realtime"
    
    /** Calculate position risk metrics */
    Promise<PositionRisk> calculatePositionRisk(
        String positionId,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal avgCost
    );
    
    /** Calculate portfolio risk metrics */
    Promise<PortfolioRisk> calculatePortfolioRisk(
        String portfolioId,
        Map<String, PositionRisk> positionRisks
    );
    
    /** Update risk limits */
    Promise<Void> updateRiskLimits(RiskLimits limits);
    
    /** Get current risk limits */
    RiskLimits getRiskLimits();
    
    /** Get position risk by ID */
    PositionRisk getPositionRisk(String positionId);
    
    /** Get portfolio risk by ID */
    PortfolioRisk getPortfolioRisk(String portfolioId);
}
```

### Compliance Extension

```java
public class ComplianceKernelExtension implements KernelExtension {
    // Extension ID: "compliance-engine-finance"
    
    /** Validate trade against compliance rules */
    Promise<ComplianceCheckResult> validateTrade(String tradeId, TradeDetails tradeDetails);
    
    /** Validate PCI-DSS compliance */
    Promise<ComplianceCheckResult> validatePCICompliance(String transactionId, PaymentDetails paymentDetails);
    
    /** Validate SOX controls */
    Promise<ComplianceCheckResult> validateSOXControl(String controlId, Map<String, Object> controlData);
    
    /** Get audit trail for entity */
    Promise<Set<AuditEntry>> getAuditTrail(String entityId);
    
    /** Add compliance rule */
    Promise<Void> addComplianceRule(ComplianceRule rule);
}
```

---

## Usage Examples

### Basic Module Registration and Startup

```java
// Create registry and context
KernelRegistryImpl registry = new KernelRegistryImpl();
Eventloop eventloop = Eventloop.create();
KernelConfigResolver configResolver = new DefaultConfigResolver();
KernelContext context = new DefaultKernelContext(registry, configResolver, eventloop, "1.0.0", "production");

// Register modules
PhrKernelModule phrModule = new PhrKernelModule();
FinanceKernelModule financeModule = new FinanceKernelModule();

registry.registerModule(phrModule);
registry.registerModule(financeModule);

// Initialize
phrModule.initialize(context);
financeModule.initialize(context);

// Start all modules in dependency order
registry.startAllModules().getResult();

// Check health
HealthStatus status = registry.getAggregateHealthStatus();
System.out.println("Kernel Health: " + status.getStatus());
```

### Using Healthcare Consent Extension

```java
HealthcareConsentKernelExtension consentExt = new HealthcareConsentKernelExtension();
consentExt.onModuleInitialized(context);
consentExt.onModuleStarted(context);

// Grant consent
ConsentRecord record = consentExt.grantConsent(
    "patient-123",
    ConsentPurpose.TREATMENT,
    ConsentScope.ALL_DATA,
    ConsentDuration.ONE_YEAR
).getResult();

// Verify before accessing data
ConsentVerification verification = consentExt.verifyConsent(
    "patient-123",
    ConsentPurpose.TREATMENT,
    "EMR"
).getResult();

if (verification.isValid()) {
    // Proceed with data access
}
```

### Using Risk Management Extension

```java
RiskManagementKernelExtension riskExt = new RiskManagementKernelExtension();
riskExt.onModuleInitialized(context);
riskExt.onModuleStarted(context);

// Calculate position risk
PositionRisk risk = riskExt.calculatePositionRisk(
    "position-001",
    new BigDecimal("100"),
    new BigDecimal("150.00"),
    new BigDecimal("145.00")
).getResult();

// Check limits
if (risk.getLimitUtilization().compareTo(new BigDecimal("0.8")) > 0) {
    // Alert: approaching limit
}
```

### Using Dual Calendar for Nepal

```java
DualCalendarKernelExtension calendarExt = new DualCalendarKernelExtension();
calendarExt.onModuleInitialized(context);
calendarExt.onModuleStarted(context);

// Convert BS to AD
LocalDate adDate = calendarExt.convertBsToAd(2081, 4, 15);

// Convert AD to BS
BsDate bsDate = calendarExt.convertAdToBs(LocalDate.of(2024, 7, 29));

// Format BS date
String formatted = calendarExt.formatDate(bsDate, "yyyy-MM-dd", CalendarType.BS);
```

---

## Configuration

### Kernel Configuration Resolution

Configuration is resolved hierarchically:
1. Cross-product defaults
2. Product-specific overrides
3. Tenant-specific overrides
4. User-specific overrides

```java
// Get configuration
String dbHost = context.getConfig("database.host", String.class);
Optional<Integer> dbPort = context.getOptionalConfig("database.port", Integer.class);
```

### Tenant Context

```java
// Create tenant context
KernelTenantContext tenant = new KernelTenantContext(
    "tenant-001",
    TenantType.ENTERPRISE,
    Map.of("region", "US", "timezone", "America/New_York"),
    Set.of("phr", "finance"),
    null,  // Security context
    null   // Feature flags
);

context.registerTenantContext("tenant-001", tenant);
```

---

## Error Handling

All async operations return `Promise<T>` which can fail with exceptions:

```java
Promise<ConsentRecord> promise = consentExt.grantConsent(...);

try {
    ConsentRecord record = promise.getResult();
} catch (Exception e) {
    // Handle error
    System.err.println("Failed to grant consent: " + e.getMessage());
}
```

---

## Version Compatibility

- Kernel Core: 1.0.0
- PHR Module: 1.0.0
- Finance Module: 1.0.0
- Java: 21+
- ActiveJ: 6.0+

---

## Thread Safety

All kernel components are thread-safe and designed for concurrent use:
- Module registration: Thread-safe
- Lifecycle operations: Atomic state management
- Health checks: Non-blocking
- Event handling: Lock-free where possible

---

## License

Apache License 2.0
