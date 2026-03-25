/**
 * Platform Testing Module
 * 
 * Provides common testing utilities, fixtures, and helpers.
 * Designed to be used by all product test suites.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Testing - Common testing utilities and fixtures"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform Core — exposed as api so consumers get core types transitively
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // JSON Path — internal implementation detail, not exported
    implementation(libs.json.path)
    
    // Data Faker — internal implementation detail, not exported
    implementation(libs.datafaker)
    
    // gRPC — internal implementation detail, not exported
    implementation(libs.grpc.api)
    implementation(libs.grpc.stub)
    
    // JUnit 5 — exposed as api so products don't need to re-declare
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    
    // Assertions — exposed as api so products don't need to re-declare
    api(libs.assertj.core)
    
    // Mocking — exposed as api so products don't need to re-declare
    api(libs.mockito.core)
    api(libs.mockito.junit.jupiter)
    
    // Test Containers — internal, products that need them declare their own
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.kafka)
    implementation(libs.testcontainers.mongodb)
    
    // PostgreSQL JDBC driver — internal to testing module
    implementation(libs.postgresql)
    
    // Awaitility for async testing — exposed so products can use it
    api(libs.awaitility)
    
    // Logging for tests — internal
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    
    // Runtime
    runtimeOnly(libs.junit.jupiter.engine)
    runtimeOnly(libs.junit.platform.launcher)
    
    // ArchUnit — exposed as api so product test suites can write boundary tests
    // using GhatanaBoundaryRules without adding a direct archunit dependency.
    api(libs.archunit.junit5)
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    useJUnitPlatform()
}
