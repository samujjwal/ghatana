plugins {
    id("java-library")
}

group = "com.ghatana.audio.video"
version = rootProject.version

description = "Audio-Video Common Library - Shared security, observability, and resilience utilities"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform audio-video library — media error types (ProcessingError hierarchy)
    api(project(":platform:java:audio-video"))

    // Tool handler SPI — ToolHandler, ToolExecutionEnvelope, ToolExecutionResult
    api(project(":platform:java:tool-runtime"))

    // Agent framework — ToolContract, ActionClass
    api(project(":platform:java:agent-core"))

    // gRPC interceptor API
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:core"))

    // JWT validation
    implementation(libs.nimbus.jose.jwt)

    // Jackson for type-safe JSON serialisation in platform clients
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
