/**
 * Platform Integration Tests
 *
 * Cross-module integration tests for platform security, observability, and database.
 * Tests real interactions between platform modules using ActiveJ event loop.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.platform"
description = "Platform Integration Tests - Cross-module integration tests"

dependencies {
    // Platform Testing - provides EventloopTestBase and test utilities
    testImplementation(project(":platform:java:testing"))
    
    // Platform Core - for core types used in integration tests
    testImplementation(project(":platform:java:core"))
    testImplementation(project(":platform:java:security"))
    testImplementation(project(":platform:java:observability"))
    testImplementation(project(":platform:java:database"))
    
    // JUnit 5
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    
    // Assertions
    testImplementation(libs.assertj.core)
    
    // Mocking
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    
    // Testcontainers for real database integration
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    
    // PostgreSQL JDBC driver
    testImplementation(libs.postgresql)
    
    // Logging for tests
    testImplementation(libs.log4j.slf4j.impl)
    testImplementation(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
    
    // Configure integration test tags
    systemProperty("junit.jupiter.conditions.deactivate", "io.activej.test.*")
    
    // Set up test environment
    systemProperty("test.environment", "integration")
}
