/**
 * event-store — Generic Event Store Kernel Module
 *
 * Provides product-agnostic event storage and streaming capabilities for the
 * Ghatana kernel platform. This module wraps the existing EventCloud platform
 * library and integrates it with the kernel framework. Contains NO
 * finance-specific logic and can be reused across all products.
 *
 * Capabilities:
 *   - Append-only immutable event log
 *   - Real-time event streaming and tailing
 *   - Historical event queries and scans
 *   - Multi-tenant event isolation
 *   - Idempotent event publishing
 *   - Event schema validation
 *
 * Dependencies:
 *   - Kernel core (KernelModule interface, KernelContext)
 *   - EventCloud platform library (existing implementation)
 *   - Configuration management capability
 *   - Data storage capability
 *   - Observability framework capability
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Event Store Kernel Module - append-only log, real-time streaming, multi-tenant isolation"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Kernel Core ────────────────────────────────────────────────────────
    api(project(":platform:java:kernel"))
    
    // ─── EventCloud Platform (Existing Implementation) ───────────────────────
    api(project(":platform:java:event-cloud"))
    
    // ─── Platform Capabilities (Required Dependencies) ───────────────────────
    api(project(":platform:java:config"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    
    // ─── Platform Types ───────────────────────────────────────────────────────
    api(project(":platform:java:types"))
    
    // ─── ActiveJ (Mandatory for kernel modules) ───────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
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
