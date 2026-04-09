plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud SPI - Shared interfaces and types for cross-product integration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Minimal dependencies - only what SPI types need
    api(project(":products:data-cloud:platform-entity"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform:java:core"))       // Offset type
    api(libs.activej.promise)                  // Promise<T> in EventLogStore
    
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
