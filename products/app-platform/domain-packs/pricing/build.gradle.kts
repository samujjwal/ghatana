/**
 * pricing — D-05 Pricing Engine domain pack
 *
 * Provides:
 *   - Real-time price ingestion and distribution (D05-001)
 *   - EOD price capture and official close (D05-002)
 *   - Price history time-series queries (D05-003)
 *   - Yield curve construction engine (D05-004)
 *   - Price validation rules (D05-011)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-05: Pricing Engine domain pack"

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
    implementation(project(":kernel:calendar-service"))   // K-15

    // ─── Domain pack dependencies ────────────────────────────────────────────
    implementation(project(":domain-packs:reference-data"))
    implementation(project(":domain-packs:market-data"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)

    // ─── Redis for real-time price cache ─────────────────────────────────────
    implementation(libs.jedis)

    // ─── Async runtime ───────────────────────────────────────────────────────
    implementation(libs.activej.promise)

    // ─── Observability ───────────────────────────────────────────────────────
    implementation(libs.micrometer.core)

    // ─── Logging ─────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)
}
