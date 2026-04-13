/**
 * Risk Domain Module - Risk Management
 */
plugins {
    id("finance-domain-module")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Risk Domain - Risk Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":products:finance:domains:pms"))
    api(libs.activej.promise)

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:oms"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)
    implementation(libs.jedis)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
