/**
 * ai-governance — Finance AI/ML Governance kernel module (K-09)
 *
 * Provides:
 *   - Model registry and lifecycle management (K09-001 to K09-005)
 *   - SHAP/LIME explainability engine (K09-006)
 *   - Concept drift and feature drift detection (K09-007 to K09-008)
 *   - Bias monitoring and fairness metrics (K09-009)
 *   - HITL review workflow integration (K09-010)
 *   - Model risk classification (TIER_1/2/3) and retraining pipeline (K09-011 to K09-015)
 *   - Prediction audit log backed by K-07 audit framework (K09-016)
 *
 * Reuse: 80% of :platform:java:ai-integration per finance-ghatana-integration-plan.md §5.1
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-09: AI/ML Governance kernel — model registry, explainability, drift detection, bias monitoring"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform AI integration (80% reuse) ─────────────────────────────────
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:agent-framework"))
    api(project(":platform:java:agent-learning"))

    // ─── Platform core ────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    // ─── Kernel dependencies ──────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:event-store"))
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
