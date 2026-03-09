/*
 * YAPPC gRPC API Module
 * gRPC API for high-performance scaffold operations
 */

plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

description = "YAPPC gRPC API - High-performance RPC access to scaffold operations"

val grpcVersion = "1.60.0"
val protobufVersion = "3.25.1"

dependencies {
    // Internal dependencies
    implementation(project(":products:yappc:core:scaffold:api"))
    implementation(project(":products:yappc:core:scaffold:core"))
    
    // gRPC
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    
    // Protobuf
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
    
    // Annotations
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation("io.grpc:grpc-testing:$grpcVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.11")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
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

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
