/**
 * OMS Domain Module - Order Management System
 *
 * Finance-specific domain module for order lifecycle management,
 * validation, and execution workflows.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "OMS Domain - Order Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform:java:kernel"))
    api(project(":platform:java:kernel:modules:authentication"))
    api(project(":platform:java:kernel:modules:event-store"))
    api(project(":platform:java:kernel:modules:audit"))
    api(project(":platform:java:kernel:modules:resilience"))

    // Platform Libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:market-data"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.jedis)
    implementation(libs.kafka.clients)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    implementation(libs.activej.core)
    implementation(libs.activej.http)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
