/**
 * secrets — Generic Secrets Management Kernel Module
 *
 * Provides product-agnostic secrets management, vault integration, and
 * credential rotation capabilities for the Ghatana kernel platform.
 * Contains NO product-specific logic and can be reused across all products.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Generic Secrets Management Kernel Module - vault integration, credential rotation"

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

    // ─── ActiveJ (Mandatory for kernel modules) ──────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Serialization ───────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ───────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    exclude("**/internal/**")
    options {
        encoding = "UTF-8"
        source = "21"
    }
}
