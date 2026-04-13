/*
 * YAPPC Example Plugin — Maven POM Generator
 *
 * This module is intentionally minimal and serves as the canonical example for
 * building YAPPC plugins. It depends only on the current consolidated plugin
 * API surface in core:yappc-infrastructure and has no runtime dependencies of its own.
 */
plugins {
    id("java-module")
}

description = "YAPPC Example Plugin: Maven POM Generator (demonstrates plugin SDK)"

dependencies {
    // Plugin API surface only — see docs/plugin-sdk/PLUGIN_DEVELOPMENT_GUIDE.md
    compileOnly(project(":core:yappc-infrastructure"))

    // Test: run in real IsolatingPluginSandbox
    testImplementation(project(":core:yappc-infrastructure"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

