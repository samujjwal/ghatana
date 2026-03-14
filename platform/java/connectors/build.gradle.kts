plugins {
    id("java-library")
}

group = "com.ghatana.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))

    // EventRecord and EventCloud abstractions (platform-only, no product dependency)
    api(project(":platform:java:event-cloud"))

    // ActiveJ Promise support (type references OK per architecture)
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

    // Kafka client (KafkaConnector)
    implementation(libs.kafka.clients)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
