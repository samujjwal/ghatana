/**
 * Regulatory Reporting Domain Module
 *
 * Finance-specific domain module for regulatory report generation,
 * including MiFID II, EMIR, SFTR, and other regulatory reporting.
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Regulatory Reporting Domain - MiFID II, EMIR, SFTR reports"

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

    // Observability (for workflow metrics in RegulatoryReportSubmissionWorkflowService)
    implementation(libs.micrometer.core)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:reference-data"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    api(project(":platform:java:governance"))
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(platform(libs.jackson.bom))
    implementation(libs.pdfbox)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.h2)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
