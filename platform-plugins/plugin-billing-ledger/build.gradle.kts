/**
 * Billing Ledger Plugin
 *
 * @doc.type build-script
 * @doc.purpose Double-entry ledger plugin for cross-product billing
 * @doc.layer platform
 */
plugins {
    id("java-module")
}

group = "com.ghatana.plugin"
version = rootProject.version
description = "Billing Ledger Plugin - double-entry ledger system"


dependencies {
    // Kernel and Platform libraries via BOMs
    implementation(platform(project(":platform-kernel:kernel-bom")))
    implementation(platform(project(":platform:java:platform-bom")))

    // Kernel modules
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    // Plugin-specific dependencies
    api(libs.activej.promise)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}
