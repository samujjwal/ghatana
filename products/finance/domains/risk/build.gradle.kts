/**
 * Risk Domain Module - Risk Management
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Risk Domain - Risk Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:kernel"))
    api(project(":products:finance:domains:pms"))
    api(libs.activej.promise)

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:oms"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    api(project(":platform:java:kernel:modules:authentication"))
    api(project(":platform:java:kernel:modules:event-store"))
    api(project(":platform:java:kernel:modules:audit"))
    api(project(":platform:java:kernel:modules:resilience"))
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.hikaricp)
    implementation(libs.jedis)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
