/*
 * YAPPC Integration Tests Module
 *
 * @doc.type module
 * @doc.purpose End-to-end integration tests for YAPPC components
 * @doc.layer product
 * @doc.pattern Test
 */
plugins {
    id("java-module")
}

description = "YAPPC integration tests — validates cross-component wiring"

dependencies {
    // YAPPC modules under test
    testImplementation(project(":products:yappc:core:yappc-services"))
    testImplementation(project(":products:yappc:core:yappc-infrastructure"))
    testImplementation(project(":products:yappc:core:knowledge-graph"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))

    // Platform modules
    testImplementation(project(":platform:java:agent-core"))
    testImplementation(project(":platform:java:ai-integration"))
    testImplementation(project(":platform:java:testing"))

    // Test framework
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // ActiveJ
    testImplementation(libs.activej.promise)
}

tasks.test {
    useJUnitPlatform()
}
