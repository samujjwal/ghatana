plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-lifecycle")

description = "YAPPC Services: Lifecycle — SDLC phase management and orchestration"

dependencies {
    // Internal domain (full monorepo paths)
    implementation(project(":products:yappc:services:domain"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:event-cloud"))  // Ph1c: EventCloud not transitively exported from core:agents
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:governance"))

    // YAPPC lifecycle module (full monorepo path)
    implementation(project(":products:yappc:core:lifecycle"))
    implementation(project(":products:yappc:core:framework"))

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
