/*
 * Platform Registry Module - Build Configuration
 * 
 * Contains pipeline registry, deployment management, and store abstractions.
 * Depends on platform-core for engine integration.
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core
    implementation(project(":products:aep:platform-core"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:connectors"))
    implementation(project(":platform:java:database"))
    implementation(project(":products:aep:platform-connectors"))
    
    // gRPC dependencies
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    
    // Connection pool and cache
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("redis.clients:jedis:5.1.0")
    
    // Lombok for model classes
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Jakarta
    implementation(libs.jakarta.inject)
    
    // ActiveJ
    implementation(libs.activej.promise)
    
    // Jackson
    implementation(libs.jackson.databind)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 100 classes
