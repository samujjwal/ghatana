plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud API contract tests — OpenAPI drift detection, route completeness, and integration tests"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation(project(":products:data-cloud:platform-api"))
    testImplementation(project(":products:data-cloud:platform-launcher"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.dataformat.yaml)
}
