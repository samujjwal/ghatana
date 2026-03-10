/*
 * YAPPC Backend Auth Module
 * Authentication, authorization, session management, and RBAC.
 */
plugins {
    id("java-library")
}

description = "YAPPC Backend - Authentication and authorization"

dependencies {
    // Internal
    api(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:backend:persistence"))
    implementation(project(":platform:java:security"))

    // ActiveJ (HTTP, DI, Promise)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.promise)

    // Security
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
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
