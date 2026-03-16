/**
 * surveillance — D-08 Surveillance domain pack
 *
 * Provides:
 *   - Wash trade detection engine (D08-001)
 *   - Wash trade pattern analysis (D08-002)
 *   - Spoofing detection engine (D08-004)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-08: Surveillance domain pack"

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
    implementation(project(":kernel:config-engine"))   // K-02
    implementation(project(":kernel:iam"))
    implementation(project(":kernel:audit-trail"))
    implementation(project(":kernel:rules-engine"))    // K-03

    // ─── Domain pack dependencies ────────────────────────────────────────────
    implementation(project(":domain-packs:oms"))
    implementation(project(":domain-packs:ems"))
    implementation(project(":domain-packs:reference-data"))

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
