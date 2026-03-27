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
    // Audio-Video common (health/metrics server, gRPC interceptor chain, security)
    implementation(project(":products:audio-video:libs:common"))

    // gRPC
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    
    // Multi-tenancy
    implementation(project(":platform:java:governance"))
    
    // Platform Audio-Video
    implementation(project(":platform:java:audio-video"))
    
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
    
    // Jackson annotations
    implementation(libs.jackson.annotations)
    
    // OpenCV for image processing (used by YoloV8Adapter)
    implementation(libs.opencv.java)
    
    // Native library loader (loads OpenCV JNI library)
    implementation(libs.native.lib.loader)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":platform:java:testing"))
}

application {
    mainClass.set("com.ghatana.audio.video.vision.grpc.VisionGrpcServer")
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

val visionPort = System.getenv("VISION_GRPC_PORT") ?: "50054"

tasks.register<JavaExec>("runVisionService") {
    group = "application"
    description = "Run the Vision gRPC service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.audio.video.vision.grpc.VisionGrpcServer")
    environment("VISION_GRPC_PORT", visionPort)
}
