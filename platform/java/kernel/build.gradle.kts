/**
 * Platform Kernel Module
 *
 * Core kernel abstractions: KernelModule interface, KernelContext, KernelCapability,
 * plugin system, lifecycle management, and boundary enforcement. This module is
 * the foundational dependency for all kernel:modules:* submodules.
 *
 * @doc.type class
 * @doc.purpose Kernel platform core — module lifecycle and context abstractions
 * @doc.layer platform
 * @doc.pattern Service
 */
plugins {
    id("java-library")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel - core module abstractions, context, and lifecycle"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // ActiveJ (mandatory async framework)
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // JSON utilities
    api(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    api(libs.slf4j.api)

    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Testing
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

tasks.withType<Javadoc> {
    exclude("**/internal/**")
    options {
        encoding = "UTF-8"
        source = "21"
    }
}
