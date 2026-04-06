/**
 * Market Data Domain Module
 *
 * Finance-specific domain module for real-time and historical market data,
 * including price feeds, market depth, and market data analytics.
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Market Data Domain - price feeds, order books, analytics"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))

    // Platform Libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))

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
    implementation(libs.flyway.database.postgresql)
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
