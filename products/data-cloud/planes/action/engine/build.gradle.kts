/*
 * Action Plane Engine Module - Build Configuration
 *
 * Core Action Plane execution engine. Contains: operators, patterns, pipelines,
 * state stores, event processing.
 *
 * @doc.type module
 * @doc.purpose Core Action Plane execution engine
 * @doc.layer product
 * @doc.pattern Module
 */

plugins {
    id("java-module")
}

dependencies {
    api(project(":products:data-cloud:planes:action:operator-contracts"))

    // ActiveJ - core async framework
    api(libs.bundles.activej.core)
    api(libs.activej.promise)
    api(libs.activej.http)
    api(libs.activej.csp)

    // Jackson - JSON processing
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)

    // Platform domain
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:contracts"))
    api(project(":platform:java:security"))
    api(project(":platform:java:config"))
    api(project(":platform:java:agent-core"))  // Agent runtime consolidation
    api(project(":platform:java:messaging"))   // Unified messaging

    // Redis
    implementation(libs.jedis)

    // Logging (canonical: Log4j2)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:data-cloud:planes:action:security"))
    testImplementation(project(":products:data-cloud:planes:action:registry"))
    testImplementation(project(":products:data-cloud:extensions:agent-registry"))  // Test-only: DataCloud integration types
    testImplementation(libs.archunit.junit5)  // Boundary test enforcement
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}

// =============================================================================
// Boundary Test — enforce Action Plane engine module boundaries
// =============================================================================

/**
 * Validates that the Action Plane engine module respects architectural boundaries:
 * - Engine depends only on operator-contracts and platform modules
 * - No direct dependencies on orchestrator, server, or other Action Plane sub-modules
 * - No dependency on product-specific modules outside Data Cloud
 *
 * Run explicitly: ./gradlew :products:data-cloud:planes:action:engine:validateModuleBoundaries
 * This task also runs automatically as part of the standard test lifecycle via `check`.
 */
tasks.register("validateModuleBoundaries") {
    group = "verification"
    description = "Validates Action Plane engine module boundary compliance"
    dependsOn(tasks.test)
    doLast {
        logger.lifecycle("✅ Action Plane engine module boundary validation complete (backed by ArchUnit tests)")
    }
}

tasks.named("check") {
    dependsOn("validateModuleBoundaries")
}
