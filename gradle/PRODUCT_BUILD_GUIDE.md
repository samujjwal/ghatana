# Gradle Product Build Configuration Guide

## Overview

This guide documents the robust build configuration pattern for products in the Ghatana monorepo. The pattern ensures:
- ✅ **Standalone builds**: `cd products/<product> && ../../gradlew build`
- ✅ **Monorepo builds**: `./gradlew :products:<product>:build`
- ✅ **Shared libs**: Properly included in both contexts
- ✅ **Consistent pattern**: Reusable across all products

## Architecture

```
ghatana/
├── settings.gradle.kts          # Root monorepo settings
├── gradle/
│   └── libs.versions.toml       # Shared version catalog
├── libs/java/                   # Shared libraries
├── contracts/                   # Shared API contracts
└── products/
    ├── data-cloud/
    │   └── settings.gradle.kts  # Product-specific settings
    ├── aep/
    │   └── settings.gradle.kts  # Product-specific settings
    └── <other-products>/
```

## Settings Template

Use this template for any new product's `settings.gradle.kts`:

```kotlin
/**
 * <Product Name> Settings
 * 
 * This enables building <product>:
 * - Standalone: cd products/<product> && ../../gradlew build
 * - From root:  ./gradlew :products:<product>:build
 */
rootProject.name = "<product-name>"

// ============================================================================
// Build Context Detection
// ============================================================================
val isStandaloneBuild = gradle.parent == null
val monorepoRoot = if (isStandaloneBuild) rootDir.parentFile.parentFile else rootDir.parentFile.parentFile
val productDir = rootDir

extra["isStandaloneBuild"] = isStandaloneBuild
extra["monorepoRoot"] = monorepoRoot
extra["productName"] = "<product-name>"

logger.lifecycle("┌─────────────────────────────────────────────────────────────")
logger.lifecycle("│ Product: <Product Name>")
logger.lifecycle("│ Mode: \${if (isStandaloneBuild) \"STANDALONE\" else \"MONOREPO\"}")
logger.lifecycle("│ Product Dir: $productDir")
logger.lifecycle("│ Monorepo Root: $monorepoRoot")
logger.lifecycle("└─────────────────────────────────────────────────────────────")

// ============================================================================
// Plugin Management & Dependency Resolution
// ============================================================================
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    // Only configure version catalog if not auto-discovered locally
    val localCatalog = File(rootDir, "gradle/libs.versions.toml")
    if (!localCatalog.exists()) {
        versionCatalogs {
            create("libs") {
                from(files("$monorepoRoot/gradle/libs.versions.toml"))
            }
        }
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

fun discoverProductModules(
    baseDir: File,
    excludePatterns: List<String> = emptyList()
): List<String> {
    val defaultExcludes = listOf("/build/", "/.", "/_", "/node_modules/", "/target/")
    val allExcludes = defaultExcludes + excludePatterns
    val included = mutableListOf<String>()
    
    baseDir.walkTopDown()
        .filter { dir ->
            dir.isDirectory &&
            dir != baseDir &&
            (dir.resolve("build.gradle.kts").exists() || dir.resolve("build.gradle").exists()) &&
            allExcludes.none { pattern -> dir.path.contains(pattern) } &&
            dir.name != "buildSrc"
        }
        .forEach { dir ->
            val relativePath = baseDir.toPath().relativize(dir.toPath()).toString()
            val projectPath = relativePath.replace(File.separatorChar, ':')
            
            include(projectPath)
            project(":$projectPath").projectDir = dir
            included.add(projectPath)
        }
    
    return included
}

fun includeLib(name: String, subPath: String = "java") {
    val libDir = File(monorepoRoot, "libs/$subPath/$name")
    if (libDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = libDir
    } else {
        logger.warn("Library not found: libs:$name at $libDir")
    }
}

fun includeNestedLib(parent: String, children: List<String>, subPath: String = "java") {
    val parentDir = File(monorepoRoot, "libs/$subPath/$parent")
    if (parentDir.exists()) {
        include("libs:$parent")
        project(":libs:$parent").projectDir = parentDir
        
        children.forEach { child ->
            val childDir = File(parentDir, child)
            if (childDir.exists() && (childDir.resolve("build.gradle.kts").exists() || childDir.resolve("build.gradle").exists())) {
                include("libs:$parent:$child")
                project(":libs:$parent:$child").projectDir = childDir
            }
        }
    }
}

fun includeContract(name: String) {
    val contractDir = File(monorepoRoot, "contracts/$name")
    if (contractDir.exists()) {
        include("contracts:$name")
        project(":contracts:$name").projectDir = contractDir
    }
}

// ============================================================================
// 1. Discover Product Internal Modules
// ============================================================================
logger.lifecycle("Discovering <product-name> modules...")
val productModules = discoverProductModules(productDir)
logger.lifecycle("  Found \${productModules.size} modules")

// ============================================================================
// 2. Include Shared Libraries
// ============================================================================
logger.lifecycle("Including shared libraries...")

// Set up the libs parent project
val libsDir = File(monorepoRoot, "libs/java")
if (libsDir.exists()) {
    include("libs")
    project(":libs").projectDir = File(monorepoRoot, "libs")
}

// Core infrastructure (customize as needed)
listOf(
    "activej-runtime",
    "http-server", 
    "observability",
    "database",
    "common-utils",
    "types",
    // Add more libs as needed for your product
).forEach { includeLib(it) }

// Nested libraries (if needed)
includeNestedLib("auth-platform", listOf("core", "jwt", "oauth"))

// Testing libraries
listOf(
    "test-utils",
    "activej-test-utils", 
    "test-containers"
).forEach { name ->
    val testLibDir = File(monorepoRoot, "libs/java/testing/$name")
    if (testLibDir.exists()) {
        include("libs:$name")
        project(":libs:$name").projectDir = testLibDir
    }
}

// ============================================================================
// 3. Include Contracts
// ============================================================================
logger.lifecycle("Including contracts...")

val contractsDir = File(monorepoRoot, "contracts")
if (contractsDir.exists()) {
    include("contracts")
    project(":contracts").projectDir = contractsDir
    
    listOf("proto", "pojos", "mappers").forEach { name ->
        includeContract(name)
    }
}

logger.lifecycle("<Product Name> build configuration complete!")
```

## Key Concepts

### 1. Build Context Detection

```kotlin
val isStandaloneBuild = gradle.parent == null
```

- `gradle.parent == null` → Running from product directory (standalone)
- `gradle.parent != null` → Running as composite build from root (monorepo)

### 2. Path Resolution

```kotlin
val monorepoRoot = if (isStandaloneBuild) 
    rootDir.parentFile.parentFile  // products/<product> → ghatana
else 
    rootDir.parentFile.parentFile  // Same in both contexts for our structure
```

### 3. Version Catalog Handling

```kotlin
val localCatalog = File(rootDir, "gradle/libs.versions.toml")
if (!localCatalog.exists()) {
    versionCatalogs {
        create("libs") {
            from(files("$monorepoRoot/gradle/libs.versions.toml"))
        }
    }
}
```

Products can have their own `gradle/libs.versions.toml` for customization. If not present, they inherit from the monorepo root.

### 4. Parent Project Setup

**CRITICAL**: Always set up parent projects before including children:

```kotlin
// ✅ Correct - set parent project directory first
include("libs")
project(":libs").projectDir = File(monorepoRoot, "libs")

// Now include children
include("libs:common-utils")
project(":libs:common-utils").projectDir = File(monorepoRoot, "libs/java/common-utils")
```

```kotlin
// ❌ Wrong - Gradle creates parent with default (wrong) directory
include("libs:common-utils")  // Error: libs project at productDir/libs doesn't exist!
```

## Testing Your Configuration

### Standalone Mode
```bash
cd products/<product>
../../gradlew projects
../../gradlew clean build
```

### Monorepo Mode
```bash
# From root
./gradlew :products:<product>:projects
./gradlew :products:<product>:build
```

### Dry Run
```bash
# Verify dependency resolution without compilation
../../gradlew :core:compileJava --dry-run
```

## Troubleshooting

### Error: "Configuring project without existing directory"
**Cause**: Parent project (like `:libs`) not configured before children.  
**Fix**: Add parent project include with projectDir before including children.

### Error: "Version catalog - from method called multiple times"
**Cause**: Product has local `gradle/libs.versions.toml` AND explicitly defines catalog.  
**Fix**: Use the conditional catalog loading pattern (check `localCatalog.exists()`).

### Error: "Project with path ':...' could not be found"
**Cause**: Build script references a project not included in settings.  
**Fix**: Add the missing library/contract to the includes list.

## Files Modified

| Product | File | Purpose |
|---------|------|---------|
| data-cloud | `products/data-cloud/settings.gradle.kts` | Robust standalone/monorepo settings |
| aep | `products/aep/settings.gradle.kts` | Robust standalone/monorepo settings |

Backups preserved as `settings.gradle.kts.backup`.
