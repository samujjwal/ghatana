/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * Platform Billing Module
 *
 * Shared billing contracts consumed by both the PHR product (healthcare billing)
 * and the Finance product (ledger posting). Neither product should depend on the
 * other — both depend downward on this platform module.
 *
 * Responsibilities:
 *   - BillingTransaction value type (domain-neutral charge record)
 *   - LedgerPostingService interface (Finance implements; PHR calls)
 *   - HealthcareBillingExtension interface (PHR-specific billing enrichment)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Billing — shared billing contracts for PHR and Finance integration"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel provides KernelContext and activej Promise types
    api(project(":platform:java:kernel"))
    api(project(":platform:java:core"))

    // ActiveJ Promise available in public contract signatures
    api(libs.activej.promise)

    // Test dependencies
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
