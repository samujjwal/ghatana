/**
 * Corporate Actions Domain Module
 *
 * Finance-specific domain module for corporate action processing,
 * including dividends, stock splits, mergers, and acquisitions.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Corporate Actions Domain - dividend, split, merger processing"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform:java:kernel"))
    api(project(":platform:java:kernel:modules:authentication"))
    api(project(":platform:java:kernel:modules:event-store"))
    api(project(":platform:java:kernel:modules:audit"))
    api(project(":platform:java:kernel:modules:resilience"))

    // Platform Libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Observability (for workflow metrics in CorporateActionWorkflowService)
    implementation(libs.micrometer.core)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:post-trade"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    api(project(":platform:java:governance"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(platform(libs.jackson.bom))
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
