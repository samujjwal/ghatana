plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

/**
 * Scaffold Core Aggregator
 *
 * This module is intentionally empty of source files. It re-exports the three
 * focused scaffold sub-modules so consumers need only a single dependency.
 *
 * @doc.type class
 * @doc.purpose Aggregator re-exporting scaffold capability modules
 * @doc.layer product
 * @doc.pattern Aggregator
 */
dependencies {
    // Re-export all scaffold capability sub-modules
    api(project(":products:yappc:core:scaffold:templates"))
    api(project(":products:yappc:core:scaffold:engine"))
    api(project(":products:yappc:core:scaffold:generators"))

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
