/**
 * platform-sdk — Finance Platform SDK aggregation module (K-12, Sprint 13)
 *
 * Aggregates all platform:java:* and kernel artifacts into a single developer-facing
 * SDK entry point. Provides:
 *   - OpenAPI code generation tooling (SDK-001 to SDK-003)
 *   - Event schema code generation (SDK-004)
 *   - Contract test harness (PACT) (SDK-005 to SDK-007)
 *   - Platform SDK core abstractions and package registry (SDK-008 to SDK-009)
 *   - Finance domain scaffold generator (SDK-010)
 *   - Developer portal and local dev environment setup (SDK-011 to SDK-012)
 *   - Test harness SDK (SDK-013)
 *   - Sandbox environment provisioning (SDK-014)
 *   - Auto-documentation generation (SDK-015)
 *
 * Reuse: 100% composition of platform artifacts per finance-ghatana-integration-plan.md §5.1
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "K-12: Platform SDK — OpenAPI codegen, event schema codegen, PACT harness, developer portal"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform SDK composition (100% aggregation) ──────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    api(project(":platform:java:security"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:observability-http"))
    api(project(":platform:java:connectors"))
    api(project(":platform:java:schema-registry"))
    api(project(":platform:java:plugin"))
    api(project(":platform:java:agent-framework"))
    api(project(":platform:java:agent-resilience"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:runtime"))
    api(project(":platform:java:testing"))

    // ─── AEP and Data Cloud SPI ───────────────────────────────────────────────
    api(project(":products:aep:platform"))
    api(project(":products:data-cloud:spi"))

    // ─── Kernel aggregation ───────────────────────────────────────────────────
    api(project(":products:app-platform:kernel:event-store"))
    api(project(":products:app-platform:kernel:audit-trail"))
    api(project(":products:app-platform:kernel:iam"))
    api(project(":products:app-platform:kernel:config-engine"))
    api(project(":products:app-platform:kernel:calendar-service"))
    api(project(":products:app-platform:kernel:ledger-framework"))
    api(project(":products:app-platform:kernel:rules-engine"))
    api(project(":products:app-platform:kernel:plugin-runtime"))
    api(project(":products:app-platform:kernel:resilience-patterns"))
    api(project(":products:app-platform:kernel:observability-sdk"))
    api(project(":products:app-platform:kernel:api-gateway"))
    api(project(":products:app-platform:kernel:secrets-management"))

    // ─── ActiveJ ─────────────────────────────────────────────────────────────
    api(libs.activej.promise)

    // ─── OpenAPI code generation (Swagger / OpenAPI Generator) ───────────────
    implementation(libs.openapi.generator)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
