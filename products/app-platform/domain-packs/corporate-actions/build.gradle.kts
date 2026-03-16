/**
 * corporate-actions — D-12 Corporate Actions domain pack
 *
 * Provides:
 *   - Corporate action lifecycle management: ANNOUNCED → EX_DATE → RECORD_DATE → PAYMENT_DATE → COMPLETED
 *   - Support for CASH_DIVIDEND, STOCK_DIVIDEND, BONUS, RIGHTS, SPLIT, MERGER action types
 *   - Cash and securities ledger postings via K-16 integration (D12-001 to D12-003)
 *   - Holder snapshot and entitlement calculations (D12-004 to D12-006)
 *   - Rights issue and stock dividend entitlement (D12-007 to D12-008)
 *   - Tax withholding and tax certificate generation (D12-009 to D12-010)
 *   - Corporate action instruction narrative (D12-011)
 *   - Notification workflow for holders on CA events (D12-012)
 *   - Election management for voluntary CAs (D12-013)
 *   - Reconciliation of CA position breaks (D12-014)
 *   - Dual BS/AD calendar dates via K-15 integration
 *   - Maker-checker enforced via K-01 before CA activation
 *
 * Covered sprints: M2B Sprint 10 (per WEEK_BY_WEEK_IMPLEMENTATION_PLAN.md §5, Sprint 10)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-12: Corporate Actions — full lifecycle for dividends, rights, splits, mergers with entitlement calculation"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Platform ────────────────────────────────────────────────────────────
    api(project(":platform:java:core"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:governance"))

    // ─── Kernel dependencies ─────────────────────────────────────────────────
    implementation(project(":products:app-platform:kernel:event-store"))
    implementation(project(":products:app-platform:kernel:config-engine"))
    implementation(project(":products:app-platform:kernel:iam"))
    implementation(project(":products:app-platform:kernel:calendar-service"))
    implementation(project(":products:app-platform:kernel:audit-trail"))
    implementation(project(":products:app-platform:kernel:ledger-framework"))
    implementation(project(":products:app-platform:kernel:workflow-orchestration"))

    // ─── Domain pack dependencies ─────────────────────────────────────────────
    implementation(project(":products:app-platform:domain-packs:reference-data"))
    implementation(project(":products:app-platform:domain-packs:oms"))
    implementation(project(":products:app-platform:domain-packs:post-trade"))

    // ─── AEP (event processing for CA events) ────────────────────────────────
    implementation(project(":products:aep:platform"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── ActiveJ (non-blocking I/O) ───────────────────────────────────────────
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
