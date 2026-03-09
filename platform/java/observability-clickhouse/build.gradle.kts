/**
 * Platform Observability ClickHouse Module
 * 
 * ClickHouse-specific trace storage implementation extracted from observability module.
 * Provides SpanBuffer, ClickHouseTraceStorage, and ClickHouseConfig for high-throughput
 * distributed tracing persistence.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Observability ClickHouse - ClickHouse trace storage backend"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    
    // ClickHouse JDBC driver
    implementation("com.clickhouse:clickhouse-jdbc:0.6.0:all")
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
