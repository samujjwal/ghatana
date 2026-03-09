plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "1.0.0-SNAPSHOT"

description = "Data-Cloud SPI - Shared interfaces and types for cross-product integration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Minimal dependencies - only what SPI types need
    api(project(":platform:java:core"))       // Offset type
    api(libs.activej.promise)                  // Promise<T> in EventLogStore
    
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
