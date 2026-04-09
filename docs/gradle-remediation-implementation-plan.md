# Gradle Build System Remediation Implementation Plan

## Executive Summary

This document provides a detailed, technically-accurate implementation plan for remediating the Gradle build system based on the audit findings. All suggestions have been reviewed for technical feasibility, and corrections have been made where necessary.

**Overall Status**: PASS WITH REQUIRED REMEDIATION

**Estimated Timeline**: 15 working days (3 weeks)

---

## Critical Technical Corrections

### Correction 1: Convention Plugin Version Management

**Original Suggestion**: "Read all tool versions from version catalog using `libs.versions.*.get()` in convention plugins"

**Why This Won't Work**: Convention plugins in `buildSrc/` have an isolated classloader and CANNOT access the main project's version catalog (`gradle/libs.versions.toml`) directly. This is a known Gradle limitation.

**Current Approach (Correct)**: The current implementation uses `buildSrc/gradle.properties` as the single source of truth for buildSrc dependencies, with a manual sync process to keep it aligned with the main catalog.

**Corrected Approach**: 
- Keep the current `buildSrc/gradle.properties` approach
- Add automated validation to ensure `buildSrc/gradle.properties` stays in sync with `gradle/libs.versions.toml`
- Document the sync process clearly
- Add CI check to validate sync

**Implementation**: See Phase 2 below

---

### Correction 2: Auto-Discovery in settings.gradle.kts

**Original Suggestion**: "Implement auto-discovery for modules to reduce manual includes"

**Risk**: Gradle has no built-in auto-discovery. Custom auto-discovery logic is fragile and can:
- Include non-module directories (`.git`, `node_modules`, `build`, etc.)
- Create unpredictable project structure
- Break existing IDE integration
- Make it harder to understand what's included

**Corrected Approach**: 
- Keep manual includes but organize them hierarchically
- Use include patterns for standard structures
- Document the include pattern for new modules
- Consider auto-discovery only for very standard, predictable structures (e.g., platform-kernel submodules)

**Implementation**: See Phase 5 below

---

### Correction 3: Deleting gradle/ Scripts

**Original Suggestion**: "Delete all old gradle/ scripts"

**Risk**: Some scripts contain essential logic:
- `platform-boundary-check.gradle` - Enforces architectural boundaries
- `product-isolation.gradle` - Enforces product isolation
- `doc-tag-check.gradle` - Validates documentation tags

**Corrected Approach**:
- Migrate essential logic to convention plugins BEFORE deletion
- Keep scripts that provide value and can't be easily migrated
- Delete only truly obsolete scripts
- Consolidate related validation logic

**Implementation**: See Phase 3 below

---

## Implementation Phases

### Phase 1: Cleanup - Duplicate and Backup Files

**Duration**: 1 day
**Risk**: LOW
**Dependencies**: None

#### 1.1 Delete .new Files

**Files to Delete**:
- `settings.gradle.kts.new`
- `build.gradle.kts.new`
- `gradle/libs.versions.toml.new`
- `products/yappc/build.gradle.kts.new`
- `buildSrc/build.gradle.kts.new`
- `platform/contracts/build.gradle.kts.new`
- `platform-kernel/kernel-core/build.gradle.kts.new`
- `products/data-cloud/ui/src/features/workflow/stores/execution.store.ts.new`
- `products/dcmaar/apps/device-health/src/core/utils/fileStorage.js.new`
- `products/dcmaar/modules/desktop/src/stores/extensionStore.ts.new`
- `products/yappc/core/refactorer/engine/src/main/java/com/ghatana/refactorer/rewriters/GoToolsRunner.java.new`

**Command**:
```bash
find /home/samujjwal/Developments/ghatana -name "*.new" -type f -delete
```

**Verification**:
```bash
find /home/samujjwal/Developments/ghatana -name "*.new" -type f
# Should return empty
```

---

#### 1.2 Delete Duplicate Convention Plugin Variants

**Files to Delete**:
- `buildSrc/src/main/kotlin/com.ghatana.lombok-conventions-fixed.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.database-conventions-fixed.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.database-conventions-simple.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.product-conventions-fixed.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.product-conventions-simple.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.protobuf-conventions-fixed.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.protobuf-conventions-simple.gradle.kts`
- `buildSrc/src/main/kotlin/com.ghatana.testing-conventions-new.gradle.kts`

**Command**:
```bash
rm -f buildSrc/src/main/kotlin/*-fixed.gradle.kts
rm -f buildSrc/src/main/kotlin/*-simple.gradle.kts
rm -f buildSrc/src/main/kotlin/*-new.gradle.kts
```

**Verification**:
```bash
ls -la buildSrc/src/main/kotlin/
# Should only show canonical plugins
```

---

#### 1.3 Delete Empty Convention Plugins

**Files to Delete**:
- `buildSrc/src/main/kotlin/com.ghatana.database-conventions.gradle.kts` (empty, only comment)
- `buildSrc/src/main/kotlin/com.ghatana.product-conventions.gradle.kts` (empty, only comment)

**Note**: These will be recreated with actual configuration in Phase 3.

**Command**:
```bash
rm -f buildSrc/src/main/kotlin/com.ghatana.database-conventions.gradle.kts
rm -f buildSrc/src/main/kotlin/com.ghatana.product-conventions.gradle.kts
```

---

#### 1.4 Delete Backup Directory

**Directory to Delete**:
- `.gradle-backup-20260408-185947/`

**Command**:
```bash
rm -rf .gradle-backup-20260408-185947/
```

---

#### 1.5 Clean buildSrc Build Artifacts

**Directory to Clean**:
- `buildSrc/build/`

**Command**:
```bash
rm -rf buildSrc/build/
```

**Note**: This will be regenerated on next build.

---

### Phase 2: Convention Plugin Version Management

**Duration**: 2 days
**Risk**: MEDIUM
**Dependencies**: Phase 1 complete

#### 2.1 Document buildSrc Version Sync Process

**Create**: `buildSrc/VERSION_SYNC.md`

**Content**:
```markdown
# buildSrc Version Synchronization

## Background

Gradle's buildSrc has an isolated classloader and CANNOT access the main project's
version catalog (gradle/libs.versions.toml) directly. This is a known Gradle limitation.

## Solution

We use `buildSrc/gradle.properties` as the single source of truth for buildSrc
classpath dependencies. This file is kept in sync with the main version catalog.

## Sync Rules

1. When updating a version in `gradle/libs.versions.toml`, also update the corresponding
   property in `buildSrc/gradle.properties` in the same commit.

2. Property naming convention:
   - buildSrc/gradle.properties: camelCase (e.g., `spotlessVersion`)
   - gradle/libs.versions.toml: kebab-case (e.g., `spotless`)

3. The CI "Verify buildSrc version sync" step enforces this contract.

## Current Mappings

| buildSrc/gradle.properties | gradle/libs.versions.toml |
|---------------------------|--------------------------|
| spotlessVersion           | spotless                 |
| spotbugsPluginVersion     | spotbugs-plugin          |
| saxonHeVersion            | saxon-he                 |
| httpclient5Version        | httpclient5              |
| httpcore5Version          | httpcore5                |

## Adding New buildSrc Dependencies

1. Add version to `buildSrc/gradle.properties`
2. Add corresponding version to `gradle/libs.versions.toml`
3. Add dependency to `buildSrc/build.gradle.kts`
4. Update this documentation
```

---

#### 2.2 Add CI Validation for Version Sync

**Create**: `.github/workflows/verify-buildsrc-sync.yml`

**Content**:
```yaml
name: Verify buildSrc Version Sync

on:
  pull_request:
    paths:
      - 'buildSrc/gradle.properties'
      - 'gradle/libs.versions.toml'
  push:
    paths:
      - 'buildSrc/gradle.properties'
      - 'gradle/libs.versions.toml'

jobs:
  verify-sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Verify buildSrc and catalog are in sync
        run: |
          # Extract versions from buildSrc/gradle.properties
          SPOTLESS_BUILD=$(grep "^spotlessVersion=" buildSrc/gradle.properties | cut -d'=' -f2)
          SPOTBUGS_BUILD=$(grep "^spotbugsPluginVersion=" buildSrc/gradle.properties | cut -d'=' -f2)
          SAXON_BUILD=$(grep "^saxonHeVersion=" buildSrc/gradle.properties | cut -d'=' -f2)
          HTTPCLIENT_BUILD=$(grep "^httpclient5Version=" buildSrc/gradle.properties | cut -d'=' -f2)
          HTTPCORE_BUILD=$(grep "^httpcore5Version=" buildSrc/gradle.properties | cut -d'=' -f2)
          
          # Extract versions from gradle/libs.versions.toml
          SPOTLESS_CATALOG=$(grep "^spotless = \"" gradle/libs.versions.toml | sed 's/spotless = "\([^"]*\)"/\1/')
          SPOTBUGS_CATALOG=$(grep "^spotbugs-plugin = \"" gradle/libs.versions.toml | sed 's/spotbugs-plugin = "\([^"]*\)"/\1/')
          SAXON_CATALOG=$(grep "^saxon-he = \"" gradle/libs.versions.toml | sed 's/saxon-he = "\([^"]*\)"/\1/')
          HTTPCLIENT_CATALOG=$(grep "^httpclient5 = \"" gradle/libs.versions.toml | sed 's/httpclient5 = "\([^"]*\)"/\1/')
          HTTPCORE_CATALOG=$(grep "^httpcore5 = \"" gradle/libs.versions.toml | sed 's/httpcore5 = "\([^"]*\)"/\1/')
          
          # Compare
          if [ "$SPOTLESS_BUILD" != "$SPOTLESS_CATALOG" ]; then
            echo "ERROR: spotless version mismatch"
            echo "  buildSrc/gradle.properties: $SPOTLESS_BUILD"
            echo "  gradle/libs.versions.toml: $SPOTLESS_CATALOG"
            exit 1
          fi
          
          if [ "$SPOTBUGS_BUILD" != "$SPOTBUGS_CATALOG" ]; then
            echo "ERROR: spotbugs-plugin version mismatch"
            echo "  buildSrc/gradle.properties: $SPOTBUGS_BUILD"
            echo "  gradle/libs.versions.toml: $SPOTBUGS_CATALOG"
            exit 1
          fi
          
          if [ "$SAXON_BUILD" != "$SAXON_CATALOG" ]; then
            echo "ERROR: saxon-he version mismatch"
            echo "  buildSrc/gradle.properties: $SAXON_BUILD"
            echo "  gradle/libs.versions.toml: $SAXON_CATALOG"
            exit 1
          fi
          
          if [ "$HTTPCLIENT_BUILD" != "$HTTPCLIENT_CATALOG" ]; then
            echo "ERROR: httpclient5 version mismatch"
            echo "  buildSrc/gradle.properties: $HTTPCLIENT_BUILD"
            echo "  gradle/libs.versions.toml: $HTTPCLIENT_CATALOG"
            exit 1
          fi
          
          if [ "$HTTPCORE_BUILD" != "$HTTPCORE_CATALOG" ]; then
            echo "ERROR: httpcore5 version mismatch"
            echo "  buildSrc/gradle.properties: $HTTPCORE_BUILD"
            echo "  gradle/libs.versions.toml: $HTTPCORE_CATALOG"
            exit 1
          fi
          
          echo "All versions are in sync"
```

---

#### 2.3 Add Lombok Version to buildSync/gradle.properties

**Current State**: Lombok version is hardcoded in `com.ghatana.lombok-conventions.gradle.kts`

**Action**: Add to `buildSrc/gradle.properties`

**Add to buildSrc/gradle.properties**:
```properties
# Lombok — annotation processor
# Sync target: [versions] lombok = "1.18.36"
lombokVersion=1.18.36
```

**Add to gradle/libs.versions.toml** (if not already present):
```toml
[versions]
lombok = "1.18.36"
```

**Update com.ghatana.lombok-conventions.gradle.kts**:
```kotlin
dependencies {
    // Read from buildSrc/gradle.properties
    val lombokVersion: String by project
    val lombokCoordinate = "org.projectlombok:lombok:$lombokVersion"

    compileOnly(lombokCoordinate)
    annotationProcessor(lombokCoordinate)
    testCompileOnly(lombokCoordinate)
    testAnnotationProcessor(lombokCoordinate)
}
```

---

#### 2.4 Add Tool Versions to buildSrc/gradle.properties

**Add to buildSrc/gradle.properties**:
```properties
# Quality tools — used by convention plugins
# Sync target: [versions] checkstyle = "10.21.4"
checkstyleVersion=10.21.4

# Sync target: [versions] pmd = "7.11.0"
pmdVersion=7.11.0

# Sync target: [versions] jacoco = "0.8.14"
jacocoVersion=0.8.14
```

**Update com.ghatana.quality-conventions.gradle.kts**:
```kotlin
configure<CheckstyleExtension> {
    val checkstyleVersion: String by project
    toolVersion = checkstyleVersion
    // ... rest of configuration
}

configure<PmdExtension> {
    val pmdVersion: String by project
    toolVersion = pmdVersion
    // ... rest of configuration
}

configure<JacocoPluginExtension> {
    val jacocoVersion: String by project
    toolVersion = jacocoVersion
}
```

**Update com.ghatana.testing-conventions.gradle.kts**:
```kotlin
configure<JacocoPluginExtension> {
    val jacocoVersion: String by project
    toolVersion = jacocoVersion
}
```

---

### Phase 3: Build Logic Consolidation

**Duration**: 3 days
**Risk**: HIGH
**Dependencies**: Phase 1, Phase 2 complete

#### 3.1 Migrate Platform Boundary Check to Convention Plugin

**Current**: `gradle/platform-boundary-check.gradle`

**Action**: Create `buildSrc/src/main/kotlin/com.ghatana.platform-conventions.gradle.kts`

**Content**:
```kotlin
/**
 * Platform Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Configures platform modules and enforces architectural boundaries
 * @doc.layer build
 * @doc.pattern Convention
 */
plugins {
    java
}

// Enforce platform boundary: platform modules cannot depend on product modules
afterEvaluate {
    val isPlatformModule = project.path.startsWith(":platform")
    
    if (isPlatformModule) {
        val violations = mutableListOf<String>()
        
        // Check project dependencies
        configurations.named("implementation").get().all {
            dependencies.all {
                if (this is ProjectDependency) {
                    val dependencyPath = this.dependencyProject.path
                    if (dependencyPath.startsWith(":products")) {
                        violations.add("Platform module $projectPath depends on product module $dependencyPath")
                    }
                }
            }
        }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Platform boundary violations:\n${violations.joinToString("\n") { "  - $it" }}"
            )
        }
    }
}
```

**Delete**: `gradle/platform-boundary-check.gradle`

**Update**: Remove from root `build.gradle.kts`:
```kotlin
// Remove this line:
apply(from = "gradle/platform-boundary-check.gradle")
```

---

#### 3.2 Migrate Product Isolation to Convention Plugin

**Current**: `gradle/product-isolation.gradle`

**Action**: Enhance `buildSrc/src/main/kotlin/com.ghatana.product-conventions.gradle.kts`

**Content**:
```kotlin
/**
 * Product Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Configures product modules and enforces product isolation
 * @doc.layer build
 * @doc.pattern Convention
 */
plugins {
    java
}

// Enforce product isolation: product modules cannot depend on other products
afterEvaluate {
    val isProductModule = project.path.startsWith(":products")
    
    if (isProductModule) {
        val currentProduct = project.path.substringAfter(":products:").substringBefore(":")
        val violations = mutableListOf<String>()
        
        // Allowed cross-product dependencies
        val allowedCrossProduct = setOf(
            "aep:yappc",  // AEP can depend on YAPPC
            // Add other allowed pairs as needed
        )
        
        // Check project dependencies
        configurations.named("implementation").get().all {
            dependencies.all {
                if (this is ProjectDependency) {
                    val dependencyPath = this.dependencyProject.path
                    if (dependencyPath.startsWith(":products")) {
                        val targetProduct = dependencyPath.substringAfter(":products:").substringBefore(":")
                        if (targetProduct != currentProduct) {
                            val pair = "${currentProduct}:${targetProduct}"
                            if (!allowedCrossProduct.contains(pair)) {
                                violations.add("Product $currentProduct depends on product $targetProduct")
                            }
                        }
                    }
                }
            }
        }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Product isolation violations:\n${violations.joinToString("\n") { "  - $it" }}"
            )
        }
    }
}
```

**Delete**: `gradle/product-isolation.gradle`

**Update**: Remove from root `build.gradle.kts`:
```kotlin
// Remove this line:
apply(from = "gradle/product-isolation.gradle")
```

---

#### 3.3 Migrate Doc Tag Check to Convention Plugin

**Current**: `gradle/doc-tag-check.gradle`

**Action**: Add to `buildSrc/src/main/kotlin/com.ghatana.quality-conventions.gradle.kts`

**Add at end of file**:
```kotlin
// ── Documentation Tag Validation ───────────────────────────────────────────────
tasks.register("validateDocTags") {
    group = "verification"
    description = "Validates that Java classes have required @doc.* tags"
    
    inputs.dir("src/main/java")
    
    doLast {
        val violations = mutableListOf<String>()
        
        fileTree("src/main/java") {
            include("**/*.java")
        }.forEach { file ->
            val content = file.readText()
            
            // Check for @doc.type
            if (content.contains("public class") || content.contains("public interface")) {
                if (!content.contains("@doc.type")) {
                    violations.add("${file.path}: Missing @doc.type tag")
                }
                if (!content.contains("@doc.purpose")) {
                    violations.add("${file.path}: Missing @doc.purpose tag")
                }
            }
        }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Documentation tag violations:\n${violations.joinToString("\n") { "  - $it" }}"
            )
        }
    }
}
```

**Delete**: `gradle/doc-tag-check.gradle`

---

#### 3.4 Delete Obsolete gradle/ Scripts

**Scripts to Delete** (after migrating essential logic):
- `gradle/build-validation.gradle` - migrate essential parts to convention plugin
- `gradle/build-isolation.gradle` - use Gradle built-in features
- `gradle/java-conventions.gradle` - replaced by convention plugins
- `gradle/common-build.gradle.kts` - replaced by convention plugins
- `gradle/conventions/conventions.gradle.kts` - replaced by convention plugins
- `gradle/conventions/checkstyle.gradle` - in quality-conventions
- `gradle/conventions/java.gradle` - in java-conventions
- `gradle/conventions/pmd.gradle` - in quality-conventions
- `gradle/conventions/spotbugs.gradle` - in quality-conventions
- `gradle/conventions/spotless.gradle` - in quality-conventions
- `gradle/test-module.gradle.kts` - use templates
- `gradle/platform-bom.gradle.kts` - use java-platform plugin
- `gradle/source-sets-*.gradle` - use convention plugins

**Scripts to Keep** (for now):
- `gradle/wrapper/` - Gradle wrapper (essential)
- `gradle/libs.versions.toml` - Version catalog (essential)
- `gradle/templates/` - Module templates (useful)

**Command**:
```bash
# Delete obsolete scripts
rm -f gradle/build-validation.gradle
rm -f gradle/build-isolation.gradle
rm -f gradle/java-conventions.gradle
rm -f gradle/common-build.gradle.kts
rm -f gradle/platform-bom.gradle.kts
rm -f gradle/test-module.gradle.kts
rm -f gradle/source-sets-*.gradle

# Delete conventions directory
rm -rf gradle/conventions/
```

---

#### 3.5 Remove Subprojects Block from Root Build

**Current**: Root `build.gradle.kts` has subprojects block that automatically applies convention plugins

**Action**: Remove subprojects block, make plugin application explicit in modules

**Remove from build.gradle.kts**:
```kotlin
// DELETE THIS ENTIRE BLOCK:
subprojects {
    if (hasJavaSources()) {
        apply(plugin = "com.ghatana.java-conventions")
        apply(plugin = "com.ghatana.testing-conventions")
        apply(plugin = "com.ghatana.quality-conventions")
        apply(plugin = "com.ghatana.lombok-conventions")
    }
}

// DELETE THIS HELPER FUNCTION:
fun hasJavaSources(): Boolean {
    return file("$projectDir/src/main/java").exists() ||
           file("$projectDir/src/main/kotlin").exists() ||
           file("$projectDir/src/test/java").exists() ||
           file("$projectDir/src/test/kotlin").exists()
}
```

**Note**: This means modules must explicitly apply convention plugins. This is actually good for explicitness and performance.

---

### Phase 4: Module Cleanup

**Duration**: 5 days
**Risk**: MEDIUM
**Dependencies**: Phase 3 complete

#### 4.1 Remove Manual Java Configuration from Modules

**Modules to Update** (18+ identified):
- `platform/java/core/build.gradle.kts`
- `platform/java/database/build.gradle.kts`
- `platform/java/workflow/build.gradle.kts`
- `platform-kernel/kernel-core/build.gradle.kts`
- `platform-kernel/kernel-plugin/build.gradle.kts`
- `platform-kernel/kernel-testing/build.gradle.kts`
- `platform-kernel/kernel-persistence/build.gradle.kts`
- `platform/java/governance/build.gradle.kts`
- `platform/java/tool-runtime/build.gradle.kts`
- `platform-plugins/plugin-risk-management/build.gradle.kts`
- `platform/java/audit/build.gradle.kts`
- `products/yappc/libs/java/yappc-domain/build.gradle.kts`
- `platform-plugins/plugin-audit-trail/build.gradle.kts`
- `platform/java/incident-response/build.gradle.kts`
- `products/yappc/services/build.gradle.kts`
- `products/yappc/infrastructure/datacloud/build.gradle.kts`
- `platform/java/agent-memory/build.gradle.kts`
- `integration-tests/phr-finance-integration/build.gradle.kts`

**Pattern to Remove**:
```kotlin
// DELETE THIS BLOCK:
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

**Replacement**: Ensure module applies `com.ghatana.java-conventions` plugin:
```kotlin
plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    // ... other plugins
}
```

**Automation Script**:
```bash
#!/bin/bash
# scripts/remove-manual-java-config.sh

MODULES=(
    "platform/java/core/build.gradle.kts"
    "platform/java/database/build.gradle.kts"
    "platform/java/workflow/build.gradle.kts"
    # ... add all modules
)

for module in "${MODULES[@]}"; do
    echo "Processing $module"
    # Remove manual java block
    sed -i '/^java {$/,/^}$/d' "$module"
    
    # Ensure java-conventions is applied
    if ! grep -q "com.ghatana.java-conventions" "$module"; then
        sed -i '/^plugins {$/a\    id("com.ghatana.java-conventions")' "$module"
    fi
done
```

---

#### 4.2 Replace Hardcoded Dependency Versions

**Modules to Update**:
- `platform/java/core/build.gradle.kts` - jakarta.validation-api, javax.annotation-api, archunit
- `platform/java/database/build.gradle.kts` - jakarta.persistence-api, hibernate-core, lettuce-core

**Add to gradle/libs.versions.toml**:
```toml
[versions]
jakarta-validation = "3.0.2"
javax-annotation = "1.3.2"
hibernate = "6.6.3.Final"
lettuce = "6.4.0.RELEASE"
archunit = "1.3.0"

[libraries]
jakarta-validation-api = { module = "jakarta.validation:jakarta.validation-api", version.ref = "jakarta-validation" }
javax-annotation-api = { module = "javax.annotation:javax.annotation-api", version.ref = "javax-annotation" }
hibernate-core = { module = "org.hibernate.orm:hibernate-core", version.ref = "hibernate" }
lettuce-core = { module = "io.lettuce:lettuce-core", version.ref = "lettuce" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
```

**Update platform/java/core/build.gradle.kts**:
```kotlin
dependencies {
    // Replace:
    // api("jakarta.validation:jakarta.validation-api:3.0.2")
    // With:
    api(libs.jakarta.validation.api)
    
    // Replace:
    // api("javax.annotation:javax.annotation-api:1.3.2")
    // With:
    api(libs.javax.annotation.api)
    
    // Replace:
    // testImplementation("com.tngtech.archunit:archunit:1.3.0")
    // With:
    testImplementation(libs.archunit.junit5)
}
```

**Update platform/java/database/build.gradle.kts**:
```kotlin
dependencies {
    // Replace:
    // api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    // With:
    api(libs.jakarta.persistence.api)  // Add to catalog first
    
    // Replace:
    // api("org.hibernate.orm:hibernate-core:6.6.1.Final")
    // With:
    api(libs.hibernate.core)
    
    // Replace:
    // api("io.lettuce:lettuce-core:6.4.0.RELEASE")
    // With:
    api(libs.lettuce.core)
}
```

**Note**: Need to add `jakarta-persistence` to catalog first:
```toml
[versions]
jakarta-persistence = "3.1.0"

[libraries]
jakarta-persistence-api = { module = "jakarta.persistence:jakarta.persistence-api", version.ref = "jakarta-persistence" }
```

---

#### 4.3 Remove Manual Test Configuration

**Modules to Update**:
- All modules with manual `tasks.test { useJUnitPlatform() }` blocks

**Pattern to Remove**:
```kotlin
// DELETE THIS BLOCK:
tasks.test {
    useJUnitPlatform()
}
```

**Replacement**: Ensure module applies `com.ghatana.testing-conventions` plugin:
```kotlin
plugins {
    id("java-library")
    id("com.ghatana.testing-conventions")
    // ... other plugins
}
```

---

#### 4.4 Ensure All Modules Use Appropriate Convention Plugins

**Standard Pattern for Java Library Modules**:
```kotlin
plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    id("com.ghatana.lombok-conventions")
    id("com.ghatana.quality-conventions")
}
```

**Standard Pattern for Application Modules**:
```kotlin
plugins {
    id("application")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    id("com.ghatana.lombok-conventions")
    id("com.ghatana.quality-conventions")
    id("com.ghatana.application-conventions")
}
```

**Standard Pattern for Platform Modules**:
```kotlin
plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    id("com.ghatana.lombok-conventions")
    id("com.ghatana.quality-conventions")
    id("com.ghatana.platform-conventions")
}
```

**Standard Pattern for Product Modules**:
```kotlin
plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    id("com.ghatana.lombok-conventions")
    id("com.ghatana.quality-conventions")
    id("com.ghatana.product-conventions")
}
```

---

### Phase 5: Settings Simplification

**Duration**: 2 days
**Risk**: MEDIUM
**Dependencies**: Phase 4 complete

#### 5.1 Fix Redundant Nested dependencyResolutionManagement

**Current**: `settings.gradle.kts` has redundant nested block

**Remove**:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    // DELETE THIS REDUNDANT NESTED BLOCK:
    dependencyResolutionManagement {
        @Suppress("UnstableApiUsage")
        configurations {
            all {
                resolutionStrategy {
                    cacheDynamicVersionsFor(24, "hours")
                    cacheChangingModulesFor(24, "hours")
                    failOnVersionConflict()
                    force(
                        "org.slf4j:slf4j-api:2.0.16",
                        "com.fasterxml.jackson.core:jackson-core:2.18.2",
                        "org.junit:junit-bom:5.11.4"
                    )
                }
            }
        }
    }
}
```

**Corrected**:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    @Suppress("UnstableApiUsage")
    configurations {
        all {
            resolutionStrategy {
                cacheDynamicVersionsFor(24, "hours")
                cacheChangingModulesFor(24, "hours")
                failOnVersionConflict()
                force(
                    "org.slf4j:slf4j-api:2.0.16",
                    "com.fasterxml.jackson.core:jackson-core:2.18.2",
                    "org.junit:junit-bom:5.11.4"
                )
            }
        }
    }
}
```

---

#### 5.2 Organize Project Includes Hierarchically

**Current**: 295 lines with 100+ manual includes

**Improved**: Organize by area with comments, reduce to ~200 lines

**Reorganized structure**:
```kotlin
// =============================================================================
// Platform Kernel
// =============================================================================
include(":platform-kernel:kernel-core")
include(":platform-kernel:kernel-plugin")
include(":platform-kernel:kernel-persistence")
include(":platform-kernel:kernel-testing")
include(":platform-kernel:kernel-bom")

// =============================================================================
// Platform Plugins
// =============================================================================
include(":platform-plugins:plugin-audit-trail")
include(":platform-plugins:plugin-billing-ledger")
include(":platform-plugins:plugin-compliance")
include(":platform-plugins:plugin-consent")
include(":platform-plugins:plugin-fraud-detection")
include(":platform-plugins:plugin-risk-management")

// =============================================================================
// Platform Java - Core
// =============================================================================
include(":platform:java:core")
include(":platform:java:domain")
include(":platform:java:database")
include(":platform:java:http")
include(":platform:java:observability")
include(":platform:java:config")
include(":platform:java:workflow")
include(":platform:java:audit")
include(":platform:java:agent-core")
include(":platform:java:agent-dispatch")
include(":platform:java:ai-integration")
include(":platform:java:agent-memory")
include(":platform:java:governance")
include(":platform:java:tool-runtime")
include(":platform:java:runtime")
include(":platform:java:identity")
include(":platform:java:security")
include(":platform:java:connectors")
include(":platform:java:audio-video")
include(":platform:java:policy-as-code")
include(":platform:java:security-analytics")
include(":platform:java:data-governance")
include(":platform:java:distributed-cache")
include(":platform:java:incident-response")
include(":platform:java:testing")

// ... continue for products, shared-services, integration-tests
```

**Note**: We keep manual includes for now. Auto-discovery is too risky for this phase.

---

### Phase 6: Validation Optimization

**Duration**: 2 days
**Risk**: LOW
**Dependencies**: Phase 5 complete

#### 6.1 Move Expensive Validations to CI-Only Task

**Current**: Validation tasks run on every build (wired into `check` task)

**Action**: Create separate CI-only validation task

**Create**: `gradle/ci-validation.gradle`

**Content**:
```kotlin
/**
 * CI-Only Validation Tasks
 *
 * These tasks are expensive and should only run in CI, not during local development.
 * Run with: ./gradlew ciValidate
 */

tasks.register("ciValidate") {
    group = "verification"
    description = "Runs all CI-only validations (not run on local builds)"
    
    dependsOn("validateDependencies")
    dependsOn("validateConventionPlugins")
    dependsOn("validateModuleStructure")
    dependsOn("validateDocTags")
    dependsOn("validateBuildIsolation")
    
    doLast {
        println("=== CI Validation Completed ===")
    }
}
```

**Remove from check task**:
```kotlin
// In build.gradle.kts or convention plugins, remove:
// tasks.named("check") { dependsOn("validateAll") }
```

**Update CI workflow**:
```yaml
# In .github/workflows/ci.yml
- name: Run CI validations
  run: ./gradlew ciValidate
```

---

#### 6.2 Make Validation Tasks Configuration Cache Compatible

**Current**: Some validation tasks access file system during configuration

**Action**: Move file system access to execution phase

**Example Fix**:
```kotlin
// BEFORE (not cache compatible):
tasks.register("validateSomething") {
    val fileContent = file("somefile.txt").readText()  // BAD: during configuration
    doLast {
        if (fileContent.contains("bad")) {
            throw GradleException("Bad content")
        }
    }
}

// AFTER (cache compatible):
tasks.register("validateSomething") {
    inputs.file("somefile.txt")
    doLast {
        val fileContent = file("somefile.txt").readText()  // GOOD: during execution
        if (fileContent.contains("bad")) {
            throw GradleException("Bad content")
        }
    }
}
```

---

#### 6.3 Remove Task Graph Inspection

**Current**: `gradle/build-isolation.gradle` inspects task graph during execution

**Action**: Use Gradle's built-in dependency verification instead

**Delete**: `gradle/build-isolation.gradle`

**Alternative**: Use Gradle's built-in features:
```kotlin
// In settings.gradle.kts
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    @Suppress("UnstableApiUsage")
    configurations {
        all {
            resolutionStrategy {
                // Gradle built-in conflict resolution
                failOnVersionConflict()
                
                // Force consistent versions
                force(
                    "org.slf4j:slf4j-api:${libs.versions.slf4j.get()}",
                    "com.fasterxml.jackson.core:jackson-core:${libs.versions.jackson.get()}"
                )
            }
        }
    }
}
```

---

## Verification Steps

### After Each Phase

**Phase 1 Verification**:
```bash
# Check no .new files
find /home/samujjwal/Developments/ghatana -name "*.new" -type f
# Should return empty

# Check no duplicate plugins
ls -la buildSrc/src/main/kotlin/
# Should only show canonical plugins

# Check backup directory deleted
ls -la .gradle-backup-20260408-185947/
# Should fail (directory doesn't exist)

# Build should still work
./gradlew clean build --no-configuration-cache
```

**Phase 2 Verification**:
```bash
# Check version sync
.github/workflows/verify-buildsrc-sync.yml
# Should pass

# Build should still work
./gradlew clean build --no-configuration-cache
```

**Phase 3 Verification**:
```bash
# Check gradle directory cleanup
ls -la gradle/
# Should only show: wrapper/, libs.versions.toml, templates/

# Check boundary checks still work
./gradlew :platform:java:core:build
# Should enforce platform boundaries

# Build should still work
./gradlew clean build --no-configuration-cache
```

**Phase 4 Verification**:
```bash
# Check no manual Java config
grep -r "languageVersion.set(JavaLanguageVersion.of(21))" platform/
# Should return empty

# Check no hardcoded versions
grep -r "\"[0-9]\+\.[0-9]\+\.[0-9]\+\"" platform/java/core/build.gradle.kts
# Should return empty (except comments)

# Build should still work
./gradlew clean build --no-configuration-cache
```

**Phase 5 Verification**:
```bash
# Check settings.gradle.kts reduced
wc -l settings.gradle.kts
# Should be ~200 lines (down from 295)

# Build should still work
./gradlew clean build --no-configuration-cache
```

**Phase 6 Verification**:
```bash
# Check CI validation task
./gradlew ciValidate
# Should run successfully

# Check configuration cache
./gradlew clean build --configuration-cache
# Should use cache (no warnings for standard tasks)

# Check check task is faster (no expensive validations)
time ./gradlew check
# Should be faster than before
```

### Final Verification

```bash
# Full build
./gradlew clean build --configuration-cache

# CI validation
./gradlew ciValidate

# Check specific modules
./gradlew :platform:java:core:build
./gradlew :platform:java:database:build
./gradlew :products:yappc:build
./gradlew :shared-services:auth-gateway:build

# Verify no configuration cache warnings
./gradlew clean build --configuration-cache
# Should have minimal warnings (only for custom validation tasks)

# Count lines in key files
wc -l settings.gradle.kts
wc -l build.gradle.kts
ls -la buildSrc/src/main/kotlin/
ls -la gradle/
```

---

## Rollback Plan

If any phase causes issues:

1. **Git commit after each phase** - This allows rollback to any previous phase
2. **Branch strategy** - Create feature branch for each phase
3. **Testing after each phase** - Verify build passes before proceeding

**Rollback Commands**:
```bash
# Rollback to specific phase
git revert <commit-hash>

# Or reset to before phase
git reset --hard <commit-hash>
```

---

## Success Criteria

**Quantitative Metrics**:
- Settings.gradle.kts: 295 → ~200 lines (32% reduction)
- Root build.gradle.kts: 180 → ~50 lines (72% reduction)
- Convention plugins: 17 → 8 canonical plugins
- Duplicate files: 19 → 0
- Manual Java config: 18+ → 0 modules
- Hardcoded versions: 6+ → 0 in modules

**Qualitative Metrics**:
- Single clear pattern for module builds
- Consistent use of convention plugins
- Centralized version management with validation
- Configuration cache compatible
- No cross-product task dependencies
- Easy to add/remove modules

**Build Performance**:
- Configuration time: No regression
- Build time: No regression
- Configuration cache: Working with minimal warnings

---

## Summary

This implementation plan provides a detailed, technically-accurate approach to remediating the Gradle build system. Key corrections from the original audit:

1. **Convention plugins CANNOT access the main version catalog** - Keep the current `buildSrc/gradle.properties` approach with validation
2. **Auto-discovery is risky** - Use hierarchical manual includes instead
3. **Essential logic in gradle/ scripts must be migrated before deletion** - Don't delete blindly

The plan is phased to minimize risk, with verification after each phase and a clear rollback strategy.

**Estimated Timeline**: 15 working days (3 weeks)

**Expected Outcome**: Production-grade build system suitable for large monorepo development.
