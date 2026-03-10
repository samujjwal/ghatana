plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

group = "com.ghatana.tutorputor"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform dependencies
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:contracts"))
    
    // LLM integration
    implementation("dev.langchain4j:langchain4j:0.34.0")
    implementation("dev.langchain4j:langchain4j-open-ai:0.34.0")
    implementation("dev.langchain4j:langchain4j-ollama:0.34.0")
    
    // gRPC
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    compileOnly(libs.javax.annotation.api)
    
    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // Observability
    implementation(libs.micrometer.core)
    implementation(libs.opentelemetry.api)
    
    // Utilities
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
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
        proto {
            srcDir("src/main/proto")
        }
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named("compileJava") {
    dependsOn("generateProto")
}

// Handle duplicate proto files in resources
tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
