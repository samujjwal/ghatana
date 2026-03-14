/**
 * event-store — Append-only DDD aggregate event store
 *
 * Generic platform kernel module providing aggregate-scoped event sourcing
 * with optimistic concurrency, dual-calendar (BS + Gregorian) timestamp
 * enrichment hooks, and row-level-security tenant isolation.
 *
 * Adapters: PostgresAggregateEventStore (JDBC/Flyway).
 * CalendarDate enrichment wired by calendar-service kernel (Sprint 2).
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "Append-only DDD aggregate event store with dual-calendar support"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // Platform dependencies
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
    // Serialization
    // -------------------------------------------------------------------------
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------
    implementation(libs.networknt.validator)

    // -------------------------------------------------------------------------
    // Event transport — Kafka (K-05: outbox relay publisher + consumer framework)
    // -------------------------------------------------------------------------
    implementation(libs.kafka.clients)

    // -------------------------------------------------------------------------
    // Idempotency store — Redis (K-05: Redis-backed dedup; jedis for SETNX)
    // -------------------------------------------------------------------------
    implementation(libs.jedis)

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
