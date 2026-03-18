/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: D (Platform Infrastructure)
 * OWNER: @infra-team
 * PURPOSE: Workflow runtime engine — definition registry, durable runtime,
 *          step executors (parallel, wait, sub-workflow), CEL expression evaluator,
 *          and observability listeners.
 */

plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "2026.3.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform Modules
    api(project(":platform:java:workflow"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))

    // ActiveJ
    api(libs.activej.promise)
    api(libs.activej.common)

    // Metrics
    implementation(libs.micrometer.core)

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
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
