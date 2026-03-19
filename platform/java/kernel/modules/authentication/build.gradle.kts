/**
 * authentication — Generic Authentication Kernel Module
 *
 * Provides product-agnostic authentication, authorization, and token management
 * capabilities for the Ghatana kernel platform. This module contains NO
 * finance-specific logic and can be reused across all products.
 *
 * Capabilities:
 *   - User authentication (password, MFA, SSO, OAuth)
 *   - Role-based access control (RBAC)
 *   - JWT token management and validation
 *   - Session management
 *   - Audit logging for authentication events
 *
 * Dependencies:
 *   - Kernel core (KernelModule interface, KernelContext)
 *   - Configuration management capability
 *   - Data storage capability
 *   - Observability framework capability
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Authentication Kernel Module - product-agnostic auth, RBAC, tokens"

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
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    
    // ─── ActiveJ (Mandatory for kernel modules) ───────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // ─── Security ───────────────────────────────────────────────────────────
    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)
    
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
