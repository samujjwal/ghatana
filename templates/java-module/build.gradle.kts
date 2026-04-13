/**
 * ${MODULE_NAME}
 *
 * @doc.type build-script
 * @doc.purpose ${MODULE_PURPOSE}
 * @doc.layer ${MODULE_LAYER}
 * @doc.pattern ${MODULE_PATTERN}
 */
plugins {
    // Standard Java conventions - provides Java 21 toolchain, compilation settings
    id("java-module")
    // Add module-specific plugin IDs here when needed (for example protobuf-module)
    ${SPECIALIZED_PLUGINS}
}

group = "${MODULE_GROUP}"
version = rootProject.version
description = "${MODULE_DESCRIPTION}"

dependencies {
    // Platform dependencies - use these when depending on platform modules
    // implementation(project(":platform:java:core"))
    // implementation(project(":platform:java:http"))
    // implementation(project(":platform:java:database"))
    
    // Library dependencies - use bundles when possible
    // implementation(libs.bundles.activej.core)
    // implementation(libs.bundles.jackson.json)
    // implementation(libs.bundles.logging.core)
    
    ${DEPENDENCIES}
    
    // Testing dependencies - convention plugins handle basic test setup
    // Add specialized test dependencies here:
    // testImplementation(libs.testcontainers.core)
    // testImplementation(libs.testcontainers.postgresql)
}
