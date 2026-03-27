/**
 * Platform Audio-Video Library
 *
 * Provides STT (Speech-to-Text), TTS (Text-to-Speech), and Vision engine APIs
 * plus ONNX-based engine implementations, all under {@code com.ghatana.media.*}.
 *
 * Products depending on speech or vision capabilities MUST use this module
 * instead of rolling their own implementations.
 *
 * @doc.type module
 * @doc.purpose Platform library for audio-video processing (STT, TTS, Vision)
 * @doc.layer platform
 * @doc.pattern Library
 */
plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = "2026.3.1-SNAPSHOT"

description = "Platform Audio-Video Library — STT, TTS, and Vision engine APIs and ONNX implementations"

// The JMH benchmark sources are in src/jmh — exclude them from normal compilation
// Run benchmarks explicitly with ./gradlew jmh after enabling the jmh plugin if needed.
// The gRPC/HTTP adapters under service/ reference product-level proto packages that live
// in separate modules rather than this platform library; exclude them from the library JAR.
sourceSets {
    main {
        java {
            // Adapters are product-specific gRPC/HTTP glue — NOT part of this platform library
            exclude("**/service/**")
            // Usage examples are standalone demos, not part of the public API
            exclude("**/examples/**")
        }
    }
}

dependencies {
    // ActiveJ async primitives — exposed as API so callers can chain Promises
    api(libs.activej.promise)
    api(libs.activej.http)

    // Platform core — BaseException hierarchy, CircuitBreaker, resilience utilities
    implementation(project(":platform:java:core"))

    // ONNX Runtime for embedded model inference
    api(libs.onnxruntime)

    // Native library loader (for OpenCV JNI, Whisper.cpp etc.)
    implementation(libs.native.lib.loader)

    // Jackson for JSON serialization within engine adapters
    implementation(libs.jackson.databind)

    // SLF4J for engine logging
    implementation(libs.slf4j.api)

    // Micrometer — compile-only, callers bring their own registry
    compileOnly(libs.micrometer.core)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
