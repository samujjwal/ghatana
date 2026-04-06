/**
 * Platform Distributed Cache Module (KRQ-05)
 *
 * Generic Redis/Dragonfly-backed distributed cache abstraction with:
 * - DistributedCachePort<K,V> interface
 * - Redis/Jedis adapter implementation
 * - TTL-based expiry + maximum-size eviction
 * - Event-driven invalidation via KernelInterScopeBus
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "1.0.0"
description = "Platform Distributed Cache — Redis-backed distributed cache abstraction (KRQ-05)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))

    // ActiveJ async
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Redis client
    implementation(libs.jedis)

    // Local fallback — Caffeine for in-process cache
    implementation(libs.caffeine)

    // JSON serialization
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
