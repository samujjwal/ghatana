plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

description = "AI Experimental - Product-specific and experimental AI implementations"

dependencies {
    // AI API (stable interfaces)
    api(project(":platform:java:ai-api"))

    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // Core Platform
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    // HTTP client for external AI services
    implementation(libs.activej.http)

    // OpenAI client
    implementation("com.openai:openai-java:0.25.0")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
