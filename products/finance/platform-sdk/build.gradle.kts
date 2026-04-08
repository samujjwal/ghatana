/**
 * finance-platform-sdk — Finance Platform SDK Service
 *
 * Provides finance-specific SDK abstractions for the Ghatana platform.
 * This service aggregates core platform capabilities with finance-specific
 * extensions and optimizations for financial workflows.
 *
 * Capabilities:
 *   - Finance-specific event publishing with compliance metadata
 *   - Financial configuration management with audit trails
 *   - Regulatory audit logging with finance-specific fields
 *   - Financial rule evaluation with compliance checking
 *   - Finance-aware authentication with role-based access
 *
 * Dependencies:
 *   - Platform core utilities and JSON handling
 *   - Platform version management
 *   - Micrometer for metrics collection
 *   - ActiveJ Promise for async operations
 */
plugins {
    id("java-library")
}

group = "com.ghatana.finance"
version = rootProject.version
description = "Finance Platform SDK - finance-specific abstractions, compliance, regulatory support"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform Core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    
    // ─── Platform Kernel (for migrated modules) ───────────────────────────────
    api(project(":platform-kernel:kernel-core"))
    
    // ─── Platform Libraries ───────────────────────────────────────────────────
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform:java:workflow"))
    
    // ─── ActiveJ (Mandatory for platform compliance) ───────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    
    // ─── Database ─────────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    
    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
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
