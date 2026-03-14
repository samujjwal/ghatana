/**
 * iam — Identity and Access Management foundation
 *
 * Provides:
 *   - RS256 JWT token generation using Nimbus JOSE+JWT (K01-001)
 *   - OAuth 2.0 client_credentials flow (K01-002)
 *   - RBAC domain model: roles, permissions (K01-010)
 *   - Pluggable key provider port for HSM/K-14 integration (K01-003)
 *
 * K-01 stories: K01-001 through K01-022.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "IAM foundation: RS256 JWT, client_credentials, RBAC"

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

    // ─── Secrets (K-14 integration for signing keys) ──────────────────────────
    implementation(project(":products:app-platform:kernel:secrets-management"))

    // ─── ActiveJ async runtime ────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── JWT — Nimbus JOSE+JWT (canonical choice) ────────────────────────────
    implementation(libs.nimbus.jose.jwt)

    // ─── Cryptography ─────────────────────────────────────────────────────────
    implementation(libs.bouncycastle.provider)

    // ─── Platform security (PasswordHasher, etc.) ─────────────────────────────
    implementation(project(":platform:java:security"))

    // ─── BCrypt (client credential hashing) ──────────────────────────────────
    implementation(libs.jbcrypt)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Logging / metrics ────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Redis (session store) ────────────────────────────────────────────────
    implementation(libs.jedis)

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
