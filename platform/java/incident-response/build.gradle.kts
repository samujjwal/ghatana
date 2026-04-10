/*
 * Build file for :platform:java:incident-response
 *
 * Provides incident taxonomy, kill-switch controls, graceful-degradation management,
 * and playbook abstractions for agent security incidents.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = rootProject.version


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
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
