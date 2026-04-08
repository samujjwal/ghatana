/**
 * Sanctions Domain Module
 *
 * Finance-specific domain module for sanctions screening and compliance,
 * including watchlist screening, PEP screening, and adverse media monitoring.
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Sanctions Domain - watchlist screening, PEP checks"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))

    // Platform Libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Testing

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.kafka.clients)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
