/**
 * Finance Integration Testing Module - End-to-End Workflow Tests
 */
plugins {
    id("finance-domain-module")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Finance Integration Testing - End-to-End Workflow Tests"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Finance Product Dependencies ──────────────────────────────────────
    api(project(":products:finance"))
    api(project(":platform-kernel:kernel-core"))

    // ─── Finance Domain Dependencies (for comprehensive e2e testing) ────────
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:pricing"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:surveillance"))
    implementation(project(":products:finance:domains:reconciliation"))

    // ─── Testing Dependencies ─────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)

    // ─── ActiveJ/Promise Dependencies ─────────────────────────────────────
    testImplementation(libs.activej.eventloop)
    testImplementation(libs.activej.promise)
    testImplementation(libs.activej.http)

    // ─── Infrastructure Dependencies ──────────────────────────────────────
    testImplementation(libs.postgresql)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.log4j.slf4j.impl)
    testImplementation(libs.jackson.databind)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
