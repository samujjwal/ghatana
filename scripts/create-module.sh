#!/bin/bash

# =============================================================================
# Module Creation Script
# =============================================================================
# Purpose: Create new modules with standard structure and templates
# Usage: ./scripts/create-module.sh <type> <path> <name>
# Types: platform, product, shared, integration
# Example: ./scripts/create-module.sh platform java core-utils
# =============================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TEMPLATE_DIR="$PROJECT_ROOT/templates"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Usage information
usage() {
    echo "Usage: $0 <type> <path> <name>"
    echo ""
    echo "Types:"
    echo "  platform    - Platform module (e.g., platform/java/core-utils)"
    echo "  product     - Product module (e.g., products/yappc/core/new-service)"
    echo "  shared      - Shared service module (e.g., shared-services/new-service)"
    echo "  integration - Integration test module (e.g., integration-tests/product-a-b)"
    echo ""
    echo "Examples:"
    echo "  $0 platform java core-utils"
    echo "  $0 product yappc core new-service"
    echo "  $0 shared new-service"
    echo "  $0 integration product-a-b"
    echo ""
    exit 1
}

# Parse arguments
if [ $# -ne 3 ]; then
    usage
fi

MODULE_TYPE="$1"
MODULE_PATH="$2"
MODULE_NAME="$3"

# Validate module type
case "$MODULE_TYPE" in
    platform|product|shared|integration)
        ;;
    *)
        log_error "Invalid module type: $MODULE_TYPE"
        usage
        ;;
esac

# Determine full module path
case "$MODULE_TYPE" in
    platform)
        FULL_PATH="platform/$MODULE_PATH/$MODULE_NAME"
        MODULE_GROUP="com.ghatana.platform"
        MODULE_LAYER="platform"
        ;;
    product)
        FULL_PATH="products/$MODULE_PATH/$MODULE_NAME"
        MODULE_GROUP="com.ghatana.products.$MODULE_PATH"
        MODULE_LAYER="product"
        ;;
    shared)
        FULL_PATH="shared-services/$MODULE_NAME"
        MODULE_GROUP="com.ghatana.shared-services"
        MODULE_LAYER="shared-services"
        ;;
    integration)
        FULL_PATH="integration-tests/$MODULE_NAME"
        MODULE_GROUP="com.ghatana.integration-tests"
        MODULE_LAYER="integration-tests"
        ;;
esac

MODULE_PROJECT_PATH=":$FULL_PATH"

# Check if module already exists
MODULE_DIR="$PROJECT_ROOT/$FULL_PATH"
if [ -d "$MODULE_DIR" ]; then
    log_error "Module already exists: $FULL_PATH"
    exit 1
fi

# Create module directory structure
log_info "Creating module directory: $FULL_PATH"
mkdir -p "$MODULE_DIR/src/main/java"
mkdir -p "$MODULE_DIR/src/test/java"
mkdir -p "$MODULE_DIR/src/main/resources"
mkdir -p "$MODULE_DIR/src/test/resources"

# Determine Java package structure
JAVA_PACKAGE_PATH=""
case "$MODULE_TYPE" in
    platform)
        JAVA_PACKAGE_PATH="com/ghatana/platform/$MODULE_PATH/$MODULE_NAME"
        ;;
    product)
        JAVA_PACKAGE_PATH="com/ghatana/products/$MODULE_PATH/$MODULE_NAME"
        ;;
    shared)
        JAVA_PACKAGE_PATH="com/ghatana/shared/services/$MODULE_NAME"
        ;;
    integration)
        JAVA_PACKAGE_PATH="com/ghatana/integration/tests/$MODULE_NAME"
        ;;
esac

# Create Java package directories
mkdir -p "$MODULE_DIR/src/main/java/$JAVA_PACKAGE_PATH"
mkdir -p "$MODULE_DIR/src/test/java/$JAVA_PACKAGE_PATH"

# Generate build.gradle.kts
log_info "Generating build.gradle.kts"
BUILD_FILE="$MODULE_DIR/build.gradle.kts"

# Determine module pattern and specialized plugins
MODULE_PATTERN="Library"
SPECIALIZED_PLUGINS=""

case "$MODULE_TYPE" in
    platform)
        if [[ "$MODULE_PATH" == *"java"* ]]; then
            MODULE_PATTERN="Library"
        elif [[ "$MODULE_PATH" == *"kernel"* ]]; then
            MODULE_PATTERN="Kernel"
        elif [[ "$MODULE_PATH" == *"contracts"* ]]; then
            MODULE_PATTERN="Contract"
            SPECIALIZED_PLUGINS="    id(\"com.ghatana.protobuf-conventions\")"
        fi
        ;;
    product)
        if [[ "$MODULE_NAME" == *"api"* ]] || [[ "$MODULE_NAME" == *"client"* ]]; then
            MODULE_PATTERN="Library"
        elif [[ "$MODULE_NAME" == *"service"* ]] || [[ "$MODULE_NAME" == *"server"* ]]; then
            MODULE_PATTERN="Service"
        elif [[ "$MODULE_NAME" == *"app"* ]] || [[ "$MODULE_NAME" == *"application"* ]]; then
            MODULE_PATTERN="Application"
        fi
        ;;
    shared)
        MODULE_PATTERN="Service"
        ;;
    integration)
        MODULE_PATTERN="Integration"
        ;;
esac

# Generate dependencies
DEPENDENCIES=""
case "$MODULE_TYPE" in
    platform)
        DEPENDENCIES="    // Platform modules typically use api() for public APIs
    api(libs.bundles.activej.core)
    
    // Common utilities
    implementation(libs.bundles.common.utils)
    
    // Testing
    testImplementation(libs.bundles.testing.core)"
        ;;
    product)
        DEPENDENCIES="    // Product modules typically depend on platform modules
    implementation(project(\":platform:java:core\"))
    implementation(libs.bundles.activej.http)
    
    // Testing
    testImplementation(libs.bundles.testing.core)"
        ;;
    shared)
        DEPENDENCIES="    // Shared services use ActiveJ HTTP and common utilities
    implementation(libs.bundles.activej.http)
    implementation(libs.bundles.jackson.json)
    implementation(libs.bundles.common.utils)
    
    // Testing
    testImplementation(libs.bundles.testing.core)"
        ;;
    integration)
        DEPENDENCIES="    // Integration tests depend on the modules being tested
    // Add dependencies on the modules you're integrating
    
    // Testing with Testcontainers
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.bundles.testing.containers)"
        ;;
esac

# Create build.gradle.kts from template
cat > "$BUILD_FILE" << EOF
/**
 * $MODULE_NAME
 *
 * @doc.type build-script
 * @doc.purpose $MODULE_NAME module
 * @doc.layer $MODULE_LAYER
 * @doc.pattern $MODULE_PATTERN
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
$SPECIALIZED_PLUGINS
}

group = "$MODULE_GROUP"
version = rootProject.version
description = "$MODULE_NAME module"

dependencies {
$DEPENDENCIES
}
EOF

# Create sample Java class
log_info "Creating sample Java class"
JAVA_CLASS_FILE="$MODULE_DIR/src/main/java/$JAVA_PACKAGE_PATH/${MODULE_NAME^}Service.java"

cat > "$JAVA_CLASS_FILE" << EOF
package $JAVA_PACKAGE_PATH.replace(/\//g, '.');

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $MODULE_NAME Service
 *
 * @doc.type class
 * @doc.purpose $MODULE_NAME service implementation
 * @doc.layer $MODULE_LAYER
 * @doc.pattern Service
 */
public class ${MODULE_NAME^}Service {
    
    private static final Logger logger = LoggerFactory.getLogger(${MODULE_NAME^}Service.class);
    
    /**
     * Default constructor.
     */
    public ${MODULE_NAME^}Service() {
        logger.info("${MODULE_NAME^}Service initialized");
    }
    
    /**
     * Process the given input.
     *
     * @param input the input to process
     * @return the processed result
     */
    public String process(String input) {
        logger.debug("Processing input: {}", input);
        
        // TODO: Implement actual processing logic
        String result = "Processed: " + input;
        
        logger.debug("Processing result: {}", result);
        return result;
    }
}
EOF

# Create sample test class
log_info "Creating sample test class"
TEST_CLASS_FILE="$MODULE_DIR/src/test/java/$JAVA_PACKAGE_PATH/${MODULE_NAME^}ServiceTest.java"

cat > "$TEST_CLASS_FILE" << EOF
package $JAVA_PACKAGE_PATH.replace(/\//g, '.');

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * $MODULE_NAME Service Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for $MODULE_NAME service
 * @doc.layer $MODULE_LAYER
 * @doc.pattern Test
 */
@DisplayName("${MODULE_NAME} Service Tests")
class ${MODULE_NAME^}ServiceTest {
    
    private ${MODULE_NAME^}Service service;
    
    @BeforeEach
    void setUp() {
        service = new ${MODULE_NAME^}Service();
    }
    
    @Test
    @DisplayName("Should process input correctly")
    void shouldProcessInputCorrectly() {
        // Given
        String input = "test input";
        
        // When
        String result = service.process(input);
        
        // Then
        assertThat(result).isEqualTo("Processed: test input");
    }
    
    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputGracefully() {
        // Given
        String input = null;
        
        // When & Then
        // TODO: Implement proper null handling test
        // assertThatThrownBy(() -> service.process(input))
        //     .isInstanceOf(IllegalArgumentException.class);
    }
}
EOF

# Create README.md
log_info "Creating README.md"
README_FILE="$MODULE_DIR/README.md"

cat > "$README_FILE" << EOF
# $MODULE_NAME

## Purpose

$MODULE_NAME module for $MODULE_TYPE.

## Structure

- \`src/main/java/$JAVA_PACKAGE_PATH\` - Main source code
- \`src/test/java/$JAVA_PACKAGE_PATH\` - Test source code
- \`src/main/resources\` - Main resources
- \`src/test/resources\` - Test resources

## Dependencies

This module depends on:

- ActiveJ framework (async HTTP and utilities)
- Jackson JSON processing
- Common utilities
- JUnit 5 for testing

## Usage

\`\`\`java
$JAVA_PACKAGE_PATH.replace(/\//g, '.').${MODULE_NAME^}Service service = new $JAVA_PACKAGE_PATH.replace(/\//g, '.').${MODULE_NAME^}Service();
String result = service.process("input");
\`\`\`

## Development

### Building

\`\`\`bash
./gradlew :$MODULE_PROJECT_PATH:build
\`\`\`

### Testing

\`\`\`bash
./gradlew :$MODULE_PROJECT_PATH:test
\`\`\`

### Running Tests with Coverage

\`\`\`bash
./gradlew :$MODULE_PROJECT_PATH:test jacocoTestReport
\`\`\`

## Module Information

- **Group**: $MODULE_GROUP
- **Layer**: $MODULE_LAYER
- **Pattern**: $MODULE_PATTERN
- **Path**: $FULL_PATH
EOF

# Create package-info.java
log_info "Creating package-info.java"
PACKAGE_INFO_FILE="$MODULE_DIR/src/main/java/$JAVA_PACKAGE_PATH/package-info.java"

PACKAGE_DIR=$(dirname "$PACKAGE_INFO_FILE")
mkdir -p "$PACKAGE_DIR"

cat > "$PACKAGE_INFO_FILE" << EOF
/**
 * $MODULE_NAME package.
 *
 * @doc.type package
 * @doc.purpose $MODULE_NAME implementation package
 * @doc.layer $MODULE_LAYER
 * @doc.pattern Package
 */
package $JAVA_PACKAGE_PATH.replace(/\//g, '.');
EOF

log_success "Module created successfully: $FULL_PATH"

# Next steps
echo ""
log_info "Next steps:"
echo "1. Review the generated files and customize as needed"
echo "2. Add your business logic to ${MODULE_NAME^}Service.java"
echo "3. Update tests in ${MODULE_NAME^}ServiceTest.java"
echo "4. Add additional dependencies to build.gradle.kts if needed"
echo "5. Run tests: ./gradlew :$MODULE_PROJECT_PATH:test"
echo "6. Build module: ./gradlew :$MODULE_PROJECT_PATH:build"
echo ""
log_info "Module location: $MODULE_DIR"
log_info "Project path: :$MODULE_PROJECT_PATH"
