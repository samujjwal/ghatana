/**
 * Platform Core Module
 * 
 * Provides foundational utilities, types, and common patterns used across all products.
 * This is the base module with NO internal dependencies.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Core - Basic utilities, types, and common patterns"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // ActiveJ (async framework)
    api(libs.activej.promise)
    
    // Logging
    api(libs.slf4j.api)
    
    // JSON Processing
    api(libs.jackson.databind)
    api(libs.jackson.core)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.datatype.jdk8)
    
    // Validation
    api(libs.jakarta.validation.api)

    // Protobuf
    api(libs.protobuf.java)
    api(libs.javax.annotation.api)
    api(libs.google.common.protos)
    
    // JWT (Nimbus JOSE + JWT)
    api(libs.nimbus.jose.jwt)
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
