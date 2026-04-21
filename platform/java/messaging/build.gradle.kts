plugins {
    id("java-module")
}

description = "Platform Messaging - Unified messaging and connector abstractions"

dependencies {
    // Core platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:http"))

    // EventEntry and EventLogStore abstractions are platform-owned
    // via platform:java:domain to avoid platform -> product dependency inversion.

    // ActiveJ Promise support
    api(libs.activej.promise)
    implementation(libs.activej.common)
    implementation(libs.activej.http)

    // PostgreSQL JDBC and replication support
    implementation(libs.postgresql)

    // HikariCP for connection pooling
    implementation(libs.hikaricp)

    // Jackson for JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:3.8.0")

    // AWS SDK (optional - compileOnly to avoid hard dependency)
    compileOnly("software.amazon.awssdk:sqs:2.21.0")
    compileOnly("software.amazon.awssdk:s3:2.21.0")

    // RabbitMQ (optional)
    compileOnly("com.rabbitmq:amqp-client:5.18.0")

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation("org.apache.kafka:kafka-clients:3.8.0")
    testImplementation("software.amazon.awssdk:sqs:2.21.0")
    testImplementation("software.amazon.awssdk:s3:2.21.0")
    testImplementation("com.rabbitmq:amqp-client:5.18.0")
}
