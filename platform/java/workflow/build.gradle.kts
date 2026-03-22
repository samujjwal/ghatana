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
version = "2026.3.1-SNAPSHOT"

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
    
    // Metrics (for MetricsWorkflowListener)
    implementation(libs.micrometer.core)
    
    // JDBC backend (for JdbcWorkflow* classes)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    
    // Serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
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
