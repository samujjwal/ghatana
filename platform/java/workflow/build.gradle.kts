/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 * 
 * PHASE: D (Platform Infrastructure)
 * OWNER: @infra-team
 * MIGRATED: 2026-02-04
 * SOURCE: ghatana/libs/java/workflow-api
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
    // ActiveJ Promise for async operations
    api(libs.activej.promise)
    api(libs.activej.common)

    // Platform Modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    
    // Annotations
    compileOnly(libs.jetbrains.annotations)
    
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
