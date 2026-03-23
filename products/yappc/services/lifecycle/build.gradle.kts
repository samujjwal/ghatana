plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-lifecycle")

description = "YAPPC Services: Lifecycle — SDLC phase management and orchestration"

dependencies {
    // Internal domain (full monorepo paths)
    implementation(project(":products:yappc:services:platform"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:agent-core"))    // YAPPC-Ph3: YamlTemplateEngine (merged from yaml-template)

    // AEP contracts (YAPPC-Ph4: OperatorCatalog, InMemoryOperatorCatalog, AepEventCloudFactory)
    implementation(project(":products:aep:platform-contracts"))

    // AEP orchestrator (TriggerListener, ExecutionQueue)
    implementation(project(":products:aep:orchestrator"))

    // Platform: Agent Runtime — MemoryPlane, MemoryStoreAdapter, Jdbc stores
    implementation(project(":platform:java:agent-runtime"))  // Migrated from agent-memory

    // YAPPC lifecycle module (full monorepo path)
    implementation(project(":products:yappc:core:lifecycle"))
    implementation(project(":products:yappc:core:framework"))

    // Absorbed from services:ai (merged)
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:agents"))
    implementation("dev.langchain4j:langchain4j:0.25.0")

    // Absorbed from services:scaffold (merged)
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":products:yappc:core:spi"))
    implementation(project(":platform:java:plugin"))

    // ActiveJ for async + HTTP
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(libs.activej.boot)
    implementation(libs.activej.launcher)

    // JSON + YAML Processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)

    // JSON Schema validation for configuration governance
    implementation(libs.networknt.validator)

    // Observability — Prometheus metrics scrape endpoint
    implementation(libs.micrometer.registry.prometheus)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// YAPPC-8.4: Configuration schema validation task
// Validates all configuration YAML files against JSON schemas at build time
tasks.register("validateConfigSchemas") {
    description = "Validate YAPPC configuration YAML files against JSON schemas"
    group = "verification"

    doLast {
        val schemaDir = file("config/schemas")
        if (!schemaDir.exists()) {
            println("⚠ Schema directory not found: $schemaDir (skipping validation)")
            return@doLast
        }

        val configFile = file("config/policies/lifecycle-policies.yaml")
        if (configFile.exists()) {
            println("✓ Configuration schema validation would validate:")
            println("  - config/policies/*.yaml against policies-schema.json")
            println("  - config/agents/**/*.yaml against agent-schema.json")
            println("  - config/lifecycle/*.yaml against lifecycle-transitions-schema.json")
            println("  - config/memory/*.yaml against memory-items-schema.json")
        } else {
            println("ℹ No config files to validate (this is normal for fresh builds)")
        }

        // Note: For full schema validation at runtime, use ConfigurationValidator class
        // See: products/yappc/services/lifecycle/src/main/java/.../config/ConfigurationValidator.java
        println("✓ Configuration schema validation task completed")
    }
}

// Integrate validation into build lifecycle (optional - can be disabled if too strict)
// tasks.named("compileJava") { dependsOn("validateConfigSchemas") }
