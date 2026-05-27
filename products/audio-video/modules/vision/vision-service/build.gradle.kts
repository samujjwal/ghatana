import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
        id("application")
    id("protobuf-module")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


dependencies {
    // Audio-Video common (health/metrics server, gRPC interceptor chain, security)
    implementation(project(":products:audio-video:libs:common"))

    // gRPC
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)

    // Multi-tenancy
    implementation(project(":platform:java:governance"))

    // Platform audit (AuditService, AuditEvent)
    implementation(project(":platform:java:audit"))

    // Persistence integration
    implementation(project(":products:audio-video:modules:infrastructure:persistence"))

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

    // Jackson annotations
    implementation(libs.jackson.annotations)

    // OpenCV for computer vision
    implementation("org.openpnp:opencv:4.9.0-0")
    implementation("org.scijava:native-lib-loader:2.4.0")

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

// AV-P0-003: Smoke test that validates the mainClass is resolvable on the runtime classpath.
tasks.register<JavaExec>("smokeTestMainClass") {
    group = "verification"
    description = "AV-P0-003: Verify mainClass 'com.ghatana.audio.video.vision.grpc.VisionGrpcServer' is resolvable on the runtime classpath."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ghatana.audio.video.vision.grpc.VisionGrpcServer")
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
