# Comprehensive Product Migration Plan - EXPANDED

**Date**: February 5, 2026  
**Status**: Detailed Implementation Guide  
**Focus**: AEP, Data Cloud, Shared Services (YAPPC Excluded)  
**Target Repository**: `ghatana-new` (Modernized Monorepo)

---

## 1. Executive Summary

Having successfully stabilized the core `platform` modules, phase 2 of the migration focuses on the "Product" layer. This involves checking out legacy product code, refactoring dependencies to use the new `platform:java:*` modules, and reorganizing code into the standard `platform` / `services` / `launcher` structure defined in `settings.gradle.kts`.

**Key Insight from Legacy Analysis**: 
- **AEP** uses a complex `modules/` structure (boot, core, domains, infra, interfaces, platform) that needs consolidation
- **Data Cloud** already has a simplified `core/`, `api/`, `http-api/` structure that maps cleanly to the new architecture

---

## 2. Standard Product Structure

All products in the new repository follow this standardized tri-module structure:

```
products/<product-name>/
├── platform/                     # Single platform module (Java only for now)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/ghatana/<product>/
│       └── test/java/com/ghatana/<product>/
├── services/                     # Business logic implementation, microservices (optional submodules)
│   ├── <service-1>/
│   └── <service-2>/
└── launcher/                     # Main entry points, Docker composition
    ├── build.gradle.kts
    └── src/main/java/com/ghatana/<product>/launcher/
```

**Note**: Each product has **ONE** platform module. The `platform/` directory IS the Gradle module (`:products:<product>:platform`), not a parent directory. Future language support (Python, TypeScript) would be handled as separate service modules, not platform variants.

---

## 3. Product-Specific Migration Plans

### 3.1. Autonomous Event Processing (AEP)

**Legacy Source**: `ghatana/products/aep`  
**Legacy Structure**: Complex multi-module setup with `modules/`, `platform/libs/`, `aep-libs/`

#### 3.1.1. Source Analysis

Legacy AEP has this structure:
```
aep/
├── modules/
│   ├── boot/                  # Bootstrap, launcher logic
│   ├── core/                  # Core orchestration (THE MAIN MODULE)
│   ├── domains/              # Detection, preprocessing, expert-interface
│   ├── infra/                # Infrastructure utilities
│   ├── interfaces/           # UI-service, API Gateway
│   └── platform/             # Agent registry, workflow
├── platform/libs/            # Database, config, health, performance
├── aep-libs/                 # Pattern system, planner, domain-specific libs
├── services/                 # Empty placeholder in legacy
├── launcher/                 # Main entry point
└── api/                      # External API contracts
```

#### 3.1.2. Migration Strategy: "Consolidate and Flatten"

**Target Structure**:
```
products/aep/
├── platform/                 # Single platform module - ALL core domain logic
│   ├── build.gradle.kts
│   └── src/main/java/com/ghatana/aep/
│       ├── orchestration/    # From modules/core
│       ├── detection/        # From modules/domains/detection-engine
│       ├── preprocessing/    # From modules/domains/data-preprocessing
│       ├── expert/           # From modules/domains/expert-interface
│       ├── workflow/         # From modules/platform/workflow
│       ├── registry/         # From modules/platform/agent-registry
│       ├── pattern/          # From aep-libs/pattern-system
│       └── planner/          # From aep-libs/planner
├── services/
│   └── ui-service/           # From modules/interfaces/ui-service (if needed)
└── launcher/                 # From modules/boot + launcher
    ├── build.gradle.kts
    └── src/main/java/com/ghatana/aep/launcher/
```

#### 3.1.3. Step-by-Step Execution Plan

**Phase 1: Create Directory Structure**
```bash
cd /Users/samujjwal/Development/ghatana-new

# Create base structure (single platform module)
mkdir -p products/aep/platform/src/main/java/com/ghatana/aep
mkdir -p products/aep/platform/src/test/java/com/ghatana/aep
mkdir -p products/aep/services
mkdir -p products/aep/launcher/src/main/java/com/ghatana/aep/launcher
```

**Phase 2: Copy Core Modules**
```bash
# 1. Copy the main orchestration core (modules/core)
cp -r /Users/samujjwal/Development/ghatana/products/aep/modules/core/src/main/java/* \
      products/aep/platform/src/main/java/
cp -r /Users/samujjwal/Development/ghatana/products/aep/modules/core/src/test/java/* \
      products/aep/platform/src/test/java/

# 2. Copy domain modules
for domain in detection-engine data-preprocessing expert-interface; do
    find /Users/samujjwal/Development/ghatana/products/aep/modules/domains/$domain \
         -path "*/src/main/java/*" -type f \
         -exec cp --parents {} products/aep/platform/ \;
done

# 3. Copy platform modules (agent-registry, workflow)
cp -r /Users/samujjwal/Development/ghatana/products/aep/modules/platform/*/src/main/java/* \
      products/aep/platform/src/main/java/

# 4. Copy AEP-specific libraries
cp -r /Users/samujjwal/Development/ghatana/products/aep/aep-libs/pattern-system/*/src/main/java/* \
      products/aep/platform/src/main/java/
cp -r /Users/samujjwal/Development/ghatana/products/aep/aep-libs/planner/src/main/java/* \
      products/aep/platform/src/main/java/

# 5. Copy platform libraries (database, config, health)
cp -r /Users/samujjwal/Development/ghatana/products/aep/platform/libs/*/src/main/java/* \
      products/aep/platform/src/main/java/
```

**Phase 3: Create build.gradle.kts**
```bash
cat > products/aep/platform/build.gradle.kts << 'EOF'
plugins {
    id("java-library")
}

group = "com.ghatana.aep"
version = "1.0.0-SNAPSHOT"

description = "AEP Platform - Core domain logic and orchestration"

dependencies {
    // =========================================================================
    // GLOBAL PLATFORM DEPENDENCIES
    // =========================================================================
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:workflow"))
    api(project(":platform:java:plugin"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:auth"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    
    // =========================================================================
    // ACTIVEJ (Async Runtime)
    // =========================================================================
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.http)
    api(libs.activej.inject)
    api(libs.activej.launcher)
    
    // =========================================================================
    // PERSISTENCE
    // =========================================================================
    api(libs.jakarta.persistence.api)
    api(libs.hibernate.core)
    api(libs.hikaricp)
    api(libs.postgresql)
    api(libs.flyway.core)
    
    // =========================================================================
    // GRPC (For Agent Communication)
    // =========================================================================
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    
    // =========================================================================
    // SERIALIZATION
    // =========================================================================
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    
    // =========================================================================
    // OBSERVABILITY
    // =========================================================================
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
    
    // =========================================================================
    // TESTING
    // =========================================================================
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.activej.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    
    // =========================================================================
    // LOMBOK
    // =========================================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}
EOF
```

**Phase 4: Import Refactoring**
```bash
# Update all imports from old structure to new platform structure
cd products/aep/platform

# Fix package declarations (if modules used inconsistent packages)
find src -name "*.java" -type f -exec sed -i '' \
    's/package com\.ghatana\.platform\./package com.ghatana.aep./g' {} \;

# Update imports to use global platform
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.core\./import com.ghatana.platform.core./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.domain\./import com.ghatana.platform.domain./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.workflow\./import com.ghatana.platform.workflow./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.plugin\./import com.ghatana.platform.plugin./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.observability\./import com.ghatana.platform.observability./g' {} \;

# Fix internal AEP references
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.eventprocessing\./import com.ghatana.aep./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.aep\.expert\./import com.ghatana.aep.expert./g' {} \;
```

**Phase 5: Build Verification**
```bash
cd /Users/samujjwal/Development/ghatana-new
./gradlew :products:aep:platform:build -x test
```

**Phase 6: Migrate Launcher**
```bash
# Copy launcher
cp -r /Users/samujjwal/Development/ghatana/products/aep/launcher/src/main/java/* \
      products/aep/launcher/src/main/java/

# Create launcher build file
cat > products/aep/launcher/build.gradle.kts << 'EOF'
plugins {
    id("application")
}

application {
    mainClass.set("com.ghatana.aep.launcher.AepApplication")
}

dependencies {
    implementation(project(":products:aep:platform:java"))
    implementation(libs.activej.launcher)
    implementation(libs.activej.inject)
    
    runtimeOnly(libs.log4j.slf4j.impl)
    runtimeOnly(libs.log4j.core)
}
EOF
```

---

### 3.2. Data Cloud

**Legacy Source**: `ghatana/products/data-cloud`  
**Legacy Structure**: Already clean with `core/`, `api/`, `http-api/`, `spi/`

#### 3.2.1. Source Analysis

Legacy Data Cloud structure:
```
data-cloud/
├── core/                     # Main domain + service logic (6830 lines gradle)
├── api/                      # API contracts (1674 lines gradle)
├── http-api/                 # REST endpoints
├── spi/                      # Storage interfaces
├── distributed/              # Distributed coordination
├── plugins/                  # Storage implementations (postgres, redis, iceberg)
├── launcher/                 # Main entry point
└── cli/                      # CLI tools
```

#### 3.2.2. Migration Strategy: "Direct Mapping"

**Target Structure**:
```
products/data-cloud/
├── platform/                 # Single platform module - Merge: core + api + spi
│   ├── build.gradle.kts
│   └── src/main/java/com/ghatana/datacloud/
│       ├── domain/           # From core
│       ├── service/          # From core
│       └── spi/              # From spi (storage interfaces)
├── services/
│   ├── http-api/             # From http-api
│   └── distributed/          # From distributed
└── launcher/                 # From launcher
    ├── build.gradle.kts
    └── src/main/java/com/ghatana/datacloud/launcher/
```

#### 3.2.3. Step-by-Step Execution Plan

**Phase 1: Create Structure**
```bash
cd /Users/samujjwal/Development/ghatana-new

# Create base structure (single platform module)
mkdir -p products/data-cloud/platform/src/main/java/com/ghatana/datacloud
mkdir -p products/data-cloud/platform/src/test/java/com/ghatana/datacloud
mkdir -p products/data-cloud/services/http-api
mkdir -p products/data-cloud/services/distributed
mkdir -p products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher
```

**Phase 2: Copy Core Modules**
```bash
# 1. Copy core (domain + service + infrastructure)
cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/core/src/main/java/* \
      products/data-cloud/platform/src/main/java/
cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/core/src/test/java/* \
      products/data-cloud/platform/src/test/java/

# 2. Copy API contracts
cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/api/src/main/java/* \
      products/data-cloud/platform/src/main/java/

# 3. Copy SPI (if separate)
if [ -d "/Users/samujjwal/Development/ghatana/products/data-cloud/spi/src" ]; then
    cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/spi/src/main/java/* \
          products/data-cloud/platform/src/main/java/
fi
```

**Phase 3: Create build.gradle.kts**
```bash
cat > products/data-cloud/platform/build.gradle.kts << 'EOF'
plugins {
    id("java-library")
    id("java-test-fixtures")
}

group = "com.ghatana.datacloud"
version = "1.0.0-SNAPSHOT"

description = "Data Cloud Platform - Metadata management and governance"

dependencies {
    // =========================================================================
    // GLOBAL PLATFORM DEPENDENCIES
    // =========================================================================
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:auth"))
    
    // =========================================================================
    // ACTIVEJ
    // =========================================================================
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.http)
    
    // =========================================================================
    // PERSISTENCE
    // =========================================================================
    api(libs.jakarta.persistence.api)
    api(libs.hibernate.core)
    api(libs.hikaricp)
    api(libs.postgresql)
    api(libs.flyway.core)
    
    // =========================================================================
    // SERIALIZATION
    // =========================================================================
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.annotations)
    
    // =========================================================================
    // VALIDATION
    // =========================================================================
    api(libs.jakarta.validation.api)
    implementation(libs.hibernate.validator)
    
    // =========================================================================
    // REDIS (for distributed cache)
    // =========================================================================
    implementation(libs.lettuce.core)
    
    // =========================================================================
    // TESTING
    // =========================================================================
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    
    // Test Fixtures (Shared test utilities)
    testFixturesImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.assertj.core)
    
    // =========================================================================
    // LOMBOK
    // =========================================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}
EOF
```

**Phase 4: Import Refactoring**
```bash
cd products/data-cloud/platform

# Update imports to use global platform (DC already mostly uses correct packages)
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.libs\.database\./import com.ghatana.platform.database./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.libs\.http\./import com.ghatana.platform.http./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.libs\.observability\./import com.ghatana.platform.observability./g' {} \;
find src -name "*.java" -type f -exec sed -i '' \
    's/import com\.ghatana\.libs\.auth\./import com.ghatana.platform.auth./g' {} \;
```

**Phase 5: Migrate Services**
```bash
# Copy HTTP API
cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/http-api/* \
      products/data-cloud/services/http-api/

# Copy Distributed module
cp -r /Users/samujjwal/Development/ghatana/products/data-cloud/distributed/* \
      products/data-cloud/services/distributed/
```

**Phase 6: Build Verification**
```bash
cd /Users/samujjwal/Development/ghatana-new
./gradlew :products:data-cloud:platform:build -x test
```

---

### 3.3. Shared Services & Libraries

**Goal**: Move product-specific libraries from `ghatana/libs/java` into appropriate product modules.

#### 3.3.1. Governance & Ingestion → Data Cloud

These are Data Cloud specific:
```bash
# Governance
cp -r /Users/samujjwal/Development/ghatana/libs/java/governance/src/main/java/* \
      /Users/samujjwal/Development/ghatana-new/products/data-cloud/platform/src/main/java/

# Ingestion
cp -r /Users/samujjwal/Development/ghatana/libs/java/ingestion/src/main/java/* \
      /Users/samujjwal/Development/ghatana-new/products/data-cloud/platform/src/main/java/
```

#### 3.3.2. Context-Policy → Flashit

```bash
mkdir -p products/flashit/platform/src/main/java
cp -r /Users/samujjwal/Development/ghatana/libs/java/context-policy/src/main/java/* \
      products/flashit/platform/src/main/java/
```

#### 3.3.3. Connectors → Shared Services

```bash
mkdir -p products/shared-services/platform/src/main/java
cp -r /Users/samujjwal/Development/ghatana/libs/java/connectors/src/main/java/* \
      products/shared-services/platform/src/main/java/
```

---

### 3.4. Security Gateway

**Legacy Source**: `ghatana/products/security-gateway`

```bash
# Create structure
mkdir -p products/security-gateway/platform/src/main/java/com/ghatana/security/gateway

# Copy legacy code
cp -r /Users/samujjwal/Development/ghatana/products/security-gateway/* \
      products/security-gateway/

# Create build file (wrapper around platform:java:auth)
cat > products/security-gateway/platform/build.gradle.kts << 'EOF'
plugins {
    id("java-library")
    id("application")
}

application {
    mainClass.set("com.ghatana.security.gateway.SecurityGatewayApplication")
}

dependencies {
    implementation(project(":platform:java:auth"))
    implementation(project(":platform:java:http"))
    implementation(libs.activej.launcher)
}
EOF
```

---

## 4. Execution Order & Dependencies

### Phase 1: Platform Consolidation (1-2 days)
1. **AEP Platform Migration** (Complex consolidation)
2. **Data Cloud Platform Migration** (Simpler, direct mapping)

### Phase 2: Services Migration (1 day)
3. **Data Cloud Services** (http-api, distributed)
4. **AEP Services** (ui-service if needed)

### Phase 3: Launchers & Integration (1 day)
5. **Create Launchers** for both products
6. **Integration Testing**

### Phase 4: Shared Services (0.5 days)
7. **Security Gateway**
8. **Shared Services consolidation**

---

## 5. Success Criteria

### Build Success
- [ ] `./gradlew :products:aep:platform:build` succeeds
- [ ] `./gradlew :products:data-cloud:platform:build` succeeds
- [ ] `./gradlew :products:security-gateway:platform:build` succeeds
- [ ] `./gradlew build` (full monorepo) succeeds

### Code Quality
- [ ] No compilation errors
- [ ] All tests pass (or documented as skipped)
- [ ] No cyclic dependencies between products
- [ ] Import statements use correct `com.ghatana.platform.*` packages

### Documentation
- [ ] Migration changelog created
- [ ] Dependency diagram updated
- [ ] Known issues documented

---

## 6. Common Pitfalls & Solutions

### Issue 1: Missing Platform Module
**Symptom**: `cannot find symbol: class SomeClass`  
**Solution**: Check if the class is in `platform:java:*`. If not, it may still be in legacy `libs/`.

### Issue 2: Circular Dependencies
**Symptom**: Gradle fails with "circular dependency detected"  
**Solution**: Move shared interfaces to `platform:contracts` or use SPI pattern.

### Issue 3: Package Name Mismatches
**Symptom**: `package com.ghatana.X does not exist`  
**Solution**: Run the sed commands again, or manually fix the package declaration.

### Issue 4: Build File Syntax Errors
**Symptom**: `Could not compile build file`  
**Solution**: Validate Gradle KTS syntax with `./gradlew help --project-dir products/aep/platform/java`

---

## 7. Post-Migration Validation Checklist

```bash
# 1. Clean build
./gradlew clean

# 2. Build all products
./gradlew :products:aep:platform:build
./gradlew :products:data-cloud:platform:build

# 3. Run tests
./gradlew :products:aep:platform:test
./gradlew :products:data-cloud:platform:test

# 4. Check for compilation warnings
./gradlew compileJava --warning-mode all

# 5. Verify no duplicate classes (same fully-qualified name in multiple JARs)
./gradlew dependencies --configuration compileClasspath
```

---

## 8. Next Steps After Migration

1. **Update CI/CD pipelines** to build products individually
2. **Create Docker images** for each product
3. **Update deployment manifests** (k8s, docker-compose)
4. **Performance testing** to ensure no regression
5. **Documentation update** for developers
