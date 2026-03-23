/**
 * products/aep/orchestrator — AEP Orchestration Sub-module
 *
 * Contains the pipeline orchestration engine, execution queues, deployment
 * adapters, and DI wiring that bridges the AEP domain platform with the
 * agent/pipeline lifecycle management layer.
 *
 * Dependency direction:
 *   products:aep:orchestrator    → products:aep:platform-contracts (shared AEP contracts)
 *   products:aep:platform-bundle ← no dependency on orchestrator
 *   products:aep:server          → products:aep:orchestrator
 *
 * This module intentionally breaks the circular dependency that would result
 * from keeping orchestrator code inside products:aep:platform-bundle.
 */
plugins {
    id("java-library")
    id("com.diffplug.spotless")
    checkstyle
    pmd
}

group = "com.ghatana.aep"
version = "2026.3.1-SNAPSHOT"

description = "AEP Orchestrator — pipeline lifecycle, execution queues, and agent dispatch wiring"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Shared AEP contracts — EventCloud, operator catalog, pipeline contracts
    api(project(":products:aep:platform-contracts"))
    implementation(project(":products:aep:platform-engine"))
    api(project(":products:aep:platform-agent"))
    api(project(":products:aep:platform-connectors"))
    api(project(":products:aep:platform-registry"))

    // Core platform libs
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:java:agent-runtime"))  // Merged: agent-dispatch + agent-resilience
    api(project(":platform:java:agent-registry"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))
    api(project(":platform:java:config"))
    api(project(":platform:java:ai-integration"))  // Includes merged registry + feature-store
    api(project(":platform:contracts"))
    api(project(":products:data-cloud:spi"))

    // ActiveJ async runtime
    api(libs.activej.promise)
    api(libs.activej.eventloop)
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
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:aep:platform-engine"))
    testImplementation(project(":products:aep:platform-analytics"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.core)
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
    useJUnitPlatform()
}

// =============================================================================
// CODE QUALITY — Spotless, Checkstyle, PMD
// =============================================================================

spotless {
    java {
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
    }
}

checkstyle {
    toolVersion = "10.12.5"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

pmd {
    toolVersion = "6.55.0"
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    isIgnoreFailures = true
}

tasks.named("checkstyleMain") { dependsOn("spotlessJavaCheck") }
tasks.named("pmdMain") { dependsOn("checkstyleMain") }
