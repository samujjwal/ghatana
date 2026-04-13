/**
 * Market Data Core Module
 *
 * Finance-specific core contracts for market data,
 * including domain records, ports, and feed SPI.
 */
plugins {
    id("finance-domain-module")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Market Data Core - domain contracts, ports, and feed SPI"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(libs.activej.promise)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
