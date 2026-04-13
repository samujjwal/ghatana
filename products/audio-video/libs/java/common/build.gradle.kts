/*
 * Audio-Video Common Library Module - Build Configuration
 *
 * Migrated from platform:java:audio-video per Phase 3.1 of the audit.
 * Provides shared audio-video processing capabilities (STT, TTS, Vision).
 *
 * @doc.type module
 * @doc.purpose Shared audio-video processing utilities
 * @doc.layer product
 * @doc.pattern Library
 */

plugins {
    id("java-module")
}

group = "com.ghatana.audio-video"
version = rootProject.version

description = "Audio-Video Common Library - STT, TTS, and Vision engine APIs and ONNX implementations"

sourceSets {
    main {
        java {
            // Exclude service adapters and examples - they are product-specific
            exclude("**/service/**")
            exclude("**/examples/**")
        }
    }
}

dependencies {
    api(project(":platform:contracts"))
    api(project(":platform:java:domain"))

    // ONNX runtime for ML models
    implementation(libs.onnx.runtime)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}
