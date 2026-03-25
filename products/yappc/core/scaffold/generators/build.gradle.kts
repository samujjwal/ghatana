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
    implementation(libs.diffutils)

    // Code Transformation
    implementation(libs.openrewrite.core)
    implementation(libs.openrewrite.java)
    implementation(libs.openrewrite.gradle)

    // ActiveJ
    api(libs.activej.inject)
    implementation(libs.activej.common)
    implementation(libs.activej.boot)
    implementation(libs.activej.promise)

    // Validation
    api(libs.jakarta.validation.api)
    implementation(libs.hibernate.validator)

    // CLI and utility
    implementation(libs.picocli)
    implementation(libs.jgit)
    implementation(libs.joda.time)
    implementation(libs.commons.text)

    implementation(project(":platform:java:runtime"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

