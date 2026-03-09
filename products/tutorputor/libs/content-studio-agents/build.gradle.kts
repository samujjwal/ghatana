plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

group = "com.ghatana.tutorputor"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

// Ensure compileJava depends on proto generation
tasks.named("compileJava") {
    dependsOn("generateProto")
}

dependencies {
    // Agent framework for agent implementation
    api(project(":platform:java:agent-framework"))
    
    // Core platform dependencies
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    
    // Shared services — REUSE existing platform infrastructure
    api(project(":platform:java:ai-integration"))       // Shared AI/ML services (OpenAI, embeddings)
    api(project(":platform:java:observability"))         // Shared observability (metrics, tracing)
    api(project(":platform:java:runtime"))               // ActiveJ promise utilities
    
    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.common)
    implementation(libs.activej.http)
    
    // Contracts for proto types
    implementation(project(":platform:contracts"))
    
    // gRPC and Protobuf
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    compileOnly(libs.javax.annotation.api)
    
    // Jackson for JSON processing
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    
    // SLF4J for logging
    implementation(libs.slf4j.api)
    
    // Lombok for boilerplate reduction
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Micrometer for metrics
    implementation(libs.micrometer.core)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
