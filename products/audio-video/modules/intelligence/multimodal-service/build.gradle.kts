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
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    
    // JSON processing
    implementation(libs.gson)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

application {
    mainClass.set("com.ghatana.audio.video.multimodal.grpc.MultimodalGrpcServer")
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

val multimodalPort = System.getenv("MULTIMODAL_GRPC_PORT") ?: "50055"

tasks.register<JavaExec>("runMultimodalService") {
    group = "application"
    description = "Run the Multimodal gRPC service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.audio.video.multimodal.grpc.MultimodalGrpcServer")
    environment("MULTIMODAL_GRPC_PORT", multimodalPort)
}
