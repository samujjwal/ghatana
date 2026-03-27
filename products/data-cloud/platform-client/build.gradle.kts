/**
 * Platform Client Module
 *
 * Provides the DataCloudClient SDK for programmatic access to data-cloud
 * platform capabilities. This module is intentionally kept lean:
 * - No framework startup code
 * - No heavy infrastructure dependencies
 * - Suitable for embedding in external services
 *
 * Phase 1 of FINDING-DC-H2 (platform-launcher split).
 * In this phase the module re-exports from platform-launcher;
 * Phase 2 will move sources here.
 */
plugins {
    id("java-library")
    id("jacoco")
    alias(libs.plugins.spotbugs)
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Client SDK — extracted from platform-launcher (Phase 1)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Phase 1: thin API wrapper delegating to platform-launcher.
    // Phase 2: client/ packages will be physically moved here.
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-config"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.micrometer.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.300".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    toolVersion.set("4.8.6")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}
