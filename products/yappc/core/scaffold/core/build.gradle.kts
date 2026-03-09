plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    api(project(":platform:java:testing"))
    
    // Core framework API and testing utilities
    api(project(":products:yappc:core:framework"))
    
    // Core should be lightweight; other modules depend on this
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310) // Java 8 time support
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)
    implementation(libs.diffutils)

    // OpenTelemetry for unified telemetry
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.sdk.testing)

    // Template Engine (Week 2 Day 6)
    implementation(libs.handlebars)

    // JSON Schema Validation
    implementation("com.networknt:json-schema-validator:1.0.87")

    // Code Transformation (Week 2 Day 6)
    implementation(libs.openrewrite.core)
    implementation(libs.openrewrite.java)
    implementation(libs.openrewrite.gradle)

    // ActiveJ for dependency injection (Phase 4)
    api(libs.activej.inject)
    implementation(libs.activej.common)
    implementation(libs.activej.boot)

    // Validation (Phase 4)
    api(libs.jakarta.validation.api)
    implementation(libs.hibernate.validator)

    // Lombok for code generation
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing utilities (used in main code for testing framework)
    implementation(project(":platform:java:runtime"))
    
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
