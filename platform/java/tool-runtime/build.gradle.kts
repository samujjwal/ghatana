/*
 * Build file for :platform:java:tool-runtime
 *
 * Provides sandboxed execution, resource monitoring, and human-approval workflows
 * for agent tool invocations.
 */
plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:java:policy-as-code"))
    api(project(":platform:java:observability"))
    api(libs.activej.promise)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("com.github.tomakehurst:wiremock-jre8:3.0.1")
    testRuntimeOnly(libs.junit.platform.launcher)
}
