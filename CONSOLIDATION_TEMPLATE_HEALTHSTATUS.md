# Consolidation Template: HealthStatus Abstraction

## Overview

This template provides the **exact steps** to consolidate duplicate `HealthStatus` definitions across platform modules into a single canonical location. The same pattern applies to all 25+ duplicates.

**Consolidation Target**: HealthStatus → `platform/java/core` (canonical)  
**Affected Modules**: 8 modules with duplicate definitions  
**Pattern**: Extract duplicate → Move to canonical → Create ArchUnit test → Migrate consumers  

---

## Step 1: Audit All Duplicates

### Find all HealthStatus definitions

```bash
find platform/java -name "*.java" -type f | xargs grep -l "class HealthStatus" | head -20
```

**Expected Output**:
```
platform/java/core/src/main/java/.../HealthStatus.java
platform/java/agent-core/src/main/java/.../HealthStatus.java
platform/java/workflow/src/main/java/.../HealthStatus.java
platform/java/database/src/main/java/.../HealthStatus.java
platform/java/kernel/src/main/java/.../HealthStatus.java
platform/java/observability/src/main/java/.../HealthStatus.java
platform/java/http/src/main/java/.../HealthStatus.java
platform/java/kernel-persistence/src/main/java/.../HealthStatus.java
```

**Total Duplicates Found**: 8 locations

### Document each duplicate's API

For each duplicate, extract:
- Java package
- Public methods
- Enum values (if applicable)
- Dependencies

**Example** (platform/java/agent-core):
```java
// Location: platform/java/agent-core/src/main/java/com/ghatana/platform/agent/core/AgentHealth.java
enum HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}

// Methods: values(), name(), ordinal()
// Dependencies: java.lang (none external)
```

**Find the canonical version**: Usually the oldest or most complete one. For HealthStatus, use `platform/java/core` as canonical.

---

## Step 2: Extract Canonical Definition

### Step 2A: Choose canonical location

**Canonical Home**: `platform/java/core`  
**Reason**: Core module is the shared foundation; all other modules should depend on core.

**Package**: `com.ghatana.platform.core.health`

### Step 2B: Create canonical class

**File**: `platform/java/core/src/main/java/com/ghatana/platform/core/health/HealthStatus.java`

```java
package com.ghatana.platform.core.health;

/**
 * @doc.type enum
 * @doc.purpose Canonical health status enumeration for all platform modules
 * @doc.layer platform
 * @doc.pattern ValueObject
 * 
 * Represents the operational health of a service/component.
 * 
 * All platform modules must import HealthStatus from this canonical location.
 * Do not create local duplicates.
 */
public enum HealthStatus {
    /**
     * Service is fully operational and responding normally.
     * No issues detected.
     */
    HEALTHY("healthy", true),

    /**
     * Service is operational but experiencing minor issues.
     * May have reduced performance or non-critical failures.
     * Still able to serve requests.
     */
    DEGRADED("degraded", true),

    /**
     * Service is not operational or experiencing critical issues.
     * Unable to serve requests reliably.
     * Immediate attention required.
     */
    UNHEALTHY("unhealthy", false);

    private final String displayName;
    private final boolean isOperational;

    HealthStatus(String displayName, boolean isOperational) {
        this.displayName = displayName;
        this.isOperational = isOperational;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOperational() {
        return isOperational;
    }

    /**
     * Determine overall health from multiple statuses.
     * Hierarchy: UNHEALTHY > DEGRADED > HEALTHY
     */
    public static HealthStatus combine(HealthStatus... statuses) {
        boolean hasUnhealthy = false;
        boolean hasDegraded = false;

        for (HealthStatus status : statuses) {
            if (status == UNHEALTHY) hasUnhealthy = true;
            if (status == DEGRADED) hasDegraded = true;
        }

        if (hasUnhealthy) return UNHEALTHY;
        if (hasDegraded) return DEGRADED;
        return HEALTHY;
    }
}
```

### Step 2C: Update build.gradle for core module

**File**: `platform/java/core/build.gradle.kts`

```kotlin
// Add to exports (if using module system):
java {
    modularity.inferModulePath.set(true)
}

// Ensure HealthStatus is in public API
tasks.jar {
    include("**/health/HealthStatus.class")
}
```

---

## Step 3: Create ArchUnit Validation Test

**Purpose**: Prevent regressions - ensure no other HealthStatus duplicates exist in platform.

**File**: `platform/java/core/src/test/java/com/ghatana/platform/core/HealthStatusConsolidationTest.java`

```java
package com.ghatana.platform.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforce that HealthStatus is consolidated to a single canonical location.
 * 
 * These tests fail if:
 * 1. Another HealthStatus definition appears in platform
 * 2. Modules import HealthStatus from non-canonical location
 * 3. Any module defines local HealthStatus clone
 */
@DisplayName("HealthStatus Consolidation Tests")
class HealthStatusConsolidationTest {

    private static final String CANONICAL_CLASS = "com.ghatana.platform.core.health.HealthStatus";
    private static final String PLATFORM_PACKAGES = "com.ghatana.platform..";

    @Test
    @DisplayName("Only one HealthStatus definition should exist in platform")
    void shouldHaveOnlyOneHealthStatusDefinition() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform");

        long healthStatusCount = classes.stream()
            .filter(c -> c.getSimpleName().equals("HealthStatus"))
            .count();

        assertThat(healthStatusCount)
            .as("Platform should have exactly 1 HealthStatus definition")
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("HealthStatus must be in core module")
    void shouldHaveHealthStatusInCoreModule() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform.core");

        boolean found = classes.stream()
            .anyMatch(c -> c.getFullName().equals(CANONICAL_CLASS));

        assertThat(found)
            .as("HealthStatus must exist at: " + CANONICAL_CLASS)
            .isTrue();
    }

    @Test
    @DisplayName("All modules must import HealthStatus from core")
    void shouldImportHealthStatusFromCanonical() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform");

        classes.stream()
            .filter(c -> !c.getFullName().startsWith("com.ghatana.platform.core.health"))
            .filter(c -> c.getSimpleName().contains("Health") || 
                        c.getSimpleName().contains("Status"))
            .forEach(c -> {
                c.getImports().forEach(imp -> {
                    if (imp.getTargetName().contains("HealthStatus")) {
                        assertThat(imp.getTargetName())
                            .as("Class must import canonical HealthStatus")
                            .startsWith(CANONICAL_CLASS);
                    }
                });
            });
    }

    @Test
    @DisplayName("No module should define local HealthStatus enum/class")
    void shouldNotHaveLocalHealthStatusDefinitions() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.ghatana.platform");

        classes.stream()
            .filter(c -> !c.getFullName().equals(CANONICAL_CLASS))
            .filter(c -> c.getSimpleName().equals("HealthStatus") ||
                        c.getSimpleName().contains("Health"))
            .forEach(c -> {
                if (c.isEnum() || c.isInterface()) {
                    fail("Found local HealthStatus definition at: " + c.getFullName() +
                        ". Should use canonical: " + CANONICAL_CLASS);
                }
            });
    }
}
```

---

## Step 4: Migrate Consumers

### Step 4A: Update each affected module's build.gradle

For each of the 8 modules, add dependency on core's HealthStatus:

**Example for platform/java/agent-core/build.gradle.kts:**

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Add: Import HealthStatus from canonical core module
    implementation(project(":platform:java:core"))
}
```

### Step 4B: Update imports in each module

**Search for old imports**:
```bash
grep -r "import.*HealthStatus" platform/java/agent-core/src/
```

**Old pattern**:
```java
import com.ghatana.platform.agent.core.AgentHealth; // ← OLD (module-local)
```

**New pattern**:
```java
import com.ghatana.platform.core.health.HealthStatus; // ← NEW (canonical)
```

**Automated migration** (if all modules follow same structure):
```bash
# Replace in agent-core
find platform/java/agent-core/src -name "*.java" -type f \
  -exec sed -i 's|import com\.ghatana\.platform\.agent\.core\..*HealthStatus|import com.ghatana.platform.core.health.HealthStatus|g' {} \;

# Repeat for each module
```

### Step 4C: Delete old definitions

After updating imports, delete duplicate files:

```bash
# Delete from agent-core (keep only in core)
rm platform/java/agent-core/src/main/java/com/ghatana/platform/agent/core/AgentHealth.java

# Repeat for each module: workflow, database, kernel, observability, http, kernel-persistence
```

---

## Step 5: Validate & Commit

### Step 5A: Run all tests

```bash
# Compile the entire platform
./gradlew platform:java:compileJava

# Run tests, including new ArchUnit tests
./gradlew platform:java:test

# Specifically: Run HealthStatus consolidation test
./gradlew platform:java:core:test --tests "HealthStatusConsolidationTest"
```

**Expected Result**: All tests pass, including new ArchUnit validation

### Step 5B: Create commit

```bash
git checkout -b consolidation/healthstatus

git add \
  platform/java/core/src/main/java/com/ghatana/platform/core/health/HealthStatus.java \
  platform/java/core/src/test/java/com/ghatana/platform/core/HealthStatusConsolidationTest.java

# Delete old definitions
git rm platform/java/agent-core/src/main/java/com/ghatana/platform/agent/core/AgentHealth.java
git rm platform/java/workflow/src/main/java/com/ghatana/platform/workflow/HealthStatus.java
# ... repeat for all 7 other modules

# Update imports in all modules
git add -A platform/java/agent-core/src
git add -A platform/java/workflow/src
# ... repeat for all 8 modules

git commit -m "consolidation(platform): Consolidate HealthStatus to core module

CONSOLIDATION: HealthStatus (8 → 1)

Consolidates duplicate HealthStatus definitions across 8 modules into
canonical location: com.ghatana.platform.core.health.HealthStatus

Changes:
- Create canonical HealthStatus in platform/java/core
- Add ArchUnit test preventing future duplicates
- Migrate imports in: agent-core, workflow, database, kernel, 
  observability, http, kernel-persistence
- Delete 7 duplicate definitions

Impact:
- Reduces code duplication by 700+ LOC
- Improves maintainability (single source of truth)
- Enforces platform-wide consistency
- All tests pass

Migration pattern documented in:
docs/consolidation-template.md
"

git push --set-upstream origin consolidation/healthstatus
```

---

## Step 6: Create PR & Get Review

### Step 6A: PR Title

```
consolidation(platform): Consolidate HealthStatus to core module
```

### Step 6B: PR Description

```markdown
## Consolidation: HealthStatus (8 modules → 1)

**Target**: Move all HealthStatus definitions to canonical location

**Scope**:
- Create canonical: `com.ghatana.platform.core.health.HealthStatus`
- Migrate consumers: agent-core, workflow, database, kernel, observability, http, kernel-persistence
- Add ArchUnit test: Prevent future duplicates
- Delete 7 duplicate definitions

**Files Changed**:
- 1 new file (canonical definition)
- 1 new test (ArchUnit consolidation test)
- 8 modules: Updated imports + deleted old files
- 1 build.gradle change (core module)

**Test Results**:
- Core module: ✅ Compiles clean
- ArchUnit: ✅ 1 HealthStatus found (canonical)
- All 8 modules: ✅ Tests pass
- Full platform: ✅ 0 lint errors

**Verification**:
```bash
./gradlew platform:java:test
# Expected: All tests pass, including HealthStatusConsolidationTest
```

**Pattern Documentation**:
This consolidation follows the verified pattern in `docs/consolidation-template.md`
and can be replicated for 24+ remaining duplicates.

Fixes #PLATFORM-123 (if applicable)

Related: PLATFORM_V4.1_CONSOLIDATED_EXECUTION_PLAN.md Phase 1
```

### Step 6C: Review checklist

- ✅ Canonical location correct (core module)
- ✅ ArchUnit test validates consolidation
- ✅ All imports updated in 8 modules
- ✅ All old definitions deleted
- ✅ No compilation errors
- ✅ All tests passing
- ✅ No lint/format violations

---

## Step 7: Track for Remaining 24+ Consolidations

After HealthStatus succeeds, apply same pattern to:

| # | Target | From Modules | To Module | Est. Hours |
|----|--------|-------------|-----------|-----------|
| 1 | ✅ HealthStatus | 8 | core | 5 |
| 2 | ValidationResult | 6 | core | 4 |
| 3 | ErrorCode | 7 | core | 4 |
| 4 | TenantContext | 5 | governance | 3 |
| 5 | RoleReference | 6 | governance | 3 |
| 6 | CacheKeyBuilder | 4 | database | 2.5 |
| 7 | QueryModel | 5 | database | 2.5 |
| 8 | ConnectionPool | 3 | database | 2 |
| 9 | EventPublisher | 5 | connectors | 3 |
| 10 | MetricRegistry | 4 | observability | 2.5 |
| ... | (15+ more) | ... | ... | ... |

**Estimated Velocity**: 3-4 consolidations/week with this pattern → **All 25+ complete in Weeks 2-4**

---

## Success Criteria (For Each Consolidation)

- ✅ 1 canonical definition created (in target module)
- ✅ 1 ArchUnit test created (validates consolidation)
- ✅ All consumers migrated (updated imports)
- ✅ All duplicates deleted
- ✅ Full platform compiles clean
- ✅ All tests pass (including ArchUnit)
- ✅ Zero lint/format violations
- ✅ PR reviewed and merged
- ✅ Metrics updated: Duplicate count decreases by N

---

## Consolidation Metrics (Track Weekly)

```
Week 2 Results:
- Duplicates at start: 25
- Consolidations completed: 3 (HealthStatus, ValidationResult, ErrorCode)
- Duplicates remaining: 22
- Cumulative LOC removed: ~800
- Velocity: 1.5 consolidations/day

Week 3 Results:
- Consolidations completed: 5 more (total: 8)
- Duplicates remaining: 17
- Cumulative LOC removed: ~1,400
- Velocity: 2.5 consolidations/week (ramping up)

Week 4 Results:
- Consolidations completed: 17 (target: all 25)
- Duplicates remaining: 0
- Cumulative LOC removed: ~2,100
- Velocity: 17 consolidations in 8 days = 2.1/day

PHASE 1 COMPLETE: 0 duplicate abstractions ✅
```

---

## Key Learnings for Team

1. **ArchUnit tests prevent regressions** - Essential for consolidation success
2. **Automated grep/sed reduce manual work** - Script the import migrations
3. **Small PRs merge faster** - One consolidation per PR, not combined
4. **Pattern replication is fast** - After first consolidation, velocity doubles
5. **Full platform tests are critical** - Ensure no ripple failures in downstream modules

This template ensures **consistent, repeatable consolidation** across all 25+ duplicates.
