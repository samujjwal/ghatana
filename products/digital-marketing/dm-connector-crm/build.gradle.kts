plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS CRM Connector — External CRM integrations (HubSpot, Salesforce, Pipedrive, Zoho)"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":platform:java:core"))

    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
