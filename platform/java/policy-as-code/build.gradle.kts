/*
 * Build file for :platform:java:policy-as-code
 *
 * Provides a pluggable policy evaluation engine with OPA (Open Policy Agent) adapter,
 * risk scoring, and immutable policy result records.
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
    api(project(":platform:java:governance"))
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
