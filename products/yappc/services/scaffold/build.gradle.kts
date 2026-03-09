plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-scaffold")

description = "YAPPC Services: Scaffold — Code generation and project scaffolding"

dependencies {
    // Internal domain (full monorepo paths)
    implementation(project(":products:yappc:services:domain"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:observability"))

    // YAPPC scaffold modules (full monorepo paths)
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":products:yappc:core:scaffold:adapters"))
    implementation(project(":products:yappc:core:scaffold:schemas"))
    implementation(project(":products:yappc:core:scaffold:packs"))

    // YAPPC framework (plugin system)
    implementation(project(":products:yappc:core:framework"))
    implementation(project(":products:yappc:core:spi"))
    implementation(project(":platform:java:plugin"))

    // ActiveJ for async + HTTP
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(libs.activej.boot)
    implementation(libs.activej.launcher)

    // JSON Processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // JSON Schema Validation
    implementation("com.networknt:json-schema-validator:1.3.3")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
