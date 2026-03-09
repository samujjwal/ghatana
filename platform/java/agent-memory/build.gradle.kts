plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

description = "Agent Memory - Multi-level memory plane with retrieval, persistence, and observability"

dependencies {
    // Core Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:event-cloud"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:database"))
    api(project(":platform:contracts"))

    // Agent Framework (for backward-compat adapters: MemoryStore, Episode, Fact, Policy)
    api(project(":platform:java:agent-framework"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)

    // Jackson for JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
