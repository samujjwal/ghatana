/**
 * incident-management — Finance Incident Management kernel module (M4 Sprint 17-30)
 *
 * Provides:
 *   - AI-powered incident detection and root-cause analysis (INC-001 to INC-003)
 *   - Automated incident playbook execution (INC-004)
 *   - Status page updates and stakeholder communication (INC-005)
 *   - MTTR metrics and reliability reporting (INC-006 to INC-007)
 *   - Post-incident review and action-item tracking (INC-008 to INC-009)
 *   - Game-day chaos scenario management (INC-010)
 *   - ML-based incident pattern detection (INC-011)
 *
 * Scope: M4 GA hardening phase.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Incident management: AI root-cause analysis, automated playbooks, MTTR tracking, chaos management"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:ai-integration"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:observability-sdk"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── HTTP client (status page, PagerDuty, Slack webhooks) ────────────────
    implementation(libs.okhttp)

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

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
