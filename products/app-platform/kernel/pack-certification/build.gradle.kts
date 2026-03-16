/**
 * pack-certification — Finance Plugin/Domain-Pack Certification Authority (M3C Sprint 15)
 *
 * Provides:
 *   - Plugin certification authority (CA) and certificate lifecycle (CERT-001 to CERT-003)
 *   - Static analysis and dependency vulnerability scanning (CERT-004 to CERT-005)
 *   - Dynamic sandbox testing harness (CERT-006)
 *   - Compliance verification checklist enforcement (CERT-007)
 *   - Plugin rating and review system (CERT-008)
 *   - Version management and release policy (CERT-009)
 *   - Certificate revocation and kill-switch (CERT-010)
 *   - Certification audit trail (CERT-011)
 *   - Policy engine for certification rules (CERT-012)
 *
 * LLD: PU-004 created Sprint 15, Week 1 per §4.3.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Pack certification authority: static analysis, sandbox testing, compliance checklist, version policy"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform plugin framework ────────────────────────────────────────────
    api(project(":platform:java:plugin"))
    api(project(":platform:java:security"))
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:plugin-runtime"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:secrets-management"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Cryptography (certificate generation, Ed25519 signing) ──────────────
    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── SAST / dependency scanning client ───────────────────────────────────
    implementation(libs.owasp.dependency.check)

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
