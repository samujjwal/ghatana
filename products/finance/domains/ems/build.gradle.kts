/**
 * EMS Domain Module - Execution Management System
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "EMS Domain - Execution Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(project(":products:finance:domains:oms"))
    api(libs.activej.promise)

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:reference-data"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.jedis)
    implementation(libs.kafka.clients)
    implementation(libs.micrometer.core)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
