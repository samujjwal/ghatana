plugins {
    id("java-library")
    // DISABLED: Protobuf plugin causes afterEvaluate conflicts with Gradle 8.10
    // alias(libs.plugins.protobuf)
}

description = "Virtual Organization Protocol Buffer Contracts"

group = "com.ghatana.products.virtual-org"
version = "1.0-SNAPSHOT"

dependencies {
    // Core Protobuf
    api(libs.protobuf.java)
    api(libs.protobuf.java.util)
    
    // gRPC dependencies
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.netty.shaded)
    
    // Google common protos
    api(libs.google.common.protos)
    
    // Common contracts (shared types)
    api(project(":platform:contracts"))
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java")
            // Include previously generated proto sources
            srcDirs("build/generated/source/proto/main/java")
            srcDirs("build/generated/source/proto/main/grpc")
        }
    }
}

// Prevent copying proto files to resources (causes duplicates)
tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
