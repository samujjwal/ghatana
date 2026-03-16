/**
 * post-trade — D-09 Post-Trade Processing domain pack
 *
 * Provides:
 *   - Trade confirmation document generation and delivery (D09-001, D09-002)
 *   - Bilateral netting calculation engine (D09-003)
 *   - Multilateral netting with CCP support (D09-004)
 *   - Netting report and reconciliation (D09-005)
 *   - Settlement instruction generation (D09-006)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-09: Post-Trade Processing domain pack"

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
    implementation(project(":kernel:plugin-runtime"))

    // ─── Domain pack dependencies ────────────────────────────────────────────
    implementation(project(":domain-packs:oms"))
    implementation(project(":domain-packs:ems"))
    implementation(project(":domain-packs:reference-data"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Kafka (post-trade events) ────────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.http)
    implementation(libs.activej.promise)

    // ─── Micrometer ───────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
}
