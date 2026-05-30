/**
 * products/data-cloud/planes/action/orchestrator — Data Cloud Action Plane Orchestration Sub-module
 *
 * Contains the pipeline orchestration engine, execution queues, deployment
 * adapters, and DI wiring that bridges the Action Plane domain platform with the
 * agent/pipeline lifecycle management layer.
 *
 * Dependency direction:
 *   products:data-cloud:planes:action:orchestrator → products:data-cloud:planes:action:operator-contracts
 */
plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Action Plane Orchestrator — pipeline lifecycle, execution queues, and agent dispatch wiring"


dependencies {
    // Action Plane contracts — EventCloud, operator catalog, pipeline contracts
    api(project(":products:data-cloud:planes:action:operator-contracts"))
    implementation(project(":products:data-cloud:planes:action:engine"))
    // Agent runtime for dispatch classes (AgentDispatcher, LlmProvider, etc.)
    implementation(project(":products:data-cloud:planes:action:agent-runtime"))
    implementation(project(":products:data-cloud:planes:data:entity"))
    implementation(project(":products:data-cloud:extensions:agent-registry"))
    api(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    api(project(":products:data-cloud:planes:action:registry"))

    // Core platform libs
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:java:workflow"))
    api(project(":products:data-cloud:planes:action:engine"))  // Unified runtime
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))
    api(project(":platform:java:config"))
    api(project(":platform:java:ai-integration"))  // Includes merged registry + feature-store
    api(project(":platform:contracts"))
    api(project(":products:data-cloud:planes:shared-spi"))

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
    testImplementation(project(":products:data-cloud:planes:action:engine"))
    testImplementation(project(":products:data-cloud:planes:action:analytics"))
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

// Legacy tests below target removed connector/ingress wiring and outdated ActiveJ APIs.
// Keep them excluded from compilation until they are migrated to current module boundaries.
sourceSets.test {
    java.exclude("com/ghatana/aep/di/AepDiModulesTest.java")
    java.exclude("com/ghatana/aep/engine/registry/AgentMemoryPlaneClientMasteryTest.java")
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
 * Validates that all Action Plane operator YAML catalog files reference only canonical
 * platform enum values (AgentType, AutonomyLevel, DeterminismGuarantee, StateMutability).
 *
 * Run explicitly: ./gradlew :products:data-cloud:planes:action:orchestrator:validateAgentCatalogs
 * This task also runs automatically as part of the standard test lifecycle via `check`.
 */
tasks.register("validateAgentCatalogs") {
    group = "verification"
    description = "Validates that all Action Plane agent catalog YAMLs use canonical platform enum values"
    dependsOn(tasks.test)
    doLast {
        logger.lifecycle("✅  Action Plane catalog canonical enum validation complete (backed by CatalogCanonicalValuesTest)")
    }
}

tasks.named("check") {
    dependsOn("validateAgentCatalogs")
}
