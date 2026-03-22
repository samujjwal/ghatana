/**
 * kernel-capabilities - Consolidated Kernel Module.
 *
 * @doc.type class
 * @doc.purpose Kernel capabilities - consolidated auth, config, event-store, audit, resilience, observability, secrets
 * @doc.layer platform
 * @doc.pattern Service
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "2026.3.1-SNAPSHOT"
description = "Kernel Capabilities - merged platform kernel sub-modules"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":platform:java:kernel"))
    api(project(":platform:java:security"))
    api(project(":platform:java:config"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:audit"))
    api(project(":platform:java:event-cloud"))
    api(project(":platform:java:core"))
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.bulkhead)
    implementation(libs.resilience4j.timelimiter)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
