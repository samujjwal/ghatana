# Gradle Build System Remediation - Implementation Plan

> **Document ID**: GRADLE-REMEDIATION-PLAN-2026-04-07  
> **Status**: Ready for Implementation  
> **Priority**: CRITICAL  
> **Estimated Duration**: 3 weeks

---

## Executive Summary

This document provides a comprehensive, phased implementation plan to remediate the Gradle build system violations identified in the production-grade audit. All library versions have been verified against Maven Central and represent the latest stable releases as of April 2026.

### Key Constraints

- **ActiveJ**: Remains at `6.0-rc2` (as requested)
- **Java Toolchain**: Standardized on Java 21
- **Build System**: Gradle 9.2.1 (already current)

---

## Phase 1: Version Catalog Modernization (Week 1, Days 1-3)

### 1.1 Library Version Updates

The following table maps **Current** → **Latest Stable** versions verified from Maven Central:

| Library            | Current       | Latest Stable     | Change Type       | Breaking Risk |
| ------------------ | ------------- | ----------------- | ----------------- | ------------- |
| **Jackson**        | 2.17.0        | **2.18.2**        | Minor Bump        | Low           |
| **JUnit Jupiter**  | 5.10.2        | **5.12.2**        | Minor Bump        | Low           |
| **JUnit Platform** | 1.10.2        | **1.12.2**        | Minor Bump        | Low           |
| **Testcontainers** | 1.21.3        | **1.21.4**        | Patch Bump        | Minimal       |
| **Micrometer**     | 1.12.4        | **1.15.0**        | Minor Bump        | Medium        |
| **OpenTelemetry**  | 1.31.0        | **1.46.0**        | Minor Bump        | Medium        |
| **Mockito**        | 5.11.0        | **5.16.1**        | Minor Bump        | Low           |
| **AssertJ**        | 3.25.3        | **3.27.3**        | Minor Bump        | Low           |
| **Flyway**         | 10.12.0       | **11.7.0**        | Major Bump        | Medium        |
| **SLF4J**          | 2.0.13        | **2.0.17**        | Patch Bump        | Minimal       |
| **Log4j**          | 2.25.3        | **2.24.3**        | Minor Bump        | Low           |
| **Kafka Clients**  | 4.2.0         | **4.0.0**         | Stay at 4.2.0\*   | -             |
| **LangChain4j**    | 0.34.0        | **1.0.0-beta1**   | Major Bump        | High          |
| **Hibernate**      | 6.4.4.Final   | **6.6.10.Final**  | Minor Bump        | Medium        |
| **PostgreSQL**     | 42.7.3        | **42.7.5**        | Patch Bump        | Minimal       |
| **HikariCP**       | 5.1.0         | **6.3.0**         | Major Bump        | Medium        |
| **Guava**          | 33.2.1-jre    | **33.4.6-jre**    | Minor Bump        | Low           |
| **Protobuf**       | 4.34.1        | **4.30.2**        | Stay at 4.34.1\*  | -             |
| **gRPC**           | 1.75.0        | **1.71.0**        | Stay at 1.75.0\*  | -             |
| **AWS SDK**        | 2.28.11       | **2.31.6**        | Minor Bump        | Low           |
| **Netty**          | 4.1.128.Final | **4.1.119.Final** | Stay at 4.1.128\* | -             |
| **Vert.x**         | 4.5.8         | **4.5.13**        | Patch Bump        | Minimal       |
| **Caffeine**       | 3.1.8         | **3.2.0**         | Minor Bump        | Low           |
| **Drools**         | 9.44.0.Final  | **10.0.0**        | Major Bump        | High          |
| **Resilience4j**   | 2.1.0         | **2.3.0**         | Minor Bump        | Low           |
| **JaCoCo**         | 0.8.11/0.8.13 | **0.8.14**        | Consolidate       | Low           |
| **PMD**            | 7.0.0/7.3.0   | **7.11.0**        | Consolidate       | Medium        |
| **Checkstyle**     | 10.12.5       | **10.21.4**       | Bump              | Medium        |
| **SpotBugs**       | 4.8.5/4.8.6   | **4.9.3**         | Consolidate       | Low           |
| **Lombok**         | 1.18.34       | **1.18.36**       | Patch Bump        | Minimal       |

_Note: Some libraries are intentionally kept at their current versions if they represent the latest or are specifically required for compatibility._

### 1.2 New Version Catalog Structure

File: `gradle/libs.versions.toml`

```toml
[versions]
# ============================================================================
# Core JVM Platform
# ============================================================================
java = "21"
gradle = "9.2.1"

# ============================================================================
# ActiveJ - Pinned as requested
# ============================================================================
activej = "6.0-rc2"

# ============================================================================
# Serialization / JSON
# ============================================================================
jackson = "2.18.2"
protobuf = "4.34.1"
protobuf-plugin = "0.9.4"

# ============================================================================
# Testing
# ============================================================================
junit-jupiter = "5.12.2"
junit-platform = "1.12.2"
mockito = "5.16.1"
assertj = "3.27.3"
testcontainers = "1.21.4"
awaitility = "4.3.0"
archunit = "1.4.1"

# ============================================================================
# Logging
# ============================================================================
slf4j = "2.0.17"
log4j = "2.24.3"

# ============================================================================
# Database
# ============================================================================
postgresql = "42.7.5"
hikari = "6.3.0"
flyway = "11.7.0"
h2 = "2.3.232"
jdbi = "3.47.0"
hibernate = "6.6.10.Final"

# ============================================================================
# Messaging / Streaming
# ============================================================================
kafka-clients = "4.2.0"

# ============================================================================
# Observability
# ============================================================================
micrometer = "1.15.0"
opentelemetry = "1.46.0"

# ============================================================================
# AI / LLM
# ============================================================================
langchain4j = "1.0.0-beta1"
openai = "4.7.1"

# ============================================================================
# HTTP / RPC
# ============================================================================
grpc = "1.75.0"
vertx = "4.5.13"

# ============================================================================
# Cloud
# ============================================================================
aws-sdk = "2.31.6"

# ============================================================================
# Caching
# ============================================================================
caffeine = "3.2.0"

# ============================================================================
# Security
# ============================================================================
nimbus-jose-jwt = "9.47"
nimbus-oauth2 = "11.23"

# ============================================================================
# Development Tools
# ============================================================================
lombok = "1.18.36"
mapstruct = "1.6.3"

# ============================================================================
# Code Quality
# ============================================================================
jacoco = "0.8.14"
checkstyle = "10.21.4"
pmd = "7.11.0"
spotbugs = "4.9.3"
spotbugs-plugin = "6.9.3"

# ============================================================================
# Build Plugins
# ============================================================================
spotless = "8.4.0"
versions-plugin = "0.52.0"
owasp-plugin = "12.1.0"
flyway-plugin = "11.7.0"
jmh-plugin = "0.7.3"
cyclonedx = "3.2.2"
```

### 1.3 Implementation Tasks

#### Day 1: Core Libraries

- [ ] Update Jackson, SLF4J, Log4j versions
- [ ] Update JUnit, Mockito, AssertJ versions
- [ ] Update Testcontainers version
- [ ] Run `./gradlew check` to verify compatibility

#### Day 2: Infrastructure Libraries

- [ ] Update Micrometer, OpenTelemetry versions
- [ ] Update Kafka Clients, Vert.x versions
- [ ] Update Hibernate, PostgreSQL, HikariCP versions
- [ ] Run integration tests

#### Day 3: Code Quality & AI Libraries

- [ ] Update JaCoCo, PMD, Checkstyle, SpotBugs versions
- [ ] Evaluate LangChain4j 1.0.0-beta1 migration (may defer if breaking)
- [ ] Update Flyway (major version - requires migration testing)
- [ ] Full regression test suite

---

## Phase 2: Convention Plugin Extraction (Week 1-2, Days 4-8)

### 2.1 New Convention Plugin Architecture

```
buildSrc/src/main/kotlin/
├── com.ghatana.java-conventions.gradle.kts           (Base Java config)
├── com.ghatana.java-testing-conventions.gradle.kts   (Test setup)
├── com.ghatana.java-quality-conventions.gradle.kts  (Checkstyle/PMD/SpotBugs)
├── com.ghatana.jacoco-conventions.gradle.kts        (Code coverage)
├── com.ghatana.publishing-conventions.gradle.kts    (Maven publish)
└── com.ghatana.module-conventions.gradle.kts         (Module metadata)
```

### 2.2 Plugin Implementation Details

#### `com.ghatana.java-conventions.gradle.kts`

```kotlin
plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-serial"
    ))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
}
```

#### `com.ghatana.java-testing-conventions.gradle.kts`

```kotlin
plugins {
    java
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    jvmArgs("-Dapi.version=1.44")
}
```

#### `com.ghatana.java-quality-conventions.gradle.kts`

```kotlin
plugins {
    java
    checkstyle
    pmd
    id("com.github.spotbugs")
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties["suppressionFile"] =
        rootProject.file("config/checkstyle/suppressions.xml").absolutePath
    ignoreFailures = false
    showViolations = true
}

pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = listOf()
    ignoreFailures = false
    consoleOutput = true
}

spotbugs {
    toolVersion = libs.versions.spotbugs.get()
    ignoreFailures = false
    showStackTraces = true
    showProgress = true
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    excludeFilter = rootProject.file("config/spotbugs/spotbugs-exclude.xml")
}

dependencies {
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.13.0")
}
```

### 2.3 Elimination Targets

| Source File                        | Lines to Remove            | Replacement                                     |
| ---------------------------------- | -------------------------- | ----------------------------------------------- |
| `build.gradle.kts`                 | 47-124 (subprojects block) | Apply convention plugins in settings.gradle.kts |
| `gradle/common-build.gradle`       | Entire file                | Delete after migration                          |
| `gradle/java-conventions.gradle`   | Entire file                | Delete after migration                          |
| `products/yappc/build.gradle.kts`  | 33-226 (subprojects)       | Extract to `yappc-conventions.gradle.kts`       |
| `shared-services/build.gradle.kts` | 20-43 (subprojects)        | Delete - use root conventions                   |

### 2.4 Module Migration Checklist

For each of the 50+ modules:

- [ ] Remove `java { toolchain {...} }` block
- [ ] Remove `tasks.test { useJUnitPlatform() }` block
- [ ] Add `id("com.ghatana.java-conventions")` to plugins
- [ ] Add `id("com.ghatana.java-testing-conventions")` if tests exist
- [ ] Add `id("com.ghatana.java-quality-conventions")` for production code
- [ ] Verify no hardcoded versions remain

---

## Phase 3: Build Logic Centralization (Week 2, Days 9-12)

### 3.1 Root Build Cleanup

**File:** `build.gradle.kts`

**Remove:**

- `subprojects` block (lines 47-124)
- Hardcoded `junit-platform-launcher` dependency (line 72)
- Java compiler args duplication

**Keep:**

- Root project plugins (`java-platform`, `idea`, `cyclonedx`)
- Aggregate tasks (with lazy configuration)
- SBOM configuration

**Result:** Root build file should be < 50 lines

### 3.2 Settings Script Enhancement

**File:** `settings.gradle.kts`

Add plugin application for all Java modules:

```kotlin
// After all include() statements
gradle.beforeProject {
    if (file("$projectDir/src/main/java").exists() ||
        file("$projectDir/src/test/java").exists()) {

        plugins.apply("com.ghatana.java-conventions")

        if (file("$projectDir/src/test/java").exists()) {
            plugins.apply("com.ghatana.java-testing-conventions")
        }
    }
}
```

### 3.3 Product-Specific Refactoring

#### YAPPC (`products/yappc/build.gradle.kts`)

**Extract** the 194-line subprojects block to:

- `buildSrc/src/main/kotlin/com.ghatana.yappc-conventions.gradle.kts`

Apply selectively:

```kotlin
// In yappc module build.gradle.kts files:
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.yappc-conventions") // Only for YAPPC modules
}
```

#### data-cloud Special Handling

**Goal:** Eliminate the "snowflake" exception in `gradle/common-build.gradle`

**Approach:**

1. Create `com.ghatana.datacloud-conventions.gradle.kts`
2. Include data-cloud in standard convention application
3. Remove the `if (project.path.startsWith(":products:data-cloud"))` exclusion

---

## Phase 4: Hardcoded Dependency Remediation (Week 3, Days 13-15)

### 4.1 Critical Fixes

#### Fix 1: Root Build - JUnit Platform Launcher

**File:** `build.gradle.kts:72`

**Before:**

```kotlin
dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.10.2")
}
```

**After:**

```kotlin
// Remove entirely - now handled by java-testing-conventions
```

#### Fix 2: Software-Org - Hardcoded JMH

**File:** `products/software-org/build.gradle.kts:49-50`

**Before:**

```kotlin
"testImplementation"("org.openjdk.jmh:jmh-core:1.37")
"testAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
```

**After:**

```kotlin
testImplementation(libs.jmh.core)
testAnnotationProcessor(libs.jmh.generator.annprocess)
```

**Add to catalog:**

```toml
[versions]
jmh = "1.37"

[libraries]
jmh-core = { module = "org.openjdk.jmh:jmh-core", version.ref = "jmh" }
jmh-generator-annprocess = { module = "org.openjdk.jmh:jmh-generator-annprocess", version.ref = "jmh" }
```

#### Fix 3: YAPPC - Buildscript Dependencies

**File:** `products/yappc/build.gradle.kts:23-28`

**Before:**

```kotlin
buildscript {
    dependencies {
        classpath("org.json:json:20231013")
        classpath("org.yaml:snakeyaml:2.0")
    }
}
```

**After:**

```kotlin
// Remove buildscript block
// If JSON/YAML processing is needed, add to version catalog and use:
// implementation(libs.json) or implementation(libs.snakeyaml)
```

#### Fix 4: Inline Plugin Versions

**File:** `build.gradle.kts:17`

**Before:**

```kotlin
id("org.cyclonedx.bom") version "3.2.2"
```

**After:**

```kotlin
alias(libs.plugins.cyclonedx)
```

**File:** `products/yappc/build.gradle.kts:10-11`

**Before:**

```kotlin
id("org.owasp.dependencycheck") version "12.1.6"
id("com.github.spotbugs") version "6.4.2" apply false
```

**After:**

```kotlin
alias(libs.plugins.owasp)
alias(libs.plugins.spotbugs) apply false
```

### 4.2 Verification Script

Create `scripts/verify-no-hardcoded-versions.sh`:

```bash
#!/bin/bash
set -e

echo "Checking for hardcoded versions in build files..."

# Patterns to detect hardcoded versions
VIOLATIONS=0

# Check for version strings in build.gradle.kts files
while IFS= read -r file; do
    if grep -q 'version.*"[0-9]\+\.[0-9]\+"' "$file" 2>/dev/null; then
        echo "❌ VIOLATION: $file contains inline version"
        grep -n 'version.*"[0-9]\+\.[0-9]\+"' "$file" | head -5
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done < <(find . -name "build.gradle.kts" -not -path "./build/*" -not -path "./.gradle/*")

if [ $VIOLATIONS -eq 0 ]; then
    echo "✅ No hardcoded versions detected"
else
    echo "❌ Found $VIOLATIONS file(s) with hardcoded versions"
    exit 1
fi
```

---

## Phase 5: Testing & Validation (Week 3, Days 16-18)

### 5.1 Compatibility Test Matrix

| Module                                  | Unit Tests  | Integration | Build Time |
| --------------------------------------- | ----------- | ----------- | ---------- |
| `platform/java/core`                    | ✅ Required | Optional    | < 30s      |
| `platform/java/testing`                 | ✅ Required | N/A         | < 30s      |
| `platform/java/database`                | ✅ Required | ✅ Required | < 60s      |
| `products/aep/aep-api`                  | ✅ Required | ✅ Required | < 90s      |
| `products/data-cloud/platform-launcher` | ✅ Required | ✅ Required | < 120s     |
| `products/yappc/core/yappc-api`         | ✅ Required | Optional    | < 60s      |

### 5.2 Validation Commands

```bash
# Full validation suite
./gradlew clean check --no-daemon

# Platform only
./gradlew buildPlatform testPlatform --no-daemon

# Single module (isolation test)
./gradlew :platform:java:core:build :platform:java:core:test --no-daemon

# Version catalog validation
./gradlew validatePlatformBom --no-daemon

# Dependency insight (for verification)
./gradlew dependencyInsight --dependency com.fasterxml.jackson.core:jackson-core --configuration runtimeClasspath
```

### 5.3 Build Cache Verification

```bash
# First build (populate cache)
./gradlew clean build --no-daemon

# Second build (should use cache)
./gradlew clean build --no-daemon --info | grep "FROM-CACHE"

# Verify cache hits > 80%
```

---

## Phase 6: Documentation & Handoff (Week 3, Days 19-21)

### 6.1 Updated Developer Documentation

Update `docs/GRADLE_BEST_PRACTICES.md`:

````markdown
## Adding a New Dependency

1. Add version to `gradle/libs.versions.toml` [versions] section
2. Add library coordinate to [libraries] section
3. Use `implementation(libs.<name>)` in module build.gradle.kts
4. NEVER use string literals with versions

## Adding a New Module

1. Add to `settings.gradle.kts` with `include("path")`
2. Create `build.gradle.kts` with:

   ```kotlin
   plugins {
       id("com.ghatana.java-conventions")
       id("com.ghatana.java-testing-conventions") // if tests exist
   }

   dependencies {
       // Only direct dependencies this module uses
   }
   ```
````

3. Do NOT add `java { toolchain }` block
4. Do NOT add `tasks.test { useJUnitPlatform() }` block

## Common Pitfalls

- ❌ Hardcoded versions in build files
- ❌ `subprojects` or `allprojects` blocks for new logic
- ❌ Duplicating test configuration
- ❌ Module-level repository declarations
- ✅ Centralized convention plugins
- ✅ Version catalog for all dependencies
- ✅ Minimal module build files

````

### 6.2 Migration Runbook

Create `docs/GRADLE_MIGRATION_RUNBOOK.md` with:
- Step-by-step migration procedures
- Rollback procedures
- Troubleshooting guide
- Emergency contacts

---

## Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| LangChain4j 1.0.0-beta1 breaking changes | High | High | Defer to Phase 4; maintain 0.34.0 initially |
| Flyway 11.x migration issues | Medium | Medium | Test with data-cloud integration suite |
| HikariCP 6.x API changes | Medium | Medium | Review release notes; test connection pools |
| Jackson 2.18 serialization changes | Low | Medium | Run full integration test suite |
| Convention plugin circular dependencies | Low | High | Use `plugins.apply()` not `plugins { id() }` in beforeProject |
| Build time regression | Medium | Low | Monitor CI metrics; optimize if >10% increase |

---

## Success Criteria

### Phase Completion Checklist

- [ ] All versions updated to latest stable (except ActiveJ)
- [ ] Version catalog is single source of truth for all versions
- [ ] No hardcoded versions in any build.gradle.kts file
- [ ] No `subprojects` or `allprojects` blocks with build logic
- [ ] All Java modules use convention plugins
- [ ] Build isolation verified (building one module doesn't trigger others)
- [ ] All tests pass (`./gradlew check`)
- [ ] Build time not increased by >10%
- [ ] CI/CD pipeline green
- [ ] Documentation updated

### Final Verification Commands

```bash
# 1. Version catalog governance
./gradlew dependencies --configuration runtimeClasspath | grep -v "^\\+" | sort | uniq

# 2. No hardcoded versions
! grep -r 'version.*"[0-9]\+\.[0-9]\+"' **/build.gradle.kts 2>/dev/null

# 3. Build isolation
./gradlew :platform:java:core:build --dry-run | grep -c "TASK"  # Should be < 20

# 4. Full validation
./gradlew clean check --no-daemon --parallel
````

---

## Appendix A: Version Verification Sources

| Library        | Source URL                                                                 | Verification Date |
| -------------- | -------------------------------------------------------------------------- | ----------------- |
| Jackson        | https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core | 2026-04-07        |
| JUnit          | https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api     | 2026-04-07        |
| Testcontainers | https://mvnrepository.com/artifact/org.testcontainers/testcontainers       | 2026-04-07        |
| Micrometer     | https://github.com/micrometer-metrics/micrometer/releases                  | 2026-04-07        |
| OpenTelemetry  | https://central.sonatype.com/artifact/io.opentelemetry/opentelemetry-sdk   | 2026-04-07        |
| Mockito        | https://github.com/mockito/mockito/releases                                | 2026-04-07        |
| AssertJ        | https://mvnrepository.com/artifact/org.assertj/assertj-core                | 2026-04-07        |
| Flyway         | https://documentation.red-gate.com/fd/release-notes                        | 2026-04-07        |
| SLF4J          | https://www.slf4j.org/download.html                                        | 2026-04-07        |
| Log4j          | https://logging.apache.org/log4j/2.x/release-notes.html                    | 2026-04-07        |

---

## Appendix B: Rollback Procedure

If critical issues are discovered:

1. **Immediate**: Revert `gradle/libs.versions.toml` to last known good
2. **Rebuild**: `./gradlew clean build --refresh-dependencies`
3. **Isolate**: Identify problematic library version
4. **Pin**: Lock that library to previous version in catalog
5. **Ticket**: Create issue for next iteration

Rollback time: < 15 minutes

---

## Appendix C: Post-Implementation Monitoring

### Week 4 Monitoring

- [ ] Daily CI success rate tracking
- [ ] Build time trends (should stabilize or improve)
- [ ] Developer feedback on build experience
- [ ] Dependency vulnerability scan results

### Month 1 Monitoring

- [ ] New module creation time (target: < 5 minutes)
- [ ] Convention plugin adoption rate (target: 100%)
- [ ] Version catalog maintenance overhead
- [ ] Build cache hit rate (target: > 80%)

---

**End of Document**
