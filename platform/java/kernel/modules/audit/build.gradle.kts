/**
 * audit — Generic Audit Kernel Module
 *
 * Provides product-agnostic audit logging capabilities for the Ghatana
 * kernel platform. This module wraps the existing audit platform library
 * and integrates it with the kernel framework. Contains NO finance-specific
 * logic and can be reused across all products.
 *
 * Capabilities:
 *   - Audit event recording and storage
 *   - Audit trail querying and reporting
 *   - Multi-tenant audit isolation
 *   - Immutable audit records
 *   - Compliance and governance support
 *
 * Dependencies:
 *   - Kernel core (KernelModule interface, KernelContext)
 *   - Audit platform library (existing implementation)
 *   - Configuration management capability
 *   - Data storage capability
 *   - Observability framework capability
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Audit Kernel Module - audit logging, trail querying, compliance support"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Kernel Core ────────────────────────────────────────────────────────
    api(project(":platform:java:kernel"))
    
    // ─── Audit Platform (Existing Implementation) ───────────────────────────
    api(project(":platform:java:audit"))
    
    // ─── Platform Capabilities (Required Dependencies) ───────────────────────
    api(project(":platform:java:config"))
    api(project(":platform:java:database"))
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
