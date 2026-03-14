/**
 * api-gateway — Finance kernel HTTP entry-point (K-11)
 *
 * Provides:
 *   - RS256 JWT validation filter using kernel IAM SigningKeyProvider
 *   - Routing to ledger, IAM, and calendar sub-modules
 *   - GatewayServerBuilder that composes platform HttpServerBuilder with
 *     tenant extraction, JWT validation, and finance routes
 *
 * Depends on platform HTTP + governance only — no reimplementation of auth/routing.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Finance kernel API gateway: JWT validation, routing, tenant extraction"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform HTTP (RoutingServlet, HttpServerBuilder, FilterChain) ────────
    api(project(":platform:java:http"))

    // ─── Platform Governance (TenantExtractionFilter, TenantContext) ─────────
    implementation(project(":platform:java:governance"))

    // ─── Kernel IAM (SigningKeyProvider, JWT claims) ──────────────────────────
    implementation(project(":products:app-platform:kernel:iam"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.http)

    // ─── JWT validation ───────────────────────────────────────────────────────
    implementation(libs.nimbus.jose.jwt)

    // ─── Logging ─────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ─── Redis (rate limit store) ─────────────────────────────────────────────
    implementation(libs.jedis)

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
