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


dependencies {
    // Platform Core — exposed as api so consumers get core types transitively
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // JSON Path — internal implementation detail, not exported
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    
    // Data Faker — internal implementation detail, not exported
    implementation("net.datafaker:datafaker:2.3.1")
    
    // gRPC — internal implementation detail, not exported
    implementation("io.grpc:grpc-api:1.79.0")
    implementation(libs.grpc.stub)
    
    // JUnit 5 — exposed as api so products don't need to re-declare
    api(libs.junit.jupiter)
    api(libs.bundles.testing.core)
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
    implementation("org.testcontainers:kafka:1.21.4")
    implementation("org.testcontainers:mongodb:1.21.4")
    
    // PostgreSQL JDBC driver — internal to testing module
    implementation(libs.postgresql)
    
    // Awaitility for async testing — exposed so products can use it
    api("org.awaitility:awaitility:4.2.2")
    
    // Logging for tests — internal
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    
    // Runtime
    runtimeOnly(libs.junit.jupiter.engine)
    runtimeOnly(libs.junit.platform.launcher)
    
    // ArchUnit — exposed as api so product test suites can write boundary tests
    // using GhatanaBoundaryRules without adding a direct archunit dependency.
    api("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.test {
    useJUnitPlatform()
}
