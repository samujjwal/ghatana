/**
 * client-onboarding — Finance client onboarding kernel module (K-W02 / Sprint 13)
 *
 * Provides:
 *   - KYC workflow orchestration with document AI verification
 *   - Periodic KYC review scheduling and risk-tier assignment
 *   - Video KYC session management
 *   - World-Check / sanctions integration port
 *   - Regulatory notification on onboarding events
 *   - Document request reminders and onboarding status tracking
 *
 * LLD: W-02 (created Sprint 13, Week 1 per §4.3)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Client onboarding: KYC workflow, document AI verification, video KYC, World-Check integration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:governance"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:workflow-orchestration"))
    implementation(project(":products:app-platform:kernel:calendar-service"))

    // ─── AEP (event processing layer) ────────────────────────────────────────
    implementation(project(":products:aep:platform"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── HTTP client (document AI, World-Check REST) ──────────────────────────
    implementation(libs.okhttp)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
