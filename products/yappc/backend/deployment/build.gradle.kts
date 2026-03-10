/*
 * YAPPC Backend Deployment Module
 * Deployment orchestration, canary releases, and rollback support.
 */
plugins {
    id("java-library")
}

description = "YAPPC Backend - Deployment orchestration (Helm, Docker, canary)"

dependencies {
    // Internal
    api(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:backend:persistence"))

    // ActiveJ (HTTP includes Reactor, DI, Promise)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.promise)

    // Docker
    implementation("com.github.docker-java:docker-java-core:3.3.6")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.6")

    // JSON & YAML
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
