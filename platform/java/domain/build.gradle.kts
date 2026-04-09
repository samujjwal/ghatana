/**
 * Platform Domain Module
 *
 * Domain models shared across platform modules.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Domain - Shared domain models"


dependencies {
    // Platform Core
    api(project(":platform:java:core"))
    api(project(":platform:java:agent-core"))
    implementation(project(":platform:contracts"))

    // Jackson for JSON serialization
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.dataformat.yaml)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // ActiveJ for async schema operations
    api(libs.activej.promise)
    api(libs.bundles.activej.core)

    // JSON Schema validation (schema-registry merger)
    implementation("com.networknt:json-schema-validator:1.0.87")

    // Logging
    implementation(libs.slf4j.api)

    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
