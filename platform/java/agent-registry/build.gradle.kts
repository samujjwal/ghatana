plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

description = "Agent Registry SPI - Agent discovery and registration interfaces"

dependencies {
    // Agent Framework (SPI definitions)
    api(project(":platform:java:agent-framework"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // Core Platform
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

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
