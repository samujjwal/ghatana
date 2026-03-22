plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "2026.3.1-SNAPSHOT"

description = """
    Agent Runtime — Unified runtime module consolidating memory, learning, dispatch, and resilience.
    Replaces: agent-memory, agent-learning, agent-dispatch, agent-resilience (all merged here).

    Packages:
      com.ghatana.agent.memory.*      — Multi-level memory plane with retrieval, persistence, and observability
      com.ghatana.agent.learning.*    — Evaluation gates, consolidation, retention, and skill management
      com.ghatana.agent.dispatch.*    — Three-tier dispatcher (Tier-J/Tier-S/Tier-L) bridging catalog to runtime
      com.ghatana.agent.resilience.*  — Circuit breaker, retry, bulkhead, and health-monitoring decorators
""".trimIndent()

dependencies {
    // Agent Framework (TypedAgent, CatalogRegistry, AgentResult, AgentConfig)
    api(project(":platform:java:agent-core"))

    // Core Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:event-cloud"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:database"))
    api(project(":platform:contracts"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)

    // Jackson for JSON/YAML serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)

    // Logging
    api(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Annotations
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))

    // JMH Benchmarks
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
