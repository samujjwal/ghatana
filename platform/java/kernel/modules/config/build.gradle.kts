/**
 * config — Generic Configuration Kernel Module
 *
 * Provides product-agnostic configuration management capabilities for the
 * Ghatana kernel platform. This module wraps the existing platform config
 * library and integrates it with the kernel framework. Contains NO
 * finance-specific logic and can be reused across all products.
 *
 * Capabilities:
 *   - Hierarchical configuration resolution
 *   - Multiple configuration sources (files, env vars, system props)
 *   - Runtime configuration updates
 *   - Configuration validation and type safety
 *   - Tenant-specific configuration isolation
 *
 * Dependencies:
 *   - Kernel core (KernelModule interface, KernelContext)
 *   - Platform config library (existing implementation)
 *   - Observability framework capability
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Configuration Kernel Module - hierarchical config, multiple sources, runtime updates"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Kernel Core ────────────────────────────────────────────────────────
    api(project(":platform:java:kernel"))
    
    // ─── Platform Config (Existing Implementation) ─────────────────────────
    api(project(":platform:java:config"))
    
    // ─── Platform Capabilities (Required Dependencies) ───────────────────────
    api(project(":platform:java:observability"))
    
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
