plugins {
    id("java-library")
}

group = "com.ghatana.aep"
version = "1.0.0-SNAPSHOT"

description = "AEP Platform - Core domain logic and orchestration"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // =========================================================================
    // GLOBAL PLATFORM DEPENDENCIES
    // =========================================================================
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:workflow"))
    api(project(":platform:java:agent-framework"))
    api(project(":platform:java:agent-dispatch"))
    api(project(":platform:java:agent-learning"))
    api(project(":platform:java:agent-memory"))
    api(project(":platform:java:agent-registry"))
    api(project(":platform:java:connectors"))
    api(project(":products:data-cloud:spi"))       // SPI interfaces only (not full platform)
    api(project(":platform:java:plugin"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:audit"))
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    api(project(":platform:java:config"))
    
    // =========================================================================
    // ACTIVEJ (Async Runtime)
    // =========================================================================
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.http)
    api(libs.activej.inject)
    api(libs.activej.launcher)
    api(libs.activej.boot)
    
    // =========================================================================
    // PERSISTENCE
    // =========================================================================
    api(libs.jakarta.persistence.api)
    api(libs.jakarta.inject)
    api(libs.hibernate.core)
    api(libs.hikaricp)
    api(libs.postgresql)
    api(libs.flyway.core)
    api(libs.flyway.database.postgresql)
    
    // =========================================================================
    // GRPC (For Agent Communication)
    // =========================================================================
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    
    // =========================================================================
    // SERIALIZATION
    // =========================================================================
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jackson.annotations)
    
    // =========================================================================
    // OBSERVABILITY
    // =========================================================================
    api(libs.micrometer.core)
    api(libs.micrometer.registry.prometheus)
    
    // =========================================================================
    // CONNECTOR STRATEGIES DEPENDENCIES
    // =========================================================================
    api(platform(libs.aws.sdk.bom))
    
    // Messaging & Queuing
    implementation(libs.kafka.clients)
    implementation(libs.rabbitmq.amqp.client)
    implementation(libs.aws.sqs)
    
    // Storage Connectors
    implementation(libs.aws.s3)
    
    // AI/ML Libraries (for planner and analytics)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.open.ai)
    
    // Apache Commons (for analytics)
    implementation(libs.commons.math3)
    implementation(libs.commons.lang3)
    
    // =========================================================================
    // TESTING
    // =========================================================================
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

    // H2 in-memory database for JDBC integration tests
    testImplementation(libs.h2)

    // Testcontainers for PostgreSQL integration tests
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)

    // JMH Benchmarks
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
    
    // =========================================================================
    // LOMBOK
    // =========================================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}
