plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

group = "com.ghatana.products.yappc.refactorer"
version = "1.0.0-SNAPSHOT"

description = "Refactorer API - Unified API, adapters & infrastructure (merged: refactorer-api + refactorer-adapters + refactorer-infra)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // ActiveJ dependencies
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.datastream)

    // Internal modules (merged: refactorer-core + refactorer-engine → engine)
    implementation(project(":products:yappc:core:refactorer:engine"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:security"))

    // gRPC dependencies
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    implementation(libs.javax.annotation.api)

    // Auth
    implementation(libs.nimbus.jose.jwt)

    // OpenTelemetry
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)

    // Metrics
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.registry.otlp)

    // CLI dependencies
    implementation("info.picocli:picocli:4.7.5")
    implementation(libs.jline)

    // Caching
    implementation(libs.caffeine)

    // HTTP client (from adapters)
    implementation(libs.okhttp)

    // Storage (from infra)
    implementation(libs.h2)
    implementation(libs.postgresql)

    // JSON (from infra)
    implementation(libs.gson)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Utilities
    implementation(libs.guava)
    implementation(libs.jackson.databind)

    // OpenRewrite
    implementation(libs.openrewrite.core)
    implementation(libs.openrewrite.java)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.activej.test)
    testImplementation(libs.jmh.core)
    testImplementation(libs.jmh.generator.annprocess)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(project(":platform:java:testing"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
