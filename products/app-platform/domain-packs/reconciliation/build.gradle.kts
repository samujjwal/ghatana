/**
 * reconciliation — D-13 Client Money Reconciliation domain pack
 *
 * Provides:
 *   - Daily reconciliation orchestrator and scheduler (D13-001)
 *   - Internal balance extraction from K-16 ledger (D13-002)
 *   - Reconciliation audit trail and evidence package (D13-003)
 *   - Bank statement ingestion engine (D13-004)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-13: Client Money Reconciliation domain pack"

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

    // ─── Kernel dependencies ─────────────────────────────────────────────────
    implementation(project(":kernel:event-store"))
    implementation(project(":kernel:config-engine"))
    implementation(project(":kernel:iam"))
    implementation(project(":kernel:audit-trail"))
    implementation(project(":kernel:calendar"))
    implementation(project(":kernel:ledger-framework"))

    // ─── Domain pack dependencies ────────────────────────────────────────────
    implementation(project(":domain-packs:post-trade"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Kafka (reconciliation events) ────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.promise)

    // ─── Micrometer ───────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
}
