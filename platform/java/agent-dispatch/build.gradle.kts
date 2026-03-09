plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

description = "Agent Dispatch — Three-tier dispatcher (Tier-J/Tier-S/Tier-L) bridging catalog definitions to runtime execution"

dependencies {
    // Agent framework (TypedAgent, CatalogRegistry, CatalogAgentEntry, AgentResult, AgentConfig)
    api(project(":platform:java:agent-framework"))

    // Agent resilience (ResilientTypedAgent, AgentBulkhead)
    api(project(":platform:java:agent-resilience"))

    // Platform core (CircuitBreaker, RetryPolicy, DeadLetterQueue)
    api(project(":platform:java:core"))

    // AI integration (LLM provider access for Tier-L execution)
    implementation(project(":platform:java:ai-integration"))

    // Observability for dispatch metrics
    implementation(project(":platform:java:observability"))

    // ActiveJ async
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)

    // Jackson for YAML/JSON parsing of agent definitions
    implementation(libs.jackson.databind)
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
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
