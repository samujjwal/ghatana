/**
 * deployment-abstraction — Finance Deployment Abstraction kernel module (K-10)
 *
 * Provides:
 *   - Blue-green and canary deployment orchestration (K10-001 to K10-003)
 *   - Environment registry with IaC drift scanning (K10-004 to K10-005)
 *   - Kubernetes HPA config management (K10-006)
 *   - Instant rollback workflow (K10-007)
 *   - Resource quota management (K10-008)
 *   - Configuration drift scanning (K10-009)
 *
 * Reuse: 50% of :platform:java:runtime + Kubernetes HPA per finance-ghatana-integration-plan.md §5.1
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-10: Deployment abstraction — blue-green, canary, rollback, HPA, environment registry"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform runtime (ActiveJ lifecycle management) ──────────────────────
    api(project(":platform:java:runtime"))
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:event-store"))

    // ─── ActiveJ async ────────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Kubernetes client (HPA, deployment status) ───────────────────────────
    implementation(libs.kubernetes.client)

    // ─── HTTP client (IaC validation, environment registry calls) ────────────
    implementation(libs.okhttp)

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
