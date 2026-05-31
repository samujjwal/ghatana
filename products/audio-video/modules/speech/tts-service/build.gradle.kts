import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
        id("application")
    id("protobuf-module")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


dependencies {
    // Audio-Video common library — TtsEngine, SttEngine, VisionEngine, media types, security interceptors
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
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation(libs.assertj.core)
}

application {
    mainClass.set("com.ghatana.tts.grpc.TtsGrpcServer")
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
val ttsPort = System.getenv("TTS_GRPC_PORT") ?: "50052"

tasks.register<JavaExec>("runTtsService") {
    group = "application"
    description = "Run the TTS gRPC service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.tts.grpc.TtsGrpcServer")
    environment("TTS_GRPC_PORT", ttsPort)
}

// AV-P0-003: Smoke test that validates the mainClass is resolvable on the runtime classpath.
tasks.register<JavaExec>("smokeTestMainClass") {
    group = "verification"
    description = "AV-P0-003: Verify mainClass 'com.ghatana.tts.grpc.TtsGrpcServer' is resolvable on the runtime classpath."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.tts.grpc.TtsGrpcServer")
    systemProperty("av.smokeTest", "true")
    isIgnoreExitValue = false
    jvmArgs("-Dav.smokeTest=true")
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
