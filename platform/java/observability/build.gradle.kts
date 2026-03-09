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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// HTTP handlers have been extracted to :platform:java:observability-http (see ADR-007)

dependencies {
    // Platform Core (includes ActiveJ)
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    api(project(":platform:java:config"))
    
    // ActiveJ Promise & HTTP (explicit)
    api(libs.activej.promise)
    api(libs.activej.http)
    api(libs.activej.inject)
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // AspectJ
    implementation("org.aspectj:aspectjrt:1.9.20")
    
    // Metrics - Micrometer
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
    
    // Tracing - OpenTelemetry
    api(libs.opentelemetry.api)
    api(libs.opentelemetry.sdk)
    api(libs.opentelemetry.exporter.otlp)
    
    // Redis (for health checks)
    implementation(libs.jedis)
    
    // Logging
    api(libs.slf4j.api)
    
    // Testing
    testFixturesApi(project(":platform:java:core"))
    testFixturesApi(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
