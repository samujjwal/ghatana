/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 * 
 * PHASE: D (Platform Infrastructure)
 * OWNER: @infra-team
 * MIGRATED: 2026-02-04
 * SOURCE: ghatana/libs/java/plugin-framework
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
    // Platform Core
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:event-cloud"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:governance"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.common)
    
    // JSON processing
    implementation(libs.jackson.databind)
    
    // Annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
