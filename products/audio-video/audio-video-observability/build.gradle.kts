/**
 * Audio-Video Observability Module
 *
 * Provides gRPC health checking, distributed tracing instrumentation,
 * and structured observability for Audio-Video microservices.
 */
plugins {
    id("java-module")
}

description = "Audio-Video Observability — health, tracing, and instrumentation"

dependencies {
    implementation(project(":platform:java:observability"))

    // gRPC health protocol
    implementation("io.grpc:grpc-services:1.79.0")
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.slf4j.api)
    implementation(libs.opentelemetry.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}



