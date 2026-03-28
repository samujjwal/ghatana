/*
 * Build file for :platform:java:tool-runtime
 *
 * Provides sandboxed execution, resource monitoring, and human-approval workflows
 * for agent tool invocations.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = rootProject.version

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

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
