plugins {
    id("java-library")
}

description = "YAPPC Scaffold Generators - Language-specific code generators and pipeline builders"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    api(project(":products:yappc:core:scaffold:engine"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    api(project(":products:yappc:core:yappc-infrastructure"))

    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)

    // Code Transformation

    // ActiveJ
    api(libs.activej.inject)
    implementation(libs.activej.common)
    implementation(libs.activej.boot)
    implementation(libs.activej.promise)

    // Validation

    // CLI and utility
    implementation(libs.picocli)

    implementation(project(":platform:java:runtime"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}
