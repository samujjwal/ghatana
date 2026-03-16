/**
 * oms — D-01 Order Management System domain pack
 *
 * Provides:
 *   - Order capture REST API with idempotent submission (D01-001)
 *   - Order field validation and market price enrichment (D01-002)
 *   - Market session and holiday validation (D01-003)
 *   - 9-state order lifecycle state machine (D01-004)
 *   - Event-sourced order reconstruction with snapshots (D01-005)
 *   - Order amendment with re-validation (D01-006)
 *   - Order cancellation with cascade (D01-007)
 *   - Pre-trade compliance and risk pipeline (D01-008, D01-009, D01-010)
 *   - Maker-checker approval workflow (D01-011, D01-012)
 *   - Order routing to EMS and fill processing (D01-013, D01-014)
 *   - Real-time position projection CQRS read model (D01-016)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-01: Order Management System domain pack"

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
    implementation(project(":kernel:calendar"))
    implementation(project(":kernel:audit-trail"))

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":domain-packs:reference-data"))
    implementation(project(":domain-packs:market-data"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Redis (position cache) ───────────────────────────────────────────────
    implementation(libs.jedis)

    // ─── Kafka (order events) ─────────────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.core)
    implementation(libs.activej.http)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
