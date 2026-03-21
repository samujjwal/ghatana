/*
 * Platform Core Module - Build Configuration
 * 
 * Contains the core AEP engine, event processing, and pipeline execution.
 * This is the foundational module that other platform modules depend on.
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // ActiveJ - core async framework
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.csp)
    
    // Jackson - JSON processing
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    
    // Platform domain
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:contracts"))
    
    // Redis
    implementation("redis.clients:jedis:5.1.0")
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:agent-framework"))
    testImplementation(project(":platform:java:agent-memory"))
    testImplementation(project(":platform:java:security"))
    testImplementation(project(":products:aep:platform-registry"))
    testImplementation(project(":products:aep:platform-agent"))
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

// Exclude test files that depend on packages not yet available in
// this module's test classpath (scaling models, data-cloud SPI, Kafka,
// connector strategies, etc.).  These tests will be re-enabled once
// the referenced packages are extracted or the tests are relocated.
sourceSets.test {
    java.exclude(
        // Tests that depend on modules/APIs not on platform-core's test classpath
        "com/ghatana/aep/agent/AepAgentAdapterTest.java",
        "com/ghatana/aep/agent/AepContextBridgeTest.java",
        "com/ghatana/aep/config/AepConfigurationValidatorTest.java",
        "com/ghatana/aep/config/ConfigurationPipelineIntegrationTest.java",
        "com/ghatana/aep/config/PipelineMaterializerTest.java",
        "com/ghatana/aep/config/EnvConfigDbTest.java",
        "com/ghatana/aep/config/EnvConfigTest.java",
        "com/ghatana/aep/integration/events/DataCloudEventCloudClientTest.java",
        "com/ghatana/aep/scaling/**",
        "com/ghatana/aep/testing/AepTestFixtures.java",
        "com/ghatana/aep/security/AepSecretManagerTest.java",
        "com/ghatana/aep/security/AepSecurityFilterTest.java",
        "com/ghatana/eventprocessing/registry/RegistrationMapperTest.java",
        "com/ghatana/eventprocessing/registry/RegistryEndToEndTest.java",
        "com/ghatana/pipeline/registry/web/PipelineMigrationControllerTest.java",
        "com/ghatana/aep/analytics/AnalyticsEngineDefaultsTest.java",
        "com/ghatana/aep/connector/strategy/**",
        "com/ghatana/aep/feature/**",
        "com/ghatana/aep/operator/DeadLetterOperatorTest.java",
        "com/ghatana/pattern/engine/agent/PatternDetectionAgentTest.java",
    )
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}

// Class count target for modularization
// Target: < 200 classes in this module
// Current: TBD (will be measured after extraction)
