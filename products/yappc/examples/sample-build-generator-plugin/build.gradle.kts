/*
 * YAPPC Example Plugin — Maven POM Generator
 *
 * This module is intentionally minimal and serves as the canonical example for
 * building YAPPC plugins. It depends only on the public plugin API surface
 * (core:framework) and has no runtime dependencies of its own.
 */
plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

description = "YAPPC Example Plugin: Maven POM Generator (demonstrates plugin SDK)"

dependencies {
    // Plugin API surface only — see docs/plugin-sdk/PLUGIN_DEVELOPMENT_GUIDE.md
    compileOnly(project(":core:framework"))

    // Test: run in real IsolatingPluginSandbox
    testImplementation(project(":core:framework"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
