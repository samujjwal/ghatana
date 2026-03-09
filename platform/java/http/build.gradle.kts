/**
 * Platform HTTP Module
 * 
 * Provides HTTP client and server utilities built on ActiveJ.
 * Includes common middleware, error handling, and request/response patterns.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform HTTP - HTTP client and server utilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // ActiveJ HTTP
    api(libs.activej.http)
    api(libs.activej.eventloop)
    
    // OkHttp client
    api(libs.okhttp)
    
    // Guava
    api(libs.guava)
    
    // JSON Processing
    api(libs.jackson.databind)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.okhttp.mockwebserver)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
