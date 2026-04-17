plugins {
    id("java-module")
}

description = "Cross-product contract tests for AEP and YAPPC Data Cloud integrations"

dependencies {
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:aep:orchestrator"))
    testImplementation(project(":products:data-cloud:spi"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation(project(":products:yappc:libs:java:yappc-domain"))
    testImplementation(project(":platform:java:governance"))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}