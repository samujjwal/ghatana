/**
 * ems — D-02 Execution Management System domain pack
 *
 * Provides:
 *   - Smart Order Router with venue selection (D02-001)
 *   - Order splitting and child order management (D02-002)
 *   - FIX 4.4/5.0 protocol engine (D02-010)
 *   - Exchange adapter plugin interface (D02-011)
 *   - NEPSE trading adapter (D02-012)
 *   - Exchange adapter health monitoring (D02-013)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-02: Execution Management System domain pack"

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
    implementation(project(":kernel:plugin-runtime"))

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":domain-packs:oms"))
    implementation(project(":domain-packs:market-data"))
    implementation(project(":domain-packs:reference-data"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Redis (execution cache) ──────────────────────────────────────────────
    implementation(libs.jedis)

    // ─── Kafka (execution events) ─────────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.http)
    implementation(libs.activej.promise)

    // ─── Micrometer ──────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
}
