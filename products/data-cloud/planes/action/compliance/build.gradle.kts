/*
 * Build file for :products:data-cloud:planes:action:compliance
 *
 * AEP compliance layer: orchestrates consent enforcement, data retention,
 * deletion requests, and consent-change propagation to downstream AEP components.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.aep"
version = rootProject.version


dependencies {
    api(project(":platform:java:data-governance"))
    api(project(":products:data-cloud:planes:action:engine"))
    api(libs.activej.promise)
    implementation(libs.slf4j.api)
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.containers)
    testRuntimeOnly(libs.junit.platform.launcher)
}
