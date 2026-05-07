plugins {
    id("java-module")
}

group = "com.ghatana.yappc"
version = rootProject.version

description = "YAPPC Agent Workflow — SDLC phase step implementations (architecture, implementation, leads, requirements, enhancement, ops, testing phases)"


dependencies {
    // Runtime base (provides base agent types, step contracts, tools, dispatch)
    api(project(":products:yappc:core:agents:runtime"))

    // Platform deps used directly by workflow steps (api — types appear in public step signatures)
    api(project(":platform:java:database"))
    // DataCloud SPI included directly; DataStorePort port decoupling tracked in architecture backlog
    api(project(":products:data-cloud:planes:shared-spi"))
    api(project(":platform:java:workflow"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    // Utilities
    implementation(libs.commons.lang3)
    implementation(libs.guava)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
    testRuntimeOnly(libs.junit.platform.launcher)
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

// java-module already sets UTF-8 encoding and -Xlint:unchecked/-Xlint:deprecation
