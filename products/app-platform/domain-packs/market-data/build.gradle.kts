/**
 * market-data — D-04 Real-time Market Data domain pack
 *
 * Provides:
 *   - MarketTick ingestion via TimescaleDB hypertable (D04-001)
 *   - Multi-source feed adapter framework with priority failover (D04-002)
 *   - Tick validation and anomaly detection (D04-003)
 *   - L1 top-of-book quote distribution via Kafka + Redis (D04-004)
 *   - WebSocket streaming gateway for real-time subscribers (D04-014)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-04: Real-time market tick data domain pack"

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
    implementation(project(":kernel:plugin-runtime"))
    implementation(project(":kernel:iam"))

    // ─── D11 reference data (for instrument validation) ───────────────────────
    implementation(project(":domain-packs:reference-data"))

    // ─── Persistence (TimescaleDB = PostgreSQL + extension) ──────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Redis (L1 live quote cache) ──────────────────────────────────────────
    implementation(libs.jedis)

    // ─── Kafka (Kafka event publishing) ───────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── ActiveJ HTTP (WebSocket gateway) ────────────────────────────────────
    implementation(libs.activej.http)

    // ─── Serialization ───────────────────────────────────────────────────────
    implementation(libs.jackson.databind)

    // ─── Logging / metrics ───────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
