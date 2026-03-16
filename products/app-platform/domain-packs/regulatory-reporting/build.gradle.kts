/**
 * regulatory-reporting — D-10 Regulatory Reporting domain pack
 *
 * Provides:
 *   - Report definition registry with versioned templates (D10-001 to D10-003)
 *   - Real-time trade reporting submission (D10-004 to D10-005)
 *   - PDF, CSV/Excel, and XBRL rendering pipelines (D10-006 to D10-008)
 *   - Regulator submission adapter with ACK/NACK processing (D10-009 to D10-010)
 *   - Submission scheduling and audit trail (D10-011 to D10-012)
 *   - Trade report reconciliation and break management (D10-013)
 *   - Reporting analytics and dashboard (D10-014 to D10-015)
 *
 * Covered sprints: M2B Sprint 10 (per WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md §5, Sprint 10)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-10: Regulatory reporting — trade reports, PDF/CSV/XBRL rendering, regulator submission, ACK/NACK"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform ────────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:governance"))

    // ─── Kernel dependencies ─────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:calendar-service"))
    implementation(project(":products:app-platform:kernel:audit-trail"))

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":products:app-platform:domain-packs:oms"))
    implementation(project(":products:app-platform:domain-packs:post-trade"))
    implementation(project(":products:app-platform:domain-packs:reference-data"))

    // ─── AEP (event-driven report submission) ─────────────────────────────────
    implementation(project(":products:aep:platform"))

    // ─── PDF generation ───────────────────────────────────────────────────────
    implementation(libs.pdfbox)

    // ─── XBRL (regulatory XBRL taxonomy rendering) ───────────────────────────
    implementation(libs.xbrl.rendering)

    // ─── CSV/Excel ────────────────────────────────────────────────────────────
    implementation(libs.apache.poi)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)

    // ─── HTTP client (regulator REST submission endpoints) ────────────────────
    implementation(libs.okhttp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
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
