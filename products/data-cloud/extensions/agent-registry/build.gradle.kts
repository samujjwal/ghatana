plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud-backed Agent Registry — persists agent descriptors and capability indices to Data-Cloud"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Agent Registry SPI (interface we implement)

    // Agent Framework (AgentDescriptor, TypedAgent, AgentConfig, AgentType …)
    api(project(":platform:java:agent-core"))

    // Data-Cloud (persistence layer)
    api(project(":products:data-cloud:delivery:runtime-composition"))

    // ActiveJ async primitives
    api(libs.activej.promise)
    api(libs.bundles.activej.core)

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

    testImplementation(libs.bundles.testing.core)
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
