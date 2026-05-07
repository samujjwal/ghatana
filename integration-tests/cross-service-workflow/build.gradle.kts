plugins {
    id("java-module")
}

description = "Cross-product contract tests for AEP and YAPPC Data Cloud integrations"

dependencies {
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:data-cloud:planes:action:orchestrator"))
    testImplementation(project(":products:data-cloud:planes:shared-spi"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation(project(":products:yappc:libs:java:yappc-domain"))
    testImplementation(project(":products:yappc:core:yappc-services"))
    testImplementation(project(":platform:java:governance"))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.micrometer.core)
}

tasks.test {
    useJUnitPlatform()
    // ArchUnit builds a full reverse-dependency graph over all ghatana product classes,
    // which requires more heap than the convention's 768m default.
    maxHeapSize = "1536m"
}