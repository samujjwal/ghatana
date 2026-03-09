/**
 * Test Utils Module
 *
 * Provides common testing utilities and base classes.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Test Utils - Common testing utilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform Core
    api(project(":platform:java:core"))
    api(project(":platform:java:testing"))

    // JUnit 5
    api("org.junit.jupiter:junit-jupiter:5.10.0")
    api("org.junit.jupiter:junit-jupiter-api:5.10.0")
    api("org.junit.jupiter:junit-jupiter-params:5.10.0")

    // AssertJ
    api("org.assertj:assertj-core:3.24.2")

    // Mockito
    api("org.mockito:mockito-core:5.5.0")
    api("org.mockito:mockito-junit-jupiter:5.5.0")

    // Test Containers
    api("org.testcontainers:testcontainers:1.19.1")
    api("org.testcontainers:junit-jupiter:1.19.1")
    api("org.testcontainers:postgresql:1.19.1")

    // Awaitility
    api("org.awaitility:awaitility:4.2.0")

    // Jackson
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)

    // JSON Path
    api("com.jayway.jsonpath:json-path:2.9.0")

    // Runtime
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
