/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: D (Platform Infrastructure)
 * OWNER: @infra-team
 * PURPOSE: JDBC-backed persistence for workflow state (runs) and definitions.
 *          Requires PostgreSQL 14+ (JSONB support).
 */

plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform Modules
    api(project(":platform:java:workflow"))
    api(project(":platform:java:workflow-runtime"))
    implementation(project(":platform:java:core"))

    // ActiveJ
    api(libs.activej.promise)
    api(libs.activej.common)

    // JDBC
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)

    // JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Annotations
    compileOnly(libs.jetbrains.annotations)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
