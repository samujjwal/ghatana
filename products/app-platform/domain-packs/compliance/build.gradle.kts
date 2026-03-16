/**
 * compliance — D-07 Compliance Engine domain pack
 *
 * Provides:
 *   - Rule orchestration pipeline: evaluate configurable rule sets (D07-001)
 *   - Jurisdiction-specific rule routing via K-03 OPA (D07-002)
 *   - Compliance check audit trail (D07-003)
 *   - Lock-in period enforcement with BS calendar (D07-004, D07-005)
 *   - KYC status validation (D07-006)
 *   - Restricted and watch list checks (D07-011)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-07: Compliance Engine domain pack"

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
    implementation(project(":kernel:rules"))
    implementation(project(":kernel:event-store"))
    implementation(project(":kernel:config-engine"))
    implementation(project(":kernel:iam"))
    implementation(project(":kernel:calendar"))
    implementation(project(":kernel:audit-trail"))

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":domain-packs:reference-data"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Kafka ───────────────────────────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
