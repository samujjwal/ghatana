/**
 * rules-engine — K-03 Policy evaluation via Open Policy Agent (OPA).
 *
 * Provides:
 *   - OPA REST API integration for policy evaluation (K03-001)
 *   - Redis-backed cache for rule evaluation results (K03-003)
 *   - Jurisdiction-aware policy routing by tenant (K03-009)
 *
 * Design:
 *   - Uses Java 11 HttpClient for HTTP (no extra HTTP libs)
 *   - Jackson Databind for JSON serialisation
 *   - Redis (Jedis) for result caching with configurable TTL
 *   - All I/O wrapped via Promise.ofBlocking for ActiveJ compatibility
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-03 Rules engine: OPA evaluation, Redis cache, jurisdiction routing"

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

    // ─── JSON (OPA request/response serialisation) ────────────────────────────
    implementation(libs.jackson.databind)

    // ─── Redis (result cache) ─────────────────────────────────────────────────
    implementation(libs.jedis)

    // ─── Logging ──────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
