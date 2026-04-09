# Module Templates

This directory contains templates for creating consistent modules in the Ghatana monorepo.

## Available Templates

### Java Module Template (`java-module/`)
Standard template for Java modules with full convention plugin support.

#### Usage
1. Copy the template to your desired module location
2. Replace template variables:
   - `${MODULE_NAME}`: Module name (e.g., "User Service")
   - `${MODULE_PURPOSE}`: Purpose description (e.g., "User authentication and authorization")
   - `${MODULE_LAYER}`: Layer (e.g., "application", "domain", "infrastructure")
   - `${MODULE_PATTERN}`: Pattern (e.g., "Service", "Repository", "Controller")
   - `${MODULE_GROUP}`: Group ID (e.g., "com.ghatana.product")
   - `${MODULE_DESCRIPTION}`: Brief description
   - `${SPECIALIZED_PLUGINS}`: Additional plugins if needed
   - `${DEPENDENCIES}`: Module-specific dependencies

#### Example Usage
```bash
# Create a new service module
cp -r templates/java-module products/myproduct/core/user-service

# Edit the build.gradle.kts to replace variables:
# ${MODULE_NAME} -> "User Service"
# ${MODULE_PURPOSE} -> "User authentication and authorization"
# ${MODULE_LAYER} -> "application"
# ${MODULE_PATTERN} -> "Service"
# ${MODULE_GROUP} -> "com.ghatana.myproduct"
# ${MODULE_DESCRIPTION} -> "User authentication and authorization service"
# ${SPECIALIZED_PLUGINS} -> "" (or additional plugins)
# ${DEPENDENCIES} -> """
#     implementation(project(":platform:java:core"))
#     implementation(libs.bundles.activej.http)
# """
```

## Template Features

### Convention Plugins
All templates automatically include:
- `com.ghatana.java-conventions`: Java 21 toolchain, compilation settings
- `com.ghatana.testing-conventions`: JUnit 5, JaCoCo, test configuration
- `com.ghatana.quality-conventions`: Spotless, SpotBugs, PMD
- `com.ghatana.lombok-conventions`: Lombok configuration

### Dependency Guidelines
- Use platform modules for shared infrastructure
- Use dependency bundles for consistency
- Follow API vs implementation scoping rules
- Convention plugins handle basic test dependencies

### Documentation Standards
- All modules must have JavaDoc comments
- Use the standard documentation tags
- Include purpose, layer, and pattern information

## Adding New Templates

To add a new template:
1. Create a new directory under `templates/`
2. Include a `build.gradle.kts` with template variables
3. Add documentation to this README
4. Update the module creation scripts

## Module Creation Script

Use the provided script for automated module creation:
```bash
./scripts/create-module.sh --type java --name user-service --product myproduct --layer application
```

This script automatically:
- Copies the appropriate template
- Replaces template variables
- Creates the standard directory structure
- Adds the module to `settings.gradle.kts`
