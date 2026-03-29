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
    api(libs.activej.eventloop)
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
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))

    // Redis
    implementation("redis.clients:jedis:5.1.0")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

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
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}
