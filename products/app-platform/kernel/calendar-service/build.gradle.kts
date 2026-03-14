/**
 * calendar-service — Bikram Sambat (BS) ↔ Gregorian calendar conversion
 *
 * Generic platform kernel module providing:
 * - Lookup-table based BS ↔ Gregorian calendar conversion (years 2070–2100)
 * - Immutable BsDate value object and CalendarConversionResult
 * - Jurisdiction-scoped holiday calendar with PostgreSQL persistence
 * - Business day calculation (is business day, next business day, days between)
 *
 * No product-specific logic — suitable for any product in the monorepo.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "BS ↔ Gregorian calendar conversion and holiday calendar"

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
    implementation(libs.activej.http)
    implementation(libs.activej.inject)

    // -------------------------------------------------------------------------
    // Persistence — JDBC + Flyway migrations
    // -------------------------------------------------------------------------
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

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
