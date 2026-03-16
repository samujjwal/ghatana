/**
 * pms — D-03 Portfolio Management System domain pack
 *
 * Provides:
 *   - Portfolio entity CRUD with dual-calendar dates (D03-001)
 *   - Target allocation and constraint engine (D03-002)
 *   - Daily NAV calculation engine (D03-004)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-03: Portfolio Management System domain pack"

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
    implementation(project(":kernel:calendar-service"))   // K-15

    // ─── Domain pack dependencies ────────────────────────────────────────────
    implementation(project(":domain-packs:reference-data"))
    implementation(project(":domain-packs:market-data"))
    implementation(project(":domain-packs:pricing"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)

    // ─── Async runtime ───────────────────────────────────────────────────────
    implementation(libs.activej.promise)

    // ─── Observability ───────────────────────────────────────────────────────
    implementation(libs.micrometer.core)

    // ─── Logging ─────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)
}
