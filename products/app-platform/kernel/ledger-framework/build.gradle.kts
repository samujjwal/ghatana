/**
 * ledger-framework — Append-only double-entry accounting ledger
 *
 * Kernel module providing the financial ledger infrastructure for the platform:
 * - Append-only journal with REVOKE UPDATE/DELETE enforcement
 * - Double-entry balance enforcement (debits == credits per currency per journal)
 * - SHA-256 hash chain per account entry for tamper detection
 * - Chart of accounts with hierarchy and jurisdiction-specific T1 config
 * - Currency registry with precision rules (ISO 4217)
 * - Monetary amount value object with decimal precision
 * - Outbox table for K-17 distributed transaction coordination
 *
 * Sprint 3 stories: K16-001, K16-002, K16-003, K16-005, K16-006, K16-010, K16-016, K16-018
 *                   K17-001, K17-002, K17-003
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Append-only double-entry accounting ledger framework"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Platform dependencies
    // -------------------------------------------------------------------------
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    // -------------------------------------------------------------------------
    // ActiveJ async runtime
    // -------------------------------------------------------------------------
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // -------------------------------------------------------------------------
    // Persistence — JDBC + Flyway migrations
    // -------------------------------------------------------------------------
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // -------------------------------------------------------------------------
    // Testing
    // -------------------------------------------------------------------------
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
