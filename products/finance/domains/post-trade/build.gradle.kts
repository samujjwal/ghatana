/**
 * Post-Trade Domain Module
 *
 * Finance-specific domain module for post-trade processing,
 * including settlement, clearing, and custody operations.
 */
plugins {
    id("finance-domain-module")
}

group = "com.ghatana.products.finance.domains"
version = rootProject.version
description = "Post-Trade Domain - settlement, clearing, custody"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel Platform
    api(project(":platform-kernel:kernel-core"))

    // Platform Libraries
    api(project(":platform:java:audit"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Observability (for workflow metrics in TradeSettlementWorkflowService)
    implementation(libs.micrometer.core)

    // Testing

    // ─── Cross-Domain Dependencies (migrated from app-platform) ────────────
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:reference-data"))

    // ─── Infrastructure Dependencies (migrated from app-platform) ──────────
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.jedis)

    implementation(libs.hikaricp)
    implementation(libs.kafka.clients)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
