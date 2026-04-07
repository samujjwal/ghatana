/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
    alias(libs.plugins.openapi.generator)
}

group = "com.ghatana.products"
version = "1.0.0"
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
    api(project(":platform-plugins:plugin-billing-ledger"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-audit-trail"))

    // =================================================================
    // Platform Libraries (legacy - will be migrated in future phases)
    // =================================================================
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:audit"))

    // Kernel platform — MIGRATED: use platform-kernel:kernel-core
    // api(project(":platform:java:kernel"))

    // Shared billing contracts — MIGRATED: use platform-plugins:plugin-billing-ledger
    // api(project(":platform:java:billing"))

    // Distributed cache (ISSUE-X02 / KRQ-05)
    implementation(project(":platform:java:distributed-cache"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Observability
    implementation(libs.micrometer.core)
    
    // JWT for authentication
    implementation(libs.nimbus.jose.jwt)
    
    // Jackson for JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // PostgreSQL persistence
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    
    // Commons utilities
    implementation(libs.commons.codec)
    implementation(libs.commons.lang3)
    implementation(libs.commons.collections4)
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)

    // Testing
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.jmh.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val phrOpenApiSpec = file("docs/openapi.yaml")

tasks.register<org.openapitools.generator.gradle.plugin.tasks.ValidateTask>("validatePhrSpec") {
    group = "contracts"
    description = "Validates products/phr/docs/openapi.yaml"
    inputSpec.set(phrOpenApiSpec.absolutePath)
    recommend.set(true)
}

tasks.named("check") {
    dependsOn("validatePhrSpec")
}

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
    dependsOn("validatePhrSpec")
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
