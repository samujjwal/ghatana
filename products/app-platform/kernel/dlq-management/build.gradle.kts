/**
 * dlq-management — Finance Dead-Letter Queue Management kernel module (K-19)
 *
 * Provides:
 *   - Dead-letter capture, routing, and payload inspection (K19-001 to K19-003)
 *   - ML-based failure classification and AI recommendations (K19-004 to K19-005)
 *   - Bulk replay scheduling with SLA tracking (K19-006 to K19-008)
 *   - Poison pill detection (K19-009)
 *   - Payload transformation for replay compatibility (K19-010)
 *   - DLQ operations dashboard and metrics (K19-011 to K19-013)
 *   - Archive retention enforcement (K19-014)
 *   - Scheduled auto-retry with exponential backoff (K19-015)
 *
 * Reuse: 90% of :products:aep:platform dead-letter handling per finance-ghatana-integration-plan.md §5.1
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-19: DLQ management — dead-letter capture, ML classification, bulk replay, SLA tracking"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── AEP Platform (90% reuse — dead-letter handling + replay) ────────────
    api(project(":products:aep:platform"))

    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:connectors"))
    api(project(":platform:java:ai-integration"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:config-engine"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Kafka (DLQ topic management) ────────────────────────────────────────
    implementation(libs.kafka.clients)

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
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
