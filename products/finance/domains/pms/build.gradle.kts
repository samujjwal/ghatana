/**
 * PMS Domain Module - Portfolio Management System
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "PMS Domain - Portfolio Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":products:finance:domains:oms"))
    api(libs.activej.promise)

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:pricing"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.activej.eventloop)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
