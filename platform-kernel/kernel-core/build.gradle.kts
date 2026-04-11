plugins {
    id("java-module")
}

description = "Platform Kernel Core - module lifecycle and context abstractions"

dependencies {
    api(project(":platform:java:core"))  // JsonUtils and core utilities
    api(libs.bundles.activej.core)
    api(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)
    implementation(libs.bundles.common.utils)
    api(libs.bundles.logging.core)
    compileOnly(libs.bundles.dev.tools)
    testCompileOnly(libs.bundles.dev.tools)
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.jmh.core)
    testImplementation(libs.jmh.generator.annprocess)
}

// Fix JaCoCo task dependency
tasks.named("jacocoTestReport") {
    dependsOn("compileJava")
}
