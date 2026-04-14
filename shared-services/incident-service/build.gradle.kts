/*
 * Incident Service Module - Build Configuration
 *
 * Migrated from platform:java:incident-response per Phase 3.2 of the audit.
 * Provides incident management capabilities as a shared service.
 *
 * @doc.type module
 * @doc.purpose Incident management shared service
 * @doc.layer shared-service
 * @doc.pattern Service
 */

plugins {
    id("java-module")
}

dependencies {
    api(project(":platform:contracts"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))

    api(libs.jackson.core)
    api(libs.jackson.databind)

    implementation(libs.slf4j.api)
    implementation(libs.activej.promise)
    implementation(project(":platform:java:database"))
    implementation(libs.jedis)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.bundles.testing.containers)
}

tasks.test {
    maxParallelForks = 4
}
