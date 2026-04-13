/**
 * Market Data Domain Module
 *
 * Finance-specific domain module for real-time and historical market data,
 * including price feeds, market depth, and market data analytics.
 */
plugins {
    id("finance-domain-module")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Market Data Domain - price feeds, order books, analytics"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))
    api(project(":products:finance:domains:market-data-core"))

    // Platform Libraries
    api(project(":platform:java:security"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.activej.http)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)

    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)
    implementation(libs.jedis)
    implementation(libs.kafka.clients)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
