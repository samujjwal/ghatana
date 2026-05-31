import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
        id("application")
    id("protobuf-module")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


dependencies {
    // Audio-Video common library — SttEngine, TtsEngine, media types, security interceptors
    implementation(project(":products:audio-video:libs:common"))

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

    // Data Cloud integration for media artifact operations
    implementation(project(":products:data-cloud:planes:data:entity"))

    // Persistence layer
    implementation(project(":products:audio-video:modules:infrastructure:persistence"))

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
    mainClass.set("com.ghatana.stt.grpc.SttGrpcServer")
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
    mainClass.set("com.ghatana.stt.grpc.SttGrpcServer")
    environment("STT_GRPC_PORT", sttPort)
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
// Runs the JVM with -cp and -noverify-style class loading check via Class.forName in dry-run mode.
// This task is intended for CI verification and will fail if the mainClass is wrong or missing.
tasks.register<JavaExec>("smokeTestMainClass") {
    group = "verification"
    description = "AV-P0-003: Verify mainClass 'com.ghatana.stt.grpc.SttGrpcServer' is resolvable on the runtime classpath."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.stt.grpc.SttGrpcServer")
    // Pass a smoke-check system property so the server main() can detect dry-run mode and exit cleanly
    systemProperty("av.smokeTest", "true")
    // Fail-fast: treat non-zero exit as a build error
    isIgnoreExitValue = false
    jvmArgs("-Dav.smokeTest=true")
}

// Data Cloud event bridge test - validates STT service can consume Data Cloud media events
tasks.register<Test>("testDataCloudEventBridge") {
    group = "verification"
    description = "Test Data Cloud event bridge integration for STT service"
    useJUnitPlatform {
        includeTags("datacloud-event-bridge")
    }
    systemProperty("datacloud.event.bridge.test", "true")
}
