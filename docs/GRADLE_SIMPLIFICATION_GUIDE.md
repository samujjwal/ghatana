# Gradle Simplification Guide

## Overview

This guide documents the simplified Gradle structure implemented for the Ghatana monorepo. The new structure reduces complexity by 80%+ while improving maintainability, performance, and developer experience.

## Architecture

### Core Principles

1. **Auto-Discovery**: Modules are discovered by pattern, not manual inclusion
2. **Convention-First**: All configuration handled by convention plugins
3. **Isolation**: Proper build isolation prevents cross-product dependencies
4. **Performance**: Optimized for large monorepo builds
5. **Simplicity**: Minimal configuration, maximum automation

### Directory Structure

```
ghatana/
|-- settings.gradle.kts              # Simplified auto-discovery (50 lines)
|-- build.gradle.kts                 # Minimal root config (30 lines)
|-- gradle/
|   |-- libs.versions.toml          # Reorganized with bundles (300 lines)
|   |-- performance.gradle          # Build performance optimizations
|   |-- build-isolation.gradle      # Build isolation rules
|   |-- build-validation.gradle     # Comprehensive validation
|   |-- platform-boundary-check.gradle
|   |-- product-isolation.gradle
|   |-- doc-tag-check.gradle
|   |-- java-version-check.gradle
|-- buildSrc/
|   |-- src/main/kotlin/
|   |   |-- com.ghatana.java-conventions.gradle.kts
|   |   |-- com.ghatana.testing-conventions.gradle.kts
|   |   |-- com.ghatana.quality-conventions.gradle.kts
|   |   |-- com.ghatana.lombok-conventions.gradle.kts
|   |   |-- com.ghatana.protobuf-conventions.gradle.kts
|   |   |-- com.ghatana.product-conventions.gradle.kts
|   |   |-- com.ghatana.database-conventions.gradle.kts
|   |-- gradle.properties
|   |-- build.gradle.kts
|-- scripts/
|   |-- migrate-gradle-structure.sh
|   |-- validate-gradle-health.sh
```

## Convention Plugins

### Core Conventions

#### `com.ghatana.java-conventions`

- Java 21 toolchain configuration
- UTF-8 encoding and compiler flags
- Javadoc generation (disabled by default)
- JAR manifest metadata
- Dependency guardrails

#### `com.ghatana.testing-conventions`

- Unified JUnit Platform setup
- Configurable JaCoCo coverage
- Integration test profile
- Parallel execution
- Docker compatibility

#### `com.ghatana.quality-conventions`

- Checkstyle, PMD, SpotBugs
- Code formatting standards
- Quality gate enforcement

### Specialized Conventions

#### `com.ghatana.protobuf-conventions`

- Protocol buffer compilation
- gRPC code generation
- Generated source handling

#### `com.ghatana.product-conventions`

- Product-level governance
- Module size validation
- Architectural rule enforcement
- Dependency exclusions

#### `com.ghatana.database-conventions`

- Flyway migrations
- Testcontainers setup
- Connection pooling
- Database testing utilities

## Module Templates

### Standard Java Module

```kotlin
/**
 * [Module Name]
 *
 * @doc.type build-script
 * @doc.purpose [Brief description of module purpose]
 * @doc.layer [platform|product|shared-services]
 * @doc.pattern [Convention|Library|Service|Application]
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    // Add specialized conventions as needed:
    // id("com.ghatana.protobuf-conventions")
    // id("com.ghatana.database-conventions")
}

group = "com.ghatana.[group]"
version = rootProject.version
description = "[Module description]"

dependencies {
    // Use dependency bundles for cleaner declarations
    implementation(libs.bundles.activej.core)
    implementation(libs.bundles.jackson.json)

    // Testing
    testImplementation(libs.bundles.testing.core)
}
```

### Platform Module

```kotlin
/**
 * Platform [Component] Module
 *
 * @doc.type build-script
 * @doc.purpose [Platform component description]
 * @doc.layer platform
 * @doc.pattern [Library|Contract|Kernel]
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.platform"
version = rootProject.version

dependencies {
    // Platform modules typically use api() for public APIs
    api(libs.bundles.activej.core)

    // Common utilities
    implementation(libs.bundles.common.utils)

    // Testing
    testImplementation(libs.bundles.testing.core)
}
```

### Product Module

```kotlin
/**
 * [Product] [Component] Module
 *
 * @doc.type build-script
 * @doc.purpose [Product component description]
 * @doc.layer product
 * @doc.pattern [Service|Application|Library]
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.products.[product]"
version = rootProject.version

dependencies {
    // Product modules typically depend on platform modules
    implementation(project(":platform:java:core"))
    implementation(libs.bundles.activej.http)

    // Testing
    testImplementation(libs.bundles.testing.core)
}
```

## Dependency Management

### Version Catalog Organization

The version catalog is organized into logical sections:

```
[versions]        # Core versions only (50 entries)
[plugins]         # Build tool plugins (15 entries)
[libraries]       # Organized by domain (120 entries)
[bundles]         # Logical dependency groups (20 bundles)
```

### Bundles

Commonly used dependency bundles:

- `activej-core` - Async primitives and injection
- `activej-http` - HTTP server and launcher
- `jackson-json` - Complete JSON processing
- `logging-core` - Logging setup
- `testing-core` - Essential testing libraries
- `database-core` - Database access
- `grpc-core` - Protocol buffers and gRPC
- `security-core` - JWT and cryptography

### Usage Examples

```kotlin
// Before: Individual dependencies
dependencies {
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.common)
    implementation(libs.activej.inject)
}

// After: Dependency bundles
dependencies {
    implementation(libs.bundles.activej.core)
}
```

## Module Management

### Adding a New Module

1. **Create Module Directory**

   ```bash
   mkdir -p products/newproduct/core/newmodule
   cd products/newproduct/core/newmodule
   ```

2. **Create Standard Structure**

   ```bash
   mkdir -p src/main/java/com/ghatana/products/newproduct
   mkdir -p src/test/java/com/ghatana/products/newproduct
   mkdir -p src/main/resources
   ```

3. **Create build.gradle.kts**
   - Use the appropriate template from this guide
   - Apply necessary convention plugins
   - Declare dependencies using bundles

4. **Add Source Files**
   - Add Java source files to `src/main/java`
   - Add test files to `src/test/java`

5. **Auto-Discovery**
   - No need to modify `settings.gradle.kts`
   - Module will be automatically discovered

### Removing a Module

1. **Check Dependencies**

   ```bash
   ./gradlew analyzeBuildIsolation
   ```

2. **Remove Directory**

   ```bash
   rm -rf products/oldproduct/core/oldmodule
   ```

3. **Validate**
   ```bash
   ./gradlew validateAll
   ```

## Performance Optimization

### Build Configuration

The following optimizations are automatically applied:

- **Configuration Cache**: Enabled by default
- **Build Cache**: Local cache with 30-day retention
- **Parallel Execution**: Optimized worker count
- **Incremental Compilation**: Enabled for Java/Kotlin
- **Dependency Resolution**: Cached and optimized

### Recommended Gradle Properties

```properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true
org.gradle.workers.max=4
```

### Performance Commands

```bash
# Analyze build performance
./gradlew analyzeBuildPerformance

# Profile build execution
./gradlew profileBuild

# Validate build isolation
./gradlew validateBuildIsolation
```

## Validation and Governance

### Validation Tasks

- `validateDependencies` - Dependency consistency
- `validateConventionPlugins` - Convention plugin usage
- `validateModuleStructure` - Module organization
- `validateBuildPerformance` - Performance characteristics
- `validateSecurity` - Security configuration
- `validateAll` - Run all validations

### Governance Rules

1. **Convention Plugin Usage**: All Java modules must use standard conventions
2. **Dependency Bundles**: Prefer bundles over individual dependencies
3. **Version Catalog**: No hardcoded versions in build files
4. **Documentation**: All modules must have proper @doc.\* tags
5. **Module Size**: Maximum 150 Java files per module

### Quality Gates

- **Code Coverage**: Minimum 80% instruction coverage
- **Code Quality**: Checkstyle, PMD, SpotBugs compliance
- **Security**: OWASP dependency check
- **Architecture**: Structural rule validation

## Migration Guide

### From Old Structure

1. **Backup Current Build**

   ```bash
   ./scripts/migrate-gradle-structure.sh --backup
   ```

2. **Run Migration Script**

   ```bash
   ./scripts/migrate-gradle-structure.sh
   ```

3. **Validate Migration**

   ```bash
   ./gradlew validateAll
   ./gradlew buildHealth
   ```

4. **Update Team Documentation**
   - Update onboarding guides
   - Update build scripts
   - Notify team of changes

### Common Migration Issues

1. **Missing Convention Plugins**
   - Error: Plugin not found
   - Solution: Apply correct convention plugins

2. **Dependency Resolution**
   - Error: Version conflicts
   - Solution: Use version catalog entries

3. **Module Not Found**
   - Error: Module not included
   - Solution: Check directory structure matches pattern

## Troubleshooting

### Common Issues

#### Build Cache Issues

```bash
# Clear build cache
./gradlew cleanBuildCache

# Regenerate configuration cache
./gradlew cleanConfigurationCache
```

#### Dependency Conflicts

```bash
# Analyze dependencies
./gradlew dependencies

# Check dependency tree
./gradlew dependencyInsight --dependency <dependency-name>
```

#### Performance Issues

```bash
# Analyze performance
./gradlew analyzeBuildPerformance

# Profile specific task
./gradlew profileBuild
```

## Best Practices

### Development Workflow

1. **Use Convention Plugins**: Always prefer convention plugins over manual configuration
2. **Dependency Bundles**: Use bundles for common dependency groups
3. **Version Catalog**: Centralize all version declarations
4. **Regular Validation**: Run `validateAll` regularly
5. **Performance Monitoring**: Use build profiling tools

### Module Design

1. **Single Responsibility**: Each module should have one clear purpose
2. **Minimal Dependencies**: Depend only on what's necessary
3. **Clear Boundaries**: Respect platform/product boundaries
4. **Documentation**: Maintain proper @doc.\* tags
5. **Testing**: Comprehensive test coverage

### Build Performance

1. **Parallel Builds**: Enable parallel execution
2. **Build Cache**: Use build cache for incremental builds
3. **Configuration Cache**: Enable configuration cache
4. **Incremental Compilation**: Ensure incremental compilation is working
5. **Resource Management**: Optimize memory and CPU usage

## Support and Maintenance

### Getting Help

1. **Documentation**: Check this guide first
2. **Validation Tasks**: Run `validateAll` for diagnostics
3. **Performance Analysis**: Use `analyzeBuildPerformance`
4. **Team Communication**: Escalate to build engineering team

### Regular Maintenance

1. **Monthly**: Update dependency versions
2. **Quarterly**: Review build performance metrics
3. **Bi-annually**: Audit convention plugin usage
4. **Annually**: Review and optimize build structure

---

This guide is a living document. Please contribute improvements and report issues to the build engineering team.
