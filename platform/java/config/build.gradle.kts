/**
 * Platform Config Module
 *
 * Provides configuration management, runtime configuration,
 * config validation, and config providers.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.platform"
description = "Platform Config - Configuration management and runtime config"


dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))

    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)

    // Typesafe Config (Lightbend/Hocon)
    api("com.typesafe:config:1.4.3")

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Jackson & YAML
    api(libs.jackson.databind)
    api(libs.jackson.dataformat.yaml)

    // JSON Schema Validation
    implementation("com.networknt:json-schema-validator:1.0.87")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
