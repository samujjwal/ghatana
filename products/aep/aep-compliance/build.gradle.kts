/*
 * Build file for :products:aep:aep-compliance
 *
 * AEP compliance layer: orchestrates consent enforcement, data retention,
 * deletion requests, and consent-change propagation to downstream AEP components.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.aep"
version = "2026.3.1-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    api(project(":platform:java:data-governance"))
    api(project(":products:aep:aep-engine"))
    api(libs.activej.promise)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
