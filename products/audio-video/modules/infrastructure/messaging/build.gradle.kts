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
    implementation(libs.rabbitmq.client)
    
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
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
