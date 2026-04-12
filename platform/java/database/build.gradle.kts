/**
 * Platform Database Module
 * 
 * Provides database abstractions, connection pooling, and caching utilities.
 * Supports JDBC, JPA, Redis, and common database patterns.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Database - Database abstractions and caching utilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Platform Core
    api(project(":platform:java:core"))
    
    // Platform Observability (metrics for cache warming, pub/sub, replica lag monitoring)
    api(project(":platform:java:observability"))
    
    // Nullability annotations
    compileOnly(libs.jetbrains.annotations)
    
    // JDBC & Connection Pooling
    api(libs.hikaricp)
    
    // Jakarta Persistence API
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    
    // Hibernate (for JPA implementation)
    api("org.hibernate.orm:hibernate-core:6.6.1.Final")
    
    // Flyway Migration
    api(libs.flyway.core)
    api(libs.flyway.postgresql)

    // Redis
    api(libs.jedis)
    api("io.lettuce:lettuce-core:6.4.0.RELEASE")

    // ActiveJ async (for distributed-cache abstractions)
    api(libs.activej.promise)

    // Caffeine (in-process cache fallback for distributed-cache)
    implementation(libs.caffeine)

    // Jackson (cache serialization)
    implementation(libs.jackson.databind)
    
    // Database Drivers (optional - consumers should include what they need)
    compileOnly(libs.postgresql)
    compileOnly(libs.h2)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.slf4j.impl)
    testRuntimeOnly(libs.log4j.core)
}

tasks.test {
    useJUnitPlatform()
}
