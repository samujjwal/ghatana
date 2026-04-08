plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
    id("jacoco")
}

group = "com.ghatana.products.yappc.refactorer"
version = rootProject.version

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
    implementation(libs.picocli)
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
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco { toolVersion = "0.8.11" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.15".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            exclude(
                "**/package-info.class",
                "**/*Config.class",
                "**/*Module.class",
                "**/*Launcher.class",
                "**/*Bootstrapper.class",
                "**/generated/**"
            )
        }
    )
}
