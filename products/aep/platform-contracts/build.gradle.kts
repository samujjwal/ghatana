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
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    api(libs.activej.promise)

    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))

    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

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