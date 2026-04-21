plugins {
    id("java-module")
}

dependencies {
    // Platform messaging
    implementation(project(":platform:java:messaging"))
    
    // Platform observability
    implementation(project(":platform:java:observability"))
    
    // Platform core
    implementation(project(":platform:java:core"))
    
    // RabbitMQ client
    implementation("com.rabbitmq:amqp-client:5.18.0")
    
    // JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // ActiveJ
    implementation(libs.activej.promise)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
