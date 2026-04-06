/**
 * Reconciliation Domain Module
 *
 * Finance-specific domain module for trade and position reconciliation,
 * including matching, break management, and exception handling.
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Reconciliation Domain - trade/position matching, breaks"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))

    // Platform Libraries
    api(project(":platform:java:audit"))
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Observability (for workflow metrics in ReconciliationOrchestrationWorkflowService)
    implementation(libs.micrometer.core)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:post-trade"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.kafka.clients)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
