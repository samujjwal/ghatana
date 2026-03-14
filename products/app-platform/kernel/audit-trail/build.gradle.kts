/**
 * audit-trail — Cryptographic hash-chain audit trail
 *
 * Generic platform kernel module providing an append-only audit log with
 * per-tenant SHA-256 hash chain for tamper detection, dual-calendar timestamps,
 * immutability enforcement triggers, and retention policy infrastructure.
 *
 * Suitable for any domain requiring cryptographically verifiable audit trails
 * (financial, medical, operational compliance, etc.).
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Append-only cryptographic hash-chain audit trail"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Platform reuse — audit abstractions
    // -------------------------------------------------------------------------
    api(project(":platform:java:audit"))
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    // -------------------------------------------------------------------------
    // event-store dependency — publish AuditLogCreatedEvent to the event store
    // -------------------------------------------------------------------------
    implementation(project(":products:app-platform:kernel:event-store"))

    // -------------------------------------------------------------------------
    // ActiveJ async runtime
    // -------------------------------------------------------------------------
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.inject)

    // -------------------------------------------------------------------------
    // Persistence — JDBC + Flyway migrations
    // -------------------------------------------------------------------------
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // -------------------------------------------------------------------------
    // PDF generation — audit evidence packages (K07-010)
    // -------------------------------------------------------------------------
    implementation(libs.pdfbox)

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
    // Code generation
    // -------------------------------------------------------------------------
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // -------------------------------------------------------------------------
    // Testing
    // -------------------------------------------------------------------------
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
