plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

description = "YAPPC AI Module - Consolidated AI capabilities (merged: ai + canvas-ai + ai-requirements)"

dependencies {
    // AI Integration (platform)
    api(project(":platform:java:ai-integration"))
    
    // Agent Framework
    api(project(":platform:java:agent-framework"))
    
    // Platform libs
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:domain"))
    
    // Domain models
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // ActiveJ
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.boot)
    implementation(libs.activej.inject)
    implementation(libs.activej.core)

    // gRPC (for canvas service)
    implementation(project(":platform:contracts"))
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)
    implementation("com.google.api.grpc:proto-google-common-protos:2.29.0")

    // Jackson
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // HTTP Client (from ai-requirements:ai)
    implementation(libs.okhttp)

    // JWT Authentication (from ai-requirements:api)
    implementation(libs.nimbus.jose.jwt)

    // GraphQL (from ai-requirements:api)
    api(libs.graphql.java)
    api(libs.graphql.extended.scalars)
    api(libs.graphql.java.tools)

    // Validation (from ai-requirements:api)
    implementation(libs.hibernate.validator)

    // Persistence (from ai-requirements:application)
    implementation(libs.hibernate.core)
    implementation(libs.postgresql)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    runtimeOnly(libs.logback.classic)

    // Testing
    testImplementation(project(":platform:java:runtime"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.activej.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
}
