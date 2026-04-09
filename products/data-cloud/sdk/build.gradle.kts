/**
 * Data-Cloud SDK Module
 *
 * Placeholder module for SDK generation. OpenAPI generator tasks are disabled
 * due to plugin resolution issues. This module can be re-enabled when the
 * OpenAPI generator plugin is properly configured.
 */
plugins {
    base
    id("java-library")
}

group = "com.ghatana.datacloud"
version = rootProject.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
