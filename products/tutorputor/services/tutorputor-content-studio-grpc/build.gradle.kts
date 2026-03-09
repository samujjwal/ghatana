plugins {
    id("java")
    id("application")
}

group = "com.ghatana.tutorputor"
version = "0.1.0"

dependencies {
    implementation(project(":products:tutorputor:libs:content-studio-agents"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))

    // gRPC runtime
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)

    // ActiveJ
    implementation(libs.activej.http)
    implementation(libs.activej.eventloop)

    // Metrics baseline
    implementation(libs.micrometer.core)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.ghatana.tutorputor.contentstudio.server.ContentStudioGrpcServerMain"
}

// Handle duplicate JAR files in distribution
tasks.named<Tar>("distTar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Zip>("distZip") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
