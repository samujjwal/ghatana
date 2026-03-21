plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "2026.3.1-SNAPSHOT"

description = "Agent API - Public contract interfaces and types for the Ghatana agent system"

dependencies {
    // ActiveJ Promise for async contracts
    api(libs.activej.promise)

    // Core platform types (TenantId, etc.)
    api(project(":platform:java:core"))

    // Lombok for value types
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
