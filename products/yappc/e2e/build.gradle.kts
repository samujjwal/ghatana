/*
 * YAPPC E2E Workflow Tests Module
 *
 * @doc.type module
 * @doc.purpose Executable end-to-end workflow proof tests for YAPPC lifecycle paths
 * @doc.layer product
 * @doc.pattern Test
 */
plugins {
    id("java-module")
}

description = "YAPPC E2E workflow tests - validates lifecycle provenance and Kernel promotion proof"

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

