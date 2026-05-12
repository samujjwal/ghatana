/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

import com.ghatana.buildlogic.ProductPackValidationExtension

plugins {
    id("java-module")
    id("product-pack-validation")
}

group = "com.ghatana.products"
version = rootProject.version
description = "PHR — Personal Health Records product module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // =================================================================
    // PHR Domain Modules (explicit submodules)
    // =================================================================
    api(project(":products:phr:domains:healthcare"))
    // =================================================================
    // Platform Kernel (NEW: extracted from platform/java)
    // =================================================================
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    // =================================================================
    // Platform Plugins (NEW: shared product-agnostic plugins)
    // =================================================================
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-audit-trail"))
    api(project(":platform-plugins:plugin-human-approval"))
    api(project(":platform-plugins:plugin-ledger"))

    // =================================================================
    // Platform Libraries (legacy - will be migrated in future phases)
    // =================================================================
    api(project(":platform:java:security"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:database"))
    api(project(":platform:java:audit"))
    api(project(":platform:java:http"))
    api(project(":platform:java:cache"))

    // Kernel platform — MIGRATED: use platform-kernel:kernel-core
    // api(project(":platform:java:kernel"))

    // Shared billing contracts — MIGRATED: use platform-plugins:plugin-ledger
    // api(project(":platform:java:billing"))

    // Distributed cache provided via platform:java:database

    // ActiveJ
    api(libs.bundles.activej.core)
    api(libs.bundles.activej.http)

    // Platform Core (JsonUtils)
    api(project(":platform:java:core"))

    // Observability
    implementation(libs.bundles.observability.core)

    // Security
    implementation(libs.bundles.security.core)

    // JSON processing
    implementation(libs.bundles.jackson.json)

    // Database
    implementation(libs.bundles.database.core)

    // Commons utilities
    implementation(libs.bundles.common.utils)

    // Logging
    implementation(libs.bundles.logging.core)

    // Testing
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.bundles.testing.containers)

    // JMH for benchmarking
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

// OpenAPI validation task removed - plugin not available in current setup

tasks.register<JavaExec>("benchmarkBillingFlow") {
    group = "verification"
    description = "Runs the PHR billing critical-path JMH benchmark"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(
        "com.ghatana.phr.kernel.service.BillingServiceBenchmark",
        "-wi", "1",
        "-i", "2",
        "-f", "1",
        "-tu", "ms",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/phr-billing-benchmark.json").get().asFile.absolutePath
    )
}

tasks.register<Test>("phrReleaseGate") {
    group = "verification"
    description = "Runs the PHR release-gate regression suite used before staging sign-off"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("com.ghatana.phr.security.PHRSecurityIntegrationTest")
        includeTestsMatching("com.ghatana.phr.observability.PHRAuditTrailServiceTest")
        includeTestsMatching("com.ghatana.phr.service.PatientServiceTest")
        includeTestsMatching("com.ghatana.phr.kernel.service.ClinicalDecisionSupportServiceTest")
        includeTestsMatching("com.ghatana.phr.kernel.PhrKernelModuleTest")
    }
}

tasks.register<Test>("checkApiContractConformance") {
    group = "verification"
    description = "Validates that implemented routes match the OpenAPI specification (CI gate for contract drift)"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("com.ghatana.phr.api.PhrApiContractConformanceTest")
    }
}

configure<ProductPackValidationExtension> {
    productName.set("PHR")
    manifestFile.set(layout.projectDirectory.file("domain-pack-manifest.yaml"))
    requiredManifestFields.set(
        listOf(
            "pack:", "id:", "version:", "capabilities:", "domain:",
            "kernelCapabilitiesConsumed:", "policyActions:", "pluginsConsumed:", "bridgesConsumed:",
            "domainPacksProvided:", "uiSurfaces:", "runtimeServices:", "dataSensitivity:",
            "policyResources:"
        )
    )
    policyPackTestPatterns.set(
        listOf(
            "com.ghatana.phr.kernel.PhrPackContractTest",
            "com.ghatana.phr.kernel.PhrKernelBoundaryContractTest"
        )
    )
    complianceSourceFile.set(layout.projectDirectory.file(
        "src/main/java/com/ghatana/phr/kernel/policy/PhrComplianceRulePack.java"
    ))
    complianceRulePrefix.set("PHR-")
}
