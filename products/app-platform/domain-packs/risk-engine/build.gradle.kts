/**
 * risk-engine — D-06 Risk Engine domain pack
 *
 * Provides:
 *   - Margin sufficiency check with Redis atomic update (D06-001)
 *   - Position limit enforcement, no short-selling (D06-002)
 *   - Portfolio concentration limit (D06-003)
 *   - Initial margin aggregation across all positions (D06-009)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-06: Risk Engine domain pack"

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

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":domain-packs:reference-data"))
    implementation(project(":domain-packs:market-data"))
    implementation(project(":domain-packs:oms"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)

    // ─── Redis for margin/position cache ─────────────────────────────────────
    implementation(libs.jedis)

    // ─── Async runtime ───────────────────────────────────────────────────────
    implementation(libs.activej.promise)

    // ─── Observability ───────────────────────────────────────────────────────
    implementation(libs.micrometer.core)

    // ─── Logging ─────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)
}
