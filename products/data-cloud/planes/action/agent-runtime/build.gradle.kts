/*
 * AEP Agent Runtime Module
 *
 * Owns the advanced agent execution runtime: memory system, dispatch, learning,
 * resilience, assurance, and audit subsystems. Products that need advanced agent
 * orchestration must depend on this module. Products that only need agent
 * contracts/SPIs depend only on platform:java:agent-core.
 *
 * Relocated from platform:java:agent-runtime (2026-03-25 Sprint 4).
 * Rationale: advanced agent runtime is AEP-owned, not a neutral platform concern.
 *
 * @doc.type module
 * @doc.purpose AEP-owned advanced agent runtime (memory, dispatch, learning, resilience)
 * @doc.layer product
 * @doc.pattern Runtime, Framework
 */
plugins {
    id("java-module")
}

group = "com.ghatana.aep"
version = rootProject.version.toString()

description = """
    AEP Agent Runtime — Advanced agent execution runtime consolidating memory,
    learning, dispatch, and resilience subsystems.

    Relocated from platform:java:agent-runtime (Sprint 4, 2026-03-25).
    AEP owns advanced agent orchestration; platform:java:agent-core owns contracts only.

    Packages:
      com.ghatana.agent.memory.*      — Multi-level memory plane with retrieval, persistence, and observability
      com.ghatana.agent.learning.*    — Evaluation gates, consolidation, retention, and skill management
      com.ghatana.agent.dispatch.*    — Three-tier dispatcher (Tier-J/Tier-S/Tier-L) bridging catalog to runtime
      com.ghatana.agent.resilience.*  — Circuit breaker, retry, bulkhead, and health-monitoring decorators
      com.ghatana.agent.assurance.*   — Evaluation packs, promotion gates
      com.ghatana.agent.audit.*       — Trace ledger, hash-chained event appender
      com.ghatana.agent.runtime.*     — Safety invariants, governed dispatcher
""".trimIndent()

dependencies {
    // ── Agent contracts (SPI, TypedAgent, AgentConfig, AgentDescriptor, etc.) ─
    api(project(":platform:java:agent-core"))

    // ── Core platform dependencies ─────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":products:data-cloud:planes:shared-spi"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:database"))
    api(project(":platform:contracts"))
    api(project(":platform:java:tool-runtime"))
    api(project(":platform:java:data-governance"))

    // ── ActiveJ ────────────────────────────────────────────────────────────────
    api(libs.activej.promise)
    api(libs.bundles.activej.core)
    api(libs.activej.common)

    // ── Jackson ────────────────────────────────────────────────────────────────
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)

    // ── Logging ────────────────────────────────────────────────────────────────
    api(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // ── Annotations ────────────────────────────────────────────────────────────
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // ── Testing ────────────────────────────────────────────────────────────────
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.hikaricp)

    // ── JMH Benchmarks ─────────────────────────────────────────────────────────
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
