/*
 * AEP Server Module - Build Configuration
 *
 * Canonical server surface for the AEP product. Contains HTTP endpoints,
 * gRPC services, and the main application entry point.
 */

plugins {
    id("java-module")
    `maven-publish`
}

dependencies {
    // Platform modules (modularized - Item 46)
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":products:aep:aep-registry"))
    implementation(project(":products:aep:aep-analytics"))
    implementation(project(":products:aep:aep-security"))
    implementation(project(":products:aep:aep-central-runtime"))
    implementation(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    implementation(project(":products:aep:aep-event-cloud"))  // Data-Cloud bridge plugin
    // aep-agent merged into aep-registry on 2026-03-22
    implementation(project(":products:aep:aep-api"))

    // Orchestrator sub-module (pipeline lifecycle, execution queues, DI wiring)
    implementation(project(":products:aep:orchestrator"))
    // Data Cloud SPI plus launcher runtime surface for embedded client creation.
    implementation(project(":products:data-cloud:spi"))
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":products:data-cloud:agent-registry"))

    // AEP product modules — identity and compliance (Phase 8)
    implementation(project(":products:aep:aep-identity"))
    implementation(project(":products:aep:aep-compliance"))

    // Agent runtime — learning, review, consolidation, evaluation packages
    implementation(project(":products:aep:aep-agent-runtime"))

    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))

    // Phase 9 — new governance platform modules wired into AEP server
    implementation(project(":platform:java:identity"))
    implementation(project(":platform:java:data-governance"))
    implementation(project(":platform:java:tool-runtime"))
    implementation(project(":platform:java:policy-as-code"))
    implementation(project(":platform:java:security"))
    implementation(project(":shared-services:incident-service"))

    // gRPC transport (for AepGrpcServer)
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
}

tasks.named<Test>("test") {
    // useJUnitPlatform() already applied by java-module; keep environment override
    environment("AEP_AUTH_DISABLED", "true")
    environment("AEP_ENV", "test")
    environment("AEP_JWT_SECRET", "test-jwt-secret-0123456789abcdef")
}

// All previously-excluded tests are now fully implemented and enabled:
//   AepComplianceServiceTest    — AepComplianceReport.operation()/recordsAffected() added
//   AepDynamicConfigServiceTest — EnvConfig.KAFKA_BOOTSTRAP_SERVERS/fromMap() added
//   AepHttpServerComplianceTest — DataCloudClient.delete() confirmed present in SPI
//   DataCloudPipelineStoreTest  — Pipeline class present in aep-registry

// =============================================================================
// CODE QUALITY — Spotless, Checkstyle, PMD, SpotBugs
// =============================================================================




// =============================================================================
// OpenAPI Spec Sync — canonical source is platform/contracts/openapi/aep.yaml
// The product-local copy at products/aep/contracts/openapi.yaml is kept as
// a legacy alias; CI enforces that both match the platform canonical.
// =============================================================================

val canonicalSpec = rootProject.file("platform/contracts/openapi/aep.yaml")
val legacySpec    = rootProject.file("products/aep/contracts/openapi.yaml")
val runtimeSpec   = file("src/main/resources/openapi.yaml")

tasks.register<Copy>("syncOpenApiSpec") {
    description = "Copies the canonical OpenAPI spec into server resources"
    group = "build"
    from(canonicalSpec)
    into(runtimeSpec.parentFile)
    rename { "openapi.yaml" }
}

tasks.register("verifyOpenApiSync") {
    description = "Fails build if the runtime OpenAPI spec diverges from the canonical copy in platform/contracts"
    group = "verification"
    dependsOn("syncOpenApiSpec")
    val canonicalSpecFile = canonicalSpec
    val legacySpecFile = legacySpec
    val runtimeSpecFile = runtimeSpec
    inputs.file(canonicalSpecFile)
    if (legacySpecFile.exists()) {
        inputs.file(legacySpecFile)
    }
    inputs.file(runtimeSpecFile)
    doLast {
        if (!canonicalSpecFile.exists()) {
            throw GradleException("Canonical OpenAPI spec not found: $canonicalSpecFile")
        }
        // Ensure the legacy product-local copy has not drifted from the platform canonical
        if (legacySpecFile.exists()) {
            val canonical = canonicalSpecFile.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            val legacy = legacySpecFile.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            if (canonical != legacy) {
                throw GradleException(
                    "OpenAPI spec drift: products/aep/contracts/openapi.yaml diverges from platform/contracts/openapi/aep.yaml.\n" +
                    "The platform canonical is the source of truth — update or remove the legacy copy."
                )
            }
        }
        if (!runtimeSpecFile.exists()) {
            throw GradleException("Runtime OpenAPI spec not found: $runtimeSpecFile — run :products:aep:server:syncOpenApiSpec")
        }
        val canonical = canonicalSpecFile.readText()
        // Strip the NOTE comment lines that only exist in the runtime copy
        val runtime = runtimeSpecFile.readLines()
            .dropWhile { it.startsWith("#") }
            .joinToString("\n")
        val canonicalBody = canonical.lines()
            .dropWhile { it.startsWith("#") }
            .joinToString("\n")
        if (runtime.trim() != canonicalBody.trim()) {
            throw GradleException(
                "OpenAPI spec drift detected! Runtime copy differs from canonical.\n" +
                "Run: ./gradlew :products:aep:server:syncOpenApiSpec"
            )
        }
    }
}

tasks.named("processResources") { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessJava" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessMisc" }.configureEach { dependsOn("syncOpenApiSpec") }
tasks.matching { it.name == "spotlessXml" }.configureEach { dependsOn("syncOpenApiSpec") }
