/*
 * Platform Scaling Module - Build Configuration
 *
 * Contains auto-scaling, cluster management, distributed processing,
 * and load balancing capabilities for the AEP platform.
 */

plugins {
    id("java-module")
}

dependencies {
    // Core platform dependencies
    implementation(project(":products:data-cloud:planes:action:engine"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.bundles.activej.core)

    // Jackson for JSON processing
    implementation(libs.jackson.databind)

    // Lombok for model classes
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 200 classes in this module
