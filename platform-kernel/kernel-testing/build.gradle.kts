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
version = rootProject.version
description = "Platform Kernel Testing - test utilities and base classes"


dependencies {
    // Kernel Core
    api(project(":platform-kernel:kernel-core"))

    // ActiveJ test support
    api(libs.bundles.activej.core)

    // JUnit
    api(libs.bundles.testing.core)

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

// Fix JaCoCo task dependency
tasks.named("jacocoTestReport") {
    dependsOn("compileJava")
}
