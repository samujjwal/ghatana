/**
 * Platform Observability Module
 * 
 * Provides metrics, tracing, logging, and health check utilities.
 * Built on Micrometer for metrics and OpenTelemetry for tracing.
 * 
 * NOTE: Some monitoring features using ActiveJ Launcher are disabled
 * as they require ActiveJ DI which is not available in 6.0-beta2.
 */
plugins {
    id("java-library")
    id("java-test-fixtures")
}

group = "com.ghatana.platform"
description = "Platform Observability - Metrics, tracing, and logging utilities"


dependencies {
    // Platform Core (includes ActiveJ)
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    api(project(":platform:java:config"))
    // NOTE: http dependency removed to break circular dependency
    // (observability should not depend on http - wrong direction)
    
    // ActiveJ Promise & HTTP (explicit)
    api(libs.activej.promise)
    api(libs.activej.http)
    api(libs.activej.inject)
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    
    // AspectJ
    implementation("org.aspectj:aspectjrt:1.9.20")
    
    // Metrics - Micrometer
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
    
    // Tracing - OpenTelemetry
    api(libs.opentelemetry.api)
    api("io.opentelemetry:opentelemetry-sdk:1.46.0")
    api("io.opentelemetry:opentelemetry-exporter-otlp:1.46.0")
    
    // Redis (for health checks)
    implementation(libs.jedis)
    
    // ClickHouse trace storage backend
    implementation("com.clickhouse:clickhouse-jdbc:0.6.0:all")
    
    // Logging
    api(libs.slf4j.api)
    
    // Testing
    testFixturesApi(project(":platform:java:core"))
    testFixturesApi(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.46.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
