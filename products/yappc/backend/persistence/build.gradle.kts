/*
 * YAPPC Backend Persistence Module
 * Repository interfaces, JDBC implementations, and database migrations.
 */
plugins {
    id("java-library")
}

description = "YAPPC Backend - Persistence layer (repositories + JDBC + migrations)"

dependencies {
    // Domain model
    api(project(":products:yappc:libs:java:yappc-domain"))

    // Platform
    implementation(project(":platform:java:core"))

    // Database
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // JSON (for JSONB columns)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
