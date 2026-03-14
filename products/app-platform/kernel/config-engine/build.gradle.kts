/**
 * config-engine — Hierarchical configuration with JSON Schema validation
 *
 * Generic platform kernel module providing:
 * - Multi-level configuration hierarchy: GLOBAL → JURISDICTION → TENANT → USER → SESSION
 * - JSON Schema registration and validation (via networknt/json-schema-validator)
 * - JDBC-backed config store with Flyway migrations
 * - Priority-based ConfigMerger that merges entries from lowest to highest precedence
 *
 * No product-specific logic — suitable for any product in the monorepo.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Hierarchical configuration engine with JSON Schema validation"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Platform reuse
    // -------------------------------------------------------------------------
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    // -------------------------------------------------------------------------
    // ActiveJ async runtime
    // -------------------------------------------------------------------------
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.inject)

    // -------------------------------------------------------------------------
    // Persistence — JDBC + Flyway migrations
    // -------------------------------------------------------------------------
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // -------------------------------------------------------------------------
    // JSON Schema validation
    // -------------------------------------------------------------------------
    implementation(libs.networknt.validator)

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // -------------------------------------------------------------------------
    // Typesafe Config — HOCON support for test fixtures
    // -------------------------------------------------------------------------
    implementation(libs.typesafe.config)

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // -------------------------------------------------------------------------
    // Redis — for RedisConfigProjection read-through cache (K02-017)
    // -------------------------------------------------------------------------
    implementation(libs.jedis)

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // -------------------------------------------------------------------------
    // Testing
    // -------------------------------------------------------------------------
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
