/**
 * Platform Domain Module
 *
 * Domain models shared across platform modules.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Domain - Shared domain models"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform Core
    api(project(":platform:java:core"))
    implementation(project(":platform:contracts"))

    // Jackson for JSON serialization
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.dataformat.yaml)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
