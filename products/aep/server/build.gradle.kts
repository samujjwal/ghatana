/*
 * AEP Server Module - Build Configuration
 * 
 * Canonical server surface for the AEP product. Contains HTTP endpoints,
 * gRPC services, and the main application entry point.
 */

plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // Platform modules (modularized - Item 46)
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":products:aep:aep-registry"))
    implementation(project(":products:aep:aep-analytics"))
    implementation(project(":products:aep:aep-security"))
    implementation(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    implementation(project(":products:aep:aep-event-cloud"))  // Data-Cloud bridge plugin
    // aep-agent merged into aep-registry on 2026-03-22
    implementation(project(":products:aep:aep-api"))
    
    // Orchestrator sub-module (pipeline lifecycle, execution queues, DI wiring)
    implementation(project(":products:aep:orchestrator"))
    // Data Cloud SPI plus launcher runtime surface for embedded client creation.
    implementation(project(":products:data-cloud:spi"))
    implementation(project(":products:data-cloud:platform-launcher"))
    
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
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Disable JWT auth for integration tests; auth logic is tested via AepSecurityTest
    environment("AEP_AUTH_DISABLED", "true")
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
    doLast {
        if (!canonicalSpec.exists()) {
            throw GradleException("Canonical OpenAPI spec not found: $canonicalSpec")
        }
        // Ensure the legacy product-local copy has not drifted from the platform canonical
        if (legacySpec.exists()) {
            val canonical = canonicalSpec.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            val legacy = legacySpec.readText().lines().dropWhile { it.startsWith("#") }.joinToString("\n").trim()
            if (canonical != legacy) {
                throw GradleException(
                    "OpenAPI spec drift: products/aep/contracts/openapi.yaml diverges from platform/contracts/openapi/aep.yaml.\n" +
                    "The platform canonical is the source of truth — update or remove the legacy copy."
                )
            }
        }
        if (!runtimeSpec.exists()) {
            throw GradleException("Runtime OpenAPI spec not found: $runtimeSpec — run :products:aep:server:syncOpenApiSpec")
        }
        val canonical = canonicalSpec.readText()
        // Strip the NOTE comment lines that only exist in the runtime copy
        val runtime = runtimeSpec.readLines()
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
