/**
 * secrets-management — Pluggable secret provider framework
 *
 * Provides a SecretProvider port with:
 *   - LocalFileSecretProvider (AES-256-GCM + Argon2id key derivation via BouncyCastle)
 *   - SecretProviderRegistry (selects implementation from config)
 *   - SecretRotationScheduler (automated rotation)
 *
 * K-14 stories: K14-001 through K14-006.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Pluggable secret provider with local AES-256-GCM and Vault KV support"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform ────────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // ─── ActiveJ async runtime ────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Cryptography ─────────────────────────────────────────────────────────
    // AES-256-GCM and Argon2id key derivation
    implementation(libs.bouncycastle.provider)

    // ─── Logging / metrics ────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
