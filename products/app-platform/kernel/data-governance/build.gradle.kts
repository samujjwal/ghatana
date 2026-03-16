/**
 * data-governance — Finance Data Governance kernel module (K-08)
 *
 * Provides:
 *   - Data catalog registry and classification (K08-001 to K08-003)
 *   - Data lineage tracking and graph visualisation (K08-004 to K08-005)
 *   - PII tagging, masking, and GDPR right-to-erasure workflow (K08-006 to K08-009)
 *   - Data sovereignty zone enforcement and trans-border flow control (K08-010)
 *   - Data quality alerting and master data governance (K08-011 to K08-012)
 *   - Retention enforcement scheduling (K08-013)
 *   - Third-party data sharing consent management (K08-014)
 *
 * Reuse: 95% of :products:data-cloud:platform per finance-ghatana-integration-plan.md §5.1
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-08: Data Governance — catalog, lineage, PII masking, GDPR erasure, data sovereignty"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Data Cloud (95% reuse) ───────────────────────────────────────────────
    api(project(":products:data-cloud:platform"))
    api(project(":products:data-cloud:spi"))

    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:governance"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:config-engine"))

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
