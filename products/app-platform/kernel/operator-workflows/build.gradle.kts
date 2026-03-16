/**
 * operator-workflows — Finance Operator Workflows kernel module (O-01, Sprint 15)
 *
 * Provides:
 *   - Tenant registration, provisioning, and resource monitoring (OPR-001 to OPR-004)
 *   - Jurisdiction registry and cross-jurisdiction reporting (OPR-005 to OPR-006)
 *   - Feature rollout management and license feature gating (OPR-007 to OPR-008)
 *   - Maintenance window scheduling (OPR-009)
 *   - Usage metering and capacity planning alerts (OPR-010 to OPR-011)
 *   - Natural language platform query interface (OPR-012)
 *   - Tenant config isolation enforcement (OPR-013)
 *
 * LLD: O-01 created Sprint 15, Week 1 per §4.3.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "O-01: Operator workflows — tenant management, jurisdiction registry, feature rollout, usage metering"

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
    api(project(":platform:java:ai-integration"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:event-store"))

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
