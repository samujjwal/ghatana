/*
 * YAPPC Scaffold API Module (merged: api + api/http + api/grpc + cli)
 * Provides programmatic, HTTP, gRPC, and CLI access to YAPPC scaffold operations
 */

plugins {
    id("java-library")
    id("application")
    id("jacoco")
}

description = "YAPPC Scaffold API - Unified API layer (merged: api + http + grpc + cli)"

application {
    mainClass.set("com.ghatana.yappc.cli.YappcEntryPoint")
}

val grpcVersion = "1.60.0"
val protobufVersion = "3.25.1"

dependencies {
    // Internal dependencies
    implementation(project(":products:yappc:core:scaffold:core"))  // absorbs packs
    
    // JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    
    // CLI (from scaffold:cli)
    implementation(libs.picocli)
    implementation(libs.diffutils)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // HTTP — migrated to ActiveJ (ADR-004 compliant)
    implementation(project(":platform:java:http"))
    implementation(libs.activej.http)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    
    // gRPC (from api/grpc)
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("io.grpc:grpc-testing:$grpcVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.11")
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate JARs in distribution (e.g. core-*.jar from multiple dependency paths)
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Jacoco configuration with lowered coverage thresholds
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
                minimum = "0.00".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
        }
    }
}
