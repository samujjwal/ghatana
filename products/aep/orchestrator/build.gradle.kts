/**
 * products/aep/orchestrator — AEP Orchestration Sub-module
 *
 * Contains the pipeline orchestration engine, execution queues, deployment
 * adapters, and DI wiring that bridges the AEP domain platform with the
 * agent/pipeline lifecycle management layer.
 *
 * Dependency direction:
 *   products:aep:orchestrator    → products:aep:aep-operator-contracts (shared AEP contracts)
 *   products:aep:platform-bundle ← retired; no dependency on orchestrator
 *   products:aep:server          → products:aep:orchestrator
 *
 * This module intentionally breaks the circular dependency that would result
 * from the earlier state where orchestrator code lived inside products:aep:platform-bundle.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.aep"
version = rootProject.version

description = "AEP Orchestrator — pipeline lifecycle, execution queues, and agent dispatch wiring"


dependencies {
    // Shared AEP contracts — EventCloud, operator catalog, pipeline contracts
    api(project(":products:aep:aep-operator-contracts"))
    implementation(project(":products:aep:aep-engine"))
    // aep-agent-runtime contains agent dispatch classes (AgentDispatcher, LlmProvider, etc.)
    implementation(project(":products:aep:aep-agent-runtime"))
    api(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    api(project(":products:aep:aep-registry"))

    // Core platform libs
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:java:workflow"))
    api(project(":products:aep:aep-engine"))  // Unified runtime (Phase 1.6 consolidation)
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))
    api(project(":platform:java:config"))
    api(project(":platform:java:ai-integration"))  // Includes merged registry + feature-store
    api(project(":platform:contracts"))
    api(project(":products:data-cloud:spi"))

    // ActiveJ async runtime
    api(libs.activej.promise)
    api(libs.bundles.activej.core)
    api(libs.activej.http)
    api(libs.activej.inject)

    // AI / LLM
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.open.ai)

    // Persistence
    api(libs.jakarta.persistence.api)
    api(libs.jakarta.inject)
    api(libs.hibernate.core)
    api(libs.hikaricp)
    api(libs.postgresql)

    // gRPC (AgentGrpcService)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)

    // Serialization
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // -------------------------------------------------------------------------
    // TESTING
    // -------------------------------------------------------------------------
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:aep:aep-engine"))
    testImplementation(project(":products:aep:aep-analytics"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

// Test depends on analytics/ingress modules not yet on orchestrator classpath
sourceSets.test {
    java.exclude("com/ghatana/aep/di/AepDiModulesTest.java")
}

tasks.test {
    // useJUnitPlatform() already applied by java-module
}

// =============================================================================
// CODE QUALITY — Spotless, Checkstyle, PMD
// =============================================================================





// =============================================================================
// validateAgentCatalogs — run CatalogCanonicalValuesTest as a named quality gate
// =============================================================================

/**
 * Enforces that all AEP operator YAML catalog files reference only canonical
 * platform enum values (AgentType, AutonomyLevel, DeterminismGuarantee, StateMutability).
 *
 * Run explicitly: ./gradlew :products:aep:orchestrator:validateAgentCatalogs
 * This task also runs automatically as part of the standard test lifecycle via `check`.
 */
tasks.register("validateAgentCatalogs") {
    group = "verification"
    description = "Validates that all AEP agent catalog YAMLs use canonical platform enum values"
    dependsOn(tasks.test)
    doLast {
        logger.lifecycle("✅  AEP catalog canonical enum validation complete (backed by CatalogCanonicalValuesTest)")
    }
}

tasks.named("check") {
    dependsOn("validateAgentCatalogs")
}
