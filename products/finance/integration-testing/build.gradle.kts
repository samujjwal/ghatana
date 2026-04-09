/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
    // alias(libs.plugins.openapi.generator)
}

dependencies {
    // Platform core dependencies
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform-kernel:kernel-plugin"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform-plugins:plugin-billing-ledger"))

    // Kernel modules

    // Finance core
    implementation(project(":products:finance"))
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:rules"))
    implementation(project(":products:finance:domains:corporate-actions"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:pricing"))
    implementation(project(":products:finance:domains:reconciliation"))
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:regulatory-reporting"))
    implementation(project(":products:finance:domains:sanctions"))
    implementation(project(":products:finance:domains:surveillance"))

    // ActiveJ Promise
    implementation(libs.activej.promise)

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")

    // PostgreSQL
    implementation(libs.postgresql)
    implementation("com.zaxxer:HikariCP")

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.h2)
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
}

// OpenAPI validation disabled due to plugin resolution issues
// val financeOpenApiSpec = rootProject.layout.projectDirectory.file("products/finance/docs/openapi.yaml").asFile

tasks.register<JavaExec>("benchmarkHealthcareBillingScenario") {
    group = "verification"
    description = "Runs the cross-domain PHR-to-Finance billing JMH benchmark"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(
        "com.ghatana.finance.integration.performance.HealthcareBillingToLedgerScenarioBenchmark",
        "-wi", "1",
        "-i", "2",
        "-f", "1",
        "-tu", "ms",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/healthcare-billing-ledger-benchmark.json").get().asFile.absolutePath
    )
}

tasks.register<JavaExec>("benchmarkFraudInferenceScenario") {
    group = "verification"
    description = "Runs the finance fraud inference JMH benchmark"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(
        "com.ghatana.finance.integration.performance.FraudInferenceScenarioBenchmark",
        "-wi", "1",
        "-i", "2",
        "-f", "1",
        "-tu", "ms",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/fraud-inference-benchmark.json").get().asFile.absolutePath
    )
}
