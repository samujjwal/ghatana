plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core for base classes
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":platform:java:core"))
    
    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    
    // Jackson for JSON processing
    implementation(libs.jackson.databind)
    
    // Kafka client (optional - compileOnly to avoid hard dependency)
    compileOnly("org.apache.kafka:kafka-clients:3.6.0")
    
    // AWS SDK (optional)
    compileOnly("software.amazon.awssdk:sqs:2.21.0")
    compileOnly("software.amazon.awssdk:s3:2.21.0")
    
    // RabbitMQ (optional)
    compileOnly("com.rabbitmq:amqp-client:5.18.0")
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.kafka)
    testImplementation("org.apache.kafka:kafka-clients:3.6.0")
    testImplementation("software.amazon.awssdk:sqs:2.21.0")
    testImplementation("com.rabbitmq:amqp-client:5.18.0")
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 50 classes in this module
