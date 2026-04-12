import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.ghatana.java-conventions")
    id("application")
    id("com.ghatana.protobuf-conventions")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


dependencies {
    // Audio-Video common library — SttEngine, TtsEngine, media types, security interceptors
    implementation(project(":products:audio-video:libs:java:common"))

    // gRPC
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    
    // Multi-tenancy
    implementation(project(":platform:java:governance"))

    // Platform security (JWT, RBAC, User model)
    implementation(project(":platform:java:security"))

    // Platform observability (MetricsCollector, TracingManager)
    implementation(project(":platform:java:observability"))
    
    // Protobuf
    implementation(libs.protobuf.java)
    
    // javax.annotation for gRPC generated code
    implementation(libs.javax.inject)
    
    // Logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.api)
    
    // JSON processing
        
    // SLF4J
    implementation(libs.slf4j.api)
    
    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Micrometer metrics
    implementation(libs.micrometer.core)
    
    // OpenTelemetry for tracing
    implementation(libs.opentelemetry.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

application {
    mainClass.set("com.ghatana.stt.core.grpc.SttGrpcServer")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


tasks.test {
    useJUnitPlatform()
}

// Environment variables for service
val sttPort = System.getenv("STT_GRPC_PORT") ?: "50051"

tasks.register<JavaExec>("runSttService") {
    group = "application"
    description = "Run the STT gRPC service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.stt.core.grpc.SttGrpcServer")
    environment("STT_GRPC_PORT", sttPort)
}

// Fix duplicate jar entries in distribution
tasks.named<Tar>("distTar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.named<Zip>("distZip") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
