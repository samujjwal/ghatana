/*
 * Platform Engine Module - Build Configuration
 *
 * Core AEP execution engine extracted from platform-core.
 * Contains: operators, patterns, pipelines, state stores, event processing.
 *
 * @doc.type module
 * @doc.purpose Core AEP execution engine
 * @doc.layer product
 * @doc.pattern Module
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    api(project(":products:aep:aep-operator-contracts"))

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
    api(project(":products:data-cloud:agent-registry"))  // DataCloudAgentRegistry for persistence

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
    testImplementation(project(":products:aep:aep-security"))
    testImplementation(project(":products:aep:aep-registry"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}
