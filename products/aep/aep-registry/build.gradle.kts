/*
 * Platform Registry Module - Build Configuration
 *
 * Contains pipeline registry, deployment management, store abstractions,
 * AND agent registry (absorbed from aep-agent on 2026-03-22 per boundary audit).
 * Depends on platform-core for engine integration.
 */

plugins {
    id("java-module")
}

dependencies {
    // Depends on core
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:messaging"))  // Unified messaging (merged connectors)
    implementation(project(":platform:java:database"))

    // Agent registry (absorbed from aep-agent — 2026-03-22; agent-registry merged into agent-core 2026-03-24)
    implementation(project(":platform:java:agent-core"))
    implementation(project(":platform-kernel:kernel-core"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:contracts"))
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("org.hibernate.orm:hibernate-core:6.6.1.Final")

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
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")

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

// Target: < 150 classes (increased due to aep-agent absorption on 2026-03-22)
