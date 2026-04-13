plugins {
    id("java-module")
    }

description = "Cross-domain integration tests for PHR and Finance workflows"

dependencies {
    testImplementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform-plugins:plugin-billing-ledger"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:phr"))
    testImplementation(project(":products:finance"))
    testImplementation(libs.bundles.testing.core)
}
