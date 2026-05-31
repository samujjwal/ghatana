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

    // STT Service proto stubs for gRPC client
    implementation(project(":products:audio-video:modules:speech:stt-service"))

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

    // Platform audit (AuditService, AuditEvent)
    implementation(project(":platform:java:audit"))

    // Platform eventstore (EventLogStore, TenantContext) for Data Cloud event consumption
    implementation(project(":platform:java:domain"))

    // Data Cloud integration for media artifact job handling
    implementation(project(":products:data-cloud:planes:data:entity"))

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

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Micrometer metrics
    implementation(libs.micrometer.core)

    // OpenTelemetry for tracing
    implementation(libs.opentelemetry.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
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

// AV-P0-003: Smoke test that validates the mainClass is resolvable on the runtime classpath.
tasks.register<JavaExec>("smokeTestMainClass") {
    group = "verification"
    description = "AV-P0-003: Verify mainClass 'com.ghatana.audio.video.multimodal.grpc.MultimodalGrpcServer' is resolvable on the runtime classpath."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.audio.video.multimodal.grpc.MultimodalGrpcServer")
    systemProperty("av.smokeTest", "true")
    isIgnoreExitValue = false
    jvmArgs("-Dav.smokeTest=true")
}
