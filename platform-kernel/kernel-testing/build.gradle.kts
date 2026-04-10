plugins {
    id("java-module")
}

description = "Platform Kernel Testing - test utilities and base classes"

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(libs.bundles.activej.core)
    api(libs.bundles.testing.core)
    api(libs.assertj.core)
    api(libs.mockito.core)
    api(libs.mockito.junit.jupiter)
    api(libs.slf4j.api)
    runtimeOnly(libs.junit.platform.launcher)
}
