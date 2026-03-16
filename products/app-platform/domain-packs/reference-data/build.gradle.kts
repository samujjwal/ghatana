/**
 * reference-data — D-11 Instrument and Entity Reference Data domain pack
 *
 * Provides:
 *   - Instrument master CRUD with temporal validity (D11-001, D11-002)
 *   - Instrument status lifecycle and event emission (D11-003)
 *   - Entity master and relationship graph (D11-004, D11-005)
 *   - Benchmark and index data model (D11-006, D11-007)
 *   - T3 reference data feed adapter framework (D11-008)
 *   - NEPSE/CDSC reference data adapter (D11-009)
 *   - Reference data snapshot service (D11-010)
 *   - Change audit trail (D11-011)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-11: Instrument and entity reference data domain pack"

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
    implementation(project(":kernel:audit-trail"))
    implementation(project(":kernel:config-engine"))
    implementation(project(":kernel:iam"))
    implementation(project(":kernel:plugin-runtime"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Redis (caching) ─────────────────────────────────────────────────────
    implementation(libs.jedis)

    // ─── Serialization ───────────────────────────────────────────────────────
    implementation(libs.jackson.databind)

    // ─── Logging / metrics ───────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
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
