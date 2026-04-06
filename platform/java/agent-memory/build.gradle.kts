plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = rootProject.version

description = "Agent Memory - Memory models and types shared across agent modules"

dependencies {
    // ActiveJ for async operations
    api(libs.activej.promise)
    
    // Core Platform
    api(project(":platform:java:core"))
    
    // Jackson for serialization
    implementation(libs.jackson.databind)
    
    // Annotations
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
