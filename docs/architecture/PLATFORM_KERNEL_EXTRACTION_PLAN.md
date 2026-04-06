# Platform Architecture Restructuring Plan
## Kernel Extraction & Plugin Framework Migration

**Document Version:** 1.0  
**Date:** 2026-04-05  
**Status:** Draft for Review  
**Owner:** Platform Architecture Team

---

## Executive Summary

This document outlines the plan to restructure the Ghatana platform architecture by:
1. Extracting `platform/java/kernel` to a new top-level `platform-kernel` directory
2. Creating a new top-level `platform-plugins` directory for shared plugins
3. Integrating plugin core (`platform/java/plugin`) into `platform-kernel` as a submodule

### Goals
- **Modularity**: Clear separation between kernel core, kernel modules, and plugins
- **Reusability**: Plugins become first-class shareable components across products
- **Maintainability**: Reduced coupling, clearer dependency boundaries
- **Scalability**: Foundation for plugin marketplace and dynamic loading

---

## 1. Current State Analysis

### 1.1 Existing Structure

```
platform/
├── java/
│   ├── kernel/                    # 168 items - Core kernel abstractions
│   ├── kernel-persistence/         # 8 items - Durable adapters
│   ├── plugin/                     # 45 items - Plugin framework (SPI + impl)
│   ├── core/                       # 159 items - Core utilities
│   ├── domain/                     # 73 items - Domain abstractions
│   ├── database/                   # 88 items - Database abstractions
│   ├── billing/                    # 10 items - Billing contracts
│   ├── observability/              # 96 items - Telemetry, audit, metrics
│   ├── security/                   # 108 items - Security framework
│   ├── testing/                    # 94 items - Test utilities
│   └── ... (other modules)
└── contracts/                      # Platform contracts

products/
├── finance/
│   ├── src/main/java/
│   │   ├── extension/              # ComplianceKernelExtension, RiskManagementKernelExtension
│   │   ├── kernel/                 # FinanceKernelModule
│   │   └── ai/                     # FraudDetectionAgent (2 duplicates!)
│   └── domains/                    # OMS, EMS, PMS, etc.
└── phr/
    └── src/main/java/
        └── kernel/                 # PhrKernelModule
```

### 1.2 Current Module Dependencies

```
platform:java:kernel
├── depends on: platform:java:core (api)
├── depends on: platform:java:testing (test)
└── provides: KernelModule, KernelContext, KernelCapability

platform:java:plugin
├── depends on: platform:java:core (api)
├── depends on: platform:java:domain (api)
├── depends on: platform:java:ai-integration (api)
├── depends on: platform:java:governance (api)
└── provides: Plugin, PluginManager, PluginContext
```

### 1.3 Identified Issues

1. **Plugin Framework Location**: Plugin core is separate from kernel but tightly coupled
2. **Extension Pattern**: KernelExtensions in products duplicate plugin concepts
3. **Feature Duplication**: FraudDetectionAgent has 2 implementations in Finance
4. **Billing Pattern**: PHR and Finance both implement billing logic that could be plugin-based

---

## 2. Target Architecture

### 2.1 New Directory Structure

```
platform-kernel/                   # NEW: Extracted from platform/java/kernel + plugin
├── build.gradle.kts              # Aggregator build
├── settings.gradle.kts           # Subproject definitions
├── kernel-core/                  # Renamed from platform:java:kernel
│   ├── src/main/java/com/ghatana/kernel/
│   │   ├── module/               # KernelModule, AbstractKernelModule
│   │   ├── context/              # KernelContext, KernelTenantContext
│   │   ├── descriptor/             # KernelCapability, KernelDescriptor
│   │   ├── extension/              # AbstractKernelExtension (DEPRECATED - migrate to plugins)
│   │   └── service/                # AbstractDataService
│   └── build.gradle.kts
├── kernel-plugin/                # MOVED from platform:java:plugin
│   ├── src/main/java/com/ghatana/platform/plugin/
│   │   ├── Plugin.java             # Core plugin interface
│   │   ├── PluginManager.java      # Plugin lifecycle management
│   │   ├── PluginContext.java      # Plugin runtime context
│   │   ├── PluginCapability.java   # Plugin capability declaration
│   │   ├── EnhancedPluginManager.java
│   │   ├── HotReloadPluginManager.java
│   │   └── spi/                    # Plugin SPI interfaces
│   └── build.gradle.kts
├── kernel-persistence/           # MOVED from platform:java:kernel-persistence
├── kernel-observability/         # EXTRACTED from platform:java:observability (kernel parts)
├── kernel-security/              # EXTRACTED from platform:java:security (kernel parts)
└── kernel-testing/               # MOVED from platform:java:testing

platform-plugins/                  # NEW: Shared plugins (product-agnostic)
├── build.gradle.kts              # Aggregator build
├── settings.gradle.kts           # Subproject definitions
├── plugin-billing-ledger/        # Billing ledger plugin (from platform:java:billing)
│   ├── src/main/java/com/ghatana/plugin/billing/
│   │   ├── BillingLedgerPlugin.java
│   │   ├── LedgerPostingService.java
│   │   └── BillingTransaction.java
│   └── build.gradle.kts
├── plugin-fraud-detection/       # NEW: Fraud detection framework
│   ├── src/main/java/com/ghatana/plugin/fraud/
│   │   ├── FraudDetectionPlugin.java
│   │   ├── FraudAssessor.java
│   │   └── FeatureExtractor.java
│   └── build.gradle.kts
├── plugin-compliance/            # NEW: Generic compliance engine
│   ├── src/main/java/com/ghatana/plugin/compliance/
│   │   ├── CompliancePlugin.java
│   │   ├── RuleEngine.java
│   │   └── AuditLogger.java
│   └── build.gradle.kts
├── plugin-consent/               # NEW: Consent management framework
│   ├── src/main/java/com/ghatana/plugin/consent/
│   │   ├── ConsentPlugin.java
│   │   ├── ConsentLifecycle.java
│   │   └── ConsentRepository.java
│   └── build.gradle.kts
├── plugin-risk-management/       # NEW: Risk management framework
│   ├── src/main/java/com/ghatana/plugin/risk/
│   │   ├── RiskManagementPlugin.java
│   │   ├── VaRCalculator.java
│   │   └── LimitEnforcer.java
│   └── build.gradle.kts
└── plugin-audit-trail/           # NEW: Audit trail framework
    ├── src/main/java/com/ghatana/plugin/audit/
    │   ├── AuditTrailPlugin.java
    │   └── HashChainLogger.java
    └── build.gradle.kts

# Remaining platform/java modules (shrunk)
platform/
├── java/
│   ├── core/                       # Keep: Core utilities (JsonUtils, etc.)
│   ├── domain/                     # Keep: Domain abstractions
│   ├── database/                   # Keep: Database abstractions
│   ├── http/                       # Keep: HTTP abstractions
│   ├── workflow/                   # Keep: Workflow engine
│   ├── connectors/                 # Keep: Integration connectors
│   ├── ai-integration/             # Keep: AI integration framework
│   └── ... (other non-kernel modules)
└── contracts/                      # Keep: Platform contracts
```

### 2.2 Gradle Module Path Mapping

| Current Path | New Path | Type |
|--------------|----------|------|
| `:platform:java:kernel` | `:platform-kernel:kernel-core` | Move |
| `:platform:java:plugin` | `:platform-kernel:kernel-plugin` | Move |
| `:platform:java:kernel-persistence` | `:platform-kernel:kernel-persistence` | Move |
| `:platform:java:testing` | `:platform-kernel:kernel-testing` | Move |
| `:platform:java:billing` | `:platform-plugins:plugin-billing-ledger` | Transform |
| NEW | `:platform-plugins:plugin-fraud-detection` | Create |
| NEW | `:platform-plugins:plugin-compliance` | Create |
| NEW | `:platform-plugins:plugin-consent` | Create |
| NEW | `:platform-plugins:plugin-risk-management` | Create |
| NEW | `:platform-plugins:plugin-audit-trail` | Create |

---

## 3. Detailed Migration Plan

### Phase 1: Prepare platform-kernel Structure (Week 1)

#### Step 1.1: Create Directory Structure
```bash
mkdir -p platform-kernel/{kernel-core,kernel-plugin,kernel-persistence,kernel-testing}/src/{main,test}/java/com/ghatana
mkdir -p platform-plugins/{plugin-billing-ledger,plugin-fraud-detection,plugin-compliance,plugin-consent,plugin-risk-management,plugin-audit-trail}/src/{main,test}/java/com/ghatana
```

#### Step 1.2: Create Root Build Files

**platform-kernel/build.gradle.kts**:
```kotlin
plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.ghatana.kernel"
version = "1.0.0"

dependencies {
    constraints {
        api(project(":platform-kernel:kernel-core"))
        api(project(":platform-kernel:kernel-plugin"))
        api(project(":platform-kernel:kernel-persistence"))
        api(project(":platform-kernel:kernel-testing"))
    }
}

subprojects {
    apply(plugin = "java-library")
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    
    dependencies {
        // ActiveJ (mandatory for kernel)
        api(libs.activej.promise)
        api(libs.activej.eventloop)
        
        // Logging
        api(libs.slf4j.api)
        
        // Nullability
        compileOnly(libs.jetbrains.annotations)
    }
}
```

**platform-kernel/settings.gradle.kts**:
```kotlin
rootProject.name = "platform-kernel"

include("kernel-core")
include("kernel-plugin")
include("kernel-persistence")
include("kernel-testing")
```

**platform-plugins/build.gradle.kts**:
```kotlin
plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.ghatana.platform"
version = "1.0.0"

dependencies {
    constraints {
        api(project(":platform-plugins:plugin-billing-ledger"))
        api(project(":platform-plugins:plugin-fraud-detection"))
        api(project(":platform-plugins:plugin-compliance"))
        api(project(":platform-plugins:plugin-consent"))
        api(project(":platform-plugins:plugin-risk-management"))
        api(project(":platform-plugins:plugin-audit-trail"))
    }
}

subprojects {
    apply(plugin = "java-library")
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    
    dependencies {
        // All plugins depend on kernel
        api(project(":platform-kernel:kernel-core"))
        api(project(":platform-kernel:kernel-plugin"))
        
        // ActiveJ
        api(libs.activej.promise)
        
        // Logging
        api(libs.slf4j.api)
    }
}
```

**platform-plugins/settings.gradle.kts**:
```kotlin
rootProject.name = "platform-plugins"

include("plugin-billing-ledger")
include("plugin-fraud-detection")
include("plugin-compliance")
include("plugin-consent")
include("plugin-risk-management")
include("plugin-audit-trail")
```

### Phase 2: Migrate kernel-core (Week 1-2)

#### Step 2.1: Copy kernel Sources
- Copy `platform/java/kernel/src` → `platform-kernel/kernel-core/src`
- Rename package: `com.ghatana.kernel` → keep as is
- Update imports for dependencies

#### Step 2.2: Create kernel-core Build Script

**platform-kernel/kernel-core/build.gradle.kts**:
```kotlin
plugins {
    id("java-library")
    id("ghatana.module.conventions")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Kernel Core - module lifecycle and context abstractions"

dependencies {
    // Depends on platform core utilities
    api(project(":platform:java:core"))
    
    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // JSON
    api(libs.jackson.databind)
    
    // Testing
    testImplementation(project(":platform-kernel:kernel-testing"))
}
```

#### Step 2.3: Mark KernelExtension as Deprecated

Add deprecation annotations to guide migration:

```java
@Deprecated(
    since = "2.0.0",
    forRemoval = true
)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrateToPlugin {
    String pluginName();
    String reason() default "Migrate to Plugin framework for better modularity";
}

// Apply to existing extensions
@MigrateToPlugin(pluginName = "plugin-compliance")
public class ComplianceKernelExtension extends AbstractKernelExtension { ... }

@MigrateToPlugin(pluginName = "plugin-risk-management")
public class RiskManagementKernelExtension extends AbstractKernelExtension { ... }
```

### Phase 3: Migrate kernel-plugin (Week 2)

#### Step 3.1: Copy Plugin Sources
- Copy `platform/java/plugin/src` → `platform-kernel/kernel-plugin/src`
- Keep package: `com.ghatana.platform.plugin`

#### Step 3.2: Update Plugin Dependencies

**platform-kernel/kernel-plugin/build.gradle.kts**:
```kotlin
plugins {
    id("java-library")
    id("ghatana.module.conventions")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Kernel Plugin Framework - lifecycle and SPI"

dependencies {
    // Kernel core
    api(project(":platform-kernel:kernel-core"))
    
    // Platform base
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    
    // ActiveJ
    api(libs.activej.promise)
    api(libs.activej.common)
    
    // JSON
    implementation(libs.jackson.databind)
}
```

#### Step 3.3: Add Kernel-Plugin Integration

Create bridge between kernel modules and plugins:

```java
// platform-kernel/kernel-plugin/src/.../plugin/KernelModulePluginAdapter.java
package com.ghatana.platform.plugin;

import com.ghatana.kernel.module.KernelModule;

/**
 * Adapter that wraps a KernelModule as a Plugin.
 * Enables gradual migration from KernelModule to Plugin.
 */
public class KernelModulePluginAdapter implements Plugin {
    private final KernelModule kernelModule;
    
    public KernelModulePluginAdapter(KernelModule kernelModule) {
        this.kernelModule = kernelModule;
    }
    
    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
            .id(kernelModule.getModuleId())
            .name(kernelModule.getName())
            .version("1.0.0")
            .build();
    }
    
    @Override
    public Promise<Void> initialize(PluginContext context) {
        // Adapt PluginContext to KernelContext
        return kernelModule.initialize(adaptContext(context));
    }
    
    // ... other methods
}
```

### Phase 4: Create Shared Plugins (Week 3-4)

#### Step 4.1: plugin-billing-ledger (Migrate from platform:java:billing)

**Source Structure**:
```
platform-plugins/plugin-billing-ledger/src/main/java/com/ghatana/plugin/billing/
├── BillingLedgerPlugin.java
├── LedgerPostingService.java
├── BillingTransaction.java
├── BillingTransactionCoordinator.java
├── TransactionReversalService.java
└── idempotency/
    └── IdempotencyKeyStore.java
```

**Key Interface**:
```java
package com.ghatana.plugin.billing;

import com.ghatana.platform.plugin.Plugin;

/**
 * Billing Ledger Plugin - Double-entry ledger as a shared plugin.
 * 
 * Usage:
 *   BillingLedgerPlugin ledger = pluginManager.load("billing-ledger");
 *   Promise<String> entryId = ledger.postTransaction(transaction);
 */
public interface BillingLedgerPlugin extends Plugin {
    
    Promise<String> postTransaction(BillingTransaction transaction);
    
    Promise<String> reverseTransaction(String originalTransactionId, 
                                       String reversalReason);
    
    Promise<PostingStatus> getPostingStatus(String transactionId);
    
    Promise<LedgerAccount> createAccount(String accountId, AccountType type);
    
    Promise<Optional<LedgerEntry>> getEntry(String entryId);
    
    Promise<List<LedgerEntry>> queryEntries(String accountId, 
                                            TimeRange range);
}
```

#### Step 4.2: plugin-fraud-detection (NEW)

**Consolidates**: Both FraudDetectionAgent implementations from Finance

```java
package com.ghatana.plugin.fraud;

import com.ghatana.platform.plugin.Plugin;

/**
 * Fraud Detection Plugin - Product-agnostic fraud detection framework.
 * 
 * Supports:
 * - Transaction fraud (Finance)
 * - Insurance claim fraud (PHR)
 * - Identity fraud (any product)
 * 
 * Usage:
 *   FraudDetectionPlugin fraud = pluginManager.load("fraud-detection");
 *   Promise<FraudAssessment> result = fraud.assess(request);
 */
public interface FraudDetectionPlugin extends Plugin {
    
    Promise<FraudAssessment> assessTransaction(String transactionId,
                                                FraudDetectionRequest request);
    
    Promise<FraudPattern> detectPatterns(String productId, 
                                        TimeWindow window);
    
    Promise<Void> trainModel(String modelId, TrainingData data);
    
    Promise<ModelMetrics> getModelMetrics(String modelId);
    
    /**
     * Register a custom rule for this product.
     */
    Promise<Void> registerRule(String productId, FraudRule rule);
}
```

#### Step 4.3: plugin-compliance (NEW)

**Extracts**: ComplianceKernelExtension from Finance

```java
package com.ghatana.plugin.compliance;

import com.ghatana.platform.plugin.Plugin;

/**
 * Compliance Plugin - Generic compliance rule engine.
 * 
 * Supports:
 * - SOX (Finance)
 * - PCI-DSS (Finance)
 * - HIPAA (PHR)
 * - GDPR (any product)
 * 
 * Usage:
 *   CompliancePlugin compliance = pluginManager.load("compliance");
 *   compliance.registerRuleSet("sox-finance", soxRules);
 *   Promise<ComplianceResult> result = compliance.evaluate("sox-finance", context);
 */
public interface CompliancePlugin extends Plugin {
    
    Promise<ComplianceResult> evaluate(String ruleSetId, 
                                      ComplianceContext context);
    
    Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules);
    
    Promise<Void> addRule(String ruleSetId, ComplianceRule rule);
    
    Promise<List<AuditEntry>> getAuditTrail(String entityId);
    
    Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId);
}
```

#### Step 4.4: plugin-consent (NEW)

**Extracts**: PHR consent management to generic framework

```java
package com.ghatana.plugin.consent;

import com.ghatana.platform.plugin.Plugin;

/**
 * Consent Management Plugin - Universal consent framework.
 * 
 * Supports:
 * - Healthcare data consent (PHR/HIPAA)
 * - Financial data sharing (Finance/GDPR)
 * - Marketing consent (any product)
 * - Terms acceptance (any product)
 */
public interface ConsentPlugin extends Plugin {
    
    Promise<ConsentRecord> recordConsent(String subjectId,
                                        String purpose,
                                        ConsentAction action);
    
    Promise<Boolean> verifyConsent(String subjectId, String purpose);
    
    Promise<Void> revokeConsent(String consentId);
    
    Promise<List<ConsentRecord>> getConsentHistory(String subjectId);
    
    Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose);
}
```

#### Step 4.5: plugin-risk-management (NEW)

**Extracts**: RiskManagementKernelExtension from Finance

```java
package com.ghatana.plugin.risk;

import com.ghatana.platform.plugin.Plugin;

/**
 * Risk Management Plugin - Generic risk calculation framework.
 * 
 * Supports:
 * - Trading risk (Finance)
 * - Clinical risk (PHR)
 * - Credit risk (any product)
 */
public interface RiskManagementPlugin extends Plugin {
    
    Promise<RiskScore> calculateRisk(String entityId,
                                    RiskType type,
                                    Map<String, Object> factors);
    
    Promise<Void> setRiskLimits(String entityId, RiskLimits limits);
    
    Promise<List<RiskAlert>> getActiveAlerts(String entityId);
    
    Promise<RiskReport> generateReport(String entityId, TimeRange range);
}
```

#### Step 4.6: plugin-audit-trail (NEW)

```java
package com.ghatana.plugin.audit;

import com.ghatana.platform.plugin.Plugin;

/**
 * Audit Trail Plugin - Immutable, tamper-evident logging.
 * 
 * Features:
 * - Hash chain verification
 * - Cryptographic signing
 * - Regulatory compliance (SOX, HIPAA, PCI-DSS)
 */
public interface AuditTrailPlugin extends Plugin {
    
    Promise<AuditEntry> logEvent(String entityId,
                                String action,
                                Map<String, Object> details);
    
    Promise<List<AuditEntry>> getTrail(String entityId);
    
    Promise<VerificationResult> verifyIntegrity(String entityId);
    
    Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out);
}
```

### Phase 5: Update Root Settings (Week 2, parallel)

#### Step 5.1: Update Root settings.gradle.kts

Add new composite builds:

```kotlin
// =============================================================================
// Platform Kernel — Core kernel framework with plugin system
// =============================================================================

includeBuild("platform-kernel") {
    dependencySubstitution {
        substitute(module("com.ghatana.kernel:kernel-core"))
            .using(project(":kernel-core"))
        substitute(module("com.ghatana.kernel:kernel-plugin"))
            .using(project(":kernel-plugin"))
        substitute(module("com.ghatana.kernel:kernel-persistence"))
            .using(project(":kernel-persistence"))
        substitute(module("com.ghatana.kernel:kernel-testing"))
            .using(project(":kernel-testing"))
    }
}

// =============================================================================
// Platform Plugins — Shared product-agnostic plugins
// =============================================================================

includeBuild("platform-plugins") {
    dependencySubstitution {
        substitute(module("com.ghatana.plugin:billing-ledger"))
            .using(project(":plugin-billing-ledger"))
        substitute(module("com.ghatana.plugin:fraud-detection"))
            .using(project(":plugin-fraud-detection"))
        // ... other plugins
    }
}

// =============================================================================
// Platform — Remaining non-kernel modules
// =============================================================================

include(":platform:java:core")
include(":platform:java:domain")
// ... (remove kernel, plugin, kernel-persistence, billing)
```

#### Step 5.2: Update Dependencies in Products

**Finance build.gradle.kts**:
```kotlin
dependencies {
    // Kernel (new location)
    implementation(project(":platform-kernel:kernel-core"))
    implementation(project(":platform-kernel:kernel-plugin"))
    
    // Plugins (instead of extensions)
    implementation(project(":platform-plugins:plugin-billing-ledger"))
    implementation(project(":platform-plugins:plugin-fraud-detection"))
    implementation(project(":platform-plugins:plugin-compliance"))
    implementation(project(":platform-plugins:plugin-risk-management"))
    implementation(project(":platform-plugins:plugin-audit-trail"))
    
    // Remove: platform:java:kernel, platform:java:plugin, platform:java:billing
}
```

**PHR build.gradle.kts**:
```kotlin
dependencies {
    // Kernel (new location)
    implementation(project(":platform-kernel:kernel-core"))
    implementation(project(":platform-kernel:kernel-plugin"))
    
    // Plugins
    implementation(project(":platform-plugins:plugin-billing-ledger"))
    implementation(project(":platform-plugins:plugin-consent"))
    implementation(project(":platform-plugins:plugin-audit-trail"))
    
    // Remove: platform:java:kernel, platform:java:billing
}
```

### Phase 6: Migrate Product Extensions to Plugins (Week 4-6)

#### Step 6.1: Finance Migration

| Current | Migration Path |
|---------|---------------|
| `ComplianceKernelExtension` | Use `plugin-compliance` + register Finance-specific rules |
| `RiskManagementKernelExtension` | Use `plugin-risk-management` + configure trading limits |
| `FraudDetectionAgent` (2 copies) | Consolidate into `plugin-fraud-detection` |
| `BillingLedgerAdapter` | Use `plugin-billing-ledger` |

**Example: Finance Compliance Migration**:

```java
// BEFORE: Extension-based
public class FinanceProductModule extends AbstractKernelModule {
    @Override
    protected void configure() {
        bind(ComplianceKernelExtension.class).toInstance(new ComplianceKernelExtension());
    }
}

// AFTER: Plugin-based
public class FinanceProductModule extends AbstractKernelModule {
    @Override
    protected void configure() {
        // Plugin is loaded by PluginManager
        CompliancePlugin compliance = pluginManager.load("compliance");
        
        // Register Finance-specific rules
        compliance.registerRuleSet("sox-finance", List.of(
            new SOX302Rule(),
            new SOX404Rule(),
            new PCIDSSRule()
        ));
    }
}
```

#### Step 6.2: PHR Migration

| Current | Migration Path |
|---------|---------------|
| PHR `BillingService` | Use `plugin-billing-ledger` for ledger posting, keep PHR billing logic |
| PHR `PHRSecurityManagerImpl` | Keep (product-specific), but use `plugin-consent` for consent management |
| PHR `PHRAuditTrailServiceImpl` | Use `plugin-audit-trail` |

### Phase 7: Clean Up (Week 6)

#### Step 7.1: Remove Old Modules

After migration is verified:

```bash
# Archive old locations (don't delete immediately - keep for rollback)
mkdir -p _archived/platform-java
mv platform/java/kernel _archived/platform-java/
mv platform/java/plugin _archived/platform-java/
mv platform/java/kernel-persistence _archived/platform-java/
mv platform/java/billing _archived/platform-java/
```

#### Step 7.2: Update Documentation

- Update architecture diagrams
- Update dependency graphs
- Update onboarding docs
- Update ADR (Architecture Decision Record)

---

## 4. Dependency Graph

### 4.1 New Dependency Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                        PRODUCTS                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │   Finance    │  │     PHR      │  │   Future...  │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
└─────────┼────────────────┼────────────────┼─────────────────────┘
          │                │                │
          ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PLATFORM-PLUGINS                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ Billing  │ │  Fraud   │ │Compliance│ │  Consent │ │  Risk   │ │
│  │ Ledger   │ │Detection │ │          │ │          │ │Management│ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘ │
└───────┼────────────┼────────────┼────────────┼────────────┼──────┘
        │            │            │            │            │
        └────────────┴────────────┴────────────┴────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PLATFORM-KERNEL                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │  kernel  │  │  kernel  │  │  kernel  │  │  kernel  │         │
│  │  -core   │  │  -plugin │  │ -persist │  │  -test   │         │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              PLATFORM (Remaining Modules)                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │   core   │  │  domain  │  │ database │  │   http   │         │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Module Dependency Table

| Module | Depends On | Used By |
|--------|-----------|---------|
| `platform-kernel:kernel-core` | `platform:java:core` | All kernel modules, all plugins |
| `platform-kernel:kernel-plugin` | `kernel-core`, `platform:java:domain` | All plugins |
| `platform-kernel:kernel-persistence` | `kernel-core` | Products needing durable storage |
| `platform-kernel:kernel-testing` | `kernel-core` | All test code |
| `platform-plugins:plugin-*` | `kernel-core`, `kernel-plugin` | Products |
| `products:finance` | `kernel-*`, `plugin-*` | - |
| `products:phr` | `kernel-*`, `plugin-*` | - |

---

## 5. Migration Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| **Phase 1** | Week 1 | Directory structure, root build files |
| **Phase 2** | Week 1-2 | kernel-core migrated, tests passing |
| **Phase 3** | Week 2 | kernel-plugin migrated, integrated |
| **Phase 4** | Week 3-4 | All 6 plugins created, unit tests |
| **Phase 5** | Week 2 (parallel) | Root settings updated, composite builds working |
| **Phase 6** | Week 4-6 | Products migrated, extensions converted |
| **Phase 7** | Week 6 | Cleanup, docs, verification |

**Total Duration:** 6 weeks  
**Parallel Tracks:** Phase 5 runs parallel with Phases 3-4  
**Risk Buffer:** Week 7 for unexpected issues

---

## 6. Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Circular dependencies | High | Strict dependency rules: plugins → kernel → platform |
| Breaking product builds | High | Migrate in feature branches, CI validation |
| Test failures | Medium | Move tests incrementally, keep passing |
| Plugin API instability | Medium | Start with internal plugins before external marketplace |
| Performance regression | Medium | Benchmark before/after, especially plugin loading |
| Rollback needed | Low | Archive old code, don't delete until v2.0 |

---

## 7. Success Criteria

1. ✅ All kernel modules build independently in `platform-kernel`
2. ✅ All plugins build independently in `platform-plugins`
3. ✅ Finance and PHR build with new module paths
4. ✅ All existing tests pass (no regressions)
5. ✅ Plugin loading time < 100ms (startup performance)
6. ✅ No circular dependencies in new structure
7. ✅ ADR documenting architecture decision merged
8. ✅ Developer onboarding docs updated

---

## 8. Appendix: File Mapping

### Kernel Core Files to Migrate

| Source | Destination |
|--------|-------------|
| `platform/java/kernel/src/main/java/com/ghatana/kernel/*.java` | `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/` |
| `platform/java/kernel/src/test/java/com/ghatana/kernel/*.java` | `platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/` |
| `platform/java/kernel/build.gradle.kts` | `platform-kernel/kernel-core/build.gradle.kts` (adapted) |

### Plugin Files to Migrate

| Source | Destination |
|--------|-------------|
| `platform/java/plugin/src/main/java/com/ghatana/platform/plugin/*.java` | `platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/plugin/` |
| `platform/java/plugin/src/test/java/com/ghatana/platform/plugin/*.java` | `platform-kernel/kernel-plugin/src/test/java/com/ghatana/platform/plugin/` |

### Billing Files to Transform

| Source | Destination | Action |
|--------|-------------|--------|
| `platform/java/billing/src/main/java/com/ghatana/platform/billing/BillingTransaction.java` | `platform-plugins/plugin-billing-ledger/src/main/java/com/ghatana/plugin/billing/` | Move |
| `platform/java/billing/src/main/java/com/ghatana/platform/billing/LedgerPostingService.java` | `platform-plugins/plugin-billing-ledger/src/main/java/com/ghatana/plugin/billing/` | Move |
| Finance `BillingLedgerAdapter.java` | Keep in Finance | Keep as adapter |
| PHR `BillingService.java` | Keep in PHR | Refactor to use plugin |

---

## 9. Next Steps

1. **Review this plan** with architecture team
2. **Create feature branch** for migration
3. **Set up CI** for new module structure
4. **Begin Phase 1** (directory structure)
5. **Weekly check-ins** to track progress

---

*Document Version History:*
- 1.0 (2026-04-05): Initial draft
