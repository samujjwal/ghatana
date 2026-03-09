/**
 * Platform Runtime - ActiveJ integration and runtime management
 * 
 * This module provides runtime infrastructure for async execution,
 * event loop management, and ActiveJ framework integration.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Runtime - ActiveJ integration and runtime management"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // ActiveJ framework
    api(libs.activej.eventloop)
    api(libs.activej.promise)
    api(libs.activej.inject)
    api(libs.activej.launcher)
    api(libs.activej.http)
    api(libs.activej.servicegraph)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing Utilities (Exposed in API since they are in main source)
    api(libs.junit.jupiter.api)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
