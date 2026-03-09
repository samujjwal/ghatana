import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.ghatana.java-conventions")
    id("application")
    alias(libs.plugins.protobuf)
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

dependencies {
    // Audio-Video common (security, tracing, rate limiting interceptors)
    implementation(project(":products:audio-video:libs:common"))

    // gRPC
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    
    // Multi-tenancy
    implementation(project(":platform:java:governance"))
    
    // Protobuf
    implementation(libs.protobuf.java)
    
    // javax.annotation for gRPC generated code
    implementation(libs.javax.annotation.api)
    
    // Logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.api)
    
    // TTS Engine (Piper or similar)
    implementation(libs.javacpp)
    
    // JSON processing
    implementation(libs.gson)
    
    // SLF4J
    implementation(libs.slf4j.api)
    
    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // ONNX Runtime
    implementation(libs.onnxruntime)
    
    // Native lib loader
    implementation(libs.native.lib.loader)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

application {
    mainClass.set("com.ghatana.tts.core.grpc.TtsGrpcServer")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libsCatalog.findVersion("protobuf").get().requiredVersion}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libsCatalog.findVersion("grpc").get().requiredVersion}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Environment variables for service
val ttsPort = System.getenv("TTS_GRPC_PORT") ?: "50052"

tasks.register<JavaExec>("runTtsService") {
    group = "application"
    description = "Run the TTS gRPC service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.tts.core.grpc.TtsGrpcServer")
    environment("TTS_GRPC_PORT", ttsPort)
}
