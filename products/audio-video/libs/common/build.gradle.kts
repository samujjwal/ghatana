plugins {
    id("java-library")
}

group = "com.ghatana.audio.video"
version = "1.0.0-SNAPSHOT"

description = "Audio-Video Common Library - Shared security, observability, and resilience utilities"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // gRPC interceptor API
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)

    // JWT validation
    implementation(libs.nimbus.jose.jwt)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
