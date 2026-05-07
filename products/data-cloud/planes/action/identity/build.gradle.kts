/*
 * Build file for :products:data-cloud:planes:action:identity
 *
 * AEP-specific agent identity resolution: wraps platform identity module with
 * AEP agent lifecycle hooks and provides adapters for external identity providers.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.aep"
version = rootProject.version


dependencies {
    api(project(":platform:java:identity"))
    api(project(":products:data-cloud:planes:action:engine"))
    api(libs.activej.promise)
    implementation(project(":platform:java:security"))
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
