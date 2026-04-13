plugins {
    id("jacoco")
    id("protobuf-module")
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

// Temporarily exclude OpenRewrite-dependent files from compilation
tasks.named<JavaCompile>("compileJava") {
    exclude("**/DebugCommand.java")
    exclude("**/PolyfixCommand.java")
    exclude("**/RunCommand.java")
    exclude("**/DiagnoseCommand.java")
    exclude("**/InteractiveCommand.java")
    exclude("**/InitCommand.java")
}

// Temporarily exclude OpenRewrite-dependent test files from compilation
tasks.named<JavaCompile>("compileTestJava") {
    exclude("**/InteractiveCommandTest.java")
    exclude("**/RunCommandTest.java")
    exclude("**/DiagnoseCommandTest.java")
    exclude("**/InitCommandTest.java")
    exclude("**/DebugCommandTest.java")
    exclude("**/PolyfixCommandTest.java")
}

dependencies {
    // ActiveJ dependencies
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)

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
    implementation(libs.javax.inject)

    // Auth
    implementation(libs.nimbus.jose.jwt)

    // OpenTelemetry
    implementation(libs.opentelemetry.api)

    // Metrics
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)
    implementation("io.micrometer:micrometer-registry-otlp:1.11.5")

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

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
        testImplementation(libs.jmh.core)
    testImplementation(libs.jmh.generator.annprocess)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(project(":platform:java:testing"))
}


tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}


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
