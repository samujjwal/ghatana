# Source Sets Configuration Guide for Ghatana Project

This guide explains how to configure source and test folders in your Gradle build files.

## Quick Start

### 1. Basic Configuration (Most Modules)

For standard Java modules, the default configuration works automatically:

```gradle
// build.gradle
plugins {
    id 'java-library'
}

// No source sets configuration needed - defaults are:
// src/main/java    - Main source code
// src/main/resources - Main resources
// src/test/java    - Test source code  
// src/test/resources - Test resources
```

### 2. Apply Standard Source Sets

For modules that need additional source sets (integration tests, functional tests, etc.):

```gradle
// build.gradle
plugins {
    id 'java-library'
}

// Enable additional source sets
ext.enableIntegrationTests = true
ext.enableFunctionalTests = true

// Apply the configuration
apply from: "$rootDir/gradle/source-sets.gradle"
```

## Available Source Sets

### Main Source Sets
- **main**: Production code (`src/main/java`, `src/main/resources`)
- **test**: Unit tests (`src/test/java`, `src/test/resources`)

### Optional Source Sets
- **integrationTest**: Integration tests (`src/integrationTest/java`, `src/integrationTest/resources`)
- **functionalTest**: Functional tests (`src/functionalTest/java`, `src/functionalTest/resources`)
- **jmh**: Performance benchmarks (`src/jmh/java`, `src/jmh/resources`)

## Enabling Optional Source Sets

Add these properties to your `build.gradle` before applying the source sets:

```gradle
// Enable integration tests
ext.enableIntegrationTests = true

// Enable functional tests  
ext.enableFunctionalTests = true

// Enable JMH benchmarks
ext.enableJmh = true

// Apply configuration
apply from: "$rootDir/gradle/source-sets.gradle"
```

## Custom Source Directory Layout

If you need non-standard directory layouts:

```gradle
sourceSets {
    main {
        java {
            srcDirs = ['src/java', 'src/gen/java']
        }
        resources {
            srcDirs = ['src/resources', 'src/conf']
        }
    }
    
    test {
        java {
            srcDirs = ['test/java', 'test/integration/java']
        }
        resources {
            srcDirs = ['test/resources', 'test/fixtures']
        }
    }
}
```

## Running Different Test Types

```bash
# Run unit tests
./gradlew test

# Run integration tests (if enabled)
./gradlew integrationTest

# Run functional tests (if enabled)
./gradlew functionalTest

# Run all tests
./gradlew check
```

## Dependencies for Additional Source Sets

```gradle
dependencies {
    // Integration test dependencies
    integrationTestImplementation sourceSets.main.output
    integrationTestImplementation libs.testcontainers.junit.jupiter
    
    // Functional test dependencies
    functionalTestImplementation sourceSets.main.output
    functionalTestImplementation libs.test.wiremock
    
    // JMH dependencies
    jmhImplementation libs.jmh.core
    jmhAnnotationProcessor libs.jmh.generator.annprocess
}
```

## Examples in Your Project

### Standard Library Module
- **Location**: `libs/java/common-utils/build.gradle`
- **Structure**: Standard `src/main/java`, `src/test/java`

### Module with Generated Sources
- **Location**: `contracts/proto/build.gradle`
- **Structure**: Includes `build/generated/source/proto/main/java`

### Integration Test Module
- **Location**: `products/data-cloud/core/build.gradle`
- **Structure**: Adds `src/integrationTest/java`

## IDE Integration

The source sets configuration automatically works with IntelliJ and Eclipse:

1. **IntelliJ**: Run `./gradlew idea` to regenerate module files
2. **Eclipse**: Run `./gradlew eclipse` to generate project files
3. **VS Code**: Java extensions will detect the source sets automatically

## Best Practices

1. **Use standard Maven layout** when possible (`src/main/java`, `src/test/java`)
2. **Enable integration tests** for modules that need database/container testing
3. **Keep functional tests separate** from unit and integration tests
4. **Use JMH source set** for performance benchmarks
5. **Apply source sets consistently** across similar modules

## Troubleshooting

### Missing Source Folders
If IntelliJ doesn't show source folders:
```bash
./gradlew clean idea
./gradlew idea
```

### Test Dependencies Not Found
Ensure you add dependencies for additional source sets:
```gradle
integrationTestImplementation sourceSets.main.output
```

### Compilation Errors
Check that source directories exist and contain valid Java/Kotlin files.
