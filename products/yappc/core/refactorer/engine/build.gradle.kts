plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.refactorer"
version = "1.0.0-SNAPSHOT"

description = "Refactorer Engine - Unified core, engine & language support (merged: refactorer-core + refactorer-engine + refactorer-languages)"

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
    implementation(libs.activej.common)

    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))

    // AST parsing and manipulation
    implementation("com.github.javaparser:javaparser-core:3.25.7")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.25.7")

    // Language parsers (from refactorer-languages)
    implementation("org.antlr:antlr4-runtime:4.13.1")

    // OpenRewrite
    implementation(libs.openrewrite.core)
    implementation(libs.openrewrite.java)

    // Jython for Python refactoring
    implementation("org.python:jython-standalone:2.7.3")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Utilities
    implementation(libs.guava)
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)

    // JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
