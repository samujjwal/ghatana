/**
 * resilience — Generic Resilience Kernel Module
 *
 * Provides product-agnostic resilience patterns for the Ghatana kernel platform.
 * This module contains NO finance-specific logic and can be reused across all products.
 *
 * Capabilities:
 *   - Circuit breaker pattern
 *   - Retry mechanism with exponential backoff
 *   - Bulkhead pattern for resource isolation
 *   - Timeout management
 *   - Fallback pattern
 *
 * Dependencies:
 *   - Kernel core (KernelModule interface, KernelContext)
 *   - Configuration management capability
 *   - Observability framework capability
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Resilience Kernel Module - circuit breaker, retry, bulkhead, timeout, fallback"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Kernel Core ────────────────────────────────────────────────────────
    api(project(":platform:java:kernel"))
    
    // ─── Platform Capabilities (Required Dependencies) ───────────────────────
    api(project(":platform:java:config"))
    api(project(":platform:java:observability"))
    
    // ─── ActiveJ (Mandatory for kernel modules) ───────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // ─── Resilience4j (Production-grade resilience patterns) ───────────────────
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.bulkhead)
    implementation(libs.resilience4j.timelimiter)
    implementation(libs.resilience4j.cache)
    
    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    
    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ─── JavaDoc Generation ─────────────────────────────────────────────────────
tasks.withType<Javadoc> {
    exclude("**/internal/**")
    options {
        encoding = "UTF-8"
        source = "21"
    }
}
