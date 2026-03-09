plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

description = "Agent Resilience — Circuit breaker, retry, bulkhead, and health-monitoring decorators for TypedAgent"

dependencies {
    // Platform resilience primitives (CircuitBreaker, RetryPolicy, DeadLetterQueue)
    api(project(":platform:java:core"))

    // Agent framework (TypedAgent, AgentDescriptor, AgentResult, AgentConfig, AgentContext)
    api(project(":platform:java:agent-framework"))

    // Observability for health metrics
    api(project(":platform:java:observability"))

    // ActiveJ async
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)

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
