/**
 * Data-Cloud SDK Module
 *
 * Placeholder module for SDK generation. OpenAPI generator tasks are disabled
 * due to plugin resolution issues. This module can be re-enabled when the
 * OpenAPI generator plugin is properly configured.
 */
plugins {
    base
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version


dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
