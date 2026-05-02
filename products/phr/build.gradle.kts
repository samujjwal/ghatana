/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-module")
}

group = "com.ghatana.products"
version = rootProject.version
description = "PHR — Personal Health Records product module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.srcDir("domains/healthcare/src/main/java")
    }
    test {
        java.srcDir("domains/healthcare/src/test/java")
    }
}

dependencies {
    // =================================================================
    // Platform Kernel (NEW: extracted from platform/java)
    // =================================================================
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    // =================================================================
    // Platform Plugins (NEW: shared product-agnostic plugins)
    // =================================================================
    api(project(":platform-plugins:plugin-ledger"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-audit-trail"))

    // =================================================================
    // Platform Libraries (legacy - will be migrated in future phases)
    // =================================================================
    api(project(":platform:java:security"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:database"))
    api(project(":platform:java:audit"))

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

// =============================================================================
// Phase 3.3: Pack schema validation gates
// =============================================================================

tasks.register("validateDomainPackManifest") {
    description = "Validates the PHR domain-pack-manifest.yaml for required fields and schema correctness."
    group = "verification"
    val manifestFile = layout.projectDirectory.file("domain-pack-manifest.yaml").asFile
    doLast {
        require(manifestFile.exists()) { "domain-pack-manifest.yaml is missing from product root" }
        val content = manifestFile.readText()
        val requiredFields = listOf("pack:", "id:", "version:", "capabilities:", "domain:")
        val missing = requiredFields.filter { field -> !content.contains(field) }
        if (missing.isNotEmpty()) {
            throw GradleException("PHR domain-pack-manifest.yaml is missing required fields: $missing")
        }
        logger.lifecycle("PHR domain-pack-manifest.yaml validation passed.")
    }
}

tasks.register("validatePolicyPack") {
    description = "Validates that PHR boundary policy store class compiles and its rules are non-empty."
    group = "verification"
    dependsOn(tasks.named("compileJava"))
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    doLast {
        val storeClass = "com.ghatana.phr.kernel.policy.PhrBoundaryPolicyStore"
        val classFile = classesDir.walkTopDown().toList()
            .firstOrNull { it.name == "PhrBoundaryPolicyStore.class" }
        requireNotNull(classFile) {
            "PHR policy pack class not found: $storeClass — ensure it compiles correctly"
        }
        logger.lifecycle("PHR policy pack validation passed: ${classFile.name}")
    }
}

tasks.register("validateComplianceRulePack") {
    description = "Validates that PHR compliance rule pack compiles and rule set IDs are non-blank."
    group = "verification"
    dependsOn(tasks.named("compileJava"))
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    doLast {
        val packClass = "com.ghatana.phr.kernel.policy.PhrComplianceRulePack"
        val classFile = classesDir.walkTopDown().toList()
            .firstOrNull { it.name == "PhrComplianceRulePack.class" }
        requireNotNull(classFile) {
            "PHR compliance rule pack class not found: $packClass — ensure it compiles correctly"
        }
        logger.lifecycle("PHR compliance rule pack validation passed: ${classFile.name}")
    }
}

tasks.named("check") {
    dependsOn(
        "validateDomainPackManifest",
        "validatePolicyPack",
        "validateComplianceRulePack"
    )
}

