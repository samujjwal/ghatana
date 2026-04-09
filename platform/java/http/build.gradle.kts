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
    api(project(":platform:java:runtime"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // ActiveJ HTTP
    api(libs.activej.http)
    api(libs.bundles.activej.http)
    
    // OkHttp client
    api("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Guava
    api(libs.guava)

    // Caffeine for bounded tenant limiter cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // JSON Processing
    api(libs.jackson.databind)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
