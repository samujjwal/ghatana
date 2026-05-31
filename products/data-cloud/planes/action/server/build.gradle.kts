/*
 * Data Cloud Action Plane Server Module - Build Configuration
 *
 * Canonical server surface for the Data Cloud Action Plane runtime. Contains HTTP endpoints,
 * gRPC services, and the main application entry point.
 */

plugins {
    id("java-module")
    `maven-publish`
}

dependencies {
    // Platform modules (modularized - Item 46)
    implementation(project(":products:data-cloud:planes:action:engine"))
    implementation(project(":products:data-cloud:planes:action:registry"))
    implementation(project(":products:data-cloud:planes:action:analytics"))
    implementation(project(":products:data-cloud:planes:action:security"))
    implementation(project(":products:data-cloud:planes:action:central-runtime"))
    implementation(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    implementation(project(":products:data-cloud:planes:action:event-bridge"))  // Data-Cloud bridge plugin
    // agent-runtime merged into registry on 2026-03-22
    implementation(project(":products:data-cloud:planes:action:api"))

    // Orchestrator sub-module (pipeline lifecycle, execution queues, DI wiring)
    implementation(project(":products:data-cloud:planes:action:orchestrator"))
    // Data Cloud SPI plus launcher runtime surface for embedded client creation.
    implementation(project(":products:data-cloud:planes:shared-spi"))
    implementation(project(":products:data-cloud:delivery:runtime-composition"))
    implementation(project(":products:data-cloud:extensions:agent-registry"))

    // Data Cloud Action Plane modules — identity and compliance (Phase 8)
    implementation(project(":products:data-cloud:planes:action:identity"))
    implementation(project(":products:data-cloud:planes:action:compliance"))

    // Agent runtime — learning, review, consolidation, evaluation packages
    implementation(project(":products:data-cloud:planes:action:agent-runtime"))

    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))

    // Phase 9 — new governance platform modules wired into Data Cloud Action Plane server
    implementation(project(":platform:java:identity"))
    implementation(project(":platform:java:data-governance"))
    implementation(project(":platform:java:tool-runtime"))
    implementation(project(":platform:java:policy-as-code"))
    implementation(project(":platform:java:security"))
    implementation(project(":shared-services:incident-service"))

    // gRPC transport (for ActionPlaneGrpcServer)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    // ActiveJ framework
    implementation(libs.activej.launcher)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.config)
    implementation(libs.bundles.activej.core)
    implementation(libs.activej.promise)

    // Jackson for JSON
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Micrometer Prometheus registry — for /metrics scrape endpoint
    implementation(libs.micrometer.registry.prometheus)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.kafka.clients)
    testImplementation(libs.testcontainers.kafka)
    testImplementation("org.junit.platform:junit-platform-suite:1.11.4")
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.archunit.junit5)
}

tasks.named<Test>("test") {
    // useJUnitPlatform() already applied by java-module; keep environment override
    environment("ACTION_PLANE_AUTH_DISABLED", "true")
    environment("ACTION_PLANE_ENV", "test")
    environment("ACTION_PLANE_JWT_SECRET", "test-jwt-secret-0123456789abcdef")

    // Fail fast when a server-side integration test truly stalls instead of
    // leaving the Gradle task looking hung indefinitely.
    systemProperty(
        "junit.jupiter.execution.timeout.default",
        System.getProperty("junit.jupiter.execution.timeout.default", "2m")
    )
    systemProperty(
        "junit.jupiter.execution.timeout.thread.mode.default",
        System.getProperty("junit.jupiter.execution.timeout.thread.mode.default", "separate_thread")
    )

    // Keep class-level --tests filtering stable by skipping the suite aggregator class.
    // The underlying integration tests still run directly as normal JUnit classes.
    exclude("**/IntegrationTestSuite.class")
}

// All previously-excluded tests are now fully implemented and enabled:
//   ActionPlaneComplianceServiceTest    — ActionPlaneComplianceReport.operation()/recordsAffected() added
//   ActionPlaneDynamicConfigServiceTest — EnvConfig.KAFKA_BOOTSTRAP_SERVERS/fromMap() added
//   ActionPlaneHttpServerComplianceTest — DataCloudClient.delete() confirmed present in SPI
//   DataCloudPipelineStoreTest  — Pipeline class present in action-registry

// =============================================================================
// CODE QUALITY — Spotless, Checkstyle, PMD, SpotBugs
// =============================================================================




// =============================================================================
// OpenAPI Spec Sync — canonical source is products/data-cloud/contracts/openapi/action-plane.yaml.
// The platform registry copy remains in platform/contracts/openapi/action-plane.yaml and
// CI enforces that both match the product-level contract.
// =============================================================================

val canonicalSpec = rootProject.file("products/data-cloud/contracts/openapi/action-plane.yaml")
val aepSpec = rootProject.file("products/data-cloud/contracts/openapi/aep.yaml")
val platformSpec  = rootProject.file("platform/contracts/openapi/action-plane.yaml")
val runtimeSpec   = file("src/main/resources/openapi.yaml")

tasks.register<Copy>("syncOpenApiSpec") {
    description = "Copies the canonical OpenAPI spec into server resources"
    group = "build"
    from(aepSpec)
    into(runtimeSpec.parentFile)
    rename { "openapi.yaml" }
}

tasks.register("verifyOpenApiSync") {
    description = "Fails build if the runtime or platform OpenAPI copy diverges from the product-level Action Plane contract"
    group = "verification"
    dependsOn("syncOpenApiSpec")
    val aepSpecFile = aepSpec
    val platformSpecFile = platformSpec
    val runtimeSpecFile = runtimeSpec
    inputs.file(aepSpecFile)
    if (platformSpecFile.exists()) {
        inputs.file(platformSpecFile)
    }
    inputs.file(runtimeSpecFile)
    doLast {
        if (!aepSpecFile.exists()) {
            throw GradleException("AEP OpenAPI spec not found: $aepSpecFile")
        }
        // Ensure the platform registry copy has not drifted from the product canonical.
        if (platformSpecFile.exists()) {
            val aep = aepSpecFile.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            val platform = platformSpecFile.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            if (aep != platform) {
                throw GradleException(
                    "OpenAPI spec drift: platform/contracts/openapi/aep.yaml diverges from products/data-cloud/contracts/openapi/aep.yaml.\n" +
                    "The product-level contract is the source of truth — update the platform registry copy."
                )
            }
        }
        if (!runtimeSpecFile.exists()) {
            throw GradleException("Runtime OpenAPI spec not found: $runtimeSpecFile — run :products:data-cloud:planes:action:server:syncOpenApiSpec")
        }
        val aep = aepSpecFile.readText()
        // Strip the NOTE comment lines that only exist in the runtime copy
        val runtime = runtimeSpecFile.readLines()
            .dropWhile { it.startsWith("#") }
            .joinToString("\n")
        val aepBody = aep.lines()
            .dropWhile { it.startsWith("#") }
            .joinToString("\n")
        if (runtime.trim() != aepBody.trim()) {
            throw GradleException(
                "OpenAPI spec drift detected! Runtime copy differs from AEP canonical.\n" +
                "Run: ./gradlew :products:data-cloud:planes:action:server:syncOpenApiSpec"
            )
        }
    }
}

tasks.named("processResources") { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessJava" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessMisc" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessXml" }.configureEach { dependsOn("syncOpenApiSpec") }
