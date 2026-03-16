/**
 * sanctions — D-14 Sanctions Screening domain pack
 *
 * Provides:
 *   - Real-time in-memory screening engine with trie/prefix structure (D14-001)
 *   - Screening REST API + OMS/onboarding/settlement integration (D14-002)
 *   - Confidence scoring and auto-block / REVIEW classification (D14-003)
 *   - Levenshtein edit distance name matching (D14-004)
 *   - Jaro-Winkler similarity + phonetic (Soundex) matching (D14-005)
 *   - Devanagari transliteration and alias expansion (D14-006)
 *   - Sanctions list ingestion: OFAC/UN/EU/NRB XML+CSV (D14-009)
 *   - Batch re-screening engine (D14-011)
 *   - Air-gap signed bundle support (D14-013, D14-014)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.appplatform"
version = "0.1.0-SNAPSHOT"
description = "D-14: Sanctions Screening domain pack"

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

    // ─── Kernel ──────────────────────────────────────────────────────────────
    implementation(project(":kernel:event-store"))
    implementation(project(":kernel:config-engine"))
    implementation(project(":kernel:iam"))
    implementation(project(":kernel:audit-trail"))
    implementation(project(":kernel:secrets-management"))

    // ─── Persistence ─────────────────────────────────────────────────────────
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.hikaricp)

    // ─── Event publishing ─────────────────────────────────────────────────────
    implementation(libs.kafka.clients)

    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)

    // ─── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
