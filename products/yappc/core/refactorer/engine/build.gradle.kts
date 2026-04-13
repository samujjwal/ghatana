plugins {
    id("java-module")
}

group = "com.ghatana.products.yappc.refactorer"
version = rootProject.version

description = "Refactorer Engine - Unified core, engine & language support (merged: refactorer-core + refactorer-engine + refactorer-languages)"

java {
    // Keep sources/javadoc jars for this publishable module
    withSourcesJar()
    withJavadocJar()
}

// Temporarily exclude OpenRewrite-dependent files from compilation
tasks.named<JavaCompile>("compileJava") {
    exclude("**/OpenRewriteRunner.java")
    exclude("**/CodemodOrchestrator.java")
    exclude("**/JavaAdvancedRewriteRunner.java")
}

// Temporarily exclude OpenRewrite-dependent test files from compilation
tasks.named<JavaCompile>("compileTestJava") {
    exclude("**/OpenRewriteRunnerTest.java")
    exclude("**/CodemodOrchestratorTest.java")
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

    // OpenRewrite - Temporarily commented out due to dependency resolution issues
    // implementation("org.openrewrite:openrewrite-core:5.0.0")
    // implementation("org.openrewrite:openrewrite-java:5.0.0")
    // implementation("org.openrewrite:openrewrite-yaml:5.0.0")

    // Jython for Python refactoring
    implementation("org.python:jython-standalone:2.7.4")

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
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
        testImplementation(project(":platform:java:testing"))
}

tasks.test {
    // useJUnitPlatform() already applied by java-module
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

// jacoco and jacocoTestReport configured by java-module; keep coverage verification thresholds

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
