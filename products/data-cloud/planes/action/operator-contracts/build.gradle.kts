/*
 * AEP Platform Contracts Module - Build Configuration
 *
 * Shared operator, pipeline, and event abstractions extracted from platform-engine.
 * Contains the reusable Java contract surface consumed by other products.
 *
 * @doc.type module
 * @doc.purpose Shared AEP operator, pipeline, and event contracts
 * @doc.layer product
 * @doc.pattern Module
 */

plugins {
    id("java-module")
}

dependencies {
    api(libs.activej.promise)

    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:agent-core"))
    api(project(":products:data-cloud:planes:event:core"))

    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}
