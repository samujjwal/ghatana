/**
 * regulator-portal — Finance Regulator Portal kernel module (R-01, Sprint 15)
 *
 * Provides:
 *   - Off-site regulator access portal with MFA authentication (REG-001 to REG-002)
 *   - Data room export with tenant consent management (REG-003 to REG-004)
 *   - Regulatory query interpreter (sandboxed, read-only SQL) (REG-005)
 *   - Report request and submission management (REG-006 to REG-007)
 *   - Secure messaging between regulator and operator (REG-008)
 *   - Regulatory audit export with tamper-evident trails (REG-009)
 *
 * LLD: R-01 created Sprint 15, Week 1 per §4.3.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "R-01: Regulator portal — secure access, data room, sandboxed queries, audit exports, consent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    api(project(":platform:java:security"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:observability"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:data-governance"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Cryptography (JWT, secure messaging) ─────────────────────────────────
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bouncycastle.provider)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
