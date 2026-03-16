plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.datacloud"
version = "1.0.0-SNAPSHOT"

description = "Data-Cloud-backed Agent Registry — persists agent descriptors and capability indices to Data-Cloud"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Agent Registry SPI (interface we implement)
    api(project(":platform:java:agent-registry"))

    // Agent Framework (AgentDescriptor, TypedAgent, AgentConfig, AgentType …)
    api(project(":platform:java:agent-framework"))

    // Data-Cloud (persistence layer)
    api(project(":products:data-cloud:platform"))

    // ActiveJ async primitives
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // Logging
    api(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Annotations
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
