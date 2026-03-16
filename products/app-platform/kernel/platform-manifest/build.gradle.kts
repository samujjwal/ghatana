/**
 * platform-manifest — Finance Platform Manifest & Release Orchestration (M3C Sprint 15)
 *
 * Provides:
 *   - Release manifest generation, signing, and verification (MAN-001 to MAN-004)
 *   - Release packaging pipeline (MAN-005)
 *   - Platform upgrade orchestration with post-upgrade smoke tests (MAN-006 to MAN-008)
 *   - Tenant upgrade scheduling (MAN-009)
 *   - Upgrade history and audit trail (MAN-010)
 *   - Release notes generation (MAN-011)
 *
 * LLD: PU-004 created Sprint 15, Week 1 per §4.3.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Platform manifest: release signing, upgrade orchestration, tenant scheduling, audit history"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:security"))
    api(project(":platform:java:observability"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:secrets-management"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:operator-workflows"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Cryptography (Ed25519 manifest signing) ──────────────────────────────
    implementation(libs.bouncycastle.provider)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
