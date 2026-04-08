/**
 * Platform Kernel Testing Module
 *
 * @doc.type build-script
 * @doc.purpose Test infrastructure for kernel modules
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel Testing - test utilities and base classes"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel Core
    api(project(":platform-kernel:kernel-core"))

    // ActiveJ test support
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // JUnit
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.engine)

    // Assertions
    api(libs.assertj.core)

    // Mocking
    api(libs.mockito.core)
    api(libs.mockito.junit.jupiter)

    // Logging
    api(libs.slf4j.api)

    // Test runtime
    runtimeOnly(libs.junit.platform.launcher)
}
