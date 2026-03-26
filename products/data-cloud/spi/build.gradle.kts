plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data-Cloud SPI - Shared interfaces and types for cross-product integration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Minimal dependencies - only what SPI types need
    api(project(":products:data-cloud:platform-entity"))
    api(project(":platform:java:plugin"))
    api(project(":platform:java:core"))       // Offset type
    api(libs.activej.promise)                  // Promise<T> in EventLogStore
    
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)
}
