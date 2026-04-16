import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
        id("application")
    id("protobuf-module")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


dependencies {
    implementation(project(":platform:contracts"))

    // Audio-Video common library (shared AI inference client, health/metrics, gRPC interceptors)
    implementation(project(":products:audio-video:libs:common"))

    // Agent framework — AbstractTypedAgent, AgentResult, AgentContext
    implementation(project(":platform:java:agent-core"))

    // Reuse the existing product video frame extraction utility instead of re-implementing it
    implementation(project(":products:audio-video:modules:vision:vision-service"))

    // Persistence integration
    implementation(project(":products:audio-video:modules:infrastructure:persistence"))

    // gRPC
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)

    // Multi-tenancy
    implementation(project(":platform:java:governance"))

    // Protobuf
    implementation(libs.protobuf.java)

    // javax.annotation for gRPC generated code
    implementation(libs.javax.inject)

    // Logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.api)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)

    // JSON processing

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.ghatana.audio.video.multimodal.grpc.MultimodalGrpcServer")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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

// Fix duplicate jar entries in distribution
tasks.named<Tar>("distTar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.named<Zip>("distZip") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.named<Sync>("installDist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
