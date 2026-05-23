plugins {
    id("java-module")
}

description = "Platform Kernel Plugin - plugin framework and lifecycle management"

dependencies {
    api(project(":platform-kernel:kernel-core"))
    api(libs.activej.promise)
    api(libs.activej.common)
    api(libs.resilience4j.circuitbreaker)
    api(libs.resilience4j.retry)
    api(libs.resilience4j.core)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
}
