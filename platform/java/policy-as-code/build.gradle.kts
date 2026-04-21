/*
 * Build file for :platform:java:policy-as-code
 *
 * Provides a pluggable policy evaluation engine with OPA (Open Policy Agent) adapter,
 * risk scoring, and immutable policy result records.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:governance"))
    api(libs.activej.promise)
    implementation(libs.slf4j.api)
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.containers)
    testRuntimeOnly(libs.junit.platform.launcher)
}
