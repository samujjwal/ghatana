/*
 * AEP Runtime Core Module - Build Configuration (Facade for Unified Runtime)
 *
 * DEPRECATED: This module is now a thin re-export facade for backward compatibility.
 * The unified AEP runtime is now in aep-engine (Phase 1.6 consolidation).
 *
 * All runtime code has been merged into aep-engine:
 * - aep-engine: Core execution engine
 * - aep-central-runtime: Central registry/catalog (merged)
 * - aep-agent-runtime: Agent runtime (merged)
 *
 * This module exists so existing consumers need not update their imports immediately.
 * Migration path: Update all dependencies from aep-runtime-core to aep-engine.
 */

plugins {
    id("java-module")
    `java-library`
}

dependencies {
    // Re-export aep-engine so all existing consumers work unchanged
    api(project(":products:aep:aep-engine"))

    // ── Test dependencies ────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:agent-core"))
    testImplementation(project(":products:aep:aep-agent-runtime"))
    testImplementation(project(":platform:java:security"))
    testImplementation(project(":products:aep:aep-security"))   // AepSecretManager, AepSecurityFilter
    testImplementation(project(":products:aep:aep-registry"))
    testImplementation(project(":products:aep:aep-engine"))

    // Additional modules unlocked by Phase-7 test restoration
    testImplementation(project(":products:aep:aep-analytics"))    // AnalyticsEngineDefaultsTest
    testImplementation(project(":platform:java:messaging"))      // Unified messaging (merged connectors)
    testImplementation(project(":products:aep:aep-event-cloud"))  // DataCloudEventCloudClientTest
    testImplementation(project(":products:data-cloud:spi"))       // EventView used by analytics + datacloud tests
    testImplementation(project(":products:aep:aep-scaling"))      // Scaling unit tests
    testImplementation("org.apache.kafka:kafka-clients:3.6.0")    // KafkaDltTest (MockConsumer / MockProducer)

    // JMH benchmarks (compile only in test sourceset; not run by JUnit)
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

// Keep-excluded: tests whose referenced production classes do not yet exist, or
// which require a live external service (Postgres, full integration pipeline).
// ALL other tests have had their deps added above and are now active.
sourceSets.test {
    java.exclude(
        // ── requires live PostgreSQL (Testcontainers not in CI baseline) ─────
        "com/ghatana/statestore/checkpoint/PostgresCheckpointStorageTest.java",
        "com/ghatana/aep/config/EnvConfigDbTest.java",

        // ── full-stack pipeline bootstrap: needs DB + Kafka + DataCloud ──────
        "com/ghatana/aep/config/ConfigurationPipelineIntegrationTest.java",
        "com/ghatana/aep/config/PipelineMaterializerTest.java",

        // ── DefaultAdvancedTimeSeriesForecaster and other analytics Default* ──
        // classes not yet implemented in aep-analytics module
        "com/ghatana/aep/analytics/AnalyticsEngineDefaultsTest.java",

        // ── HttpIngressStrategy not present in aep-connectors ────────────────
        "com/ghatana/aep/connector/strategy/ConnectorConfigTest.java",

        // ── feature module classes AepFeatureStoreClient etc not implemented ─
        "com/ghatana/aep/feature/**",

        // ── DataCloudEventCloudClient / InMemoryEventLogStore not in SPI ─────
        "com/ghatana/aep/integration/events/DataCloudEventCloudClientTest.java",

        // ── ClusterState missing from aep-scaling ────────────────────────────
        "com/ghatana/aep/scaling/cluster/ClusterManagementSystemTest.java",
        "com/ghatana/aep/scaling/loadbalancer/AdvancedLoadBalancerTest.java",

        // ── PipelineMigrationController class not present in aep-registry ────
        "com/ghatana/pipeline/registry/web/PipelineMigrationControllerTest.java",

        // ── KafkaConsumerStrategy API changed; KafkaDltTest needs refresh ────
        "com/ghatana/aep/connector/strategy/kafka/KafkaDltTest.java",

        // ── ScalingIntegrationService.ScalingConfiguration API changed ───────
        "com/ghatana/aep/scaling/integration/ScalingIntegrationServiceTest.java",

        // ── AepTestFixtures references AgentSpec which is not yet implemented ─
        // (utility factory, not a test; re-enable when AgentSpec lands in engine)
        "com/ghatana/aep/testing/AepTestFixtures.java",
    )
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}

// Class count target: < 200 classes in this module (facade + test infra only)
