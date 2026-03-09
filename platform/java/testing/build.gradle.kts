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
    // Platform Core
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // JSON Path
    api(libs.json.path)
    
    // Data Faker
    api(libs.datafaker)
    
    // gRPC
    api(libs.grpc.api)
    api(libs.grpc.stub)
    
    // JUnit 5
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    
    // Assertions
    api(libs.assertj.core)
    
    // Mocking
    api(libs.mockito.core)
    api(libs.mockito.junit.jupiter)
    
    // Test Containers (for integration tests)
    api(libs.testcontainers.core)
    api(libs.testcontainers.junit.jupiter)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)
    api(libs.testcontainers.mongodb)
    
    // PostgreSQL JDBC driver (for DataSource in integration tests)
    api(libs.postgresql)
    
    // Awaitility for async testing
    api(libs.awaitility)
    
    // Logging for tests - log4j2 with slf4j bridge
    api(libs.log4j.slf4j.impl)
    api(libs.log4j.core)
    
    // Runtime
    runtimeOnly(libs.junit.jupiter.engine)
    runtimeOnly(libs.junit.platform.launcher)
    
    // ArchUnit
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    useJUnitPlatform()
}
