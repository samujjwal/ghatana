/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

dependencies {
    // =================================================================
    // Platform Kernel (NEW: extracted from platform/java)
    // =================================================================
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform-kernel:kernel-testing"))

    // =================================================================
    // Platform Plugins (NEW: shared product-agnostic plugins)
    // =================================================================
    api(project(":platform-plugins:plugin-billing-ledger"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-risk-management"))
    api(project(":platform-plugins:plugin-audit-trail"))

    // =================================================================
    // Platform Core (legacy - will be migrated in future phases)
    // =================================================================
    api(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:agent-core"))

    // AI-specific dependencies
    implementation(libs.bundles.ai.integration)

    // ActiveJ - for async operations and DI
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.servicegraph)
    implementation(libs.activej.launcher)

    // Finance Domains - Core
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:rules"))

    // Finance Domains - New (Migration Phase 2)
    implementation(project(":products:finance:domains:corporate-actions"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:pricing"))
    implementation(project(":products:finance:domains:reconciliation"))
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:regulatory-reporting"))
    implementation(project(":products:finance:domains:sanctions"))
    implementation(project(":products:finance:domains:surveillance"))

    // Distributed cache (KRQ-05)
    implementation(project(":platform:java:distributed-cache"))

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Micrometer for metrics
    implementation(libs.micrometer.core)

    // PostgreSQL
    implementation(libs.bundles.database.core)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.bundles.testing.containers)
}

tasks.register<JavaExec>("validateContracts") {
    group = "verification"
    description = "Validates Finance contracts for deployment"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.ghatana.finance.contracts.ContractValidationRunner")
}

// Fix JaCoCo task dependency
tasks.named("jacocoTestReport") {
    dependsOn("compileJava")
}
