plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

description = "Platform YAML Template Engine — variable substitution and inheritance for YAML config files"

dependencies {
    // YAML parsing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    // Logging
    implementation(libs.slf4j.api)

    // Annotations
    compileOnly(libs.jetbrains.annotations)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
